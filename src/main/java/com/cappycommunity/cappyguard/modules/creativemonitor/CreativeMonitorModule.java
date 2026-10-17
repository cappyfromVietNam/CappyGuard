package com.cappycommunity.cappyguard.modules.creativemonitor;

import com.cappycommunity.cappyguard.CappyGuard;
import com.cappycommunity.cappyguard.discord.DiscordEmbed;
import com.cappycommunity.cappyguard.modules.AbstractModule;
import com.cappycommunity.cappyguard.modules.logging.LoggingModule;
import com.cappycommunity.cappyguard.utils.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

/**
 * Creative Monitor Module.
 * Tracks staff taking items from Creative mode and storing them in chests (with exact coordinates),
 * Ender Chests, or dropping them for other players to collect.
 */
public final class CreativeMonitorModule extends AbstractModule {

    private static final String METADATA_KEY_DROPPER = "cappyguard_creative_dropper";

    private String action;
    private boolean trackChestPlace;
    private boolean trackEnderChest;
    private boolean trackItemDrop;
    private boolean trackItemPickup;
    private boolean trackGamemodeSwitch;
    private boolean clearOnSwitch;

    private final Set<Material> trackedItems = new HashSet<>();
    private final EnumSet<Material> ignoredItems = EnumSet.noneOf(Material.class);

    public CreativeMonitorModule(CappyGuard plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Creative Monitor";
    }

    @Override
    public String getConfigName() {
        return "creative-monitor";
    }

    @Override
    protected void onEnable() {
        trackedItems.clear();
        ignoredItems.clear();

        FileConfiguration config = getModuleConfig();
        this.action = config != null ? config.getString("action", "ALERT").toUpperCase(Locale.ROOT) : "ALERT";
        this.trackChestPlace = config == null || config.getBoolean("track.chest-place", true);
        this.trackEnderChest = config == null || config.getBoolean("track.ender-chest", true);
        this.trackItemDrop = config == null || config.getBoolean("track.item-drop", true);
        this.trackItemPickup = config == null || config.getBoolean("track.item-pickup", true);
        this.trackGamemodeSwitch = config == null || config.getBoolean("track.gamemode-switch", true);
        this.clearOnSwitch = config != null && config.getBoolean("clear-on-switch", false);

        if (config != null) {
            List<String> tracked = config.getStringList("tracked-items");
            for (String item : tracked) {
                Material mat = Material.matchMaterial(item);
                if (mat != null) trackedItems.add(mat);
            }

            List<String> ignored = config.getStringList("ignored-items");
            for (String item : ignored) {
                Material mat = Material.matchMaterial(item);
                if (mat != null) ignoredItems.add(mat);
            }
        }
    }

    @Override
    protected void onDisable() {
        trackedItems.clear();
        ignoredItems.clear();
    }

