package hu.taliann.icesmp.shaman;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free behavior regression for the concrete Sámán runtime state. */
public final class ShamanGameplayRegressionSuite {

    private static int assertions;

    private ShamanGameplayRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        overloadChargeAndRetention();
        maelstromRhythmAndBlessingAlternation();
        maelstromSpendAndCapstoneVent();
        tideFlowThresholdsAndRetention();
        cleanupLifecycle();
        totemWheelAndAllowlistSourceContracts();
        System.out.println("Shaman gameplay regression suite passed. assertions=" + assertions);
    }

    private static void overloadChargeAndRetention() {
        final ShamanCombatState state = new ShamanCombatState();
        check(state.chargeOverload(1, 4) == 1, "resonant cast charges the overload");
        state.chargeOverload(1, 4);
        state.chargeOverload(1, 4);
        check(!state.isOverloadArmed(4), "three of four charges is not yet armed");
        state.chargeOverload(1, 4);
        check(state.isOverloadArmed(4), "the threshold arms the overload");
        check(state.chargeOverload(5, 4) == 4, "the charge is bounded at the threshold");
        state.consumeOverload(0);
        check(state.overload() == 0, "spent overload vents fully");
        check(!state.isOverloadArmed(4), "spent overload cannot double-fire");
        state.chargeOverload(4, 4);
        state.consumeOverload(2);
        check(state.overload() == 2, "retention doctrine keeps partial charge");
    }

    private static void maelstromRhythmAndBlessingAlternation() {
        final ShamanCombatState state = new ShamanCombatState();
        final long t0 = 10_000L;
        check(state.recordMeleeHit(t0, 600L, 1_600L, 4, 4) == 4,
                "the first hit earns only the base amount");
        check(state.blessingSide() == ShamanCombatState.BlessingSide.VIHAR,
                "the first hit does not alternate the blessing");
        check(state.recordMeleeHit(t0 + 1_000L, 600L, 1_600L, 4, 4) == 8,
                "a hit inside the rhythm window earns the bonus");
        check(state.blessingSide() == ShamanCombatState.BlessingSide.FOLD,
                "the rhythm hit alternates the blessing side");
        check(state.recordMeleeHit(t0 + 1_200L, 600L, 1_600L, 4, 4) == 4,
                "a spammed hit earns only the base amount");
        check(state.blessingSide() == ShamanCombatState.BlessingSide.FOLD,
                "a spammed hit does not alternate the blessing");
        check(state.recordMeleeHit(t0 + 5_000L, 600L, 1_600L, 4, 4) == 4,
                "a late hit earns only the base amount");
        check(state.maelstrom() == 20, "the Maelstrom accumulates exactly");
    }

    private static void maelstromSpendAndCapstoneVent() {
        final ShamanCombatState state = new ShamanCombatState();
        final long t0 = 50_000L;
        for (int i = 0; i < 30; i++) {
            state.recordMeleeHit(t0 + i * 1_000L, 600L, 1_600L, 4, 4);
        }
        check(state.maelstrom() == 100, "the Maelstrom is bounded at 100");
        check(state.spendMaelstrom(30), "the Maelstrom funds spec spells");
        check(state.maelstrom() == 70, "the spend is exact");
        check(!state.spendMaelstrom(71), "the Maelstrom cannot overspend");
        check(state.ventMaelstrom(0) == 70, "the capstone vents everything");
        check(state.maelstrom() == 0, "the vent leaves nothing by default");
        state.recordMeleeHit(t0 + 60_000L, 600L, 1_600L, 4, 0);
        for (int i = 1; i <= 30; i++) {
            state.recordMeleeHit(t0 + 60_000L + i * 1_000L, 600L, 1_600L, 4, 4);
        }
        final int before = state.maelstrom();
        final int vented = state.ventMaelstrom(25);
        check(vented == before - 25 && state.maelstrom() == 25,
                "the level-50 doctrine retains part of the vented Maelstrom");
    }

    private static void tideFlowThresholdsAndRetention() {
        final ShamanCombatState state = new ShamanCombatState();
        check(state.pushTide(20) == 20, "a direct heal pushes toward Dagály");
        state.pushTide(20);
        check(!state.isHighTide(60), "below the threshold there is no Dagály yet");
        state.pushTide(20);
        check(state.isHighTide(60), "the threshold reaches Dagály");
        check(state.pushTide(1000) == 100, "the tide is bounded at +100");
        state.consumeTide(0);
        check(state.tide() == 0, "a consumed tide flows back to the middle");
        state.pushTide(-80);
        check(state.isLowTide(60), "chain heals reach Apály on the other side");
        check(state.pushTide(-1000) == -100, "the tide is bounded at -100");
        state.consumeTide(25);
        check(state.tide() == -25, "retention doctrine keeps part of the momentum");
    }

    private static void cleanupLifecycle() {
        final ShamanCombatState state = new ShamanCombatState();
        final long t0 = 100_000L;
        state.chargeOverload(4, 4);
        state.recordMeleeHit(t0, 600L, 1_600L, 4, 4);
        state.recordMeleeHit(t0 + 1_000L, 600L, 1_600L, 4, 4);
        state.pushTide(70);
        state.clearSpecializationState();
        check(state.overload() == 0, "spec switch clears the overload");
        check(state.maelstrom() == 0, "spec switch clears the Maelstrom");
        check(state.tide() == 0, "spec switch clears the tide");
        check(state.blessingSide() == ShamanCombatState.BlessingSide.VIHAR,
                "spec switch resets the blessing side");
        state.pushTide(30);
        state.clearAll();
        check(state.tide() == 0, "death/logout cleanup clears everything");
    }

    private static void totemWheelAndAllowlistSourceContracts() throws Exception {
        final String policy = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/classspec/application/GameplayV2ClassPolicy.java"));
        check(policy.contains("\"shaman\""),
                "the gameplay-v2 allowlist still admits this completed slice");

        final String totems = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/TotemManager.java"));
        check(totems.contains("enum TotemCategory") && totems.contains("category()"),
                "every totem type belongs to a Totemkerék category");
        check(totems.contains("owned.get(type.category())")
                        && totems.contains("removeTotem(oldStand)"),
                "placing a same-category totem replaces the previous one");
        check(totems.contains("activeTotemTypes"),
                "the Totemkerék pair is exposed as a runtime projection");
        check(totems.contains("final CastModifiers snapshot")
                        && totems.contains("startPulse(totem, type, snapshot)"),
                "totem creation captures immutable cast modifiers before scheduler hops");
        check(totems.contains("SpellDamageUtil.scaledDamage(damage, modifiers)"),
                "delayed totem damage applies the captured damage multiplier");
        check(totems.contains("type.affect(nearby, durationTicks, modifiers)"),
                "same-region and cross-region pulses both carry the same modifier snapshot");
        check(!totems.contains("monster.damage(damage);"),
                "fixed unscaled totem pulse damage cannot bypass shared power");
        check(totems.contains("totemsByOwner.remove(ownerId)")
                        && totems.contains("stand.getScheduler().run(plugin, task -> removeTotem(stand)"),
                "owner cleanup despawns the live totem on its own entity scheduler");

        final String totemSpell = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/spells/ShamanTotemSpell.java"));
        check(totemSpell.contains("SpellExecutionContext.capture()"),
                "totem spell snapshots modifiers while the synchronous cast context is alive");

        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/shaman/ShamanGameplayService.java"));
        check(service.contains("pair.size() < required"),
                "elemental resonance requires the live Totemkerék pair");
        check(!service.contains("getNearbyEntities") && !service.contains("runAtFixedRate"),
                "no proximity scans or repeating tasks in the shaman class runtime itself");
        check(service.contains("max-power-bonus-percent"),
                "shaman cast bonuses ride the capped shared power pipeline");
        check(service.contains("totemManager.clearOwnerProjection(playerId);"),
                "death/logout/kick class cleanup tears down live totems through TotemManager");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
