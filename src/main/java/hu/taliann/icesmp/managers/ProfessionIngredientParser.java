package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.ConfigMaterialResolver;
import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Strict, atomic parser for profession recipe ingredient rows. */
public final class ProfessionIngredientParser {
    private ProfessionIngredientParser() {
    }

    public record ParsedIngredients(Map<Material, Integer> materials, Map<String, Integer> uniqueMaterials) {
        public ParsedIngredients {
            materials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
            uniqueMaterials = Collections.unmodifiableMap(new LinkedHashMap<>(uniqueMaterials));
        }
    }

    public static ParsedIngredients parse(final List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("hiányzó ingredient-lista");
        }
        final Map<Material, Integer> materials = new LinkedHashMap<>();
        final Map<String, Integer> uniqueMaterials = new LinkedHashMap<>();
        for (final String token : tokens) {
            parseToken(token, materials, uniqueMaterials);
        }
        if (materials.isEmpty() && uniqueMaterials.isEmpty()) {
            throw new IllegalArgumentException("üres ingredient-lista");
        }
        return new ParsedIngredients(materials, uniqueMaterials);
    }

    private static void parseToken(final String token, final Map<Material, Integer> materials,
                                   final Map<String, Integer> uniqueMaterials) {
        if (token == null || token.isBlank()) {
            throw invalid(token, "üres bejegyzés");
        }
        final String[] parts = token.split(":", -1);
        if ("unique".equalsIgnoreCase(parts[0].trim())) {
            if (parts.length != 3 || parts[1].isBlank()) {
                throw invalid(token, "formátum: unique:<id>:<darab>");
            }
            merge(uniqueMaterials, parts[1].trim().toLowerCase(Locale.ROOT),
                    positiveCount(parts[2], token));
            return;
        }
        if (parts.length < 1 || parts.length > 2 || parts[0].isBlank()) {
            throw invalid(token, "formátum: MATERIAL:<darab>");
        }
        final Material material = ConfigMaterialResolver.match(parts[0]);
        if (material == null) {
            throw invalid(token, "ismeretlen Material");
        }
        merge(materials, material, parts.length == 2 ? positiveCount(parts[1], token) : 1);
    }

    private static int positiveCount(final String raw, final String token) {
        try {
            final int count = Integer.parseInt(raw.trim());
            if (count <= 0) {
                throw invalid(token, "a darabszám legyen pozitív");
            }
            return count;
        } catch (final NumberFormatException invalidNumber) {
            throw invalid(token, "érvénytelen darabszám");
        }
    }

    private static <K> void merge(final Map<K, Integer> target, final K key, final int count) {
        try {
            target.merge(key, count, Math::addExact);
        } catch (final ArithmeticException overflow) {
            throw new IllegalArgumentException("ingredient-darabszám túlcsordul", overflow);
        }
    }

    private static IllegalArgumentException invalid(final String token, final String reason) {
        return new IllegalArgumentException("'" + token + "': " + reason);
    }
}
