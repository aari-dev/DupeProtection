package dev.aari.antidupe.listeners;

import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.data.ItemRegistry;
import dev.aari.antidupe.managers.DupeDebugManager;
import dev.aari.antidupe.util.ItemIdentifier;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class ItemTrackingListener implements Listener {

    private static final long THROTTLE_DEFAULT = 1000L;
    private static final int SCAN_CHANCE = 20;

    private final ItemRegistry itemRegistry;
    private final ConfigManager configManager;
    private final Object2LongOpenHashMap<UUID> lastActionTime;

    public ItemTrackingListener(ItemRegistry itemRegistry, ConfigManager configManager, DupeDebugManager debugManager) {
        this.itemRegistry = itemRegistry;
        this.configManager = configManager;
        this.lastActionTime = new Object2LongOpenHashMap<>(128);
        this.lastActionTime.defaultReturnValue(0L);

        startCleanupTask();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (!player.hasPermission("antidupe.admin")) return;

        CompletableFuture.runAsync(() -> {
            int count = 0;
            for (final ItemStack item : player.getInventory().getContents()) {
                if (isValidItem(item) && ItemIdentifier.getItemId(item) == null) {
                    itemRegistry.registerItem(item, "LOGIN_SCAN", player.getName());
                    if (++count >= 10) break; // Limit login scanning
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        final Player player = event.getPlayer();
        if (!shouldTrack(player)) return;

        final ItemStack tool = player.getInventory().getItemInMainHand();
        if (isValidItem(tool)) {
            event.getBlock().getDrops(tool).stream()
                    .filter(this::isValidItem)
                    .limit(3) // Limit drops processed
                    .forEach(drop -> trackItemAsync(drop, "MINED", player.getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!shouldTrack(player)) return;

        final ItemStack result = event.getCurrentItem();
        if (isValidItem(result)) {
            trackItemAsync(result, "CRAFTED", player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmeltItem(FurnaceExtractEvent event) {
        final Player player = event.getPlayer();
        if (!shouldTrack(player)) return;

        final ItemStack result = new ItemStack(event.getItemType(), event.getItemAmount());
        trackItemAsync(result, "SMELTED", player.getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastActionTime.removeLong(event.getPlayer().getUniqueId());
    }

    private boolean shouldTrack(Player player) {
        if (ThreadLocalRandom.current().nextInt(SCAN_CHANCE) != 0) return false;

        final UUID playerId = player.getUniqueId();
        final long currentTime = System.currentTimeMillis();
        final long lastTime = lastActionTime.getLong(playerId);
        final long throttle = configManager.getLong("settings.action-throttle-ms", THROTTLE_DEFAULT);

        if (currentTime - lastTime < throttle) return false;

        lastActionTime.put(playerId, currentTime);
        return true;
    }

    private boolean isValidItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }

    private void trackItemAsync(ItemStack item, String action, String playerName) {
        if (!isValidItem(item)) return;

        CompletableFuture.runAsync(() -> itemRegistry.registerItem(item, action, playerName));
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                final long threshold = System.currentTimeMillis() - 300_000L; // 5 minutes
                lastActionTime.object2LongEntrySet().removeIf(entry -> entry.getLongValue() < threshold);
            }
        }.runTaskTimerAsynchronously(configManager.getPlugin(), 6000L, 6000L);
    }

    public void cleanup() {
        lastActionTime.clear();
    }
}