package com.nlw.waypoint;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class WaypointPlugin extends JavaPlugin {

    private final Map<String, Location> waypoints = new HashMap<>();
    private final Map<String, TextDisplay> beamLabels = new HashMap<>();
    private final Map<UUID, Map<String, TextDisplay>> playerLabels = new HashMap<>();

    private static final double HIDE_DISTANCE = 10.0;
    private static final double LABEL_OFFSET = 8.0;

    @Override
    public void onEnable() {
        getLogger().info("WaypointPlugin enabled!");
        loadWaypoints();
        startBeamTask();
        startPlayerLabelTask();
    }

    @Override
    public void onDisable() {
        for (TextDisplay d : beamLabels.values()) if (d != null) d.remove();
        for (Map<String, TextDisplay> map : playerLabels.values())
            for (TextDisplay d : map.values()) if (d != null) d.remove();
        saveWaypoints();
        getLogger().info("WaypointPlugin disabled!");
    }

    private void spawnBeamLabel(String name, Location loc) {
        Location labelLoc = loc.clone().add(0, 25, 0);
        TextDisplay display = (TextDisplay) loc.getWorld().spawnEntity(labelLoc, EntityType.TEXT_DISPLAY);
        display.setText(name);
        display.setBillboard(display.getBillboard().CENTER);
        display.setSeeThrough(false);
        display.setViewRange(64);
        org.bukkit.util.Transformation t = new org.bukkit.util.Transformation(
            new org.joml.Vector3f(0, 0, 0),
            new org.joml.AxisAngle4f(0, 0, 0, 1),
            new org.joml.Vector3f(5, 5, 5),
            new org.joml.AxisAngle4f(0, 0, 0, 1)
        );
        display.setTransformation(t);
        beamLabels.put(name, display);
    }

    private void startBeamTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, Location> entry : waypoints.entrySet()) {
                    Location loc = entry.getValue();
                    World world = loc.getWorld();
                    int maxHeight = world.getEnvironment() == World.Environment.NETHER ? 128 : 300;
                    for (double y = 0; y <= maxHeight; y += 0.5) {
                        world.spawnParticle(
                            Particle.END_ROD,
                            loc.getX(), loc.getY() + y, loc.getZ(),
                            1, 0.0, 0.0, 0.0, 0
                        );
                    }
                }
            }
        }.runTaskTimer(this, 0L, 3L);
    }

    private void startPlayerLabelTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Map<String, TextDisplay> labels = playerLabels.computeIfAbsent(
                        player.getUniqueId(), k -> new HashMap<>()
                    );

                    for (Map.Entry<String, Location> entry : waypoints.entrySet()) {
                        String name = entry.getKey();
                        Location waypointLoc = entry.getValue();

                        if (!waypointLoc.getWorld().equals(player.getWorld())) {
                            TextDisplay d = labels.get(name);
                            if (d != null) { d.remove(); labels.remove(name); }
                            continue;
                        }

                        double distance = player.getLocation().distance(waypointLoc);

                        if (distance <= HIDE_DISTANCE) {
                            TextDisplay d = labels.get(name);
                            if (d != null) { d.remove(); labels.remove(name); }
                            continue;
                        }

                        String distanceText = "§f" + name + "\n§e" + (int) distance + "m";

                        Vector dir = waypointLoc.toVector()
                            .subtract(player.getLocation().toVector())
                            .normalize()
                            .multiply(LABEL_OFFSET);

                        Location labelLoc = player.getLocation().clone().add(dir).add(0, 1.8, 0);

                        TextDisplay display = labels.get(name);

                        if (display == null || display.isDead()) {
                            display = (TextDisplay) player.getWorld().spawnEntity(labelLoc, EntityType.TEXT_DISPLAY);
                            display.setBillboard(display.getBillboard().CENTER);
                            display.setSeeThrough(true);
                            display.setViewRange(20);
                            org.bukkit.util.Transformation t = new org.bukkit.util.Transformation(
                                new org.joml.Vector3f(0, 0, 0),
                                new org.joml.AxisAngle4f(0, 0, 0, 1),
                                new org.joml.Vector3f(2, 2, 2),
                                new org.joml.AxisAngle4f(0, 0, 0, 1)
                            );
                            display.setTransformation(t);
                            labels.put(name, display);
                        }

                        display.setText(distanceText);
                        display.teleport(labelLoc);
                    }

                    labels.keySet().removeIf(name -> {
                        if (!waypoints.containsKey(name)) {
                            TextDisplay d = labels.get(name);
                            if (d != null) d.remove();
                            return true;
                        }
                        return false;
                    });
                }
            }
        }.runTaskTimer(this, 0L, 2L);
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
            spawnBeamLabel(name, loc);
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
                spawnBeamLabel(name, loc);
                saveWaypoints();
            }
            case "delwaypoint" -> {
                waypoints.remove(name);
                TextDisplay old = beamLabels.remove(name);
                if (old != null) old.remove();
                for (Map<String, TextDisplay> map : playerLabels.values()) {
                    TextDisplay d = map.remove(name);
                    if (d != null) d.remove();
                }
                saveWaypoints();
            }
        }

        return true;
    }
}
