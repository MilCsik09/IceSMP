package hu.taliann.icesmp.factions;

import java.util.Set;

/** Immutable live-config snapshot consumed by the pure faction-passive policy. */
public record FactionPassiveSettings(
        boolean enabled,
        Red red,
        Blue blue,
        Neutral neutral,
        Dark dark,
        Whisper whisper) {

    public record Red(
            boolean enabled,
            double fireDamageMultiplier,
            double fireTickDamageMultiplier,
            double entityFireDamageMultiplier,
            double lavaDamageMultiplier,
            double hotFloorDamageMultiplier,
            boolean affectIceSmpFireMagic,
            double fireMagicDamageMultiplier,
            boolean affectScriptedCombatFire) {
    }

    public record Blue(
            boolean enabled,
            double freezeDamageMultiplier,
            double drowningDamageMultiplier,
            double naturalExhaustionSaveChance,
            Set<String> affectedExhaustionReasons) {

        public Blue {
            affectedExhaustionReasons = Set.copyOf(affectedExhaustionReasons);
        }
    }

    public record Neutral(
            boolean enabled,
            double fallDamageMultiplier,
            boolean passiveMobTruceEnabled,
            boolean includeNonMonsters,
            Set<String> additionalNeutralEntityTypes,
            boolean breakOnDamage,
            long retaliationMillis,
            boolean ignoreEndermanStareAggro,
            boolean allowEndermanRetaliation) {

        public Neutral {
            additionalNeutralEntityTypes = Set.copyOf(additionalNeutralEntityTypes);
        }
    }

    public record Dark(
            boolean enabled,
            boolean witherDamageEnabled,
            double witherDamageMultiplier,
            boolean witherDurationEnabled,
            double witherDurationMultiplier,
            AmbientUndead ambientUndead,
            WildUndead wildUndead,
            Exclusions exclusions,
            Set<String> combatMarkerKeys,
            Set<String> questMarkerKeys) {

        public Dark {
            combatMarkerKeys = Set.copyOf(combatMarkerKeys);
            questMarkerKeys = Set.copyOf(questMarkerKeys);
        }
    }

    public record AmbientUndead(
            boolean enabled,
            boolean breakOnDamage,
            long retaliationMillis,
            double alertNearbyRadius,
            boolean disabledDuringBloodMoon) {
    }

    public record WildUndead(
            boolean enabled,
            boolean nightOnly,
            double targetCancelChance,
            boolean disabledDuringBloodMoon) {
    }

    public record Exclusions(
            boolean corruption,
            boolean dungeon,
            boolean invasion,
            boolean worldBoss,
            boolean eventMobs,
            boolean questMobs,
            boolean crownCurse) {
    }

    public record Whisper(
            boolean enabled,
            boolean nightOnly,
            double targetCancelChance,
            boolean disabledDuringBloodMoon,
            boolean breakOnDamage,
            long retaliationMillis,
            double witnessRadius) {
    }
}
