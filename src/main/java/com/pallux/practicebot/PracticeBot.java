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

        // --- NEW: Run cleanup BEFORE initializing managers ---
        cleanupOldBots();

        initializeManagers();
        registerCommands();

        getLogger().info("PracticeBot has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (areaManager != null) {
            getLogger().info("Despawning all PracticeBot NPCs...");
            areaManager.shutdown();
        }
        getLogger().info("PracticeBot has been disabled!");
    }

    private void cleanupOldBots() {
        getLogger().info("Scanning for and removing any leftover PracticeBot NPCs...");
        int count = 0;
        // Iterate through a copy to avoid modification issues
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.hasTrait(PracticeBotTrait.class)) {
                npc.destroy();
                count++;
            }
        }
        if (count > 0) {
            getLogger().info("Removed " + count + " leftover PracticeBot NPCs.");
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

    // Getters
    public ConfigManager getConfigManager() { return configManager; }
    public MessageUtils getMessageUtils() { return messageUtils; }
    public AreaManager getAreaManager() { return areaManager; }
    public KitManager getKitManager() { return kitManager; }
    public BotManager getBotManager() { return botManager; }
}