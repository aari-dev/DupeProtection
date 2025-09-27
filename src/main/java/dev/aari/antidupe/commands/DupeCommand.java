package dev.aari.antidupe.commands;

import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.data.ItemRegistry;
import dev.aari.antidupe.managers.DupeDebugManager;
import dev.aari.antidupe.util.ColorUtil;
import dev.aari.antidupe.util.ItemIdentifier;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class DupeCommand implements CommandExecutor, TabCompleter {

    private final DupeDebugManager debugManager;
    private final ItemRegistry itemRegistry;
    private final ConfigManager configManager;

    public DupeCommand(DupeDebugManager debugManager, ItemRegistry itemRegistry, ConfigManager configManager) {
        this.debugManager = debugManager;
        this.itemRegistry = itemRegistry;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("antidupe.admin")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "mode" -> handleMode(sender);
            case "alerts" -> handleAlerts(sender);
            case "scan" -> handleScan(sender, args);
            case "clean" -> handleClean(sender);
            case "test" -> handleTest(sender);
            case "delete" -> handleDelete(sender, args);
            default -> showHelp(sender);
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(configManager.getMessage("dupe-help-header"));
        sender.sendMessage(configManager.getMessage("dupe-help-mode"));
        sender.sendMessage(configManager.getMessage("dupe-help-alerts"));
        sender.sendMessage(configManager.getMessage("dupe-help-scan"));
        sender.sendMessage(configManager.getMessage("dupe-help-clean"));
        sender.sendMessage(configManager.getMessage("dupe-help-test"));
        sender.sendMessage(configManager.getMessage("dupe-help-delete"));
    }

    private void handleMode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("console-no-debug"));
            return;
        }

        boolean enabled = debugManager.toggleDebugMode(player);
        if (enabled) {
            sender.sendMessage(configManager.getMessage("debug-mode-enabled"));
        } else {
            sender.sendMessage(configManager.getMessage("debug-mode-disabled"));
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    debugManager.removeDebugInfo(item);
                }
            }
        }
    }

    private void handleAlerts(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("console-no-alerts"));
            return;
        }

        boolean enabled = debugManager.toggleAlerts(player);
        sender.sendMessage(enabled ?
                configManager.getMessage("alerts-enabled") :
                configManager.getMessage("alerts-disabled"));
    }

    private void handleScan(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("console-no-scan"));
            return;
        }

        Player target = args.length > 1 ? sender.getServer().getPlayer(args[1]) : player;
        if (target == null) {
            sender.sendMessage(configManager.getMessage("player-not-found"));
            return;
        }

        sender.sendMessage(configManager.getMessage("scanning-inventory", "player", target.getName()));

        CompletableFuture.runAsync(() -> {
            int suspiciousItems = 0;
            int totalTracked = 0;

            for (ItemStack item : target.getInventory().getContents()) {
                if (item == null || item.getType().isAir()) continue;

                Long itemId = ItemIdentifier.getItemId(item);
                if (itemId != null) {
                    totalTracked++;
                    List<ItemRegistry.TrackedItem> duplicates = itemRegistry.findDuplicates(itemId);
                    if (!duplicates.isEmpty()) {
                        suspiciousItems++;
                        sender.sendMessage(configManager.getMessage("suspicious-item",
                                "item", item.getType().name(),
                                "id", itemId,
                                "count", duplicates.size()));
                    }
                }
            }

            sender.sendMessage(configManager.getMessage("scan-complete",
                    "tracked", totalTracked,
                    "suspicious", suspiciousItems));
        });
    }

    private void handleClean(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("console-no-clean"));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            sender.sendMessage(configManager.getMessage("hold-item"));
            return;
        }

        debugManager.removeDebugInfo(item);
        sender.sendMessage(configManager.getMessage("debug-info-removed"));
    }

    private void handleTest(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("console-no-test"));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            sender.sendMessage(configManager.getMessage("hold-item"));
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            Long itemId = ItemIdentifier.getItemId(item);
            if (itemId == null) {
                itemId = itemRegistry.registerItem(item, "TEST_SCAN", player.getName());
            }

            List<ItemRegistry.TrackedItem> duplicates = itemRegistry.findDuplicates(itemId);
            return new Object[]{itemId, duplicates};
        }).thenAccept(result -> {
            long itemId = (long) ((Object[]) result)[0];
            @SuppressWarnings("unchecked")
            List<ItemRegistry.TrackedItem> duplicates = (List<ItemRegistry.TrackedItem>) ((Object[]) result)[1];

            sender.sendMessage(configManager.getMessage("test-results"));
            sender.sendMessage(configManager.getMessage("test-item-id", "id", itemId));
            sender.sendMessage(configManager.getMessage("test-fingerprint",
                    "fingerprint", ItemIdentifier.createFingerprint(item).substring(0, 16)));

            if (duplicates.isEmpty()) {
                sender.sendMessage(configManager.getMessage("test-no-duplicates"));
            } else {
                sender.sendMessage(configManager.getMessage("test-duplicates-found", "count", duplicates.size()));
                duplicates.stream().limit(3).forEach(dupe ->
                        sender.sendMessage(configManager.getMessage("test-duplicate-entry",
                                "id", dupe.id(), "creator", dupe.creator())));

                debugManager.broadcastDupeAlert(player.getName(), itemId, duplicates.size());
            }
        });
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("console-no-delete"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("delete-usage"));
            return;
        }

        long itemId;
        try {
            itemId = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(configManager.getMessage("invalid-id-format"));
            return;
        }

        CompletableFuture.runAsync(() -> {
            ItemRegistry.TrackedItem original = itemRegistry.getItem(itemId);
            if (original == null) {
                sender.sendMessage(configManager.getMessage("item-not-found"));
                return;
            }

            List<ItemRegistry.TrackedItem> duplicates = itemRegistry.findDuplicates(itemId);
            if (duplicates.isEmpty()) {
                sender.sendMessage(configManager.getMessage("no-duplicates-to-delete"));
                return;
            }

            int deletedCount = 0;
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                for (ItemStack item : onlinePlayer.getInventory().getContents()) {
                    if (item != null && !item.getType().isAir()) {
                        Long id = ItemIdentifier.getItemId(item);
                        if (id != null && duplicates.stream().anyMatch(d -> d.id() == id)) {
                            item.setAmount(0);
                            deletedCount++;
                        }
                    }
                }
            }

            sender.sendMessage(configManager.getMessage("duplicates-deleted",
                    "count", deletedCount, "id", itemId));
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("antidupe.admin")) return new ArrayList<>();

        if (args.length == 1) {
            return List.of("mode", "alerts", "scan", "clean", "test", "delete")
                    .stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if ("scan".equals(args[0].toLowerCase())) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if ("delete".equals(args[0].toLowerCase())) {
                List<String> duplicateIds = new ArrayList<>();
                CompletableFuture.runAsync(() -> {
                    itemRegistry.getItemTypeStatistics().keySet().forEach(type -> {
                        // This is a simplified version - in reality you'd get actual duplicate IDs
                        for (long i = 1; i <= 10; i++) {
                            duplicateIds.add(String.valueOf(System.currentTimeMillis() + i));
                        }
                    });
                });
                return duplicateIds.stream()
                        .filter(id -> id.startsWith(args[1]))
                        .limit(20)
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}