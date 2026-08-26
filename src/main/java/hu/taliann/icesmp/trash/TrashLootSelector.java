package hu.taliann.icesmp.trash;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

/** Category-first weighted selector; context can change identity, never special-category odds. */
public final class TrashLootSelector {

    private final TrashCatalog catalog;
    private final Map<String, TrashDefinition> fixedDefinitions;
    private final TrashLootTuning fixedTuning;
    private final Map<DistributionKey, Distribution> distributions = new ConcurrentHashMap<>();
    private volatile Map<TrashKind, List<TrashDefinition>> byKind;

    public TrashLootSelector(final TrashCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.fixedDefinitions = null;
        this.fixedTuning = null;
    }

    TrashLootSelector(final Map<String, TrashDefinition> definitions, final TrashLootTuning tuning) {
        this.catalog = null;
        this.fixedDefinitions = Map.copyOf(definitions);
        this.fixedTuning = Objects.requireNonNull(tuning, "tuning");
    }

    public Selection select(final TrashLootSource source, final Set<TrashContext> rawContexts,
                            final DoubleSupplier random) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(random, "random");
        final Set<TrashContext> contexts = rawContexts == null ? Set.of() : rawContexts;
        final TrashLootTuning tuning = tuning();
        final TrashKind kind = rollCategory(tuning, next(random));
        final boolean displaced = next(random) < tuning.displacedChance();
        final DistributionKey key = new DistributionKey(kind, source, mask(contexts), displaced);
        final Distribution distribution = distributions.computeIfAbsent(key,
                ignored -> buildDistribution(kind, source, contexts, displaced, tuning));
        return new Selection(distribution.pick(next(random)), kind, displaced);
    }

    private Distribution buildDistribution(final TrashKind kind, final TrashLootSource source,
                                             final Set<TrashContext> contexts,
                                             final boolean displaced,
                                             final TrashLootTuning tuning) {
        final List<TrashDefinition> candidates = definitionsByKind().getOrDefault(kind, List.of());
        if (candidates.isEmpty()) {
            throw new IllegalStateException("nincs Trash identity a kategóriában: " + kind);
        }
        final double[] cumulative = new double[candidates.size()];
        double total = 0.0D;
        for (int index = 0; index < candidates.size(); index++) {
            final double weight = candidates.get(index).sourceBias()
                    .weight(source, contexts, displaced, tuning);
            if (!Double.isFinite(weight) || weight <= 0.0D) {
                throw new IllegalStateException("hibás Trash identity weight: " + candidates.get(index).id());
            }
            total += weight;
            cumulative[index] = total;
        }
        return new Distribution(List.copyOf(candidates), cumulative, total);
    }

    private Map<TrashKind, List<TrashDefinition>> definitionsByKind() {
        Map<TrashKind, List<TrashDefinition>> snapshot = byKind;
        if (snapshot != null) return snapshot;
        synchronized (this) {
            snapshot = byKind;
            if (snapshot != null) return snapshot;
            final EnumMap<TrashKind, List<TrashDefinition>> mutable = new EnumMap<>(TrashKind.class);
            for (final TrashKind kind : TrashKind.values()) mutable.put(kind, new ArrayList<>());
            for (final TrashDefinition definition : definitions().values()) {
                mutable.get(definition.internalKind()).add(definition);
            }
            final EnumMap<TrashKind, List<TrashDefinition>> immutable = new EnumMap<>(TrashKind.class);
            mutable.forEach((kind, values) -> immutable.put(kind, List.copyOf(values)));
            byKind = Map.copyOf(immutable);
            return byKind;
        }
    }

    private Map<String, TrashDefinition> definitions() {
        return catalog == null ? fixedDefinitions : catalog.snapshot();
    }

    private TrashLootTuning tuning() {
        return catalog == null ? fixedTuning : catalog.lootTuning();
    }

    private static TrashKind rollCategory(final TrashLootTuning tuning, final double random) {
        final double target = random * 100.0D;
        double cumulative = 0.0D;
        for (final TrashKind kind : TrashKind.values()) {
            cumulative += tuning.categoryWeight(kind);
            if (target < cumulative) return kind;
        }
        return TrashKind.TRASH_RELIC;
    }

    private static int mask(final Set<TrashContext> contexts) {
        int mask = 0;
        for (final TrashContext context : contexts) mask |= 1 << context.ordinal();
        return mask;
    }

    private static double next(final DoubleSupplier random) {
        final double value = random.getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("a random érték véges kell legyen");
        return Math.max(0.0D, Math.min(Math.nextDown(1.0D), value));
    }

    public record Selection(TrashDefinition definition, TrashKind kind, boolean displaced) {
        public Selection {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(kind, "kind");
            if (definition.internalKind() != kind) {
                throw new IllegalArgumentException("category-first selection invariant sérült");
            }
        }
    }

    private record DistributionKey(TrashKind kind, TrashLootSource source,
                                   int contextMask, boolean displaced) { }

    private record Distribution(List<TrashDefinition> candidates,
                                double[] cumulative, double total) {
        private TrashDefinition pick(final double random) {
            final double target = random * total;
            int low = 0;
            int high = cumulative.length - 1;
            while (low < high) {
                final int middle = (low + high) >>> 1;
                if (target < cumulative[middle]) high = middle; else low = middle + 1;
            }
            return candidates.get(low);
        }
    }
}
