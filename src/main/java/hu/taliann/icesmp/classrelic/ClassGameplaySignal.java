package hu.taliann.icesmp.classrelic;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tipizált, immutable szemantikus gameplay-jelzés a Resonance-dispatchhez. A class rework
 * képesség-implementációi EZT a szerződést szolgáltatják: a payload (actor, cél-identitás,
 * mennyiség, tagek) esemény-fajtánként kötött rekord — se {@code Map<String,Object>}, se
 * stringly-typed adat. A payload-hordozó eseményekhez a Generic alak szándékosan nem
 * használható (a fordítási/futásidejű kényszer nélkül a typed contract kiüresedne).
 */
public sealed interface ClassGameplaySignal {

    ClassGameplayEvent event();

    UUID actorId();

    Set<AbilityTag> tags();

    /** Payload-hordozó események: ezekhez kötelező a dedikált rekord-alak. */
    Set<ClassGameplayEvent> PAYLOAD_EVENTS = Set.of(
            ClassGameplayEvent.ABILITY_RESOLVED,
            ClassGameplayEvent.RESOURCE_SPENT,
            ClassGameplayEvent.DAMAGE_DEALT,
            ClassGameplayEvent.DAMAGE_TAKEN,
            ClassGameplayEvent.HEAL_RESOLVED,
            ClassGameplayEvent.OVERHEAL,
            ClassGameplayEvent.SUMMON);

    record AbilityResolved(UUID actorId, String abilityId,
                           Set<AbilityTag> tags) implements ClassGameplaySignal {
        public AbilityResolved {
            Objects.requireNonNull(actorId, "actorId");
            abilityId = requireId(abilityId, "abilityId");
            tags = copyTags(tags);
        }

        @Override
        public ClassGameplayEvent event() {
            return ClassGameplayEvent.ABILITY_RESOLVED;
        }
    }

    record DamageDealt(UUID actorId, UUID targetId, double amount,
                       Set<AbilityTag> tags) implements ClassGameplaySignal {
        public DamageDealt {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(targetId, "targetId");
            amount = requireAmount(amount);
            tags = copyTags(tags);
        }

        @Override
        public ClassGameplayEvent event() {
            return ClassGameplayEvent.DAMAGE_DEALT;
        }
    }

    record DamageTaken(UUID actorId, Optional<UUID> sourceId, double amount,
                       Set<AbilityTag> tags) implements ClassGameplaySignal {
        public DamageTaken {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(sourceId, "sourceId");
            amount = requireAmount(amount);
            tags = copyTags(tags);
        }

        @Override
        public ClassGameplayEvent event() {
            return ClassGameplayEvent.DAMAGE_TAKEN;
        }
    }

    record HealResolved(UUID actorId, UUID targetId, double amount,
                        Set<AbilityTag> tags) implements ClassGameplaySignal {
        public HealResolved {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(targetId, "targetId");
            amount = requireAmount(amount);
            tags = copyTags(tags);
        }

        @Override
        public ClassGameplayEvent event() {
            return ClassGameplayEvent.HEAL_RESOLVED;
        }
    }

    record Overheal(UUID actorId, UUID targetId, double amount,
                    Set<AbilityTag> tags) implements ClassGameplaySignal {
        public Overheal {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(targetId, "targetId");
            amount = requireAmount(amount);
            tags = copyTags(tags);
        }

        @Override
        public ClassGameplayEvent event() {
            return ClassGameplayEvent.OVERHEAL;
        }
    }

    record ResourceSpent(UUID actorId, double amount,
                         Set<AbilityTag> tags) implements ClassGameplaySignal {
        public ResourceSpent {
            Objects.requireNonNull(actorId, "actorId");
            amount = requireAmount(amount);
            tags = copyTags(tags);
        }

        @Override
        public ClassGameplayEvent event() {
            return ClassGameplayEvent.RESOURCE_SPENT;
        }
    }

    record Summon(UUID actorId, String summonId, int count,
                  Set<AbilityTag> tags) implements ClassGameplaySignal {
        public Summon {
            Objects.requireNonNull(actorId, "actorId");
            summonId = requireId(summonId, "summonId");
            if (count < 1) {
                throw new IllegalArgumentException("summon count must be positive: " + count);
            }
            tags = copyTags(tags);
        }

        @Override
        public ClassGameplayEvent event() {
            return ClassGameplayEvent.SUMMON;
        }
    }

    /** Payload nélküli események (BLOCK, DODGE, KILL, …) közös alakja. */
    record Generic(ClassGameplayEvent event, UUID actorId,
                   Set<AbilityTag> tags) implements ClassGameplaySignal {
        public Generic {
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(actorId, "actorId");
            if (PAYLOAD_EVENTS.contains(event)) {
                throw new IllegalArgumentException(
                        "payload-carrying event requires its typed signal record: " + event);
            }
            tags = copyTags(tags);
        }
    }

    private static Set<AbilityTag> copyTags(final Set<AbilityTag> tags) {
        return tags == null ? Set.of() : Set.copyOf(tags);
    }

    private static double requireAmount(final double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D) {
            throw new IllegalArgumentException("signal amount must be finite and non-negative: "
                    + amount);
        }
        return amount;
    }

    private static String requireId(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
