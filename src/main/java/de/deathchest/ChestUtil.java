package de.deathchest;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChestUtil {

    private static final int SLOTS_PER_CHEST = 27;

    private ChestUtil() {
    }

    /**
     * Sucht ausgehend von einem Ort einen Block, an dem eine Truhe platziert werden kann
     * (kein fester Block wie Bedrock/Stein, keine Lava usw.).
     */
    public static Location findSafeLocation(Location start) {
        World world = start.getWorld();
        if (world == null) {
            return start;
        }

        Location loc = start.clone();
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        loc.setY(Math.max(minY, Math.min(loc.getBlockY(), maxY)));

        for (int y = loc.getBlockY(); y <= maxY; y++) {
            Block block = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ());
            if (isReplaceable(block)) {
                loc.setY(y);
                return loc;
            }
        }

        // Nichts Passendes gefunden -> Originalposition zurueckgeben (Truhe ersetzt dann den Block dort).
        return loc;
    }

    private static boolean isReplaceable(Block block) {
        Material type = block.getType();
        return type.isAir()
                || type == Material.WATER
                || type == Material.LAVA
                || type == Material.SNOW
                || type.name().endsWith("_GRASS")
                || type.name().endsWith("_CARPET");
    }

    /**
     * Platziert eine oder (falls noetig) mehrere Truhen ab dem angegebenen Standort und
     * verteilt die Items darauf. Gibt den Standort der ersten Truhe zurueck (fuer den Marker).
     */
    public static Location placeDeathChests(DeathChestPlugin plugin, Location start, UUID owner,
                                             List<ItemStack> items, int exp) {
        World world = start.getWorld();
        if (world == null) {
            return start;
        }

        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                remaining.add(item.clone());
            }
        }

        int maxChests = Math.max(1, plugin.getConfig().getInt("max-additional-chests", 8));
        long lifetimeTicks = plugin.getConfig().getLong("chest-lifetime-minutes", 0) * 60L * 20L;

        List<Location> placedChestLocations = new ArrayList<>();
        Location firstChestLocation = null;
        Location current = start.clone();
        int chestsPlaced = 0;
        int searchAttempts = 0;

        while (!remaining.isEmpty() && chestsPlaced < maxChests && searchAttempts < maxChests * 4) {
            Block block = world.getBlockAt(current);

            if (!isReplaceable(block) && !isDeathChest(plugin, block)) {
                current = current.clone().add(1, 0, 0);
                current = findSafeLocation(current);
                searchAttempts++;
                continue;
            }

            if (!isDeathChest(plugin, block)) {
                block.setType(Material.CHEST);
            }

            Chest chestState = (Chest) block.getState();
            boolean isFirstChest = firstChestLocation == null;

            chestState.getPersistentDataContainer().set(plugin.getDeathChestKey(), PersistentDataType.BYTE, (byte) 1);
            chestState.getPersistentDataContainer().set(plugin.getDeathChestOwnerKey(), PersistentDataType.STRING, owner.toString());
            chestState.getPersistentDataContainer().set(plugin.getDeathChestExpKey(), PersistentDataType.INTEGER, isFirstChest ? exp : 0);
            chestState.update(true, false);

            Iterator<ItemStack> it = remaining.iterator();
            while (it.hasNext() && chestState.getInventory().firstEmpty() != -1) {
                ItemStack stack = it.next();
                Map<Integer, ItemStack> leftover = chestState.getInventory().addItem(stack);
                if (leftover.isEmpty()) {
                    it.remove();
                } else {
                    ItemStack rest = leftover.values().iterator().next();
                    stack.setAmount(rest.getAmount());
                }
            }
            chestState.update(true, false);

            if (isFirstChest) {
                firstChestLocation = block.getLocation();
            }
            placedChestLocations.add(block.getLocation());
            chestsPlaced++;

            if (!remaining.isEmpty()) {
                current = current.clone().add(1, 0, 0);
                current = findSafeLocation(current);
            }
            searchAttempts++;
        }

        // Falls wirklich zu viele Items uebrig sind: auf dem Boden droppen statt zu verlieren.
        for (ItemStack over : remaining) {
            world.dropItemNaturally(start, over);
        }

        if (lifetimeTicks > 0) {
            for (Location chestLoc : placedChestLocations) {
                scheduleExpiry(plugin, chestLoc, owner, lifetimeTicks);
            }
        }

        Location result = firstChestLocation != null ? firstChestLocation : start;
        plugin.getLastChestLocation().put(owner, result);
        return result;
    }

    /**
     * Entfernt eine Truhe nach Ablauf der konfigurierten Zeit automatisch, auch wenn sie
     * noch Items enthaelt. Die Items werden dabei auf dem Boden ausgeschuettet.
     */
    private static void scheduleExpiry(DeathChestPlugin plugin, Location chestLocation, UUID owner, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World world = chestLocation.getWorld();
            if (world == null) return;

            Block block = world.getBlockAt(chestLocation);
            if (!(block.getState() instanceof Chest chest)) return;
            if (!chest.getPersistentDataContainer().has(plugin.getDeathChestKey(), PersistentDataType.BYTE)) return;

            for (ItemStack item : chest.getInventory().getContents()) {
                if (item != null) {
                    world.dropItemNaturally(chestLocation, item);
                }
            }
            block.setType(Material.AIR);

            Location tracked = plugin.getLastChestLocation().get(owner);
            if (tracked != null && tracked.equals(chestLocation)) {
                plugin.getLastChestLocation().remove(owner);
            }

            Player player = Bukkit.getPlayer(owner);
            if (player != null) {
                player.sendMessage(plugin.msg("chest-expired"));
            }
        }, delayTicks);
    }

    private static boolean isDeathChest(DeathChestPlugin plugin, Block block) {
        if (!(block.getState() instanceof Chest chest)) {
            return false;
        }
        return chest.getPersistentDataContainer().has(plugin.getDeathChestKey(), PersistentDataType.BYTE);
    }

    /**
     * Erstellt einen Kompass, der (unabhaengig vom eigentlichen Spawn/Lodestone-Kompass des Spielers)
     * dauerhaft zum angegebenen Ort zeigt.
     */
    public static ItemStack createMarkerCompass(DeathChestPlugin plugin, Location location) {
        String materialName = plugin.getConfig().getString("marker.material", "COMPASS");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.COMPASS;
        }

        String rawName = plugin.getConfig().getString("marker.name", "&c☠ &fTodes-Marker");
        List<String> rawLore = plugin.getConfig().getStringList("marker.lore");

        List<String> lore = new ArrayList<>();
        for (String line : rawLore) {
            String replaced = line
                    .replace("%x%", String.valueOf(location.getBlockX()))
                    .replace("%y%", String.valueOf(location.getBlockY()))
                    .replace("%z%", String.valueOf(location.getBlockZ()));
            lore.add(ChatColor.translateAlternateColorCodes('&', replaced));
        }

        ItemStack compass = new ItemStack(material);
        ItemMeta rawMeta = compass.getItemMeta();

        if (rawMeta instanceof CompassMeta meta) {
            // Nur bei echtem Kompass funktioniert das automatische Zeigen zum Ziel.
            meta.setLodestoneTracked(false);
            meta.setLodestone(location);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', rawName));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(plugin.getMarkerCompassKey(), PersistentDataType.BYTE, (byte) 1);
            compass.setItemMeta(meta);
        } else if (rawMeta != null) {
            rawMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', rawName));
            rawMeta.setLore(lore);
            rawMeta.getPersistentDataContainer().set(plugin.getMarkerCompassKey(), PersistentDataType.BYTE, (byte) 1);
            compass.setItemMeta(rawMeta);
        }

        return compass;
    }
}
