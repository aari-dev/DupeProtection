package dev.aari.antidupe.managers;

import dev.aari.antidupe.AntiDupe;
import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.util.ColorUtil;
import dev.aari.antidupe.util.ItemIdentifier;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DupeDebugManager implements Listener {

    private final AntiDupe plugin;
    private final ConfigManager configManager;
    private final Set<UUID> debugMode;
    private final Set<UUID> alertsEnabled;

    public DupeDebugManager(AntiDupe plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.debugMode = new ObjectOpenHashSet<>(16, 0.75f);
        this.alertsEnabled = new ObjectOpenHashSet<>(32, 0.75f);
    }

    public void broadcastDupeAlert(String playerName, long itemId, int duplicateCount) {
        if (!configManager.getBoolean("settings.broadcast-alerts", true)) return;

        Component alertMessage = ColorUtil.translateColorCodes(
                configManager.getString("messages.dupe-alert",
                                "&#ff4757⚠ DUPE ALERT: &#ffffff{player} &#ff4757may have duped items! ID: &#ffa502{id} &#ff4757({count} duplicates)")
                        .replace("{player}", playerName)
                        .replace("{id}", String.valueOf(itemId))
                        .replace("{count}", String.valueOf(duplicateCount))
        );

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("antidupe.alerts"))
                .filter(p -> alertsEnabled.contains(p.getUniqueId()))
                .forEach(p -> p.sendMessage(alertMessage));
    }

    public boolean toggleDebugMode(Player player) {
        UUID uuid = player.getUniqueId();
        if (debugMode.contains(uuid)) {
            debugMode.remove(uuid);
            return false;
        } else {
            debugMode.add(uuid);
            return true;
        }
    }

    public boolean toggleAlerts(Player player) {
        UUID uuid = player.getUniqueId();
        if (alertsEnabled.contains(uuid)) {
            alertsEnabled.remove(uuid);
            return false;
        } else {
            alertsEnabled.add(uuid);
            return true;
        }
    }

    public boolean isInDebugMode(Player player) {
        return debugMode.contains(player.getUniqueId());
    }

    public boolean hasAlertsEnabled(Player player) {
        return alertsEnabled.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("antidupe.alerts")) {
            alertsEnabled.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        debugMode.remove(uuid);
        alertsEnabled.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!debugMode.contains(player.getUniqueId())) return;

        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item == null || item.getType().isAir()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                showItemDebugInfo(player, item);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!debugMode.contains(player.getUniqueId())) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                showItemDebugInfo(player, item);
            }
        }, 1L);
    }

    private void showItemDebugInfo(Player player, ItemStack item) {
        if (!player.hasPermission("antidupe.admin")) return;

        Long itemId = ItemIdentifier.getItemId(item);
        if (itemId == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();

        Component debugInfo = ColorUtil.translateColorCodes("&#747d8c[DEBUG] ID: &#ffa502" + itemId);

        boolean hasDebugInfo = lore.stream()
                .anyMatch(line -> line.toString().contains("[DEBUG]"));

        if (!hasDebugInfo) {
            lore.add(Component.empty());
            lore.add(debugInfo);
            meta.lore(lore);
            item.setItemMeta(meta);
        }
    }

    public void removeDebugInfo(ItemStack item) {
        if (item == null || item.getType().isAir()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        List<Component> lore = meta.lore();
        List<Component> cleanLore = new ArrayList<>();

        for (Component line : lore) {
            if (!line.toString().contains("[DEBUG]")) {
                cleanLore.add(line);
            }
        }

        while (!cleanLore.isEmpty() && cleanLore.get(cleanLore.size() - 1).equals(Component.empty())) {
            cleanLore.remove(cleanLore.size() - 1);
        }

        meta.lore(cleanLore.isEmpty() ? null : cleanLore);
        item.setItemMeta(meta);
    }

    public void clearDebugModes() {
        for (UUID uuid : debugMode) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                cleanPlayerInventory(player);
            }
        }
        debugMode.clear();
    }

    private void cleanPlayerInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                removeDebugInfo(item);
            }
        }
    }
}