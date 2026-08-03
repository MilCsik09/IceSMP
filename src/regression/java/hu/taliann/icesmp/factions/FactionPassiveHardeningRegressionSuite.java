package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;
import org.bukkit.event.entity.EntityTargetEvent.TargetReason;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Pure regressions for the review-found adapter, retaliation, precedence and food boundaries. */
public final class FactionPassiveHardeningRegressionSuite {

    private FactionPassiveHardeningRegressionSuite() {
    }

    public static void main(final String[] args) {
        targetCancellationClearsTheRequestedTarget();
        darkRetaliationIsPlayerMobScopedAndExpires();
        bloodMoonOverridesAmbientTruce();
        whisperRetaliationPreservesWildTruceConstraints();
        vanillaRetaliationReasonsReachUnbreakableTruces();
        signatureFoodUsesLiveMembership();
        foodDutyCallbackUsesLiveConfigAndMembership();
        foodDurationsFailClosedOnOverflow();
        taxEvasionSinStaysPendingUntilOwnerAck();
        combustProvenanceNeverShortensExistingFire();
        System.out.println("Faction passive hardening regression suite passed.");
    }

    private static void targetCancellationClearsTheRequestedTarget() {
        for (final FactionPassivePolicy.TargetDecision decision
                : FactionPassivePolicy.TargetDecision.values()) {
            final FactionPassiveAdapterPolicy.TargetMutation mutation =
                    FactionPassiveAdapterPolicy.targetMutation(decision);
            if (decision == FactionPassivePolicy.TargetDecision.ALLOW) {
                check(!mutation.cancelEvent() && !mutation.clearRequestedTarget(),
                        "ALLOW mutated an explicit target");
            } else {
                check(!mutation.cancelEvent() && mutation.clearRequestedTarget(),
                        "cancelled acquisition retained an existing target: " + decision);
            }
        }
    }

    private static void darkRetaliationIsPlayerMobScopedAndExpires() {
        final AtomicLong clock = new AtomicLong(1_000L);
        final FactionPassiveService state = new FactionPassiveService(clock::get);
        final UUID playerA = UUID.randomUUID();
        final UUID playerB = UUID.randomUUID();
        final UUID mobA = UUID.randomUUID();
        final UUID mobB = UUID.randomUUID();
        state.provokeDark(playerA, mobA, 5_000L);
        check(state.isDarkRetaliating(playerA, mobA),
                "provoked pair did not retaliate");
        check(!state.isDarkRetaliating(playerA, mobB)
                        && !state.isDarkRetaliating(playerB, mobA),
                "retaliation leaked to another mob or player");
        clock.addAndGet(5_001L);
        check(!state.isDarkRetaliating(playerA, mobA),
                "expired retaliation remained live");
        state.provokeDark(playerA, mobA, 5_000L);
        state.clearDarkRetaliation(playerA, mobA);
        check(!state.isDarkRetaliating(playerA, mobA),
                "retired/rejected entity cleanup retained the pair lease");
        state.provokeDark(playerA, mobA, 5_000L);
        state.clearPlayerState(playerA);
        check(!state.isDarkRetaliating(playerA, mobA),
                "logout/faction-reset cleanup retained retaliation");
    }

