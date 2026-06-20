package com.jellypudding.blockReports.listeners;

import com.jellypudding.blockReports.BlockReports;
import com.jellypudding.blockReports.util.ConnectionHelper;
import net.minecraft.ChatFormatting;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.chat.RemoteChatSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;


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
            
            // Remove existing handler if present on this specific channel only
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
                String playerName = (player != null) ? player.getName() : address.getHostAddress();
                plugin.getLogger().info("Injected packet listener for player: " + playerName);
            }
                
        } catch (Exception e) {
            String playerName = (player != null) ? player.getName() : address.getHostAddress();
            plugin.getLogger().warning("Failed to inject packet listener for player " + 
                playerName + ": " + e.getMessage());
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
                loginPacket.onlineMode(),
                true // Pretend enforce-secure-profile is true to hide the popup warning.
            );

            if (plugin.isLoggingEnabled()) {
                plugin.getLogger().info("Spoofed secure profile in login packet to hide chat warning");
            }
        } else if (packet instanceof ClientboundPlayerChatPacket chatPacket) {
            if (plugin.isStripServerSignatures()) {
                // Convert player chat to system chat to strip server signatures
                try {
                    String textContent = chatPacket.body().content();
                    // Format as "name: message" instead of vanilla "<name> message". The
                    // client reads the name inside <...> to apply account chat filters
                    // (friends-only / blocked-player) which hide messages from affected players.
                    Component decoratedMessage = Component.empty()
                        .append(chatPacket.chatType().name())
                        .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(textContent).withStyle(ChatFormatting.WHITE));

                    ClientboundSystemChatPacket systemChatPacket = new ClientboundSystemChatPacket(decoratedMessage, false);

                    if (plugin.isLoggingEnabled()) {
                        plugin.getLogger().info("Converted chat packet to system chat (stripped signature)");
                    }

                    super.write(ctx, systemChatPacket, promise);
                    return;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to convert chat packet: " + e.getMessage());
                }
            }
        } else if (packet instanceof ClientboundDisguisedChatPacket disguisedPacket) {
            if (plugin.isStripServerSignatures()) {
                try {
                    Component decoratedMessage = disguisedPacket.chatType().decorate(disguisedPacket.message());

                    ClientboundSystemChatPacket systemChatPacket = new ClientboundSystemChatPacket(decoratedMessage, false);

                    if (plugin.isLoggingEnabled()) {
                        plugin.getLogger().info("Converted disguised chat packet to system chat");
                    }

                    super.write(ctx, systemChatPacket, promise);
                    return;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to convert disguised chat packet: " + e.getMessage());
                }
            }
        }

        super.write(ctx, packet, promise);
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
        // Handle incoming packets
        if (packet instanceof ServerboundChatSessionUpdatePacket sessionUpdatePacket) {
            if (plugin.isNeutraliseChatSessions()) {
                try {
                    // Neutralise the packet by creating a new one with null public key
                    // This maintains communication flow while preventing secure session establishment
                    RemoteChatSession.Data originalData = sessionUpdatePacket.chatSession();
                    // Intentionally pass null to neutralise the public key - this breaks secure chat while maintaining packet flow
                    @SuppressWarnings("null")
                    RemoteChatSession.Data neutralisedData = new RemoteChatSession.Data(originalData.sessionId(), null);
                    ServerboundChatSessionUpdatePacket neutralisedPacket = new ServerboundChatSessionUpdatePacket(neutralisedData);
                    
                    if (plugin.isLoggingEnabled()) {
                        plugin.getLogger().info("Successfully neutralised chat session update packet");
                    }

                    super.channelRead(ctx, neutralisedPacket);
                    return;
                } catch (Exception e) {
                    if (plugin.isLoggingEnabled()) {
                        plugin.getLogger().warning("Failed to neutralise chat session packet - falling back to blocking: " + e.getMessage());
                    }
                    return;
                }
            }
        }

        super.channelRead(ctx, packet);
    }
}
