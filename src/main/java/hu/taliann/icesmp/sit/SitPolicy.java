package hu.taliann.icesmp.sit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Dependency-free policy helpers shared by the sit config codec and regression suite. */
public final class SitPolicy {

    public static final double MIN_CLICK_DISTANCE = 1.0D;
    public static final double MAX_CLICK_DISTANCE = 16.0D;

    private SitPolicy() {
    }

    /** Accepts YAML numeric nodes for a finite non-integral setting without string coercion. */
    public static double finiteNumber(final Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("value must be a numeric YAML node");
        }
        final double parsed = number.doubleValue();
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException("value must be finite");
        }
        return parsed;
    }

    public static double validateClickDistance(final double distance) {
        if (!Double.isFinite(distance)
                || distance < MIN_CLICK_DISTANCE
                || distance > MAX_CLICK_DISTANCE) {
            throw new IllegalArgumentException("max-click-distance must be finite and within "
                    + MIN_CLICK_DISTANCE + ".." + MAX_CLICK_DISTANCE);
        }
        return distance;
    }

    /** Normalizes case-insensitive world/material/tag identifiers while preserving config order. */
    public static Set<String> normalizeIdentifiers(final List<String> values) {
        final Set<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return Set.of();
        }
        for (final String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("identifier list contains an empty value");
            }
            normalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    /** Command roots are stored without slash, namespace or arguments. */
    public static Set<String> normalizeCommandRoots(final List<String> values) {
        final Set<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return Set.of();
        }
        for (final String value : values) {
            final String root = commandRoot(value);
            if (root.isEmpty()) {
                throw new IllegalArgumentException("blocked-commands contains an empty command");
            }
            if ("sit".equals(root)) {
                throw new IllegalArgumentException("blocked-commands must not contain /sit; players need /sit fel");
            }
            normalized.add(root);
        }
        return Set.copyOf(normalized);
    }

    /**
     * Normalizes `/home`, `home`, `/plugin:home` and `plugin:home` to the same root (`home`).
     * Only the first command token is considered; similar names such as `homes` remain distinct.
     */
    public static String commandRoot(final String rawCommand) {
        if (rawCommand == null) {
            return "";
        }
        String normalized = rawCommand.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        final int space = normalized.indexOf(' ');
        if (space >= 0) {
            normalized = normalized.substring(0, space);
        }
        final int namespace = normalized.lastIndexOf(':');
        if (namespace >= 0) {
            normalized = normalized.substring(namespace + 1);
        }
        return normalized;
    }

    public static boolean isCommandBlocked(final Set<String> blockedRoots, final String rawCommand) {
        return blockedRoots != null && blockedRoots.contains(commandRoot(rawCommand));
    }

    public static boolean isWorldAllowed(final Set<String> whitelist,
                                         final Set<String> blacklist,
                                         final String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        final String normalized = worldName.toLowerCase(Locale.ROOT);
        if (blacklist != null && blacklist.contains(normalized)) {
            return false;
        }
        return whitelist == null || whitelist.isEmpty() || whitelist.contains(normalized);
    }
}
