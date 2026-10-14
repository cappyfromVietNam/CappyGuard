package com.cappycommunity.cappyguard.modules;

import com.cappycommunity.cappyguard.CappyGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/**
 * Base abstract class for all CappyGuard security modules.
 */
public abstract class AbstractModule implements Listener {

    protected final CappyGuard plugin;
    protected boolean enabled;

    public AbstractModule(CappyGuard plugin) {
        this.plugin = plugin;
    }

    public abstract String getName();

    public abstract String getConfigPath();

    protected abstract void onEnable();

    protected abstract void onDisable();

    public void enable() {
        boolean shouldEnable = plugin.getConfig().getBoolean(getConfigPath() + ".enabled", true);
        if (shouldEnable) {
            this.enabled = true;
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            onEnable();
        } else {
            this.enabled = false;
        }
    }

    public void disable() {
        if (this.enabled) {
            this.enabled = false;
            HandlerList.unregisterAll(this);
            onDisable();
        }
    }

    public void reload() {
        disable();
        enable();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Checks if a player is designated as a monitored staff member subject to anti-abuse rules.
     * Evaluates: (isOp || hasPermission("cappyguard.staff") || hasPermission(specificPermission))
     */
    protected boolean isMonitoredStaff(Player player, String specificPermission) {
        if (player == null) return false;
        if (isBypassed(player, specificPermission)) return false;

        return player.isOp() ||
               player.hasPermission("cappyguard.staff") ||
               (specificPermission != null && player.hasPermission(specificPermission));
    }

    /**
     * Checks if a player has permission to bypass a specific module check.
     */
    protected boolean isBypassed(Player player, String specificPermission) {
        if (player == null) return false;
        if (player.hasPermission("cappyguard.bypass.*")) return true;
        return specificPermission != null && player.hasPermission(specificPermission);
    }
}

