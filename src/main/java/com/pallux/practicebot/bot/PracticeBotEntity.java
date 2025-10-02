package com.pallux.practicebot.bot;

import com.pallux.practicebot.PracticeBot;
import com.pallux.practicebot.managers.AreaManager;
import com.pallux.practicebot.managers.KitManager;
import com.pallux.practicebot.utils.ColorUtils;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Represents a practice bot entity
 */
public class PracticeBotEntity {

    private final PracticeBot plugin;
    private final String name;
    private final String kitName;
    private final AreaManager.PracticeArea area;
    private final int botNumber;

    private NPC npc;
    private BotAI ai;
    private BukkitTask aiTask;
    private BukkitTask hologramTask;
    private boolean isDead;
    private Location deathLocation;

    private double healthMultiplier;
    private double damageMultiplier;

    public PracticeBotEntity(PracticeBot plugin, int botNumber, String kitName, AreaManager.PracticeArea area) {
        this.plugin = plugin;
        this.botNumber = botNumber;
        this.kitName = kitName;
        this.area = area;
        this.isDead = false;
        this.healthMultiplier = 1.0;
        this.damageMultiplier = 1.0;

        // Generate name
        String nameFormat = plugin.getConfigManager().getString("bot-name-format", "<gradient:#FF6B6B:#4ECDC4>Practice Bot {number}</gradient>");
        this.name = nameFormat.replace("{number}", String.valueOf(botNumber));
    }

    /**
     * Set difficulty multipliers
     */
    public void setMultipliers(double health, double damage) {
        this.healthMultiplier = health;
        this.damageMultiplier = damage;
    }

    /**
     * Spawn the bot
     */
    public boolean spawn() {
        if (npc != null && npc.isSpawned()) {
            return false;
        }

        try {
            // Get spawn location
            Location spawnLoc = area.getRandomLocation();
            spawnLoc = findSafeLocation(spawnLoc);

            if (spawnLoc == null) {
                plugin.getLogger().warning("Could not find safe spawn location for bot!");
                return false;
            }

            // Create NPC
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            npc = registry.createNPC(EntityType.PLAYER, ColorUtils.stripColor(name));

            // Spawn first
            npc.spawn(spawnLoc);

            // Configure NPC settings for better movement (only use compatible metadata)
            npc.setProtected(false);

            try {
                npc.data().set(NPC.Metadata.PATHFINDER_OPEN_DOORS, true);
                npc.data().set(NPC.Metadata.USE_MINECRAFT_AI, true);
            } catch (Exception e) {
                // Metadata not available in this Citizens version
                plugin.getLogger().warning("Some Citizens metadata not available, bots may have limited AI");
            }

            // Get entity
            if (!(npc.getEntity() instanceof Player)) {
                plugin.getLogger().warning("Bot entity is not a player!");
                return false;
            }

            Player botPlayer = (Player) npc.getEntity();

            // Apply kit
            KitManager.Kit kit = plugin.getKitManager().getKit(kitName);
            if (kit != null) {
                kit.apply(botPlayer);
            }

            // Set display name
            Component displayName = ColorUtils.colorize(name);
            botPlayer.displayName(displayName);
            botPlayer.customName(displayName);
            botPlayer.setCustomNameVisible(true);

            // Make bot invulnerable to prevent weird behavior
            botPlayer.setInvulnerable(false);

            // Set AI to be more responsive
            botPlayer.setCollidable(true);
            botPlayer.setGravity(true);

            // Apply health multiplier
            double maxHealth = botPlayer.getMaxHealth() * healthMultiplier;
            botPlayer.setMaxHealth(maxHealth);
            botPlayer.setHealth(maxHealth);

            // Initialize AI
            ai = new BotAI(plugin, npc, kitName, area);

            // Initialize AI with bot player
            ai.initialize(botPlayer);

            // Apply AI skill modifiers from bot profile if exists
            applyProfileSkills();

            // Start AI task
            startAI();

            // Start hologram task
            if (plugin.getConfigManager().getBoolean("hologram.enabled", true)) {
                startHologram();
            }

            plugin.getLogger().info("Spawned bot: " + name + " with kit: " + kitName);
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to spawn bot: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Apply profile skills to AI
     */
    private void applyProfileSkills() {
        // Try to get profile from bots.yml
        String profilePath = "behavior-modifiers." + getProfileName() + ".";

        double combo = plugin.getConfigManager().getBotsConfig().getDouble(profilePath + "combo-chance", 0.6);
        double strafe = plugin.getConfigManager().getBotsConfig().getDouble(profilePath + "strafe-chance", 0.7);
        double critical = plugin.getConfigManager().getBotsConfig().getDouble(profilePath + "critical-chance", 0.4);
        double block = plugin.getConfigManager().getBotsConfig().getDouble(profilePath + "block-chance", 0.35);
        double rod = plugin.getConfigManager().getBotsConfig().getDouble(profilePath + "rod-chance", 0.4);
        double bow = plugin.getConfigManager().getBotsConfig().getDouble(profilePath + "bow-accuracy", 0.8);

        ai.setSkillModifiers(combo, strafe, critical, block, rod, bow);
    }

    /**
     * Get profile name based on kit
     */
    private String getProfileName() {
        // Map kit to profile
        if (kitName.equalsIgnoreCase("iron")) return "easy";
        if (kitName.equalsIgnoreCase("netherite")) return "hard";
        if (kitName.equalsIgnoreCase("crystal")) return "crystal";
        if (kitName.equalsIgnoreCase("uhc")) return "uhc";
        return "medium";
    }

    /**
     * Find safe location (on ground)
     */
    private Location findSafeLocation(Location start) {
        Location loc = start.clone();

        // Check if current location is safe
        if (loc.getBlock().getType().isSolid()) {
            // Move up
            for (int i = 0; i < 10; i++) {
                loc.add(0, 1, 0);
                if (!loc.getBlock().getType().isSolid()) {
                    break;
                }
            }
        } else {
            // Move down to find ground
            for (int i = 0; i < 10; i++) {
                loc.subtract(0, 1, 0);
                if (loc.getBlock().getType().isSolid()) {
                    loc.add(0, 1, 0);
                    break;
                }
            }
        }

        return loc;
    }

    /**
     * Start AI
     */
    private void startAI() {
        int tickRate = plugin.getConfigManager().getInt("performance.ai-tick-rate", 1);

        aiTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (npc == null || !npc.isSpawned() || isDead) {
                    cancel();
                    return;
                }

                try {
                    ai.tick();
                } catch (Exception e) {
                    plugin.getLogger().warning("Error in bot AI for " + name + ": " + e.getMessage());
                }
            }
        }.runTaskTimer(plugin, 5L, tickRate); // Start after 5 ticks to let spawn complete
    }

