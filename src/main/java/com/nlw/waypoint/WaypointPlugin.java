package com.nlw.waypoint;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class WaypointPlugin extends JavaPlugin {

    private final Map<String, Location> waypoints = new HashMap<>();
    private final Map<String, TextDisplay> labels = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("WaypointPlugin enabled!");
        loadWaypoints();
        startBeamTask();
    }

    @Override
    public void onDisable() {
        for (TextDisplay display : labels.values()) {
            if (display != null) display.remove();
        }
        saveWaypoints();
        getLogger().info("WaypointPlugin disabled!");
    }

    private void spawnLabel(String name, Location loc) {
        Location labelLoc = loc.clone().add(0, 20, 0);
        TextDisplay display = (TextDisplay) loc.getWorld().spawnEntity(labelLoc, EntityType.TEXT_DISPLAY);
        display.setText(name);
        display.setBillboard(display.getBillboard().CENTER);
        display.setSeeThrough(false);
        display.setViewRange(128);
        labels.put(name, display);
    }

    private void startBeamTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, Location> entry : waypoints.entrySet()) {
                    Location loc = entry.getValue();
                    for (int y = 0; y <= 150; y += 2) {
                        loc.getWorld().spawnParticle(
                            Particle.END_ROD,
                            loc.getX(), loc.getY() + y, loc.getZ(),
                            2, 0.1, 0, 0.1, 0
                        );
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private void saveWaypoints() {
        FileConfiguration config = getConfig();
        config.set("waypoints", null);
        for (Map.Entry<String, Location> entry : waypoints.entrySet()) {
            String key = "waypoints." + entry.getKey();
            config.set(key + ".world", entry.getValue().getWorld().getName());
            config.set(key + ".x", entry.getValue().getX());
            config.set(key + ".y", entry.getValue().getY());
            config.set(key + ".z", entry.getValue().getZ());
        }
        saveConfig();
    }

    private void loadWaypoints() {
        FileConfiguration config = getConfig();
        if (!config.contains("waypoints")) return;
        for (String name : config.getConfigurationSection("waypoints").getKeys(false)) {
            String path = "waypoints." + name;
            World world = Bukkit.getWorld(config.getString(path + ".world"));
            if (world == null) continue;
            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");
            Location loc = new Location(world, x, y, z);
            waypoints.put(name, loc);
            spawnLabel(name, loc);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) return true;

        String name = args[0];

        switch (cmd.getName().toLowerCase()) {
            case "setwaypoint" -> {
                Location loc = player.getLocation();
                waypoints.put(name, loc);
                spawnLabel(name, loc);
                saveWaypoints();
            }
            case "delwaypoint" -> {
                waypoints.remove(name);
                TextDisplay old = labels.remove(name);
                if (old != null) old.remove();
                saveWaypoints();
            }
        }

        return true;
    }
}
