package com.jellypudding.blockReports;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

import java.util.Properties;

/**
 * BlockReports - A Minecraft Paper 1.21.6 plugin to prevent chat reporting
 */
public final class BlockReports extends JavaPlugin implements Listener {

    private boolean enableLogging;
    private boolean stripSignatures;
    private boolean hideSecureChatWarning;
    private boolean showJoinMessage;
    private Component joinMessage;
    private Component prefix;
    
    private ChatPacketListener packetListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        
        // Enforce secure profile settings in server properties
        enforceServerProperties();
        
        // Initialize and inject packet listener
        packetListener = new ChatPacketListener(this);
        packetListener.inject();
        
        // Register Bukkit events for join messages
        getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().info("BlockReports v" + getPluginMeta().getVersion() + " enabled!");
        getLogger().info("Chat reporting has been disabled on this server.");
        getLogger().info("Direct packet injection is active for signature stripping.");
        getLogger().info("Configuration: Strip signatures=" + isStripSignatures() + 
                        ", Hide warnings=" + isHideSecureChatWarning() + 
                        ", Debug=" + isLoggingEnabled());
        
        if (enableLogging) {
            getLogger().info("Debug logging is enabled");
        }
    }

    @Override
    public void onDisable() {
        // Clean up packet listener
        if (packetListener != null) {
            packetListener.uninject();
        }
        getLogger().info("BlockReports disabled!");
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        
        enableLogging = config.getBoolean("enable-logging", true);
        stripSignatures = config.getBoolean("strip-chat-signatures", true);
        hideSecureChatWarning = config.getBoolean("hide-secure-chat-warning", true);
        showJoinMessage = config.getBoolean("show-join-message", true);
        
        MiniMessage miniMessage = MiniMessage.miniMessage();
        String joinMessageStr = config.getString("join-message", "<gray>[BlockReports] Chat reporting is disabled on this server");
        String prefixStr = config.getString("prefix", "<dark_gray>[<gold>BlockReports<dark_gray>]");
        
        joinMessage = miniMessage.deserialize(joinMessageStr);
        prefix = miniMessage.deserialize(prefixStr);
    }
    
    // Getter methods for ChatPacketListener
    public boolean isLoggingEnabled() {
        return enableLogging;
    }
    
    public boolean isStripSignatures() {
        return stripSignatures;
    }
    
    public boolean isHideSecureChatWarning() {
        return hideSecureChatWarning;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        if (showJoinMessage) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                player.sendMessage(joinMessage);
            }, 20L);
        }
        
        if (enableLogging) {
            getLogger().info("Player " + player.getName() + " joined - chat signatures will be stripped");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                           @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("blockreports")) {
            if (args.length == 0) {
                sender.sendMessage(prefix.append(Component.space())
                    .append(Component.text("BlockReports v" + getPluginMeta().getVersion(), NamedTextColor.GOLD)));
                sender.sendMessage(prefix.append(Component.space())
                    .append(Component.text("Commands:", NamedTextColor.GRAY)));
                sender.sendMessage(Component.text("  /blockreports reload - Reload configuration", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /blockreports stats - Show packet statistics", NamedTextColor.GRAY));
                return true;
            }
            
            if (!sender.hasPermission("blockreports.admin")) {
                sender.sendMessage(prefix.append(Component.space())
                    .append(Component.text("You don't have permission to use this command", NamedTextColor.RED)));
                return true;
            }
            
            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadConfig();
                sender.sendMessage(prefix.append(Component.space())
                    .append(Component.text("Configuration reloaded!", NamedTextColor.GREEN)));
                return true;
            } else if (args[0].equalsIgnoreCase("stats")) {
                sender.sendMessage(prefix.append(Component.space())
                    .append(Component.text("Packet Statistics:", NamedTextColor.GOLD)));
                sender.sendMessage(Component.text("  " + packetListener.getDiagnostics(), NamedTextColor.GRAY));
                return true;
            } else {
                sender.sendMessage(prefix.append(Component.space())
                    .append(Component.text("Unknown subcommand. Use /blockreports for help.", NamedTextColor.RED)));
                return true;
            }
        }
        
        return false;
    }

    /**
     * Enforce server properties to disable secure profile enforcement
     */
    private void enforceServerProperties() {
        try {
            MinecraftServer minecraftServer = MinecraftServer.getServer();
            if (!(minecraftServer instanceof DedicatedServer server)) {
                getLogger().warning("Not running on a dedicated server, cannot enforce server properties");
                return;
            }
            
            server.settings.update((config) -> {
                final Properties newProps = new Properties(config.properties);
                newProps.setProperty("enforce-secure-profile", "false");
                
                return config.reload(server.registryAccess(), newProps, server.options);
            });
            
            getLogger().info("Enforced enforce-secure-profile=false in server properties");
        } catch (Exception e) {
            getLogger().warning("Failed to enforce server properties: " + e.getMessage());
            getLogger().warning("Please manually set enforce-secure-profile=false in server.properties");
        }
    }
}
