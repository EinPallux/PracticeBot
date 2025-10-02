package com.pallux.practicebot.managers;

import com.pallux.practicebot.PracticeBot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages practice areas where bots can spawn and roam
 */
public class AreaManager {

    private final PracticeBot plugin;
    private final Map<UUID, Location> pos1Map;
    private final Map<UUID, Location> pos2Map;
    private final Map<String, PracticeArea> areas;
    private Material selectionTool;

    public AreaManager(PracticeBot plugin) {
        this.plugin = plugin;
        this.pos1Map = new HashMap<>();
        this.pos2Map = new HashMap<>();
        this.areas = new HashMap<>();
        loadSelectionTool();
        loadAreas();
    }

    /**
     * Load selection tool from config
     */
    private void loadSelectionTool() {
        String toolName = plugin.getConfigManager().getString("area-settings.selection-tool", "STONE_AXE");
        try {
            this.selectionTool = Material.valueOf(toolName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid selection tool: " + toolName + ", using STONE_AXE");
            this.selectionTool = Material.STONE_AXE;
        }
    }

    /**
     * Load areas from bots.yml
     */
    private void loadAreas() {
        FileConfiguration config = plugin.getConfigManager().getBotsConfig();
        ConfigurationSection areasSection = config.getConfigurationSection("areas");

        if (areasSection == null) {
            return;
        }

        for (String name : areasSection.getKeys(false)) {
            String path = "areas." + name + ".";

            String worldName = config.getString(path + "world");
            World world = plugin.getServer().getWorld(worldName);

            if (world == null) {
                plugin.getLogger().warning("World " + worldName + " not found for area " + name);
                continue;
            }

            Location min = new Location(world,
                    config.getDouble(path + "min.x"),
                    config.getDouble(path + "min.y"),
                    config.getDouble(path + "min.z")
            );

            Location max = new Location(world,
                    config.getDouble(path + "max.x"),
                    config.getDouble(path + "max.y"),
                    config.getDouble(path + "max.z")
            );

            PracticeArea area = new PracticeArea(name, min, max);
            areas.put(name, area);
        }

        plugin.getLogger().info("Loaded " + areas.size() + " practice area(s)");
    }

    /**
     * Save areas to bots.yml
     */
    public void saveAreas() {
        FileConfiguration config = plugin.getConfigManager().getBotsConfig();
        config.set("areas", null); // Clear existing

        for (PracticeArea area : areas.values()) {
            String path = "areas." + area.getName() + ".";
            config.set(path + "world", area.getWorld().getName());
            config.set(path + "min.x", area.getMin().getX());
            config.set(path + "min.y", area.getMin().getY());
            config.set(path + "min.z", area.getMin().getZ());
            config.set(path + "max.x", area.getMax().getX());
            config.set(path + "max.y", area.getMax().getY());
            config.set(path + "max.z", area.getMax().getZ());
        }

        plugin.getConfigManager().saveConfig("bots");
    }

    /**
     * Set position 1 for a player
     */
    public void setPosition1(Player player, Location location) {
        pos1Map.put(player.getUniqueId(), location);

        if (plugin.getConfigManager().getBoolean("area-settings.show-selection-particles", true)) {
            String particleName = plugin.getConfigManager().getString("area-settings.pos1-particle", "VILLAGER_HAPPY");
            showParticle(location, particleName);
        }
    }

    /**
     * Set position 2 for a player
     */
    public void setPosition2(Player player, Location location) {
        pos2Map.put(player.getUniqueId(), location);

        if (plugin.getConfigManager().getBoolean("area-settings.show-selection-particles", true)) {
            String particleName = plugin.getConfigManager().getString("area-settings.pos2-particle", "FLAME");
            showParticle(location, particleName);
        }
    }

    /**
     * Show particle at location
     */
    private void showParticle(Location location, String particleName) {
        try {
            Particle particle = Particle.valueOf(particleName);
            location.getWorld().spawnParticle(particle, location, 10);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle type: " + particleName);
        }
    }

    /**
     * Get position 1 for a player
     */
    public Location getPosition1(Player player) {
        return pos1Map.get(player.getUniqueId());
    }

    /**
     * Get position 2 for a player
     */
    public Location getPosition2(Player player) {
        return pos2Map.get(player.getUniqueId());
    }

    /**
     * Check if player has both positions set
     */
    public boolean hasBothPositions(Player player) {
        return pos1Map.containsKey(player.getUniqueId()) && pos2Map.containsKey(player.getUniqueId());
    }

    /**
     * Create a new practice area
     */
    public boolean createArea(String name, Location pos1, Location pos2) {
        if (areas.containsKey(name)) {
            return false;
        }

        // Validate same world
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            return false;
        }

        // Create normalized area
        Location min = new Location(pos1.getWorld(),
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ())
        );

        Location max = new Location(pos1.getWorld(),
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );

        PracticeArea area = new PracticeArea(name, min, max);
        areas.put(name, area);
        saveAreas();

        return true;
    }

    /**
     * Delete an area
     */
    public boolean deleteArea(String name) {
        if (!areas.containsKey(name)) {
            return false;
        }

        areas.remove(name);
        saveAreas();
        return true;
    }

    /**
     * Get an area by name
     */
    public PracticeArea getArea(String name) {
        return areas.get(name);
    }

    /**
     * Get all areas
     */
    public Collection<PracticeArea> getAreas() {
        return areas.values();
    }

    /**
     * Get area names
     */
    public Set<String> getAreaNames() {
        return areas.keySet();
    }

    /**
     * Get the selection tool material
     */
    public Material getSelectionTool() {
        return selectionTool;
    }

    /**
     * Clear player's selection
     */
    public void clearSelection(Player player) {
        pos1Map.remove(player.getUniqueId());
        pos2Map.remove(player.getUniqueId());
    }

    /**
     * Reload area manager
     */
    public void reload() {
        loadSelectionTool();
        areas.clear();
        loadAreas();
    }

    /**
     * Inner class representing a practice area
     */
    public static class PracticeArea {
        private final String name;
        private final Location min;
        private final Location max;

        public PracticeArea(String name, Location min, Location max) {
            this.name = name;
            this.min = min;
            this.max = max;
        }

        public String getName() {
            return name;
        }

        public Location getMin() {
            return min;
        }

        public Location getMax() {
            return max;
        }

        public World getWorld() {
            return min.getWorld();
        }

        public boolean contains(Location location) {
            if (!location.getWorld().equals(min.getWorld())) {
                return false;
            }

            return location.getX() >= min.getX() && location.getX() <= max.getX()
                    && location.getY() >= min.getY() && location.getY() <= max.getY()
                    && location.getZ() >= min.getZ() && location.getZ() <= max.getZ();
        }

        public Location getRandomLocation() {
            Random random = new Random();

            double x = min.getX() + (max.getX() - min.getX()) * random.nextDouble();
            double y = min.getY() + (max.getY() - min.getY()) * random.nextDouble();
            double z = min.getZ() + (max.getZ() - min.getZ()) * random.nextDouble();

            return new Location(min.getWorld(), x, y, z);
        }

        public double getSize() {
            return (max.getX() - min.getX()) * (max.getY() - min.getY()) * (max.getZ() - min.getZ());
        }
    }
}