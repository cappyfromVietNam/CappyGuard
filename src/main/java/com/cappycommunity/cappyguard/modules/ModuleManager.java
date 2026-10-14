package com.cappycommunity.cappyguard.modules;

import com.cappycommunity.cappyguard.CappyGuard;
import com.cappycommunity.cappyguard.modules.antiadminabuse.AntiAdminAbuseModule;
import com.cappycommunity.cappyguard.modules.antidangerous.AntiDangerousModule;
import com.cappycommunity.cappyguard.modules.antiforceop.AntiForceOPModule;
import com.cappycommunity.cappyguard.modules.antigamemode.AntiGamemodeModule;
import com.cappycommunity.cappyguard.modules.creativemonitor.CreativeMonitorModule;
import com.cappycommunity.cappyguard.modules.logging.LoggingModule;
import com.cappycommunity.cappyguard.modules.opwhitelist.OPWhitelistModule;
import com.cappycommunity.cappyguard.modules.pluginspoof.PluginSpoofModule;

import java.util.*;

/**
 * Registry and lifecycle manager for all CappyGuard security modules.
 */
public final class ModuleManager {

    private final CappyGuard plugin;
    private final List<AbstractModule> modules = new ArrayList<>();
    private final Map<Class<? extends AbstractModule>, AbstractModule> moduleMap = new HashMap<>();

    public ModuleManager(CappyGuard plugin) {
        this.plugin = plugin;
        registerModules();
    }

    private void registerModules() {
        addModule(new LoggingModule(plugin));
        addModule(new OPWhitelistModule(plugin));
        addModule(new AntiForceOPModule(plugin));
        addModule(new AntiAdminAbuseModule(plugin));
        addModule(new AntiGamemodeModule(plugin));
        addModule(new AntiDangerousModule(plugin));
        addModule(new CreativeMonitorModule(plugin));
        addModule(new PluginSpoofModule(plugin));
    }

    private void addModule(AbstractModule module) {
        modules.add(module);
        moduleMap.put(module.getClass(), module);
    }

    public void enableAll() {
        for (AbstractModule module : modules) {
            module.enable();
        }
    }

    public void disableAll() {
        for (int i = modules.size() - 1; i >= 0; i--) {
            modules.get(i).disable();
        }
    }

    public void reloadAll() {
        for (AbstractModule module : modules) {
            module.reload();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractModule> T getModule(Class<T> clazz) {
        return (T) moduleMap.get(clazz);
    }

    public List<AbstractModule> getModules() {
        return Collections.unmodifiableList(modules);
    }
}
