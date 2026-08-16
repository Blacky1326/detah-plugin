package de.deathchest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeathListener implements Listener {

    private final DeathChestPlugin plugin;

    public DeathListener(DeathChestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        int exp = event.getDroppedExp();

        if (drops.isEmpty() && exp <= 0) {
            return; // nichts zu retten
        }

        Location deathLocation = player.getLocation();
        Location safeLocation = ChestUtil.findSafeLocation(deathLocation);
        Location chestLocation = ChestUtil.placeDeathChests(plugin, safeLocation, player.getUniqueId(), drops, exp);

        // Normales Droppen verhindern - alles liegt jetzt sicher in der Truhe.
        event.getDrops().clear();
        event.setDroppedExp(0);

        plugin.getPendingCompassGrant().put(player.getUniqueId(), chestLocation);

        String message = plugin.msg("death")
                .replace("%x%", String.valueOf(chestLocation.getBlockX()))
                .replace("%y%", String.valueOf(chestLocation.getBlockY()))
                .replace("%z%", String.valueOf(chestLocation.getBlockZ()))
                .replace("%world%", chestLocation.getWorld() != null ? chestLocation.getWorld().getName() : "?");
        player.sendMessage(message);

        if (plugin.getConfig().getBoolean("broadcast-death", false)) {
            String broadcast = plugin.msg("death-broadcast").replace("%player%", player.getName());
            Bukkit.broadcastMessage(broadcast);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location chestLocation = plugin.getPendingCompassGrant().remove(player.getUniqueId());
        if (chestLocation == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("give-marker-on-respawn", true)) {
            return;
        }

        // Einen Tick warten, damit das frische Inventar nach dem Respawn schon existiert.
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack compass = ChestUtil.createMarkerCompass(plugin, chestLocation);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(compass);
            if (!leftover.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), compass);
            }
            player.sendMessage(plugin.msg("marker-given"));
        });
    }

    // Schutz: nur der Besitzer (oder jemand mit Bypass-Recht) darf die Todes-Truhe oeffnen.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Chest chest)) return;

        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        if (!pdc.has(plugin.getDeathChestKey(), PersistentDataType.BYTE)) return;
        if (!plugin.getConfig().getBoolean("protect-chest", true)) return;

        String ownerString = pdc.get(plugin.getDeathChestOwnerKey(), PersistentDataType.STRING);
        if (ownerString == null) return;

        UUID owner = UUID.fromString(ownerString);
        Player player = event.getPlayer();

        if (!player.getUniqueId().equals(owner) && !player.hasPermission("deathchest.bypass")) {
            event.setCancelled(true);
            player.sendMessage(plugin.msg("not-your-chest"));
        }
    }

    // XP zurueckgeben, sobald die Truhe zum ersten Mal geoeffnet wird.
    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest chest)) return;

        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        if (!pdc.has(plugin.getDeathChestKey(), PersistentDataType.BYTE)) return;

        Integer storedExp = pdc.get(plugin.getDeathChestExpKey(), PersistentDataType.INTEGER);
        if (storedExp != null && storedExp > 0 && event.getPlayer() instanceof Player player) {
            int percentage = Math.max(0, Math.min(100, plugin.getConfig().getInt("xp-return-percentage", 100)));
            int expToGive = (storedExp * percentage) / 100;

            if (expToGive > 0) {
                player.giveExp(expToGive);
                player.sendMessage(plugin.msg("xp-restored").replace("%xp%", String.valueOf(expToGive)));
            }
            pdc.set(plugin.getDeathChestExpKey(), PersistentDataType.INTEGER, 0);
            chest.update(true, false);
        }
    }

    // Leere Todes-Truhen automatisch entfernen.
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!plugin.getConfig().getBoolean("remove-when-empty", true)) return;
        if (!(event.getInventory().getHolder() instanceof Chest chest)) return;

        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        if (!pdc.has(plugin.getDeathChestKey(), PersistentDataType.BYTE)) return;
        if (!event.getInventory().isEmpty()) return;

        String ownerString = pdc.get(plugin.getDeathChestOwnerKey(), PersistentDataType.STRING);
        chest.getBlock().setType(Material.AIR);

        if (ownerString != null) {
            UUID owner = UUID.fromString(ownerString);
            Location chestLoc = chest.getBlock().getLocation();
            Location tracked = plugin.getLastChestLocation().get(owner);
            if (tracked != null && tracked.equals(chestLoc)) {
                plugin.getLastChestLocation().remove(owner);
            }
        }
    }
}
