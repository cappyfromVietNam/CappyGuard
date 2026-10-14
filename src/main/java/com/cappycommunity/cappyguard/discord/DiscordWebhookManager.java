package com.cappycommunity.cappyguard.discord;

import com.cappycommunity.cappyguard.CappyGuard;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Level;

/**
 * High-performance, asynchronous Discord Webhook dispatcher using Java 21 HttpClient.
 */
public final class DiscordWebhookManager {

    private final CappyGuard plugin;
    private final HttpClient httpClient;

    private boolean enabled;
    private String webhookUrl;
    private String username;
    private String avatarUrl;
    private String mention;

    private boolean eventForceOp;
    private boolean eventCreativeAbuse;
    private boolean eventAdminAbuse;
    private boolean eventDangerousCmd;
    private boolean eventPluginSpoof;
    private boolean eventPacketGuard;

    private String colorCritical;
    private String colorWarning;
    private String colorInfo;

    private String titlePrefix;
    private boolean includeTimestamp;
    private boolean includePlayerHead;
    private String footerText;
    private String footerIconUrl;

    public DiscordWebhookManager(CappyGuard plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("discord.enabled", false);
        this.webhookUrl = config.getString("discord.webhook-url", "");
        this.username = config.getString("discord.username", "CappyGuard Security");
        this.avatarUrl = config.getString("discord.avatar-url", "");
        this.mention = config.getString("discord.mention", "");

        this.eventForceOp = config.getBoolean("discord.events.force-op", true);
        this.eventCreativeAbuse = config.getBoolean("discord.events.creative-abuse", true);
        this.eventAdminAbuse = config.getBoolean("discord.events.admin-abuse", true);
        this.eventDangerousCmd = config.getBoolean("discord.events.dangerous-commands", true);
        this.eventPluginSpoof = config.getBoolean("discord.events.plugin-spoof", false);
        this.eventPacketGuard = config.getBoolean("discord.events.packet-guard", true);

        this.colorCritical = config.getString("discord.embed-colors.critical", "#FF2A2A");
        this.colorWarning = config.getString("discord.embed-colors.warning", "#FF9900");
        this.colorInfo = config.getString("discord.embed-colors.info", "#3498DB");

        this.titlePrefix = config.getString("discord.embed-format.title-prefix", "🛡️ [CappyGuard Alert] ");
        this.includeTimestamp = config.getBoolean("discord.embed-format.include-timestamp", true);
        this.includePlayerHead = config.getBoolean("discord.embed-format.include-player-head", true);
        this.footerText = config.getString("discord.embed-format.footer-text", "CappyGuard Protection System • Paper 1.21");
        this.footerIconUrl = config.getString("discord.embed-format.footer-icon-url", "");
    }

    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.isBlank() && webhookUrl.startsWith("http");
    }

    public boolean isEventEnabled(String eventType) {
        if (!isEnabled()) return false;
        return switch (eventType.toLowerCase()) {
            case "force-op" -> eventForceOp;
            case "creative-abuse" -> eventCreativeAbuse;
            case "admin-abuse" -> eventAdminAbuse;
            case "dangerous-commands" -> eventDangerousCmd;
            case "plugin-spoof" -> eventPluginSpoof;
            case "packet-guard" -> eventPacketGuard;
            default -> true;
        };
    }

    /**
     * Creates a pre-configured DiscordEmbed based on event severity.
     */
    public DiscordEmbed createEmbed(String title, String severity, UUID playerUuid, String playerName) {
        DiscordEmbed embed = new DiscordEmbed()
                .setTitle((titlePrefix != null ? titlePrefix : "") + title)
                .setTimestamp(includeTimestamp)
                .setFooter(footerText, footerIconUrl);

        if (playerName != null) {
            String headUrl = (includePlayerHead && playerUuid != null)
                    ? "https://visage.surgeplay.com/bust/512/" + playerUuid
                    : null;
            embed.setAuthor(playerName, headUrl);
            if (headUrl != null) {
                embed.setThumbnail(headUrl);
            }
        }

        switch (severity.toLowerCase()) {
            case "critical" -> embed.setColorHex(colorCritical);
            case "warning" -> embed.setColorHex(colorWarning);
            default -> embed.setColorHex(colorInfo);
        }

        return embed;
    }

    /**
     * Asynchronously dispatches a Discord Embed payload to the configured webhook.
     */
    public void sendEmbedAsync(DiscordEmbed embed, boolean forceMention) {
        if (!isEnabled()) {
            return;
        }

        StringBuilder payload = new StringBuilder("{");
        if (username != null && !username.isBlank()) {
            payload.append("\"username\":").append(escape(username)).append(",");
        }
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            payload.append("\"avatar_url\":").append(escape(avatarUrl)).append(",");
        }
        if (forceMention && mention != null && !mention.isBlank()) {
            payload.append("\"content\":").append(escape(mention)).append(",");
        }
        payload.append("\"embeds\":[").append(embed.toJson()).append("]}");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "CappyGuard-Minecraft-Plugin")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            plugin.getLogger().log(Level.FINE, "Failed to deliver Discord webhook: " + throwable.getMessage());
                        }
                    });
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to create Discord webhook request: " + e.getMessage());
        }
    }

    private static String escape(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}

