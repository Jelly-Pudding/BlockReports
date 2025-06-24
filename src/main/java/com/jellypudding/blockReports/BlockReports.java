package com.jellypudding.blockReports;

import com.jellypudding.blockReports.commands.BlockReportsCommand;
import com.jellypudding.blockReports.listeners.ChatPacketListener;
import com.jellypudding.blockReports.listeners.KickListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlockReports extends JavaPlugin implements Listener {

    private ChatPacketListener packetListener;
    private FileConfiguration config;
    private boolean enableLogging;

    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        loadConfig();

        // Enforce the enforce-secure-profile setting in server.properties
        enforceServerProperties();

        // Initialise and inject packet listener
        packetListener = new ChatPacketListener(this);
        packetListener.inject();

        // Register listeners
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new KickListener(this), this);

        // Register command
        getCommand("blockreports").setExecutor(new BlockReportsCommand(this));
        getCommand("blockreports").setTabCompleter(new BlockReportsCommand(this));

        getLogger().info("BlockReports has been enabled. " +
                         "Strip signatures=" + isStripServerSignatures() + 
                         ", Hide warnings=" + isHideSecureChatWarning() + 
                         ", Debug=" + isLoggingEnabled());
    }

    @Override
    public void onDisable() {
        if (packetListener != null) {
            packetListener.uninject();
        }
        
        getLogger().info("BlockReports has been disabled.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (packetListener != null) {
            // Inject immediately on join with highest priority
            packetListener.injectPlayer(event.getPlayer());
        }
    }

    private void enforceServerProperties() {
        try {
            MinecraftServer minecraftServer = MinecraftServer.getServer();
            if (!(minecraftServer instanceof DedicatedServer server)) {
                getLogger().warning("Not running on a dedicated server so cannot enforce server properties");
                return;
            }

            DedicatedServerProperties properties = server.getProperties();

            var enforceSecureProfileField = properties.getClass().getDeclaredField("enforceSecureProfile");
            enforceSecureProfileField.setAccessible(true);

            boolean currentValue = (boolean) enforceSecureProfileField.get(properties);
            if (currentValue) {
                enforceSecureProfileField.set(properties, false);
                getLogger().info("Enforced enforce-secure-profile=false in server properties");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to enforce server properties: " + e.getMessage());
        }
    }

    public void reloadConfigManager() {
        reloadConfig();
        loadConfig();

        // Reinject packet listener with new settings
        if (packetListener != null) {
            packetListener.uninject();
            packetListener.inject();
        }

        getLogger().info("Configuration reloaded successfully!");
    }

    private void loadConfig() {
        config = getConfig();
        enableLogging = config.getBoolean("enable-logging", false);
    }

    // Configuration getters
    public boolean isStripServerSignatures() {
        return config.getBoolean("strip-server-signatures", true);
    }

    public boolean isHideSecureChatWarning() {
        return config.getBoolean("hide-secure-chat-warning", true);
    }

    public boolean isBlockChatSessionUpdates() {
        return config.getBoolean("block-chat-session-updates", true);
    }

    public boolean isPreventChatKicks() {
        return config.getBoolean("prevent-chat-kicks", true);
    }

    public boolean isLoggingEnabled() {
        return enableLogging;
    }

    public ChatPacketListener getPacketListener() {
        return packetListener;
    }
}
