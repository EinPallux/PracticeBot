package com.pallux.practicebot.bot;

import com.pallux.practicebot.PracticeBot;
import com.pallux.practicebot.managers.AreaManager;
import com.pallux.practicebot.managers.BotManager;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Optional;
import java.util.Random;

public class BotAI {

    private final PracticeBot plugin;
    private final NPC npc;
    private final AreaManager.PracticeArea area;
    private final Random random;
    private final BotManager botManager;

    private LivingEntity target;
    private int attackCooldown = 0;
    private int jumpCooldown = 0;
    private int retargetCooldown = 0;

    private Vector movementVector = new Vector(0, 0, 0);
    private boolean wantsToJump = false;

    public BotAI(PracticeBot plugin, NPC npc, String kitName, AreaManager.PracticeArea area) {
        this.plugin = plugin;
        this.npc = npc;
        this.area = area;
        this.random = new Random();
        this.botManager = plugin.getBotManager();
    }

    public void setSkillModifiers(double combo, double strafe, double critical, double block, double rod, double bow) {
    }

    public void tick() {
        if (!npc.isSpawned() || !(npc.getEntity() instanceof Player)) {
            return;
        }
        Player bot = (Player) npc.getEntity();

        updateCooldowns();
        updateTarget(bot);

        if (target != null) {
            if (npc.getNavigator().isNavigating()) {
                npc.getNavigator().cancelNavigation();
            }
            executeCombatLogic(bot);
        } else {
            executeWanderingLogic(bot);
        }

        applyMovement(bot);
    }

    private void updateCooldowns() {
        if (attackCooldown > 0) attackCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;
        if (retargetCooldown > 0) retargetCooldown--;
    }

    private void executeWanderingLogic(Player bot) {
        Navigator navigator = npc.getNavigator();
        if (!navigator.isNavigating()) {
            navigator.setTarget(area.getRandomLocation());
            navigator.getLocalParameters().speedModifier(1.0f);
        }
    }

    private void executeCombatLogic(Player bot) {
        npc.faceLocation(target.getEyeLocation());

        movementVector.zero();
        wantsToJump = false;

        Vector directionToTarget = target.getLocation().toVector().subtract(bot.getLocation().toVector()).normalize();

        if (bot.isOnGround() && isFacingWall(bot, directionToTarget)) {
            if (jumpCooldown <= 0) {
                wantsToJump = true;
                jumpCooldown = 20;
            } else {
                movementVector.add(new Vector(-directionToTarget.getZ(), 0, directionToTarget.getX()).normalize());
            }
        }

        double attackRange = plugin.getConfigManager().getDouble("bot-behavior.attack-range", 3.5);
        double distance = getDistanceToTarget(bot);

        if (distance > attackRange) { // Chasing
            movementVector.add(directionToTarget);
        } else { // Melee Range
            double strafeChance = plugin.getConfigManager().getDouble("combat.strafe-chance", 0.7);
            if (random.nextDouble() < strafeChance) {
                movementVector.add(new Vector(-directionToTarget.getZ(), 0, directionToTarget.getX()).normalize().multiply(random.nextBoolean() ? 1 : -1));
            }
        }

        if (attackCooldown <= 0 && distance < attackRange) {
            bot.attack(target);
            bot.swingMainHand();
            attackCooldown = plugin.getConfigManager().getInt("combat.attack-delay", 12);
        }
    }

    private void applyMovement(Player bot) {
        if (target != null) {
            double chaseSpeed = plugin.getConfigManager().getDouble("movement.chase-speed", 1.4);
            double meleeSpeed = plugin.getConfigManager().getDouble("movement.melee-speed", 1.2);
            double speed = (getDistanceToTarget(bot) > 3.5) ? chaseSpeed : meleeSpeed;

            Vector velocity = bot.getVelocity();

            if (wantsToJump && bot.isOnGround()) {
                velocity.setY(0.42);
            }

            if (bot.isOnGround()) {
                if (movementVector.lengthSquared() > 0) {
                    movementVector.normalize().multiply(0.22 * speed);
                }
                velocity.setX(movementVector.getX());
                velocity.setZ(movementVector.getZ());
            } else {
                Vector airControl = new Vector(movementVector.getX(), 0, movementVector.getZ()).multiply(0.05);
                velocity.add(airControl);
            }

            bot.setVelocity(velocity);
        }
    }

    private void updateTarget(Player bot) {
        if (retargetCooldown > 0) return;
        retargetCooldown = 20; // Re-evaluate target every second

        // Invalidate current target if it's dead, gone, or too far
        if (target != null && (target.isDead() || !target.isValid() || getDistanceToTarget(bot) > 32)) {
            botManager.releaseTarget(npc.getUniqueId());
            target = null;
        }

        // --- NEW PLAYER-FIRST LOGIC ---
        // Step 1: ALWAYS search for an available player target.
        Optional<Player> bestPlayerTarget = findValidPlayerTarget(bot);

        if (bestPlayerTarget.isPresent()) {
            // If we found a player and we're not already targeting them, SWITCH.
            if (!bestPlayerTarget.get().equals(target)) {
                forceTarget(bestPlayerTarget.get());
            }
            return; // Lock onto the player and do nothing else this cycle.
        }

        // Step 2: If we reach here, NO players are available to be targeted.
        // If we are currently fighting another bot, we can continue.
        if (target != null && target.hasMetadata("NPC")) {
            return;
        }

        // Step 3: If we reach here, we are idle. Look for a bot to fight.
        if (plugin.getConfigManager().getBoolean("combat.bot-vs-bot.enabled", true)) {
            findValidBotTarget(bot).ifPresent(this::forceTarget);
        }
    }


    private Optional<Player> findValidPlayerTarget(Player bot) {
        double detectionRange = plugin.getConfigManager().getDouble("bot-behavior.detection-range", 32.0);
        return bot.getWorld().getPlayers().stream()
                .filter(p -> p != bot && p.getLocation().distance(bot.getLocation()) < detectionRange)
                .filter(p -> !p.hasMetadata("NPC"))
                .filter(p -> p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                .filter(botManager::isTargetSlotAvailable)
                .min(java.util.Comparator.comparingDouble(p -> p.getLocation().distance(bot.getLocation())));
    }

    private Optional<LivingEntity> findValidBotTarget(Player bot) {
        double detectionRange = plugin.getConfigManager().getDouble("bot-behavior.detection-range", 32.0);
        return botManager.getAllBots().stream()
                .filter(b -> b.getNpc() != null && b.getNpc().isSpawned() && b.getNpc().getEntity() != bot)
                .map(b -> (LivingEntity) b.getNpc().getEntity())
                .filter(e -> e.getLocation().distance(bot.getLocation()) < detectionRange)
                .filter(botManager::isTargetSlotAvailable)
                .min(java.util.Comparator.comparingDouble(e -> e.getLocation().distance(bot.getLocation())));
    }

    public void forceTarget(LivingEntity newTarget) {
        botManager.claimTarget(npc.getUniqueId(), newTarget);
        this.target = newTarget;
        this.retargetCooldown = 100; // Force focus for 5 seconds
    }

    public LivingEntity getTarget() {
        return this.target;
    }

    private boolean isFacingWall(Player bot, Vector direction) {
        Location front = bot.getEyeLocation().add(direction.clone().multiply(0.8));
        return front.getBlock().getType().isSolid() && !front.getBlock().isPassable();
    }

    private double getDistanceToTarget(Player bot) {
        if (target == null) return Double.MAX_VALUE;
        return bot.getLocation().distance(target.getLocation());
    }
}