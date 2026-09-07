package hu.taliann.icesmp.territory;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import java.util.Objects;

/** Explicit canonical field mutations; identity, geometry and unrelated fields are retained. */
public sealed interface TerritoryAdjustment {
    Territory apply(Territory before);

    default String fingerprint() {
        final String value = switch (this) {
            case Rename rename -> "rename\0" + rename.name();
            case SetType type -> "type\0" + type.type().name();
            case SetOwner owner -> "owner\0" + owner.owner().name();
            case SetYBounds bounds -> "y\0" + bounds.minimum() + "\0" + bounds.maximum();
        };
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (final java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    record Rename(String name) implements TerritoryAdjustment {
        public Rename {
            Objects.requireNonNull(name);
            if (name.isBlank() || name.length() > 128 || name.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid territory display name");
            }
        }
        @Override public Territory apply(Territory before) { return copy(before, before.faction(), name, before.type(), before.minY(), before.maxY()); }
    }
    record SetType(TerritoryType type) implements TerritoryAdjustment {
        public SetType { Objects.requireNonNull(type); }
        @Override public Territory apply(Territory before) { return copy(before, before.faction(), before.name(), type, before.minY(), before.maxY()); }
    }
    record SetOwner(FactionType owner) implements TerritoryAdjustment {
        public SetOwner { Objects.requireNonNull(owner); }
        @Override public Territory apply(Territory before) { return copy(before, owner, before.name(), before.type(), before.minY(), before.maxY()); }
    }
    record SetYBounds(int minimum, int maximum) implements TerritoryAdjustment {
        public SetYBounds { if (minimum > maximum) throw new IllegalArgumentException("Reversed territory Y bounds"); }
        @Override public Territory apply(Territory before) { return copy(before, before.faction(), before.name(), before.type(), minimum, maximum); }
    }
    private static Territory copy(Territory before, FactionType owner, String name, TerritoryType type, int minY, int maxY) {
        Objects.requireNonNull(before);
        return new Territory(before.id(), owner, name, type, before.world(), before.x(), before.z(), before.radius(), before.polygon(), minY, maxY);
    }
}
