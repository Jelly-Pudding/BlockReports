package com.jellypudding.blockReports.listeners;

import com.jellypudding.blockReports.BlockReports;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

public class KickListener implements Listener {

    private final BlockReports plugin;

    public KickListener(BlockReports plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        if (!plugin.isPreventChatKicks()) {
            return;
        }

        try {
            Class<? extends PlayerKickEvent> kickClass = event.getClass();
            java.lang.reflect.Method getCause = kickClass.getMethod("getCause");
            Enum<?> cause = (Enum<?>) getCause.invoke(event);
            String paperReason = cause.name();

            if (paperReason.equals("OUT_OF_ORDER_CHAT") ||
                paperReason.equals("TOO_MANY_PENDING_CHATS") ||
                paperReason.equals("EXPIRED_PROFILE_PUBLIC_KEY") ||
                paperReason.equals("CHAT_VALIDATION_FAILED") ||
                paperReason.equals("UNSIGNED_CHAT")) {

                event.setCancelled(true);
                sendPreventedKickMessage(event, paperReason);
                return;
            }
        } catch (Exception e) {
            // Ignore reflection errors and fall back to string checking.
        }

        String reason = PlainTextComponentSerializer.plainText().serialize(event.reason());
        if (reason.equals("Received chat packet with missing or invalid signature.")) {
            event.setCancelled(true);
            sendPreventedKickMessage(event, reason);
        }
    }

    private void sendPreventedKickMessage(PlayerKickEvent event, String reason) {
        // Send message to player about prevented kick
        Component message = Component.text("BlockReports prevented a chat-related kick: ", NamedTextColor.YELLOW)
            .append(Component.text(reason, NamedTextColor.RED))
            .append(Component.newline())
            .append(Component.text("You may need to rejoin the server if you experience issues with chat.", NamedTextColor.GOLD));
        event.getPlayer().sendMessage(message);

        if (plugin.isLoggingEnabled()) {
            plugin.getLogger().info("Prevented chat-related kick for player " + 
                event.getPlayer().getName() + ": " + reason);
        }
    }
}
