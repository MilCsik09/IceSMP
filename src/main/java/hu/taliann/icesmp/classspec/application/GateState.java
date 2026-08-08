package hu.taliann.icesmp.classspec.application;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, Bukkit-independent snapshot of the gates protecting a class
 * specialization. A condition which is not required can never seal or unseal a
 * loadout by accident.
 */
public record GateState(
        Condition faction,
        Condition sinner,
        Condition quest) {

    public GateState {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(sinner, "sinner");
        Objects.requireNonNull(quest, "quest");
    }

    public static GateState satisfied() {
        return new GateState(Condition.SATISFIED, Condition.SATISFIED, Condition.SATISFIED);
    }

    public static GateState ofRequirements(
            final boolean factionRequired,
            final boolean factionSatisfied,
            final boolean sinnerRequired,
            final boolean sinnerSatisfied,
            final boolean questRequired,
            final boolean questSatisfied) {
        return new GateState(
                Condition.of(factionRequired, factionSatisfied),
                Condition.of(sinnerRequired, sinnerSatisfied),
                Condition.of(questRequired, questSatisfied));
    }

    public boolean allSatisfied() {
        return faction != Condition.MISSING
                && sinner != Condition.MISSING
                && quest != Condition.MISSING;
    }

    public boolean isMissing(final Gate gate) {
        return condition(gate) == Condition.MISSING;
    }

    public boolean isRecovered(final Gate gate) {
        return condition(gate) != Condition.MISSING;
    }

    public Set<Gate> missingGates() {
        final EnumSet<Gate> missing = EnumSet.noneOf(Gate.class);
        for (final Gate gate : Gate.values()) {
            if (isMissing(gate)) {
                missing.add(gate);
            }
        }
        return Collections.unmodifiableSet(missing);
    }

    private Condition condition(final Gate gate) {
        return switch (Objects.requireNonNull(gate, "gate")) {
            case FACTION -> faction;
            case SINNER -> sinner;
            case QUEST -> quest;
        };
    }

    public enum Gate {
        FACTION,
        SINNER,
        QUEST
    }

    public enum Condition {
        NOT_REQUIRED,
        SATISFIED,
        MISSING;

        private static Condition of(final boolean required, final boolean satisfied) {
            return required ? (satisfied ? SATISFIED : MISSING) : NOT_REQUIRED;
        }
    }
}
