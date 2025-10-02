package com.pallux.practicebot.bot;

import com.pallux.practicebot.PracticeBot;
import com.pallux.practicebot.managers.AreaManager;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Advanced AI for practice bots
 */
public class BotAI {

    private final PracticeBot plugin;
    private final NPC npc;
    private final String kitName;
    private final AreaManager.PracticeArea area;
    private final Random random;

    private LivingEntity target;
    private int attackCooldown;
    private int rodCooldown;
    private int bowCooldown;
    private int comboHits;
    private boolean isSprinting;
    private boolean isBlocking;
    private long lastTargetUpdate;
    private Location wanderTarget;

    // AI Skill modifiers
    private double comboChance;
    private double strafeChance;
    private double criticalChance;
    private double blockChance;
    private double rodChance;
    private double bowAccuracy;

    public BotAI(PracticeBot plugin, NPC npc, String kitName, AreaManager.PracticeArea area) {
        this.plugin = plugin;
        this.npc = npc;
        this.kitName = kitName;
        this.area = area;
        this.random = new Random();
        this.attackCooldown = 0;
        this.rodCooldown = 0;
        this.bowCooldown = 0;
        this.comboHits = 0;
        this.isSprinting = false;
        this.isBlocking = false;
        this.lastTargetUpdate = 0;

        loadAISkills();
    }

    /**
     * Load AI skill modifiers from config
     */
    private void loadAISkills() {
        this.comboChance = plugin.getConfigManager().getBoolean("bot-behavior.enable-combos", true) ? 0.7 : 0;
        this.strafeChance = plugin.getConfigManager().getBoolean("bot-behavior.enable-strafing", true) ? 0.8 : 0;
        this.criticalChance = plugin.getConfigManager().getDouble("bot-behavior.critical-chance", 0.45);
        this.blockChance = plugin.getConfigManager().getDouble("bot-behavior.block-chance", 0.35);
        this.rodChance = plugin.getConfigManager().getBoolean("bot-behavior.rod-usage.enabled", true) ? 0.5 : 0;
        this.bowAccuracy = plugin.getConfigManager().getDouble("bot-behavior.bow-usage.accuracy", 0.85);
    }

    /**
     * Set AI skill modifiers for difficulty
     */
    public void setSkillModifiers(double combo, double strafe, double critical, double block, double rod, double bow) {
        this.comboChance = combo;
        this.strafeChance = strafe;
        this.criticalChance = critical;
        this.blockChance = block;
        this.rodChance = rod;
        this.bowAccuracy = bow;
    }

    /**
     * Main AI tick - called every tick
     */
    public void tick() {
        if (!npc.isSpawned()) {
            return;
        }

        Entity entity = npc.getEntity();
        if (!(entity instanceof LivingEntity)) {
            return;
        }

        LivingEntity bot = (LivingEntity) entity;

        // Update target periodically
        updateTarget(bot);

        // Decrease cooldowns
        if (attackCooldown > 0) attackCooldown--;
        if (rodCooldown > 0) rodCooldown--;
        if (bowCooldown > 0) bowCooldown--;

        if (target != null && target.isValid() && !target.isDead()) {
            // Combat mode
            handleCombat(bot);
        } else {
            // Wander mode
            handleWandering(bot);
            comboHits = 0;
        }
    }

    /**
     * Update target
     */
    private void updateTarget(LivingEntity bot) {
        long currentTime = System.currentTimeMillis();
        int updateInterval = plugin.getConfigManager().getInt("bot-behavior.target-update-interval", 10) * 50;

        if (currentTime - lastTargetUpdate < updateInterval) {
            return;
        }

        lastTargetUpdate = currentTime;

        double detectionRange = plugin.getConfigManager().getDouble("bot-behavior.detection-range", 32.0);

        // Find nearest player (NOT bots)
        Player nearestPlayer = null;
        double nearestDistance = detectionRange;

        for (Entity nearby : bot.getNearbyEntities(detectionRange, detectionRange, detectionRange)) {
            if (nearby instanceof Player && !nearby.isDead()) {
                Player player = (Player) nearby;

                // Skip if player is in creative or spectator mode
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    continue;
                }

                // CRITICAL FIX: Skip if this is another bot (NPC)
                if (player.hasMetadata("NPC")) {
                    continue;
                }

                double distance = bot.getLocation().distance(player.getLocation());
                if (distance < nearestDistance) {
                    nearestPlayer = player;
                    nearestDistance = distance;
                }
            }
        }

