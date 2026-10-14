package com.cappycommunity.cappyguard.modules.antiadminabuse;

import com.cappycommunity.cappyguard.CappyGuard;
import com.cappycommunity.cappyguard.discord.DiscordEmbed;
import com.cappycommunity.cappyguard.modules.AbstractModule;
import com.cappycommunity.cappyguard.modules.logging.LoggingModule;
import com.cappycommunity.cappyguard.utils.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti Admin Abuse Module.
 * Prevents staff from spamming administrative commands and self-giving items.
 */
public final class AntiAdminAbuseModule extends AbstractModule {

    private boolean rateLimitEnabled;
    private int maxCommands;
    private int timeWindow;
    private String action;
    private boolean blockSelfGive;

    // Sliding window timestamps per player
    private final Map<UUID, ArrayDeque<Long>> commandTimestamps = new ConcurrentHashMap<>();

    public AntiAdminAbuseModule(CappyGuard plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Anti Admin Abuse";
    }

    @Override
    public String getConfigPath() {
        return "anti-admin-abuse";
    }

    @Override
    protected void onEnable() {
        FileConfiguration config = plugin.getConfig();
        this.rateLimitEnabled = config.getBoolean("anti-admin-abuse.rate-limit.enabled", true);
        this.maxCommands = config.getInt("anti-admin-abuse.rate-limit.max-commands", 10);
        this.timeWindow = config.getInt("anti-admin-abuse.rate-limit.time-window", 60);
        this.action = config.getString("anti-admin-abuse.rate-limit.action", "WARN").toUpperCase(Locale.ROOT);
        this.blockSelfGive = config.getBoolean("anti-admin-abuse.block-self-give", true);
    }

    @Override
    protected void onDisable() {
        commandTimestamps.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().trim();
        String lower = message.toLowerCase(Locale.ROOT);

        // 1. Self-Give Detection
        if (blockSelfGive && isMonitoredStaff(player, "cappyguard.staff.selfgive")) {
            if (isGiveCommand(lower, player.getName().toLowerCase(Locale.ROOT))) {
                event.setCancelled(true);
                MessageUtil.send(player, "&c[CappyGuard] Staff are not permitted to give items to themselves!");

                LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
                if (logging != null) {
                    logging.incrementBlockedEvents();
                    logging.recordSecurityIncident(
                            "admin-abuse",
                            "warning",
                            "&6[ADMIN ABUSE] &cStaff &e" + player.getName() + " &ctried to self-give items via: &e" + message,
                            "Staff " + player.getName() + " attempted self-give command: " + message,
                            player.getUniqueId(),
                            player.getName(),
                            List.of(
                                    new DiscordEmbed.Field("Player", player.getName(), true),
                                    new DiscordEmbed.Field("Command", message, true),
                                    new DiscordEmbed.Field("Violation", "Self-Give Prohibited", true)
                            )
                    );
                }
                return;
            }
        }

        // 2. Rate Limiting Check
        if (rateLimitEnabled && isMonitoredStaff(player, "cappyguard.staff.ratelimit")) {
            if (isBypassed(player, "cappyguard.bypass.ratelimit")) return;

            long now = System.currentTimeMillis();
            long windowMillis = timeWindow * 1000L;

            ArrayDeque<Long> queue = commandTimestamps.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
            synchronized (queue) {
                // Purge outdated timestamps
                while (!queue.isEmpty() && (now - queue.peekFirst()) > windowMillis) {
                    queue.pollFirst();
                }

                queue.addLast(now);

                if (queue.size() > maxCommands) {
                    event.setCancelled(true);
                    handleRateLimitBreach(player, queue.size(), message);
                }
            }
        }
    }

    private boolean isGiveCommand(String lower, String playerNameLower) {
        if (lower.startsWith("/give ") || lower.startsWith("/minecraft:give ")) {
            String[] parts = lower.split("\\s+");
            if (parts.length > 1) {
                String target = parts[1];
                return target.equals("@s") || target.equals("@p") || target.equals(playerNameLower);
            }
        }
        return false;
    }

    private void handleRateLimitBreach(Player player, int commandCount, String lastCommand) {
        LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
        if (logging != null) {
            logging.incrementBlockedEvents();
            logging.recordSecurityIncident(
                    "admin-abuse",
                    "warning",
                    "&6[RATE LIMIT] &cStaff &e" + player.getName() + " &cexceeded command rate limit: &e" + commandCount + " cmds in " + timeWindow + "s!",
                    "Staff " + player.getName() + " exceeded command rate limit (" + commandCount + " commands in " + timeWindow + "s). Last: " + lastCommand,
                    player.getUniqueId(),
                    player.getName(),
                    List.of(
                            new DiscordEmbed.Field("Player", player.getName(), true),
                            new DiscordEmbed.Field("Command Count", commandCount + " / " + maxCommands, true),
                            new DiscordEmbed.Field("Window", timeWindow + " seconds", true),
                            new DiscordEmbed.Field("Last Command", lastCommand, false)
                    )
            );
        }

        switch (action) {
            case "KICK" -> player.kick(MessageUtil.parse("&c[CappyGuard] Exceeded administrative command rate limit!"));
            case "TEMPBAN" -> {
                Date expire = Date.from(Instant.now().plusSeconds(600)); // 10 minutes
                player.getServer().getBanList(org.bukkit.BanList.Type.NAME).addBan(player.getName(), "Command spamming rate limit exceeded", expire, "CappyGuard");
                player.kick(MessageUtil.parse("&c[CappyGuard] Temporarily banned for command abuse."));
            }
            default -> MessageUtil.send(player, "&c[CappyGuard] Warning: You are executing administrative commands too rapidly!");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        commandTimestamps.remove(event.getPlayer().getUniqueId());
    }
}

