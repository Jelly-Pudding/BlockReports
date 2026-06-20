package com.jellypudding.blockReports.commands;

import com.jellypudding.blockReports.BlockReports;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EmoteCommand implements CommandExecutor {

    private final BlockReports plugin;

    public EmoteCommand(BlockReports plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("[BlockReports] ", NamedTextColor.RED)
                .append(Component.text("Only players can use this command.", NamedTextColor.WHITE)));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("[BlockReports] ", NamedTextColor.GOLD)
                .append(Component.text("Usage: /" + label + " <action>", NamedTextColor.WHITE)));
            return true;
        }

        String message = String.join(" ", args);

        // Name keeps its colour; the "* " prefix and the action text are forced white.
        Component rendered = Component.text("* ", NamedTextColor.WHITE)
            .append(player.displayName())
            .append(Component.text(" " + message, NamedTextColor.WHITE));
        for (Player recipient : plugin.getServer().getOnlinePlayers()) {
            recipient.sendMessage(rendered);
        }
        plugin.getServer().getConsoleSender().sendMessage(rendered);

        if (plugin.isLoggingEnabled()) {
            plugin.getLogger().info("Relayed emote from " + player.getName() + ": " + message);
        }

        return true;
    }
}
