package com.pallux.practicebot.bot;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Advanced combat controller for human-like PvP
 */
public class CombatController {

    private final Player bot;
    private final Random random;

    // Combat state
    private int hitCombo;
    private long lastAttackTime;
    private long lastCriticalTime;
    private long lastRodTime;
    private boolean isWTapping;

    // Combat settings
    private final double critChance;
    private final double rodChance;
    private final int attackDelay; // In milliseconds

    // CPS (Clicks Per Second) - human range: 8-15
    private final int targetCPS;

    public CombatController(Player bot, double critChance, double rodChance, int attackDelayTicks) {
        this.bot = bot;
        this.random = new Random();
        this.critChance = critChance;
        this.rodChance = rodChance;
        this.attackDelay = attackDelayTicks * 50; // Convert ticks to ms
        this.targetCPS = 10 + random.nextInt(5); // 10-14 CPS
        this.hitCombo = 0;
        this.lastAttackTime = 0;
        this.lastCriticalTime = 0;
        this.lastRodTime = 0;
        this.isWTapping = false;
    }

    /**
     * Attempt to attack target with human-like timing
     */
    public boolean tryAttack(Player target) {
        long currentTime = System.currentTimeMillis();

        // Check attack cooldown (realistic CPS)
        long timeSinceLastAttack = currentTime - lastAttackTime;
        long cpsDelay = 1000 / targetCPS;

        if (timeSinceLastAttack < cpsDelay) {
            return false; // Too soon
        }

        // Add slight randomness to timing (human imperfection)
        if (random.nextDouble() < 0.1) {
            return false; // Occasional miss-click
        }

        // W-Tap for knockback
        if (shouldWTap()) {
            performWTap();
        }

        // Critical hit
        if (shouldCritical()) {
            performCritical();
        }

        // Perform attack
        bot.attack(target);

        lastAttackTime = currentTime;
        hitCombo++;

        return true;
    }

    /**
     * Check if should perform W-tap
     */
    private boolean shouldWTap() {
        // W-tap every 2-3 hits for extra knockback
        return hitCombo % (2 + random.nextInt(2)) == 0;
    }

    /**
     * Perform W-tap (sprint reset)
     */
    private void performWTap() {
        if (!bot.isSprinting()) return;

        bot.setSprinting(false);
        isWTapping = true;

        // Reset sprint after 1 tick (50ms)
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("PracticeBot"),
                () -> {
                    if (bot.isValid()) {
                        bot.setSprinting(true);
                        isWTapping = false;
                    }
                },
                1L
        );
    }

    /**
     * Check if should perform critical hit
     */
    private boolean shouldCritical() {
        long currentTime = System.currentTimeMillis();

        // Can't critical if not on ground
        if (!bot.isOnGround()) {
            return false;
        }

        // Cooldown between criticals (realistic timing)
        if (currentTime - lastCriticalTime < 500) {
            return false;
        }

        // Random chance based on skill
        if (random.nextDouble() < critChance) {
            lastCriticalTime = currentTime;
            return true;
        }

        return false;
    }

    /**
     * Perform critical hit (jump attack)
     */
    private void performCritical() {
        Vector velocity = bot.getVelocity();
        velocity.setY(0.42); // Jump velocity
        bot.setVelocity(velocity);
    }

    /**
     * Try to use fishing rod for combo
     */
    public boolean tryUseRod(Player target, double distance) {
        long currentTime = System.currentTimeMillis();

        // Check rod cooldown
        if (currentTime - lastRodTime < 1500) { // 1.5 second cooldown
            return false;
        }

        // Check distance
        if (distance > 6.0 || distance < 2.0) {
            return false;
        }

        // Random chance
        if (random.nextDouble() < rodChance) {
            // TODO: Implement actual rod throwing
            lastRodTime = currentTime;
            return true;
        }

        return false;
    }

    /**
     * Calculate optimal distance for combat
     */
    public double getOptimalDistance() {
        // Stay at 2.5-3.5 blocks for optimal reach
        return 2.8 + (random.nextDouble() * 0.7);
    }

    /**
     * Reset combo counter
     */
    public void resetCombo() {
        hitCombo = 0;
    }

    /**
     * Get current combo count
     */
    public int getComboCount() {
        return hitCombo;
    }

    /**
     * Check if can attack (not in cooldown)
     */
    public boolean canAttack() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastAttack = currentTime - lastAttackTime;
        long cpsDelay = 1000 / targetCPS;

        return timeSinceLastAttack >= cpsDelay;
    }

    /**
     * Calculate aim offset (human imperfection)
     */
    public Vector getAimOffset(Location targetLoc) {
        // Small random offset to simulate human aim
        double accuracy = 0.95; // 95% accuracy
        double maxOffset = (1.0 - accuracy) * 0.5;

        return new Vector(
                (random.nextDouble() - 0.5) * maxOffset,
                (random.nextDouble() - 0.5) * maxOffset,
                (random.nextDouble() - 0.5) * maxOffset
        );
    }
}