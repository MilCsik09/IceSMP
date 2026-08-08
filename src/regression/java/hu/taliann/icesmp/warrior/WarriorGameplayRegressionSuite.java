package hu.taliann.icesmp.warrior;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Dependency-free behavior regression for the concrete Harcos runtime state. */
public final class WarriorGameplayRegressionSuite {

    private static int assertions;

    private WarriorGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        battleTempoThresholdsAndDecay();
        battleTempoTargetSwitchAndFinishers();
        berserkerFuryExhaustionAndSafeDump();
        berserkerFractionalPollingPreservesElapsedTime();
        berserkerOverdriveAndAftermath();
        guardianGuardAndOathLifecycle();
        specializationAndDeathCleanup();
        reviewBlockerSourceContracts();
        System.out.println("Warrior gameplay regression suite passed. assertions=" + assertions);
    }

    private static void battleTempoThresholdsAndDecay() {
        final WarriorCombatState state = new WarriorCombatState();
        final long t0 = 10_000L;
        check(state.battleTempo(t0, 5_000L, 10.0D) == 0, "tempo starts empty");
        check(state.tempoTier(t0, 35, 75, 5_000L, 10.0D)
                == WarriorCombatState.TempoTier.RENDEZETT, "empty tempo is orderly");

        state.addTempo(34, t0, 5_000L, 10.0D);
        check(state.tempoTier(t0, 35, 75, 5_000L, 10.0D)
                == WarriorCombatState.TempoTier.RENDEZETT, "34 remains orderly");
        state.addTempo(1, t0, 5_000L, 10.0D);
        check(state.tempoTier(t0, 35, 75, 5_000L, 10.0D)
                == WarriorCombatState.TempoTier.HEVES, "35 enters heated");
        state.addTempo(40, t0, 5_000L, 10.0D);
        check(state.tempoTier(t0, 35, 75, 5_000L, 10.0D)
                == WarriorCombatState.TempoTier.TULCSORDULO, "75 enters overflowing");
        state.addTempo(500, t0, 5_000L, 10.0D);
        check(state.battleTempo(t0, 5_000L, 10.0D) == 100, "tempo is bounded at 100");

        check(state.battleTempo(t0 + 4_999L, 5_000L, 10.0D) == 100,
                "short combat break does not decay tempo");
        check(state.battleTempo(t0 + 7_000L, 5_000L, 10.0D) == 80,
                "tempo decays lazily after grace window");
    }

    private static void battleTempoTargetSwitchAndFinishers() {
        final WarriorCombatState state = new WarriorCombatState();
        final UUID targetA = UUID.randomUUID();
        final UUID targetB = UUID.randomUUID();
        final long t0 = 50_000L;
        state.addTempoFromAttack(targetA, 7, 10, 1_500L, t0, 5_000L, 0.0D);
        check(state.battleTempo(t0, 5_000L, 0.0D) == 7,
                "first target only grants normal attack tempo");
        state.addTempoFromAttack(targetB, 7, 10, 1_500L, t0 + 200L, 5_000L, 0.0D);
        check(state.battleTempo(t0 + 200L, 5_000L, 0.0D) == 24,
                "meaningful target switch adds one bounded bonus");
        state.addTempoFromAttack(targetA, 7, 10, 1_500L, t0 + 500L, 5_000L, 0.0D);
        check(state.battleTempo(t0 + 500L, 5_000L, 0.0D) == 31,
                "target-switch bonus respects cooldown");

        state.addTempo(69, t0 + 500L, 5_000L, 0.0D);
        check(state.battleTempo(t0 + 500L, 5_000L, 0.0D) == 100,
                "setup reaches overflow");
        state.finishTempo(25);
        check(state.battleTempo(t0 + 500L, 5_000L, 0.0D) == 25,
                "overflow finisher may retain partial class tempo");
        state.finishTempo(0);
        check(state.battleTempo(t0 + 500L, 5_000L, 0.0D) == 0,
                "normal finisher can fully vent class tempo");
    }

    private static void berserkerFuryExhaustionAndSafeDump() {
        final WarriorCombatState state = new WarriorCombatState();
        final long t0 = 100_000L;
        state.addBloodFrenzy(50, t0, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25);
        check(state.bloodFrenzy(t0, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25) == 50,
                "damage builds Blood Frenzy");
        state.addBloodFrenzy(20, t0, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25);
        check(state.bloodFrenzy(t0, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25) == 70,
                "70 opens high-fury state");
        check(state.exhaustion(t0 + 2_000L, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25) == 10,
                "high Blood Frenzy builds predictable exhaustion");

        state.safeDump(55, 30);
        check(state.bloodFrenzy(t0 + 2_000L, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25) == 15,
                "safe dump vents Blood Frenzy");
        check(state.exhaustion(t0 + 2_000L, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25) == 0,
                "safe dump controls exhaustion");
        state.addBloodFrenzy(200, t0 + 2_000L, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25);
        check(state.bloodFrenzy(t0 + 2_000L, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25) == 100,
                "Blood Frenzy is bounded at 100");
    }

    private static void berserkerFractionalPollingPreservesElapsedTime() {
        final WarriorCombatState state = new WarriorCombatState();
        final long t0 = 150_000L;
        state.addBloodFrenzy(70, t0, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25);
        for (int i = 1; i <= 200; i++) {
            state.exhaustion(t0 + i * 50L, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25);
        }
        check(state.exhaustion(t0 + 10_000L, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25) == 50,
                "50 ms polling preserves ten seconds of high-fury exhaustion");

        state.safeDump(100, 0);
        for (int i = 201; i <= 220; i++) {
            state.exhaustion(t0 + i * 50L, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25);
        }
        check(state.exhaustion(t0 + 11_000L, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25) == 43,
                "50 ms polling preserves one second of exhaustion recovery");
    }

    private static void berserkerOverdriveAndAftermath() {
        final WarriorCombatState state = new WarriorCombatState();
        final long t0 = 200_000L;
        state.addBloodFrenzy(80, t0, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25);
        check(state.startOverdrive(t0, 70, 70, 5_000L,
                        5.0D, 7.0D, 9.0D, 7_000L, 25),
                "overdrive starts only from controlled high Fury");
        check(state.overdriveActive(t0 + 4_999L), "overdrive stays active for configured window");
        check(!state.startOverdrive(t0 + 1_000L, 70, 70, 5_000L,
                        5.0D, 7.0D, 9.0D, 7_000L, 25),
                "overdrive cannot be nested");
        state.refreshBerserker(t0 + 5_100L, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25);
        check(!state.overdriveActive(t0 + 5_100L), "overdrive ends deterministically");
        check(state.aftermathActive(t0 + 5_100L), "overdrive creates a cooling aftermath");
        check(state.exhaustion(t0 + 5_100L, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25) == 70,
                "crossing overdrive end integrates active time before end exhaustion");
        check(!state.startOverdrive(t0 + 6_000L, 70, 100, 5_000L,
                        5.0D, 7.0D, 9.0D, 7_000L, 25),
                "aftermath prevents immediate re-overdrive");
        state.forceMaximumExhaustion();
        check(state.exhaustion(t0 + 6_000L, 70, 5.0D, 0.0D, 9.0D, 7_000L, 25) == 100,
                "Defiant consequence can force full exhaustion");
    }

    private static void guardianGuardAndOathLifecycle() {
        final WarriorCombatState state = new WarriorCombatState();
        state.addGuard(40);
        check(state.guard() == 40, "Guardian builds Guard");
        state.addGuard(1000);
        check(state.guard() == 100, "Guard is bounded at 100");
        check(state.spendGuard(60), "Guard can fund protection");
        check(state.guard() == 40, "Guard spend is exact");
        check(!state.spendGuard(41), "Guard cannot spend more than available");
        check(state.guard() == 40, "failed Guard spend is side-effect free");

        final UUID oath = UUID.randomUUID();
        state.setOathTarget(oath, "Karaván");
        check(state.oathTargetId().orElseThrow().equals(oath), "Guardian tracks one oath target id");
        check(state.oathTargetLabel().equals("Karaván"), "Guardian tracks readable oath target label");
        state.setOathTarget(UUID.randomUUID(), "Kapuvédő");
        check(state.oathTargetLabel().equals("Kapuvédő"), "new oath replaces old oath instead of stacking");
    }

    private static void specializationAndDeathCleanup() {
        final WarriorCombatState state = new WarriorCombatState();
        final long now = 300_000L;
        state.addTempo(80, now, 5_000L, 0.0D);
        state.addBloodFrenzy(80, now, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25);
        state.addGuard(70);
        state.setOathTarget(UUID.randomUUID(), "Eskütárs");
        state.clearSpecializationState();
        check(state.battleTempo(now, 5_000L, 0.0D) == 80,
                "spec switch preserves class-common Csatatempó");
        check(state.bloodFrenzy(now, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25) == 0,
                "spec switch removes Berserker Fury");
        check(state.exhaustion(now, 70, 5.0D, 7.0D, 9.0D, 7_000L, 25) == 0,
                "spec switch removes Berserker Exhaustion");
        check(state.guard() == 0, "spec switch removes Guardian Guard");
        check(state.oathTargetId().isEmpty(), "spec switch removes Guardian oath target");

        state.addTempo(50, now, 5_000L, 0.0D);
        state.addGuard(50);
        state.clearAll();
        check(state.battleTempo(now, 5_000L, 0.0D) == 0, "death/logout cleanup removes Csatatempó");
        check(state.guard() == 0, "death/logout cleanup removes Guard");
    }

    private static void reviewBlockerSourceContracts() throws Exception {
        final String warriorService = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/warrior/WarriorGameplayService.java"));
        check(!warriorService.contains("void onLethalDamage"),
                "Defiant no longer prevents lethal damage before durable reservation");
        final int defiant = warriorService.indexOf("private void tryDefiantRecovery");
        final int reserve = warriorService.indexOf("cooldowns.reserve", defiant);
        final int recovery = warriorService.indexOf("player.getScheduler().run", reserve);
        check(defiant >= 0 && reserve > defiant && recovery > reserve,
                "Defiant durable cooldown reservation precedes recovery side effect");

        final String gateway = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/DefaultClassSpecProfileGateway.java"));
        check(gateway.contains("\"warrior\".equals(p.primaryClassId())&&level>=r.secondSpecUnlockLevel()"),
                "second-slot XP unlock is Warrior-only in this vertical slice");
        check(gateway.contains("second-slot gameplay is currently enabled only for warrior"),
                "SECOND-slot learning is rejected for unreworked classes");
        check(gateway.contains("loadout switching is currently enabled only for warrior"),
                "gateway loadout switching is rejected for unreworked classes");

        final String protection = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/CatalystProtectionListener.java"));
        check(protection.contains("InventoryClickEvent") && protection.contains("InventoryDragEvent"),
                "Soulbond protects external inventory transfer paths");
        check(protection.contains("event.getItemsToKeep().add(drop)"),
                "Soulbond death retention uses the same-event itemsToKeep path");
        check(!protection.contains("PlayerProfileDeathEscrowStore"),
                "Soulbond death retention has no claim/materialize/redeposit crash window");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
