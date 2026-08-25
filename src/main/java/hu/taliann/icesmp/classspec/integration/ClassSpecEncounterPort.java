package hu.taliann.icesmp.classspec.integration;

import java.util.Map;
import java.util.UUID;

/** Boundary for IceSMP-authored initiation and capstone encounters. */
public interface ClassSpecEncounterPort {

    EncounterHandle start(String encounterId, UUID ownerId, LocationSnapshot origin,
                          Map<String, String> context);

    void signal(EncounterHandle handle, String signalId, Map<String, String> data);

    void stop(EncounterHandle handle, StopReason reason);

    record LocationSnapshot(UUID worldId, double x, double y, double z, float yaw, float pitch) {
    }

    record EncounterHandle(UUID handleId, String encounterId, UUID ownerId) {
    }

    enum StopReason {
        COMPLETED,
        FAILED,
        CANCELLED,
        OWNER_LEFT,
        PLUGIN_DISABLE
    }
}
