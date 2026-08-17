package hu.taliann.icesmp.itemization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Authored-family drift detector; structural failures stay in {@link ItemTemplate}. */
public final class EquipmentCatalogValidator {

    public enum Severity { WARNING, ERROR }

    public record Finding(Severity severity, String templateId, String code, String detail) { }

    private EquipmentCatalogValidator() {
    }

    public static List<Finding> validate(final ItemTemplate template,
                                         final ArmorFamilyProfile profile) {
        if (template == null || !template.isArmorFamilyEquipment()) return List.of();
        final ArrayList<Finding> findings = new ArrayList<>();
        final Set<String> stats = allStats(template);
        final boolean defensive = template.baseArmor() > 0.0D
                || stats.contains("armor") || stats.contains("armor_toughness")
                || stats.contains("max_health");
        final boolean offensive = stats.contains("attack_damage")
                || stats.contains("attack_speed") || stats.contains("ability_power");
        final boolean utility = stats.contains("movement_speed");
        final boolean exception = !template.familyExceptionReason().isBlank();

        if (template.armorFamily() == ArmorFamily.PLATE && !defensive && !exception) {
            findings.add(warning(template, "PLATE_WITHOUT_DEFENSE",
                    "Plate itemnek nincs armor/HP/mitigation identitása és nincs exception."));
        }
        if (template.armorFamily() == ArmorFamily.CLOTH
                && template.baseArmor() > profile.baseArmorCoefficient() * 3.0D && !exception) {
            findings.add(warning(template, "CLOTH_ARMOR_OUTLIER",
                    "Cloth base armor meghaladja a family profil auditküszöbét."));
        }
        if (template.armorFamily() == ArmorFamily.LEATHER && !utility && !offensive && !exception) {
            findings.add(warning(template, "LEATHER_IDENTITY_MISSING",
                    "Leather itemen nincs mobilitási vagy támadó identitás."));
        }
        if (template.armorFamily() == ArmorFamily.MAIL
                && !(defensive || offensive || utility) && !exception) {
            findings.add(warning(template, "MAIL_HYBRID_WEAK",
                    "Mail item nem hordoz védelmi, támadó vagy utility jelet."));
        }
        for (final String stat : profile.disfavoredStats()) {
            if (stats.contains(stat) && !exception) {
                findings.add(warning(template, "DISFAVORED_STAT",
                        stat + " family-idegen stat explicit exception nélkül."));
            }
        }
        final EquipmentBudgetModel.Budget budget = EquipmentBudgetModel.midpoint(template, profile);
        final double ratio = budget.expectedTierBudget() <= 0.0D ? 0.0D
                : budget.normalizedTotal() / budget.expectedTierBudget();
        if ((ratio < 0.20D || ratio > 8.0D) && !exception) {
            findings.add(warning(template, "BUDGET_OUTLIER",
                    "Normalized/expected budget ratio: " + String.format(java.util.Locale.ROOT,
                            "%.3f", ratio)));
        }
        return List.copyOf(findings);
    }

    private static Set<String> allStats(final ItemTemplate template) {
        final LinkedHashSet<String> result = new LinkedHashSet<>(template.fixedStats().keySet());
        result.addAll(template.rolledStats().keySet());
        return Set.copyOf(result);
    }

    private static Finding warning(final ItemTemplate template, final String code,
                                   final String detail) {
        return new Finding(Severity.WARNING, template.templateId(), code, detail);
    }
}
