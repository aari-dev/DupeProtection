package dev.aari.antidupe.commands;

import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.data.ItemRegistry;
import dev.aari.antidupe.managers.DupeDebugManager;
import dev.aari.antidupe.util.ColorUtil;
import dev.aari.antidupe.util.ItemIdentifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class DupeCommand implements CommandExecutor {

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
            default -> showHelp(sender);
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.translateColorCodes("&#ffa502AntiDupe Debug Commands:"));
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8c• &#ffffff/dupe mode &#747d8c- Toggle debug mode (shows ItemIDs in lore)"));
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8c• &#ffffff/dupe alerts &#747d8c- Toggle dupe alert notifications"));
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8c• &#ffffff/dupe scan [player] &#747d8c- Scan inventory for duplicates"));
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8c• &#ffffff/dupe clean &#747d8c- Remove debug info from held item"));
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8c• &#ffffff/dupe test &#747d8c- Test dupe detection on held item"));
    }

    private void handleMode(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.translateColorCodes("&#ff4757Console cannot use debug mode."));
            return;
        }

        boolean enabled = debugManager.toggleDebugMode(player);
        String status = enabled ? "&#2ed573enabled" : "&#ff4757disabled";
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8cDebug mode " + status + "&#747d8c."));

        if (enabled) {
            sender.sendMessage(ColorUtil.translateColorCodes("&#ffa502ItemIDs will now appear in item lore."));
        } else {
            sender.sendMessage(ColorUtil.translateColorCodes("&#747d8cCleaning debug info from inventory..."));
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    debugManager.removeDebugInfo(item);
                }
            }
        }
    }

    private void handleAlerts(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.translateColorCodes("&#ff4757Console cannot toggle alerts."));
            return;
        }

        boolean enabled = debugManager.toggleAlerts(player);
        String status = enabled ? "&#2ed573enabled" : "&#ff4757disabled";
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8cDupe alerts " + status + "&#747d8c."));
    }

    private void handleScan(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.translateColorCodes("&#ff4757Console cannot scan inventories."));
            return;
        }

        Player target = args.length > 1 ? sender.getServer().getPlayer(args[1]) : player;
        if (target == null) {
            sender.sendMessage(ColorUtil.translateColorCodes("&#ff4757Player not found."));
            return;
        }

        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8cScanning " + target.getName() + "'s inventory..."));

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
                        sender.sendMessage(ColorUtil.translateColorCodes(
                                "&#ff4757• " + item.getType().name() + " &#747d8c(ID: " + itemId + ") - " +
                                        duplicates.size() + " duplicates"));
                    }
                }
            }

            sender.sendMessage(ColorUtil.translateColorCodes(
                    "&#747d8cScan complete: &#ffffff" + totalTracked + " &#747d8ctracked items, &#ff4757" +
                            suspiciousItems + " &#747d8csuspicious items found."));
        });
    }

    private void handleClean(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.translateColorCodes("&#ff4757Console cannot clean items."));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            sender.sendMessage(ColorUtil.translateColorCodes("&#ff4757Hold an item to clean."));
            return;
        }

        debugManager.removeDebugInfo(item);
        sender.sendMessage(ColorUtil.translateColorCodes("&#2ed573Debug info removed from item."));
    }

    private void handleTest(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.translateColorCodes("&#ff4757Console cannot test items."));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            sender.sendMessage(ColorUtil.translateColorCodes("&#ff4757Hold an item to test."));
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

            sender.sendMessage(ColorUtil.translateColorCodes("&#747d8cTest Results:"));
            sender.sendMessage(ColorUtil.translateColorCodes("&#747d8cItem ID: &#ffa502" + itemId));
            sender.sendMessage(ColorUtil.translateColorCodes("&#747d8cFingerprint: &#ffffff" +
                    ItemIdentifier.createFingerprint(item).substring(0, 16) + "..."));

            if (duplicates.isEmpty()) {
                sender.sendMessage(ColorUtil.translateColorCodes("&#2ed573No duplicates found - item appears legitimate."));
            } else {
                sender.sendMessage(ColorUtil.translateColorCodes("&#ff4757WARNING: " + duplicates.size() + " potential duplicates found!"));
                duplicates.stream().limit(3).forEach(dupe ->
                        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8c• ID " + dupe.id() + " by " + dupe.creator())));

                debugManager.broadcastDupeAlert(player.getName(), itemId, duplicates.size());
            }
        });
    }
}