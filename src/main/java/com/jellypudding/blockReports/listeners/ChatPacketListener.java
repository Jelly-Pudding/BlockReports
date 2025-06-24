package com.jellypudding.blockReports.listeners;

import com.jellypudding.blockReports.BlockReports;
import com.jellypudding.blockReports.util.ConnectionHelper;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ChatPacketListener extends ChannelDuplexHandler {
    
    private final BlockReports plugin;
    private static final String HANDLER_NAME = "blockreports_packet_interceptor";
    
    public ChatPacketListener(BlockReports plugin) {
        this.plugin = plugin;
    }

    public void inject() {
        // Inject for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            injectPlayer(player);
        }
        
        if (plugin.isLoggingEnabled()) {
            plugin.getLogger().info("Packet listener injected for " + Bukkit.getOnlinePlayers().size() + " players");
        }
    }
    
    public void injectPlayer(Player player) {
        injectPlayer(player, player.getAddress().getAddress());
    }
    
    public void injectPlayer(Player player, java.net.InetAddress address) {
        try {
            var connection = ConnectionHelper.requireConnectionByAddress(address);
            var channel = ConnectionHelper.getConnectionChannel(connection);
            
            // Remove existing handler if present
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }

            // Inject before packet_handler to catch all packets including login
            if (channel.pipeline().get("packet_handler") != null) {
                channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChatPacketListener(plugin));
            } else {
                channel.pipeline().addLast(HANDLER_NAME, new ChatPacketListener(plugin));
            }
            
            if (plugin.isLoggingEnabled()) {
                plugin.getLogger().info("✓ Injected packet listener for player: " + player.getName());
            }
                
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to inject packet listener for player " + 
                player.getName() + ": " + e.getMessage());
        }
    }
    
    public void uninject() {
        // Remove from all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            uninjectPlayer(player);
        }
        
        if (plugin.isLoggingEnabled()) {
            plugin.getLogger().info("Packet listener removed from all players");
        }
    }
    
    public void uninjectPlayer(Player player) {
        try {
            var connection = ConnectionHelper.findConnectionByAddress(player.getAddress().getAddress());
            if (connection != null) {
                var channel = connection.channel;
                if (channel != null && channel.pipeline().get(HANDLER_NAME) != null) {
                    channel.pipeline().remove(HANDLER_NAME);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove packet listener for player " + 
                player.getName() + ": " + e.getMessage());
        }
    }
    
    @Override
    public void write(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) throws Exception {
        // Handle outgoing packets        
        if (packet instanceof ClientboundLoginPacket loginPacket && plugin.isHideSecureChatWarning()) {
            packet = new ClientboundLoginPacket(
                loginPacket.playerId(),
                loginPacket.hardcore(),
                loginPacket.levels(),
                loginPacket.maxPlayers(),
                loginPacket.chunkRadius(),
                loginPacket.simulationDistance(),
                loginPacket.reducedDebugInfo(),
                loginPacket.showDeathScreen(),
                loginPacket.doLimitedCrafting(),
                loginPacket.commonPlayerSpawnInfo(),
                true // Pretend enforce-secureprofile is true to hide the popup warning.
            );
            
            if (plugin.isLoggingEnabled()) {
                plugin.getLogger().info("✓ Spoofed secure profile in login packet to hide chat warning");
            }
        } else if (packet instanceof ClientboundPlayerChatPacket chatPacket) {
            if (plugin.isStripServerSignatures()) {
                // Convert player chat to system chat to strip server signatures
                try {
                    // Access the known fields (from previous testing).
                    Field bodyField = ClientboundPlayerChatPacket.class.getDeclaredField("body");
                    Field chatTypeField = ClientboundPlayerChatPacket.class.getDeclaredField("chatType");
                    
                    bodyField.setAccessible(true);
                    chatTypeField.setAccessible(true);
                    
                    Object bodyValue = bodyField.get(chatPacket);
                    ChatType.Bound chatTypeBound = (ChatType.Bound) chatTypeField.get(chatPacket);
                    
                    // Extract content from the Packed body object
                    Method contentMethod = bodyValue.getClass().getDeclaredMethod("content");
                    contentMethod.setAccessible(true);
                    String textContent = (String) contentMethod.invoke(bodyValue);
                    
                    Component rawMessage = Component.literal(textContent);
                    
                    // Use the direct decorate method available in ChatType.Bound
                    Method decorateMethod = ChatType.Bound.class.getDeclaredMethod("decorate", Component.class);
                    decorateMethod.setAccessible(true);
                    Component decoratedMessage = (Component) decorateMethod.invoke(chatTypeBound, rawMessage);
                    
                    // Create system chat packet with the properly decorated message
                    ClientboundSystemChatPacket systemChatPacket = new ClientboundSystemChatPacket(decoratedMessage, false);

                    if (plugin.isLoggingEnabled()) {
                        plugin.getLogger().info("✓ Converted chat packet to system chat (stripped signature)");
                    }

                    super.write(ctx, systemChatPacket, promise);
                    return;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to convert chat packet: " + e.getMessage());
                }
            }
        }
        
        super.write(ctx, packet, promise);
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
        // Handle incoming packets
        if (packet instanceof ServerboundChatSessionUpdatePacket) {
            if (plugin.isBlockChatSessionUpdates()) {
                if (plugin.isLoggingEnabled()) {
                    plugin.getLogger().info("Blocked chat session update packet");
                }
                return; // Block the packet
            }
        }
        
        super.channelRead(ctx, packet);
    }
}