package hu.taliann.icesmp.utils;

import org.bukkit.Material;

import java.util.Locale;

/** Compatibility-aware Bukkit material resolver for persisted IceSMP configuration. */
public final class ConfigMaterialResolver {
    private ConfigMaterialResolver() {
    }

    public static Material match(final String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        final String normalized = configured.trim().toUpperCase(Locale.ROOT);
        if ("CHAIN".equals(normalized) || "MINECRAFT:CHAIN".equals(normalized)) {
            return Material.IRON_CHAIN;
        }
        return Material.matchMaterial(configured.trim());
    }
}
