package com.jellypudding.blockReports.listeners;

import com.jellypudding.blockReports.BlockReports;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlockedChatNotifier implements Listener {

    private final BlockReports plugin;
    private final NamespacedKey communicatedKey;

    // Fast-path cache so active chatters don't schedule a player-data write per message.
    private final Set<UUID> communicatedThisSession = ConcurrentHashMap.newKeySet();

    public BlockedChatNotifier(BlockReports plugin) {
        this.plugin = plugin;
        this.communicatedKey = new NamespacedKey(plugin, "has_communicated");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.isChatCommandHintEnabled() || !plugin.isChatCommandEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        // Give the client a moment to settle, then hint if they've never communicated.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || hasCommunicated(player)) {
                return;
            }
            player.sendMessage(buildBlockedChatHint());
        }, 100L); // ~5 seconds
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        // Only the first message per session needs to touch player data.
        if (!communicatedThisSession.add(player.getUniqueId())) {
            return;
        }
        // AsyncChatEvent can fire off the main thread; player-data writes must be on it.
        plugin.getServer().getScheduler().runTask(plugin, () -> markCommunicated(player));
    }

    private boolean hasCommunicated(Player player) {
        return communicatedThisSession.contains(player.getUniqueId())
            || player.getPersistentDataContainer().has(communicatedKey, PersistentDataType.BYTE);
    }

    private void markCommunicated(Player player) {
        player.getPersistentDataContainer().set(communicatedKey, PersistentDataType.BYTE, (byte) 1);
    }

    private Component buildBlockedChatHint() {
        return Component.text("If you can't send chat messages, use ", NamedTextColor.YELLOW)
            .append(Component.text("/chat <message>", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand("/chat "))
                .hoverEvent(HoverEvent.showText(Component.text("Click to start typing a message"))))
            .append(Component.text(" to talk.", NamedTextColor.YELLOW));
    }
}
