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
        startManagementTask();
    }

    public void shutdown() {
        if (managementTask != null) {
            managementTask.cancel();
        }
        for (PracticeArea area : areas.values()) {
            area.despawnAllBots();
        }
        areas.clear();
    }

    private void startManagementTask() {
        managementTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (PracticeArea area : areas.values()) {
                    if (area.isEnabled()) {
                        area.manageBots();
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 100L); // Check every 5 seconds
    }

    public void loadAreas() {
        areas.clear();
        ConfigurationSection areasSection = plugin.getConfigManager().getMainConfig().getConfigurationSection("areas");
        if (areasSection == null) return;

        for (String name : areasSection.getKeys(false)) {
            String path = "areas." + name;
            String worldName = areasSection.getString(path + ".world");

            if (worldName == null || worldName.isEmpty()) {
                plugin.getLogger().warning("Configuration error: 'world' is missing for area '" + name + "'. Skipping.");
                continue;
            }

            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World '" + worldName + "' for area '" + name + "' not found or not loaded. Skipping.");
                continue;
            }

            Location min = areasSection.getLocation(path + ".min");
            Location max = areasSection.getLocation(path + ".max");
            int botCount = areasSection.getInt(path + ".bot-count", 0);
            boolean enabled = areasSection.getBoolean(path + ".enabled", false);

            if (min != null && max != null) {
                areas.put(name.toLowerCase(), new PracticeArea(name, min, max, botCount, enabled));
            } else {
                plugin.getLogger().warning("Configuration error: 'min' or 'max' location is missing for area '" + name + "'. Skipping.");
            }
        }
        plugin.getLogger().info("Loaded " + areas.size() + " practice areas.");
    }


    public void saveArea(PracticeArea area) {
        String path = "areas." + area.getName();
        plugin.getConfigManager().getMainConfig().set(path + ".world", area.getWorld().getName());
        plugin.getConfigManager().getMainConfig().set(path + ".min", area.getMin());
        plugin.getConfigManager().getMainConfig().set(path + ".max", area.getMax());
        plugin.getConfigManager().getMainConfig().set(path + ".bot-count", area.getBotCount());
        plugin.getConfigManager().getMainConfig().set(path + ".enabled", area.isEnabled());
        plugin.saveConfig();
    }

    public void deleteArea(String name) {
        PracticeArea area = getArea(name);
        if (area != null) {
            area.despawnAllBots();
            areas.remove(name.toLowerCase());
            plugin.getConfigManager().getMainConfig().set("areas." + name, null);
            plugin.saveConfig();
        }
    }

    public boolean createArea(String name, Location pos1, Location pos2, int botCount) {
        if (areas.containsKey(name.toLowerCase())) return false;

        Location min = new Location(pos1.getWorld(), Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
        Location max = new Location(pos1.getWorld(), Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));

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
        private int botCounter = 0;
        private boolean isSpawning = false;

        public PracticeArea(String name, Location min, Location max, int botCount, boolean enabled) {
            this.name = name;
            this.min = min;
            this.max = max;
            this.botCount = botCount;
            this.enabled = enabled;
        }

        void manageBots() {
            if (isSpawning) return; // Don't run if a spawning task is already active for this area

            activeBots.removeIf(bot -> bot.getNpc() == null || !bot.getNpc().isSpawned());

            int needed = botCount - activeBots.size();
            int globalMaxBots = plugin.getConfigManager().getInt("settings.max-bots", 50);
            int globalCurrentBots = plugin.getBotManager().getAllBots().size();
            int canSpawn = Math.min(needed, globalMaxBots - globalCurrentBots);

            if (canSpawn > 0) {
                spawnBots(canSpawn);
            }
        }

        public void spawnBots(int amount) {
            isSpawning = true;
            long spawnDelay = plugin.getConfigManager().getInt("performance.spawn-delay-ticks", 2);

            new BukkitRunnable() {
                private int count = 0;
                @Override
                public void run() {
                    if (count >= amount || !enabled) {
                        isSpawning = false;
                        this.cancel();
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
                    botCounter++;

                    PracticeBotEntity bot = new PracticeBotEntity(plugin, botCounter, randomKit, this_PracticeArea());
                    if (bot.spawn()) {
                        activeBots.add(bot);
                        plugin.getBotManager().addBot(bot);
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