package com.jellypudding.blockReports.util;

import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import io.netty.channel.Channel;

import java.lang.reflect.Field;
import java.util.Objects;
import java.net.InetSocketAddress;
import java.net.InetAddress;

public class ConnectionHelper {

    private static Field cachedConnectionField = null;

    public static Connection findConnectionByAddress(InetAddress playerAddress) {
        for (Connection activeConnection : getAllServerConnections()) {
            if (activeConnection.getRemoteAddress() instanceof InetSocketAddress socketAddr) {
                if (socketAddr.getAddress() == playerAddress) {
                    return activeConnection;
                }
            }
        }
        return null;
    }

    public static Connection findLatestConnectionByAddress(InetAddress playerAddress) {
        Connection latestConnection = null;
        for (Connection activeConnection : getAllServerConnections()) {
            if (activeConnection.getRemoteAddress() instanceof InetSocketAddress socketAddr) {
                if (socketAddr.getAddress() == playerAddress) {
                    // Return the last one found (most recent connection)
                    latestConnection = activeConnection;
                }
            }
        }
        return latestConnection;
    }

    public static Channel getConnectionChannel(Connection connection) {
        return Objects.requireNonNull(connection.channel, 
            "Network channel is null for connection: " + connection.getRemoteAddress());
    }

    public static Connection requireConnectionByAddress(InetAddress address) {
        Connection found = findLatestConnectionByAddress(address);
        return Objects.requireNonNull(found, 
            "No active connection found for address: " + address);
    }

    public static Connection extractConnection(ServerGamePacketListenerImpl packetListener) {
        try {
            if (cachedConnectionField == null) {
                cachedConnectionField = findConnectionField();
            }
            return (Connection) cachedConnectionField.get(packetListener);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to extract network connection from packet listener", e);
        }
    }

    public static Iterable<Connection> getAllServerConnections() {
        return MinecraftServer.getServer().getConnection().getConnections();
    }

    private static Field findConnectionField() throws NoSuchFieldException {
        Class<?> currentClass = ServerGamePacketListenerImpl.class;

        while (currentClass != null) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (Connection.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        throw new NoSuchFieldException("Could not find Connection field in ServerGamePacketListenerImpl");
    }
}