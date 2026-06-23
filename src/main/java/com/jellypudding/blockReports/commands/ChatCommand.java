package com.jellypudding.blockReports.commands;

import com.jellypudding.blockReports.BlockReports;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.chat.PlayerChatMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public class ChatCommand implements CommandExecutor {

    private final BlockReports plugin;

    public ChatCommand(BlockReports plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isChatCommandEnabled()) {
            sender.sendMessage(Component.text("[BlockReports] ", NamedTextColor.RED)
                .append(Component.text("The chat command is currently disabled.", NamedTextColor.WHITE)));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("[BlockReports] ", NamedTextColor.RED)
                .append(Component.text("Only players can use this command. Use /say from the console.", NamedTextColor.WHITE)));
            return true;
        }

        if (!player.hasPermission("blockreports.chat")) {
            player.sendMessage(Component.text("[BlockReports] ", NamedTextColor.RED)
                .append(Component.text("You don't have permission to chat.", NamedTextColor.WHITE)));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("[BlockReports] ", NamedTextColor.GOLD)
                .append(Component.text("Usage: /" + label + " <message>", NamedTextColor.WHITE)));
            return true;
        }

        String message = String.join(" ", args);

        if (message.startsWith("/")) {
            player.sendMessage(Component.text("[BlockReports] ", NamedTextColor.RED)
                .append(Component.text("Your message can't start with '/'.", NamedTextColor.WHITE)));
            return true;
        }

        broadcast(player, message);

        if (plugin.isLoggingEnabled()) {
            plugin.getLogger().info("Relayed chat command from " + player.getName() + ": " + message);
        }

        return true;
    }

    private void broadcast(Player player, String message) {
        Component messageComponent = Component.text(message);

        Set<Audience> viewers = new HashSet<>(plugin.getServer().getOnlinePlayers());
        viewers.add(plugin.getServer().getConsoleSender());
        SignedMessage signedMessage = (SignedMessage) PlayerChatMessage.system(message).adventureView();
        AsyncChatEvent event = new AsyncChatEvent(false, player, viewers, ChatRenderer.defaultRenderer(),
            messageComponent, messageComponent, signedMessage);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        // The client reads the name inside "<...>" and applies account-level chat filters to it 
        // (friends-only and blocked-player matching) which silently hides the message from players
        // who have those restrictions. Using "name: message" gives the client no name to match so
        // it shows for everyone.
        Component rendered = player.displayName()
            .append(Component.text(": ", NamedTextColor.WHITE))
            .append(event.message().colorIfAbsent(NamedTextColor.WHITE));
        plugin.getServer().broadcast(rendered);
    }
}
