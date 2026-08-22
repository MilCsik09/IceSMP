package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.itemization.ItemRarity;
import hu.taliann.icesmp.itemization.ItemTemplate;

import java.util.List;
import java.util.Map;

/** Bounded equipped-item projection used only for encounter telemetry and start snapshots. */
public final class EquippedCombatPowerModel {
    public record GearSignal(ItemTemplate.Slot slot, int itemLevel, ItemRarity rarity,
                             double baseDamage, double baseArmor,
                             Map<String, Double> fixedStats, Map<String, Double> rolledStats,
                             int runeCount, int signatureTier, String setId,
                             double armorCoefficient, double normalizedBudget) {
        public GearSignal {
            if (slot == null || rarity == null || itemLevel < 1 || itemLevel > 1_000
                    || !Double.isFinite(baseDamage) || baseDamage < 0.0D
                    || !Double.isFinite(baseArmor) || baseArmor < 0.0D
                    || !Double.isFinite(armorCoefficient) || armorCoefficient <= 0.0D
                    || !Double.isFinite(normalizedBudget) || normalizedBudget < 0.0D) {
                throw new IllegalArgumentException("invalid equipped gear signal");
            }
            fixedStats = Map.copyOf(fixedStats == null ? Map.of() : fixedStats);
            rolledStats = Map.copyOf(rolledStats == null ? Map.of() : rolledStats);
            runeCount = Math.max(0, Math.min(2, runeCount));
            signatureTier = Math.max(0, Math.min(16, signatureTier));
            setId = setId == null ? "" : setId;
            normalizedBudget = Math.min(4.0D, normalizedBudget);
        }

        public GearSignal(final ItemTemplate.Slot slot, final int itemLevel,
                          final ItemRarity rarity, final double baseDamage,
                          final double baseArmor, final Map<String, Double> fixedStats,
                          final Map<String, Double> rolledStats, final int runeCount,
                          final int signatureTier, final String setId) {
            this(slot, itemLevel, rarity, baseDamage, baseArmor, fixedStats, rolledStats,
                    runeCount, signatureTier, setId, 1.0D, 0.0D);
        }
    }

    private EquippedCombatPowerModel() { }

    public static double estimate(final int classLevel, final List<GearSignal> equipped) {
        double health = 20.0D;
        double damage = 1.0D;
        double armor = 0.0D;
        double abilityPower = 0.0D;
        double context = 0.0D;
        int runes = 0;
        int setTiers = 0;
        final java.util.HashMap<String, Integer> setPieces = new java.util.HashMap<>();
        for (final GearSignal gear : List.copyOf(equipped == null ? List.of() : equipped)) {
            damage += bounded(gear.baseDamage(), 0.0D, 200.0D);
            armor += bounded(gear.baseArmor(), 0.0D, 100.0D) / gear.armorCoefficient();
            health += stat(gear, "max_health");
            damage += stat(gear, "attack_damage");
            armor += stat(gear, "armor") / gear.armorCoefficient();
            abilityPower += stat(gear, "ability_power");
            runes += gear.runeCount();
            context += Math.min(0.24D, gear.signatureTier() * 0.06D);
            context += Math.min(0.08D, gear.rarity().ordinal() * 0.012D);
            context += Math.min(0.08D, gear.itemLevel() / 1_000.0D);
            context += Math.min(0.12D, gear.normalizedBudget() * 0.04D);
            if (!gear.setId().isBlank()) setPieces.merge(gear.setId(), 1, Integer::sum);
        }
        for (final int pieces : setPieces.values()) setTiers += Math.min(4, pieces / 2);
        damage += Math.max(0.0D, abilityPower) * 0.08D;
        final double mitigation = armor <= 0.0D ? 0.0D : armor / (armor + 100.0D);
        return CombatPowerEstimator.estimate(new CombatPowerEstimator.Input(
                Math.max(1, Math.min(70, classLevel)),
                bounded(health, 1.0D, 10_000.0D), bounded(damage, 0.0D, 2_000.0D),
                bounded(armor, 0.0D, 100.0D),
                bounded(abilityPower * 0.04D, 0.0D, 1_000.0D), mitigation,
                bounded(context, 0.0D, 1.0D), Math.min(8, setTiers), Math.min(2, runes)));
    }

    private static double stat(final GearSignal gear, final String id) {
        return bounded(gear.fixedStats().getOrDefault(id, 0.0D), -10_000.0D, 10_000.0D)
                + bounded(gear.rolledStats().getOrDefault(id, 0.0D), -10_000.0D, 10_000.0D);
    }

    private static double bounded(final double value, final double minimum, final double maximum) {
        if (!Double.isFinite(value)) return 0.0D;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
