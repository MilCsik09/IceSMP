package hu.taliann.icesmp.itemization;

import java.util.Locale;

public enum ItemRarity {
    COMMON("kozonseges", "Közönséges"),
    UNCOMMON("nem_mindennapi", "Nem mindennapi"),
    RARE("ritka", "Ritka"),
    EPIC("epikus", "Epikus"),
    LEGENDARY("legendas", "Legendás"),
    MYTHIC("mitikus", "Mitikus");

    private final String id;
    private final String displayName;

    ItemRarity(final String id, final String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static ItemRarity parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("item rarity is required");
        }
        final String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (final ItemRarity rarity : values()) {
            if (rarity.id.equals(normalized) || rarity.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return rarity;
            }
        }
        throw new IllegalArgumentException("unknown item rarity: " + raw);
    }
}
