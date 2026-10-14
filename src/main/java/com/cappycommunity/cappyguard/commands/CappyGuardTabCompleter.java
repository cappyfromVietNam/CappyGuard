package com.cappycommunity.cappyguard.commands;

import com.cappycommunity.cappyguard.CappyGuard;
import com.cappycommunity.cappyguard.modules.opwhitelist.OPWhitelistModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * High-performance tab completion for /cappyguard command.
 */
public final class CappyGuardTabCompleter implements TabCompleter {

    private static final List<String> ROOT_SUBCOMMANDS = List.of("reload", "status", "whitelist", "help");
    private static final List<String> WHITELIST_SUBCOMMANDS = List.of("add", "remove", "list");

    private final CappyGuard plugin;

    public CappyGuardTabCompleter(CappyGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cappyguard.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterStartingWith(args[0], ROOT_SUBCOMMANDS);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("whitelist")) {
            return filterStartingWith(args[1], WHITELIST_SUBCOMMANDS);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("whitelist")) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("add")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return filterStartingWith(args[2], names);
            } else if (action.equals("remove")) {
                OPWhitelistModule whitelist = plugin.getModuleManager().getModule(OPWhitelistModule.class);
                if (whitelist != null) {
                    return filterStartingWith(args[2], whitelist.getRawEntries());
                }
            }
        }

        return Collections.emptyList();
    }

    private static List<String> filterStartingWith(String prefix, Collection<String> candidates) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(candidates);
        }
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                matches.add(c);
            }
        }
        return matches;
    }
}
