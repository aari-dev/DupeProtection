package dev.aari.antidupe.commands;

import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.data.ItemRegistry;
import dev.aari.antidupe.util.ColorUtil;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class AdminCommand implements CommandExecutor {

    private final ItemRegistry itemRegistry;
    private final ConfigManager configManager;

    public AdminCommand(ItemRegistry itemRegistry, ConfigManager configManager) {
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
            case "reload" -> {
                configManager.reload();
                sender.sendMessage(ColorUtil.translateColorCodes("&#2ed573Configuration reloaded successfully!"));
            }
            case "stats" -> showStats(sender);
            default -> showHelp(sender);
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.translateColorCodes("&#ffa502AntiDupe Admin Commands:"));
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8c• &#ffffff/antidupe reload &#747d8c- Reload configuration"));
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8c• &#ffffff/antidupe stats &#747d8c- Show tracking statistics"));
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8c• &#ffffff/id check &#747d8c- Check item in hand"));
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8c• &#ffffff/id lookup <id> &#747d8c- Find duplicates"));
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8c• &#ffffff/item history [id] &#747d8c- Show item history"));
    }

    private void showStats(CommandSender sender) {
        Object2LongMap<String> stats = itemRegistry.getItemTypeStatistics();

        sender.sendMessage(ColorUtil.translateColorCodes("&#ffa502Item Tracking Statistics:"));

        long totalItems = stats.values().longStream().sum();
        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8cTotal tracked items: &#ffffff" + totalItems));

        sender.sendMessage(ColorUtil.translateColorCodes("&#747d8cTop item types:"));
        stats.object2LongEntrySet().stream()
                .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                .limit(10)
                .forEach(entry -> sender.sendMessage(ColorUtil.translateColorCodes(
                        "&#747d8c• &#ffffff" + entry.getKey() + "&#747d8c: &#ffa502" + entry.getLongValue())));
    }
}