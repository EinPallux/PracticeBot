package com.pallux.practicebot.managers;

import com.pallux.practicebot.PracticeBot;
import com.pallux.practicebot.bot.PracticeBotEntity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all practice bots
 */
public class BotManager implements Listener {

    private final PracticeBot plugin;
    private final Map<String, PracticeBotEntity> activeBots;
    private int botCounter;

    public BotManager(PracticeBot plugin) {
        this.plugin = plugin;
        this.activeBots = new ConcurrentHashMap<>();
        this.botCounter = 0;

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Spawn bots
     */
    public int spawnBots(int amount, String kitName, AreaManager.PracticeArea area) {
        // Check max bots limit
        int maxBots = plugin.getConfigManager().getInt("settings.max-bots", 50);
        int currentBots = activeBots.size();

        if (currentBots >= maxBots) {
            return 0;
        }

        // Adjust amount if it would exceed limit
        amount = Math.min(amount, maxBots - currentBots);

        int spawned = 0;

        for (int i = 0; i < amount; i++) {
            botCounter++;
            String botName = "Practice Bot " + botCounter;

            PracticeBotEntity bot = new PracticeBotEntity(plugin, botCounter, kitName, area);

            // Apply difficulty modifiers from profile
            applyProfileModifiers(bot, kitName);

            if (bot.spawn()) {
                activeBots.put(botName, bot);
                spawned++;
            }
        }

        return spawned;
    }

    /**
     * Apply profile modifiers to bot
     */
    private void applyProfileModifiers(PracticeBotEntity bot, String kitName) {
        ConfigurationSection profilesSection = plugin.getConfigManager().getBotsConfig().getConfigurationSection("profiles");

        if (profilesSection == null) {
            return;
        }

        // Find matching profile
        String profileName = null;
        for (String profile : profilesSection.getKeys(false)) {
            String profileKit = profilesSection.getString(profile + ".kit");
            if (kitName.equalsIgnoreCase(profileKit)) {
                profileName = profile;
                break;
            }
        }

        if (profileName == null) {
            // Use default profile
            profileName = plugin.getConfigManager().getBotsConfig().getString("default-profile", "medium");
        }

        String path = "profiles." + profileName + ".";
        double healthMult = plugin.getConfigManager().getBotsConfig().getDouble(path + "health-multiplier", 1.0);
        double damageMult = plugin.getConfigManager().getBotsConfig().getDouble(path + "damage-multiplier", 1.0);

        bot.setMultipliers(healthMult, damageMult);
    }

    /**
     * Despawn a specific bot
     */
    public boolean despawnBot(String botName) {
        PracticeBotEntity bot = activeBots.get(botName);

        if (bot == null) {
            // Try to find by partial name
            for (Map.Entry<String, PracticeBotEntity> entry : activeBots.entrySet()) {
                if (entry.getKey().toLowerCase().contains(botName.toLowerCase())) {
                    bot = entry.getValue();
                    botName = entry.getKey();
                    break;
                }
            }
        }

        if (bot != null) {
            bot.despawn();
            activeBots.remove(botName);
            return true;
        }

        return false;
    }

    /**
     * Despawn all bots
     */
    public int despawnAll() {
        int count = activeBots.size();

        for (PracticeBotEntity bot : activeBots.values()) {
            bot.despawn();
        }

        activeBots.clear();
        return count;
    }

    /**
     * Get a bot by name
     */
    public PracticeBotEntity getBot(String name) {
        return activeBots.get(name);
    }

    /**
     * Get all active bots
     */
    public Collection<PracticeBotEntity> getActiveBots() {
        return activeBots.values();
    }

    /**
     * Get number of active bots
     */
    public int getActiveBotCount() {
        return activeBots.size();
    }

    /**
     * Get bot names
     */
    public Set<String> getBotNames() {
        return activeBots.keySet();
    }

    /**
     * Check if entity is a bot
     */
    public boolean isBot(LivingEntity entity) {
        for (PracticeBotEntity bot : activeBots.values()) {
            if (bot.getNpc() != null && bot.getNpc().getEntity() != null) {
                if (bot.getNpc().getEntity().equals(entity)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get bot from entity
     */
    public PracticeBotEntity getBotFromEntity(LivingEntity entity) {
        for (PracticeBotEntity bot : activeBots.values()) {
            if (bot.getNpc() != null && bot.getNpc().getEntity() != null) {
                if (bot.getNpc().getEntity().equals(entity)) {
                    return bot;
                }
            }
        }
        return null;
    }

    /**
     * Handle entity death
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (!isBot(entity)) {
            return;
        }

        PracticeBotEntity bot = getBotFromEntity(entity);
        if (bot == null) {
            return;
        }

        // Clear drops
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Handle respawn
        bot.onDeath();

        // Send message to killer
        Player killer = entity.getKiller();
        if (killer != null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("bot", bot.getName());
            plugin.getMessageUtils().sendMessage(killer, "bots.bot-killed", placeholders);
        }
    }

    /**
     * Handle entity damage by entity (for damage multipliers)
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if damaged entity is a bot
        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity damaged = (LivingEntity) event.getEntity();

            if (isBot(damaged)) {
                PracticeBotEntity bot = getBotFromEntity(damaged);

                // Allow players to damage bots
                if (event.getDamager() instanceof Player) {
                    // Players can damage bots normally
                    return;
                }

                // Prevent bots from damaging each other
                if (event.getDamager() instanceof LivingEntity && isBot((LivingEntity) event.getDamager())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Check if damager is a bot
        if (event.getDamager() instanceof LivingEntity) {
            LivingEntity damager = (LivingEntity) event.getDamager();

            if (isBot(damager)) {
                PracticeBotEntity bot = getBotFromEntity(damager);

                // Prevent bots from damaging other bots
                if (event.getEntity() instanceof LivingEntity && isBot((LivingEntity) event.getEntity())) {
                    event.setCancelled(true);
                    return;
                }

                if (bot != null && event.getEntity() instanceof Player) {
                    // Apply damage multiplier when bot damages player
                    double originalDamage = event.getDamage();
                    double multiplier = getProfileDamageMultiplier(bot.getKitName());
                    event.setDamage(originalDamage * multiplier);
                }
            }
        }
    }

    /**
     * Get damage multiplier from profile
     */
    private double getProfileDamageMultiplier(String kitName) {
        ConfigurationSection profilesSection = plugin.getConfigManager().getBotsConfig().getConfigurationSection("profiles");

        if (profilesSection == null) {
            return 1.0;
        }

        // Find matching profile
        for (String profile : profilesSection.getKeys(false)) {
            String profileKit = profilesSection.getString(profile + ".kit");
            if (kitName.equalsIgnoreCase(profileKit)) {
                return plugin.getConfigManager().getBotsConfig().getDouble("profiles." + profile + ".damage-multiplier", 1.0);
            }
        }

        return 1.0;
    }

    /**
     * Cleanup all bots on disable
     */
    public void cleanup() {
        despawnAll();
    }

    /**
     * Reload bot manager
     */
    public void reload() {
        // Respawn all bots with new settings
        List<PracticeBotEntity> botsToRespawn = new ArrayList<>(activeBots.values());
        despawnAll();

        for (PracticeBotEntity oldBot : botsToRespawn) {
            // Recreate bots with new settings
            // Note: This is simplified, you might want to store more info to properly recreate
        }
    }
}