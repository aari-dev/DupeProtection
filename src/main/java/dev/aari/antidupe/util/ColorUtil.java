package dev.aari.antidupe.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.legacySection();

    private ColorUtil() {}

    public static Component translateColorCodes(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        String processed = message.replaceAll("&([0-9a-fk-or])", "§$1");

        Matcher matcher = HEX_PATTERN.matcher(processed);
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            String hexCode = matcher.group(1);
            String replacement = "§x"
                    + "§" + hexCode.charAt(0)
                    + "§" + hexCode.charAt(1)
                    + "§" + hexCode.charAt(2)
                    + "§" + hexCode.charAt(3)
                    + "§" + hexCode.charAt(4)
                    + "§" + hexCode.charAt(5);
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);

        return SERIALIZER.deserialize(buffer.toString())
                .decoration(TextDecoration.ITALIC, false);
    }
}