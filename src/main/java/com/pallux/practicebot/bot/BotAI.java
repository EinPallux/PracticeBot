package com.pallux.practicebot.bot;

import com.pallux.practicebot.PracticeBot;
import com.pallux.practicebot.managers.AreaManager;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Random;

/**
 * Professional AI for practice bots using advanced movement and combat controllers
 */
public class BotAI {

    private final PracticeBot plugin;
    private final NPC npc;
    private final String kitName;
    private final AreaManager.PracticeArea area;
    private final Random random;

    // Advanced controllers
    private MovementController movement;
    private CombatController combat;

    private Player target;
    private long lastTargetUpdate;
    private Location wanderTarget;
    private int wanderTicks;

    // Combat state
    private CombatState state;
    private int strafeTicks;
    private int strafeDirection; // -1 = left, 1 = right

    // AI Skills
    private double strafeChance;
    private double bowAccuracy;

    private enum CombatState {
        IDLE,
        CHASING,
        ENGAGING,
        STRAFING,
        RETREATING
    }

    public BotAI(PracticeBot plugin, NPC npc, String kitName, AreaManager.PracticeArea area) {
        this.plugin = plugin;
        this.npc = npc;
        this.kitName = kitName;
        this.area = area;
        this.random = new Random();

        this.lastTargetUpdate = 0;
        this.wanderTicks = 0;
        this.state = CombatState.IDLE;
        this.strafeTicks = 0;
        this.strafeDirection = 1;

        loadAISkills();
    }

    /**
     * Initialize bot - called after spawn
     */
    public void initialize(Player bot) {
        // Initialize controllers
        double critChance = plugin.getConfigManager().getDouble("bot-behavior.critical-chance", 0.45);
        double rodChance = plugin.getConfigManager().getBoolean("bot-behavior.rod-usage.enabled", true) ? 0.4 : 0;
        int attackDelay = plugin.getConfigManager().getInt("combat.attack-delay", 10);

        this.movement = new MovementController(bot);
        this.combat = new CombatController(bot, critChance, rodChance, attackDelay);
    }

    /**
     * Load AI skill modifiers from config
     */
    private void loadAISkills() {
        this.strafeChance = plugin.getConfigManager().getBoolean("bot-behavior.enable-strafing", true) ? 0.75 : 0;
        this.bowAccuracy = plugin.getConfigManager().getDouble("bot-behavior.bow-usage.accuracy", 0.85);
    }

    /**
     * Set AI skill modifiers for difficulty
     */
    public void setSkillModifiers(double combo, double strafe, double critical, double block, double rod, double bow) {
        this.strafeChance = strafe;
        this.bowAccuracy = bow;
        // Combat controller handles crit and rod chances
    }

    /**
     * Main AI tick - called every tick
     */
    public void tick() {
        if (!npc.isSpawned()) {
            return;
        }

        Entity entity = npc.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }

        Player bot = (Player) entity;

        // Initialize controllers if needed
        if (movement == null) {
            initialize(bot);
        }

        // Ensure bot stays on ground
        ensureGrounded(bot);

        // Update target periodically
        updateTarget(bot);

        wanderTicks++;
        strafeTicks++;

