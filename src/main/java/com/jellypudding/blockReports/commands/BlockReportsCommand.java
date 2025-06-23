package com.jellypudding.blockReports.commands;

import com.jellypudding.blockReports.BlockReports;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class BlockReportsCommand implements CommandExecutor, TabCompleter {

    private final BlockReports plugin;

    public BlockReportsCommand(BlockReports plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("blockreports.admin")) {
            Component message = Component.text("[BlockReports] ", NamedTextColor.RED)
                .append(Component.text("You don't have permission to use this command.", NamedTextColor.WHITE));
            sender.sendMessage(message);
            return true;
        }

        if (args.length == 0) {
            Component message = Component.text("[BlockReports] ", NamedTextColor.GOLD)
                .append(Component.text("Usage: /blockreports reload", NamedTextColor.WHITE));
            sender.sendMessage(message);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfigManager();
            Component message = Component.text("[BlockReports] ", NamedTextColor.GREEN)
                .append(Component.text("Configuration has been reloaded.", NamedTextColor.WHITE));
            sender.sendMessage(message);
            return true;
        }

        Component message = Component.text("[BlockReports] ", NamedTextColor.RED)
            .append(Component.text("Unknown command. Usage: /blockreports reload", NamedTextColor.WHITE));
        sender.sendMessage(message);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("reload");
        }

        return completions;
    }
} 