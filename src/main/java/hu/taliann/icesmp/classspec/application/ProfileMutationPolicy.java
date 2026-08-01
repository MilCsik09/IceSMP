package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;

import java.util.Objects;
import java.util.Optional;

/** The one access policy applied before any Profile v2 mutation. */
public final class ProfileMutationPolicy {

    public Decision assess(final ClassProfile profile,
                           final Optional<String> sessionBlockReason,
                           final Operation operation) {
        Objects.requireNonNull(profile, "profile");
        final Optional<String> externalBlock = sessionBlockReason == null
                ? Optional.empty() : sessionBlockReason.filter(reason -> !reason.isBlank());
        Objects.requireNonNull(operation, "operation");

        if (operation == Operation.EXPLICIT_RECOVERY) {
            return Decision.permit();
        }
        if (externalBlock.isPresent()) {
            return Decision.rejected("session blocked: " + externalBlock.orElseThrow());
        }
        if (profile.diagnostics().sessionBlocked()) {
            return Decision.rejected("session blocked: " + profile.diagnostics().sessionBlockReason());
        }
        if (profile.status() == ProfileStatus.MIGRATION_REVIEW) {
            return Decision.rejected("profile requires migration review");
        }
        if (profile.status() == ProfileStatus.CORRUPT_QUARANTINE) {
            return Decision.rejected("profile is quarantined");
        }
        return Decision.permit();
    }

    public enum Operation {
        SELECT,
        LOADOUT_RESET,
        ADMIN_RESET,
        EXPLICIT_SEAL,
        GATE_RECONCILE,
        CLASS_ASSIGN,
        CLASS_LEVEL_MIRROR,
        SOULFORGE_UPGRADE,
        EXPLICIT_RECOVERY
    }

    public record Decision(boolean allowed, String detail) {
        public Decision {
            detail = detail == null ? "" : detail;
        }

        public static Decision permit() {
            return new Decision(true, "");
        }

        public static Decision rejected(final String detail) {
            return new Decision(false, detail);
        }
    }
}
