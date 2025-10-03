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
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class PracticeBotEntity {

    private final PracticeBot plugin;
    private final String name;
    private final String kitName;
    private final AreaManager.PracticeArea area;

    private NPC npc;
    private BotAI ai;
    private BukkitTask aiTask;
    private BukkitTask hologramTask;
    private boolean isDead = false;

    public PracticeBotEntity(PracticeBot plugin, String kitName, AreaManager.PracticeArea area) {
        this.plugin = plugin;
        this.kitName = kitName;
        this.area = area;
        // Simple name without numbers
        this.name = plugin.getConfigManager().getString("bot-name-format", "Practice Bot");
    }

    public boolean spawn() {
        try {
            Location spawnLoc = findSafeLocation(area.getRandomLocation());
            if (spawnLoc == null) {
                plugin.getLogger().warning("Could not find safe spawn location for bot!");
                return false;
            }

            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            this.npc = registry.createNPC(EntityType.PLAYER, ColorUtils.stripColor(name));
            this.npc.setProtected(false);

            // Add our custom trait to "tag" this NPC so we can identify it later
            this.npc.getOrAddTrait(PracticeBotTrait.class);

            npc.spawn(spawnLoc);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (npc == null || !npc.isSpawned()) return;
                    if (!(npc.getEntity() instanceof Player)) return;

                    Player botPlayer = (Player) npc.getEntity();
                    applySettings(botPlayer);

                    ai = new BotAI(plugin, npc, kitName, area);
                    startAI();
                    startHologram();
                }
            }.runTaskLater(plugin, 1L);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error spawning bot: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void applySettings(Player botPlayer) {
        KitManager.Kit kit = plugin.getKitManager().getKit(kitName);
        if (kit != null) kit.apply(botPlayer);

        Component displayName = ColorUtils.colorize(name);
        botPlayer.customName(displayName);
        botPlayer.setCustomNameVisible(true);

        botPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        botPlayer.setHealth(20.0);
    }

    public void onDeath() {
        if (isDead) return;
        this.isDead = true;
        stopAI();
        plugin.getBotManager().removeBot(this);

        if (npc != null) {
            try {
                if (npc.isSpawned()) {
                    npc.despawn();
                }
                npc.destroy();
            } catch (Exception e) {
                plugin.getLogger().warning("Error destroying bot on death: " + e.getMessage());
            }
            npc = null;
        }
    }

    public void despawn() {
        stopAI();
        if (npc != null) {
            try {
                if (npc.isSpawned()) {
                    npc.despawn();
                }
                npc.destroy();
            } catch (Exception e) {
                plugin.getLogger().warning("Error despawning bot: " + e.getMessage());
            }
            npc = null;
        }
    }

    private void startAI() {
        stopAI();
        aiTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (npc == null || !npc.isSpawned() || isDead) {
                    cancel();
                    return;
                }
                ai.tick();
            }
        }.runTaskTimer(plugin, 2L, 1L);
    }

    private void stopAI() {
        if (aiTask != null) {
            aiTask.cancel();
            aiTask = null;
        }
        if (hologramTask != null) {
            hologramTask.cancel();
            hologramTask = null;
        }
    }

    private void startHologram() {
        if (!plugin.getConfigManager().getBoolean("hologram.enabled", true)) return;
        hologramTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (npc == null || !npc.isSpawned() || isDead) {
                    cancel();
                    return;
                }
                updateHologram();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void updateHologram() {
        if (!(npc.getEntity() instanceof LivingEntity)) return;
        LivingEntity entity = (LivingEntity) npc.getEntity();
        String format = plugin.getConfigManager().getString("hologram.format", "&6{name}\n&c❤ {health}/{max_health}");
        String health = String.format("%.1f", entity.getHealth());
        String maxHealth = String.format("%.1f", entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        String hologramText = format.replace("{name}", name).replace("{health}", health).replace("{max_health}", maxHealth);
        entity.customName(ColorUtils.colorize(hologramText));
    }

    private Location findSafeLocation(Location start) {
        if (start == null) return null;
        Location loc = start.clone();
        for (int y = loc.getBlockY(); y < loc.getWorld().getMaxHeight(); y++) {
            loc.setY(y);
            if (!loc.getBlock().getType().isSolid() && !loc.clone().add(0,1,0).getBlock().getType().isSolid()) {
                Location below = loc.clone().subtract(0, 1, 0);
                if (below.getBlock().getType().isSolid()) {
                    return loc;
                }
            }
        }
        return null;
    }

    public String getName() { return name; }
    public String getKitName() { return kitName; }
    public NPC getNpc() { return npc; }
    public BotAI getAi() { return ai; }

    public double getHealth() {
        if (npc != null && npc.isSpawned() && npc.getEntity() instanceof LivingEntity) {
            return ((LivingEntity) npc.getEntity()).getHealth();
        }
        return 0.0;
    }

    public double getMaxHealth() {
        if (npc != null && npc.isSpawned() && npc.getEntity() instanceof LivingEntity) {
            return ((LivingEntity) npc.getEntity()).getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        }
        return 20.0;
    }
}