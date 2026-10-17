package com.cappycommunity.cappyguard.modules.packetguard;

import com.cappycommunity.cappyguard.CappyGuard;
import com.cappycommunity.cappyguard.discord.DiscordEmbed;
import com.cappycommunity.cappyguard.modules.AbstractModule;
import com.cappycommunity.cappyguard.modules.logging.LoggingModule;
import com.cappycommunity.cappyguard.utils.MessageUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Deep Packet Guard Module.
 * Uses PacketEvents to intercept raw Netty packets to detect Hack Clients,
 * GuiMove/InvMove (ClickGUI movement), and raw command prefix leaks.
 */
public final class PacketGuardModule extends AbstractModule {

    private final PacketEventListener packetListener = new PacketEventListener();
    private PacketListenerCommon registeredListener;

    private boolean detectGuiMove;
    private double minVelocity;
    private String guiMoveAction;

    private boolean detectPrefixLeaks;
    private boolean dropPrefixPacket;
    private final List<String> leakPrefixes = new ArrayList<>();
    private final List<String> leakKeywords = new ArrayList<>();

    private boolean detectModChannels;
    private final List<String> blockedChannels = new ArrayList<>();

    public PacketGuardModule(CappyGuard plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Deep Packet Guard";
    }

    @Override
    public String getConfigName() {
        return "packet-guard";
    }

    @Override
    protected void onEnable() {
        leakPrefixes.clear();
        leakKeywords.clear();
        blockedChannels.clear();

        FileConfiguration config = getModuleConfig();
        if (config != null) {
            this.detectGuiMove = config.getBoolean("detect-guimove.enabled", true);
            this.minVelocity = config.getDouble("detect-guimove.min-velocity", 0.08);
            this.guiMoveAction = config.getString("detect-guimove.action", "ALERT").toUpperCase(Locale.ROOT);

            this.detectPrefixLeaks = config.getBoolean("detect-prefix-leaks.enabled", true);
            this.dropPrefixPacket = config.getBoolean("detect-prefix-leaks.drop-packet", true);
            this.leakPrefixes.addAll(config.getStringList("detect-prefix-leaks.prefixes"));
            this.leakKeywords.addAll(config.getStringList("detect-prefix-leaks.keywords"));

            this.detectModChannels = config.getBoolean("detect-mod-channels.enabled", true);
            this.blockedChannels.addAll(config.getStringList("detect-mod-channels.blocked-channels"));
        } else {
            this.detectGuiMove = true;
            this.minVelocity = 0.08;
            this.guiMoveAction = "ALERT";
            this.detectPrefixLeaks = true;
            this.dropPrefixPacket = true;
            this.detectModChannels = true;
        }

        // Register PacketEvents listener
        this.registeredListener = PacketEvents.getAPI().getEventManager().registerListener(packetListener, PacketListenerPriority.NORMAL);
    }

    @Override
    protected void onDisable() {
        // Unregister PacketEvents listener
        if (registeredListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(registeredListener);
            } catch (Exception ignored) {}
            this.registeredListener = null;
        }

