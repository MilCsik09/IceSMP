package hu.taliann.icesmp.itemization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Pure candidate builder. Payment and publication remain outside this immutable domain boundary. */
public final class ItemMutationService {

    public enum Status { APPLIED, ALREADY_APPLIED, INVALID_LOCK, NOT_ASCENDABLE, TERMINAL_STAGE }

    public record RerollRequest(UUID operationId, String lockedStatId,
                                double minimumQuality, boolean stabilitySeal, long occurredAt) {
        public RerollRequest {
            Objects.requireNonNull(operationId, "operationId");
            lockedStatId = lockedStatId == null || lockedStatId.isBlank()
                    ? "" : ItemStatCatalog.normalizeId(lockedStatId);
            if (!Double.isFinite(minimumQuality) || minimumQuality < 0.0D || minimumQuality > 1.0D) {
                throw new IllegalArgumentException("minimum reroll quality must be between zero and one");
            }
            if (occurredAt < 0L) throw new IllegalArgumentException("negative reroll timestamp");
        }
    }

    public record AscensionRequest(UUID operationId, long occurredAt) {
        public AscensionRequest {
            Objects.requireNonNull(operationId, "operationId");
            if (occurredAt < 0L) throw new IllegalArgumentException("negative ascension timestamp");
        }
    }

    public record Result(Status status, ItemInstance before, ItemInstance candidate,
                         String detail) {
        public boolean applied() { return status == Status.APPLIED; }
    }

    public Result reroll(final ItemTemplate template, final ItemInstance before,
                         final RerollRequest request, final DoubleSupplier qualitySource) {
        requireMatchingIdentity(template, before);
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(qualitySource, "qualitySource");
        if (before.mutation().hasReceipt(request.operationId())) {
            return new Result(Status.ALREADY_APPLIED, before, before, request.operationId().toString());
        }
        final Map<String, ItemTemplate.StatRange> ranges =
                template.rolledStatsAt(before.ascension().stageId());
        if (!request.lockedStatId().isBlank() && !ranges.containsKey(request.lockedStatId())) {
            return new Result(Status.INVALID_LOCK, before, before, request.lockedStatId());
        }
        final LinkedHashMap<String, ItemInstance.Roll> rolls = new LinkedHashMap<>();
        ranges.forEach((stat, range) -> {
            if (stat.equals(request.lockedStatId())) {
                rolls.put(stat, before.rolls().get(stat));
                return;
            }
            final double raw = qualitySource.getAsDouble();
            if (!Double.isFinite(raw)) throw new IllegalArgumentException("non-finite reroll quality");
            final double bounded = Math.max(0.0D, Math.min(1.0D, raw));
            final double quality = request.minimumQuality()
                    + ((1.0D - request.minimumQuality()) * bounded);
            rolls.put(stat, new ItemInstance.Roll(range.valueAt(quality), quality));
        });
        final ItemInstance candidate = before.withMutation(rolls, before.runes(), before.ascension(),
                before.itemLevel(), before.mutation().afterReroll(
                        request.operationId(), request.stabilitySeal()),
                new ItemHistoryEvent(ItemHistoryEvent.Type.REROLLED, request.occurredAt(),
                        request.lockedStatId().isBlank() ? "full" : "lock:" + request.lockedStatId()));
        return new Result(Status.APPLIED, before, candidate, request.operationId().toString());
    }

    public Result ascend(final ItemTemplate template, final ItemInstance before,
                         final AscensionRequest request) {
        requireMatchingIdentity(template, before);
        Objects.requireNonNull(request, "request");
        if (before.mutation().hasReceipt(request.operationId())) {
            return new Result(Status.ALREADY_APPLIED, before, before, request.operationId().toString());
        }
        if (template.ascensionPath().isEmpty()) {
            return new Result(Status.NOT_ASCENDABLE, before, before, template.templateId());
        }
        final int nextIndex = before.ascension().stageIndex();
        if (nextIndex >= template.ascensionPath().size()) {
            return new Result(Status.TERMINAL_STAGE, before, before, before.ascension().stageId());
        }
        final String nextStageId = template.ascensionPath().get(nextIndex);
        final ItemTemplate.AscensionStage stage = template.stage(nextStageId);
        if (stage == null) {
            throw new IllegalArgumentException("missing authored ascension stage: " + nextStageId);
        }
        final LinkedHashMap<String, ItemInstance.Roll> rolls = new LinkedHashMap<>();
        stage.rolledStats().forEach((stat, range) -> {
            final ItemInstance.Roll old = before.rolls().get(stat);
            if (old == null) throw new IllegalArgumentException("ascension roll mismatch: " + stat);
            rolls.put(stat, new ItemInstance.Roll(range.valueAt(old.quality()), old.quality()));
        });
        ItemInstance candidate = before.withMutation(rolls, before.runes(),
                new ItemInstance.AscensionState(nextStageId, nextIndex + 1), stage.itemLevel(),
                before.mutation().afterMutation(request.operationId()),
                new ItemHistoryEvent(ItemHistoryEvent.Type.ASCENDED, request.occurredAt(), nextStageId));
        if (template.signatureTierAt(nextStageId)
                > template.signatureTierAt(before.ascension().stageId())) {
            candidate = candidate.appendHistory(new ItemHistoryEvent(
                    ItemHistoryEvent.Type.SIGNATURE_UPGRADED, request.occurredAt(),
                    "tier:" + template.signatureTierAt(nextStageId)));
        }
        return new Result(Status.APPLIED, before, candidate, request.operationId().toString());
    }

