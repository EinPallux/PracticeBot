package com.pallux.practicebot.bot;

import com.pallux.practicebot.PracticeBot;
import com.pallux.practicebot.managers.AreaManager;
import com.pallux.practicebot.managers.BotManager;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    private int rodCooldown = 0;
    private int potionCooldown = 0;

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
        checkTotemUsage(bot);
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
        if (rodCooldown > 0) rodCooldown--;
        if (potionCooldown > 0) potionCooldown--;
    }

    private void checkTotemUsage(Player bot) {
        // Check if bot is low on health and has a totem
        double healthPercent = bot.getHealth() / bot.getMaxHealth();

        if (healthPercent < 0.3) { // Below 30% health
            // Check if totem is in offhand
            ItemStack offhand = bot.getInventory().getItemInOffHand();
            if (offhand.getType() == Material.TOTEM_OF_UNDYING) {
                return; // Already equipped
            }

            // Search inventory for totem
            for (int i = 0; i < bot.getInventory().getSize(); i++) {
                ItemStack item = bot.getInventory().getItem(i);
                if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                    // Swap totem to offhand
                    ItemStack currentOffhand = bot.getInventory().getItemInOffHand();
                    bot.getInventory().setItemInOffHand(item.clone());
                    bot.getInventory().setItem(i, currentOffhand);
                    break;
                }
            }
        }
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

        // Try fishing rod first (if available and in range)
        if (rodCooldown <= 0 && distance > 3.0 && distance < 10.0) {
            if (tryFishingRod(bot)) {
                rodCooldown = plugin.getConfigManager().getInt("combat.rod-cooldown", 60); // 3 seconds
            }
        }

        // Try throwing potions (if low health or at medium range)
        if (potionCooldown <= 0 && distance > 2.0 && distance < 15.0) {
            double healthPercent = bot.getHealth() / bot.getMaxHealth();
            double potionChance = plugin.getConfigManager().getDouble("combat.potion-chance", 0.3);

            if (healthPercent < 0.5 || (random.nextDouble() < potionChance && distance > 5.0)) {
                if (tryThrowPotion(bot)) {
                    potionCooldown = plugin.getConfigManager().getInt("combat.potion-cooldown", 100); // 5 seconds
                }
            }
        }

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

    private boolean tryFishingRod(Player bot) {
        // Find fishing rod in inventory
        ItemStack rod = findItemInInventory(bot, Material.FISHING_ROD);
        if (rod == null) return false;

        // Switch to rod temporarily
        ItemStack currentItem = bot.getInventory().getItemInMainHand();
        int rodSlot = bot.getInventory().first(Material.FISHING_ROD);

        if (rodSlot == -1) return false;

        // Cast the rod toward target
        bot.getInventory().setItemInMainHand(rod);

        // Launch fishing hook
        Vector direction = target.getLocation().toVector().subtract(bot.getLocation().toVector()).normalize();
        bot.launchProjectile(org.bukkit.entity.FishHook.class, direction.multiply(1.5));

        // Switch back to weapon after a short delay
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (bot.isValid() && npc.isSpawned()) {
                bot.getInventory().setItemInMainHand(currentItem);
            }
        }, 10L); // Switch back after 0.5 seconds

        return true;
    }

    private boolean tryThrowPotion(Player bot) {
        // Find splash potion in inventory
        ItemStack potion = findSplashPotion(bot);
        if (potion == null) return false;

        // Calculate throw direction (aim slightly upward for arc)
        Vector direction = target.getEyeLocation().toVector()
                .subtract(bot.getEyeLocation().toVector())
                .normalize()
                .multiply(0.75);
        direction.setY(direction.getY() + 0.2); // Add upward arc

        // Throw the potion
        ThrownPotion thrownPotion = bot.launchProjectile(ThrownPotion.class, direction);
        thrownPotion.setItem(potion.clone());

        // Remove one potion from inventory
        potion.setAmount(potion.getAmount() - 1);
        if (potion.getAmount() <= 0) {
            int slot = findPotionSlot(bot);
            if (slot != -1) {
                bot.getInventory().setItem(slot, null);
            }
        }

        return true;
    }

    private ItemStack findItemInInventory(Player bot, Material material) {
        for (ItemStack item : bot.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                return item;
            }
        }
        return null;
    }

    private ItemStack findSplashPotion(Player bot) {
        for (ItemStack item : bot.getInventory().getContents()) {
            if (item != null && (item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION)) {
                return item;
            }
        }
        return null;
    }

    private int findPotionSlot(Player bot) {
        for (int i = 0; i < bot.getInventory().getSize(); i++) {
            ItemStack item = bot.getInventory().getItem(i);
            if (item != null && (item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION)) {
                return i;
            }
        }
        return -1;
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