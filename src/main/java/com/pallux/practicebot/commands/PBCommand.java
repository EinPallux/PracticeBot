package com.pallux.practicebot.commands;

import com.pallux.practicebot.PracticeBot;
import com.pallux.practicebot.bot.PracticeBotEntity;
import com.pallux.practicebot.managers.AreaManager;
import com.pallux.practicebot.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Main command handler for /pb
 */
public class PBCommand implements CommandExecutor, TabCompleter {

    private final PracticeBot plugin;

    public PBCommand(PracticeBot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "spawn":
                return handleSpawn(sender, args);
            case "despawn":
                return handleDespawn(sender, args);
            case "list":
                return handleList(sender);
            case "reload":
                return handleReload(sender);
            case "info":
                return handleInfo(sender);
            case "setarea":
                return handleSetArea(sender);
            case "help":
                sendHelp(sender);
                return true;
            default:
                plugin.getMessageUtils().sendMessage(sender, "general.unknown-command");
                return true;
        }
    }

    /**
     * Handle spawn command
     */
    private boolean handleSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicebot.spawn")) {
            plugin.getMessageUtils().sendMessage(sender, "general.no-permission");
            return true;
        }

        // Check if there are any areas
        if (plugin.getAreaManager().getAreas().isEmpty()) {
            plugin.getMessageUtils().sendMessage(sender, "area.no-area-set");
            return true;
        }

        // Get first area (or you could add area selection)
        AreaManager.PracticeArea area = plugin.getAreaManager().getAreas().iterator().next();

        int amount = plugin.getConfigManager().getInt("settings.default-spawn-amount", 1);
        String kitName = plugin.getConfigManager().getString("default-kit", "default");

        // Parse amount
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount <= 0) {
                    plugin.getMessageUtils().sendMessage(sender, "general.invalid-number");
                    return true;
                }
            } catch (NumberFormatException e) {
                plugin.getMessageUtils().sendMessage(sender, "general.invalid-number");
                return true;
            }
        }

        // Parse kit
        if (args.length >= 3) {
            kitName = args[2];
            if (!plugin.getKitManager().hasKit(kitName)) {
                Map<String, String> placeholders = MessageUtils.builder()
                        .add("kit", kitName)
                        .build();
                plugin.getMessageUtils().sendMessage(sender, "kits.kit-not-found", placeholders);
                return true;
            }
        }

        // Spawn bots
        int spawned = plugin.getBotManager().spawnBots(amount, kitName, area);

        if (spawned == 0) {
            Map<String, String> placeholders = MessageUtils.builder()
                    .add("amount", plugin.getBotManager().getActiveBotCount())
                    .add("max", plugin.getConfigManager().getInt("settings.max-bots", 50))
                    .build();
            plugin.getMessageUtils().sendMessage(sender, "bots.max-bots-reached", placeholders);
        } else {
            Map<String, String> placeholders = MessageUtils.builder()
                    .add("amount", spawned)
                    .add("kit", kitName)
                    .build();
            plugin.getMessageUtils().sendMessage(sender, "bots.spawned", placeholders);
        }

        return true;
    }

    /**
     * Handle despawn command
     */
    private boolean handleDespawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicebot.despawn")) {
            plugin.getMessageUtils().sendMessage(sender, "general.no-permission");
            return true;
        }

        if (args.length < 2) {
            plugin.getMessageUtils().sendRaw(sender, "<red>Usage: /pb despawn <name|all>");
            return true;
        }

        String target = args[1];

        if (target.equalsIgnoreCase("all")) {
            int count = plugin.getBotManager().despawnAll();

            if (count == 0) {
                plugin.getMessageUtils().sendMessage(sender, "bots.no-bots-active");
            } else {
                Map<String, String> placeholders = MessageUtils.builder()
                        .add("amount", count)
                        .build();
                plugin.getMessageUtils().sendMessage(sender, "bots.despawned-all", placeholders);
            }
        } else {
            if (plugin.getBotManager().despawnBot(target)) {
                Map<String, String> placeholders = MessageUtils.builder()
                        .add("bot", target)
                        .build();
                plugin.getMessageUtils().sendMessage(sender, "bots.despawned-single", placeholders);
            } else {
                Map<String, String> placeholders = MessageUtils.builder()
                        .add("bot", target)
                        .build();
                plugin.getMessageUtils().sendMessage(sender, "bots.bot-not-found", placeholders);
            }
        }

        return true;
    }

    /**
     * Handle list command
     */
    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("practicebot.list")) {
            plugin.getMessageUtils().sendMessage(sender, "general.no-permission");
            return true;
        }

        Collection<PracticeBotEntity> bots = plugin.getBotManager().getActiveBots();

        if (bots.isEmpty()) {
            plugin.getMessageUtils().sendMessage(sender, "bots.no-bots-active");
            return true;
        }

        plugin.getMessageUtils().sendMessageNoPrefix(sender, "bot-list.header");

        for (PracticeBotEntity bot : bots) {
            Map<String, String> placeholders = MessageUtils.builder()
                    .add("bot", bot.getName())
                    .add("kit", bot.getKitName())
                    .add("health", String.format("%.1f", bot.getHealth()))
                    .add("max_health", String.format("%.1f", bot.getMaxHealth()))
                    .build();

            plugin.getMessageUtils().sendMessageNoPrefix(sender, "bot-list.entry", placeholders);
        }

        Map<String, String> footerPlaceholders = MessageUtils.builder()
                .add("count", bots.size())
                .build();
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "bot-list.footer", footerPlaceholders);

        return true;
    }

    /**
     * Handle reload command
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("practicebot.reload")) {
            plugin.getMessageUtils().sendMessage(sender, "general.no-permission");
            return true;
        }

        plugin.getConfigManager().reloadAll();
        plugin.getKitManager().reload();
        plugin.getAreaManager().reload();
        plugin.getMessageUtils().reload();

        plugin.getMessageUtils().sendMessage(sender, "general.plugin-reloaded");
        return true;
    }

    /**
     * Handle info command
     */
    private boolean handleInfo(CommandSender sender) {
        if (!sender.hasPermission("practicebot.info")) {
            plugin.getMessageUtils().sendMessage(sender, "general.no-permission");
            return true;
        }

        plugin.getMessageUtils().sendMessageNoPrefix(sender, "info.header");

        Map<String, String> placeholders = MessageUtils.builder()
                .add("version", plugin.getDescription().getVersion())
                .add("author", "Pallux")
                .add("bots", plugin.getBotManager().getActiveBotCount())
                .add("max", plugin.getConfigManager().getInt("settings.max-bots", 50))
                .add("areas", plugin.getAreaManager().getAreas().size())
                .add("kits", plugin.getKitManager().getKitNames().size())
                .build();

        plugin.getMessageUtils().sendMessageNoPrefix(sender, "info.version", placeholders);
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "info.author", placeholders);
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "info.active-bots", placeholders);
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "info.max-bots", placeholders);
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "info.areas", placeholders);
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "info.kits", placeholders);
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "info.footer");

        return true;
    }

    /**
     * Handle setarea command (give selection tool)
     */
    private boolean handleSetArea(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.getMessageUtils().sendMessage(sender, "general.player-only");
            return true;
        }

        if (!sender.hasPermission("practicebot.area")) {
            plugin.getMessageUtils().sendMessage(sender, "general.no-permission");
            return true;
        }

        Player player = (Player) sender;
        Material toolMaterial = plugin.getAreaManager().getSelectionTool();

        ItemStack tool = new ItemStack(toolMaterial);
        player.getInventory().addItem(tool);

        plugin.getMessageUtils().sendMessage(player, "area.tool-received");

        return true;
    }

    /**
     * Send help message
     */
    private void sendHelp(CommandSender sender) {
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "help.header");
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "help.spawn");
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "help.despawn");
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "help.list");
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "help.reload");
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "help.info");
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "help.area-tool");
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "help.area-create");
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "help.area-delete");
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "help.area-list");
        plugin.getMessageUtils().sendMessageNoPrefix(sender, "help.footer");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("spawn", "despawn", "list", "reload", "info", "setarea", "help"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("despawn")) {
                completions.addAll(plugin.getBotManager().getBotNames());
                completions.add("all");
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("spawn")) {
                completions.addAll(plugin.getKitManager().getKitNames());
            }
        }

        // Filter by what user typed
        String input = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(input));

        return completions;
    }
}