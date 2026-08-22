package hu.taliann.icesmp.itemization;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Pure weighted selector: build relevance is modest and diversity never guarantees a rarity. */
public final class BuildAwareLootService {

    public record Context(int level, String classId, String specializationId,
                          Set<String> buildTags, ItemTemplate.Slot preferredSlot,
                          Set<String> contentSourceTags) {
        public Context {
            if (level < 0 || level > 1000) throw new IllegalArgumentException("invalid player level");
            classId = optionalId(classId);
            specializationId = optionalId(specializationId);
            buildTags = ids(buildTags, 32);
            preferredSlot = preferredSlot == null ? ItemTemplate.Slot.NONE : preferredSlot;
            contentSourceTags = ids(contentSourceTags, 16);
        }
    }

    public record Tuning(double maxPersonalizationMultiplier, int historyWindow,
                         double repeatedTemplatePenalty, double repeatedCategoryPenalty,
                         double unseenCategoryBoost) {
        public Tuning {
            if (!Double.isFinite(maxPersonalizationMultiplier)
                    || maxPersonalizationMultiplier < 1.0D
                    || maxPersonalizationMultiplier > 1.5D) {
                throw new IllegalArgumentException("personalization multiplier must be between 1.0 and 1.5");
            }
            if (historyWindow < 1 || historyWindow > LootDiversityState.MAX_DROPS) {
                throw new IllegalArgumentException("loot history window is outside the durable bound");
            }
            requireRate(repeatedTemplatePenalty, "template penalty", 0.0D, 0.25D);
            requireRate(repeatedCategoryPenalty, "category penalty", 0.0D, 0.10D);
            requireRate(unseenCategoryBoost, "unseen category boost", 0.0D, 0.10D);
        }

        public static Tuning defaults() {
            return new Tuning(1.5D, 24, 0.12D, 0.035D, 0.04D);
        }
    }

    public record Selection(ItemTemplate template, double weight,
                            double personalizationMultiplier, double diversityMultiplier) {
        public Selection {
            Objects.requireNonNull(template, "template");
            if (!Double.isFinite(weight) || weight <= 0.0D) {
                throw new IllegalArgumentException("loot weight must remain positive");
            }
        }
    }

    public Optional<Selection> select(final Collection<ItemTemplate> candidates,
                                      final Context context,
                                      final LootDiversityState diversity,
                                      final Tuning tuning,
                                      final double randomUnit) {
        Objects.requireNonNull(candidates, "candidates");
        if (!Double.isFinite(randomUnit) || randomUnit < 0.0D || randomUnit >= 1.0D) {
            throw new IllegalArgumentException("random unit must be in [0, 1)");
        }
        final List<Selection> weighted = candidates.stream()
                .distinct()
                .sorted(Comparator.comparing(ItemTemplate::templateId))
                .map(template -> weight(template, context, diversity, tuning))
                .toList();
        if (weighted.isEmpty()) return Optional.empty();
        final double total = weighted.stream().mapToDouble(Selection::weight).sum();
        double cursor = randomUnit * total;
        for (final Selection selection : weighted) {
            cursor -= selection.weight();
            if (cursor < 0.0D) return Optional.of(selection);
        }
        return Optional.of(weighted.get(weighted.size() - 1));
    }

    public Selection weight(final ItemTemplate template,
                            final Context context,
                            final LootDiversityState diversity,
                            final Tuning tuning) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(diversity, "diversity");
        Objects.requireNonNull(tuning, "tuning");