        target = nearestPlayer;
    }

    /**
     * Handle combat behavior
     */
    private void handleCombat(LivingEntity bot) {
        if (target == null) return;

        Location botLoc = bot.getLocation();
        Location targetLoc = target.getLocation();
        double distance = botLoc.distance(targetLoc);

        double attackRange = plugin.getConfigManager().getDouble("bot-behavior.attack-range", 3.5);
        double bowPreferredRange = plugin.getConfigManager().getDouble("bot-behavior.bow-usage.preferred-range", 10.0);

        // Check if should retreat
        double health = bot.getHealth() / bot.getMaxHealth();
        double retreatThreshold = plugin.getConfigManager().getDouble("combat.retreat-health-threshold", 0.3);

        if (health < retreatThreshold) {
            // Try to heal
            if (plugin.getConfigManager().getBoolean("combat.enable-healing", true)) {
                tryToHeal(bot);
            }

            // Retreat
            retreat(bot, targetLoc);
            return;
        }

        // Use bow at range
        if (distance > bowPreferredRange && distance < plugin.getConfigManager().getDouble("bot-behavior.bow-usage.max-range", 30.0)) {
            if (plugin.getConfigManager().getBoolean("bot-behavior.bow-usage.enabled", true) && bowCooldown <= 0) {
                useBow(bot, targetLoc);
                bowCooldown = 40; // 2 second cooldown between shots
                return; // Don't move towards if shooting
            }
        }

        // Move towards target
        if (distance > attackRange) {
            moveTowards(bot, targetLoc);

            // Sprint towards target
            if (plugin.getConfigManager().getBoolean("bot-behavior.enable-sprinting", true)) {
                isSprinting = true;
            }
        } else {
            // In attack range

            // Strafe around target
            if (random.nextDouble() < strafeChance) {
                strafe(bot, targetLoc);
            }

            // Use fishing rod for combo
            if (rodCooldown <= 0 && random.nextDouble() < rodChance) {
                if (useRod(bot, distance)) {
                    rodCooldown = plugin.getConfigManager().getInt("bot-behavior.rod-usage.cooldown", 30);
                }
            }

            // Attack
            if (attackCooldown <= 0) {
                performAttack(bot);
            }
        }
    }

    /**
     * Perform attack on target
     */
    private void performAttack(LivingEntity bot) {
        if (target == null || !target.isValid()) return;

        // W-tap for extra knockback
        if (plugin.getConfigManager().getBoolean("bot-behavior.enable-wtap", true) && isSprinting) {
            isSprinting = false;
            // Reset sprint next tick
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> isSprinting = true, 1L);
        }

        // Critical hit chance
        boolean critical = false;
        if (random.nextDouble() < criticalChance && bot.isOnGround()) {
            // Jump for critical
            bot.setVelocity(bot.getVelocity().setY(0.42));
            critical = true;
        }

        // Perform attack
        if (bot instanceof Player) {
            ((Player) bot).attack(target);
        } else {
            target.damage(getAttackDamage(bot), bot);
        }

        comboHits++;

        // Reset combo if max reached
        int maxCombo = plugin.getConfigManager().getInt("combat.max-combo-length", 8);
        if (comboHits >= maxCombo) {
            comboHits = 0;
            attackCooldown = 20; // Longer cooldown after combo
        } else {
            attackCooldown = plugin.getConfigManager().getInt("combat.attack-delay", 10);
        }
    }

    /**
     * Get attack damage for bot
     */
    private double getAttackDamage(LivingEntity bot) {
        // Base damage from held item
        return 5.0; // Placeholder
    }

    /**
     * Use fishing rod
     */
    private boolean useRod(LivingEntity bot, double distance) {
        double maxRange = plugin.getConfigManager().getDouble("bot-behavior.rod-usage.max-range", 6.0);
        if (distance > maxRange) return false;

        // Simulate rod usage (this would need proper implementation with projectiles)
        return true;
    }

    /**
     * Use bow
     */
    private void useBow(LivingEntity bot, Location targetLoc) {
        // Calculate trajectory with accuracy modifier
        Vector direction = targetLoc.toVector().subtract(bot.getLocation().toVector()).normalize();

        // Add inaccuracy
        double inaccuracy = (1.0 - bowAccuracy) * 0.1;
        direction.add(new Vector(
                (random.nextDouble() - 0.5) * inaccuracy,
                (random.nextDouble() - 0.5) * inaccuracy,
                (random.nextDouble() - 0.5) * inaccuracy
        ));

        // Shoot arrow (simplified)
        bot.getWorld().spawnArrow(bot.getEyeLocation(), direction, 3.0f, 0);
    }

    /**
     * Try to heal
     */
    private void tryToHeal(LivingEntity bot) {
        double healThreshold = plugin.getConfigManager().getDouble("combat.heal-threshold", 0.5);
        if (bot.getHealth() / bot.getMaxHealth() < healThreshold) {
            // Eat food or use potion (simplified)
            bot.setHealth(Math.min(bot.getHealth() + 2.0, bot.getMaxHealth()));
        }
    }

    /**
     * Retreat from target
     */
    private void retreat(LivingEntity bot, Location targetLoc) {
        Vector direction = bot.getLocation().toVector().subtract(targetLoc.toVector()).normalize();
        Location retreatLoc = bot.getLocation().add(direction.multiply(2));

        if (area.contains(retreatLoc)) {
            moveTowards(bot, retreatLoc);
        }
    }

    /**
     * Strafe around target
     */
    private void strafe(LivingEntity bot, Location targetLoc) {
        Vector direction = targetLoc.toVector().subtract(bot.getLocation().toVector()).normalize();
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX());

        if (random.nextBoolean()) {
            perpendicular.multiply(-1);
        }

        Location strafeLoc = bot.getLocation().add(perpendicular.multiply(0.5));

        if (area.contains(strafeLoc)) {
            moveTowards(bot, strafeLoc);
        }

        // Look at target while strafing
        lookAt(bot, targetLoc);
    }

    /**
     * Handle wandering behavior
     */
    private void handleWandering(LivingEntity bot) {
        // Check if we need a new wander target
        if (wanderTarget == null || bot.getLocation().distance(wanderTarget) < 2.0 || !area.contains(wanderTarget)) {
            // Get a random location in the area
            wanderTarget = area.getRandomLocation();

            // Find ground level
            wanderTarget = findGroundLocation(wanderTarget);
        }

        // Move towards wander target
        if (npc != null && npc.getNavigator() != null) {
            if (!npc.getNavigator().isNavigating()) {
                moveTowards(bot, wanderTarget);
            }
        } else {
            moveTowards(bot, wanderTarget);
        }
    }

    /**
     * Find ground location
     */
    private Location findGroundLocation(Location loc) {
        Location ground = loc.clone();

        // Move down to find ground
        while (!ground.getBlock().getType().isSolid() && ground.getY() > ground.getWorld().getMinHeight()) {
            ground.subtract(0, 1, 0);
        }

        // Move up one block to stand on ground
        ground.add(0, 1, 0);

        return ground;
    }

    /**
     * Move bot towards location
     */
    private void moveTowards(LivingEntity bot, Location target) {
        // Use Citizens navigation instead of velocity
        if (npc != null && npc.getNavigator() != null) {
            npc.getNavigator().setTarget(target);
            npc.getNavigator().getLocalParameters().speedModifier((float) plugin.getConfigManager().getDouble("bot-behavior.movement-speed", 1.1));
        }

        lookAt(bot, target);
    }

    /**
     * Make bot look at location
     */
    private void lookAt(LivingEntity bot, Location target) {
        Vector direction = target.toVector().subtract(bot.getLocation().toVector());
        Location newLoc = bot.getLocation().setDirection(direction);
        bot.teleport(newLoc);
    }

    /**
     * Get current target
     */
    public LivingEntity getTarget() {
        return target;
    }
}