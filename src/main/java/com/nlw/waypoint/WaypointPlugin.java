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
    private final Map<String, List<TextDisplay>> beamDisplays = new HashMap<>();
    private final Map<UUID, Map<String, TextDisplay>> playerLabels = new HashMap<>();

    private static final double HIDE_DISTANCE = 10.0;
    private static final double LABEL_OFFSET = 8.0;

    @Override
    public void onEnable() {
        getLogger().info("BetterWaypoint enabled!");
        loadWaypoints();
        startParticleTask();
        startPlayerLabelTask();
    }

    @Override
    public void onDisable() {
        for (List<TextDisplay> list : beamDisplays.values())
            for (TextDisplay d : list) if (d != null) d.remove();
        for (Map<String, TextDisplay> map : playerLabels.values())
            for (TextDisplay d : map.values()) if (d != null) d.remove();
        saveWaypoints();
        getLogger().info("BetterWaypoint disabled!");
    }

    private void spawnBeam(String name, Location loc) {
        List<TextDisplay> list = new ArrayList<>();

        Location pos = loc.clone().add(0, 4, 0);
        TextDisplay display = (TextDisplay) loc.getWorld().spawnEntity(pos, EntityType.TEXT_DISPLAY);
        display.setText("§f-" + name + "-");
        display.setBillboard(display.getBillboard().CENTER);
        display.setSeeThrough(true);
        display.setViewRange(128);
        org.bukkit.util.Transformation t = new org.bukkit.util.Transformation(
            new org.joml.Vector3f(0, 0, 0),
            new org.joml.AxisAngle4f(0, 0, 0, 1),
            new org.joml.Vector3f(1.5f, 1.5f, 1.5f),
            new org.joml.AxisAngle4f(0, 0, 0, 1)
        );
        display.setTransformation(t);
        list.add(display);

        beamDisplays.put(name, list);
    }

    private void startParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, Location> entry : waypoints.entrySet()) {
                    Location loc = entry.getValue();
                    World world = loc.getWorld();

                    world.spawnParticle(
                        Particle.END_ROD,
                        loc.getX(), loc.getY() + 4.8, loc.getZ(),
                        3, 0.05, 0, 0.05, 0
                    );
                    world.spawnParticle(
                        Particle.END_ROD,
                        loc.getX(), loc.getY() + 3.2, loc.getZ(),
                        3, 0.05, 0, 0.05, 0
                    );
                }
            }
        }.runTaskTimer(this, 0L, 5L);
    }

    private void removeBeam(String name) {
        List<TextDisplay> list = beamDisplays.remove(name);
        if (list != null) for (TextDisplay d : list) if (d != null) d.remove();
    }

    private void cleanAllDisplays(Player player) {
        for (Entity e : player.getWorld().getEntities()) {
            if (e instanceof TextDisplay) e.remove();
        }
        for (List<TextDisplay> list : beamDisplays.values())
            for (TextDisplay d : list) if (d != null) d.remove();
        beamDisplays.clear();

        for (Map<String, TextDisplay> map : playerLabels.values())
            for (TextDisplay d : map.values()) if (d != null) d.remove();
        playerLabels.clear();

        for (Map.Entry<String, Location> entry : waypoints.entrySet())
            spawnBeam(entry.getKey(), entry.getValue());
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
            spawnBeam(name, loc);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (cmd.getName().equalsIgnoreCase("cleanwaypoints")) {
            cleanAllDisplays(player);
            return true;
        }

        if (args.length == 0) return true;

        String name = args[0];

        switch (cmd.getName().toLowerCase()) {
            case "setwaypoint" -> {
                Location loc = player.getLocation();
                waypoints.put(name, loc);
                spawnBeam(name, loc);
                saveWaypoints();
            }
            case "delwaypoint" -> {
                waypoints.remove(name);
                removeBeam(name);
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
