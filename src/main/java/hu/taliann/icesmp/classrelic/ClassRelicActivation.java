package hu.taliann.icesmp.classrelic;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Egyetlen központi feloldás eredménye: a konkrét relic-képességeknek soha nem kell
 * maguknak class/spec/ownership/possession kérdéseket feltenniük. DORMANT állapotban a
 * {@link #dormantReason()} adja a pontos okot (diagnosztika és teszt-invariánsok).
 */
public record ClassRelicActivation(
        UUID playerId,
        String relicId,
        String classId,
        Optional<String> activeSpecializationId,
        boolean basePowerActive,
        Optional<String> resolvedResonanceId,
        boolean resonanceActive,
        boolean awakeningConfigured,
        DormantReason dormantReason) {

    public enum DormantReason {
        NONE,
        FRAMEWORK_DISABLED,
        NO_BINDING,
        PROFILE_NOT_USABLE,
        WRONG_CLASS,
        NOT_OWNER,
        RELIC_LOST,
        NO_PHYSICAL_POSSESSION
    }

    public ClassRelicActivation {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(relicId, "relicId");
        Objects.requireNonNull(classId, "classId");
        Objects.requireNonNull(activeSpecializationId, "activeSpecializationId");
        Objects.requireNonNull(resolvedResonanceId, "resolvedResonanceId");
        Objects.requireNonNull(dormantReason, "dormantReason");
        if (basePowerActive && dormantReason != DormantReason.NONE) {
            throw new IllegalArgumentException("active power cannot carry a dormant reason");
        }
        if (resonanceActive && !basePowerActive) {
            throw new IllegalArgumentException("resonance requires an active base power");
        }
    }

    public static ClassRelicActivation dormant(final UUID playerId, final String relicId,
                                               final String classId, final DormantReason reason) {
        return new ClassRelicActivation(playerId, relicId, classId, Optional.empty(),
                false, Optional.empty(), false, false, reason);
    }
}
