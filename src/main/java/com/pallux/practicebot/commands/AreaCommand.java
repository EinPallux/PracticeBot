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

import java.util.*;

/**
 * Command handler for /pbarea
 */
public class AreaCommand implements CommandExecutor, TabCompleter, Listener {

    private final PracticeBot plugin;

    public AreaCommand(PracticeBot plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageUtils().sendMessage(sender, "general.player-only");
            return true;
        }

        if (!sender.hasPermission("practicebot.area")) {
            plugin.getMessageUtils().sendMessage(sender, "general.no-permission");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "pos1":
            case "position1":
                return handlePos1(player);
            case "pos2":
            case "position2":
                return handlePos2(player);
            case "create":
                return handleCreate(player, args);
            case "delete":
            case "remove":
                return handleDelete(player, args);
            case "list":
                return handleList(player);
            default:
                sendHelp(player);
                return true;
        }
    }

    /**
     * Handle pos1 command
     */
    private boolean handlePos1(Player player) {
        Location loc = player.getLocation();
        plugin.getAreaManager().setPosition1(player, loc);

        Map<String, String> placeholders = MessageUtils.builder()
                .add("x", (int) loc.getX())
                .add("y", (int) loc.getY())
                .add("z", (int) loc.getZ())
                .build();

        plugin.getMessageUtils().sendMessage(player, "area.position1-set", placeholders);
        return true;
    }

    /**
     * Handle pos2 command
     */
    private boolean handlePos2(Player player) {
        Location loc = player.getLocation();
        plugin.getAreaManager().setPosition2(player, loc);

        Map<String, String> placeholders = MessageUtils.builder()
                .add("x", (int) loc.getX())
                .add("y", (int) loc.getY())
                .add("z", (int) loc.getZ())
                .build();

        plugin.getMessageUtils().sendMessage(player, "area.position2-set", placeholders);
        return true;
    }

    /**
     * Handle create command
     */
    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageUtils().sendRaw(player, "<red>Usage: /pbarea create <name>");
            return true;
        }

        String areaName = args[1];

        // Check if both positions are set
        if (!plugin.getAreaManager().hasBothPositions(player)) {
            plugin.getMessageUtils().sendMessage(player, "area.both-positions-required");
            return true;
        }

        Location pos1 = plugin.getAreaManager().getPosition1(player);
        Location pos2 = plugin.getAreaManager().getPosition2(player);

        // Validate same world
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            plugin.getMessageUtils().sendMessage(player, "area.invalid-world");
            return true;
        }

        // Calculate size
        double sizeX = Math.abs(pos2.getX() - pos1.getX());
        double sizeY = Math.abs(pos2.getY() - pos1.getY());
        double sizeZ = Math.abs(pos2.getZ() - pos1.getZ());
        double totalSize = sizeX * sizeY * sizeZ;

        int minSize = plugin.getConfigManager().getInt("area-settings.min-area-size", 10);
        int maxSize = plugin.getConfigManager().getInt("area-settings.max-area-size", 500);

        // Validate size
        if (sizeX < minSize || sizeY < minSize || sizeZ < minSize) {
            Map<String, String> placeholders = MessageUtils.builder()
                    .add("min", minSize)
                    .build();
            plugin.getMessageUtils().sendMessage(player, "area.area-too-small", placeholders);
            return true;
        }

        if (sizeX > maxSize || sizeY > maxSize || sizeZ > maxSize) {
            Map<String, String> placeholders = MessageUtils.builder()
                    .add("max", maxSize)
                    .build();
            plugin.getMessageUtils().sendMessage(player, "area.area-too-large", placeholders);
            return true;
        }

        // Check if area already exists
        if (plugin.getAreaManager().getArea(areaName) != null) {
            Map<String, String> placeholders = MessageUtils.builder()
                    .add("area", areaName)
                    .build();
            plugin.getMessageUtils().sendMessage(player, "area.area-already-exists", placeholders);
            return true;
        }

        // Create area
        if (plugin.getAreaManager().createArea(areaName, pos1, pos2)) {
            Map<String, String> placeholders = MessageUtils.builder()
                    .add("area", areaName)
                    .build();
            plugin.getMessageUtils().sendMessage(player, "area.area-created", placeholders);

            // Clear selection
            plugin.getAreaManager().clearSelection(player);
        } else {
            plugin.getMessageUtils().sendMessage(player, "errors.invalid-area");
        }

        return true;
    }

    /**
     * Handle delete command
     */
    private boolean handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageUtils().sendRaw(player, "<red>Usage: /pbarea delete <name>");
            return true;
        }

        String areaName = args[1];

        if (plugin.getAreaManager().deleteArea(areaName)) {
            Map<String, String> placeholders = MessageUtils.builder()
                    .add("area", areaName)
                    .build();
            plugin.getMessageUtils().sendMessage(player, "area.area-deleted", placeholders);
        } else {
            Map<String, String> placeholders = MessageUtils.builder()
                    .add("area", areaName)
                    .build();
            plugin.getMessageUtils().sendMessage(player, "area.area-not-found", placeholders);
        }

        return true;
    }

    /**
     * Handle list command
     */
    private boolean handleList(Player player) {
        Collection<AreaManager.PracticeArea> areas = plugin.getAreaManager().getAreas();

        if (areas.isEmpty()) {
            plugin.getMessageUtils().sendMessage(player, "area.no-areas");
            return true;
        }

        plugin.getMessageUtils().sendMessageNoPrefix(player, "area-list.header");

        for (AreaManager.PracticeArea area : areas) {
            Map<String, String> placeholders = MessageUtils.builder()
                    .add("area", area.getName())
                    .add("world", area.getWorld().getName())
                    .add("min_x", (int) area.getMin().getX())
                    .add("min_y", (int) area.getMin().getY())
                    .add("min_z", (int) area.getMin().getZ())
                    .add("max_x", (int) area.getMax().getX())
                    .add("max_y", (int) area.getMax().getY())
                    .add("max_z", (int) area.getMax().getZ())
                    .build();

            plugin.getMessageUtils().sendMessageNoPrefix(player, "area-list.entry", placeholders);
        }

        Map<String, String> footerPlaceholders = MessageUtils.builder()
                .add("count", areas.size())
                .build();
        plugin.getMessageUtils().sendMessageNoPrefix(player, "area-list.footer", footerPlaceholders);

        return true;
    }

    /**
     * Send help message
     */
    private void sendHelp(Player player) {
        plugin.getMessageUtils().sendRaw(player, "<gradient:#FF6B6B:#4ECDC4>╔══════════════════════════════════╗</gradient>");
        plugin.getMessageUtils().sendRawNoPrefix(player, "<gradient:#FF6B6B:#4ECDC4>  Area Commands</gradient>");
        plugin.getMessageUtils().sendRawNoPrefix(player, "<gradient:#FF6B6B:#4ECDC4>╚══════════════════════════════════╝</gradient>");
        plugin.getMessageUtils().sendRawNoPrefix(player, "<yellow>/pbarea pos1</yellow> <gray>- Set position 1");
        plugin.getMessageUtils().sendRawNoPrefix(player, "<yellow>/pbarea pos2</yellow> <gray>- Set position 2");
        plugin.getMessageUtils().sendRawNoPrefix(player, "<yellow>/pbarea create <name></yellow> <gray>- Create area");
        plugin.getMessageUtils().sendRawNoPrefix(player, "<yellow>/pbarea delete <name></yellow> <gray>- Delete area");
        plugin.getMessageUtils().sendRawNoPrefix(player, "<yellow>/pbarea list</yellow> <gray>- List all areas");
    }

    /**
     * Handle player interaction with selection tool
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("practicebot.area")) {
            return;
        }

        if (event.getItem() == null) {
            return;
        }

        if (event.getItem().getType() != plugin.getAreaManager().getSelectionTool()) {
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        Location blockLoc = event.getClickedBlock().getLocation();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Set position 1
            event.setCancelled(true);
            plugin.getAreaManager().setPosition1(player, blockLoc);

            Map<String, String> placeholders = MessageUtils.builder()
                    .add("position", "1")
                    .add("x", (int) blockLoc.getX())
                    .add("y", (int) blockLoc.getY())
                    .add("z", (int) blockLoc.getZ())
                    .build();

            plugin.getMessageUtils().sendMessage(player, "area.position1-set", placeholders);

        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Set position 2
            event.setCancelled(true);
            plugin.getAreaManager().setPosition2(player, blockLoc);

            Map<String, String> placeholders = MessageUtils.builder()
                    .add("position", "2")
                    .add("x", (int) blockLoc.getX())
                    .add("y", (int) blockLoc.getY())
                    .add("z", (int) blockLoc.getZ())
                    .build();

            plugin.getMessageUtils().sendRaw(player, plugin.getMessageUtils().getMessage("area-tool.position-set", placeholders));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("pos1", "pos2", "create", "delete", "list"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("delete")) {
                completions.addAll(plugin.getAreaManager().getAreaNames());
            }
        }

        // Filter by what user typed
        String input = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(input));

        return completions;
    }
}