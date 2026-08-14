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
        staggerConservesActualDamage();
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
        check(policy.contains("\"monk\""),
                "the gameplay-v2 allowlist still admits this completed slice");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/monk/MonkGameplayService.java"))
                .replace("\r\n", "\n");
        check(service.contains("final double finalBefore = event.getFinalDamage();")
                        && service.contains("MonkCombatState.acceptedDefer(finalBefore,"),
                "phase one takes the deferred share from the FINAL, already mitigated damage");
        check(service.contains("bankedFromReducedFinal(\n                event.getFinalDamage(), fraction)")
                        || service.contains("bankedFromReducedFinal(event.getFinalDamage(), fraction)"),
                "phase two recovers the amount from the authoritative settled final damage");
        check(service.contains("event.setDamage(Math.max(0.0D, event.getDamage() * (1.0D - fraction)))"),
                "the event is scaled multiplicatively, so no mitigation is applied twice or skipped");
        check(service.contains("MonkCombatState.bankedFromReducedFinal("),
                "phase two banks the exact deferred amount recovered from the settled pipeline");
        check(service.contains("EventPriority.MONITOR")
                        && service.contains("onIncomingDamageResolved"),
                "phase two observes at MONITOR — it never modifies the event or overrides a later plugin");
        check(!service.contains("event.getDamage() * staggerPercent"),
                "the raw-damage share that caused the over-charge is gone");

        check(service.contains("player.setHealth(Math.max(1.0D,")
                        && !service.contains("player.damage("),
                "the Stagger drain steps health directly — never a duplicated damage event");
        check(service.contains("applyStaggerConsequence(event.getPlayer());")
                        && service.contains("applyStaggerConsequence(player);"),
                "logout/kick/spec-switch applies the remaining pool — no consequence-free escape");
        check(service.contains("link.scheduler().run(plugin,"),
                "Ködszál ripple heals always run on the linked ally's scheduler");
        check(service.contains("TargetRegistry") && service.contains("mistLinks")
                        && service.contains("clearLinkTarget(playerId)"),
                "linked ally departure clears every monk-side link");
        check(service.contains("record LinkTarget(UUID id, EntityScheduler scheduler")
                        && !service.contains("record LinkTarget(UUID id, Player"),
                "link handles do not retain strong Player references");
        check(!service.contains("getNearbyEntities") && !service.contains("runAtFixedRate"),
                "no proximity scans or repeating tasks in the monk runtime");

        final String adapter = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/integration/BukkitClassSpecRuntimeAdapter.java"));
        check(adapter.contains("clearUuidOnly(id, kind, false)")
                        && adapter.contains("monk.clearSpecializationStateOffline(playerId)")
                        && adapter.contains("sessions.runIfCurrent(id, token")
                        && adapter.contains("selectWhile(id, \"\", () -> current(id, token))")
                        && adapter.contains("if (Bukkit.getPlayer(id) != null)"),
                "offline reconciliation is generation-fenced and cannot touch player health");
    }

    /**
     * The Stagger must be damage-CONSERVING against the damage actually suffered: the monk should
     * lose the same total health as without it, only spread over time. The old implementation took
     * a share of the RAW damage and paid it back as direct health loss, which cost strictly more
     * health whenever the hit was mitigated. These cases pin the arithmetic.
     */
    private static void staggerConservesActualDamage() {
        // The event pipeline is multiplicative: scaling the base by (1 - q) scales the final by
        // (1 - q) too. These helpers model exactly that, so the numbers below are the real ones.
        final double deferPercent = 35.0D;
        final double room = 1000.0D;

        // 1. no mitigation at all: raw 20 -> final 20
        conservation(20.0D, 20.0D, deferPercent, room, "mitigation nélkül");

        // 2. the review's own example: raw 20, final 8 (60% effective mitigation)
        final double accepted = MonkCombatState.acceptedDefer(8.0D, deferPercent, room);
        check(Math.abs(accepted - 2.8D) < 1e-9,
                "the deferred amount is a share of the FINAL damage (8 * 35% = 2.8), not of the raw 20");
        conservation(20.0D, 8.0D, deferPercent, room, "50%+ mitigation mellett");
        // The regressed model, spelled out: a raw-damage share (7.0) subtracted from the base and
        // then repaid as direct health loss cost 12.2 HP for a hit that should have cost 8.
        final double rawShare = 20.0D * deferPercent / 100.0D;
        final double regressedImmediate = 8.0D * ((20.0D - rawShare) / 20.0D);
        final double regressedTotal = regressedImmediate + rawShare;
        check(Math.abs(regressedTotal - 12.2D) < 1e-9,
                "the old raw-damage model is reproduced exactly: it charged 12.2 HP");
        check(regressedTotal > 8.0D + 1e-9,
                "the old model charged MORE than the unmitigated-through hit itself — the defect");
        check(accepted < rawShare,
                "the conserving model defers strictly less than the old raw share");

        // 3. very high mitigation
        conservation(40.0D, 2.0D, deferPercent, room, "nagyon magas mitigation mellett");

        // 4. partial pool cap: only part fits, the rest is suffered immediately
        final double tightRoom = 1.0D;
        final double partial = MonkCombatState.acceptedDefer(8.0D, deferPercent, tightRoom);
        check(Math.abs(partial - 1.0D) < 1e-9, "the pool cap bounds what may be deferred");
        conservation(20.0D, 8.0D, deferPercent, tightRoom, "részleges pool-cap mellett");
        check(MonkCombatState.acceptedDefer(8.0D, deferPercent, 0.0D) == 0.0D,
                "a full pool defers nothing and the hit lands whole");

        // 5. the ceiling holds even if a doctrine stacks the percent up
        final double capped = MonkCombatState.acceptedDefer(10.0D, 500.0D, room);
        check(Math.abs(capped - 10.0D * MonkCombatState.MAX_DEFER_PERCENT / 100.0D) < 1e-9,
                "no configuration can defer more than the hard ceiling");

        // 6. purify removes banked health debt — that is the only way the total drops
        final MonkCombatState state = new MonkCombatState();
        state.stagger(8.0D, 100.0D);
        final double cleared = state.purifyStagger(50.0D);
        check(Math.abs(cleared - 4.0D) < 1e-9 && Math.abs(state.staggerPool() - 4.0D) < 1e-9,
                "purify clears exactly its share of the banked damage");

        // 7. logout/spec-switch consequence: the remaining debt is paid, never forgiven
        final double collapsed = state.collapseStagger();
        check(Math.abs(collapsed - 4.0D) < 1e-9 && state.staggerPool() == 0.0D,
                "the logout/spec-switch consequence pays out the whole remaining pool");

        // 8. the pending fraction is one-shot: a hit can never bank twice
        final MonkCombatState pending = new MonkCombatState();
        pending.setPendingDeferFraction(0.35D);
        check(Math.abs(pending.takePendingDeferFraction() - 0.35D) < 1e-9,
                "phase two reads the fraction phase one recorded");
        check(pending.takePendingDeferFraction() == 0.0D,
                "the pending fraction is consumed once — no double banking");
        pending.setPendingDeferFraction(5.0D);
        check(pending.takePendingDeferFraction()
                        <= MonkCombatState.MAX_DEFER_PERCENT / 100.0D + 1e-9,
                "the pending fraction is bounded by the same ceiling");
    }

    /**
     * Runs one hit through both phases and proves the monk loses the same total health as an
     * identical hit taken without any Stagger at all.
     */
    private static void conservation(final double rawDamage, final double finalDamage,
                                     final double deferPercent, final double room,
                                     final String label) {
        final MonkCombatState state = new MonkCombatState();
        final double accepted = MonkCombatState.acceptedDefer(finalDamage, deferPercent, room);
        final double fraction = accepted / finalDamage;
        state.setPendingDeferFraction(fraction);

        // phase one scales the event; the pipeline stays multiplicative, so the final scales too
        final double reducedBase = rawDamage * (1.0D - fraction);
        final double reducedFinal = finalDamage * (reducedBase / rawDamage);

        // phase two recovers the deferred amount from the authoritative final damage
        final double banked = MonkCombatState.bankedFromReducedFinal(
                reducedFinal, state.takePendingDeferFraction());
        state.stagger(banked, room);

        final double immediate = reducedFinal;
        final double deferred = state.staggerPool();
        final double total = immediate + deferred;
        check(Math.abs(total - finalDamage) < 1e-6,
                label + ": a Staggerrel elszenvedett teljes életveszteség (" + total
                        + ") megegyezik a Stagger nélküli tényleges sebzéssel (" + finalDamage + ")");
        check(deferred <= finalDamage + 1e-9,
                label + ": a halasztott rész sosem nagyobb a tényleges sebzésnél");
        check(immediate >= 0.0D, label + ": az azonnali rész sosem negatív");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
