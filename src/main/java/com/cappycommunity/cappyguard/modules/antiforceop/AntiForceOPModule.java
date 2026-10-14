package com.cappycommunity.cappyguard.modules.antiforceop;

import com.cappycommunity.cappyguard.CappyGuard;
import com.cappycommunity.cappyguard.discord.DiscordEmbed;
import com.cappycommunity.cappyguard.modules.AbstractModule;
import com.cappycommunity.cappyguard.modules.logging.LoggingModule;
import com.cappycommunity.cappyguard.modules.opwhitelist.OPWhitelistModule;
import com.cappycommunity.cappyguard.utils.MessageUtil;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Anti Force OP Module.
 * Intercepts unauthorized operator promotions from players, console, and backdoor scripts.
 */
public final class AntiForceOPModule extends AbstractModule {

    private String action;
    private boolean blockOpCommand;
    private boolean blockConsoleOp;
    private int scanInterval;
    private String kickMessage;
    private String banMessage;
    private BukkitTask scanTask;

    public AntiForceOPModule(CappyGuard plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Anti Force OP";
    }

    @Override
    public String getConfigPath() {
        return "anti-force-op";
    }

    @Override
    protected void onEnable() {
        FileConfiguration config = plugin.getConfig();
        this.action = config.getString("anti-force-op.action", "KICK").toUpperCase(Locale.ROOT);
        this.blockOpCommand = config.getBoolean("anti-force-op.block-op-command", true);
        this.blockConsoleOp = config.getBoolean("anti-force-op.block-console-op", true);
        this.scanInterval = config.getInt("anti-force-op.scan-interval", 10);
        this.kickMessage = config.getString("anti-force-op.kick-message", "&c[CappyGuard] Unauthorized Operator privileges detected!");
        this.banMessage = config.getString("anti-force-op.ban-message", "&c[CappyGuard] Force OP attempt detected. You have been banned!");

        startScanTask();
    }

    @Override
    protected void onDisable() {
        if (scanTask != null && !scanTask.isCancelled()) {
            scanTask.cancel();
        }
    }

    private void startScanTask() {
        if (scanInterval > 0) {
            long ticks = scanInterval * 20L;
            this.scanTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::scanOnlinePlayers, ticks, ticks);
        }
    }

    private void scanOnlinePlayers() {
        OPWhitelistModule whitelist = plugin.getModuleManager().getModule(OPWhitelistModule.class);
        if (whitelist == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp() && !whitelist.isWhitelisted(player) && !isBypassed(player, "cappyguard.bypass.forceop")) {
                enforceAction(player, "Periodic scanner detected unwhitelisted operator status");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!blockOpCommand) return;

        String message = event.getMessage().toLowerCase(Locale.ROOT);
        if (message.startsWith("/op ") || message.equals("/op") ||
            message.startsWith("/minecraft:op ") || message.equals("/minecraft:op")) {

            Player player = event.getPlayer();
            OPWhitelistModule whitelist = plugin.getModuleManager().getModule(OPWhitelistModule.class);

            if (whitelist != null && !whitelist.isWhitelisted(player) && !isBypassed(player, "cappyguard.bypass.forceop")) {
                event.setCancelled(true);
                MessageUtil.send(player, "&cYou are not authorized to execute operator management commands!");

                LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
                if (logging != null) {
                    logging.incrementBlockedEvents();
                    logging.recordSecurityIncident(
                            "force-op",
                            "critical",
                            "&cPlayer &e" + player.getName() + " &ctried to execute &e" + event.getMessage() + " &cwithout authorization!",
                            "Player " + player.getName() + " (" + player.getUniqueId() + ") attempted unauthorized OP command: " + event.getMessage(),
                            player.getUniqueId(),
                            player.getName(),
                            List.of(
                                    new DiscordEmbed.Field("Player", player.getName() + " (" + player.getUniqueId() + ")", true),
                                    new DiscordEmbed.Field("Attempted Command", event.getMessage(), true),
                                    new DiscordEmbed.Field("Status", "Blocked & Logged", true)
                            )
                    );
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!blockConsoleOp) return;

        String cmd = event.getCommand().trim();
        String lower = cmd.toLowerCase(Locale.ROOT);

        if (lower.startsWith("op ") || lower.startsWith("minecraft:op ")) {
            String[] parts = cmd.split("\\s+");
            if (parts.length > 1) {
                String targetName = parts[1];
                OPWhitelistModule whitelist = plugin.getModuleManager().getModule(OPWhitelistModule.class);

                if (whitelist != null && !whitelist.isWhitelisted(null, targetName)) {
                    event.setCancelled(true);

                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target != null) {
                        target.setOp(false);
                    }

                    LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
                    if (logging != null) {
                        logging.incrementBlockedEvents();
                        logging.recordSecurityIncident(
                                "force-op",
                                "critical",
                                "&4[BACKDOOR ALERT] &cServer Console or script tried to OP unwhitelisted player &e" + targetName + "&c!",
                                "Console or backdoor script attempted unauthorized OP on target: " + targetName,
                                target != null ? target.getUniqueId() : null,
                                targetName,
                                List.of(
                                        new DiscordEmbed.Field("Target", targetName, true),
                                        new DiscordEmbed.Field("Origin", "Console / Plugin Script", true),
                                        new DiscordEmbed.Field("Action", "Blocked & Denied", true)
                                )
                        );
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp()) return;

        OPWhitelistModule whitelist = plugin.getModuleManager().getModule(OPWhitelistModule.class);
        if (whitelist != null && !whitelist.isWhitelisted(player) && !isBypassed(player, "cappyguard.bypass.forceop")) {
            enforceAction(player, "Player logged in with unauthorized Operator status");
        }
    }

    private void enforceAction(Player player, String reason) {
        player.setOp(false);

        LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
        if (logging != null) {
            logging.incrementBlockedEvents();
            logging.recordSecurityIncident(
                    "force-op",
                    "critical",
                    "&4[FORCE OP] &cRevoked operator status from &e" + player.getName() + "&c! Action: &e" + action,
                    reason + ": " + player.getName() + " (" + player.getUniqueId() + ") - Action: " + action,
                    player.getUniqueId(),
                    player.getName(),
                    List.of(
                            new DiscordEmbed.Field("Player", player.getName(), true),
                            new DiscordEmbed.Field("Reason", reason, true),
                            new DiscordEmbed.Field("Action Taken", action, true)
                    )
            );
        }

        switch (action) {
            case "BAN" -> {
                Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), MessageUtil.parse(banMessage).toString(), (Date) null, "CappyGuard");
                player.kick(MessageUtil.parse(banMessage));
            }
            case "KICK" -> player.kick(MessageUtil.parse(kickMessage));
            default -> MessageUtil.send(player, "&c[CappyGuard] Your Operator status has been revoked!");
        }
    }
}
