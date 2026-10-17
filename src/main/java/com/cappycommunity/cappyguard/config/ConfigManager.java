package com.cappycommunity.cappyguard.config;

import com.cappycommunity.cappyguard.CappyGuard;
import com.cappycommunity.cappyguard.utils.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages configuration loading, saving, and reloading across the core config and individual module files.
 */
public final class ConfigManager {

    private static final List<String> KNOWN_MODULES = List.of(
            "anti-force-op",
            "op-whitelist",
            "anti-admin-abuse",
            "anti-gamemode",
            "anti-dangerous-commands",
            "creative-monitor",
            "plugin-spoof",
            "packet-guard",
            "logging"
    );

    private final CappyGuard plugin;
    private final Map<String, YamlConfiguration> moduleConfigs = new ConcurrentHashMap<>();
    private final Map<String, File> moduleFiles = new ConcurrentHashMap<>();

    public ConfigManager(CappyGuard plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        // 1. Core config.yml
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        FileConfiguration config = plugin.getConfig();
        String prefix = config.getString("prefix", "<gradient:#ff6b6b:#ee5a24>[CappyGuard]</gradient> ");
        MessageUtil.setPrefix(prefix);

        // 2. Load dedicated module configurations from modules/
        File modulesFolder = new File(plugin.getDataFolder(), "modules");
        if (!modulesFolder.exists()) {
            modulesFolder.mkdirs();
        }

        for (String name : KNOWN_MODULES) {
            loadModuleConfig(name, modulesFolder);
        }
    }

    private void loadModuleConfig(String name, File modulesFolder) {
        File file = new File(modulesFolder, name + ".yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("modules/" + name + ".yml", false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Default resource for module " + name + " could not be extracted: " + e.getMessage());
            }
        }

        if (file.exists()) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            moduleConfigs.put(name, configuration);
            moduleFiles.put(name, file);
        }
    }

    public FileConfiguration getModuleConfig(String name) {
        if (!moduleConfigs.containsKey(name)) {
            File modulesFolder = new File(plugin.getDataFolder(), "modules");
            loadModuleConfig(name, modulesFolder);
        }
        return moduleConfigs.get(name);
    }

    public void saveModuleConfig(String name) {
        YamlConfiguration configuration = moduleConfigs.get(name);
        File file = moduleFiles.get(name);

        if (configuration != null && file != null) {
            try {
                configuration.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save module configuration: " + name + ".yml", e);
            }
        }
    }

    public void reload() {
        moduleConfigs.clear();
        moduleFiles.clear();
        loadConfig();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public void saveConfig() {
        plugin.saveConfig();
    }
}
