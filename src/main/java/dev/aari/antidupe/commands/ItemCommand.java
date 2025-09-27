package dev.aari.antidupe.commands;

import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.data.ItemRegistry;
import dev.aari.antidupe.util.ItemIdentifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ItemCommand implements CommandExecutor {

    private final ItemRegistry itemRegistry;
    private final ConfigManager configManager;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public ItemCommand(ItemRegistry itemRegistry, ConfigManager configManager) {
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

        if (args.length > 0 && "history".equals(args[0])) {
            return handleHistory(sender, args);
        }

        sender.sendMessage(configManager.getMessage("usage-item"));
        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        Long itemId = null;

        if (args.length > 1) {
            try {
                itemId = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(configManager.getMessage("invalid-id-format"));
                return true;
            }
        } else if (sender instanceof Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                sender.sendMessage(configManager.getMessage("hold-item-or-specify"));
                return true;
            }
            itemId = ItemIdentifier.getItemId(item);
            if (itemId == null) {
                sender.sendMessage(configManager.getMessage("no-tracking-id"));
                return true;
            }
        } else {
            sender.sendMessage(configManager.getMessage("console-specify-id"));
            return true;
        }

        final Long finalItemId = itemId;
        CompletableFuture.supplyAsync(() -> itemRegistry.getItemHistory(finalItemId))
                .thenAccept(history -> displayHistory(sender, finalItemId, history))
                .exceptionally(throwable -> {
                    sender.sendMessage(configManager.getMessage("error-history"));
                    return null;
                });

        return true;
    }

    private void displayHistory(CommandSender sender, long itemId, List<ItemRegistry.ItemAction> history) {
        ItemRegistry.TrackedItem item = itemRegistry.getItem(itemId);
        if (item == null) {
            sender.sendMessage(configManager.getMessage("item-not-found"));
            return;
        }

        sender.sendMessage(configManager.getMessage("history-for-item", "id", itemId));

        String createdTime = TIME_FORMAT.format(Instant.ofEpochMilli(item.timestamp()));
        sender.sendMessage(configManager.getMessage("created-by",
                "time", createdTime, "creator", item.creator()));

        if (history.isEmpty()) {
            sender.sendMessage(configManager.getMessage("no-actions"));
            return;
        }

        sender.sendMessage(configManager.getMessage("actions-header"));

        int maxDisplay = configManager.getInt("settings.max-history-display", 10);

        history.stream()
                .sorted((a, b) -> Long.compare(b.timestamp(), a.timestamp()))
                .limit(maxDisplay)
                .forEach(action -> {
                    String actionTime = TIME_FORMAT.format(Instant.ofEpochMilli(action.timestamp()));
                    sender.sendMessage(configManager.getMessage("action-entry",
                            "time", actionTime,
                            "action", action.action(),
                            "player", action.player()));
                });

        if (history.size() > maxDisplay) {
            int remaining = history.size() - maxDisplay;
            sender.sendMessage(configManager.getMessage("more-actions", "count", remaining));
        }
    }
}