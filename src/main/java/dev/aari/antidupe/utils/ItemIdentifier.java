package dev.aari.antidupe.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public final class ItemIdentifier {

    public static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("antidupe", "item_id");
    private static final MessageDigest SHA256;

    static {
        try {
            SHA256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private ItemIdentifier() {}

    public static String createFingerprint(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(256);
        builder.append(item.getType().name())
                .append('|')
                .append(item.getAmount());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                builder.append('|').append(meta.displayName());
            }
            if (meta.hasLore()) {
                builder.append('|').append(Objects.toString(meta.lore()));
            }
            if (meta.hasEnchants()) {
                meta.getEnchants().forEach((enchant, level) ->
                        builder.append('|').append(enchant.getKey()).append(':').append(level));
            }
            if (meta.hasCustomModelData()) {
                builder.append('|').append(meta.getCustomModelData());
            }
        }

        synchronized (SHA256) {
            SHA256.reset();
            byte[] hash = SHA256.digest(builder.toString().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        }
    }

    public static void markItem(ItemStack item, long id) {
        if (item == null || item.getType().isAir()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ITEM_ID_KEY, PersistentDataType.LONG, id);
            item.setItemMeta(meta);
        }
    }

    public static Long getItemId(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        return meta.getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.LONG);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}