        leakPrefixes.clear();
        leakKeywords.clear();
        blockedChannels.clear();
    }

    private final class PacketEventListener implements PacketListener {

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            Object playerObj = event.getPlayer();
            if (!(playerObj instanceof Player player)) return;
            if (isBypassed(player, "cappyguard.bypass.packet")) return;

            // 1. GuiMove / InvMove Detection (ClickWindow while moving at speed)
            if (detectGuiMove && event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
                handleGuiMoveCheck(event, player);
                return;
            }

            // 2. Chat / Command Prefix Leaks (.killaura, .fly, .bind, etc.)
            if (detectPrefixLeaks) {
                if (event.getPacketType() == PacketType.Play.Client.CHAT_MESSAGE) {
                    WrapperPlayClientChatMessage msg = new WrapperPlayClientChatMessage(event);
                    handleChatOrCommand(event, player, msg.getMessage(), false);
                    return;
                } else if (event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND) {
                    WrapperPlayClientChatCommand cmd = new WrapperPlayClientChatCommand(event);
                    handleChatOrCommand(event, player, cmd.getCommand(), true);
                    return;
                }
            }

            // 3. Custom Payload Channel Sniffing
            if (detectModChannels && event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
                WrapperPlayClientPluginMessage payload = new WrapperPlayClientPluginMessage(event);
                handlePluginMessage(event, player, payload);
            }
        }

        private void handleGuiMoveCheck(PacketReceiveEvent event, Player player) {
            Vector velocity = player.getVelocity();
            double horizontalSpeed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
            boolean isJumping = !player.isOnGround() && velocity.getY() > 0.1;

            if (player.isSprinting() || horizontalSpeed > minVelocity || isJumping) {
                if ("CANCEL".equals(guiMoveAction)) {
                    event.setCancelled(true);
                }

                String speedFormatted = String.format(Locale.ROOT, "%.3f", horizontalSpeed);
                LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
                if (logging != null) {
                    logging.incrementBlockedEvents();
                    logging.recordSecurityIncident(
                            "packet-guard",
                            "warning",
                            "&c[GUIMOVE DETECTED] &e" + player.getName() + " &7interacted with UI while moving at &e" + speedFormatted + " &7blocks/tick!",
                            "GuiMove anomaly: " + player.getName() + " clicked inventory slots with speed " + speedFormatted + " (Sprinting: " + player.isSprinting() + ")",
                            player.getUniqueId(),
                            player.getName(),
                            List.of(
                                    new DiscordEmbed.Field("Player", player.getName(), true),
                                    new DiscordEmbed.Field("Horizontal Speed", speedFormatted, true),
                                    new DiscordEmbed.Field("Is Sprinting", String.valueOf(player.isSprinting()), true),
                                    new DiscordEmbed.Field("Action Taken", guiMoveAction, true)
                            )
                    );
                }
            }
        }

        private void handleChatOrCommand(PacketReceiveEvent event, Player player, String content, boolean isCommand) {
            if (content == null || content.isEmpty()) return;
            String trimmed = content.trim();

            boolean prefixMatch = false;
            for (String prefix : leakPrefixes) {
                if (trimmed.startsWith(prefix)) {
                    prefixMatch = true;
                    break;
                }
            }

            String lower = trimmed.toLowerCase(Locale.ROOT);
            boolean keywordMatch = false;
            for (String keyword : leakKeywords) {
                if (lower.contains(keyword)) {
                    keywordMatch = true;
                    break;
                }
            }

            if (prefixMatch || keywordMatch) {
                if (dropPrefixPacket) {
                    event.setCancelled(true);
                }

                LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
                if (logging != null) {
                    logging.incrementBlockedEvents();
                    logging.recordSecurityIncident(
                            "packet-guard",
                            "critical",
                            "&4[CLIENT COMMAND LEAK] &cPlayer &e" + player.getName() + " &cleaked client command: &e" + trimmed,
                            "Hack client prefix leak detected from " + player.getName() + " (" + player.getUniqueId() + "): " + trimmed,
                            player.getUniqueId(),
                            player.getName(),
                            List.of(
                                    new DiscordEmbed.Field("Player", player.getName(), true),
                                    new DiscordEmbed.Field("Leaked Command", trimmed, false),
                                    new DiscordEmbed.Field("Status", dropPrefixPacket ? "Packet Dropped (Silenced)" : "Logged", true)
                            )
                    );
                }
            }
        }

        private void handlePluginMessage(PacketReceiveEvent event, Player player, WrapperPlayClientPluginMessage payload) {
            String channel = payload.getChannelName().toLowerCase(Locale.ROOT);
            String data = new String(payload.getData(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);

            for (String blocked : blockedChannels) {
                if (channel.contains(blocked) || data.contains(blocked)) {
                    event.setCancelled(true);

                    LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
                    if (logging != null) {
                        logging.incrementBlockedEvents();
                        logging.recordSecurityIncident(
                                "packet-guard",
                                "warning",
                                "&c[CHEAT MOD DETECTED] &e" + player.getName() + " &7sent cheat mod payload: &e" + channel,
                                "Cheat mod channel detected from " + player.getName() + ": " + channel,
                                player.getUniqueId(),
                                player.getName(),
                                List.of(
                                        new DiscordEmbed.Field("Player", player.getName(), true),
                                        new DiscordEmbed.Field("Payload Channel", channel, true),
                                        new DiscordEmbed.Field("Action", "Packet Blocked", true)
                                )
                        );
                    }
                    break;
                }
            }
        }
    }
}