    public ItemInstance changeRunes(final ItemTemplate template, final ItemInstance before,
                                    final UUID operationId, final List<String> nextRunes,
                                    final long occurredAt) {
        requireMatchingIdentity(template, before);
        Objects.requireNonNull(operationId, "operationId");
        if (before.mutation().hasReceipt(operationId)) return before;
        final ArrayList<String> normalized = new ArrayList<>();
        for (final String rune : nextRunes) {
            final String id = ItemStatCatalog.normalizeId(rune);
            if (normalized.contains(id)) throw new IllegalArgumentException("duplicate rune");
            normalized.add(id);
        }
        if (normalized.size() > template.runeSocketCountAt(before.ascension().stageId())) {
            throw new IllegalArgumentException("rune capacity exceeded");
        }
        return before.withMutation(before.rolls(), normalized, before.ascension(), before.itemLevel(),
                before.mutation().afterMutation(operationId),
                new ItemHistoryEvent(ItemHistoryEvent.Type.RUNE_CHANGED, occurredAt,
                        String.join(",", normalized)));
    }

    /** Supports decimal, integer-rounded and negative ranges; clamp is always final. */
    public static double preserveRelativeQuality(final ItemTemplate.StatRange oldRange,
                                                 final double oldValue,
                                                 final ItemTemplate.StatRange newRange,
                                                 final boolean wholeNumber) {
        Objects.requireNonNull(oldRange, "oldRange");
        Objects.requireNonNull(newRange, "newRange");
        if (!Double.isFinite(oldValue)) throw new IllegalArgumentException("non-finite old roll");
        final double width = oldRange.max() - oldRange.min();
        final double quality = width == 0.0D ? 1.0D
                : Math.max(0.0D, Math.min(1.0D, (oldValue - oldRange.min()) / width));
        double value = newRange.valueAt(quality);
        if (wholeNumber) value = Math.rint(value);
        return Math.max(newRange.min(), Math.min(newRange.max(), value));
    }

    public static long boundedGeometricCost(final long base, final double growth,
                                            final int step, final long maximum) {
        if (base < 0L || maximum < base || !Double.isFinite(growth) || growth < 1.0D || step < 0) {
            throw new IllegalArgumentException("invalid reroll cost curve");
        }
        if (base == 0L) return 0L;
        final double value = base * Math.pow(growth, Math.min(step, 1_000_000));
        if (!Double.isFinite(value) || value >= maximum) return maximum;
        return Math.min(maximum, Math.max(base, Math.round(value)));
    }

    private static void requireMatchingIdentity(final ItemTemplate template,
                                                final ItemInstance instance) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(instance, "instance");
        if (!template.templateId().equals(instance.templateId())
                || template.templateVersion() != instance.templateVersion()) {
            throw new IllegalArgumentException("item instance does not match template");
        }
        if (!template.matchesAscensionState(instance.ascension().stageId(),
                instance.ascension().stageIndex())) {
            throw new IllegalArgumentException("item ascension state does not match template path");
        }
        final Map<String, ItemTemplate.StatRange> ranges =
                template.rolledStatsAt(instance.ascension().stageId());
        if (!ranges.keySet().equals(instance.rolls().keySet())) {
            throw new IllegalArgumentException("item rolls do not match current authored stage");
        }
        ranges.forEach((stat, range) -> {
            final ItemInstance.Roll roll = instance.rolls().get(stat);
            if (!range.matches(roll.value(), roll.quality())) {
                throw new IllegalArgumentException("item roll is outside current authored stage: " + stat);
            }
        });
    }
}
