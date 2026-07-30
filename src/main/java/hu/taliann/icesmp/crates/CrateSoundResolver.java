package hu.taliann.icesmp.crates;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.util.Locale;

/** Resolves both legacy enum-style and canonical registry sound identifiers. */
public final class CrateSoundResolver {
    private CrateSoundResolver() {
    }

    static String enumName(final String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String name = configured.trim();
        if (name.regionMatches(true, 0, "minecraft:", 0, "minecraft:".length())) {
            name = name.substring("minecraft:".length());
        }
        return name.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("deprecation")
    public static Sound resolve(final String configured) {
        final String enumName = enumName(configured);
        if (enumName == null) {
            return null;
        }
        final String raw = configured.trim();
        try {
            return Sound.valueOf(enumName);
        } catch (final IllegalArgumentException ignored) {
            // Custom/non-enum registry keys continue through the canonical registry path.
        }
        final String namespaced = raw.contains(":") ? raw : "minecraft:" + raw;
        final NamespacedKey key = NamespacedKey.fromString(namespaced.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.SOUNDS.get(key);
    }
}
