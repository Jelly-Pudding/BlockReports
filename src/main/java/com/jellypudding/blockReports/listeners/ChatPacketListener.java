package com.jellypudding.blockReports.listeners;

import com.jellypudding.blockReports.BlockReports;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

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
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
            var channel = serverPlayer.connection.connection.channel;
            
            // Remove existing handler if present
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
            
            // Add our handler before the packet encoder
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, 
                new ChatPacketListener(plugin));
                
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
            ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
            var channel = serverPlayer.connection.connection.channel;
            
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove packet listener for player " + 
                player.getName() + ": " + e.getMessage());
        }
    }
    
    @Override
    public void write(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) throws Exception {
        // Handle outgoing packets
        if (packet instanceof ClientboundPlayerChatPacket chatPacket) {
            if (plugin.isStripServerSignatures()) {
                // Convert player chat to system chat to strip server signatures
                // Use reflection to access the packet fields and properly format the message
                try {
                    Field messageField = ClientboundPlayerChatPacket.class.getDeclaredField("message");
                    Field chatTypeField = ClientboundPlayerChatPacket.class.getDeclaredField("chatType");
                    
                    messageField.setAccessible(true);
                    chatTypeField.setAccessible(true);
                    
                    Component rawMessage = (Component) messageField.get(chatPacket);
                    ChatType.Bound chatTypeBound = (ChatType.Bound) chatTypeField.get(chatPacket);
                    
                    // Resolve the chat type and decorate the message properly
                    Method resolveMethod = ChatType.Bound.class.getDeclaredMethod("resolve", net.minecraft.core.RegistryAccess.class);
                    resolveMethod.setAccessible(true);
                    
                    Optional<?> resolvedChatType = (Optional<?>) 
                        resolveMethod.invoke(chatTypeBound, MinecraftServer.getServer().registryAccess());
                    
                    Component decoratedMessage;
                    if (resolvedChatType.isPresent()) {
                        // Get the resolved chat type object
                        Object chatTypeNetwork = resolvedChatType.get();
                        
                        // Get the chat decoration method
                        Method chatMethod = chatTypeNetwork.getClass().getDeclaredMethod("chat");
                        chatMethod.setAccessible(true);
                        Object chatFunction = chatMethod.invoke(chatTypeNetwork);
                        
                        // Decorate the message with proper formatting
                        Method decorateMethod = chatFunction.getClass().getDeclaredMethod("decorate", Component.class);
                        decorateMethod.setAccessible(true);
                        decoratedMessage = (Component) decorateMethod.invoke(chatFunction, rawMessage);
                    } else {
                        // Fallback to raw message if resolution fails
                        decoratedMessage = rawMessage;
                    }
                    
                    // Create system chat packet with the properly decorated message
                    ClientboundSystemChatPacket systemChatPacket = new ClientboundSystemChatPacket(decoratedMessage, false);
                    
                    if (plugin.isLoggingEnabled()) {
                        plugin.getLogger().info("Converted player chat packet to system chat packet");
                    }
                    
                    super.write(ctx, systemChatPacket, promise);
                    return;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to convert chat packet: " + e.getMessage());
                }
            }
        } else if (packet instanceof ClientboundServerDataPacket serverDataPacket) {
            if (plugin.isHideSecureChatWarning()) {
                // Modify server data packet to hide secure chat warning
                try {
                    // Use reflection to access private fields
                    Field motdField = ClientboundServerDataPacket.class.getDeclaredField("motd");
                    Field iconField = ClientboundServerDataPacket.class.getDeclaredField("iconBytes");
                    
                    motdField.setAccessible(true);
                    iconField.setAccessible(true);
                    
                    Component motd = (Component) motdField.get(serverDataPacket);
                    Object iconBytesObj = iconField.get(serverDataPacket);
                    
                    // Create new packet without enforceSecureChat parameter
                    ClientboundServerDataPacket modifiedPacket = new ClientboundServerDataPacket(motd, (Optional<byte[]>) iconBytesObj);
                    
                    if (plugin.isLoggingEnabled()) {
                        plugin.getLogger().info("Modified server data packet to hide secure chat warning");
                    }
                    
                    super.write(ctx, modifiedPacket, promise);
                    return;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to modify server data packet: " + e.getMessage());
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