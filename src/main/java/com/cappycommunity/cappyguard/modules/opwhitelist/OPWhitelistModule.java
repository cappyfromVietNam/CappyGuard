package com.cappycommunity.cappyguard.modules.opwhitelist;

import com.cappycommunity.cappyguard.CappyGuard;
import com.cappycommunity.cappyguard.modules.AbstractModule;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * High-performance operator whitelist using O(1) HashSet lookups.
 */
public final class OPWhitelistModule extends AbstractModule {

    private final Set<UUID> allowedUuids = new HashSet<>();
    private final Set<String> allowedNames = new HashSet<>();
    private final List<String> rawEntries = new ArrayList<>();

    public OPWhitelistModule(CappyGuard plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "OP Whitelist";
    }

    @Override
    public String getConfigPath() {
        return "op-whitelist";
    }

    @Override
    protected void onEnable() {
        reloadCache();
    }

    @Override
    protected void onDisable() {
        allowedUuids.clear();
        allowedNames.clear();
        rawEntries.clear();
    }

    public synchronized void reloadCache() {
        allowedUuids.clear();
        allowedNames.clear();
        rawEntries.clear();

        List<String> list = plugin.getConfig().getStringList("op-whitelist.allowed");
        for (String entry : list) {
            if (entry == null || entry.isBlank()) continue;
            rawEntries.add(entry.trim());
            try {
                UUID uuid = UUID.fromString(entry.trim());
                allowedUuids.add(uuid);
            } catch (IllegalArgumentException e) {
                allowedNames.add(entry.trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    public boolean isWhitelisted(Player player) {
        if (player == null) return false;
        if (!enabled) return true;
        return isWhitelisted(player.getUniqueId(), player.getName());
    }

    public boolean isWhitelisted(UUID uuid, String name) {
        if (!enabled) return true;
        if (uuid != null && allowedUuids.contains(uuid)) {
            return true;
        }
        if (name != null && allowedNames.contains(name.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return false;
    }

    public synchronized boolean addPlayer(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        String clean = identifier.trim();
        if (rawEntries.contains(clean)) return false;

        rawEntries.add(clean);
        try {
            allowedUuids.add(UUID.fromString(clean));
        } catch (IllegalArgumentException e) {
            allowedNames.add(clean.toLowerCase(Locale.ROOT));
        }

        plugin.getConfig().set("op-whitelist.allowed", rawEntries);
        plugin.saveConfig();
        return true;
    }

    public synchronized boolean removePlayer(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        String clean = identifier.trim();

        boolean removed = rawEntries.removeIf(e -> e.equalsIgnoreCase(clean));
        if (removed) {
            try {
                allowedUuids.remove(UUID.fromString(clean));
            } catch (IllegalArgumentException e) {
                allowedNames.remove(clean.toLowerCase(Locale.ROOT));
            }
            plugin.getConfig().set("op-whitelist.allowed", rawEntries);
            plugin.saveConfig();
            return true;
        }
        return false;
    }

    public List<String> getRawEntries() {
        return Collections.unmodifiableList(rawEntries);
    }
}
