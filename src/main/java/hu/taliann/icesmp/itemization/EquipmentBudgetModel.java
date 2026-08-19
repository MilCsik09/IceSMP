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
        final double normalized = normalized(offensive, profile.offensiveShare())
                + normalized(defensive, profile.defensiveShare())
                + normalized(utility, profile.utilityShare());
        final double slotShare = switch (template.slot()) {
            case CHEST -> 1.0D;
            case LEGS -> 0.82D;
            case HEAD -> 0.62D;
            case FEET -> 0.56D;
            default -> 0.0D;
        };
        final double rarity = 1.0D + template.rarity().ordinal() * 0.10D;
        final double expected = slotShare * rarity * (2.0D + template.itemLevel() * 0.12D);
        final double normalizedArmor = armor / profile.baseArmorCoefficient();
        final double physicalEhp = (20.0D + health) * (1.0D + normalizedArmor / 100.0D);
        return new Budget(offensive, defensive, utility, normalized, expected, physicalEhp);
    }

    private static double normalized(final double amount, final double share) {
        return share <= 0.0D ? (amount == 0.0D ? 0.0D : amount * 10.0D) : amount / share;
    }
}
