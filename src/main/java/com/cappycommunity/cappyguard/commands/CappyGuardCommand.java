package com.cappycommunity.cappyguard.commands;

import com.cappycommunity.cappyguard.CappyGuard;
import com.cappycommunity.cappyguard.modules.AbstractModule;
import com.cappycommunity.cappyguard.modules.logging.LoggingModule;
import com.cappycommunity.cappyguard.modules.opwhitelist.OPWhitelistModule;
import com.cappycommunity.cappyguard.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;

/**
 * Command Executor for /cappyguard (/cg).
 */
public final class CappyGuardCommand implements CommandExecutor {

    private final CappyGuard plugin;

    public CappyGuardCommand(CappyGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cappyguard.admin")) {
            MessageUtil.send(sender, "&cYou do not have permission to execute CappyGuard commands!");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "whitelist" -> handleWhitelist(sender, args, label);
            default -> {
                MessageUtil.send(sender, "&cUnknown subcommand. Type &e/" + label + " help &cfor available commands.");
            }
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("cappyguard.command.reload") && !sender.hasPermission("cappyguard.admin")) {
            MessageUtil.send(sender, "&cYou do not have permission to reload CappyGuard!");
            return;
        }

        plugin.reloadPlugin();
        MessageUtil.send(sender, "&aCappyGuard configuration, modules, and Discord webhooks reloaded successfully!");
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("cappyguard.command.status") && !sender.hasPermission("cappyguard.admin")) {
            MessageUtil.send(sender, "&cYou do not have permission to view CappyGuard status!");
            return;
        }

        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        long hours = uptimeMillis / (1000 * 60 * 60);
        long minutes = (uptimeMillis % (1000 * 60 * 60)) / (1000 * 60);

        LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
        long blocked = logging != null ? logging.getBlockedEventsCount() : 0;
        long logged = logging != null ? logging.getLoggedEventsCount() : 0;

        MessageUtil.sendRaw(sender, "&8&m══════════════════════════════════════════");
        MessageUtil.sendRaw(sender, "       <gradient:#ff6b6b:#ee5a24>&lCappyGuard Protection System</gradient>");
        MessageUtil.sendRaw(sender, "       &7Developed by &eCappy Community &7• &bv1.0.0");
        MessageUtil.sendRaw(sender, "&8&m══════════════════════════════════════════");
        MessageUtil.sendRaw(sender, "  &6Security Modules Status:");

        for (AbstractModule module : plugin.getModuleManager().getModules()) {
            String badge = module.isEnabled() ? "&a[ENABLED]" : "&c[DISABLED]";
            MessageUtil.sendRaw(sender, "   " + badge + " &f" + module.getName());
        }

        MessageUtil.sendRaw(sender, "");
        MessageUtil.sendRaw(sender, "  &6Runtime Statistics:");
        MessageUtil.sendRaw(sender, "   &7• Blocked Violations: &e" + blocked);
        MessageUtil.sendRaw(sender, "   &7• Logged Incidents:   &e" + logged);
        MessageUtil.sendRaw(sender, "   &7• Server Uptime:       &e" + hours + "h " + minutes + "m");
        MessageUtil.sendRaw(sender, "&8&m══════════════════════════════════════════");
    }

    private void handleWhitelist(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("cappyguard.command.whitelist") && !sender.hasPermission("cappyguard.admin")) {
            MessageUtil.send(sender, "&cYou do not have permission to manage the OP whitelist!");
            return;
        }

        OPWhitelistModule whitelist = plugin.getModuleManager().getModule(OPWhitelistModule.class);
        if (whitelist == null) {
            MessageUtil.send(sender, "&cOP Whitelist module is unavailable.");
            return;
        }

        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            List<String> entries = whitelist.getRawEntries();
            MessageUtil.sendRaw(sender, "&8&m══════════════════════════════════════════");
            MessageUtil.sendRaw(sender, "  <gradient:#ff6b6b:#ee5a24>&lCappyGuard OP Whitelist</gradient> &7(" + entries.size() + " entries)");
            MessageUtil.sendRaw(sender, "&8&m══════════════════════════════════════════");
            if (entries.isEmpty()) {
                MessageUtil.sendRaw(sender, "   &7(No players currently whitelisted)");
            } else {
                for (String entry : entries) {
                    MessageUtil.sendRaw(sender, "   &a• &f" + entry);
                }
            }
            MessageUtil.sendRaw(sender, "&8&m══════════════════════════════════════════");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length < 3) {
            MessageUtil.send(sender, "&cUsage: /" + label + " whitelist " + action + " <player/uuid>");
            return;
        }

        String target = args[2];
        if (action.equals("add")) {
            boolean success = whitelist.addPlayer(target);
            if (success) {
                MessageUtil.send(sender, "&aSuccessfully added &e" + target + " &ato the OP Whitelist.");
            } else {
                MessageUtil.send(sender, "&cPlayer &e" + target + " &cis already on the OP Whitelist!");
            }
        } else if (action.equals("remove")) {
            boolean success = whitelist.removePlayer(target);
            if (success) {
                MessageUtil.send(sender, "&aSuccessfully removed &e" + target + " &afrom the OP Whitelist.");
            } else {
                MessageUtil.send(sender, "&cPlayer &e" + target + " &cwas not found on the OP Whitelist!");
            }
        } else {
            MessageUtil.send(sender, "&cInvalid action! Use: /" + label + " whitelist <add|remove|list> [target]");
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        MessageUtil.sendRaw(sender, "&8&m══════════════════════════════════════════");
        MessageUtil.sendRaw(sender, "       <gradient:#ff6b6b:#ee5a24>&lCappyGuard Commands</gradient>");
        MessageUtil.sendRaw(sender, "&8&m══════════════════════════════════════════");
        MessageUtil.sendRaw(sender, "  &e/" + label + " reload          &7- Reload configurations and modules");
        MessageUtil.sendRaw(sender, "  &e/" + label + " status          &7- Display module statuses and statistics");
        MessageUtil.sendRaw(sender, "  &e/" + label + " whitelist list  &7- List all whitelisted Operators");
        MessageUtil.sendRaw(sender, "  &e/" + label + " whitelist add   &7- Add an operator to the whitelist");
        MessageUtil.sendRaw(sender, "  &e/" + label + " whitelist remove&7- Remove an operator from the whitelist");
        MessageUtil.sendRaw(sender, "  &e/" + label + " help            &7- Show this help page");
        MessageUtil.sendRaw(sender, "&8&m══════════════════════════════════════════");
    }
}

