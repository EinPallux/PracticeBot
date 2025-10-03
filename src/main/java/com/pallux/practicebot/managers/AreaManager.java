package com.pallux.practicebot.managers;

import com.pallux.practicebot.PracticeBot;
import com.pallux.practicebot.bot.PracticeBotEntity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AreaManager {

    private final PracticeBot plugin;
    private final Map<String, PracticeArea> areas = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos1Map = new HashMap<>();
    private final Map<UUID, Location> pos2Map = new HashMap<>();
    private BukkitTask managementTask;

    public AreaManager(PracticeBot plugin) {
        this.plugin = plugin;
        loadAreas();
        // Don't start management immediately - let cleanup happen first
    }

    public void startManagement() {
        if (managementTask != null) {
            managementTask.cancel();
        }

        plugin.getLogger().info("Starting bot management for " + areas.size() + " areas...");

        managementTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (PracticeArea area : areas.values()) {
                    if (area.isEnabled()) {
                        area.manageBots();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 100L); // Start after 1 second, check every 5 seconds

        plugin.getLogger().info("Bot management task started successfully!");
    }

    public void shutdown() {
        if (managementTask != null) {
            managementTask.cancel();
            managementTask = null;
        }
        for (PracticeArea area : areas.values()) {
            area.despawnAllBots();
        }
        areas.clear();
    }

    public void reload() {
        shutdown();
        loadAreas();
        startManagement();
    }

    public void loadAreas() {
        areas.clear();
        ConfigurationSection areasSection = plugin.getConfigManager().getMainConfig().getConfigurationSection("areas");
        if (areasSection == null) {
            plugin.getLogger().info("No areas section found in config.yml");
            return;
        }

        for (String name : areasSection.getKeys(false)) {
            try {
                String path = "areas." + name;

                // Get the world name as a string
                Object worldObj = plugin.getConfigManager().getMainConfig().get(path + ".world");
                String worldName = null;

                if (worldObj instanceof String) {
                    worldName = (String) worldObj;
                } else if (worldObj != null) {
                    worldName = worldObj.toString();
                }

                plugin.getLogger().info("Loading area '" + name + "' - World value: " + worldName);

                if (worldName == null || worldName.isEmpty()) {
                    plugin.getLogger().warning("Configuration error: 'world' is missing or invalid for area '" + name + "'. Skipping.");
                    continue;
                }

                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World '" + worldName + "' for area '" + name + "' not found or not loaded. Skipping.");
                    continue;
                }

                // Load locations using getLocation which handles the serialized format
                Location min = plugin.getConfigManager().getMainConfig().getLocation(path + ".min");
                Location max = plugin.getConfigManager().getMainConfig().getLocation(path + ".max");

                if (min == null || max == null) {
                    plugin.getLogger().warning("Configuration error: 'min' or 'max' location is missing for area '" + name + "'. Skipping.");
                    continue;
                }

                // Ensure locations have the correct world (in case it wasn't set properly)
                if (min.getWorld() == null) {
                    min.setWorld(world);
                }
                if (max.getWorld() == null) {
                    max.setWorld(world);
                }

                int botCount = plugin.getConfigManager().getMainConfig().getInt(path + ".bot-count", 0);
                boolean enabled = plugin.getConfigManager().getMainConfig().getBoolean(path + ".enabled", false);

                PracticeArea area = new PracticeArea(name, min, max, botCount, enabled);
                areas.put(name.toLowerCase(), area);
                plugin.getLogger().info("Successfully loaded area: " + name + " (World: " + worldName + ", Bots: " + botCount + ", Enabled: " + enabled + ")");

            } catch (Exception e) {
                plugin.getLogger().severe("Error loading area '" + name + "': " + e.getMessage());
                e.printStackTrace();
            }
        }
        plugin.getLogger().info("Loaded " + areas.size() + " practice area(s).");
    }

    public void saveArea(PracticeArea area) {
        String path = "areas." + area.getName();

        // Save world as a STRING, not as part of the location
        plugin.getConfigManager().getMainConfig().set(path + ".world", area.getWorld().getName());

        // Save locations
        plugin.getConfigManager().getMainConfig().set(path + ".min", area.getMin());
        plugin.getConfigManager().getMainConfig().set(path + ".max", area.getMax());
        plugin.getConfigManager().getMainConfig().set(path + ".bot-count", area.getBotCount());
        plugin.getConfigManager().getMainConfig().set(path + ".enabled", area.isEnabled());

        plugin.getConfigManager().saveAll();

        plugin.getLogger().info("Saved area: " + area.getName() + " (World: " + area.getWorld().getName() + ")");
    }

    public void deleteArea(String name) {
        PracticeArea area = getArea(name);
        if (area != null) {
            area.despawnAllBots();
            areas.remove(name.toLowerCase());
            plugin.getConfigManager().getMainConfig().set("areas." + name, null);
            plugin.getConfigManager().saveAll();
        }
    }

    public boolean createArea(String name, Location pos1, Location pos2, int botCount) {
        if (areas.containsKey(name.toLowerCase())) return false;

        Location min = new Location(pos1.getWorld(),
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()));

        Location max = new Location(pos1.getWorld(),
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ()));

        PracticeArea area = new PracticeArea(name, min, max, botCount, true);
        areas.put(name.toLowerCase(), area);
        saveArea(area);
        return true;
    }

    public PracticeArea getArea(String name) {
        return areas.get(name.toLowerCase());
    }

    public Collection<PracticeArea> getAreas() {
        return areas.values();
    }

    public Set<String> getAreaNames() {
        Set<String> names = new HashSet<>();
        for (PracticeArea area : areas.values()) {
            names.add(area.getName());
        }
        return names;
    }

    public void setPosition1(Player p, Location l) { pos1Map.put(p.getUniqueId(), l); }
    public void setPosition2(Player p, Location l) { pos2Map.put(p.getUniqueId(), l); }
    public Location getPosition1(Player p) { return pos1Map.get(p.getUniqueId()); }
    public Location getPosition2(Player p) { return pos2Map.get(p.getUniqueId()); }

    public Material getSelectionTool() {
        try {
            return Material.valueOf(plugin.getConfigManager().getString("area-settings.selection-tool", "STONE_AXE").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid selection tool in config.yml. Defaulting to STONE_AXE.");
            return Material.STONE_AXE;
        }
    }

    public class PracticeArea {
        private final String name;
        private final Location min;
        private final Location max;
        private int botCount;
        private boolean enabled;
        private final List<PracticeBotEntity> activeBots = Collections.synchronizedList(new ArrayList<>());
        private boolean isSpawning = false;

        public PracticeArea(String name, Location min, Location max, int botCount, boolean enabled) {
            this.name = name;
            this.min = min;
            this.max = max;
            this.botCount = botCount;
            this.enabled = enabled;
        }

        void manageBots() {
            if (isSpawning) return;

            // Clean up dead/invalid bots
            activeBots.removeIf(bot -> bot.getNpc() == null || !bot.getNpc().isSpawned());

            int needed = botCount - activeBots.size();

            if (needed > 0) {
                spawnBots(needed);
            }
        }

        public void spawnBots(int amount) {
            if (amount <= 0) return;

            isSpawning = true;
            long spawnDelay = plugin.getConfigManager().getInt("performance.spawn-delay-ticks", 2);

            plugin.getLogger().info("Spawning " + amount + " bots in area '" + name + "'...");

            new BukkitRunnable() {
                private int count = 0;
                @Override
                public void run() {
                    if (count >= amount || !enabled) {
                        isSpawning = false;
                        this.cancel();
                        if (count > 0) {
                            plugin.getLogger().info("Finished spawning " + count + " bots in area '" + name + "'");
                        }
                        return;
                    }

                    List<String> kitNames = new ArrayList<>(plugin.getKitManager().getKitNames());
                    if (kitNames.isEmpty()) {
                        plugin.getLogger().warning("Cannot spawn bot in area '" + name + "': No kits available.");
                        isSpawning = false;
                        this.cancel();
                        return;
                    }
                    String randomKit = kitNames.get(new Random().nextInt(kitNames.size()));

                    PracticeBotEntity bot = new PracticeBotEntity(plugin, randomKit, this_PracticeArea());
                    if (bot.spawn()) {
                        activeBots.add(bot);
                        plugin.getBotManager().addBot(bot);
                    } else {
                        plugin.getLogger().warning("Failed to spawn bot in area '" + name + "'");
                    }
                    count++;
                }
            }.runTaskTimer(plugin, 0L, spawnDelay);
        }

        public void despawnAllBots() {
            synchronized (activeBots) {
                Iterator<PracticeBotEntity> iterator = activeBots.iterator();
                while (iterator.hasNext()) {
                    PracticeBotEntity bot = iterator.next();
                    plugin.getBotManager().removeBot(bot);
                    bot.despawn();
                    iterator.remove();
                }
            }
        }

        private AreaManager.PracticeArea this_PracticeArea() {
            return this;
        }

        public Location getRandomLocation() {
            double x = min.getX() + (max.getX() - min.getX()) * new Random().nextDouble();
            double y = min.getY() + (max.getY() - min.getY()) * new Random().nextDouble();
            double z = min.getZ() + (max.getZ() - min.getZ()) * new Random().nextDouble();
            return new Location(min.getWorld(), x, y, z);
        }

        public boolean contains(Location location) {
            if (location.getWorld() == null || min.getWorld() == null || !location.getWorld().equals(min.getWorld())) {
                return false;
            }
            return location.getX() >= min.getX() && location.getX() <= max.getX() &&
                    location.getY() >= min.getY() && location.getY() <= max.getY() &&
                    location.getZ() >= min.getZ() && location.getZ() <= max.getZ();
        }

        public String getName() { return name; }
        public World getWorld() { return min.getWorld(); }
        public Location getMin() { return min; }
        public Location getMax() { return max; }
        public int getBotCount() { return botCount; }
        public boolean isEnabled() { return enabled; }
        public List<PracticeBotEntity> getActiveBots() { return activeBots; }

        public void setBotCount(int count) {
            this.botCount = Math.max(0, count);
            if (this.botCount < activeBots.size()) {
                int toRemove = activeBots.size() - this.botCount;
                for (int i = 0; i < toRemove; i++) {
                    if (!activeBots.isEmpty()) {
                        PracticeBotEntity bot = activeBots.remove(0);
                        plugin.getBotManager().removeBot(bot);
                        bot.despawn();
                    }
                }
            }
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
            if (!enabled) {
                despawnAllBots();
            }
        }
    }
}