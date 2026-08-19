package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Player-facing hints derived from profession-materials metadata; no shadow encyclopedia. */
public final class MaterialSourceHints {

    private MaterialSourceHints() { }

    public static List<String> forManagedMaterial(final String materialId) {
        if (materialId == null || materialId.isBlank()) return List.of();
        final ConfigManager config = ConfigManager.current();
        if (config == null || config.getConfiguration() == null) return List.of();
        final ConfigurationSection material = config.getConfiguration().getConfigurationSection(
                "profession-materials." + materialId.trim().toLowerCase(Locale.ROOT));
        if (material == null || !material.getBoolean("economy-managed", false)) return List.of();

        final ArrayList<String> hints = new ArrayList<>(3);
        final List<String> sources = material.getStringList("source-types");
        if (!sources.isEmpty()) {
            hints.add("Forrás: " + sources.stream().limit(2).map(MaterialSourceHints::humanizeTag)
                    .reduce((left, right) -> left + " / " + right).orElse("ismeretlen"));
        }
        final String profession = material.getString("primary-profession", "").trim();
        if (!profession.isBlank()) hints.add("Feldolgozza: " + humanize(profession));
        final List<String> sinks = material.getStringList("sink-types");
        if (!sinks.isEmpty()) {
            hints.add("Felhasználás: " + sinks.stream().limit(3).map(MaterialSourceHints::humanizeTag)
                    .reduce((left, right) -> left + ", " + right).orElse("felszerelés"));
        }
        return List.copyOf(hints);
    }

    public static String humanizeTag(final String raw) {
        if (raw == null || raw.isBlank()) return "ismeretlen";
        final String normalized = raw.trim().toLowerCase(Locale.ROOT);
        final int separator = normalized.indexOf(':');
        if (separator < 0) return humanize(normalized);
        final String scope = normalized.substring(0, separator);
        final String detail = humanize(normalized.substring(separator + 1));
        return switch (scope) {
            case "gathering" -> "Gyűjtögetés • " + detail;
            case "profession-processing" -> "Feldolgozás • " + detail;
            case "combat" -> "PvE • " + detail;
            case "fishing" -> "Halászat • " + detail;
            case "mining" -> "Bányászat • " + detail;
            case "hunting" -> "Vadászat • " + detail;
            case "herbalist" -> "Gyógynövény • " + detail;
            case "profession" -> "Szakma • " + detail;
            case "catalog" -> "Katalógus • " + detail;
            default -> humanize(scope) + " • " + detail;
        };
    }

    private static String humanize(final String raw) {
        if (raw == null || raw.isBlank()) return "ismeretlen";
        final String cleaned = raw.trim().replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }
}