        if (target != null && target.isValid() && !target.isDead()) {
            // Combat mode
            handleCombat(bot);
        } else {
            // Idle/Wander mode
            handleWandering(bot);
            state = CombatState.IDLE;
            combat.resetCombo();
        }
    }

    /**
     * Ensure bot stays grounded - fix glitching
     */
    private void ensureGrounded(Player bot) {
        if (!bot.isOnGround()) {
            double yVel = bot.getVelocity().getY();
            // If velocity is near zero but not on ground, find ground
            if (yVel > -0.1 && yVel < 0.1) {
                Location loc = bot.getLocation();
                for (int i = 0; i < 5; i++) {
                    loc.subtract(0, 1, 0);
                    if (loc.getBlock().getType().isSolid()) {
                        bot.teleport(loc.add(0, 1, 0));
                        break;
                    }
                }
            }
        }
    }

    /**
     * Update target - find nearest real player
     */
    private void updateTarget(Player bot) {
        long currentTime = System.currentTimeMillis();
        int updateInterval = plugin.getConfigManager().getInt("bot-behavior.target-update-interval", 10) * 50;

        if (currentTime - lastTargetUpdate < updateInterval) {
            return;
        }

        lastTargetUpdate = currentTime;

        double detectionRange = plugin.getConfigManager().getDouble("bot-behavior.detection-range", 32.0);

        Player nearestPlayer = null;
        double nearestDistance = detectionRange;

        for (Entity nearby : bot.getNearbyEntities(detectionRange, detectionRange, detectionRange)) {
            if (nearby instanceof Player && !nearby.isDead()) {
                Player player = (Player) nearby;

                // Skip creative/spectator
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
                        player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    continue;
                }

                // Skip other bots
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
     * Handle combat with advanced AI
     */
    private void handleCombat(Player bot) {
        if (target == null) return;

        double distance = bot.getLocation().distance(target.getLocation());
        double optimalDistance = combat.getOptimalDistance();
        double attackRange = plugin.getConfigManager().getDouble("bot-behavior.attack-range", 3.5);

        // Check health for retreat
        double healthPercent = bot.getHealth() / bot.getMaxHealth();
        double retreatThreshold = plugin.getConfigManager().getDouble("combat.retreat-health-threshold", 0.3);

        if (healthPercent < retreatThreshold) {
            state = CombatState.RETREATING;
            retreat(bot);
            return;
        }

        // State machine for natural combat flow
        if (distance > attackRange + 2.0) {
            // Too far - chase
            state = CombatState.CHASING;
            chase(bot);
        } else if (distance <= attackRange && distance >= optimalDistance - 0.5) {
            // Perfect range - strafe and attack
            if (random.nextDouble() < strafeChance) {
                state = CombatState.STRAFING;
                strafeAndFight(bot);
            } else {
                state = CombatState.ENGAGING;
                engage(bot);
            }
        } else if (distance < optimalDistance - 1.0) {
            // Too close - back up while attacking
            state = CombatState.ENGAGING;
            backUpAndFight(bot);
        } else {
            // Approaching range - move closer
            state = CombatState.CHASING;
            approach(bot);
        }

        // Try to attack if in range
        if (distance <= attackRange && combat.canAttack()) {
            combat.tryAttack(target);
        }
    }

    /**
     * Chase target with sprint
     */
    private void chase(Player bot) {
        movement.moveTo(target.getLocation(), true); // Sprint enabled
    }

    /**
     * Approach target cautiously
     */
    private void approach(Player bot) {
        movement.moveTo(target.getLocation(), false); // Walk
    }

    /**
     * Strafe around target while fighting
     */
    private void strafeAndFight(Player bot) {
        // Change strafe direction periodically
        if (strafeTicks % 25 == 0) {
            strafeDirection = random.nextBoolean() ? -1 : 1;
        }

        movement.strafe(target.getLocation(), strafeDirection, true);
    }

    /**
     * Engage directly
     */
    private void engage(Player bot) {
        movement.lookAt(target.getLocation());
        movement.moveTo(target.getLocation(), true);
    }

    /**
     * Back up while fighting
     */
    private void backUpAndFight(Player bot) {
        // Calculate backwards direction
        Location behindBot = bot.getLocation().clone();
        org.bukkit.util.Vector awayFromTarget = bot.getLocation().toVector().subtract(target.getLocation().toVector());
        awayFromTarget.setY(0);
        awayFromTarget.normalize().multiply(2);
        behindBot.add(awayFromTarget);

        if (area.contains(behindBot)) {
            movement.moveTo(behindBot, false);
        }

        movement.lookAt(target.getLocation());
    }

    /**
     * Retreat from target
     */
    private void retreat(Player bot) {
        Location retreatLoc = bot.getLocation().clone();
        org.bukkit.util.Vector awayFromTarget = bot.getLocation().toVector().subtract(target.getLocation().toVector());
        awayFromTarget.setY(0);
        awayFromTarget.normalize().multiply(5);
        retreatLoc.add(awayFromTarget);

        if (area.contains(retreatLoc)) {
            movement.moveTo(retreatLoc, true); // Sprint away
        }
    }

    /**
     * Handle wandering behavior
     */
    private void handleWandering(Player bot) {
        // Get new wander target every 5 seconds
        if (wanderTarget == null || wanderTicks % 100 == 0 ||
                movement.getDistanceToTarget() < 1.0) {

            wanderTarget = getRandomWanderLocation();
            wanderTicks = 0;
        }

        // Walk to wander target
        movement.moveTo(wanderTarget, false);

        // Stop if stuck
        if (movement.isStuck()) {
            wanderTarget = null;
        }
    }

    /**
     * Get random location in area for wandering
     */
    private Location getRandomWanderLocation() {
        Location loc = area.getRandomLocation();

        // Find ground
        while (!loc.getBlock().getType().isSolid() && loc.getY() > loc.getWorld().getMinHeight()) {
            loc.subtract(0, 1, 0);
        }
        loc.add(0, 1, 0);

        return loc;
    }

    /**
     * Get current target
     */
    public Player getTarget() {
        return target;
    }

    /**
     * Get current state
     */
    public CombatState getState() {
        return state;
    }
}