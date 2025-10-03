package com.pallux.practicebot.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public class MessageUtils {

    private final FileConfiguration messagesConfig;
    private final FileConfiguration config;
    private String prefix;

    public MessageUtils(FileConfiguration messagesConfig, FileConfiguration config) {
        this.messagesConfig = messagesConfig;
        this.config = config;
        loadPrefix();
    }

    private void loadPrefix() {
        this.prefix = config.getString("settings.prefix", "<gradient:#FF6B6B:#4ECDC4>PracticeBot</gradient> <gray>»</gray> ");
    }

    public void reload() {
        loadPrefix();
    }

    public void sendMessage(CommandSender sender, String path, Map<String, String> placeholders) {
        String message = getMessage(path, placeholders);
        sender.sendMessage(ColorUtils.colorize(prefix + message));
    }

    public void sendMessage(CommandSender sender, String path) {
        sendMessage(sender, path, new HashMap<>());
    }

    public void sendMessageNoPrefix(CommandSender sender, String path, Map<String, String> placeholders) {
        String message = getMessage(path, placeholders);
        sender.sendMessage(ColorUtils.colorize(message));
    }

    public void sendMessageNoPrefix(CommandSender sender, String path) {
        sendMessageNoPrefix(sender, path, new HashMap<>());
    }

    private String replacePlaceholders(String message, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String message = messagesConfig.getString(path, "Message not found: " + path);
        return replacePlaceholders(message, placeholders);
    }

    public String getMessage(String path) {
        return getMessage(path, new HashMap<>());
    }

    public static PlaceholderBuilder builder() {
        return new PlaceholderBuilder();
    }

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

        public Map<String, String> build() {
            return placeholders;
        }
    }
}