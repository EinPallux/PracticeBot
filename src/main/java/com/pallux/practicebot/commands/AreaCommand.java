package com.pallux.practicebot.commands;

import com.pallux.practicebot.PracticeBot;
import com.pallux.practicebot.managers.AreaManager;
import com.pallux.practicebot.utils.MessageUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AreaCommand implements CommandExecutor, TabCompleter, Listener {

    private final PracticeBot plugin;

    public AreaCommand(PracticeBot plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageUtils().sendMessage(sender, "general.player-only");
            return true;
        }

        if (!player.hasPermission("practicebot.admin")) {
            plugin.getMessageUtils().sendMessage(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "delete" -> handleDelete(player, args);
            case "tool" -> handleTool(player);
            case "list" -> handleList(player);
            case "setcount" -> handleSetCount(player, args);
            case "toggle" -> handleToggle(player, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            plugin.getMessageUtils().sendMessage(player, "errors.usage-area-create");
            return;
        }
        String name = args[1];
        int count;
        try {
            count = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageUtils().sendMessage(player, "general.invalid-number");
            return;
        }

        Location pos1 = plugin.getAreaManager().getPosition1(player);
        Location pos2 = plugin.getAreaManager().getPosition2(player);

        if (pos1 == null || pos2 == null) {
            plugin.getMessageUtils().sendMessage(player, "area.both-positions-required");
            return;
        }
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            plugin.getMessageUtils().sendMessage(player, "area.invalid-world");
            return;
        }

        if (plugin.getAreaManager().createArea(name, pos1, pos2, count)) {
            plugin.getMessageUtils().sendMessage(player, "area.area-created", Map.of("area", name));
        } else {
            plugin.getMessageUtils().sendMessage(player, "area.area-already-exists", Map.of("area", name));
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageUtils().sendMessage(player, "errors.usage-area-delete");
            return;
        }
        String name = args[1];
        if (plugin.getAreaManager().getArea(name) == null) {
            plugin.getMessageUtils().sendMessage(player, "area.area-not-found", Map.of("area", name));
            return;
        }
        plugin.getAreaManager().deleteArea(name);
        plugin.getMessageUtils().sendMessage(player, "area.area-deleted", Map.of("area", name));
    }

    private void handleTool(Player player) {
        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(plugin.getAreaManager().getSelectionTool()));
        plugin.getMessageUtils().sendMessage(player, "area.tool-received");
    }

    private void handleList(Player player) {
        plugin.getMessageUtils().sendMessageNoPrefix(player, "area-list.header");
        for (AreaManager.PracticeArea area : plugin.getAreaManager().getAreas()) {
            String status = area.isEnabled() ? "<green>Enabled</green>" : "<red>Disabled</red>";
            plugin.getMessageUtils().sendMessageNoPrefix(player, "area-list.entry", Map.of(
                    "area", area.getName(),
                    "status", status,
                    "bots", String.valueOf(area.getActiveBots().size()),
                    "max", String.valueOf(area.getBotCount())
            ));
        }
    }

    private void handleSetCount(Player player, String[] args) {
        if (args.length < 3) {
            plugin.getMessageUtils().sendMessage(player, "errors.usage-area-setcount");
            return;
        }
        String name = args[1];
        AreaManager.PracticeArea area = plugin.getAreaManager().getArea(name);
        if (area == null) {
            plugin.getMessageUtils().sendMessage(player, "area.area-not-found", Map.of("area", name));
            return;
        }
        int count;
        try {
            count = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageUtils().sendMessage(player, "general.invalid-number");
            return;
        }
        area.setBotCount(count);
        plugin.getAreaManager().saveArea(area);
        plugin.getMessageUtils().sendMessage(player, "area.set-count", Map.of("area", name, "count", String.valueOf(count)));
    }

    private void handleToggle(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageUtils().sendMessage(player, "errors.usage-area-toggle");
            return;
        }
        String name = args[1];
        AreaManager.PracticeArea area = plugin.getAreaManager().getArea(name);
        if (area == null) {
            plugin.getMessageUtils().sendMessage(player, "area.area-not-found", Map.of("area", name));
            return;
        }
        area.setEnabled(!area.isEnabled());
        plugin.getAreaManager().saveArea(area);
        String status = area.isEnabled() ? "enabled" : "disabled";
        plugin.getMessageUtils().sendMessage(player, "area.toggled", Map.of("area", name, "status", status));
    }

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reloadAll();
        plugin.getKitManager().loadKits();
        plugin.getAreaManager().shutdown();
        plugin.getAreaManager().loadAreas();
        plugin.getMessageUtils().sendMessage(sender, "general.plugin-reloaded");
    }

    private void sendHelp(Player player) {
        plugin.getMessageUtils().sendMessageNoPrefix(player, "help.header");
        plugin.getMessageUtils().sendMessageNoPrefix(player, "help.tool");
        plugin.getMessageUtils().sendMessageNoPrefix(player, "help.create");
        plugin.getMessageUtils().sendMessageNoPrefix(player, "help.delete");
        plugin.getMessageUtils().sendMessageNoPrefix(player, "help.list");
        plugin.getMessageUtils().sendMessageNoPrefix(player, "help.setcount");
        plugin.getMessageUtils().sendMessageNoPrefix(player, "help.toggle");
        plugin.getMessageUtils().sendMessageNoPrefix(player, "help.reload");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getItem() != null && event.getItem().getType() == plugin.getAreaManager().getSelectionTool() && player.hasPermission("practicebot.admin")) {
            event.setCancelled(true);
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                plugin.getAreaManager().setPosition1(player, event.getClickedBlock().getLocation());
                plugin.getMessageUtils().sendMessage(player, "area.position1-set", Map.of(
                        "x", String.valueOf(event.getClickedBlock().getX()),
                        "y", String.valueOf(event.getClickedBlock().getY()),
                        "z", String.valueOf(event.getClickedBlock().getZ())
                ));
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                plugin.getAreaManager().setPosition2(player, event.getClickedBlock().getLocation());
                plugin.getMessageUtils().sendMessage(player, "area.position2-set", Map.of(
                        "x", String.valueOf(event.getClickedBlock().getX()),
                        "y", String.valueOf(event.getClickedBlock().getY()),
                        "z", String.valueOf(event.getClickedBlock().getZ())
                ));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            return List.of("create", "delete", "tool", "list", "setcount", "toggle", "reload");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("setcount") || args[0].equalsIgnoreCase("toggle"))) {
            return new ArrayList<>(plugin.getAreaManager().getAreaNames());
        }
        return completions;
    }
}