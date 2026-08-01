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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Dependency-free behavioural regressions for the faction-passive policy and lifecycle. */
public final class FactionPassiveRegressionSuite {

    private static final FactionPassivePolicy POLICY = new FactionPassivePolicy();

    private FactionPassiveRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        membershipRequiresExplicitCitizenship();
        redDamageChannelsRemainIndependentAndUnclamped();
        blueOnlySavesConfiguredExhaustionReasons();
        neutralTruceHonoursProvocationAndForcedCombat();
        darkTruceHonoursAmbientWildAndCombatPrecedence();
        whisperTruceRemainsPartialAndBreakable();
        transientStateExpiresAndCleansUp();
        configReloadMigratesLegacyValuesAndFailsSafely();
        packagedConfigDocumentsEveryLiveControl();
        integrationBenefitsRequireExplicitCitizenship();
        verifiesAdapterAndLifecycleWiring();
        System.out.println("Faction passive regression suite passed.");
    }

    private static void membershipRequiresExplicitCitizenship() {
        final FactionPassiveSettings settings = defaults();
        final FactionMembership guest = FactionMembership.guest();
        final FactionMembership neutral = FactionMembership.citizen(FactionType.NEUTRAL);

        check(!guest.hasChosenFaction() && !guest.isEligibleForFactionBenefits()
                        && guest.chosenFactionOptional().isEmpty(),
                "a Menedek guest became an implicit citizen");
        check(neutral.hasChosenFaction() && neutral.isEligibleForFactionBenefits()
                        && neutral.isMember(FactionType.NEUTRAL),
                "explicit NEUTRAL citizenship is not eligible");
        checkDouble(1.0D, POLICY.damageMultiplier(guest,
                FactionPassivePolicy.DamageChannel.NEUTRAL_FALL, settings),
                "guest received the NEUTRAL fall passive");
        check(POLICY.resolveTarget(guest, spontaneousNeutral(false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "guest received the NEUTRAL mob truce");
        check(POLICY.resolveTarget(guest, whisperWild(false, true, false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "guest received a hidden faction truce without citizenship");
        checkDouble(0.50D, POLICY.damageMultiplier(neutral,
                FactionPassivePolicy.DamageChannel.NEUTRAL_FALL, settings),
                "explicit NEUTRAL citizen lost the fall passive");
        check(POLICY.resolveTarget(neutral, spontaneousNeutral(false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_NEUTRAL_TRUCE,
                "explicit NEUTRAL citizen lost the spontaneous mob truce");

        final FactionMembership red = FactionMembership.citizen(FactionType.RED);
        final FactionMembership blue = FactionMembership.citizen(FactionType.BLUE);
        checkDouble(0.25D, POLICY.damageMultiplier(red,
                FactionPassivePolicy.DamageChannel.RED_FIRE, settings),
                "RED passive did not apply immediately after switch");
        checkDouble(1.0D, POLICY.damageMultiplier(blue,
                FactionPassivePolicy.DamageChannel.RED_FIRE, settings),
                "old RED passive survived a membership switch");
        checkDouble(0.25D, POLICY.blueExhaustionSaveChance(blue, "SPRINT", settings),
                "new BLUE passive did not apply immediately after switch");
    }

    private static void redDamageChannelsRemainIndependentAndUnclamped() {
        final FactionMembership red = FactionMembership.citizen(FactionType.RED);
        final FactionPassiveSettings settings = defaults();

        checkDouble(0.25D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_FIRE, settings),
                "environmental fire multiplier changed");
        checkDouble(0.25D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_FIRE_TICK, settings),
                "fire tick multiplier changed");
        checkDouble(0.75D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_ENTITY_FIRE, settings),
                "entity fire multiplier changed");
        checkDouble(0.50D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_LAVA, settings),
                "lava multiplier changed");
        checkDouble(0.25D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_HOT_FLOOR, settings),
                "hot-floor multiplier changed");
        checkDouble(1.0D, multiplier(red, FactionPassivePolicy.DamageChannel.ICE_SMP_FIRE_MAGIC, settings),
                "TUZ spell damage was accidentally folded into the RED passive");

        final FactionPassiveSettings.Red configured = new FactionPassiveSettings.Red(
                true, 1.75D, 2.25D, 1.50D, 3.0D, 4.0D, true, 2.75D, true);
        final FactionPassiveSettings custom = withRed(settings, configured);
        checkDouble(1.75D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_FIRE, custom),
                "RED multiplier was silently capped");
        checkDouble(2.25D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_FIRE_TICK, custom),
                "RED fire-tick multiplier was silently capped");
        checkDouble(1.50D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_ENTITY_FIRE, custom),
                "RED entity-fire multiplier was silently capped");
        checkDouble(3.0D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_LAVA, custom),
                "RED lava multiplier was silently capped");
        checkDouble(4.0D, multiplier(red, FactionPassivePolicy.DamageChannel.RED_HOT_FLOOR, custom),
                "RED hot-floor multiplier was silently capped");
        checkDouble(2.75D, multiplier(red, FactionPassivePolicy.DamageChannel.ICE_SMP_FIRE_MAGIC, custom),
                "enabled TUZ multiplier was silently capped or ignored");
        check(configured.affectScriptedCombatFire(),
                "scripted combat fire switch did not survive the immutable settings snapshot");
    }

    private static void blueOnlySavesConfiguredExhaustionReasons() {
        final FactionPassiveSettings settings = defaults();
        final FactionMembership blue = FactionMembership.citizen(FactionType.BLUE);
        final FactionMembership red = FactionMembership.citizen(FactionType.RED);

        for (final String reason : settings.blue().affectedExhaustionReasons()) {
            checkDouble(0.25D, POLICY.blueExhaustionSaveChance(blue, reason, settings),
                    "configured natural exhaustion reason was not saved: " + reason);
        }
        checkDouble(0.25D, POLICY.blueExhaustionSaveChance(blue, "sprint", settings),
                "exhaustion reason normalization changed");
        for (final String punitive : List.of("HUNGER_EFFECT", "REGEN", "DAMAGED", "UNKNOWN", "ATTACK")) {
            checkDouble(0.0D, POLICY.blueExhaustionSaveChance(blue, punitive, settings),
                    "punitive/scripted exhaustion was accidentally erased: " + punitive);
        }
        checkDouble(0.0D, POLICY.blueExhaustionSaveChance(blue, null, settings),
                "null exhaustion reason received a passive");
        checkDouble(0.0D, POLICY.blueExhaustionSaveChance(red, "SPRINT", settings),
                "non-BLUE citizen received the BLUE exhaustion passive");
        checkDouble(0.0D, multiplier(blue,
                FactionPassivePolicy.DamageChannel.BLUE_FREEZE, settings),
                "BLUE freeze multiplier changed");
        checkDouble(0.50D, multiplier(blue,
                FactionPassivePolicy.DamageChannel.BLUE_DROWNING, settings),
                "BLUE drowning multiplier changed");
    }

    private static void neutralTruceHonoursProvocationAndForcedCombat() {
        final FactionMembership neutral = FactionMembership.citizen(FactionType.NEUTRAL);
        final FactionPassiveSettings settings = defaults();

        check(POLICY.resolveTarget(neutral, spontaneousNeutral(false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_NEUTRAL_TRUCE,
                "spontaneous neutral-mob aggression was not cancelled");
        check(POLICY.resolveTarget(neutral, neutralRetaliation(false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "provoked neutral mob could not retaliate");
        check(POLICY.resolveTarget(neutral, forcedNeutral(), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "admin/scripted targeting did not override NEUTRAL truce");
        check(POLICY.resolveTarget(neutral,
                markedNeutral(FactionPassivePolicy.ContentContext.EVENT_MOB), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "event targeting did not override NEUTRAL truce");
        check(POLICY.resolveTarget(neutral, spontaneousEnderman(), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_ENDERMAN_STARE,
                "spontaneous Enderman stare aggression was not cancelled");
        check(POLICY.resolveTarget(neutral, endermanRetaliation(), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "struck Enderman could not retaliate");

        final FactionPassiveSettings.Neutral unbreakable = new FactionPassiveSettings.Neutral(
                true, 0.50D, true, true, Set.of("PIGLIN"), false, 60_000L, true, false);
        final FactionPassiveSettings configured = withNeutral(settings, unbreakable);
        check(POLICY.resolveTarget(neutral, neutralRetaliation(false), configured, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_NEUTRAL_TRUCE,
                "break-on-damage=false did not preserve neutral truce");
        check(POLICY.resolveTarget(neutral, endermanRetaliation(), configured, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_ENDERMAN_STARE,
                "allow-enderman-retaliation=false did not preserve Enderman truce");
    }

    private static void darkTruceHonoursAmbientWildAndCombatPrecedence() {
        final FactionMembership dark = FactionMembership.citizen(FactionType.DARK);
        final FactionPassiveSettings settings = defaults();

        check(POLICY.resolveTarget(dark, darkAmbient(false, false), settings, 0.99D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_DARK_AMBIENT,
                "ambient DARK undead citizen was not peaceful");
        check(POLICY.resolveTarget(dark, darkAmbient(true, false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "provoked ambient undead could not retaliate");
        check(POLICY.resolveTarget(dark, darkWild(true, false), settings, 0.25D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_DARK_WILD,
                "configured night-time wild truce chance did not cancel");
        check(POLICY.resolveTarget(dark, darkWild(true, false), settings, 0.75D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "wild truce cancelled above its configured probability");
        check(POLICY.resolveTarget(dark, darkWild(false, false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "night-only wild truce applied by day");
        check(POLICY.resolveTarget(dark, darkWild(true, true), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "Blood Moon did not override the wild undead truce");
        check(POLICY.resolveTarget(dark, darkAmbient(false, true), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_DARK_AMBIENT,
                "Blood Moon incorrectly disabled ambient Thanaopolis citizenship");
        check(POLICY.resolveTarget(dark, forcedDark(), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "forced targeting did not outrank DARK truce");
        check(POLICY.canAlertDarkUndead(dark, true, Set.of(), settings),
                "live DARK retaliation could not alert a nearby ordinary undead");
        check(!POLICY.canAlertDarkUndead(dark, false, Set.of(), settings),
                "a queued nearby alert survived cleared retaliation state");
        check(!POLICY.canAlertDarkUndead(FactionMembership.guest(), true, Set.of(), settings),
                "a queued nearby alert survived a DARK membership change");
        check(!POLICY.canAlertDarkUndead(dark, true,
                        EnumSet.of(FactionPassivePolicy.ContentContext.DUNGEON), settings),
                "nearby alert pulled a dungeon undead into faction truce retaliation");

        for (final FactionPassivePolicy.ContentContext context
                : FactionPassivePolicy.ContentContext.values()) {
            check(POLICY.resolveTarget(dark, markedDark(context), settings, 0.0D)
                            == FactionPassivePolicy.TargetDecision.ALLOW,
                    "marked combat context received DARK truce: " + context);
        }

        checkDouble(0.50D, multiplier(dark,
                FactionPassivePolicy.DamageChannel.DARK_WITHER, settings),
                "DARK Wither damage multiplier changed");
        checkDouble(0.50D, POLICY.witherDurationMultiplier(dark, settings),
                "DARK Wither duration multiplier changed");
        final FactionPassiveSettings.Dark splitWither = new FactionPassiveSettings.Dark(
                true, false, 2.0D, true, 1.75D, settings.dark().ambientUndead(),
                settings.dark().wildUndead(), settings.dark().exclusions(),
                settings.dark().combatMarkerKeys(), settings.dark().questMarkerKeys());
        final FactionPassiveSettings configured = withDark(settings, splitWither);
        checkDouble(1.0D, multiplier(dark,
                FactionPassivePolicy.DamageChannel.DARK_WITHER, configured),
                "disabled Wither damage branch still modified damage");
        checkDouble(1.75D, POLICY.witherDurationMultiplier(dark, configured),
                "Wither duration setting was capped or coupled to damage");

        final FactionPassiveSettings.AmbientUndead unbreakableAmbient =
                new FactionPassiveSettings.AmbientUndead(true, false, 60_000L, 23.5D);
        final FactionPassiveSettings.Dark ambientConfig = new FactionPassiveSettings.Dark(
                true, true, 0.50D, true, 0.50D, unbreakableAmbient,
                settings.dark().wildUndead(), settings.dark().exclusions(),
                settings.dark().combatMarkerKeys(), settings.dark().questMarkerKeys());
        final FactionPassiveSettings unbreakableSettings = withDark(settings, ambientConfig);
        check(POLICY.resolveTarget(dark, darkAmbient(true, false), unbreakableSettings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_DARK_AMBIENT,
                "ambient break-on-damage=false did not preserve truce");
        checkDouble(23.5D, unbreakableSettings.dark().ambientUndead().alertNearbyRadius(),
                "nearby alert radius changed in the settings snapshot");
    }

    private static void whisperTruceRemainsPartialAndBreakable() {
        final FactionMembership redWhisperer = FactionMembership.citizen(FactionType.RED);
        final FactionPassiveSettings settings = defaults();

        check(POLICY.resolveTarget(redWhisperer, whisperWild(false, true, false), settings, 0.10D)
                        == FactionPassivePolicy.TargetDecision.CANCEL_WHISPER_WILD,
                "Whisper night truce did not use its configured chance");
        check(POLICY.resolveTarget(redWhisperer, whisperWild(false, true, false), settings, 0.80D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "Whisper truce became unconditional");
        check(POLICY.resolveTarget(redWhisperer, whisperWild(false, false, false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "Whisper night-only truce applied by day");
        check(POLICY.resolveTarget(redWhisperer, whisperWild(false, true, true), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "Blood Moon did not override Whisper truce");
        check(POLICY.resolveTarget(redWhisperer, whisperWild(true, true, false), settings, 0.0D)
                        == FactionPassivePolicy.TargetDecision.ALLOW,
                "provoked undead could not retaliate against a Whisperer");
    }

    private static void transientStateExpiresAndCleansUp() {
        final AtomicLong now = new AtomicLong(10_000L);
        final FactionPassiveService state = new FactionPassiveService(now::get);
        final UUID player = UUID.randomUUID();
        final UUID otherPlayer = UUID.randomUUID();
        final UUID mob = UUID.randomUUID();

        state.provokeNeutral(player, mob, 1_000L);
        state.provokeDark(player, 1_000L);
        state.markEntityFire(player, 1_000L, true);
        check(state.beginWitherAdjustment(player), "first Wither adjustment was rejected");
        check(!state.beginWitherAdjustment(player), "recursive Wither adjustment was accepted");
        check(state.isNeutralRetaliating(player, mob) && state.isDarkRetaliating(player)
                        && state.isEntityFire(player) && state.isScriptedCombatFire(player)
                        && state.isAdjustingWitherEffect(player),
                "transient retaliation state was not observable before expiry");
        check(state.transientEntryCount() == 5, "unexpected initial transient-state count");

        state.clearPlayerState(player);
        check(state.transientEntryCount() == 0,
                "quit/switch cleanup left player-scoped passive state behind");

        state.provokeDark(player, 500L);
        state.provokeDark(otherPlayer, 2_000L);
        now.addAndGet(500L);
        check(!state.isDarkRetaliating(player), "retaliation survived its exact expiry instant");
        check(state.isDarkRetaliating(otherPlayer), "one player's expiry removed another player's state");
        state.provokeNeutral(player, mob, 250L);
        now.addAndGet(251L);
        check(!state.isNeutralRetaliating(player, mob), "neutral retaliation did not expire");

        state.markEntityFire(player, 2_000L, true);
        now.addAndGet(100L);
        state.markEntityFire(player, 250L, false);
        now.addAndGet(300L);
        check(state.isEntityFire(player) && state.isScriptedCombatFire(player),
                "a shorter unmarked combust truncated scripted fire provenance");
        now.addAndGet(1_601L);
        check(!state.isEntityFire(player) && !state.isScriptedCombatFire(player),
                "overlapping fire provenance did not expire at the longest source deadline");

        state.markEntityFire(player, 1_000L);
        check(state.beginWitherAdjustment(player), "Wither guard could not be reacquired after cleanup");
        state.endWitherAdjustment(player);
        check(!state.isAdjustingWitherEffect(player), "Wither recursion guard did not release");
        state.clearAll();
        check(state.transientEntryCount() == 0,
                "reload/disable cleanup left faction-passive state behind");

        state.provokeDark(player, Long.MAX_VALUE);
        check(state.isDarkRetaliating(player), "overflow-safe expiry unexpectedly disabled state");
        state.clearAll();
    }

    private static void configReloadMigratesLegacyValuesAndFailsSafely() throws Exception {
        final YamlConfiguration live = new YamlConfiguration();
        live.set("factions.passives.red.fire-damage-multiplier", 1.75D);
        live.set("factions.passives.red.fire-tick-damage-multiplier", 2.25D);
        live.set("factions.passives.red.entity-fire-damage-multiplier", 1.25D);
        live.set("factions.passives.red.lava-damage-multiplier", 1.50D);
        live.set("factions.passives.red.hot-floor-damage-multiplier", 1.80D);
        live.set("factions.passives.red.affect-icesmp-fire-magic", true);
        live.set("factions.passives.red.fire-magic-damage-multiplier", 2.75D);
        live.set("factions.passives.red.affect-scripted-combat-fire", true);
        live.set("factions.passives.blue.freeze-damage-multiplier", 0.30D);
        live.set("factions.passives.blue.drowning-damage-multiplier", 0.60D);
        live.set("factions.passives.blue.natural-exhaustion-save-chance", 0.20D);
        live.set("factions.passives.blue.affected-exhaustion-reasons", List.of("WALK", "SWIM"));
        live.set("factions.passives.neutral.fall-damage-multiplier", 1.20D);
        live.set("factions.passives.neutral.passive-mob-truce.include-non-monsters", false);
        live.set("factions.passives.neutral.passive-mob-truce.break-on-damage", false);
        live.set("factions.passives.neutral.passive-mob-truce.retaliation-seconds", 17L);
        live.set("factions.passives.neutral.enderman.ignore-stare-aggro", false);
        live.set("factions.passives.neutral.enderman.allow-retaliation", false);
        live.set("factions.passives.dark.wither.damage-enabled", true);
        live.set("factions.passives.dark.wither.damage-multiplier", 1.50D);
        live.set("factions.passives.dark.wither.duration-enabled", true);
        live.set("factions.passives.dark.wither.duration-multiplier", 2.50D);
        live.set("factions.passives.dark.ambient-undead.break-on-damage", false);
        live.set("factions.passives.dark.ambient-undead.retaliation-seconds", 23L);
        live.set("factions.passives.dark.ambient-undead.alert-nearby-radius", 31.5D);
        live.set("factions.passives.dark.wild-undead.night-only", false);
        live.set("factions.passives.dark.wild-undead.target-cancel-chance", 0.65D);
        live.set("factions.passives.dark.wild-undead.disabled-during-blood-moon", false);
        live.set("factions.passives.dark.exclusions.corruption", false);
        live.set("factions.passives.dark.exclusions.quest-mobs", false);
        live.set("factions.passives.dark.exclusions.combat-marker-keys",
                List.of("icesmp:custom_combat"));
        live.set("factions.passives.dark.exclusions.quest-marker-keys",
                List.of("icesmp:custom_quest"));
        live.set("factions.whisper.night-undead-target-cancel-chance", 0.45D);
        live.set("factions.whisper.night-undead-retaliation-seconds", 19L);
        live.set("factions.whisper.truce-witness-chance", 0.15D);
        live.set("factions.whisper.truce-witness-radius", 27.0D);
        live.set("factions.whisper.truce-witness-suspicion", 3.5D);
        final ConfigHarness harness = config(live, Set.of());
        final FactionPassiveSettings parsed = harness.config().snapshot();

        checkDouble(1.75D, parsed.red().fireDamageMultiplier(),
                "config parser silently capped a RED multiplier");
        checkDouble(2.25D, parsed.red().fireTickDamageMultiplier(),
                "config parser silently capped a RED fire-tick multiplier");
        checkDouble(1.25D, parsed.red().entityFireDamageMultiplier(),
                "config parser ignored RED entity-fire multiplier");
        checkDouble(1.50D, parsed.red().lavaDamageMultiplier(),
                "config parser ignored RED lava multiplier");
        checkDouble(1.80D, parsed.red().hotFloorDamageMultiplier(),
                "config parser ignored RED hot-floor multiplier");
        check(parsed.red().affectIceSmpFireMagic()
                        && parsed.red().affectScriptedCombatFire(),
                "config parser ignored a RED damage-domain switch");
        checkDouble(2.75D, parsed.red().fireMagicDamageMultiplier(),
                "config parser ignored RED TUZ multiplier");
        checkDouble(0.30D, parsed.blue().freezeDamageMultiplier(),
                "config parser ignored BLUE freeze multiplier");
        checkDouble(0.60D, parsed.blue().drowningDamageMultiplier(),
                "config parser ignored BLUE drowning multiplier");
        check(parsed.blue().affectedExhaustionReasons().equals(Set.of("WALK", "SWIM")),
                "config parser ignored BLUE exhaustion-reason selection");
        checkDouble(1.20D, parsed.neutral().fallDamageMultiplier(),
                "config parser ignored NEUTRAL fall multiplier");
        check(!parsed.neutral().includeNonMonsters() && !parsed.neutral().breakOnDamage()
                        && !parsed.neutral().ignoreEndermanStareAggro()
                        && !parsed.neutral().allowEndermanRetaliation()
                        && parsed.neutral().retaliationMillis() == 17_000L,
                "config parser ignored NEUTRAL truce controls");
        checkDouble(1.50D, parsed.dark().witherDamageMultiplier(),
                "config parser silently capped Wither damage");
        checkDouble(2.50D, parsed.dark().witherDurationMultiplier(),
                "config parser silently capped Wither duration");
        check(!parsed.dark().ambientUndead().breakOnDamage()
                        && parsed.dark().ambientUndead().retaliationMillis() == 23_000L,
                "config parser ignored DARK ambient retaliation controls");
        checkDouble(31.5D, parsed.dark().ambientUndead().alertNearbyRadius(),
                "config parser ignored DARK ambient alert radius");
        check(!parsed.dark().wildUndead().nightOnly()
                        && !parsed.dark().wildUndead().disabledDuringBloodMoon(),
                "config parser ignored DARK wild-world gates");
        checkDouble(0.65D, parsed.dark().wildUndead().targetCancelChance(),
                "config parser ignored DARK wild target chance");
        check(!parsed.dark().exclusions().corruption() && !parsed.dark().exclusions().questMobs(),
                "config parser ignored DARK exclusion switches");
        check(parsed.dark().combatMarkerKeys().equals(Set.of("icesmp:custom_combat"))
                        && parsed.dark().questMarkerKeys().equals(Set.of("icesmp:custom_quest")),
                "config parser ignored custom combat marker sets");
        checkDouble(0.45D, parsed.whisper().targetCancelChance(),
                "config parser ignored Whisper target chance");
        check(parsed.whisper().retaliationMillis() == 19_000L,
                "config parser ignored Whisper retaliation duration");
        checkDouble(0.15D, parsed.whisper().witnessChance(),
                "config parser ignored Whisper witness chance");
        checkDouble(27.0D, parsed.whisper().witnessRadius(),
                "config parser ignored Whisper witness radius");
        checkDouble(3.5D, parsed.whisper().witnessSuspicion(),
                "config parser ignored Whisper witness suspicion");

        live.set("factions.passives.red.fire-damage-multiplier", 3.25D);
        live.set("factions.passives.blue.natural-exhaustion-save-chance", 0.40D);
        harness.config().reload();
        checkDouble(3.25D, harness.config().snapshot().red().fireDamageMultiplier(),
                "reload retained a stale RED multiplier");
        checkDouble(0.40D, harness.config().snapshot().blue().naturalExhaustionSaveChance(),
                "reload retained a stale BLUE chance");

        final YamlConfiguration migrated = new YamlConfiguration();
        migrated.set("factions.passives.blue.natural-exhaustion-save-chance", 0.25D);
        migrated.set("factions.passives.blue-hunger-slow-chance", 0.42D);
        final ConfigHarness legacyOverride = config(migrated,
                Set.of("factions.passives.blue-hunger-slow-chance"));
        checkDouble(0.42D, legacyOverride.config().snapshot().blue().naturalExhaustionSaveChance(),
                "legacy BLUE override did not beat the bundled new default");
        check(legacyOverride.warnings().contains("Legacy faction-passive override"),
                "legacy BLUE override did not emit a migration warning");

        final ConfigHarness bothOverrides = config(migrated, Set.of(
                "factions.passives.blue-hunger-slow-chance",
                "factions.passives.blue.natural-exhaustion-save-chance"));
        checkDouble(0.25D, bothOverrides.config().snapshot().blue().naturalExhaustionSaveChance(),
                "new BLUE override did not take precedence over the legacy key");

        final YamlConfiguration invalid = new YamlConfiguration();
        invalid.set("factions.passives.red.fire-damage-multiplier", -0.1D);
        invalid.set("factions.passives.red.fire-tick-damage-multiplier", "not-a-number");
        invalid.set("factions.passives.blue.natural-exhaustion-save-chance", 1.1D);
        invalid.set("factions.passives.blue.affected-exhaustion-reasons",
                List.of("SPRINT", "NOT_A_REASON"));
        invalid.set("factions.passives.neutral.passive-mob-truce.additional-entity-types",
                List.of("PIGLIN", "NOT_A_MOB"));
        invalid.set("factions.passives.neutral.passive-mob-truce.retaliation-seconds", 1.5D);
        invalid.set("factions.passives.dark.wither.duration-multiplier", Double.NaN);
        invalid.set("factions.passives.dark.ambient-undead.alert-nearby-radius", -4.0D);
        invalid.set("factions.passives.dark.ambient-undead.retaliation-seconds", Long.MAX_VALUE);
        invalid.set("factions.passives.dark.exclusions.combat-marker-keys",
                List.of("icesmp:event_mob", "not a namespaced key"));
        final ConfigHarness rejected = config(invalid, Set.of());
        final FactionPassiveSettings safe = rejected.config().snapshot();
        checkDouble(1.0D, safe.red().fireDamageMultiplier(),
                "negative multiplier did not fail to vanilla damage");
        checkDouble(1.0D, safe.red().fireTickDamageMultiplier(),
                "wrong-type multiplier did not fail to vanilla damage");
        checkDouble(0.0D, safe.blue().naturalExhaustionSaveChance(),
                "out-of-domain chance did not disable the chance branch");
        check(safe.blue().affectedExhaustionReasons().equals(Set.of("SPRINT")),
                "unknown exhaustion reason was not filtered");
        check(safe.neutral().additionalNeutralEntityTypes().equals(Set.of("PIGLIN")),
                "unknown neutral entity type was not filtered");
        check(safe.neutral().retaliationMillis() == 0L,
                "fractional retaliation duration was silently truncated instead of disabled");
        checkDouble(1.0D, safe.dark().witherDurationMultiplier(),
                "NaN Wither multiplier did not fail to vanilla duration");
        checkDouble(0.0D, safe.dark().ambientUndead().alertNearbyRadius(),
                "negative alert radius was not disabled");
        check(safe.dark().ambientUndead().retaliationMillis() == 0L,
                "overflowing retaliation duration was not disabled");
        check(safe.dark().combatMarkerKeys().equals(Set.of("icesmp:event_mob")),
                "invalid combat marker was not filtered");
        for (final String path : List.of(
                "red.fire-damage-multiplier", "red.fire-tick-damage-multiplier",
                "blue.natural-exhaustion-save-chance", "affected-exhaustion-reasons",
                "additional-entity-types", "retaliation-seconds", "wither.duration-multiplier",
                "alert-nearby-radius", "combat-marker-keys")) {
            check(rejected.warnings().contains(path), "invalid config warning omitted path: " + path);
        }
        check(rejected.warnings().contains("nem clampeli"),
                "invalid numeric config warning no longer explains the no-clamp policy");
    }

    private static void packagedConfigDocumentsEveryLiveControl() throws IOException {
        final Path path = Path.of("src/main/resources/config/factions.yml");
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        final List<String> required = List.of(
                "factions.passives.enabled",
                "factions.passives.red.enabled",
                "factions.passives.red.fire-damage-multiplier",
                "factions.passives.red.fire-tick-damage-multiplier",
                "factions.passives.red.entity-fire-damage-multiplier",
                "factions.passives.red.lava-damage-multiplier",
                "factions.passives.red.hot-floor-damage-multiplier",
                "factions.passives.red.affect-icesmp-fire-magic",
                "factions.passives.red.fire-magic-damage-multiplier",
                "factions.passives.red.affect-scripted-combat-fire",
                "factions.passives.blue.enabled",
                "factions.passives.blue.freeze-damage-multiplier",
                "factions.passives.blue.drowning-damage-multiplier",
                "factions.passives.blue.natural-exhaustion-save-chance",
                "factions.passives.blue.affected-exhaustion-reasons",
                "factions.passives.neutral.enabled",
                "factions.passives.neutral.fall-damage-multiplier",
                "factions.passives.neutral.passive-mob-truce.enabled",
                "factions.passives.neutral.passive-mob-truce.include-non-monsters",
                "factions.passives.neutral.passive-mob-truce.additional-entity-types",
                "factions.passives.neutral.passive-mob-truce.break-on-damage",
                "factions.passives.neutral.passive-mob-truce.retaliation-seconds",
                "factions.passives.neutral.enderman.ignore-stare-aggro",
                "factions.passives.neutral.enderman.allow-retaliation",
                "factions.passives.dark.enabled",
                "factions.passives.dark.wither.damage-enabled",
                "factions.passives.dark.wither.damage-multiplier",
                "factions.passives.dark.wither.duration-enabled",
                "factions.passives.dark.wither.duration-multiplier",
                "factions.passives.dark.ambient-undead.enabled",
                "factions.passives.dark.ambient-undead.break-on-damage",
                "factions.passives.dark.ambient-undead.retaliation-seconds",
                "factions.passives.dark.ambient-undead.alert-nearby-radius",
                "factions.passives.dark.wild-undead.enabled",
                "factions.passives.dark.wild-undead.night-only",
                "factions.passives.dark.wild-undead.target-cancel-chance",
                "factions.passives.dark.wild-undead.disabled-during-blood-moon",
                "factions.passives.dark.exclusions.corruption",
                "factions.passives.dark.exclusions.dungeon",
                "factions.passives.dark.exclusions.invasion",
                "factions.passives.dark.exclusions.world-boss",
                "factions.passives.dark.exclusions.event-mobs",
                "factions.passives.dark.exclusions.quest-mobs",
                "factions.passives.dark.exclusions.crown-curse",
                "factions.passives.dark.exclusions.combat-marker-keys",
                "factions.passives.dark.exclusions.quest-marker-keys",
                "factions.whisper.night-undead-truce",
                "factions.whisper.night-undead-night-only",
                "factions.whisper.night-undead-target-cancel-chance",
                "factions.whisper.night-undead-disabled-during-blood-moon",
                "factions.whisper.night-undead-break-on-damage",
                "factions.whisper.night-undead-retaliation-seconds",
                "factions.whisper.truce-witness-chance",
                "factions.whisper.truce-witness-radius",
                "factions.whisper.truce-witness-suspicion");
        for (final String key : required) {
            check(yaml.isSet(key), "packaged faction-passive key missing: " + key);
        }
        check(!yaml.isSet("factions.passives.blue-hunger-slow-chance"),
                "legacy BLUE key must not shadow the new default on fresh installs");
        final String source = Files.readString(path);
        check(source.contains("blue-hunger-slow-chance") && source.contains("migráció"),
                "legacy BLUE fallback is not documented beside the new key");
        checkDouble(0.25D, yaml.getDouble("factions.passives.red.fire-damage-multiplier"),
                "packaged RED default changed unexpectedly");
        checkDouble(0.25D,
                yaml.getDouble("factions.passives.blue.natural-exhaustion-save-chance"),
                "packaged BLUE exhaustion default changed unexpectedly");
        checkDouble(0.50D,
                yaml.getDouble("factions.passives.dark.wild-undead.target-cancel-chance"),
                "packaged DARK wild chance changed unexpectedly");
    }

    /**
     * Source-level integration contracts supplement the behavioural policy tests above. They
     * deliberately guard the Bukkit-heavy consumers which cannot be instantiated without a
     * server, so a later fallback refactor cannot silently turn a guest into a NEUTRAL citizen.
     */
    private static void integrationBenefitsRequireExplicitCitizenship() throws IOException {
        final String manager = read(
                "src/main/java/hu/taliann/icesmp/managers/FactionManager.java");
        check(manager.contains("public Optional<FactionType> getChosenFaction(final UUID uuid)")
                        && manager.contains("public boolean isEligibleForFactionBenefits(final UUID uuid)")
                        && manager.contains("public boolean isMember(final UUID uuid, final FactionType faction)"),
                "the central explicit-membership API is incomplete");
        check(manager.contains("_membership-history")
                        && manager.contains("hasEverChosenFaction")
                        && manager.contains("getLastChosenFaction"),
                "durable membership history no longer closes assignment-removal bypasses");

        final String join = read(
                "src/main/java/hu/taliann/icesmp/commands/faction/FactionJoinSubcommand.java");
        check(join.contains("factionManager.hasEverChosenFaction(player)")
                        && join.contains("FactionSwitchRules.passesSeasonRules(")
                        && join.contains("FactionSwitchRules.passesNeutralCapitalGate("),
                "season lockout or switch limit can be skipped from a missing assignment");
        final String leave = read(
                "src/main/java/hu/taliann/icesmp/commands/faction/FactionLeaveSubcommand.java");
        check(leave.contains("FactionType.NEUTRAL") && !leave.contains("removeFaction("),
                "leave recreated assignment-free first-choice state");

        final String quests = read(
                "src/main/java/hu/taliann/icesmp/managers/QuestManager.java");
        check(quests.contains("isStillFactionEligible")
                        && quests.contains("factionManager.isMember(")
                        && quests.contains("factionManager.getChosenFaction(player.getUniqueId())")
                        && quests.contains("setFaction(player.getUniqueId(), FactionType.NEUTRAL)"),
                "faction quests, OWN rewards or redemption no longer fail closed on membership");
        final String community = read(
                "src/main/java/hu/taliann/icesmp/managers/CommunityGoalManager.java");
        check(community.contains("final FactionType playerFaction = factionManager.getChosenFaction(")
                        && community.contains("if (playerFaction == null)")
                        && community.contains("factionManager.isMember("),
                "guest contribution or faction-only community buffs regained a NEUTRAL fallback");
        final String council = read(
                "src/main/java/hu/taliann/icesmp/managers/CouncilManager.java");
        check(council.contains("factionManager.isMember(voter.getUniqueId(), FactionType.NEUTRAL)")
                        && council.contains("onMembershipChange(final UUID playerId)"),
                "council voting or stale candidacy is no longer tied to explicit NEUTRAL citizenship");

        final String food = read(
                "src/main/java/hu/taliann/icesmp/listeners/FactionFoodListener.java");
        check(food.contains("factionManager.getChosenFaction(player.getUniqueId()).orElse(null)")
                        && food.contains("final boolean homeFood = faction != null && switch (faction)"),
                "food duty or signature-food faction matching regained an implicit assignment");
        final String relic = read(
                "src/main/java/hu/taliann/icesmp/listeners/ElytraRelicListener.java");
        final String soulstone = read(
                "src/main/java/hu/taliann/icesmp/listeners/SoulstoneListener.java");
        check(relic.contains("factionManager.isMember(")
                        && soulstone.contains("factionManager.isMember(kill.killerId(), FactionType.DARK)"),
                "signature/relic or DARK soulstone gates no longer require explicit membership");

        final String corruption = read(
                "src/main/java/hu/taliann/icesmp/managers/CorruptionManager.java");
        final String worldBoss = read(
                "src/main/java/hu/taliann/icesmp/managers/WorldBossManager.java");
        check(corruption.contains("FactionCombatMarkers.CORRUPTION_MOB")
                        && corruption.contains("getChosenFaction(cleanser.getUniqueId()).ifPresent"),
                "corruption targeting or season credit lost its combat/membership gate");
        check(worldBoss.contains("FactionCombatMarkers.EVENT_MOB")
                        && worldBoss.contains("getChosenFaction(")
                        && worldBoss.contains("if (faction != null)"),
                "world-boss adds or treasury/season credit lost event/membership gating");
        final String crown = read(
                "src/main/java/hu/taliann/icesmp/managers/CrownCurseManager.java");
        check(crown.contains("FactionCombatMarkers.CROWN_CURSE_TARGET")
                        && crown.indexOf("FactionCombatMarkers.CROWN_CURSE_TARGET")
                        < crown.indexOf("mob.setTarget(king)"),
                "crown-curse combat intent is not marked before target assignment");
    }

    private static void verifiesAdapterAndLifecycleWiring() throws IOException {
        final String listener = read("src/main/java/hu/taliann/icesmp/listeners/FactionPassiveListener.java");
        check(listener.contains("EntityExhaustionEvent")
                        && !listener.contains("FoodLevelChangeEvent"),
                "BLUE passive returned to blanket food-level cancellation");
        check(listener.contains("SpellDamageUtil.schoolOf")
                        && listener.contains("ICE_SMP_FIRE_MAGIC")
                        && listener.contains("RED_ENTITY_FIRE"),
                "RED adapter no longer separates TUZ and entity fire channels");
        check(listener.contains("ProjectileLaunchEvent")
                        && listener.contains("isScriptedCombatFire")
                        && listener.contains("Bukkit.isOwnedByCurrentRegion(source)"),
                "scripted projectile fire lost provenance or performed foreign entity reads");
        check(listener.contains("getScheduler().run(plugin")
                        && !listener.contains("Bukkit.getScheduler()"),
                "nearby DARK retaliation is not dispatched through an entity scheduler");
        check(listener.contains("alertNearbyRadius()")
                        && listener.contains("canAlertDarkUndead(")
                        && listener.contains("final FactionPassiveSettings liveSettings = config.snapshot()")
                        && listener.contains("state.isDarkRetaliating(playerId)"),
                "ambient undead alert ignored radius, live config, membership or retaliation cleanup");
        final int alertTracking = listener.indexOf(
                "target != null && trackRetaliationTarget(playerId, mob, true)");
        check(alertTracking >= 0
                        && listener.indexOf("mob.setTarget(target)", alertTracking) > alertTracking,
                "queued DARK alert mutates target before cleanup can observe its tracking entry");
        check(listener.contains("final io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled")
                        && listener.contains("if (scheduled == null)"),
                "retired entity schedulers can leak tracked retaliation state");

        final String resolver = read(
                "src/main/java/hu/taliann/icesmp/factions/FactionMobContextResolver.java");
        check(!resolver.contains("getPersistentDataContainer().set("),
                "the passive resolver mutates entity PDC instead of remaining read-only");
        check(resolver.contains("!contexts.contains(FactionPassivePolicy.ContentContext.CROWN_CURSE)"),
                "crown-curse targets collapse into an unconditional CUSTOM force reason");

        final String core = read("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java");
        check(core.contains("factionPassiveConfig.reload()")
                        && core.contains("factionPassiveListener.clearAllState()"),
                "config reload does not refresh the passive snapshot and clear transient state");
        check(core.contains("key.startsWith(\"factions.passives.\")")
                        && core.contains("key.startsWith(\"factions.whisper.\")"),
                "live admin config edits do not refresh faction-passive settings");
        check(core.contains("setMembershipChangeHook(playerId ->")
                        && core.contains("factionPassiveListener.clearPlayerState(playerId)"),
                "faction switch does not clear player-scoped passive state");
        check(core.contains("new PlayerSessionCleanupListener(")
                        && core.contains("factionManager,\n                factionPassiveListener,"),
                "quit/kick lifecycle did not register the faction-passive state owner");
        check(core.contains("public void disable()")
                        && core.indexOf("factionPassiveListener.clearAllState()",
                        core.indexOf("public void disable()")) > 0,
                "plugin disable does not clear faction-passive transient state");

        final String factionSet = read(
                "src/main/java/hu/taliann/icesmp/commands/faction/FactionSetSubcommand.java");
        check(factionSet.contains("final io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled")
                        && factionSet.contains("if (scheduled == null)"),
                "an immediately retired player can silently lose an admin DARK transition");

        final String cleanup = read(
                "src/main/java/hu/taliann/icesmp/listeners/PlayerSessionCleanupListener.java");
        check(cleanup.contains("PlayerStateCleanup") && cleanup.contains("clearPlayerState(playerId)"),
                "quit/kick cleanup no longer invokes registered player-state owners");
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
                        new FactionPassiveSettings.AmbientUndead(true, true, 60_000L, 16.0D),
                        new FactionPassiveSettings.WildUndead(true, true, 0.50D, true),
                        new FactionPassiveSettings.Exclusions(
                                true, true, true, true, true, true, true),
                        Set.of("icesmp:scripted_combat", "icesmp:event_mob", "icesmp:minion_owner"),
                        Set.of("icesmp:quest_mob")),
                new FactionPassiveSettings.Whisper(
                        true, true, 0.35D, true, true, 60_000L, 0.02D, 16.0D, 1.0D));
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
                !enderman, enderman, false, !enderman, false, false);
    }

    private static FactionPassivePolicy.TargetContext neutralRetaliation(final boolean enderman) {
        return target(false, Set.of(), true, false, true, false, false,
                !enderman, enderman, false, false, false, false);
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
                true, true, true, true, false, false);
    }

    private static FactionPassivePolicy.TargetContext endermanRetaliation() {
        return target(false, Set.of(), true, false, true, false, false,
                true, true, false, false, false, false);
    }

    private static FactionPassivePolicy.TargetContext darkAmbient(final boolean retaliation,
                                                                 final boolean bloodMoon) {
        return target(false, Set.of(), retaliation, bloodMoon, true, true, true,
                false, false, false, false, true, false);
    }

    private static FactionPassivePolicy.TargetContext darkWild(final boolean night,
                                                              final boolean bloodMoon) {
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

    private static FactionPassivePolicy.TargetContext whisperWild(final boolean retaliation,
                                                                 final boolean night,
                                                                 final boolean bloodMoon) {
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
        setField(manager, "configuration", configuration);
        setField(manager, "overridePaths", Set.copyOf(overridePaths));
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
