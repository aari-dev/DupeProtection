package dev.aari.antidupe.listeners;

import dev.aari.antidupe.AntiDupe;
import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.managers.DupeDebugManager;
import dev.aari.antidupe.util.ItemIdentifier;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public final class AdvancedProtectionListener implements Listener {

    private static final long CLICK_THRESHOLD = 100L;
    private static final int MAX_VIOLATIONS = 5;
    private static final Material[] SHULKER_BOXES = {
            Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX
    };

    private final AntiDupe plugin;
    private final ConfigManager config;
    private final DupeDebugManager debugManager;
    private final Object2LongOpenHashMap<UUID> lastClickTime;
    private final Object2IntOpenHashMap<UUID> violationCount;

    public AdvancedProtectionListener(AntiDupe plugin, ConfigManager config, DupeDebugManager debugManager) {
        this.plugin = plugin;
        this.config = config;
        this.debugManager = debugManager;
        this.lastClickTime = new Object2LongOpenHashMap<>(64);
        this.violationCount = new Object2IntOpenHashMap<>(64);

        this.lastClickTime.defaultReturnValue(0L);
        this.violationCount.defaultReturnValue(0);

        startCleanupTask();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreativeInventory(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!config.getBoolean("protection.creative-enabled", true)) return;

        final InventoryAction action = event.getAction();
        if (action != InventoryAction.PLACE_ALL && action != InventoryAction.PLACE_SOME) return;

        final ItemStack cursor = event.getCursor();
        final ItemStack current = event.getCurrentItem();

        if (!isValidItemStack(cursor) || !isValidItemStack(current)) return;
        if (!cursor.getType().equals(current.getType())) return;

        final UUID playerId = player.getUniqueId();
        final long currentTime = System.currentTimeMillis();
        final long lastClick = lastClickTime.getLong(playerId);

        if (currentTime - lastClick < CLICK_THRESHOLD) {
            if (incrementViolations(playerId) >= MAX_VIOLATIONS) {
                notifyAdmins(player, "RAPID_CREATIVE_CLICKS");
            }
        }

        lastClickTime.put(playerId, currentTime);

        final Long cursorId = ItemIdentifier.getItemId(cursor);
        final Long currentId = ItemIdentifier.getItemId(current);

        if (cursorId != null && currentId != null && !cursorId.equals(currentId)) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            notifyAdmins(player, "CREATIVE_ITEM_SPREAD");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        lastClickTime.removeLong(playerId);
        violationCount.removeInt(playerId);
    }

    private boolean isValidItemStack(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private int incrementViolations(UUID playerId) {
        final int current = violationCount.getInt(playerId);
        final int newCount = current + 1;
        violationCount.put(playerId, newCount);
        return newCount;
    }

    private void notifyAdmins(Player player, String reason) {
        if (!config.getBoolean("protection.notify-admins", true)) return;

        debugManager.broadcastDupeAlert(player.getName(), -1L, violationCount.getInt(player.getUniqueId()));

        if (config.getBoolean("protection.log-violations", true)) {
            plugin.getSLF4JLogger().warn("Protection triggered: {} - {}", player.getName(), reason);
        }
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                final long threshold = System.currentTimeMillis() - 300_000L;
                lastClickTime.object2LongEntrySet().removeIf(entry -> entry.getLongValue() < threshold);
                violationCount.object2IntEntrySet().removeIf(entry ->
                        !lastClickTime.containsKey(entry.getKey()));
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L);
    }

    public void cleanup() {
        lastClickTime.clear();
        violationCount.clear();
    }
}