package hu.taliann.icesmp.utils;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class TextUtil {

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private TextUtil() {
    }

    public static String color(final String text) {
        if (text == null) {
            return "";
        }
        // Keep return type as String, but use Adventure serializers instead of deprecated ChatColor.
        return SECTION.serialize(AMPERSAND.deserialize(text));
    }
}

