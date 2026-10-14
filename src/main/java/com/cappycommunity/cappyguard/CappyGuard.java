package com.cappycommunity.cappyguard;

import com.cappycommunity.cappyguard.commands.CappyGuardCommand;
import com.cappycommunity.cappyguard.commands.CappyGuardTabCompleter;
import com.cappycommunity.cappyguard.config.ConfigManager;
import com.cappycommunity.cappyguard.discord.DiscordWebhookManager;
import com.cappycommunity.cappyguard.modules.ModuleManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CappyGuard Main Plugin Entry Point.
 * High-performance server security system developed by Cappy Community.
 */
public final class CappyGuard extends JavaPlugin {

    private static CappyGuard instance;

    private ConfigManager configManager;
    private DiscordWebhookManager discordWebhookManager;
    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Initialize Configuration
        this.configManager = new ConfigManager(this);

        // 2. Initialize Discord Webhook Manager
        this.discordWebhookManager = new DiscordWebhookManager(this);

        // 3. Initialize & Enable Security Modules
        this.moduleManager = new ModuleManager(this);
        this.moduleManager.enableAll();

        // 4. Register Commands & Tab Completers
        PluginCommand cmd = getCommand("cappyguard");
        if (cmd != null) {
            cmd.setExecutor(new CappyGuardCommand(this));
            cmd.setTabCompleter(new CappyGuardTabCompleter(this));
        }

        // Print Startup Banner
        getLogger().info("==================================================");
        getLogger().info("  CappyGuard v" + getPluginMeta().getVersion() + " has been successfully enabled!");
        getLogger().info("  Developed by Cappy Community (Paper 1.21+)");
        getLogger().info("  8 Security Modules Active. Server protected 24/7.");
        getLogger().info("==================================================");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }

        getLogger().info("CappyGuard has been disabled cleanly. All buffers flushed.");
    }

    /**
     * Complete plugin reload: re-reads config.yml and refreshes all modules and discord hooks.
     */
    public void reloadPlugin() {
        if (configManager != null) {
            configManager.reload();
        }
        if (discordWebhookManager != null) {
            discordWebhookManager.reload();
        }
        if (moduleManager != null) {
            moduleManager.reloadAll();
        }
    }

    public static CappyGuard getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DiscordWebhookManager getDiscordWebhookManager() {
        return discordWebhookManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }
}

