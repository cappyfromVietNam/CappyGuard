package com.cappycommunity.cappyguard.modules.antigamemode;

import com.cappycommunity.cappyguard.CappyGuard;
import com.cappycommunity.cappyguard.discord.DiscordEmbed;
import com.cappycommunity.cappyguard.modules.AbstractModule;
import com.cappycommunity.cappyguard.modules.logging.LoggingModule;
import com.cappycommunity.cappyguard.utils.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

import java.util.*;

/**
 * Anti Gamemode Abuse Module.
 * Restricts unauthorized switching into Creative or Spectator modes.
 */
public final class AntiGamemodeModule extends AbstractModule {

    private final EnumSet<GameMode> blockedGamemodes = EnumSet.noneOf(GameMode.class);
    private final Set<UUID> allowedUuids = new HashSet<>();
    private final Set<String> allowedNames = new HashSet<>();
    private final Set<String> allowedWorlds = new HashSet<>();

    public AntiGamemodeModule(CappyGuard plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Anti Gamemode Abuse";
    }

    @Override
    public String getConfigPath() {
        return "anti-gamemode";
    }

    @Override
    protected void onEnable() {
        blockedGamemodes.clear();
        allowedUuids.clear();
        allowedNames.clear();
        allowedWorlds.clear();

        FileConfiguration config = plugin.getConfig();
        List<String> gmList = config.getStringList("anti-gamemode.blocked-gamemodes");
        for (String gm : gmList) {
            try {
                blockedGamemodes.add(GameMode.valueOf(gm.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {}
        }

        List<String> playerList = config.getStringList("anti-gamemode.allowed-players");
        for (String p : playerList) {
            if (p == null || p.isBlank()) continue;
            try {
                allowedUuids.add(UUID.fromString(p.trim()));
            } catch (IllegalArgumentException e) {
                allowedNames.add(p.trim().toLowerCase(Locale.ROOT));
            }
        }

        List<String> worldList = config.getStringList("anti-gamemode.allowed-worlds");
        for (String w : worldList) {
            if (w != null && !w.isBlank()) {
                allowedWorlds.add(w.trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    @Override
    protected void onDisable() {
        blockedGamemodes.clear();
        allowedUuids.clear();
        allowedNames.clear();
        allowedWorlds.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        GameMode newGm = event.getNewGameMode();
        if (!blockedGamemodes.contains(newGm)) {
            return;
        }

        Player player = event.getPlayer();

        // Check if player is exempt
        if (isBypassed(player, "cappyguard.bypass.gamemode")) return;
        if (allowedUuids.contains(player.getUniqueId()) || allowedNames.contains(player.getName().toLowerCase(Locale.ROOT))) {
            return;
        }
        if (allowedWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return;
        }

        // Apply to staff members or everyone if monitored
        if (isMonitoredStaff(player, "cappyguard.staff.gamemode")) {
            event.setCancelled(true);
            MessageUtil.send(player, "&c[CappyGuard] You are not permitted to switch to " + newGm.name() + " mode!");

            LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
            if (logging != null) {
                logging.incrementBlockedEvents();
                logging.recordSecurityIncident(
                        "gamemode-changes",
                        "info",
                        "&e[GAMEMODE BLOCKED] &cStaff &e" + player.getName() + " &ctried to switch to &e" + newGm.name() + " &cin world &e" + player.getWorld().getName(),
                        "Staff " + player.getName() + " attempted unauthorized gamemode switch to " + newGm.name() + " in world " + player.getWorld().getName(),
                        player.getUniqueId(),
                        player.getName(),
                        List.of(
                                new DiscordEmbed.Field("Player", player.getName(), true),
                                new DiscordEmbed.Field("Attempted Gamemode", newGm.name(), true),
                                new DiscordEmbed.Field("World", player.getWorld().getName(), true)
                        )
                );
            }
        }
    }
}
