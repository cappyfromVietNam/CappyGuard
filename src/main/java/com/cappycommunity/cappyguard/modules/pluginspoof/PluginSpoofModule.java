package com.cappycommunity.cappyguard.modules.pluginspoof;

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
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.*;

/**
 * Plugin Spoof Module.
 * Conceals real server plugins from unauthorized inspection and hack client reconnaissance.
 */
public final class PluginSpoofModule extends AbstractModule {

    private static final Set<String> INSPECTION_ROOTS = Set.of(
            "pl", "plugins", "bukkit:pl", "bukkit:plugins",
            "ver", "version", "about", "bukkit:ver", "bukkit:version", "bukkit:about"
    );

    private String mode;
    private String hideMessage;
    private String customMessage;
    private final List<String> fakePlugins = new ArrayList<>();
    private boolean blockTabComplete;
    private boolean blockVersionCheck;
    private boolean logAttempts;

    public PluginSpoofModule(CappyGuard plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Plugin Spoof";
    }

    @Override
    public String getConfigPath() {
        return "plugin-spoof";
    }

    @Override
    protected void onEnable() {
        fakePlugins.clear();

        FileConfiguration config = plugin.getConfig();
        this.mode = config.getString("plugin-spoof.mode", "FAKE").toUpperCase(Locale.ROOT);
        this.hideMessage = config.getString("plugin-spoof.hide-message", "&cUnknown command. Type \"/help\" for help.");
        this.customMessage = config.getString("plugin-spoof.custom-message", "&7Server plugin inspection is disabled on this server.");
        this.fakePlugins.addAll(config.getStringList("plugin-spoof.fake-plugins"));
        this.blockTabComplete = config.getBoolean("plugin-spoof.block-tab-complete", true);
        this.blockVersionCheck = config.getBoolean("plugin-spoof.block-version-check", true);
        this.logAttempts = config.getBoolean("plugin-spoof.log-attempts", true);
    }

    @Override
    protected void onDisable() {
        fakePlugins.clear();
    }

    public String getMode() {
        return mode;
    }

    /**
     * Paper native event: Prunes plugin inspection suggestions from the command tree sent to clients.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (!blockTabComplete) return;
        Player player = event.getPlayer();
        if (isBypassed(player, "cappyguard.bypass.spoof")) return;

        // Prune plugin inspection commands from client autocomplete tree
        event.getCommands().removeIf(cmd -> {
            String lower = cmd.toLowerCase(Locale.ROOT);
            return INSPECTION_ROOTS.contains(lower);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isBypassed(player, "cappyguard.bypass.spoof")) return;

        String raw = event.getMessage().trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/")) lower = lower.substring(1);

        String[] parts = lower.split("\\s+");
        String root = parts[0];

        boolean isInspection = INSPECTION_ROOTS.contains(root);
        boolean isVersionCheck = blockVersionCheck && (root.equals("version") || root.equals("ver") || root.equals("about")) && parts.length > 1;

        if (isInspection || isVersionCheck) {
            event.setCancelled(true);

            switch (mode) {
                case "FAKE" -> {
                    String formattedList = "&fServer Plugins (" + fakePlugins.size() + "): &a" + String.join("&f, &a", fakePlugins);
                    MessageUtil.sendRaw(player, formattedList);
                }
                case "CUSTOM_MESSAGE" -> MessageUtil.send(player, customMessage);
                default -> MessageUtil.sendRaw(player, hideMessage);
            }

            if (logAttempts) {
                LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
                if (logging != null) {
                    logging.recordSecurityIncident(
                            "plugin-spoof",
                            "info",
                            "&7[PLUGIN SCAN] Player &e" + player.getName() + " &7attempted plugin scan via: &e" + event.getMessage(),
                            "Player " + player.getName() + " (" + player.getUniqueId() + ") attempted plugin reconnaissance: " + event.getMessage(),
                            player.getUniqueId(),
                            player.getName(),
                            List.of(
                                    new DiscordEmbed.Field("Player", player.getName(), true),
                                    new DiscordEmbed.Field("Query", event.getMessage(), true),
                                    new DiscordEmbed.Field("Mode Response", mode, true)
                            )
                    );
                }
            }
        }
    }
}