    private boolean shouldTrackItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        Material mat = stack.getType();
        if (ignoredItems.contains(mat)) return false;
        if (!trackedItems.isEmpty() && !trackedItems.contains(mat)) return false;
        return true;
    }

    /**
     * Detects items deposited into external containers (Chests, Barrels, Shulkers, Hoppers) while in Creative.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!trackChestPlace) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.getGameMode() != GameMode.CREATIVE) return;
        if (!isMonitoredStaff(player, "cappyguard.staff.creative")) return;
        if (isBypassed(player, "cappyguard.bypass.creative")) return;

        Inventory top = event.getView().getTopInventory();
        InventoryType type = top.getType();

        // Check if top inventory is a container (not player crafting/anvil/etc)
        if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) {
            return;
        }

        // Only track when clicking into the container slots
        if (event.getRawSlot() >= top.getSize()) {
            return;
        }

        ItemStack placed = event.getCursor();
        if (!shouldTrackItem(placed)) return;

        Location loc = top.getLocation();
        String locStr = formatLocation(loc);

        if ("RESTRICT".equals(action)) {
            event.setCancelled(true);
            MessageUtil.send(player, "&c[CappyGuard] You are not permitted to place creative items into containers!");
        }

        LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
        if (logging != null) {
            String itemDesc = placed.getAmount() + "x " + placed.getType().name();
            logging.recordSecurityIncident(
                    "creative-abuse",
                    "warning",
                    "&6[CREATIVE MONITOR] &e" + player.getName() + " &7stored &e" + itemDesc + " &7into &e" + type.name() + " &7at &b" + locStr,
                    "Staff " + player.getName() + " placed " + itemDesc + " into " + type.name() + " at " + locStr,
                    player.getUniqueId(),
                    player.getName(),
                    List.of(
                            new DiscordEmbed.Field("Staff Player", player.getName(), true),
                            new DiscordEmbed.Field("Item & Amount", itemDesc, true),
                            new DiscordEmbed.Field("Container Type", type.name(), true),
                            new DiscordEmbed.Field("Coordinates", locStr, false)
                    )
            );
        }
    }

    /**
     * Detects interaction with Ender Chests while in Creative mode.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnderChestInteract(PlayerInteractEvent event) {
        if (!trackEnderChest) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) return;
        if (!isMonitoredStaff(player, "cappyguard.staff.creative")) return;
        if (isBypassed(player, "cappyguard.bypass.creative")) return;

        String locStr = formatLocation(block.getLocation());

        LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
        if (logging != null) {
            logging.recordSecurityIncident(
                    "creative-abuse",
                    "warning",
                    "&6[CREATIVE MONITOR] &e" + player.getName() + " &7accessed &dENDER CHEST &7while in Creative at &b" + locStr,
                    "Staff " + player.getName() + " opened Ender Chest in Creative at " + locStr,
                    player.getUniqueId(),
                    player.getName(),
                    List.of(
                            new DiscordEmbed.Field("Staff Player", player.getName(), true),
                            new DiscordEmbed.Field("Action", "Opened Ender Chest in Creative", true),
                            new DiscordEmbed.Field("Location", locStr, false)
                    )
            );
        }
    }

    /**
     * Detects item dropping while in Creative mode.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (!trackItemDrop) return;

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) return;
        if (!isMonitoredStaff(player, "cappyguard.staff.creative")) return;
        if (isBypassed(player, "cappyguard.bypass.creative")) return;

        Item itemEntity = event.getItemDrop();
        ItemStack stack = itemEntity.getItemStack();
        if (!shouldTrackItem(stack)) return;

        // Tag the item entity with metadata so we know who dropped it when someone picks it up
        itemEntity.setMetadata(METADATA_KEY_DROPPER, new FixedMetadataValue(plugin, player.getName()));

        String locStr = formatLocation(itemEntity.getLocation());
        String itemDesc = stack.getAmount() + "x " + stack.getType().name();

        LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
        if (logging != null) {
            logging.recordSecurityIncident(
                    "creative-abuse",
                    "warning",
                    "&6[CREATIVE MONITOR] &e" + player.getName() + " &7dropped &e" + itemDesc + " &7at &b" + locStr,
                    "Staff " + player.getName() + " dropped creative item " + itemDesc + " at " + locStr,
                    player.getUniqueId(),
                    player.getName(),
                    List.of(
                            new DiscordEmbed.Field("Staff Player", player.getName(), true),
                            new DiscordEmbed.Field("Dropped Item", itemDesc, true),
                            new DiscordEmbed.Field("Location", locStr, false)
                    )
            );
        }
    }

    /**
     * Detects when another player picks up a dropped creative item.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!trackItemPickup) return;
        if (!(event.getEntity() instanceof Player collector)) return;

        Item itemEntity = event.getItem();
        if (!itemEntity.hasMetadata(METADATA_KEY_DROPPER)) return;

        String dropperName = itemEntity.getMetadata(METADATA_KEY_DROPPER).get(0).asString();
        ItemStack stack = itemEntity.getItemStack();
        String itemDesc = stack.getAmount() + "x " + stack.getType().name();
        String locStr = formatLocation(collector.getLocation());

        LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
        if (logging != null) {
            logging.recordSecurityIncident(
                    "creative-abuse",
                    "warning",
                    "&6[CREATIVE TRANSFER] &e" + collector.getName() + " &7picked up &e" + itemDesc + " &7(dropped by staff &e" + dropperName + "&7) at &b" + locStr,
                    "Player " + collector.getName() + " picked up " + itemDesc + " dropped by staff " + dropperName + " at " + locStr,
                    collector.getUniqueId(),
                    collector.getName(),
                    List.of(
                            new DiscordEmbed.Field("Collector", collector.getName(), true),
                            new DiscordEmbed.Field("Original Dropper", dropperName, true),
                            new DiscordEmbed.Field("Item", itemDesc, true),
                            new DiscordEmbed.Field("Location", locStr, false)
                    )
            );
        }
    }

    /**
     * Detects retaining items when switching from Creative to Survival.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeSwitch(PlayerGameModeChangeEvent event) {
        if (!trackGamemodeSwitch) return;

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE || event.getNewGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (!isMonitoredStaff(player, "cappyguard.staff.creative")) return;
        if (isBypassed(player, "cappyguard.bypass.creative")) return;

        if (clearOnSwitch) {
            player.getInventory().clear();
            MessageUtil.send(player, "&6[CappyGuard] Inventory cleared after switching from Creative mode.");
            return;
        }

        // Count non-ignored items
        int totalItems = 0;
        List<String> summary = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (shouldTrackItem(item)) {
                totalItems += item.getAmount();
                if (summary.size() < 5) {
                    summary.add(item.getAmount() + "x " + item.getType().name());
                }
            }
        }

        if (totalItems > 0) {
            String itemsSummary = String.join(", ", summary) + (summary.size() < totalItems ? "..." : "");
            LoggingModule logging = plugin.getModuleManager().getModule(LoggingModule.class);
            if (logging != null) {
                logging.recordSecurityIncident(
                        "creative-abuse",
                        "warning",
                        "&6[CREATIVE SWITCH] &e" + player.getName() + " &7switched to Survival with &e" + totalItems + " &7items: &e" + itemsSummary,
                        "Staff " + player.getName() + " switched to Survival retaining creative items: " + itemsSummary,
                        player.getUniqueId(),
                        player.getName(),
                        List.of(
                                new DiscordEmbed.Field("Player", player.getName(), true),
                                new DiscordEmbed.Field("Items Retained", itemsSummary, false)
                        )
                );
            }
        }
    }

    private String formatLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return "World: Unknown | X: 0, Y: 0, Z: 0";
        return String.format("World: %s | X: %d, Y: %d, Z: %d",
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
