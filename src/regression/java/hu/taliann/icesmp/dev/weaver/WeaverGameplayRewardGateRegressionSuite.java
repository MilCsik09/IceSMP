package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.integrity.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Exercises the installed gameplay gateway, including unclaimed rewards without a fabricated recipient. */
public final class WeaverGameplayRewardGateRegressionSuite {
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
    public static void main(final String[] args) throws Exception {
        final UUID recipient = UUID.randomUUID(), world = UUID.randomUUID(); final var victim = new RewardSource.Entity(UUID.randomUUID());
        final var cause = new RewardSource.Entity(UUID.randomUUID()); final var player = new RewardSource.Player(recipient);
        final List<RewardSource> sources = List.of(victim, cause, player, new RewardSource.World(world), new RewardSource.Location(world, 1, 64, 1));
        final RewardSourceContext unclaimed = new RewardSourceContext(RewardChannel.VANILLA_DROPS, sources);
        check(!GameplayRewardGate.evaluateSources(unclaimed).allowed() && !GameplayRewardGate.evaluate(unclaimed.forRecipient(recipient)).allowed(), "unbound gameplay gateway allowed rewards");
        final Map<RewardSource, InfluenceRewardEligibilityPolicy.Evidence> evidence = new ConcurrentHashMap<>();
        final AtomicReference<InfluenceRewardEligibilityPolicy.Evidence> recipientEvidence = new AtomicReference<>(InfluenceRewardEligibilityPolicy.Evidence.CLEAN);
        final AtomicBoolean broken = new AtomicBoolean(); final AtomicInteger recipientReads = new AtomicInteger();
        final var policy = new InfluenceRewardEligibilityPolicy(new InfluenceRewardEligibilityPolicy.Lookup() {
            @Override public InfluenceRewardEligibilityPolicy.Evidence recipient(final UUID id) { recipientReads.incrementAndGet(); return recipientEvidence.get(); }
            @Override public InfluenceRewardEligibilityPolicy.Evidence source(final RewardSource source) {
                if (broken.get()) throw new LinkageError("injected unavailable lookup");
                return evidence.getOrDefault(source, InfluenceRewardEligibilityPolicy.Evidence.CLEAN);
            }
        });
        final var binding = GameplayRewardGate.install(new CompositeRewardEligibilityPolicy(List.of(policy)));
        try { GameplayRewardGate.install(context -> RewardDecision.allow()); throw new AssertionError("policy authority replaced"); } catch (final IllegalStateException expected) { }
        final int before = recipientReads.get(); check(GameplayRewardGate.evaluateSources(unclaimed).allowed() && recipientReads.get() == before, "unclaimed world reward invented a recipient lookup");
        for (final RewardChannel channel : RewardChannel.values()) {
            final RewardSourceContext source = new RewardSourceContext(channel, sources); check(GameplayRewardGate.evaluate(source.forRecipient(recipient)).allowed(), "clean reward rejected");
            for (final RewardSource tainted : sources) {
                evidence.put(tainted, InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED);
                check(!GameplayRewardGate.evaluate(source.forRecipient(recipient)).allowed() && !GameplayRewardGate.evaluateSources(source).allowed(), "source/causal/spatial taint bypassed " + channel);
                evidence.clear();
            }
        }
        recipientEvidence.set(InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED);
        check(!GameplayRewardGate.evaluate(unclaimed.forRecipient(recipient)).allowed(), "beneficiary quarantine bypassed"); recipientEvidence.set(InfluenceRewardEligibilityPolicy.Evidence.CLEAN);
        final var continueOwner = new CompletableFuture<Void>(); final AtomicInteger awarded = new AtomicInteger();
        final RewardContext captured = new RewardContext(RewardChannel.CLASS_XP, recipient, sources);
        check(GameplayRewardGate.evaluate(captured).allowed(), "initial context not clean");
        final var continuation = continueOwner.thenRun(() -> { if (GameplayRewardGate.evaluate(captured).allowed()) awarded.incrementAndGet(); });
        evidence.put(cause, InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED); continueOwner.complete(null); continuation.get(10, TimeUnit.SECONDS);
        check(awarded.get() == 0, "cached allow decision survived an owner continuation"); evidence.clear();
        broken.set(true); check(!GameplayRewardGate.evaluate(captured).allowed() && !GameplayRewardGate.evaluateSources(unclaimed).allowed(), "lookup linkage failure leaked a reward");
        final RewardEligibilityPolicy recipientOnly = context -> RewardDecision.allow();
        check(!new CompositeRewardEligibilityPolicy(List.of(recipientOnly)).evaluateSources(unclaimed).allowed(), "recipient-only policy implicitly allowed unclaimed world drops");
        binding.close(); check(!GameplayRewardGate.evaluate(captured).allowed(), "disabled binding still granted rewards"); broken.set(false);
        try (final var restarted = GameplayRewardGate.install(policy)) {
            binding.close(); check(GameplayRewardGate.evaluate(captured).allowed(), "stale shutdown cleared a newer plugin binding");
        }
        final AtomicReference<GameplayRewardGate.Binding> closing = new AtomicReference<>();
        closing.set(GameplayRewardGate.install(context -> { closing.get().close(); return RewardDecision.allow(); }));
        check(!GameplayRewardGate.evaluate(captured).allowed(), "shutdown during policy evaluation returned a stale allow");
        System.out.println("Gameplay reward gateway passed: one-time binding, unclaimed source-only policy, 26 channels with victim/cause/player/world/spatial evidence, late continuation quarantine and unavailable-policy denial. Native producers and asynchronous profile settlement require separate evidence.");
    }
}
