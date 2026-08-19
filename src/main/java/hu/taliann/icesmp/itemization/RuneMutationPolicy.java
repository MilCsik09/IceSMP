package hu.taliann.icesmp.itemization;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure canonical socket transition used by insert, remove and atomic replace. */
public final class RuneMutationPolicy {
    public enum Action { INSERT, REMOVE, REPLACE }

    /** Optional extension seam; bundled runes currently use {@link #unrestrictedFamilies()}. */
    public interface FamilyCompatibility {
        boolean allowed(String runeId, ArmorFamily family);

        default double weight(final String runeId, final ArmorFamily family) {
            return 1.0D;
        }
    }

    public record Result(List<String> runes, String removedRune) {
        public Result {
            runes = List.copyOf(runes);
            removedRune = Objects.requireNonNullElse(removedRune, "");
        }
    }

    private RuneMutationPolicy() { }

    public static FamilyCompatibility unrestrictedFamilies() {
        return (runeId, family) -> true;
    }

    public static Result apply(final List<String> currentRunes, final int capacity,
                               final Action action, final int socketIndex,
                               final String rawNewRune) {
        return apply(currentRunes, capacity, action, socketIndex, rawNewRune,
                null, unrestrictedFamilies());
    }

    public static Result apply(final List<String> currentRunes, final int capacity,
                               final Action action, final int socketIndex,
                               final String rawNewRune, final ArmorFamily family,
                               final FamilyCompatibility compatibility) {
        Objects.requireNonNull(currentRunes, "currentRunes");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(compatibility, "compatibility");
        if (capacity < 0 || capacity > 2 || currentRunes.size() > capacity) {
            throw new IllegalArgumentException("invalid rune capacity");
        }
        final ArrayList<String> next = new ArrayList<>();
        for (final String raw : currentRunes) {
            final String rune = ItemStatCatalog.normalizeId(raw);
            if (next.contains(rune)) throw new IllegalArgumentException("duplicate current rune");
            next.add(rune);
        }
        return switch (action) {
            case INSERT -> {
                final String newRune = requireNewRune(rawNewRune);
                requireCompatible(newRune, family, compatibility);
                if (next.size() >= capacity) throw new IllegalArgumentException("rune sockets full");
                if (next.contains(newRune)) throw new IllegalArgumentException("duplicate rune");
                next.add(newRune);
                yield new Result(next, "");
            }
            case REMOVE -> {
                requireOccupiedSocket(socketIndex, next.size());
                final String removed = next.remove(socketIndex);
                yield new Result(next, removed);
            }
            case REPLACE -> {
                requireOccupiedSocket(socketIndex, next.size());
                final String newRune = requireNewRune(rawNewRune);
                requireCompatible(newRune, family, compatibility);
                if (next.get(socketIndex).equals(newRune)) {
                    throw new IllegalArgumentException("replacement rune is unchanged");
                }
                if (next.contains(newRune)) throw new IllegalArgumentException("duplicate rune");
                final String removed = next.set(socketIndex, newRune);
                yield new Result(next, removed);
            }
        };
    }

    private static void requireCompatible(final String rune, final ArmorFamily family,
                                          final FamilyCompatibility compatibility) {
        if (!compatibility.allowed(rune, family)) {
            throw new IllegalArgumentException("rune is incompatible with armor family");
        }
        final double weight = compatibility.weight(rune, family);
        if (!Double.isFinite(weight) || weight < 0.0D) {
            throw new IllegalArgumentException("invalid rune family weight");
        }
    }

    private static String requireNewRune(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("missing new rune");
        return ItemStatCatalog.normalizeId(raw);
    }

    private static void requireOccupiedSocket(final int socketIndex, final int runeCount) {
        if (socketIndex < 0 || socketIndex >= runeCount) {
            throw new IllegalArgumentException("rune socket is empty or out of range");
        }
    }
}