    /**
     * Start hologram updates
     */
    private void startHologram() {
        hologramTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (npc == null || !npc.isSpawned() || isDead) {
                    cancel();
                    return;
                }

                updateHologram();
            }
        }.runTaskTimer(plugin, 0L, 20L); // Update every second
    }

    /**
     * Update hologram display
     */
    private void updateHologram() {
        if (!(npc.getEntity() instanceof LivingEntity)) return;

        LivingEntity entity = (LivingEntity) npc.getEntity();

        if (plugin.getConfigManager().getBoolean("hologram.show-health", true)) {
            String format = plugin.getConfigManager().getString("hologram.format", "<gold>{name}</gold>\n<red>❤ {health}</red>");

            String health = String.format("%.1f", entity.getHealth());
            String hologramText = format
                    .replace("{name}", ColorUtils.stripColor(name))
                    .replace("{health}", health);

            Component hologram = ColorUtils.colorize(hologramText);
            entity.customName(hologram);
        }
    }

    /**
     * Handle bot death
     */
    public void onDeath() {
        if (isDead) return;

        isDead = true;
        deathLocation = npc.getStoredLocation();

        // Stop AI
        if (aiTask != null) {
            aiTask.cancel();
        }

        if (hologramTask != null) {
            hologramTask.cancel();
        }

        // Schedule respawn
        int respawnDelay = plugin.getConfigManager().getInt("bot-behavior.respawn-delay", 5) * 20;

        new BukkitRunnable() {
            @Override
            public void run() {
                respawn();
            }
        }.runTaskLater(plugin, respawnDelay);
    }

    /**
     * Respawn the bot
     */
    public void respawn() {
        isDead = false;

        if (npc != null) {
            npc.despawn();
        }

        spawn();
    }

    /**
     * Despawn the bot
     */
    public void despawn() {
        if (aiTask != null) {
            aiTask.cancel();
        }

        if (hologramTask != null) {
            hologramTask.cancel();
        }

        if (npc != null) {
            npc.destroy();
            npc = null;
        }
    }

    /**
     * Check if bot is spawned
     */
    public boolean isSpawned() {
        return npc != null && npc.isSpawned() && !isDead;
    }

    /**
     * Get bot name
     */
    public String getName() {
        return name;
    }

    /**
     * Get kit name
     */
    public String getKitName() {
        return kitName;
    }

    /**
     * Get bot number
     */
    public int getBotNumber() {
        return botNumber;
    }

    /**
     * Get NPC
     */
    public NPC getNpc() {
        return npc;
    }

    /**
     * Get AI
     */
    public BotAI getAi() {
        return ai;
    }

    /**
     * Get health
     */
    public double getHealth() {
        if (npc == null || !npc.isSpawned()) return 0;
        if (!(npc.getEntity() instanceof LivingEntity)) return 0;

        return ((LivingEntity) npc.getEntity()).getHealth();
    }

    /**
     * Get max health
     */
    public double getMaxHealth() {
        if (npc == null || !npc.isSpawned()) return 0;
        if (!(npc.getEntity() instanceof LivingEntity)) return 0;

        return ((LivingEntity) npc.getEntity()).getMaxHealth();
    }
}