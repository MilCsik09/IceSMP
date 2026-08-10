package hu.taliann.icesmp.classspec.compat;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Small dependency-free matcher used by the production dependency preflight and regressions. */
public record VersionRequirement(String pluginName, RuntimeRole role, List<String> acceptedVersions,
                                 String verificationStatus) {

    public VersionRequirement {
        Objects.requireNonNull(pluginName, "pluginName");
        role = Objects.requireNonNull(role, "role");
        acceptedVersions = acceptedVersions == null ? List.of() : List.copyOf(acceptedVersions);
        verificationStatus = verificationStatus == null ? "unverified" : verificationStatus;
    }

    /** Compatibility constructor for callers created before the role-based lock schema. */
    public VersionRequirement(final String pluginName, final boolean required,
                              final List<String> acceptedVersions, final String verificationStatus) {
        this(pluginName, required ? RuntimeRole.REQUIRED_RUNTIME : RuntimeRole.OPTIONAL_INTEGRATION,
                acceptedVersions, verificationStatus);
    }

    public boolean accepts(final String runtimeVersion) {
        if (runtimeVersion == null || runtimeVersion.isBlank()) {
            return false;
        }
        final String normalizedRuntime = normalize(runtimeVersion);
        for (final String accepted : acceptedVersions) {
            final String normalizedAccepted = normalize(accepted);
            if (normalizedAccepted.endsWith(".*")) {
                final String prefix = normalizedAccepted.substring(0, normalizedAccepted.length() - 1);
                if (normalizedRuntime.startsWith(prefix)) {
                    return true;
                }
            } else if (normalizedRuntime.equals(normalizedAccepted)
                    || normalizedRuntime.startsWith(normalizedAccepted + "+")
                    || normalizedRuntime.startsWith(normalizedAccepted + "-")) {
                return true;
            }
        }
        return false;
    }

    /** Only a truly runtime-required capability may prevent IceSMP from starting. */
    public boolean blocksStartup() {
        return role == RuntimeRole.REQUIRED_RUNTIME;
    }

    /** Dev-only and validation-only entries are lock metadata, not server runtime checks. */
    public boolean participatesInRuntimeCheck() {
        return role == RuntimeRole.REQUIRED_RUNTIME || role == RuntimeRole.OPTIONAL_INTEGRATION;
    }

    public boolean acceptsMissingDependency() {
        return !blocksStartup();
    }

    /** Backwards-compatible semantic alias; new code should use {@link #blocksStartup()}. */
    public boolean required() {
        return blocksStartup();
    }

    private static String normalize(final String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    public enum RuntimeRole {
        REQUIRED_RUNTIME("required-runtime"),
        OPTIONAL_INTEGRATION("optional-integration"),
        DEV_ONLY("dev-only"),
        VALIDATION_ONLY("validation-only");

        private final String lockValue;

        RuntimeRole(final String lockValue) {
            this.lockValue = lockValue;
        }

        public String lockValue() {
            return lockValue;
        }

        public static RuntimeRole parse(final String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("runtime-role is required");
            }
            final String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            for (final RuntimeRole role : values()) {
                if (role.lockValue.equals(normalized)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("unknown runtime-role: " + raw);
        }
    }
}
