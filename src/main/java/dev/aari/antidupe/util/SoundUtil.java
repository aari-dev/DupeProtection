package dev.aari.antidupe.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class SoundUtil {

    private SoundUtil() {}

    public static void playSuccessSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
    }

    public static void playErrorSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    public static void playAnnouncementSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 1.0f);
    }

    public static void sendActionBar(Player player, Component message) {
        player.sendActionBar(message);
    }

    public static void sendTitle(Player player, Component title, Component subtitle) {
        Title titleObject = Title.title(
                title,
                subtitle,
                Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(1)
                )
        );
        player.showTitle(titleObject);
    }

    public static void sendAnnouncement(Player player, Component message) {
        Component title = dev.aari.antidupe.util.ColorUtil.translateColorCodes("&#AAFF00&lANNOUNCEMENT");
        sendTitle(player, title, message);
        playAnnouncementSound(player);
    }
}