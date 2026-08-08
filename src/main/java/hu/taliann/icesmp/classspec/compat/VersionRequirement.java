package hu.taliann.icesmp.classspec.compat;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Small dependency-free matcher used by the production dependency preflight and regressions. */
public record VersionRequirement(String pluginName, boolean required, List<String> acceptedVersions,
                                 String verificationStatus) {

    public VersionRequirement {
        Objects.requireNonNull(pluginName, "pluginName");
        acceptedVersions = acceptedVersions == null ? List.of() : List.copyOf(acceptedVersions);
        verificationStatus = verificationStatus == null ? "unverified" : verificationStatus;
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

    public boolean acceptsMissingDependency() {
        return !required;
    }

    private static String normalize(final String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("v")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
