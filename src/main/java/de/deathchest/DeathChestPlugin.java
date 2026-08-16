package de.deathchest;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DeathChestPlugin extends JavaPlugin {

    // Kompass-Marker soll direkt nach dem naechsten Respawn vergeben werden.
    private final Map<UUID, Location> pendingCompassGrant = new HashMap<>();

    // Letzte bekannte (noch nicht geleerte) Todes-Truhe eines Spielers -> fuer /deathchest.
    private final Map<UUID, Location> lastChestLocation = new HashMap<>();

    private NamespacedKey deathChestKey;
    private NamespacedKey deathChestOwnerKey;
    private NamespacedKey deathChestExpKey;
    private NamespacedKey markerCompassKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        deathChestKey = new NamespacedKey(this, "death_chest");
        deathChestOwnerKey = new NamespacedKey(this, "death_chest_owner");
        deathChestExpKey = new NamespacedKey(this, "death_chest_exp");
        markerCompassKey = new NamespacedKey(this, "death_marker_compass");

        getServer().getPluginManager().registerEvents(new DeathListener(this), this);

        getLogger().info("DeathChest wurde aktiviert.");
    }

    @Override
    public void onDisable() {
        getLogger().info("DeathChest wurde deaktiviert.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("deathchest")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler koennen diesen Befehl nutzen.");
            return true;
        }

        Location location = lastChestLocation.get(player.getUniqueId());
        if (location == null || location.getWorld() == null) {
            player.sendMessage(msg("no-chest"));
            return true;
        }

        ItemStack compass = ChestUtil.createMarkerCompass(this, location);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(compass);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), compass);
        }
        player.sendMessage(msg("marker-given"));
        return true;
    }

    public NamespacedKey getDeathChestKey() {
        return deathChestKey;
    }

    public NamespacedKey getDeathChestOwnerKey() {
        return deathChestOwnerKey;
    }

    public NamespacedKey getDeathChestExpKey() {
        return deathChestExpKey;
    }

    public NamespacedKey getMarkerCompassKey() {
        return markerCompassKey;
    }

    public Map<UUID, Location> getPendingCompassGrant() {
        return pendingCompassGrant;
    }

    public Map<UUID, Location> getLastChestLocation() {
        return lastChestLocation;
    }

    public String msg(String path) {
        FileConfiguration config = getConfig();
        String raw = config.getString("messages." + path, "");
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
