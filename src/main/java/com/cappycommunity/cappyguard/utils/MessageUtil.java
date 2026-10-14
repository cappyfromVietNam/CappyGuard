package com.cappycommunity.cappyguard.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * High-performance message utility using Adventure MiniMessage and Legacy color codes.
 */
public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private static String prefix = "<gradient:#ff6b6b:#ee5a24>[CappyGuard]</gradient> ";

    private MessageUtil() {}

    public static void setPrefix(String newPrefix) {
        if (newPrefix != null) {
            prefix = newPrefix;
        }
    }

    public static String getPrefix() {
        return prefix;
    }

    /**
     * Parses a string containing either MiniMessage tags or legacy '&' color codes into a Component.
     */
    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        if (message.contains("&")) {
            message = LEGACY_SERIALIZER.serialize(LEGACY_SERIALIZER.deserialize(message));
        }
        try {
            return MINI_MESSAGE.deserialize(message);
        } catch (Exception e) {
            return LEGACY_SERIALIZER.deserialize(message);
        }
    }

    /**
     * Sends a formatted message with plugin prefix to the sender.
     */
    public static void send(CommandSender sender, String message) {
        if (sender != null && message != null && !message.isEmpty()) {
            sender.sendMessage(parse(prefix + message));
        }
    }

    /**
     * Sends a raw formatted message (without prefix) to the sender.
     */
    public static void sendRaw(CommandSender sender, String message) {
        if (sender != null && message != null && !message.isEmpty()) {
            sender.sendMessage(parse(message));
        }
    }

    /**
     * Broadcasts an alert message to console and all online players holding the given permission.
     */
    public static void broadcastAlert(String permission, String message) {
        Component component = parse(prefix + message);
        Bukkit.getConsoleSender().sendMessage(component);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permission == null || permission.isEmpty() || player.hasPermission(permission) || player.hasPermission("cappyguard.alert")) {
                player.sendMessage(component);
            }
        }
    }
}
