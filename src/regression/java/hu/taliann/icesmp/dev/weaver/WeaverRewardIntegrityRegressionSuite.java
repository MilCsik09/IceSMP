package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class WeaverRewardIntegrityRegressionSuite {
    public static void main(final String[] args) {
        final UUID player = UUID.randomUUID(), world = UUID.randomUUID();
        final DeveloperInfluence sandbox = new DeveloperInfluence(UUID.randomUUID(), IntegrityMode.SANDBOX, "fixture.action", HiddenDevAuthority.PRIMARY_DEVELOPER, 1);
        final DeveloperInfluence live = new DeveloperInfluence(UUID.randomUUID(), IntegrityMode.LIVE_GM, "fixture.live", HiddenDevAuthority.PRIMARY_DEVELOPER, 2);
        check(sandbox.retainSandboxOrigin(live).equals(sandbox), "LIVE_GM washed sandbox provenance");
        check(live.retainSandboxOrigin(sandbox).equals(sandbox), "SANDBOX origin lost");
        final PlayerQuarantine active = PlayerQuarantine.clean(player).affected(sandbox, true, 100);
        check(active.quarantined(9_000_000), "active projection quarantine expired");
        final PlayerQuarantine ended = active.ended(sandbox.operationId(), 9_000_000);
        check(ended.quarantined(9_299_999) && !ended.quarantined(9_300_000), "five-minute tail did not begin on projection removal");
        check(ended.affected(live, false, 10).equals(ended), "LIVE_GM shortened player quarantine");
        final PlayerQuarantine oneShot = PlayerQuarantine.clean(player).affected(sandbox, false, 100);
        check(oneShot.quarantined(300_099) && !oneShot.quarantined(300_100), "one-shot quarantine tail");
        final List<RewardSource> sources = List.of(new RewardSource.Entity(UUID.randomUUID()), new RewardSource.Player(UUID.randomUUID()),
                new RewardSource.Item(UUID.randomUUID()), new RewardSource.Event("fixture", UUID.randomUUID()), new RewardSource.World(world), new RewardSource.Location(world, 1, 64, -1));
        final AtomicReference<RewardSource> tainted = new AtomicReference<>();
        final AtomicReference<InfluenceRewardEligibilityPolicy.Evidence> recipient = new AtomicReference<>(InfluenceRewardEligibilityPolicy.Evidence.CLEAN);
        final InfluenceRewardEligibilityPolicy gate = new InfluenceRewardEligibilityPolicy(new InfluenceRewardEligibilityPolicy.Lookup() {
            @Override public InfluenceRewardEligibilityPolicy.Evidence recipient(final UUID id) { return recipient.get(); }
            @Override public InfluenceRewardEligibilityPolicy.Evidence source(final RewardSource source) {
                return source.equals(tainted.get()) ? InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED : InfluenceRewardEligibilityPolicy.Evidence.CLEAN;
            }
        });
        int denied = 0;
        for (final RewardChannel channel : RewardChannel.values()) {
            final RewardContext context = new RewardContext(channel, player, sources);
            check(gate.evaluate(context).allowed(), "clean channel rejected");
            for (final RewardSource source : sources) {
                tainted.set(source); check(!gate.evaluate(context).allowed(), "source leak in " + channel); denied++;
            }
            tainted.set(null); recipient.set(InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED);
            check(!gate.evaluate(context).allowed(), "player quarantine leak in " + channel);
            recipient.set(InfluenceRewardEligibilityPolicy.Evidence.UNAVAILABLE);
            check(!gate.evaluate(context).allowed(), "unavailable influence admitted reward");
            recipient.set(InfluenceRewardEligibilityPolicy.Evidence.CLEAN);
        }
        final RewardContext context = new RewardContext(RewardChannel.QUEST_REWARD, player, sources);
        check(!new CompositeRewardEligibilityPolicy(List.of(c -> RewardDecision.allow(), c -> RewardDecision.deny("FIXTURE_DENY"), c -> RewardDecision.allow())).evaluate(context).allowed(), "later allow overrode deny");
        check(!new CompositeRewardEligibilityPolicy(List.of(c -> { throw new IllegalStateException("private detail"); })).evaluate(context).allowed(), "failed policy admitted reward");
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> new RewardContext(RewardChannel.KILL_REWARD, player, List.of()));
        System.out.println("Weaver reward-policy foundation passed " + denied + " channel/source denials; gameplay flow wiring remains a separate acceptance gate.");
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}
