package com.pallux.practicebot.managers;

import com.pallux.practicebot.PracticeBot;
import com.pallux.practicebot.bot.PracticeBotEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BotManager implements Listener {

    private final PracticeBot plugin;
    private final Set<PracticeBotEntity> allBots = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Set<UUID>> targeterMap = new ConcurrentHashMap<>();

    public BotManager(PracticeBot plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void addBot(PracticeBotEntity bot) {
        allBots.add(bot);
    }

    public void removeBot(PracticeBotEntity bot) {
        if (bot == null || bot.getNpc() == null) return;
        allBots.remove(bot);
        releaseTarget(bot.getNpc().getUniqueId());
    }

    public PracticeBotEntity getBotFromEntity(LivingEntity entity) {
        if (entity == null || !entity.hasMetadata("NPC")) return null;
        for (PracticeBotEntity bot : allBots) {
            if (bot.getNpc() != null && bot.getNpc().getEntity() != null && bot.getNpc().getEntity().getUniqueId().equals(entity.getUniqueId())) {
                return bot;
            }
        }
        return null;
    }

    public Set<PracticeBotEntity> getAllBots() {
        return allBots;
    }

    public boolean isTargetSlotAvailable(LivingEntity target) {
        int maxAttackers = (target instanceof Player)
                ? plugin.getConfigManager().getInt("combat.max-player-attackers", 2)
                : plugin.getConfigManager().getInt("combat.bot-vs-bot.max-bot-attackers", 1);

        return targeterMap.getOrDefault(target.getUniqueId(), Collections.emptySet()).size() < maxAttackers;
    }

    public void claimTarget(UUID attackerId, LivingEntity newTarget) {
        releaseTarget(attackerId);

        if (newTarget != null) {
            targeterMap.computeIfAbsent(newTarget.getUniqueId(), k -> Collections.synchronizedSet(new HashSet<>())).add(attackerId);
        }
    }

    public void releaseTarget(UUID uniqueId) {
        targeterMap.forEach((targetUUID, attackers) -> attackers.remove(uniqueId));
        targeterMap.remove(uniqueId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBotDamaged(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof LivingEntity)) return;

        PracticeBotEntity bot = getBotFromEntity((LivingEntity) event.getEntity());
        if (bot == null) return;

        if (!(event.getDamager() instanceof LivingEntity attacker)) return;

        if (bot.getAi().getTarget() != null && bot.getAi().getTarget().equals(attacker)) {
            return;
        }

        bot.getAi().forceTarget(attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();

        PracticeBotEntity killedBot = getBotFromEntity(victim);

        if (killedBot != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);

            if (killer != null) {
                plugin.getMessageUtils().sendMessage(killer, "bots.bot-killed", Map.of("bot", killedBot.getName()));
            }

            killedBot.onDeath();
        }

        // Clean up targeting map for the deceased entity
        releaseTarget(victim.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        releaseTarget(event.getPlayer().getUniqueId());
    }
}