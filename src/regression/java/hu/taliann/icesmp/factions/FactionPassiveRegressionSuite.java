package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Dependency-free behavioural regressions for faction-passive policy, config and lifecycle. */
public final class FactionPassiveRegressionSuite {

    private static final FactionPassivePolicy POLICY = new FactionPassivePolicy();

    private FactionPassiveRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        membershipLifecycleFailsClosed();
        membershipPersistenceRollbackIsAtomic();
        targetAdapterClearsExistingTargets();
        signatureFoodRequiresLiveMembership();
        redProvenanceAndCombustDurationRemainIndependent();
        blueOnlySavesExplicitNaturalExhaustion();
        neutralPrecedenceSeparatesSpontaneousAndRetaliation();
        darkPrecedenceSeparatesAmbientWildAndCombat();
        retaliationIsPerPlayerAndPerMob();
        staleCleanupUsesLivePolicy();
        configReloadIsAtomicAndValidationFailsSafely();
        packagedConfigDocumentsLiveControls();
        runtimeAdaptersKeepTheBehaviouralContracts();
        System.out.println("Faction passive regression suite passed.");
    }

    private static void membershipLifecycleFailsClosed() throws IOException {
        final FactionPassiveSettings settings = defaults();
        final FactionMembership guest = FactionMembership.guest();
        final FactionMembership red = FactionMembership.citizen(FactionType.RED);
        final FactionMembership blue = FactionMembership.citizen(FactionType.BLUE);
        final FactionMembership neutral = FactionMembership.citizen(FactionType.NEUTRAL);

        check(!guest.hasChosenFaction() && !guest.isEligibleForFactionBenefits()
                        && guest.chosenFactionOptional().isEmpty(),
                "assignment-free guest became an implicit citizen");
        checkDouble(1.0D, POLICY.damageMultiplier(
                guest, FactionPassivePolicy.DamageChannel.NEUTRAL_FALL, settings),
                "guest received a faction passive");
        check(POLICY.resolveTarget(guest, spontaneousNeutral(false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "guest received the NEUTRAL truce");
        check(POLICY.resolveTarget(guest, whisperWild(false, true, false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "guest received a hidden Whisper faction advantage");

        checkDouble(0.25D, POLICY.damageMultiplier(
                red, FactionPassivePolicy.DamageChannel.RED_FIRE, settings),
                "RED passive did not activate after explicit selection");
        checkDouble(1.0D, POLICY.damageMultiplier(
                blue, FactionPassivePolicy.DamageChannel.RED_FIRE, settings),
                "old RED passive survived a switch to BLUE");
        checkDouble(0.25D, POLICY.blueExhaustionSaveChance(blue, "SPRINT", settings),
                "BLUE passive did not activate after switch");
        checkDouble(0.50D, POLICY.damageMultiplier(
                neutral, FactionPassivePolicy.DamageChannel.NEUTRAL_FALL, settings),
                "explicit NEUTRAL citizen lost the fall resistance");
        checkDouble(1.0D, POLICY.damageMultiplier(
                guest, FactionPassivePolicy.DamageChannel.NEUTRAL_FALL, settings),
                "admin reset back to guest retained the old passive");

        final String manager = read("src/main/java/hu/taliann/icesmp/managers/FactionManager.java");
        check(manager.contains("getChosenFaction(final UUID uuid)")
                        && manager.contains("isEligibleForFactionBenefits(final UUID uuid)")
                        && manager.contains("isMember(final UUID uuid, final FactionType faction)"),
                "central explicit-membership API is incomplete");
        check(manager.contains("_membership-history")
                        && manager.contains("hasEverChosenFaction")
                        && manager.contains("getLastChosenFaction"),
                "durable choice history can be bypassed by deleting the current assignment");

        final String quests = read("src/main/java/hu/taliann/icesmp/managers/QuestManager.java");
        final String community = read(
                "src/main/java/hu/taliann/icesmp/managers/CommunityGoalManager.java");
        final String council = read("src/main/java/hu/taliann/icesmp/managers/CouncilManager.java");
        final String food = read("src/main/java/hu/taliann/icesmp/listeners/FactionFoodListener.java");
        check(quests.contains("isStillFactionEligible")
                        && quests.contains("factionManager.isMember("),
                "faction quest progress/completion no longer revalidates membership");
        check(community.contains("if (playerFaction == null)")
                        && community.contains("factionManager.isMember("),
                "guest community or season credit regained a fallback faction");
        check(council.contains(
                        "factionManager.isMember(voter.getUniqueId(), FactionType.NEUTRAL)")
                        && council.contains("onMembershipChange(final UUID playerId)"),
                "council authority is no longer tied to live explicit citizenship");
        check(food.contains("getChosenFaction(player.getUniqueId()).orElse(null)")
                        && food.contains("faction != null && switch (faction)"),
                "food duty/signature food regained an implicit NEUTRAL assignment");
    }

    private static void membershipPersistenceRollbackIsAtomic() {
        final UUID playerId = UUID.randomUUID();
        final Map<UUID, FactionType> assignments = new HashMap<>();
        final Map<UUID, FactionType> history = new HashMap<>();
        assignments.put(playerId, FactionType.RED);
        history.put(playerId, FactionType.RED);

        final FactionMembershipMutation.Snapshot beforeSwitch =
                FactionMembershipMutation.capture(assignments, history, playerId);
        FactionMembershipMutation.assign(
                assignments, history, playerId, FactionType.BLUE);
        FactionMembershipMutation.restore(assignments, history, beforeSwitch);
        check(assignments.get(playerId) == FactionType.RED
                        && history.get(playerId) == FactionType.RED,
                "failed membership save did not roll back assignment and history");

        final FactionMembershipMutation.Snapshot beforeReset =
                FactionMembershipMutation.capture(assignments, history, playerId);
        FactionMembershipMutation.removeAssignment(assignments, playerId);
        FactionMembershipMutation.restore(assignments, history, beforeReset);
        check(assignments.get(playerId) == FactionType.RED
                        && history.get(playerId) == FactionType.RED,
                "failed admin reset did not restore durable citizenship");

        try {
            FactionMembershipMutation.assign(
                    assignments, history, playerId, FactionType.DARK);
            throw new IllegalStateException("simulated persistence failure");
        } catch (final IllegalStateException expected) {
            FactionMembershipMutation.restore(assignments, history, beforeSwitch);
        }
        check(assignments.get(playerId) == FactionType.RED,
                "simulated save failure left the candidate assignment published");
    }


    private static void targetAdapterClearsExistingTargets() {
        final FactionPassiveAdapterPolicy.TargetMutation allow =
                FactionPassiveAdapterPolicy.targetMutation(FactionPassivePolicy.TargetDecision.ALLOW);
        check(!allow.cancelEvent() && !allow.clearRequestedTarget(),
                "ALLOW decision mutated a scripted or explicit target");
        for (final FactionPassivePolicy.TargetDecision decision
                : FactionPassivePolicy.TargetDecision.values()) {
            if (decision == FactionPassivePolicy.TargetDecision.ALLOW) {
                continue;
            }
            final FactionPassiveAdapterPolicy.TargetMutation mutation =
                    FactionPassiveAdapterPolicy.targetMutation(decision);
            check(!mutation.cancelEvent() && mutation.clearRequestedTarget(),
                    "truce cancellation retained an existing target: " + decision);
        }
    }

    private static void signatureFoodRequiresLiveMembership() {
        check(FactionFoodPolicy.mayApplyBuff(FactionType.RED, "fonixtojas_rantotta", true),
                "RED signature food rejected its live citizen");
        check(!FactionFoodPolicy.mayApplyBuff(FactionType.BLUE, "fonixtojas_rantotta", true)
                        && !FactionFoodPolicy.mayApplyBuff(null, "fonixtojas_rantotta", true),
                "signature food buff ignored live faction/guest state");
        check(FactionFoodPolicy.mayApplyBuff(FactionType.NEUTRAL, "kakaobabos_sutemeny", true)
                        && !FactionFoodPolicy.mayApplyBuff(FactionType.RED, "kakaobabos_sutemeny", true),
                "NEUTRAL food remained usable after a faction switch");
        check(!FactionFoodPolicy.mayApplyBuff(FactionType.DARK, "forged_metadata", true)
                        && FactionFoodPolicy.requiredFaction(null) == null,
                "unknown or partial signature metadata granted a buff");
    }

    private static void redProvenanceAndCombustDurationRemainIndependent() {
        final FactionMembership red = FactionMembership.citizen(FactionType.RED);
        final FactionPassiveSettings settings = defaults();

        checkDouble(0.25D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_FIRE, settings),
                "environmental FIRE multiplier changed");
        checkDouble(0.25D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_FIRE_TICK, settings),
                "environmental FIRE_TICK multiplier changed");
        checkDouble(0.75D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_ENTITY_FIRE, settings),
                "entity-fire multiplier changed");
        checkDouble(0.50D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_LAVA, settings),
                "LAVA multiplier changed");
        checkDouble(0.25D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_HOT_FLOOR, settings),
                "HOT_FLOOR multiplier changed");
        checkDouble(1.0D, multiplier(red,
                FactionPassivePolicy.DamageChannel.ICE_SMP_FIRE_MAGIC, settings),
                "IceSMP TUZ damage was accidentally classified as vanilla fire");

        final FactionPassiveSettings.Red configured = new FactionPassiveSettings.Red(
                true, 1.75D, 2.25D, 1.50D, 3.0D, 4.0D, true, 2.75D, true);
        final FactionPassiveSettings custom = withRed(settings, configured);
        checkDouble(1.75D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_FIRE, custom),
                "RED multiplier was silently capped");
        checkDouble(2.25D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_FIRE_TICK, custom),
                "RED fire-tick multiplier was silently capped");
        checkDouble(1.50D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_ENTITY_FIRE, custom),
                "RED entity-fire multiplier was silently capped");
        checkDouble(2.75D, multiplier(red,
                FactionPassivePolicy.DamageChannel.ICE_SMP_FIRE_MAGIC, custom),
                "enabled TUZ multiplier was capped or ignored");

        check(FactionPassiveService.combustDurationMillis(0.25F) == 250L,
                "sub-second Paper combust duration was truncated to zero");
        check(FactionPassiveService.combustDurationMillis(0.0001F) == 1L,
                "positive combust provenance did not round outward to one millisecond");
        check(FactionPassiveService.combustDurationMillis(0.0F) == 0L
                        && FactionPassiveService.combustDurationMillis(Float.NaN) == 0L
                        && FactionPassiveService.combustDurationMillis(
                        Float.POSITIVE_INFINITY) == 0L,
                "invalid combust duration did not fail closed");
        check(FactionPassiveService.effectiveCombustDurationMillis(1.0F, 100) == 5_000L
                        && FactionPassiveService.effectiveCombustDurationMillis(8.0F, 20) == 8_000L,
                "combust provenance shortened existing fire or ignored the requested duration");
    }

    private static void blueOnlySavesExplicitNaturalExhaustion() {
        final FactionPassiveSettings settings = defaults();
        final FactionMembership blue = FactionMembership.citizen(FactionType.BLUE);
        final FactionMembership red = FactionMembership.citizen(FactionType.RED);

        for (final String reason : settings.blue().affectedExhaustionReasons()) {
            checkDouble(0.25D, POLICY.blueExhaustionSaveChance(blue, reason, settings),
                    "configured natural exhaustion reason was not handled: " + reason);
        }
        checkDouble(0.25D, POLICY.blueExhaustionSaveChance(blue, "sprint", settings),
                "exhaustion reason normalization changed");
        for (final String excluded : List.of(
                "HUNGER_EFFECT", "REGEN", "DAMAGED", "UNKNOWN", "ATTACK", "BLOCK_MINED")) {
            checkDouble(0.0D, POLICY.blueExhaustionSaveChance(blue, excluded, settings),
                    "scripted/punitive exhaustion was erased: " + excluded);
        }
        checkDouble(0.0D, POLICY.blueExhaustionSaveChance(blue, null, settings),
                "missing exhaustion reason received a passive");
        checkDouble(0.0D, POLICY.blueExhaustionSaveChance(red, "SPRINT", settings),
                "non-BLUE citizen received BLUE exhaustion saving");
        checkDouble(0.0D, multiplier(
                blue, FactionPassivePolicy.DamageChannel.BLUE_FREEZE, settings),
                "BLUE freeze multiplier changed");
        checkDouble(0.50D, multiplier(
                blue, FactionPassivePolicy.DamageChannel.BLUE_DROWNING, settings),
                "BLUE drowning multiplier changed");
    }

    private static void neutralPrecedenceSeparatesSpontaneousAndRetaliation() {
        final FactionMembership neutral = FactionMembership.citizen(FactionType.NEUTRAL);
        final FactionPassiveSettings settings = defaults();

        check(POLICY.resolveTarget(neutral, spontaneousNeutral(false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_NEUTRAL_TRUCE,
                "spontaneous neutral-mob aggro was not cancelled");
        check(POLICY.resolveTarget(neutral, neutralRetaliation(false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "provoked neutral mob could not retaliate");
        check(POLICY.resolveTarget(neutral, forcedNeutral(), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "admin/scripted target did not override NEUTRAL truce");
        for (final FactionPassivePolicy.ContentContext context
                : FactionPassivePolicy.ContentContext.values()) {
            check(POLICY.resolveTarget(neutral, markedNeutral(context), settings, 0.0D)
                            == FactionPassivePolicy.TargetDecision.ALLOW,
                    "marked combat content lost its target: " + context);
        }
        check(POLICY.resolveTarget(neutral, spontaneousEnderman(), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_ENDERMAN_STARE,
                "Enderman stare truce was not applied");
        check(POLICY.resolveTarget(neutral, endermanRetaliation(), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "damaged Enderman could not retaliate");

        final FactionPassiveSettings.Neutral unbreakable = new FactionPassiveSettings.Neutral(
                true, 0.50D, true, true, Set.of("PIGLIN"), false, 60_000L, true, false);
        final FactionPassiveSettings configured = withNeutral(settings, unbreakable);
        check(POLICY.resolveTarget(neutral, neutralRetaliation(false), configured, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_NEUTRAL_TRUCE,
                "break-on-damage=false did not preserve truce");
        check(POLICY.resolveTarget(neutral, endermanRetaliation(), configured, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_ENDERMAN_STARE,
                "allow-enderman-retaliation=false did not preserve Enderman truce");
    }

    private static void darkPrecedenceSeparatesAmbientWildAndCombat() {
        final FactionMembership dark = FactionMembership.citizen(FactionType.DARK);
        final FactionMembership redWhisperer = FactionMembership.citizen(FactionType.RED);
        final FactionPassiveSettings settings = defaults();

        check(POLICY.resolveTarget(dark, darkAmbient(false, false), settings, 0.99D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_DARK_AMBIENT,
                "ambient undead did not recognize DARK territorial citizenship");
        check(POLICY.resolveTarget(dark, darkAmbient(true, false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "provoked ambient undead could not retaliate");
        check(POLICY.resolveTarget(dark, darkWild(true, false), settings, 0.25D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_DARK_WILD,
                "night wild-undead chance did not apply");
        check(POLICY.resolveTarget(dark, darkWild(true, false), settings, 0.75D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "wild truce became unconditional");
        check(POLICY.resolveTarget(dark, darkWild(false, false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "night-only wild truce applied by day");
        check(POLICY.resolveTarget(dark, darkWild(true, true), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "Blood Moon did not override wild truce");
        check(POLICY.resolveTarget(dark, darkAmbient(false, true), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "Blood Moon did not override ambient DARK truce");
        check(POLICY.resolveTarget(dark, darkAmbient(true, true), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "provoked DARK mob was incorrectly pacified during Blood Moon");
        check(POLICY.resolveTarget(dark, forcedDark(), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "forced targeting did not outrank DARK truce");

        for (final FactionPassivePolicy.ContentContext context
                : FactionPassivePolicy.ContentContext.values()) {
            check(POLICY.resolveTarget(dark, markedDark(context), settings, 0.0D)
                            == FactionPassivePolicy.TargetDecision.ALLOW,
                    "DARK exclusion failed for combat context: " + context);
        }
        check(POLICY.canAlertDarkUndead(dark, true, Set.of(), settings),
                "live DARK retaliation could not alert a nearby undead");
        check(!POLICY.canAlertDarkUndead(dark, false, Set.of(), settings),
                "queued alert survived expired retaliation");
        check(!POLICY.canAlertDarkUndead(FactionMembership.guest(), true, Set.of(), settings),
                "queued alert survived membership reset");
        check(!POLICY.canAlertDarkUndead(dark, true,
                        EnumSet.of(FactionPassivePolicy.ContentContext.DUNGEON), settings),
                "queued alert pulled a dungeon undead into ambient retaliation");

        check(POLICY.resolveTarget(redWhisperer,
                whisperWild(false, true, false), settings, 0.10D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_WHISPER_WILD,
                "Whisper night truce ignored its chance");
        check(POLICY.resolveTarget(redWhisperer,
                whisperWild(false, true, false), settings, 0.80D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "Whisper truce became unconditional");
        check(POLICY.resolveTarget(redWhisperer,
                whisperWild(true, true, false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "provoked undead could not retaliate against a Whisperer");

        checkDouble(0.50D, multiplier(
                dark, FactionPassivePolicy.DamageChannel.DARK_WITHER, settings),
                "DARK Wither damage multiplier changed");
        checkDouble(0.50D, POLICY.witherDurationMultiplier(dark, settings),
                "DARK Wither duration multiplier changed");
        final FactionPassiveSettings.Dark splitWither = new FactionPassiveSettings.Dark(
                true, false, 2.0D, true, 1.75D, settings.dark().ambientUndead(),
                settings.dark().wildUndead(), settings.dark().exclusions(),
                settings.dark().combatMarkerKeys(), settings.dark().questMarkerKeys());
        final FactionPassiveSettings configured = withDark(settings, splitWither);
        checkDouble(1.0D, multiplier(
                dark, FactionPassivePolicy.DamageChannel.DARK_WITHER, configured),
                "disabled Wither damage branch still changed damage");
        checkDouble(1.75D, POLICY.witherDurationMultiplier(dark, configured),
                "Wither duration was capped or coupled to damage");
    }

    private static void retaliationIsPerPlayerAndPerMob() {
        final AtomicLong now = new AtomicLong(10_000L);
        final FactionPassiveService state = new FactionPassiveService(now::get);
        final UUID playerA = UUID.randomUUID();
        final UUID playerB = UUID.randomUUID();
        final UUID mobA = UUID.randomUUID();
        final UUID mobB = UUID.randomUUID();

        state.provokeDark(playerA, mobA, 1_000L);
        check(state.isDarkRetaliating(playerA, mobA),
                "provoked DARK pair was not active");
        check(!state.isDarkRetaliating(playerA, mobB),
                "hitting one undead disabled truce for every undead near the player");
        check(!state.isDarkRetaliating(playerB, mobA),
                "one player's provocation leaked to another player on the same mob");

        state.provokeDark(playerB, mobA, 2_000L);
        state.provokeNeutral(playerA, mobB, 750L);
        state.markEntityFire(playerA, 1_000L, true);
        check(state.beginWitherAdjustment(playerA),
                "Wither recursion guard could not be acquired");
        check(!state.beginWitherAdjustment(playerA),
                "recursive Wither adjustment was accepted");
        check(state.transientEntryCount() == 6,
                "unexpected pair-scoped transient state count");

        now.addAndGet(1_000L);
        check(!state.isDarkRetaliating(playerA, mobA),
                "DARK retaliation survived its exact expiry");
        check(state.isDarkRetaliating(playerB, mobA),
                "one player's expiry removed another player's retaliation");
        check(!state.isNeutralRetaliating(playerA, mobB),
                "NEUTRAL retaliation did not expire");

        state.clearPlayerState(playerA);
        check(!state.isEntityFire(playerA) && !state.isScriptedCombatFire(playerA)
                        && !state.isAdjustingWitherEffect(playerA),
                "quit/switch cleanup retained player state");
        check(state.isDarkRetaliating(playerB, mobA),
                "cleanup for one player erased another player's mob pair");
        state.clearAll();
        check(state.transientEntryCount() == 0,
                "reload/disable cleanup leaked transient entries");

        state.provokeDark(playerA, mobA, Long.MAX_VALUE);
        check(state.isDarkRetaliating(playerA, mobA),
                "overflow-safe expiry unexpectedly disabled retaliation");
        state.clearAll();
    }

    private static void staleCleanupUsesLivePolicy() {
        final FactionPassiveSettings settings = defaults();
        final FactionMembership dark = FactionMembership.citizen(FactionType.DARK);
        final FactionMembership red = FactionMembership.citizen(FactionType.RED);
        final FactionPassivePolicy.TargetContext ambientAfterExpiry = darkAmbient(false, false);

        check(POLICY.resolveTarget(dark, ambientAfterExpiry, settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_DARK_AMBIENT,
                "expired retaliation did not restore live ambient DARK truce");
        check(POLICY.resolveTarget(red, ambientAfterExpiry, settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "stale cleanup would clear a target after DARK-to-RED switch");
        check(POLICY.resolveTarget(dark,
                markedDark(FactionPassivePolicy.ContentContext.EVENT_MOB), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "stale cleanup would clear a newly marked event target");
        check(POLICY.resolveTarget(dark, ambientAfterExpiry,
                disabled(settings), 0.0D) == FactionPassivePolicy.TargetDecision.ALLOW,
                "stale cleanup would clear a target after passive disable/reload");

        final FactionPassivePolicy.TargetContext incomplete =
                new FactionPassivePolicy.TargetContext(false, null, false,
                        false, true, true, true, false,
                        false, false, false, true, false);
        check(POLICY.resolveTarget(dark, incomplete, settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_DARK_AMBIENT,
                "missing content set caused a non-deterministic/null policy failure");
    }

    private static void configReloadIsAtomicAndValidationFailsSafely() throws Exception {
        final YamlConfiguration live = new YamlConfiguration();
        live.set("factions.passives.red.fire-damage-multiplier", 1.75D);
        live.set("factions.passives.red.fire-tick-damage-multiplier", 2.25D);
        live.set("factions.passives.red.entity-fire-damage-multiplier", 1.25D);
        live.set("factions.passives.red.affect-icesmp-fire-magic", true);
        live.set("factions.passives.red.fire-magic-damage-multiplier", 2.75D);
        live.set("factions.passives.blue.natural-exhaustion-save-chance", 0.20D);
        live.set("factions.passives.blue.affected-exhaustion-reasons", List.of("WALK", "SWIM"));
        live.set("factions.passives.neutral.passive-mob-truce.retaliation-seconds", 17L);
        live.set("factions.passives.dark.ambient-undead.alert-nearby-radius", 31.5D);
        live.set("factions.passives.dark.wild-undead.target-cancel-chance", 0.65D);
        final ConfigHarness harness = config(live, Set.of());
        final FactionPassiveSettings parsed = harness.config().snapshot();

        checkDouble(1.75D, parsed.red().fireDamageMultiplier(),
                "config parser silently capped RED fire");
        checkDouble(2.25D, parsed.red().fireTickDamageMultiplier(),
                "config parser silently capped RED fire tick");
        checkDouble(1.25D, parsed.red().entityFireDamageMultiplier(),
                "config parser ignored entity-fire provenance");
        checkDouble(2.75D, parsed.red().fireMagicDamageMultiplier(),
                "config parser ignored TUZ multiplier");
        check(parsed.blue().affectedExhaustionReasons().equals(Set.of("WALK", "SWIM")),
                "config parser ignored BLUE reason allowlist");
        check(parsed.neutral().retaliationMillis() == 17_000L,
                "config parser ignored retaliation duration");
        checkDouble(31.5D, parsed.dark().ambientUndead().alertNearbyRadius(),
                "config parser ignored DARK alert radius");
        checkDouble(0.65D, parsed.dark().wildUndead().targetCancelChance(),
                "config parser ignored DARK chance");

        live.set("factions.passives.red.fire-damage-multiplier", 3.25D);
        live.set("factions.passives.blue.natural-exhaustion-save-chance", 0.40D);
        harness.config().reload();
        final FactionPassiveSettings reloaded = harness.config().snapshot();
        checkDouble(3.25D, reloaded.red().fireDamageMultiplier(),
                "reload retained stale RED value");
        checkDouble(0.40D, reloaded.blue().naturalExhaustionSaveChance(),
                "reload retained stale BLUE value");

        final YamlConfiguration migrated = new YamlConfiguration();
        migrated.set("factions.passives.blue.natural-exhaustion-save-chance", 0.25D);
        migrated.set("factions.passives.blue-hunger-slow-chance", 0.42D);
        final ConfigHarness legacyOverride = config(migrated,
                Set.of("factions.passives.blue-hunger-slow-chance"));
        checkDouble(0.42D,
                legacyOverride.config().snapshot().blue().naturalExhaustionSaveChance(),
                "legacy BLUE override did not beat bundled new default");
        check(legacyOverride.warnings().contains("Legacy faction-passive override"),
                "legacy BLUE fallback did not emit a migration warning");
        final ConfigHarness bothOverrides = config(migrated, Set.of(
                "factions.passives.blue-hunger-slow-chance",
                "factions.passives.blue.natural-exhaustion-save-chance"));
        checkDouble(0.25D,
                bothOverrides.config().snapshot().blue().naturalExhaustionSaveChance(),
                "new BLUE override did not take precedence");

        final YamlConfiguration invalid = new YamlConfiguration();
        invalid.set("factions.passives.red.fire-damage-multiplier", -0.1D);
        invalid.set("factions.passives.red.fire-tick-damage-multiplier", "bad");
        invalid.set("factions.passives.blue.natural-exhaustion-save-chance", 1.1D);
        invalid.set("factions.passives.blue.affected-exhaustion-reasons",
                List.of("SPRINT", "NOT_A_REASON"));
        invalid.set("factions.passives.neutral.passive-mob-truce.retaliation-seconds", 1.5D);
        invalid.set("factions.passives.dark.wither.duration-multiplier", Double.NaN);
        invalid.set("factions.passives.dark.ambient-undead.alert-nearby-radius", -4.0D);
        invalid.set("factions.passives.dark.exclusions.combat-marker-keys",
                List.of("icesmp:event_mob", "not a namespaced key"));
        final FactionPassiveSettings safe = config(invalid, Set.of()).config().snapshot();
        checkDouble(1.0D, safe.red().fireDamageMultiplier(),
                "negative RED multiplier did not fail to vanilla");
        checkDouble(1.0D, safe.red().fireTickDamageMultiplier(),
                "wrong-type RED multiplier did not fail to vanilla");
        checkDouble(0.0D, safe.blue().naturalExhaustionSaveChance(),
                "out-of-domain chance did not disable its branch");
        check(safe.blue().affectedExhaustionReasons().equals(Set.of("SPRINT")),
                "unknown exhaustion reason was not filtered");
        check(safe.neutral().retaliationMillis() == 0L,
                "fractional retaliation duration was silently truncated");
        checkDouble(1.0D, safe.dark().witherDurationMultiplier(),
                "NaN Wither multiplier did not fail to vanilla");
        checkDouble(0.0D, safe.dark().ambientUndead().alertNearbyRadius(),
                "negative alert radius did not disable alerting");
        check(safe.dark().combatMarkerKeys().equals(Set.of("icesmp:event_mob")),
                "invalid namespaced marker was not filtered");

        final String source = read(
                "src/main/java/hu/taliann/icesmp/factions/FactionPassiveConfig.java");
        final String managerSource = read(
                "src/main/java/hu/taliann/icesmp/managers/ConfigManager.java");
        check(source.contains("synchronized (configManager)")
                        && source.contains("current = buildSnapshot()")
                        && managerSource.contains("volatile ConfigSnapshot liveSnapshot")
                        && managerSource.contains("liveSnapshot = new ConfigSnapshot("),
                "reload can publish a half-old/half-new config snapshot");
    }

    private static void packagedConfigDocumentsLiveControls() throws IOException {
        final Path path = Path.of("src/main/resources/config/factions.yml");
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(path));
        } catch (final Exception exception) {
            throw new AssertionError("packaged factions.yml is not parseable", exception);
        }
        for (final String key : List.of(
                "factions.passives.enabled",
                "factions.passives.red.fire-damage-multiplier",
                "factions.passives.red.entity-fire-damage-multiplier",
                "factions.passives.blue.natural-exhaustion-save-chance",
                "factions.passives.blue.affected-exhaustion-reasons",
                "factions.passives.neutral.passive-mob-truce.retaliation-seconds",
                "factions.passives.dark.ambient-undead.alert-nearby-radius",
                "factions.passives.dark.ambient-undead.disabled-during-blood-moon",
                "factions.passives.dark.wild-undead.target-cancel-chance",
                "factions.passives.dark.exclusions.combat-marker-keys",
                "factions.whisper.night-undead-target-cancel-chance")) {
            check(yaml.isSet(key), "packaged faction-passive key missing: " + key);
        }
        check(!yaml.isSet("factions.passives.blue-hunger-slow-chance"),
                "legacy BLUE key shadows the new default on fresh installs");
    }

    private static void runtimeAdaptersKeepTheBehaviouralContracts() throws IOException {
        final String listener = read(
                "src/main/java/hu/taliann/icesmp/listeners/FactionPassiveListener.java");
        check(listener.contains("EntityExhaustionEvent")
                        && !listener.contains("FoodLevelChangeEvent"),
                "BLUE returned to blanket food-level cancellation");
        check(listener.contains("FactionPassiveAdapterPolicy.targetMutation(decision)")
                        && listener.contains("event.setTarget(null)")
                        && listener.contains("EventPriority.HIGHEST"),
                "target cancellation does not clear the requested target after other plugins");
        check(listener.contains("SpellDamageUtil.schoolOf")
                        && listener.contains("ICE_SMP_FIRE_MAGIC")
                        && listener.contains("RED_ENTITY_FIRE")
                        && listener.contains("explicitCombatContexts(source, settings)"),
                "RED no longer separates TUZ and entity fire");
        check(listener.contains("effectiveCombustDurationMillis(event.getDuration(), player.getFireTicks())"),
                "Paper float combust duration or existing fire ticks are ignored before provenance storage");
        check(listener.contains("getDamageSource().getDirectEntity()")
                        && listener.contains("getDamageSource().getCausingEntity()")
                        && listener.contains("owningPlayerId(projectile.getShooter())")
                        && listener.contains("instanceof org.bukkit.entity.Tameable"),
                "RED/provocation provenance ignores direct, causing, projectile or tame owners");
        check(listener.contains("darkRetaliationRemainingMillis(playerId, sourceMobId)")
                        && listener.contains("state.provokeDark(playerId, mob.getUniqueId(), remaining)"),
                "nearby alert no longer derives mob-specific retaliation from the source pair");
        check(listener.contains("resolveCurrentTruce(")
                        && listener.contains("clearTargetIfStillProtected(")
                        && listener.contains("if (scheduled == null)"),
                "delayed cleanup does not revalidate live policy or scheduler rejection");
        check(listener.contains("contentContexts(mob, liveSettings, playerId)")
                        && listener.contains("canAlertDarkUndead("),
                "queued alert ignores live membership/config/content exclusions");
        check(listener.contains("owningPlayerId(event.getDamageSource().getCausingEntity())")
                        && listener.contains("instanceof org.bukkit.entity.Tameable"),
                "indirect or tame-owner provocation is not attributed to the player");

        final String service = read(
                "src/main/java/hu/taliann/icesmp/factions/FactionPassiveService.java");
        check(service.contains("record PlayerMob(UUID playerId, UUID mobId)")
                        && service.contains("Map<PlayerMob, Long> darkRetaliationUntil"),
                "DARK retaliation regressed to global per-player state");
        check(listener.contains("retireTrackedTarget(playerId, mobId, tracked)")
                        && listener.contains("state.clearDarkRetaliation(playerId, mobId)")
                        && listener.contains("a newer lease replaced this callback"),
                "retired/rejected callbacks can leak or erase a newer retaliation lease");

        final String resolver = read(
                "src/main/java/hu/taliann/icesmp/factions/FactionMobContextResolver.java");
        check(resolver.contains("TargetReason.TARGET_ATTACKED_ENTITY")
                        && resolver.contains("TargetReason.OWNER_ATTACKED_TARGET")
                        && !resolver.contains("TARGET_ATTACKED_PLAYER")
                        && !resolver.contains("OWNER_ATTACKED\""),
                "target resolver uses non-existent/stringly Paper reasons");
        check(!resolver.contains("getPersistentDataContainer().set("),
                "read-only policy adapter mutates entity markers");

        final String membershipManager = read(
                "src/main/java/hu/taliann/icesmp/managers/FactionManager.java");
        check(membershipManager.contains("writeStateLocked(candidate);")
                        && membershipManager.contains("liveState = candidate;")
                        && membershipManager.indexOf("writeStateLocked(candidate);")
                        < membershipManager.indexOf("liveState = candidate;")
                        && membershipManager.indexOf("liveState = candidate;")
                        < membershipManager.indexOf("publishMembershipChange(playerId"),
                "membership state or hook can publish before its durable save");
        check(membershipManager.contains("FactionMembershipMutation.capture(")
                        && membershipManager.contains("DurableTransactionProtocol.execute(")
                        && membershipManager.contains("currencyManager.rollbackDurably(wallet)")
                        && membershipManager.contains("recoverPendingSwitch()"),
                "paid membership switches bypass the durable WAL/rollback/recovery protocol");

        final String foodListener = read(
                "src/main/java/hu/taliann/icesmp/listeners/FactionFoodListener.java");
        final String itemFactory = read(
                "src/main/java/hu/taliann/icesmp/items/ItemDataFactory.java");
        check(foodListener.contains("withoutEmbeddedSignatureFoodEffects(item)")
                        && foodListener.contains("event.setItem(sanitized)")
                        && foodListener.contains("FactionFoodPolicy.mayApplyBuff(faction, sig, trustedFoodMarker)")
                        && itemFactory.contains("toBuilder().effects(List.of())"),
                "signature food keeps embedded or acquisition-time faction entitlement");

        final String worldBoss = read(
                "src/main/java/hu/taliann/icesmp/managers/WorldBossManager.java");
        final String honorDuel = read(
                "src/main/java/hu/taliann/icesmp/managers/HonorDuelManager.java");
        final String spy = read("src/main/java/hu/taliann/icesmp/managers/SpyManager.java");
        check(worldBoss.contains("world-boss-slain-guest")
                        && honorDuel.contains("victimFaction != null")
                        && spy.contains("!factionManager.hasChosenFaction(player.getUniqueId())"),
                "guest reward/authority boundaries are not enforced");

        final String raid = read("src/main/java/hu/taliann/icesmp/managers/RaidManager.java");
        final String war = read("src/main/java/hu/taliann/icesmp/managers/WarWindowManager.java");
        check(raid.contains("lifecycleRevision")
                        && raid.contains("factionManager.isMember(online.getUniqueId(), winner)")
                        && war.contains("rewardWindowToken()")
                        && war.contains("isCurrentRewardWindow"),
                "delayed raid/war rewards use stale captured faction or event identity");

        final String core = read("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java");
        check(core.contains("factionPassiveConfig.reload()")
                        && core.contains("factionPassiveListener.clearAllState()"),
                "reload/disable lifecycle does not refresh config and clear transient state");
        check(core.contains("setMembershipChangeHook(playerId ->")
                        && core.contains("factionPassiveListener.clearPlayerState(playerId)"),
                "membership switch does not clear passive state");

        final String cleanup = read(
                "src/main/java/hu/taliann/icesmp/listeners/PlayerSessionCleanupListener.java");
        check(cleanup.contains("PlayerStateCleanup")
                        && cleanup.contains("clearPlayerState(playerId)"),
                "quit/kick lifecycle no longer calls registered state owners");
    }

    private static double multiplier(final FactionMembership membership,
                                     final FactionPassivePolicy.DamageChannel channel,
                                     final FactionPassiveSettings settings) {
        return POLICY.damageMultiplier(membership, channel, settings);
    }

    private static FactionPassiveSettings defaults() {
        return new FactionPassiveSettings(
                true,
                new FactionPassiveSettings.Red(
                        true, 0.25D, 0.25D, 0.75D, 0.50D, 0.25D,
                        false, 0.75D, false),
                new FactionPassiveSettings.Blue(
                        true, 0.0D, 0.50D, 0.25D,
                        Set.of("SPRINT", "JUMP_SPRINT", "SWIM", "WALK_ON_WATER",
                                "WALK_UNDERWATER")),
                new FactionPassiveSettings.Neutral(
                        true, 0.50D, true, true,
                        Set.of("PIGLIN", "ZOMBIFIED_PIGLIN", "SPIDER", "CAVE_SPIDER"),
                        true, 60_000L, true, true),
                new FactionPassiveSettings.Dark(
                        true, true, 0.50D, true, 0.50D,
                        new FactionPassiveSettings.AmbientUndead(true, true, 60_000L, 16.0D, true),
                        new FactionPassiveSettings.WildUndead(true, true, 0.50D, true),
                        new FactionPassiveSettings.Exclusions(
                                true, true, true, true, true, true, true),
                        Set.of("icesmp:scripted_combat", "icesmp:event_mob", "icesmp:minion_owner"),
                        Set.of("icesmp:quest_mob")),
                new FactionPassiveSettings.Whisper(
                        true, true, 0.35D, true, true, 60_000L,
                        0.02D, 16.0D, 1.0D));
    }

    private static FactionPassiveSettings disabled(final FactionPassiveSettings base) {
        return new FactionPassiveSettings(false, base.red(), base.blue(), base.neutral(),
                base.dark(), base.whisper());
    }

    private static FactionPassiveSettings withRed(final FactionPassiveSettings base,
                                                  final FactionPassiveSettings.Red red) {
        return new FactionPassiveSettings(base.enabled(), red, base.blue(), base.neutral(),
                base.dark(), base.whisper());
    }

    private static FactionPassiveSettings withNeutral(final FactionPassiveSettings base,
                                                      final FactionPassiveSettings.Neutral neutral) {
        return new FactionPassiveSettings(base.enabled(), base.red(), base.blue(), neutral,
                base.dark(), base.whisper());
    }

    private static FactionPassiveSettings withDark(final FactionPassiveSettings base,
                                                   final FactionPassiveSettings.Dark dark) {
        return new FactionPassiveSettings(base.enabled(), base.red(), base.blue(), base.neutral(),
                dark, base.whisper());
    }

    private static FactionPassivePolicy.TargetContext spontaneousNeutral(final boolean enderman) {
        return target(false, Set.of(), false, false, true, false, false,
                !enderman, enderman, enderman, !enderman, false, false);
    }

    private static FactionPassivePolicy.TargetContext neutralRetaliation(final boolean enderman) {
        return target(false, Set.of(), true, false, true, false, false,
                true, enderman, false, false, false, false);
    }

    private static FactionPassivePolicy.TargetContext forcedNeutral() {
        return target(true, Set.of(), false, false, true, false, false,
                true, false, false, true, false, false);
    }

    private static FactionPassivePolicy.TargetContext markedNeutral(
            final FactionPassivePolicy.ContentContext content) {
        return target(false, EnumSet.of(content), false, false, true, false, false,
                true, false, false, true, false, false);
    }

    private static FactionPassivePolicy.TargetContext spontaneousEnderman() {
        return target(false, Set.of(), false, false, true, false, false,
                false, true, true, false, false, false);
    }

    private static FactionPassivePolicy.TargetContext endermanRetaliation() {
        return target(false, Set.of(), true, false, true, false, false,
                true, true, false, false, false, false);
    }

    private static FactionPassivePolicy.TargetContext darkAmbient(
            final boolean retaliation, final boolean bloodMoon) {
        return target(false, Set.of(), retaliation, bloodMoon, true, true, true,
                false, false, false, false, true, false);
    }

    private static FactionPassivePolicy.TargetContext darkWild(
            final boolean night, final boolean bloodMoon) {
        return target(false, Set.of(), false, bloodMoon, night, true, false,
                false, false, false, false, true, false);
    }

    private static FactionPassivePolicy.TargetContext forcedDark() {
        return target(true, Set.of(), false, false, true, true, false,
                false, false, false, false, true, false);
    }

    private static FactionPassivePolicy.TargetContext markedDark(
            final FactionPassivePolicy.ContentContext content) {
        return target(false, EnumSet.of(content), false, false, true, true, true,
                false, false, false, false, true, false);
    }

    private static FactionPassivePolicy.TargetContext whisperWild(
            final boolean retaliation, final boolean night, final boolean bloodMoon) {
        return target(false, Set.of(), retaliation, bloodMoon, night, true, false,
                false, false, false, false, true, true);
    }

    private static FactionPassivePolicy.TargetContext target(
            final boolean forced,
            final Set<FactionPassivePolicy.ContentContext> contents,
            final boolean retaliation,
            final boolean bloodMoon,
            final boolean night,
            final boolean undead,
            final boolean ambientUndead,
            final boolean neutralMob,
            final boolean enderman,
            final boolean spontaneousEndermanStare,
            final boolean spontaneousNeutralAggro,
            final boolean spontaneousUndeadAggro,
            final boolean whisperer) {
        return new FactionPassivePolicy.TargetContext(
                forced, contents, retaliation, bloodMoon, night, undead, ambientUndead,
                neutralMob, enderman, spontaneousEndermanStare, spontaneousNeutralAggro,
                spontaneousUndeadAggro, whisperer);
    }

    private static ConfigHarness config(final YamlConfiguration configuration,
                                        final Set<String> overridePaths) throws Exception {
        final ConfigManager manager = new ConfigManager(null);
        setField(manager, "liveSnapshot", new ConfigManager.ConfigSnapshot(
                configuration, Set.copyOf(overridePaths), 1L));
        final CapturingHandler handler = new CapturingHandler();
        final Logger logger = Logger.getLogger(
                FactionPassiveRegressionSuite.class.getName() + "." + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        return new ConfigHarness(new FactionPassiveConfig(manager, logger), handler);
    }

    private static void setField(final Object target, final String name, final Object value)
            throws ReflectiveOperationException {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String read(final String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static void checkDouble(final double expected, final double actual,
                                    final String message) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record ConfigHarness(FactionPassiveConfig config, CapturingHandler handler) {
        String warnings() {
            return handler.messages();
        }
    }

    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(final LogRecord record) {
            if (isLoggable(record)) {
                messages.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        String messages() {
            return String.join("\n", messages);
        }
    }
}
