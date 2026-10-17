package com.cappycommunity.cappyguard.modules.antidangerous;

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

import java.util.*;

/**
 * Anti Dangerous Commands Module.
 * Intercepts high-risk commands that could shut down or damage the server.
 */
public final class AntiDangerousModule extends AbstractModule {

    private final Set<String> blockedCommands = new HashSet<>();
    private final Set<UUID> bypassUuids = new HashSet<>();
    private final Set<String> bypassNames = new HashSet<>();

    public AntiDangerousModule(CappyGuard plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Anti Dangerous Commands";
    }

    @Override
    public String getConfigName() {
        return "anti-dangerous-commands";
    }

    @Override
    protected void onEnable() {
        blockedCommands.clear();
        bypassUuids.clear();
        bypassNames.clear();

        FileConfiguration config = getModuleConfig();
        if (config != null) {
            List<String> list = config.getStringList("blocked-commands");
            for (String cmd : list) {
                if (cmd != null && !cmd.isBlank()) {
                    String clean = cmd.trim().toLowerCase(Locale.ROOT);
                    if (!clean.startsWith("/")) clean = "/" + clean;
                    blockedCommands.add(clean);
                }
            }

            List<String> bypassList = config.getStringList("bypass-players");
            for (String p : bypassList) {
                if (p == null || p.isBlank()) continue;
                try {
                    bypassUuids.add(UUID.fromString(p.trim()));
                } catch (IllegalArgumentException e) {
                    bypassNames.add(p.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    @Override
    protected void onDisable() {
        blockedCommands.clear();
        bypassUuids.clear();
        bypassNames.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (isBypassed(player, "cappyguard.bypass.dangerous")) return;
        if (bypassUuids.contains(player.getUniqueId()) || bypassNames.contains(player.getName().toLowerCase(Locale.ROOT))) {
            return;
        }

        String message = event.getMessage().trim().toLowerCase(Locale.ROOT);
        String rootCommand = message.split("\\s+")[0];

        if (blockedCommands.contains(rootCommand) || blockedCommands.contains(message)) {
            event.setCancelled(true);
            MessageUtil.send(player, "&c[CappyGuard] This command is restricted to protect server safety!");

            LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
            if (logging != null) {
                logging.incrementBlockedEvents();
                logging.recordSecurityIncident(
                        "dangerous-commands",
                        "warning",
                        "&c[RESTRICTED CMD] &cPlayer &e" + player.getName() + " &ctried to execute: &e" + event.getMessage(),
                        "Player " + player.getName() + " (" + player.getUniqueId() + ") attempted restricted command: " + event.getMessage(),
                        player.getUniqueId(),
                        player.getName(),
                        List.of(
                                new DiscordEmbed.Field("Player", player.getName(), true),
                                new DiscordEmbed.Field("Command", event.getMessage(), true),
                                new DiscordEmbed.Field("Status", "Blocked", true)
                        )
                );
            }
        }
    }
}
