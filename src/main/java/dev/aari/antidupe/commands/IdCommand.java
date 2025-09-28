package dev.aari.antidupe.commands;

import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.data.ItemRegistry;
import dev.aari.antidupe.util.ItemIdentifier;
import dev.aari.antidupe.util.SoundUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class IdCommand implements CommandExecutor {

    private final ItemRegistry itemRegistry;
    private final ConfigManager configManager;

    public IdCommand(ItemRegistry itemRegistry, ConfigManager configManager) {
        this.itemRegistry = itemRegistry;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("antidupe.admin")) {
            if (sender instanceof Player player) {
                SoundUtil.sendActionBar(player, configManager.getMessage("no-permission"));
                SoundUtil.playErrorSound(player);
            } else {
                sender.sendMessage(configManager.getMessage("no-permission"));
            }
            return true;
        }

        if (args.length == 0 || "check".equals(args[0])) {
            return handleCheck(sender);
        }

        if ("lookup".equals(args[0]) && args.length > 1) {
            return handleLookup(sender, args[1]);
        }

        if (sender instanceof Player player) {
            SoundUtil.sendActionBar(player, configManager.getMessage("usage-id"));
            SoundUtil.playErrorSound(player);
        } else {
            sender.sendMessage(configManager.getMessage("usage-id"));
        }
        return true;
    }

    private boolean handleCheck(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("console-cannot-check"));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            SoundUtil.sendActionBar(player, configManager.getMessage("hold-item"));
            SoundUtil.playErrorSound(player);
            return true;
        }

        CompletableFuture.supplyAsync(() -> {
            Long existingId = ItemIdentifier.getItemId(item);
            return existingId != null ? existingId :
                    itemRegistry.registerItem(item, "CHECKED", player.getName());
        }).thenAccept(id -> {
            if (id == -1L) {
                SoundUtil.sendActionBar(player, configManager.getMessage("invalid-item"));
                SoundUtil.playErrorSound(player);
                return;
            }

            player.sendMessage(configManager.getMessage("item-id", "id", id));
            SoundUtil.playSuccessSound(player);

            List<ItemRegistry.TrackedItem> duplicates = itemRegistry.findDuplicates(id);
            if (!duplicates.isEmpty()) {
                player.sendMessage(configManager.getMessage("duplicates-warning", "count", duplicates.size()));
                SoundUtil.playErrorSound(player); // Alert sound for duplicates
            }
        }).exceptionally(throwable -> {
            SoundUtil.sendActionBar(player, configManager.getMessage("error-checking"));
            SoundUtil.playErrorSound(player);
            return null;
        });

        return true;
    }

    private boolean handleLookup(CommandSender sender, String idStr) {
        long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            if (sender instanceof Player player) {
                SoundUtil.sendActionBar(player, configManager.getMessage("invalid-id-format"));
                SoundUtil.playErrorSound(player);
            } else {
                sender.sendMessage(configManager.getMessage("invalid-id-format"));
            }
            return true;
        }

        CompletableFuture.supplyAsync(() -> itemRegistry.findDuplicates(id))
                .thenAccept(duplicates -> {
                    ItemRegistry.TrackedItem original = itemRegistry.getItem(id);
                    if (original == null) {
                        if (sender instanceof Player player) {
                            SoundUtil.sendActionBar(player, configManager.getMessage("item-not-found"));
                            SoundUtil.playErrorSound(player);
                        } else {
                            sender.sendMessage(configManager.getMessage("item-not-found"));
                        }
                        return;
                    }

                    sender.sendMessage(configManager.getMessage("original-item",
                            "id", id, "creator", original.creator()));

                    if (duplicates.isEmpty()) {
                        sender.sendMessage(configManager.getMessage("no-duplicates"));
                        if (sender instanceof Player player) {
                            SoundUtil.playSuccessSound(player);
                        }
                    } else {
                        sender.sendMessage(configManager.getMessage("duplicates-found", "count", duplicates.size()));

                        for (ItemRegistry.TrackedItem dupe : duplicates) {
                            sender.sendMessage(configManager.getMessage("duplicate-entry",
                                    "id", dupe.id(), "creator", dupe.creator()));
                        }

                        if (sender instanceof Player player) {
                            SoundUtil.playErrorSound(player); // Alert sound for found duplicates
                        }
                    }
                })
                .exceptionally(throwable -> {
                    if (sender instanceof Player player) {
                        SoundUtil.sendActionBar(player, configManager.getMessage("error-lookup"));
                        SoundUtil.playErrorSound(player);
                    } else {
                        sender.sendMessage(configManager.getMessage("error-lookup"));
                    }
                    return null;
                });

        return true;
    }
}