        double relevance = 1.0D;
        if (context.level() > 0) {
            final int delta = Math.abs(template.itemLevel() - context.level());
            if (delta <= 5) relevance += 0.10D;
            else if (delta <= 15) relevance += 0.05D;
        }
        if (!context.classId().isBlank()
                && template.classRestrictions().contains(context.classId())) relevance += 0.10D;
        final hu.taliann.icesmp.data.JobType playerClass =
                hu.taliann.icesmp.data.JobType.fromId(context.classId());
        if (template.isArmorFamilyEquipment() && playerClass != null
                && EquipmentProficiencyPolicy.familyOf(playerClass) == template.armorFamily()) {
            // Strongest single build signal, still bounded by the existing global 1.5x cap.
            relevance += 0.18D;
        }
        if (!context.specializationId().isBlank()
                && template.specializationRestrictions().contains(context.specializationId())) relevance += 0.10D;
        if (context.preferredSlot() != ItemTemplate.Slot.NONE
                && template.slot() == context.preferredSlot()) relevance += 0.08D;
        if (!disjoint(context.contentSourceTags(), template.sourceTags())) relevance += 0.05D;
        final Set<String> signals = signals(template);
        relevance += Math.min(0.12D, intersectionSize(context.buildTags(), signals) * 0.04D);
        final double personalization = Math.min(tuning.maxPersonalizationMultiplier(), relevance);

        final List<LootDiversityState.Drop> recent = diversity.tail(tuning.historyWindow());
        int templateRepeats = 0;
        int rarityRepeats = 0;
        int slotRepeats = 0;
        int familyRepeats = 0;
        for (final LootDiversityState.Drop drop : recent) {
            if (drop.templateId().equals(template.templateId())) templateRepeats++;
            if (drop.rarity() == template.rarity()) rarityRepeats++;
            if (drop.slot() == template.slot()) slotRepeats++;
            if (drop.family() == template.family()) familyRepeats++;
        }
        double diversityMultiplier = 1.0D;
        diversityMultiplier /= 1.0D + (templateRepeats * tuning.repeatedTemplatePenalty());
        diversityMultiplier /= 1.0D + ((rarityRepeats + slotRepeats + familyRepeats)
                * tuning.repeatedCategoryPenalty());
        if (!recent.isEmpty()) {
            if (rarityRepeats == 0) diversityMultiplier += tuning.unseenCategoryBoost();
            if (slotRepeats == 0) diversityMultiplier += tuning.unseenCategoryBoost();
            if (familyRepeats == 0) diversityMultiplier += tuning.unseenCategoryBoost();
        }
        diversityMultiplier = Math.max(0.45D, Math.min(1.20D, diversityMultiplier));
        return new Selection(template, personalization * diversityMultiplier,
                personalization, diversityMultiplier);
    }

    private static Set<String> signals(final ItemTemplate template) {
        final LinkedHashSet<String> signals = new LinkedHashSet<>();
        signals.add("family:" + template.family().name().toLowerCase(Locale.ROOT));
        if (template.armorFamily() != null) {
            signals.add("armor-family:" + template.armorFamily().id());
        }
        signals.add("slot:" + template.slot().name().toLowerCase(Locale.ROOT));
        template.fixedStats().keySet().forEach(stat -> signals.add("stat:" + stat));
        template.rolledStats().keySet().forEach(stat -> signals.add("stat:" + stat));
        signals.addAll(template.sourceTags());
        signals.addAll(template.regionTags());
        return Set.copyOf(signals);
    }

    private static int intersectionSize(final Set<String> left, final Set<String> right) {
        int matches = 0;
        for (final String value : left) if (right.contains(value)) matches++;
        return matches;
    }

    private static boolean disjoint(final Set<String> left, final Set<String> right) {
        return intersectionSize(left, right) == 0;
    }

    private static Set<String> ids(final Set<String> source, final int max) {
        if (source == null || source.isEmpty()) return Set.of();
        if (source.size() > max) throw new IllegalArgumentException("loot context id set exceeds limit");
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (final String value : source) result.add(ItemStatCatalog.normalizeId(value));
        return Set.copyOf(result);
    }

    private static String optionalId(final String raw) {
        return raw == null || raw.isBlank() ? "" : ItemStatCatalog.normalizeId(raw);
    }

    private static void requireRate(final double value, final String name,
                                    final double minimum, final double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
    }
}