    private static void bloodMoonOverridesAmbientTruce() {
        final FactionPassivePolicy policy = new FactionPassivePolicy();
        final FactionMembership dark = FactionMembership.citizen(FactionType.DARK);
        final FactionPassiveSettings settings = settings();
        final FactionPassivePolicy.TargetContext ordinary = context(false, false);
        final FactionPassivePolicy.TargetContext bloodMoon = context(true, false);
        final FactionPassivePolicy.TargetContext provokedBloodMoon = context(true, true);
        check(policy.resolveTarget(dark, ordinary, settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_DARK_AMBIENT,
                "ordinary ambient citizenship was lost");
        check(policy.resolveTarget(dark, bloodMoon, settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "Blood Moon did not override ambient citizenship");
        check(policy.resolveTarget(dark, provokedBloodMoon, settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "provoked ambient undead was pacified during Blood Moon");
    }

    private static void whisperRetaliationPreservesWildTruceConstraints() {
        final FactionPassivePolicy policy = new FactionPassivePolicy();
        final FactionMembership red = FactionMembership.citizen(FactionType.RED);
        final FactionPassiveSettings base = settings();
        final FactionPassiveSettings configured = new FactionPassiveSettings(
                base.enabled(), base.red(), base.blue(), base.neutral(), base.dark(),
                new FactionPassiveSettings.Whisper(true, true, 0.35D,
                        true, false, 60_000L, 0.02D, 16.0D, 1.0D));
        final FactionPassivePolicy.TargetContext night = whisperContext(false, true);
        final FactionPassivePolicy.TargetContext day = whisperContext(false, false);
        final FactionPassivePolicy.TargetContext bloodMoon = whisperContext(true, true);

        check(policy.resolveTarget(red, night, configured, 0.34D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_WHISPER_WILD,
                "non-breaking Whisper retaliation lost its configured night truce");
        check(policy.resolveTarget(red, night, configured, 0.35D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "provocation upgraded Whisper target cancellation to a guaranteed truce");
        check(policy.resolveTarget(red, day, configured, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "provocation bypassed Whisper night-only precedence");
        check(policy.resolveTarget(red, bloodMoon, configured, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "provocation bypassed the Whisper Blood Moon override");
        check(policy.resolveTarget(FactionMembership.guest(), night, configured, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "guest received a Whisper faction benefit");
    }

    private static void vanillaRetaliationReasonsReachUnbreakableTruces() {
        check(FactionMobContextResolver.isRetaliating(
                        false, TargetReason.TARGET_ATTACKED_ENTITY)
                        && FactionMobContextResolver.isRetaliating(
                        false, TargetReason.TARGET_ATTACKED_NEARBY_ENTITY)
                        && FactionMobContextResolver.isRetaliating(
                        false, TargetReason.REINFORCEMENT_TARGET),
                "vanilla retaliation reason was discarded before the truce policy");
        check(!FactionMobContextResolver.isRetaliating(false, TargetReason.CLOSEST_PLAYER)
                        && FactionMobContextResolver.isRetaliating(true, TargetReason.CLOSEST_PLAYER),
                "spontaneous targeting and timed retaliation were conflated");

        final FactionPassiveSettings base = settings();
        final FactionPassiveSettings unbreakableNeutral = new FactionPassiveSettings(
                base.enabled(), base.red(), base.blue(),
                new FactionPassiveSettings.Neutral(true, 0.50D, true, true,
                        Set.of("PIGLIN"), false, 60_000L, true, true),
                base.dark(), base.whisper());
        final FactionPassivePolicy.TargetContext neutralRetaliation =
                new FactionPassivePolicy.TargetContext(
                        false, Set.of(), FactionMobContextResolver.isRetaliating(
                        false, TargetReason.TARGET_ATTACKED_ENTITY), false, true,
                        false, false, true, false, false, false, false, false);
        check(new FactionPassivePolicy().resolveTarget(
                        FactionMembership.citizen(FactionType.NEUTRAL), neutralRetaliation,
                        unbreakableNeutral, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_NEUTRAL_TRUCE,
                "NEUTRAL break-on-damage=false was bypassed by the Bukkit adapter");

        final FactionPassiveSettings.Dark dark = base.dark();
        final FactionPassiveSettings unbreakableDark = new FactionPassiveSettings(
                base.enabled(), base.red(), base.blue(), base.neutral(),
                new FactionPassiveSettings.Dark(
                        dark.enabled(), dark.witherDamageEnabled(), dark.witherDamageMultiplier(),
                        dark.witherDurationEnabled(), dark.witherDurationMultiplier(),
                        new FactionPassiveSettings.AmbientUndead(
                                true, false, 60_000L, 16.0D, true),
                        dark.wildUndead(), dark.exclusions(),
                        dark.combatMarkerKeys(), dark.questMarkerKeys()),
                base.whisper());
        final FactionPassivePolicy.TargetContext darkRetaliation =
                new FactionPassivePolicy.TargetContext(
                        false, Set.of(), FactionMobContextResolver.isRetaliating(
                        false, TargetReason.TARGET_ATTACKED_ENTITY), false, true,
                        true, true, false, false, false, false, false, false);
        check(new FactionPassivePolicy().resolveTarget(
                        FactionMembership.citizen(FactionType.DARK), darkRetaliation,
                        unbreakableDark, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_DARK_AMBIENT,
                "DARK ambient break-on-damage=false was bypassed by the Bukkit adapter");
    }

    private static void combustProvenanceNeverShortensExistingFire() {
        check(FactionPassiveService.effectiveCombustDurationMillis(1.0F, 100) == 5_000L,
                "short combust event truncated existing fire provenance");
        check(FactionPassiveService.effectiveCombustDurationMillis(8.0F, 20) == 8_000L,
                "long combust event did not extend provenance");
        check(FactionPassiveService.effectiveCombustDurationMillis(Float.NaN, -1) == 0L,
                "invalid duration created provenance");
    }

    private static void signatureFoodUsesLiveMembership() {
        check(FactionFoodPolicy.mayApplyBuff(FactionType.RED, "fonixtojas_rantotta", true),
                "correct faction was rejected");
        check(!FactionFoodPolicy.mayApplyBuff(FactionType.BLUE, "fonixtojas_rantotta", true)
                        && !FactionFoodPolicy.mayApplyBuff(null, "fonixtojas_rantotta", true),
                "wrong faction or guest received a signature buff");
        check(!FactionFoodPolicy.mayApplyBuff(FactionType.RED, "kakaobabos_sutemeny", true)
                        && FactionFoodPolicy.mayApplyBuff(
                        FactionType.NEUTRAL, "kakaobabos_sutemeny", true),
                "item acquisition survived a live faction switch");
        check(FactionFoodPolicy.requiredFaction("forged", true) == null
                        && FactionFoodPolicy.requiredFaction(
                        "fonixtojas_rantotta", false) == null,
                "forged/partial metadata was accepted");
    }

    private static void foodDutyCallbackUsesLiveConfigAndMembership() {
        check(FactionFoodPolicy.mayRunDutyCallback(true, FactionType.BLUE)
                        && FactionFoodPolicy.mayRunDutyCallback(true, FactionType.RED),
                "eligible citizen food-duty callback was rejected");
        check(!FactionFoodPolicy.mayRunDutyCallback(false, FactionType.BLUE),
                "queued food-duty callback ignored a live reload disable");
        check(!FactionFoodPolicy.mayRunDutyCallback(true, null)
                        && !FactionFoodPolicy.mayRunDutyCallback(true, FactionType.NEUTRAL)
                        && !FactionFoodPolicy.mayRunDutyCallback(true, FactionType.DARK),
                "guest or non-duty faction received a food-duty callback");
    }

    private static void foodDurationsFailClosedOnOverflow() {
        check(FactionFoodPolicy.durationMillis(5L, 60_000L) == 300_000L,
                "normal food-duty interval conversion changed");
        check(FactionFoodPolicy.durationMillis(Long.MAX_VALUE, 60_000L) == 0L
                        && FactionFoodPolicy.durationMillis(0L, 60_000L) == 0L,
                "invalid/overflowing food-duty interval did not fail closed");
        check(FactionFoodPolicy.deadline(1_000L, 300_000L) == 301_000L
                        && FactionFoodPolicy.deadline(Long.MAX_VALUE - 1L, 2L) == 0L,
                "food-duty deadline wrapped into the past");
        check(FactionFoodPolicy.hasGraceElapsed(10_000L, 1_000L, 9_000L)
                        && !FactionFoodPolicy.hasGraceElapsed(10_000L, 10_001L, 1L)
                        && !FactionFoodPolicy.hasGraceElapsed(10_000L, -1L, 1L),
                "future/corrupt food-duty timestamp triggered a debuff");
        check(FactionFoodPolicy.durationTicks(60) == 1_200
                        && FactionFoodPolicy.durationTicks(Integer.MAX_VALUE) == 0
                        && FactionFoodPolicy.durationTicks(0) == 0,
                "food potion duration overflowed Paper's int tick range");
    }

    private static void taxEvasionSinStaysPendingUntilOwnerAck() {
        final FactionTaxEvasionPolicy.Decision rejected =
                FactionTaxEvasionPolicy.afterCollection(
                        2, 0.0D, 50.0D, 50.0D, 3, false);
        check(rejected.strikesAfter() == 3 && !rejected.reportSin(),
                "scheduler rejection cleared the durable tax-evasion threshold");

        final FactionTaxEvasionPolicy.Decision offlineRetry =
                FactionTaxEvasionPolicy.afterCollection(
                        3, 50.0D, 0.0D, 50.0D, 3, false);
        check(offlineRetry.strikesAfter() == 3 && !offlineRetry.reportSin(),
                "offline repayment erased a pending tax-evasion delivery");

        final FactionTaxEvasionPolicy.Decision ownerThreadRetry =
                FactionTaxEvasionPolicy.afterCollection(
                        3, 50.0D, 0.0D, 50.0D, 3, true);
        check(ownerThreadRetry.strikesAfter() == 3 && ownerThreadRetry.reportSin(),
                "pending tax-evasion sin was not retried on an available owner thread");

        final FactionTaxEvasionPolicy.Decision ordinaryPayment =
                FactionTaxEvasionPolicy.afterCollection(
                        2, 5.0D, 20.0D, 50.0D, 3, true);
        check(ordinaryPayment.strikesAfter() == 0 && !ordinaryPayment.reportSin(),
                "sub-threshold strikes survived a normal arrears recovery");
        check(FactionTaxEvasionPolicy.afterCollection(
                        3, 0.0D, 50.0D, 50.0D, 0, true).strikesAfter() == 0,
                "disabled evasion policy retained a stale pending threshold");
    }

    private static FactionPassivePolicy.TargetContext context(final boolean bloodMoon,
                                                              final boolean provoked) {
        return new FactionPassivePolicy.TargetContext(
                false, Set.of(), provoked, bloodMoon, true,
                true, true, false, false, false,
                false, true, false);
    }

    private static FactionPassivePolicy.TargetContext whisperContext(final boolean bloodMoon,
                                                                     final boolean night) {
        return new FactionPassivePolicy.TargetContext(
                false, Set.of(), true, bloodMoon, night,
                true, false, false, false, false,
                false, false, true);
    }

    private static FactionPassiveSettings settings() {
        return new FactionPassiveSettings(
                true,
                new FactionPassiveSettings.Red(true, 0.25D, 0.25D, 0.75D,
                        0.50D, 0.25D, false, 0.75D, false),
                new FactionPassiveSettings.Blue(true, 0.0D, 0.50D, 0.25D,
                        Set.of("SPRINT")),
                new FactionPassiveSettings.Neutral(true, 0.50D, true, true,
                        Set.of("PIGLIN"), true, 60_000L, true, true),
                new FactionPassiveSettings.Dark(true, true, 0.50D, true, 0.50D,
                        new FactionPassiveSettings.AmbientUndead(
                                true, true, 60_000L, 16.0D, true),
                        new FactionPassiveSettings.WildUndead(true, true, 0.50D, true),
                        new FactionPassiveSettings.Exclusions(
                                true, true, true, true, true, true, true),
                        Set.of("icesmp:scripted_combat"), Set.of("icesmp:quest_mob")),
                new FactionPassiveSettings.Whisper(true, true, 0.35D,
                        true, true, 60_000L, 0.02D, 16.0D, 1.0D));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
