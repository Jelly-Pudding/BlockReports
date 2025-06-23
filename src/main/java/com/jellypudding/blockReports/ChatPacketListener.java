package com.jellypudding.blockReports;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.scheduler.BukkitRunnable;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.lang.reflect.Field;

/**
 * Netty-based packet listener for intercepting and modifying chat packets
 * to remove signatures and prevent chat reporting
 */
public class ChatPacketListener implements Listener {
    
    private final BlockReports plugin;
    private final Map<UUID, Channel> playerChannels = new ConcurrentHashMap<>();
    private static final String HANDLER_NAME = "blockreports_chat_handler";
    
    // Counters for diagnostics
    private final AtomicInteger processedChatPackets = new AtomicInteger(0);
    private final AtomicInteger processedServerDataPackets = new AtomicInteger(0);
    private final AtomicInteger blockedSessionUpdates = new AtomicInteger(0);
    private final AtomicInteger strippedClientSignatures = new AtomicInteger(0);
    private final AtomicInteger failedPackets = new AtomicInteger(0);
    
    private final ExecutorService asyncExecutor;
    
    public ChatPacketListener(BlockReports plugin) {
        this.plugin = plugin;
        this.asyncExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "BlockReports-Worker");
            thread.setDaemon(true);
            return thread;
        });
        plugin.getLogger().info("Initializing chat packet listener for Minecraft 1.21.6");
    }
    
    public void inject() {
        // Register event listener for player join/quit
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // Inject currently online players
        final Player[] onlinePlayers = plugin.getServer().getOnlinePlayers().toArray(new Player[0]);
        
        new BukkitRunnable() {
            int index = 0;
            final int BATCH_SIZE = 10;
            
            @Override
            public void run() {
                int processed = 0;
                while (index < onlinePlayers.length && processed < BATCH_SIZE) {
                    Player player = onlinePlayers[index++];
                    if (injectPlayer(player)) {
                        processed++;
                    }
                }
                
                if (index >= onlinePlayers.length) {
                    plugin.getLogger().info("Chat packet listeners registered for " + index + " players");
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        
        asyncExecutor.execute(() -> {
            injectPlayer(player);
            
            if (plugin.isLoggingEnabled()) {
                plugin.getLogger().info("BlockReports now monitoring chat for " + player.getName());
            }
        });
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        
        asyncExecutor.execute(() -> {
            uninjectPlayer(player);
        });
    }
    
    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        String reason = event.getReason();
        
        // Prevent kicks related to chat signatures
        if (reason != null && (
            reason.contains("Received chat packet with missing or invalid signature") ||
            reason.contains("chat signature") ||
            reason.contains("secure chat") ||
            reason.toLowerCase().contains("signature")
        )) {
            event.setCancelled(true);
            
            if (plugin.isLoggingEnabled()) {
                plugin.getLogger().info("Prevented chat signature kick for " + event.getPlayer().getName() + ": " + reason);
            }
            
            // Optionally send a message to the player
            event.getPlayer().sendMessage("§6[BlockReports] §7Prevented a chat signature related kick.");
        }
    }
    
    public boolean injectPlayer(Player player) {
        try {
            if (!(player instanceof CraftPlayer)) {
                plugin.getLogger().warning("Player " + player.getName() + " is not a CraftPlayer, cannot inject");
                return false;
            }
            
            CraftPlayer craftPlayer = (CraftPlayer) player;
            Channel channel = getChannel(craftPlayer);
            
            if (channel == null) {
                plugin.getLogger().warning("Could not get channel for player " + player.getName());
                return false;
            }
            
            playerChannels.put(player.getUniqueId(), channel);
            
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                return true;
            }
            
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    try {
                        // Intercept outgoing player chat packets and convert to system messages
                        if (msg instanceof ClientboundPlayerChatPacket chatPacket && plugin.isStripSignatures()) {
                            processedChatPackets.incrementAndGet();
                            
                            // Extract the chat content
                            Component chatContent = extractChatContent(chatPacket);
                            
                            if (chatContent != null) {
                                // Create unsigned system chat message
                                ClientboundSystemChatPacket systemPacket = new ClientboundSystemChatPacket(chatContent, false);
                                
                                if (plugin.isLoggingEnabled()) {
                                    plugin.getLogger().fine("Converted signed chat to system message for " + player.getName());
                                }
                                
                                // Send system message instead of signed chat
                                super.write(ctx, systemPacket, promise);
                                return;
                            }
                        }
                        // Modify server data packet to hide secure chat warnings
                        else if (msg instanceof ClientboundServerDataPacket serverDataPacket && plugin.isHideSecureChatWarning()) {
                            processedServerDataPackets.incrementAndGet();
                            
                            try {
                                // Use reflection to access and modify the enforceSecureChat field
                                // Set to true to hide the scary popup (counterintuitive but correct)
                                Field enforceSecureChatField = ClientboundServerDataPacket.class.getDeclaredField("c");
                                enforceSecureChatField.setAccessible(true);
                                enforceSecureChatField.setBoolean(serverDataPacket, true);
                                
                                if (plugin.isLoggingEnabled()) {
                                    plugin.getLogger().fine("Modified server data packet to hide secure chat warning");
                                }
                            } catch (NoSuchFieldException | IllegalAccessException e) {
                                if (plugin.isLoggingEnabled()) {
                                    plugin.getLogger().warning("Failed to modify server data packet: " + e.getMessage());
                                }
                            }
                        }
                    } catch (Exception e) {
                        failedPackets.incrementAndGet();
                        if (plugin.isLoggingEnabled()) {
                            asyncExecutor.execute(() -> {
                                plugin.getLogger().warning("Error processing outgoing packet: " + e.getMessage());
                            });
                        }
                    }
                    
                    // Pass the packet along if it wasn't modified
                    super.write(ctx, msg, promise);
                }
                
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    try {
                        // Strip signatures from incoming chat messages
                        if (msg instanceof ServerboundChatPacket chatPacket && plugin.isStripSignatures()) {
                            strippedClientSignatures.incrementAndGet();
                            
                            try {
                                // Use reflection to remove the signature
                                Field signatureField = ServerboundChatPacket.class.getDeclaredField("signature");
                                signatureField.setAccessible(true);
                                signatureField.set(chatPacket, null);
                                
                                if (plugin.isLoggingEnabled()) {
                                    plugin.getLogger().fine("Stripped signature from chat message from " + player.getName());
                                }
                            } catch (NoSuchFieldException | IllegalAccessException e) {
                                if (plugin.isLoggingEnabled()) {
                                    plugin.getLogger().fine("Could not strip chat signature: " + e.getMessage());
                                }
                            }
                        }
                        // Strip signatures from incoming chat commands
                        else if (msg instanceof ServerboundChatCommandPacket commandPacket && plugin.isStripSignatures()) {
                            strippedClientSignatures.incrementAndGet();
                            
                            try {
                                // Use reflection to remove the signature
                                Field signatureField = ServerboundChatCommandPacket.class.getDeclaredField("signature");
                                signatureField.setAccessible(true);
                                signatureField.set(commandPacket, null);
                                
                                if (plugin.isLoggingEnabled()) {
                                    plugin.getLogger().fine("Stripped signature from chat command from " + player.getName());
                                }
                            } catch (NoSuchFieldException | IllegalAccessException e) {
                                if (plugin.isLoggingEnabled()) {
                                    plugin.getLogger().fine("Could not strip command signature: " + e.getMessage());
                                }
                            }
                        }
                        // Block chat session updates entirely
                        else if (msg instanceof ServerboundChatSessionUpdatePacket) {
                            blockedSessionUpdates.incrementAndGet();
                            
                            if (plugin.isLoggingEnabled()) {
                                plugin.getLogger().fine("Blocked chat session update from " + player.getName());
                            }
                            
                            // Don't pass this packet through at all
                            return;
                        }
                    } catch (Exception e) {
                        failedPackets.incrementAndGet();
                        if (plugin.isLoggingEnabled()) {
                            asyncExecutor.execute(() -> {
                                plugin.getLogger().warning("Error processing incoming packet: " + e.getMessage());
                            });
                        }
                    }
                    
                    // Pass the packet along if it wasn't blocked
                    super.channelRead(ctx, msg);
                }
            });
            
            if (plugin.isLoggingEnabled()) {
                asyncExecutor.execute(() -> {
                    plugin.getLogger().info("Successfully injected chat packet handler for " + player.getName());
                });
            }
            return true;
            
        } catch (Exception e) {
            final String errorMsg = e.getMessage();
            asyncExecutor.execute(() -> {
                plugin.getLogger().severe("Error injecting player " + player.getName() + ": " + errorMsg);
                if (plugin.isLoggingEnabled()) {
                    e.printStackTrace();
                }
            });
            return false;
        }
    }
    
    private Component extractChatContent(ClientboundPlayerChatPacket chatPacket) {
        try {
            // Try to get the unsigned content first (formatted message)
            Component unsignedContent = chatPacket.unsignedContent();
            if (unsignedContent != null) {
                return chatPacket.chatType().decorate(unsignedContent);
            }
            
            // Fallback to signed content
            String plainMessage = chatPacket.body().content();
            if (plainMessage != null && !plainMessage.isEmpty()) {
                Component messageComponent = Component.literal(plainMessage);
                return chatPacket.chatType().decorate(messageComponent);
            }
            
            return null;
        } catch (Exception e) {
            if (plugin.isLoggingEnabled()) {
                plugin.getLogger().warning("Error extracting chat content: " + e.getMessage());
            }
            return null;
        }
    }
    
    private Channel getChannel(CraftPlayer player) {
        try {
            ServerPlayer handle = player.getHandle();
            ServerGamePacketListenerImpl connection = handle.connection;
            Connection networkManager = connection.connection;
            return networkManager.channel;
        } catch (Exception e) {
            final String errorMsg = e.getMessage();
            asyncExecutor.execute(() -> {
                plugin.getLogger().severe("Error getting channel for player " + player.getName() + ": " + errorMsg);
            });
            return null;
        }
    }
    
    public void uninject() {
        // Unregister all players
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            uninjectPlayer(player);
        }
        
        // Shutdown executor
        asyncExecutor.shutdown();
        
        // Print final statistics
        plugin.getLogger().info("BlockReports packet processing statistics:");
        plugin.getLogger().info("Processed " + processedChatPackets.get() + " chat packets, " + 
                                processedServerDataPackets.get() + " server data packets, " + 
                                blockedSessionUpdates.get() + " blocked session updates, " + 
                                strippedClientSignatures.get() + " stripped client signatures, " + 
                                failedPackets.get() + " failed");
    }
    
    /**
     * Get diagnostic statistics for packet processing
     */
    public String getDiagnostics() {
        return String.format("Chat packets: %d, Server data: %d, Blocked sessions: %d, " +
                           "Stripped signatures: %d, Failed: %d",
                           processedChatPackets.get(), processedServerDataPackets.get(),
                           blockedSessionUpdates.get(), strippedClientSignatures.get(), failedPackets.get());
    }
    
    public void uninjectPlayer(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        
        Channel channel = playerChannels.remove(playerId);
        if (channel != null && channel.pipeline().get(HANDLER_NAME) != null) {
            channel.pipeline().remove(HANDLER_NAME);
            if (plugin.isLoggingEnabled()) {
                asyncExecutor.execute(() -> {
                    plugin.getLogger().info("Removed chat packet handler from " + player.getName());
                });
            }
        }
    }
} 