package com.pallux.practicebot;

import com.pallux.practicebot.bot.PracticeBotTrait;
import com.pallux.practicebot.commands.AreaCommand;
import com.pallux.practicebot.managers.AreaManager;
import com.pallux.practicebot.managers.BotManager;
import com.pallux.practicebot.managers.ConfigManager;
import com.pallux.practicebot.managers.KitManager;
import com.pallux.practicebot.utils.MessageUtils;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitInfo;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class PracticeBot extends JavaPlugin {

    private ConfigManager configManager;
    private MessageUtils messageUtils;
    private AreaManager areaManager;
    private KitManager kitManager;
    private BotManager botManager;

    @Override
    public void onEnable() {
        if (!checkDependencies()) {
            getLogger().severe("Citizens plugin not found! Disabling PracticeBot.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register our custom trait with Citizens
        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(PracticeBotTrait.class).withName("practicebottrait"));

        // Initialize managers first (but don't start area spawning yet)
        initializeManagers();
        registerCommands();

        // Delay cleanup and then start area management
        new BukkitRunnable() {
            @Override
            public void run() {
                getLogger().info("Running cleanup of leftover NPCs...");
                cleanupOldBots();

                // After cleanup, start the area management
                if (areaManager != null) {
                    getLogger().info("Starting area management...");
                    areaManager.startManagement();
                }
            }
        }.runTaskLater(this, 60L); // Wait 3 seconds

        getLogger().info("PracticeBot has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (areaManager != null) {
            getLogger().info("Despawning all PracticeBot NPCs...");
            areaManager.shutdown();
        }

        // Final cleanup on disable
        cleanupOldBots();

        getLogger().info("PracticeBot has been disabled!");
    }

    private void cleanupOldBots() {
        getLogger().info("Scanning for and removing any leftover PracticeBot NPCs...");
        int count = 0;

        List<NPC> botsToRemove = new ArrayList<>();

        try {
            if (CitizensAPI.getNPCRegistry() == null) {
                getLogger().warning("Citizens registry not ready yet!");
                return;
            }

            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (npc != null && npc.hasTrait(PracticeBotTrait.class)) {
                    botsToRemove.add(npc);
                }
            }

            for (NPC npc : botsToRemove) {
                try {
                    if (npc.isSpawned()) {
                        npc.despawn();
                    }
                    npc.destroy();
                    count++;
                } catch (Exception e) {
                    getLogger().warning("Failed to remove NPC " + npc.getId() + ": " + e.getMessage());
                }
            }

            if (count > 0) {
                getLogger().info("Removed " + count + " leftover PracticeBot NPCs.");
            } else {
                getLogger().info("No leftover PracticeBot NPCs found.");
            }
        } catch (Exception e) {
            getLogger().severe("Error during bot cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean checkDependencies() {
        if (getServer().getPluginManager().getPlugin("Citizens") == null) {
            return false;
        }
        try {
            CitizensAPI.getNPCRegistry();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void initializeManagers() {
        configManager = new ConfigManager(this);
        configManager.initialize();

        messageUtils = new MessageUtils(configManager.getMessagesConfig(), configManager.getMainConfig());
        kitManager = new KitManager(this);
        botManager = new BotManager(this);
        areaManager = new AreaManager(this);
    }

    private void registerCommands() {
        AreaCommand areaCommand = new AreaCommand(this);
        getCommand("pbarea").setExecutor(areaCommand);
        getCommand("pbarea").setTabCompleter(areaCommand);
    }

    public ConfigManager getConfigManager() { return configManager; }
    public MessageUtils getMessageUtils() { return messageUtils; }
    public AreaManager getAreaManager() { return areaManager; }
    public KitManager getKitManager() { return kitManager; }
    public BotManager getBotManager() { return botManager; }
}