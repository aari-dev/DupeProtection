package dev.aari.antidupe.listeners;

import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.data.ItemRegistry;
import dev.aari.antidupe.util.ItemIdentifier;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class ItemTrackingListener implements Listener {

    private final ItemRegistry itemRegistry;
    private final ConfigManager configManager;
    private final Object2LongMap<String> lastActionTime;

    public ItemTrackingListener(ItemRegistry itemRegistry, ConfigManager configManager) {
        this.itemRegistry = itemRegistry;
        this.configManager = configManager;
        this.lastActionTime = new Object2LongOpenHashMap<>(256);
        this.lastActionTime.defaultReturnValue(0L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("antidupe.admin")) return;

        CompletableFuture.runAsync(() -> {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    Long id = ItemIdentifier.getItemId(item);
                    if (id == null) {
                        itemRegistry.registerItem(item, "LOGIN_SCAN", player.getName());
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!shouldTrackAction(event.getPlayer().getName())) return;

        event.getBlock().getDrops(event.getPlayer().getInventory().getItemInMainHand())
                .forEach(drop -> trackItemAsync(drop, "MINED", event.getPlayer().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!shouldTrackAction(player.getName())) return;

        ItemStack result = event.getCurrentItem();
        if (result != null && !result.getType().isAir()) {
            trackItemAsync(result, "CRAFTED", player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmeltItem(FurnaceExtractEvent event) {
        if (!shouldTrackAction(event.getPlayer().getName())) return;

        ItemStack result = new ItemStack(event.getItemType(), event.getItemAmount());
        trackItemAsync(result, "SMELTED", event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        if (!shouldTrackAction(event.getEnchanter().getName())) return;

        trackItemAsync(event.getItem(), "ENCHANTED", event.getEnchanter().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!shouldTrackAction(player.getName())) return;

        ItemStack item = event.getItem().getItemStack();
        Long existingId = ItemIdentifier.getItemId(item);

        if (existingId != null) {
            CompletableFuture.runAsync(() ->
                    itemRegistry.registerItem(item, "PICKED_UP", player.getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!shouldTrackAction(event.getPlayer().getName())) return;

        ItemStack item = event.getItemDrop().getItemStack();
        Long existingId = ItemIdentifier.getItemId(item);

        if (existingId != null) {
            CompletableFuture.runAsync(() ->
                    itemRegistry.registerItem(item, "DROPPED", event.getPlayer().getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!shouldTrackAction(player.getName())) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && !clicked.getType().isAir()) {
            Long id = ItemIdentifier.getItemId(clicked);
            int scanChance = configManager.getInt("settings.inventory-scan-chance", 20);
            if (id != null && ThreadLocalRandom.current().nextInt(scanChance) == 0) {
                CompletableFuture.runAsync(() ->
                        itemRegistry.registerItem(clicked, "MOVED", player.getName()));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!shouldTrackAction(player.getName())) return;

        ItemStack dragged = event.getOldCursor();
        if (dragged != null && !dragged.getType().isAir()) {
            Long id = ItemIdentifier.getItemId(dragged);
            if (id != null) {
                CompletableFuture.runAsync(() ->
                        itemRegistry.registerItem(dragged, "DRAGGED", player.getName()));
            }
        }
    }

    private boolean shouldTrackAction(String playerName) {
        long currentTime = System.currentTimeMillis();
        long lastTime = lastActionTime.getLong(playerName);
        long throttleMs = configManager.getLong("settings.action-throttle-ms", 100L);

        if (currentTime - lastTime < throttleMs) {
            return false;
        }

        lastActionTime.put(playerName, currentTime);
        return true;
    }

    private void trackItemAsync(ItemStack item, String action, String playerName) {
        if (item == null || item.getType().isAir()) return;

        CompletableFuture.runAsync(() ->
                itemRegistry.registerItem(item, action, playerName));
    }

    public void cleanup() {
        lastActionTime.clear();
    }
}