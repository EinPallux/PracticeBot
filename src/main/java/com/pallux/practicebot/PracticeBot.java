package com.pallux.practicebot;

import com.pallux.practicebot.commands.AreaCommand;
import com.pallux.practicebot.commands.PBCommand;
import com.pallux.practicebot.managers.AreaManager;
import com.pallux.practicebot.managers.BotManager;
import com.pallux.practicebot.managers.ConfigManager;
import com.pallux.practicebot.managers.KitManager;
import com.pallux.practicebot.utils.MessageUtils;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for PracticeBot
 *
 * @author Pallux
 * @version 1.0.0
 */
public class PracticeBot extends JavaPlugin {

    // Managers
    private ConfigManager configManager;
    private MessageUtils messageUtils;
    private AreaManager areaManager;
    private KitManager kitManager;
    private BotManager botManager;

    @Override
    public void onEnable() {
        // ASCII Art Banner
        getLogger().info("╔══════════════════════════════════╗");
        getLogger().info("║      PracticeBot v1.0.0          ║");
        getLogger().info("║      Author: Pallux              ║");
        getLogger().info("║   + Custom A* Pathfinding        ║");
        getLogger().info("╚══════════════════════════════════╝");

        // Check for Citizens dependency
        if (!checkDependencies()) {
            getLogger().severe("Citizens plugin not found! Disabling PracticeBot.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        initializeManagers();

        // Register commands
        registerCommands();

        // Log success
        getLogger().info("PracticeBot has been enabled successfully!");
        getLogger().info("Using custom A* pathfinding (no external dependencies)");
        getLogger().info("Loaded " + kitManager.getKitNames().size() + " kit(s)");
        getLogger().info("Loaded " + areaManager.getAreas().size() + " practice area(s)");
    }

    @Override
    public void onDisable() {
        // Cleanup
        if (botManager != null) {
            getLogger().info("Despawning all active bots...");
            botManager.cleanup();
        }

        // Save configurations
        if (configManager != null) {
            configManager.saveAll();
        }

        getLogger().info("PracticeBot has been disabled!");
    }

    /**
     * Check if required dependencies are present
     */
    private boolean checkDependencies() {
        // Check for Citizens
        if (getServer().getPluginManager().getPlugin("Citizens") == null) {
            return false;
        }

        // Verify Citizens API is accessible
        try {
            CitizensAPI.getNPCRegistry();
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Citizens API!");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Initialize all managers
     */
    private void initializeManagers() {
        getLogger().info("Initializing managers...");

        // Config Manager (first, as others depend on it)
        configManager = new ConfigManager(this);
        configManager.initialize();

        // Message Utils
        messageUtils = new MessageUtils(
                configManager.getMessagesConfig(),
                configManager.getMainConfig()
        );

        // Area Manager
        areaManager = new AreaManager(this);

        // Kit Manager
        kitManager = new KitManager(this);

        // Bot Manager (last, as it depends on other managers)
        botManager = new BotManager(this);

        getLogger().info("All managers initialized successfully!");
    }

    /**
     * Register commands
     */
    private void registerCommands() {
        getLogger().info("Registering commands...");

        // Main command
        PBCommand pbCommand = new PBCommand(this);
        getCommand("pb").setExecutor(pbCommand);
        getCommand("pb").setTabCompleter(pbCommand);

        // Area command
        AreaCommand areaCommand = new AreaCommand(this);
        getCommand("pbarea").setExecutor(areaCommand);
        getCommand("pbarea").setTabCompleter(areaCommand);

        getLogger().info("Commands registered successfully!");
    }

    // Getters for managers

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageUtils getMessageUtils() {
        return messageUtils;
    }

    public AreaManager getAreaManager() {
        return areaManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public BotManager getBotManager() {
        return botManager;
    }
}