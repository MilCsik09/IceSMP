package hu.taliann.icesmp.classspec.integration;

import java.util.List;
import java.util.UUID;

/** Presentation boundary for CraftEngine-backed Soulbond/signature-item assets. */
public interface ClassSpecContentPort {

    /** Rebinds the one physical Soulbond item to the active class/spec loadout. */
    boolean rebindSoulbond(UUID playerId, SoulbondView view);

    /** Invalidates duplicate or stale physical copies after a profile revision change. */
    void invalidateSoulbondRevision(UUID playerId, long minimumRevision);

    record SoulbondView(String classId, String specializationId, int evolution,
                        List<String> modules, long revision) {
        public SoulbondView {
            modules = List.copyOf(modules);
        }
    }
}
