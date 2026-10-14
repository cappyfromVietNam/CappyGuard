package com.cappycommunity.cappyguard.modules.logging;

import com.cappycommunity.cappyguard.CappyGuard;
import com.cappycommunity.cappyguard.discord.DiscordEmbed;
import com.cappycommunity.cappyguard.modules.AbstractModule;
import com.cappycommunity.cappyguard.utils.AsyncLogBuffer;
import com.cappycommunity.cappyguard.utils.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central Logging & Alerting Module.
 * Dispatches alerts across In-Game Staff Chat, Async File I/O, and Discord Webhooks.
 */
public final class LoggingModule extends AbstractModule {

    private final AtomicLong blockedEventsCount = new AtomicLong(0);
    private final AtomicLong loggedEventsCount = new AtomicLong(0);

    private AsyncLogBuffer logBuffer;
    private boolean fileLogging;
    private boolean staffAlerts;

    public LoggingModule(CappyGuard plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Logging & Alert";
    }

    @Override
    public String getConfigPath() {
        return "logging";
    }

    @Override
    protected void onEnable() {
        FileConfiguration config = plugin.getConfig();
        this.fileLogging = config.getBoolean("logging.file-logging", true);
        this.staffAlerts = config.getBoolean("logging.staff-alerts", true);

        String relativePath = config.getString("logging.log-file", "logs/cappyguard.log");
        if (this.logBuffer != null) {
            this.logBuffer.shutdown();
        }
        this.logBuffer = new AsyncLogBuffer(plugin, relativePath);
    }

    @Override
    protected void onDisable() {
        if (this.logBuffer != null) {
            this.logBuffer.shutdown();
        }
    }

    public void incrementBlockedEvents() {
        blockedEventsCount.incrementAndGet();
    }

    public long getBlockedEventsCount() {
        return blockedEventsCount.get();
    }

    public long getLoggedEventsCount() {
        return loggedEventsCount.get();
    }

    /**
     * Centralized dispatch for all security violations and audits.
     */
    public void recordSecurityIncident(
            String category,
            String severity,
            String inGameMessage,
            String logFileMessage,
            UUID playerUuid,
            String playerName,
            List<DiscordEmbed.Field> discordFields
    ) {
        loggedEventsCount.incrementAndGet();

        // 1. In-Game Staff Alert
        if (staffAlerts) {
            String permission = "cappyguard.alert." + category.toLowerCase().replace("-", "").replace("_", "");
            MessageUtil.broadcastAlert(permission, inGameMessage);
        }

        // 2. Async File Logging
        if (fileLogging && logBuffer != null) {
            logBuffer.log(severity.toUpperCase(), "[" + category + "] " + logFileMessage);
        }

        // 3. Discord Webhook Delivery
        if (plugin.getDiscordWebhookManager().isEventEnabled(category)) {
            DiscordEmbed embed = plugin.getDiscordWebhookManager()
                    .createEmbed(category.toUpperCase() + " VIOLATION", severity, playerUuid, playerName)
                    .setDescription(inGameMessage);

            if (discordFields != null) {
                for (DiscordEmbed.Field field : discordFields) {
                    embed.addField(field.name, field.value, field.inline);
                }
            }

            boolean isCritical = "critical".equalsIgnoreCase(severity);
            plugin.getDiscordWebhookManager().sendEmbedAsync(embed, isCritical);
        }
    }
}

