package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.SealCause;
import hu.taliann.icesmp.classspec.domain.SealReason;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Gate facts plus the stable identifiers required for exact-cause unsealing. */
public record GateSnapshot(GateState state, Map<GateState.Gate, String> gateIds) {

    public GateSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(gateIds, "gateIds");
        final EnumMap<GateState.Gate, String> copy = new EnumMap<>(GateState.Gate.class);
        gateIds.forEach((gate, rawId) -> {
            final String id = rawId == null ? "" : rawId.trim();
            if (gate == null || id.isEmpty()) {
                throw new IllegalArgumentException("Gate ids must be non-blank");
            }
            copy.put(gate, id);
        });
        for (final GateState.Gate gate : GateState.Gate.values()) {
            if (condition(state, gate) != GateState.Condition.NOT_REQUIRED && !copy.containsKey(gate)) {
                throw new IllegalArgumentException("Required gate has no stable id: " + gate);
            }
        }
        gateIds = Collections.unmodifiableMap(copy);
    }

    public SealReason missingReason() {
        final EnumMap<SealCause, String> missing = new EnumMap<>(SealCause.class);
        for (final GateState.Gate gate : GateState.Gate.values()) {
            if (state.isMissing(gate)) {
                missing.put(causeFor(gate), gateIds.get(gate));
            }
        }
        return missing.isEmpty() ? null
                : new SealReason(missing, "one or more DARK gate requirements are not satisfied");
    }

    public boolean authorizesRecovery(final SealReason reason) {
        if (reason == null || !reason.gateRestorableOnly()) {
            return false;
        }
        for (final Map.Entry<SealCause, String> entry : reason.gateIds().entrySet()) {
            final GateState.Gate gate = gateFor(entry.getKey());
            if (!state.isRecovered(gate) || !entry.getValue().equals(gateIds.get(gate))) {
                return false;
            }
        }
        return true;
    }

    private static SealCause causeFor(final GateState.Gate gate) {
        return switch (gate) {
            case FACTION -> SealCause.FACTION_MISSING;
            case SINNER -> SealCause.SINNER_MARK_MISSING;
            case QUEST -> SealCause.QUEST_REQUIREMENT_MISSING;
        };
    }

    private static GateState.Gate gateFor(final SealCause cause) {
        return switch (cause) {
            case FACTION_MISSING -> GateState.Gate.FACTION;
            case SINNER_MARK_MISSING -> GateState.Gate.SINNER;
            case QUEST_REQUIREMENT_MISSING -> GateState.Gate.QUEST;
            default -> throw new IllegalArgumentException("Seal cause is not gate-restorable: " + cause);
        };
    }

    private static GateState.Condition condition(final GateState state, final GateState.Gate gate) {
        return switch (gate) {
            case FACTION -> state.faction();
            case SINNER -> state.sinner();
            case QUEST -> state.quest();
        };
    }
}
