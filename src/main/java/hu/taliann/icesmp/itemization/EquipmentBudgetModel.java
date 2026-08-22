package hu.taliann.icesmp.itemization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Repeatable normalized audit model; it reports authored drift and never rewrites an item. */
public final class EquipmentBudgetModel {

    public record Budget(double offensive, double defensive, double utility,
                         double normalizedTotal, double expectedTierBudget,
                         double physicalEffectiveHealthSignal) {
        public double totalRaw() {
            return offensive + defensive + utility;
        }
    }

    private static final Map<String, Double> STAT_WEIGHTS = Map.of(
            "attack_damage", 5.0D,
            "attack_speed", 8.0D,
            "ability_power", 1.25D,
            "max_health", 0.70D,
            "armor", 2.0D,
            "armor_toughness", 2.5D,
            "movement_speed", 120.0D);

    private EquipmentBudgetModel() {
    }

    public static Budget midpoint(final ItemTemplate template, final ArmorFamilyProfile profile) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(profile, "profile");
        if (template.armorFamily() != profile.family()) {
            throw new IllegalArgumentException("template and family profile differ");
        }
        final LinkedHashMap<String, Double> values = new LinkedHashMap<>(template.fixedStats());
        template.rolledStats().forEach((id, range) -> values.merge(id, range.valueAt(0.5D), Double::sum));
        if (template.baseDamage() > 0.0D) values.merge("attack_damage", template.baseDamage(), Double::sum);
        if (template.baseArmor() > 0.0D) values.merge("armor", template.baseArmor(), Double::sum);
        return evaluate(template, profile, values);
    }

    public static Budget rolled(final ItemTemplate template, final ArmorFamilyProfile profile,
                                final String stageId, final Map<String, Double> rolledValues) {
        final LinkedHashMap<String, Double> values = new LinkedHashMap<>(template.fixedStatsAt(stageId));
        if (rolledValues != null) rolledValues.forEach((id, value) -> values.merge(id, value, Double::sum));
        if (template.baseDamage() > 0.0D) values.merge("attack_damage", template.baseDamage(), Double::sum);
        if (template.baseArmor() > 0.0D) values.merge("armor", template.baseArmor(), Double::sum);
        return evaluate(template, profile, values);
    }

    private static Budget evaluate(final ItemTemplate template, final ArmorFamilyProfile profile,
                                   final Map<String, Double> values) {
        double offensive = 0.0D;
        double defensive = 0.0D;
        double utility = 0.0D;
        double health = 0.0D;
        double armor = 0.0D;
        for (final Map.Entry<String, Double> entry : values.entrySet()) {
            final ItemStatCatalog.Definition definition = ItemStatCatalog.require(entry.getKey());
            final double amount = Double.isFinite(entry.getValue()) ? Math.max(0.0D, entry.getValue()) : 0.0D;
            final double weighted = amount * STAT_WEIGHTS.getOrDefault(definition.id(), 1.0D);
            switch (definition.group()) {
                case OFFENSIVE -> offensive += weighted;
                case DEFENSIVE -> defensive += weighted;
                case UTILITY, CLASS_SPECIFIC -> utility += weighted;
            }
            if ("max_health".equals(definition.id())) health += amount;
            if ("armor".equals(definition.id())) armor += amount;
        }
        // The stat weights already express one common combat-impact currency. Family shares are
        // presentation/build-identity guidance, not divisors: dividing by them made two equally
        // useful items incomparable solely because they belonged to different armor families.
        final double normalized = offensive + defensive + utility;
        final double slotShare = switch (template.slot()) {
            case CHEST -> 0.34D;
            case LEGS -> 0.28D;
            case HEAD -> 0.19D;
            case FEET -> 0.19D;
            default -> 0.0D;
        };
        final String band = template.encounterMetadata().getOrDefault("progression-band", "");
        final double setBudget = switch (band) {
            case "early" -> 32.0D;
            case "mid" -> 60.0D;
            case "high" -> 76.0D;
            case "endgame" -> 92.0D;
            default -> 20.0D + Math.min(50, template.itemLevel());
        };
        final double expected = slotShare * setBudget;
        final double toughness = Math.max(0.0D, values.getOrDefault("armor_toughness", 0.0D));
        final double representativeHit = 10.0D;
        final double armorPoints = Math.min(20.0D, Math.max(armor / 5.0D,
                armor - 4.0D * representativeHit / (toughness + 8.0D)));
        final double reduction = Math.max(0.0D, armorPoints / 25.0D);
        final double physicalEhp = (20.0D + health) / Math.max(0.20D, 1.0D - reduction);
        return new Budget(offensive, defensive, utility, normalized, expected, physicalEhp);
    }
}
