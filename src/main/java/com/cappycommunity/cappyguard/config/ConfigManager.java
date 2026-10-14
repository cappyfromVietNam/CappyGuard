package com.cappycommunity.cappyguard.config;

import com.cappycommunity.cappyguard.CappyGuard;
import com.cappycommunity.cappyguard.utils.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages configuration loading, reloading, and fast retrieval.
 */
public final class ConfigManager {

    private final CappyGuard plugin;

    public ConfigManager(CappyGuard plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        FileConfiguration config = plugin.getConfig();
        String prefix = config.getString("prefix", "<gradient:#ff6b6b:#ee5a24>[CappyGuard]</gradient> ");
        MessageUtil.setPrefix(prefix);
    }

    public void reload() {
        loadConfig();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public void saveConfig() {
        plugin.saveConfig();
    }
}

