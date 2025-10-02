package com.pallux.practicebot.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for sending formatted messages
 */
public class MessageUtils {

    private final FileConfiguration messagesConfig;
    private final FileConfiguration config;
    private String prefix;

    public MessageUtils(FileConfiguration messagesConfig, FileConfiguration config) {
        this.messagesConfig = messagesConfig;
        this.config = config;
        loadPrefix();
    }

    /**
     * Load prefix from config
     */
    private void loadPrefix() {
        this.prefix = config.getString("settings.prefix", "<gradient:#FF6B6B:#4ECDC4>PracticeBot</gradient> <gray>»</gray> ");
    }

    /**
     * Reload the prefix
     */
    public void reload() {
        loadPrefix();
    }

    /**
     * Send a message to a player with prefix
     */
    public void sendMessage(CommandSender sender, String path, Map<String, String> placeholders) {
        String message = messagesConfig.getString(path, "Message not found: " + path);
        message = replacePlaceholders(message, placeholders);

        Component component = ColorUtils.colorize(prefix + message);
        sender.sendMessage(component);
    }

    /**
     * Send a message to a player with prefix (no placeholders)
     */
    public void sendMessage(CommandSender sender, String path) {
        sendMessage(sender, path, new HashMap<>());
    }

    /**
     * Send a message without prefix
     */
    public void sendMessageNoPrefix(CommandSender sender, String path, Map<String, String> placeholders) {
        String message = messagesConfig.getString(path, "Message not found: " + path);
        message = replacePlaceholders(message, placeholders);

        Component component = ColorUtils.colorize(message);
        sender.sendMessage(component);
    }

    /**
     * Send a message without prefix (no placeholders)
     */
    public void sendMessageNoPrefix(CommandSender sender, String path) {
        sendMessageNoPrefix(sender, path, new HashMap<>());
    }

    /**
     * Send a raw message (direct string, not from config)
     */
    public void sendRaw(CommandSender sender, String message) {
        Component component = ColorUtils.colorize(prefix + message);
        sender.sendMessage(component);
    }

    /**
     * Send a raw message without prefix
     */
    public void sendRawNoPrefix(CommandSender sender, String message) {
        Component component = ColorUtils.colorize(message);
        sender.sendMessage(component);
    }

    /**
     * Replace placeholders in a message
     */
    private String replacePlaceholders(String message, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    /**
     * Get a message from config with placeholders
     */
    public String getMessage(String path, Map<String, String> placeholders) {
        String message = messagesConfig.getString(path, "Message not found: " + path);
        return replacePlaceholders(message, placeholders);
    }

    /**
     * Get a message from config without placeholders
     */
    public String getMessage(String path) {
        return getMessage(path, new HashMap<>());
    }

    /**
     * Create a placeholder map builder
     */
    public static PlaceholderBuilder builder() {
        return new PlaceholderBuilder();
    }

    /**
     * Helper class for building placeholder maps
     */
    public static class PlaceholderBuilder {
        private final Map<String, String> placeholders = new HashMap<>();

        public PlaceholderBuilder add(String key, String value) {
            placeholders.put(key, value);
            return this;
        }

        public PlaceholderBuilder add(String key, int value) {
            placeholders.put(key, String.valueOf(value));
            return this;
        }

        public PlaceholderBuilder add(String key, double value) {
            placeholders.put(key, String.valueOf(value));
            return this;
        }

        public PlaceholderBuilder add(String key, long value) {
            placeholders.put(key, String.valueOf(value));
            return this;
        }

        public Map<String, String> build() {
            return placeholders;
        }
    }
}