package hu.taliann.icesmp.monk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Dependency-free behavior regression for the concrete Szerzetes runtime state. */
public final class MonkGameplayRegressionSuite {

    private static int assertions;

    private MonkGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        flowRewardsVarietyNotRepetition();
        martialChainOrderWindowAndRetention();
        staggerPoolBoundsDrainAndConsequence();
        mistLinksCapacityAndReplacement();
        cleanupLifecycle();
        staggerAndAllowlistSourceContracts();
        System.out.println("Monk gameplay regression suite passed. assertions=" + assertions);
    }

    private static void flowRewardsVarietyNotRepetition() {
        final MonkCombatState state = new MonkCombatState();
        final long t0 = 10_000L;
        check(state.recordTechnique("tiger_palm", t0, 12, 6_000L, 6.0D) == 12,
                "a fresh technique builds flow");
        check(state.recordTechnique("tiger_palm", t0 + 500L, 12, 6_000L, 6.0D) == 0,
                "repeating a recent technique earns nothing");
        check(state.recordTechnique("roll", t0 + 1_000L, 12, 6_000L, 6.0D) == 12,
                "a different technique builds flow again");
        state.recordTechnique("chi_wave", t0 + 1_500L, 12, 6_000L, 6.0D);
        check(state.recordTechnique("tiger_palm", t0 + 2_000L, 12, 6_000L, 6.0D) == 0,
                "a technique still in the recent window earns nothing — repeats refresh recency");
        state.recordTechnique("vivify", t0 + 2_500L, 12, 6_000L, 6.0D);
        state.recordTechnique("expel_harm", t0 + 3_000L, 12, 6_000L, 6.0D);
        state.recordTechnique("leg_sweep", t0 + 3_500L, 12, 6_000L, 6.0D);
        check(state.recordTechnique("tiger_palm", t0 + 4_000L, 12, 6_000L, 6.0D) == 12,
                "a technique fully rotated out of the recent window is fresh again");
        check(state.flow(t0 + 4_000L, 6_000L, 6.0D) == 84, "flow accumulates exactly");
        check(state.flow(t0 + 15_000L, 6_000L, 6.0D) < 84,
                "idle flow decays lazily after the grace window");
    }

    private static void martialChainOrderWindowAndRetention() {
        final MonkCombatState state = new MonkCombatState();
        final List<String> steps = List.of("tiger_palm", "blackout_kick", "rising_sun_kick");
        final long t0 = 50_000L;
        check(state.recordChainStep("tiger_palm", steps, t0, 5_000L) == 1,
                "the opening technique starts the chain");
        check(state.recordChainStep("rising_sun_kick", steps, t0 + 1_000L, 5_000L) == 0,
                "a wrong-order technique breaks the chain");
        state.recordChainStep("tiger_palm", steps, t0 + 2_000L, 5_000L);
        check(state.recordChainStep("blackout_kick", steps, t0 + 3_000L, 5_000L) == 2,
                "the expected step advances the chain");
        check(state.recordChainStep("tiger_palm", steps, t0 + 4_000L, 5_000L) == 1,
                "the opening technique restarts rather than advances");
        state.recordChainStep("blackout_kick", steps, t0 + 5_000L, 5_000L);
        check(state.recordChainStep("rising_sun_kick", steps, t0 + 12_000L, 5_000L) == 0,
                "a stale window resets the chain");

        state.recordChainStep("tiger_palm", steps, t0 + 20_000L, 5_000L);
        state.recordChainStep("blackout_kick", steps, t0 + 21_000L, 5_000L);
        state.recordChainStep("rising_sun_kick", steps, t0 + 22_000L, 5_000L);
        check(!state.consumeChain(4, 0), "below-threshold chain cannot finish");
        check(state.consumeChain(3, 0), "the full chain opens the finisher");
        check(state.chainStep(t0 + 22_100L, 5_000L) == 0, "the finisher vents the chain");

        state.recordChainStep("tiger_palm", steps, t0 + 30_000L, 5_000L);
        state.recordChainStep("blackout_kick", steps, t0 + 31_000L, 5_000L);
        state.recordChainStep("rising_sun_kick", steps, t0 + 32_000L, 5_000L);
        check(state.consumeChain(3, 1) && state.chainStep(t0 + 32_100L, 5_000L) == 1,
                "the level-50 doctrine retains one step");
    }

    private static void staggerPoolBoundsDrainAndConsequence() {
        final MonkCombatState state = new MonkCombatState();
        check(state.stagger(4.0D, 12.0D) == 4.0D, "part of a hit defers into the pool");
        check(state.stagger(6.0D, 12.0D) == 6.0D, "the pool accumulates");
        check(state.stagger(5.0D, 12.0D) == 2.0D, "the pool is bounded by its cap");
        check(state.staggerPool() == 12.0D, "the cap holds exactly");

        check(state.drainStagger(0.5D) == 0.5D, "the drain steps the pool down");
        check(state.purifyStagger(50.0D) == 5.75D, "the purify clears a fraction");
        check(Math.abs(state.staggerPool() - 5.75D) < 1.0E-9, "the purify math is exact");

        check(state.collapseStagger() == 5.75D,
                "logout/spec-switch takes the whole remaining pool at once");
        check(state.staggerPool() == 0.0D, "the collapse leaves nothing behind");
        check(state.drainStagger(1.0D) == 0.0D, "an empty pool drains nothing");
    }

    private static void mistLinksCapacityAndReplacement() {
        final MonkCombatState state = new MonkCombatState();
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();
        final UUID c = UUID.randomUUID();
        final UUID d = UUID.randomUUID();
        check(state.addLink(a, "A", 3), "the first Ködszál links");
        check(!state.addLink(a, "A", 3), "the same ally cannot be double-linked");
        state.addLink(b, "B", 3);
        state.addLink(c, "C", 3);
        check(state.linkIds().size() == 3, "three links is the canonical maximum");
        check(state.addLink(d, "D", 3), "a fourth link replaces the oldest");
        check(!state.linkIds().contains(a) && state.linkIds().contains(d),
                "the oldest link made room for the newest");
        state.removeLink(b);
        check(state.linkIds().size() == 2 && !state.linkIds().contains(b),
                "an invalid ally's link is removable");
    }

    private static void cleanupLifecycle() {
        final MonkCombatState state = new MonkCombatState();
        final long t0 = 100_000L;
        state.recordTechnique("tiger_palm", t0, 12, 6_000L, 6.0D);
        state.recordChainStep("tiger_palm", List.of("tiger_palm", "blackout_kick",
                "rising_sun_kick"), t0, 5_000L);
        state.stagger(6.0D, 12.0D);
        state.addLink(UUID.randomUUID(), "A", 3);
        state.clearSpecializationState();
        check(state.flow(t0, 6_000L, 6.0D) == 0, "spec switch clears the flow");
        check(state.chainStep(t0, 5_000L) == 0, "spec switch clears the chain");
        check(state.staggerPool() == 0.0D, "spec switch clears the pool");
        check(state.linkIds().isEmpty(), "spec switch clears the Ködszálak");

        state.stagger(3.0D, 12.0D);
        state.clearAll();
        check(state.staggerPool() == 0.0D, "death/logout cleanup clears everything");
    }

    private static void staggerAndAllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("Set.of(\"warrior\", \"evoker\", \"archer\", \"shaman\", \"monk\")"),
                "gameplay-v2 allowlist is exactly the completed slices");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/monk/MonkGameplayService.java"));
        check(service.contains("player.setHealth(Math.max(1.0D,")
                        && !service.contains("player.damage("),
                "the Stagger drain steps health directly — never a duplicated damage event");
        check(service.contains("applyStaggerConsequence(event.getPlayer().getUniqueId());")
                        && service.contains("applyStaggerConsequence(playerId);"),
                "logout/kick/spec-switch applies the remaining pool — no consequence-free escape");
        check(service.contains("link.scheduler().run(plugin,"),
                "Ködszál ripple heals always run on the linked ally's scheduler");
        check(!service.contains("getNearbyEntities") && !service.contains("runAtFixedRate"),
                "no proximity scans or repeating tasks in the monk runtime");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
