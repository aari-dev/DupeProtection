package dev.aari.antidupe.commands;

import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.util.ColorUtil;
import dev.aari.antidupe.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class AnnounceCommand implements CommandExecutor {

    private final ConfigManager configManager;

    public AnnounceCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("antidupe.announce")) {
            SoundUtil.sendActionBar((Player) sender, configManager.getMessage("no-permission"));
            if (sender instanceof Player player) {
                SoundUtil.playErrorSound(player);
            }
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                SoundUtil.sendActionBar(player, configManager.getMessage("announce-usage"));
                SoundUtil.playErrorSound(player);
            } else {
                sender.sendMessage(configManager.getMessage("announce-usage"));
            }
            return true;
        }

        String message = String.join(" ", args);

        for (Player player : Bukkit.getOnlinePlayers()) {
            SoundUtil.sendAnnouncement(player, ColorUtil.translateColorCodes("&#FFFFFF" + message));
        }

        if (sender instanceof Player player) {
            SoundUtil.sendActionBar(player, configManager.getMessage("announcement-sent"));
            SoundUtil.playSuccessSound(player);
        } else {
            sender.sendMessage(configManager.getMessage("announcement-sent"));
        }

        return true;
    }
}