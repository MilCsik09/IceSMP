package hu.taliann.icesmp.gui;

import java.util.List;

/**
 * Szerkeszthető config-bejegyzés a második hullám összetettebb felületeihez.
 * A régi {@link ConfigMenuGUI.Entry} változatlan marad; ez a típus hozzáadja a
 * szöveg- és stringlista-bevitelt, valamint a bemeneti korlátokat.
 */
public record AdvancedConfigEntry(String key, String label, Type type,
                                  double step, double min, double max,
                                  List<String> options, int maxLength,
                                  int maxItems, String itemPattern,
                                  boolean allowBlank, String description) {

    public enum Type { TOGGLE, NUMBER, INTEGER, CYCLE, TEXT, STRING_LIST }

    public AdvancedConfigEntry {
        options = options == null ? List.of() : List.copyOf(options);
        itemPattern = itemPattern == null ? "" : itemPattern;
        description = description == null ? "" : description;
        if (key == null || key.isBlank() || label == null || label.isBlank()) {
            throw new IllegalArgumentException("Az advanced config kulcsa és címkéje kötelező.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Az advanced config típusa kötelező.");
        }
        if ((type == Type.TEXT || type == Type.STRING_LIST) && maxLength < 1) {
            throw new IllegalArgumentException("A szöveges config maxLength értéke pozitív kell legyen.");
        }
        if (type == Type.STRING_LIST && maxItems < 1) {
            throw new IllegalArgumentException("A stringlista maxItems értéke pozitív kell legyen.");
        }
    }

    public static AdvancedConfigEntry toggle(final String key, final String label,
                                             final String description) {
        return new AdvancedConfigEntry(key, label, Type.TOGGLE, 0, 0, 0,
                List.of(), 0, 0, "", false, description);
    }

    public static AdvancedConfigEntry number(final String key, final String label,
                                             final double step, final double min,
                                             final double max, final String description) {
        return new AdvancedConfigEntry(key, label, Type.NUMBER, step, min, max,
                List.of(), 0, 0, "", false, description);
    }

    public static AdvancedConfigEntry integer(final String key, final String label,
                                              final int step, final int min,
                                              final int max, final String description) {
        return new AdvancedConfigEntry(key, label, Type.INTEGER, step, min, max,
                List.of(), 0, 0, "", false, description);
    }

    public static AdvancedConfigEntry cycle(final String key, final String label,
                                            final List<String> options,
                                            final String description) {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("A cycle entry legalább egy opciót igényel.");
        }
        return new AdvancedConfigEntry(key, label, Type.CYCLE, 0, 0, 0,
                options, 0, 0, "", false, description);
    }

    public static AdvancedConfigEntry text(final String key, final String label,
                                           final int maxLength, final boolean allowBlank,
                                           final String pattern, final String description) {
        return new AdvancedConfigEntry(key, label, Type.TEXT, 0, 0, 0,
                List.of(), maxLength, 0, pattern, allowBlank, description);
    }

    public static AdvancedConfigEntry stringList(final String key, final String label,
                                                 final int maxItems, final int maxItemLength,
                                                 final boolean allowBlank,
                                                 final String itemPattern,
                                                 final String description) {
        return new AdvancedConfigEntry(key, label, Type.STRING_LIST, 0, 0, 0,
                List.of(), maxItemLength, maxItems, itemPattern, allowBlank, description);
    }
}
