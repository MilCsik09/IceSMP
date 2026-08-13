package hu.taliann.icesmp.client.projection;

import hu.taliann.icesmp.classrelic.ClassRelicActivation;
import hu.taliann.icesmp.client.protocol.RelicStatePayload;

import java.util.function.Function;

/**
 * ClassRelicActivation → RELIC_STATE leképezés. Tiszta függvény; a display-név
 * feloldását injektált resolverrel kapja (relics.definitions katalógus), így a
 * projekció nem függ a RelicManager típusától és dependency-free tesztelhető.
 */
public final class ClientRelicProjector {

    private ClientRelicProjector() {
    }

    public static RelicStatePayload project(final ClassRelicActivation activation,
                                            final Function<String, String> displayNameResolver,
                                            final long awakeningRemainingMillis) {
        if (activation == null || activation.relicId() == null || activation.relicId().isBlank()) {
            return new RelicStatePayload("", "", "", "", false, "", false, false,
                    ClassRelicActivation.DormantReason.NO_BINDING.name(), 0L);
        }
        final String displayName = displayNameResolver.apply(activation.relicId());
        return new RelicStatePayload(
                activation.relicId(),
                displayName == null ? activation.relicId() : displayName,
                activation.classId() == null ? "" : activation.classId(),
                activation.activeSpecializationId().orElse(""),
                activation.basePowerActive(),
                activation.resolvedResonanceId().orElse(""),
                activation.resonanceActive(),
                activation.awakeningConfigured(),
                activation.dormantReason().name(),
                Math.max(0L, awakeningRemainingMillis));
    }
}
