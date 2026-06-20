package com.jellypudding.blockReports.commands;

import com.jellypudding.blockReports.BlockReports;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MessageCommand implements CommandExecutor, TabCompleter {

    private final BlockReports plugin;

    // Last conversation partner for each player (used by /r).
    private final Map<UUID, UUID> lastContact = new ConcurrentHashMap<>();

    public MessageCommand(BlockReports plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(error("Only players can use this command."));
            return true;
        }

        boolean isReply = command.getName().equalsIgnoreCase("r");
        Player target;
        String message;

        if (isReply) {
            if (args.length == 0) {
                sendUsage(player, label, true);
                return true;
            }
            UUID targetId = lastContact.get(player.getUniqueId());
            target = targetId != null ? Bukkit.getPlayer(targetId) : null;
            if (target == null) {
                player.sendMessage(error("There is no one to reply to."));
                return true;
            }
            message = String.join(" ", args);
        } else {
            if (args.length < 2) {
                sendUsage(player, label, false);
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(error("Player '" + args[0] + "' is not online."));
                return true;
            }
            message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        }

        if (target.equals(player)) {
            player.sendMessage(error("You can't message yourself."));
            return true;
        }

        deliver(player, target, message);
        return true;
    }

    private void deliver(Player from, Player to, String message) {
        to.sendMessage(Component.text(from.getName() + " --> You: ", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text(message, NamedTextColor.WHITE)));
        from.sendMessage(Component.text("You --> " + to.getName() + ": ", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text(message, NamedTextColor.WHITE)));

        lastContact.put(from.getUniqueId(), to.getUniqueId());
        lastContact.put(to.getUniqueId(), from.getUniqueId());

        if (plugin.isLoggingEnabled()) {
            plugin.getLogger().info("PM " + from.getName() + " --> " + to.getName() + ": " + message);
        }
    }

    private void sendUsage(Player player, String label, boolean isReply) {
        String usage = isReply ? "/" + label + " <message>" : "/" + label + " <player> <message>";
        player.sendMessage(Component.text("[BlockReports] ", NamedTextColor.GOLD)
            .append(Component.text("Usage: " + usage, NamedTextColor.WHITE)));
    }

    private Component error(String text) {
        return Component.text("[BlockReports] ", NamedTextColor.RED)
            .append(Component.text(text, NamedTextColor.WHITE));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!command.getName().equalsIgnoreCase("r") && args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(online.getName());
                }
            }
        }
        return completions;
    }
}
