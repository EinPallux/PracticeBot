package com.pallux.practicebot.managers;

import com.pallux.practicebot.PracticeBot;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages all configuration files for the plugin
 */
public class ConfigManager {

    private final PracticeBot plugin;
    private final Map<String, FileConfiguration> configs;
    private final Map<String, File> configFiles;

    public ConfigManager(PracticeBot plugin) {
        this.plugin = plugin;
        this.configs = new HashMap<>();
        this.configFiles = new HashMap<>();
    }

    /**
     * Initialize all configuration files
     */
    public void initialize() {
        // Load default config
        plugin.saveDefaultConfig();
        configs.put("config", plugin.getConfig());

        // Load custom config files
        loadCustomConfig("messages.yml");
        loadCustomConfig("kits.yml");
        loadCustomConfig("bots.yml");

        plugin.getLogger().info("All configuration files loaded successfully!");
    }

    /**
     * Load a custom configuration file
     */
    private void loadCustomConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);

        // Create file if it doesn't exist
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Load defaults
        InputStream defConfigStream = plugin.getResource(fileName);
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream));
            config.setDefaults(defConfig);
        }

        // Store configuration
        String configName = fileName.replace(".yml", "");
        configs.put(configName, config);
        configFiles.put(configName, file);
    }

    /**
     * Get a configuration by name
     */
    public FileConfiguration getConfig(String name) {
        return configs.getOrDefault(name, null);
    }

    /**
     * Get the main config
     */
    public FileConfiguration getMainConfig() {
        return plugin.getConfig();
    }

    /**
     * Get messages config
     */
    public FileConfiguration getMessagesConfig() {
        return getConfig("messages");
    }

    /**
     * Get kits config
     */
    public FileConfiguration getKitsConfig() {
        return getConfig("kits");
    }

    /**
     * Get bots config
     */
    public FileConfiguration getBotsConfig() {
        return getConfig("bots");
    }

    /**
     * Save a specific configuration
     */
    public void saveConfig(String name) {
        FileConfiguration config = configs.get(name);
        File file = configFiles.get(name);

        if (config != null && file != null) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save " + name + ".yml", e);
            }
        }
    }

    /**
     * Reload a specific configuration
     */
    public void reloadConfig(String name) {
        if (name.equals("config")) {
            plugin.reloadConfig();
            configs.put("config", plugin.getConfig());
            return;
        }

        File file = configFiles.get(name);
        if (file != null && file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            // Load defaults
            InputStream defConfigStream = plugin.getResource(name + ".yml");
            if (defConfigStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream));
                config.setDefaults(defConfig);
            }

            configs.put(name, config);
        }
    }

    /**
     * Reload all configurations
     */
    public void reloadAll() {
        reloadConfig("config");
        reloadConfig("messages");
        reloadConfig("kits");
        reloadConfig("bots");

        plugin.getLogger().info("All configuration files reloaded!");
    }

    /**
     * Save all configurations
     */
    public void saveAll() {
        plugin.saveConfig();
        saveConfig("messages");
        saveConfig("kits");
        saveConfig("bots");
    }

    /**
     * Get a value from main config with default
     */
    public <T> T get(String path, T defaultValue) {
        Object value = plugin.getConfig().get(path);
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException e) {
            plugin.getLogger().warning("Invalid type for config value: " + path);
            return defaultValue;
        }
    }

    /**
     * Get a string from config
     */
    public String getString(String path, String defaultValue) {
        return plugin.getConfig().getString(path, defaultValue);
    }

    /**
     * Get an integer from config
     */
    public int getInt(String path, int defaultValue) {
        return plugin.getConfig().getInt(path, defaultValue);
    }

    /**
     * Get a double from config
     */
    public double getDouble(String path, double defaultValue) {
        return plugin.getConfig().getDouble(path, defaultValue);
    }

    /**
     * Get a boolean from config
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        return plugin.getConfig().getBoolean(path, defaultValue);
    }
}