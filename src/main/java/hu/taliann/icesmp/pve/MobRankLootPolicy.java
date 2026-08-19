package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.managers.ConfigManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Thin rank-aware policy layered on the existing loot authority.
 *
 * <p>Ranks unlock authored source pools and small additive probability shifts. They never multiply
 * loot quantity, bypass BuildAware selection, or create rank currencies.</p>
 */
public final class MobRankLootPolicy {

    public record RewardBand(String id, Set<String> sourceTags, double gearChanceAdditive,
                             double blueprintChance, String specialMaterial,
                             double specialMaterialChance, boolean bossLike) {
        public RewardBand {
            id = normalize(id);
            sourceTags = sourceTags == null ? Set.of() : Set.copyOf(sourceTags);
            gearChanceAdditive = bounded(gearChanceAdditive, 0.0D, 0.15D);
            blueprintChance = bounded(blueprintChance, 0.0D, 0.15D);
            specialMaterial = specialMaterial == null ? "" : normalize(specialMaterial);
            specialMaterialChance = bounded(specialMaterialChance, 0.0D, 0.05D);
        }

        public String primarySourceTag() {
            return sourceTags.stream().findFirst().orElse("combat:wilderness");
        }
    }

    private MobRankLootPolicy() { }

    public static RewardBand resolve(final MobRank rank, final ConfigManager config) {
        final MobRank effective = rank == null ? MobRank.NORMAL : rank;
        final String band = switch (effective) {
            case NORMAL -> "normal";
            case VETERAN -> "veteran";
            case ELITE -> "elite";
            case CHAMPION -> "champion";
            case MINIBOSS, BOSS, WORLD_BOSS -> "boss";
        };
        final String root = "loot.rank-rewards." + band;
        final List<String> authored = config == null ? List.of() : config.getStringList(root + ".source-tags");
        final LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (final String raw : authored.isEmpty() ? defaultSources(band) : authored) {
            if (raw != null && !raw.isBlank()) tags.add(normalize(raw));
        }
        final double gearAdd = config == null ? defaultGearAdd(band)
                : config.getDouble(root + ".gear-chance-additive", defaultGearAdd(band));
        final double blueprint = config == null ? defaultBlueprint(band)
                : config.getDouble(root + ".blueprint-chance", defaultBlueprint(band));
        final String special = config == null ? "" : config.getString(root + ".special-material", "");
        final double specialChance = config == null ? 0.0D
                : config.getDouble(root + ".special-material-chance", 0.0D);
        return new RewardBand(band, tags, gearAdd, blueprint, special, specialChance,
                "boss".equals(band));
    }

    private static List<String> defaultSources(final String band) {
        return switch (band) {
            case "veteran" -> List.of("combat:veteran", "combat:wilderness");
            case "elite" -> List.of("combat:elite", "combat:veteran", "combat:wilderness");
            case "champion" -> List.of("combat:champion", "combat:elite", "combat:veteran", "combat:wilderness");
            case "boss" -> List.of("combat:boss");
            default -> List.of("combat:wilderness");
        };
    }

    private static double defaultGearAdd(final String band) {
        return switch (band) {
            case "veteran" -> 0.02D;
            case "elite" -> 0.04D;
            case "champion" -> 0.06D;
            default -> 0.0D;
        };
    }

    private static double defaultBlueprint(final String band) {
        return switch (band) {
            case "veteran" -> 0.0035D;
            case "elite" -> 0.006D;
            case "champion" -> 0.01D;
            case "boss" -> 0.05D;
            default -> 0.002D;
        };
    }

    private static String normalize(final String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static double bounded(final double value, final double minimum, final double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
