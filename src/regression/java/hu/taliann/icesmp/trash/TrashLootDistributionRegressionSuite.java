package hu.taliann.icesmp.trash;

import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Deterministic 30M source-event Monte Carlo gate for category, context and recycle invariants. */
public final class TrashLootDistributionRegressionSuite {

    private static final int TRIALS_PER_SOURCE = 10_000_000;
    private static final Path CATALOG = Path.of("src/main/resources/content/trash/catalog.yml");
    private static final List<Set<TrashContext>> CONTEXTS = List.of(
            Set.of(), Set.of(TrashContext.WET), Set.of(TrashContext.DEEP),
            Set.of(TrashContext.COLD, TrashContext.OPEN_SKY),
            Set.of(TrashContext.HOT, TrashContext.NETHER),
            Set.of(TrashContext.UNDEAD, TrashContext.DARK),
            Set.of(TrashContext.HUMANOID, TrashContext.UNDERGROUND));

    private TrashLootDistributionRegressionSuite() { }

    public static void main(final String[] args) {
        final TrashCatalog.Parsed parsed = TrashCatalog.parse(
                YamlConfiguration.loadConfiguration(CATALOG.toFile()));
        final TrashLootSelector selector = new TrashLootSelector(parsed.definitions(), parsed.lootTuning());
        for (final TrashLootSource source : TrashLootSource.values()) {
            simulate(source, parsed.lootTuning(), selector);
        }
        verifyContextBias(selector);
        final TrashLootTuning.Ambient ambient = parsed.lootTuning().ambient();
        final double meanSeconds = (ambient.attemptMinSeconds() + ambient.attemptMaxSeconds()) / 2.0D;
        final double perPlayerDay = 86_400.0D / meanSeconds
                * parsed.lootTuning().chance(TrashLootSource.AMBIENT);
        System.out.printf(java.util.Locale.ROOT,
                "TRASH_AMBIENT_EXPECTED raw_per_active_player_day=%.3f raw_20_player_day=%.3f caps=runtime%n",
                perPlayerDay, perPlayerDay * 20.0D);
        System.out.println("Trash loot distribution regression suite passed (30,000,000 source events).");
    }

    private static void verifyContextBias(final TrashLootSelector selector) {
        final int trials = 1_000_000;
        final Random neutralRandom = new Random(0xC017E57L);
        final Random wetRandom = new Random(0xC017E57L);
        long neutralWetAffinity = 0L;
        long matchedWetAffinity = 0L;
        long comparable = 0L;
        for (int trial = 0; trial < trials; trial++) {
            final TrashLootSelector.Selection neutral = selector.select(
                    TrashLootSource.AMBIENT, Set.of(), neutralRandom::nextDouble);
            final TrashLootSelector.Selection wet = selector.select(
                    TrashLootSource.AMBIENT, Set.of(TrashContext.WET), wetRandom::nextDouble);
            check(neutral.kind() == wet.kind() && neutral.displaced() == wet.displaced(),
                    "context must not alter category/displaced rolls");
            if (neutral.displaced()) continue;
            comparable++;
            if (neutral.definition().sourceBias().affinities().contains("WET")) neutralWetAffinity++;
            if (wet.definition().sourceBias().affinities().contains("WET")) matchedWetAffinity++;
        }
        final double neutralRate = rate(neutralWetAffinity, comparable);
        final double matchedRate = rate(matchedWetAffinity, comparable);
        check(matchedRate > neutralRate * 1.10D,
                "matching WET context must materially bias identity selection: neutral="
                        + neutralRate + " matched=" + matchedRate);
        System.out.printf(java.util.Locale.ROOT,
                "TRASH_CONTEXT_PROBE trials=%d neutral_wet=%.6f matched_wet=%.6f category_unchanged=true%n",
                trials, neutralRate, matchedRate);
    }

    private static void simulate(final TrashLootSource source, final TrashLootTuning tuning,
                                 final TrashLootSelector selector) {
        final Random random = new Random(0x1CE5A5EEDL + source.ordinal() * 7_919L);
        final EnumMap<TrashKind, Long> categories = new EnumMap<>(TrashKind.class);
        long trash = 0L;
        long displaced = 0L;
        long recycled = 0L;
        for (int trial = 0; trial < TRIALS_PER_SOURCE; trial++) {
            if (random.nextDouble() >= tuning.chance(source)) continue;
            final Set<TrashContext> contexts = CONTEXTS.get(trial % CONTEXTS.size());
            final TrashLootSelector.Selection selection = selector.select(source, contexts, random::nextDouble);
            trash++;
            categories.merge(selection.kind(), 1L, Long::sum);
            if (selection.displaced()) displaced++;
            if (random.nextDouble() < tuning.recycleSubstitutionChance()) recycled++;
        }

        assertAbsolute(rate(trash, TRIALS_PER_SOURCE), tuning.chance(source), 0.0006D,
                source + " source chance");
        for (final TrashKind kind : TrashKind.values()) {
            final double expected = tuning.categoryWeight(kind) / 100.0D;
            final double tolerance = kind == TrashKind.TRASH_RELIC ? 0.00035D
                    : kind == TrashKind.ANOMALY ? 0.0010D : 0.0020D;
            assertAbsolute(rate(categories.getOrDefault(kind, 0L), trash), expected, tolerance,
                    source + " category " + kind);
        }
        assertAbsolute(rate(displaced, trash), tuning.displacedChance(), 0.0020D,
                source + " displaced");
        assertAbsolute(rate(recycled, trash), tuning.recycleSubstitutionChance(), 0.0020D,
                source + " recycle substitution");

        final double anomalyPerEvent = tuning.chance(source)
                * tuning.categoryWeight(TrashKind.ANOMALY) / 100.0D;
        final double relicPerEvent = tuning.chance(source)
                * tuning.categoryWeight(TrashKind.TRASH_RELIC) / 100.0D;
        assertRelative(rate(categories.getOrDefault(TrashKind.ANOMALY, 0L), TRIALS_PER_SOURCE),
                anomalyPerEvent, 0.08D, source + " anomaly/event");
        assertRelative(rate(categories.getOrDefault(TrashKind.TRASH_RELIC, 0L), TRIALS_PER_SOURCE),
                relicPerEvent, 0.18D, source + " relic/event");
        System.out.printf(java.util.Locale.ROOT,
                "TRASH_DISTRIBUTION source=%s events=%d trash=%d trash/event=%.8f anomaly/event=%.8f relic/event=%.8f displaced=%.5f recycled=%.5f%n",
                source, TRIALS_PER_SOURCE, trash, rate(trash, TRIALS_PER_SOURCE),
                rate(categories.getOrDefault(TrashKind.ANOMALY, 0L), TRIALS_PER_SOURCE),
                rate(categories.getOrDefault(TrashKind.TRASH_RELIC, 0L), TRIALS_PER_SOURCE),
                rate(displaced, trash), rate(recycled, trash));
    }

    private static double rate(final long numerator, final long denominator) {
        return denominator == 0L ? 0.0D : (double) numerator / denominator;
    }

    private static void assertAbsolute(final double actual, final double expected,
                                       final double tolerance, final String label) {
        check(Math.abs(actual - expected) <= tolerance,
                label + " drifted: expected=" + expected + " actual=" + actual);
    }

    private static void assertRelative(final double actual, final double expected,
                                       final double relativeTolerance, final String label) {
        check(expected > 0.0D && Math.abs(actual - expected) / expected <= relativeTolerance,
                label + " drifted: expected=" + expected + " actual=" + actual);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
