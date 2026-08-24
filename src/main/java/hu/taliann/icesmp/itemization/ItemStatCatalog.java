package hu.taliann.icesmp.itemization;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ItemStatCatalog {

    public enum Group { OFFENSIVE, DEFENSIVE, UTILITY, CLASS_SPECIFIC }

    public record Definition(String id, String displayName, Group group, String consumer) {
        public Definition {
            id = normalizeId(id);
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("stat display name is required");
            }
            if (consumer == null || consumer.isBlank()) {
                throw new IllegalArgumentException("stat consumer is required");
            }
            java.util.Objects.requireNonNull(group, "group");
        }
    }

    private static final Map<String, Definition> DEFINITIONS;

    static {
        final LinkedHashMap<String, Definition> definitions = new LinkedHashMap<>();
        register(definitions, "attack_damage", "Támadóerő", Group.OFFENSIVE,
                "minecraft:generic.attack_damage");
        register(definitions, "attack_speed", "Támadási sebesség", Group.OFFENSIVE,
                "minecraft:generic.attack_speed");
        register(definitions, "ability_power", "Képességerő", Group.OFFENSIVE,
                "icesmp:ability-catalyst-scaling");
        register(definitions, "max_health", "Max. életerő", Group.DEFENSIVE,
                "minecraft:generic.max_health");
        register(definitions, "armor", "Páncél", Group.DEFENSIVE,
                "minecraft:generic.armor");
        register(definitions, "armor_toughness", "Páncél-szívósság", Group.DEFENSIVE,
                "minecraft:generic.armor_toughness");
        register(definitions, "knockback_resistance", "Visszalökés-ellenállás", Group.DEFENSIVE,
                "minecraft:generic.knockback_resistance");
        register(definitions, "movement_speed", "Mozgási sebesség", Group.UTILITY,
                "minecraft:generic.movement_speed");
        DEFINITIONS = Map.copyOf(definitions);
    }

    private ItemStatCatalog() {
    }

    public static Optional<Definition> find(final String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(DEFINITIONS.get(normalizeId(id)));
    }

    public static Definition require(final String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("unknown or dead item stat: " + id));
    }

    public static Map<String, Definition> definitions() {
        return DEFINITIONS;
    }

    private static void register(final Map<String, Definition> definitions, final String id,
                                 final String name, final Group group, final String consumer) {
        final Definition definition = new Definition(id, name, group, consumer);
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("duplicate item stat: " + definition.id());
        }
    }

    static String normalizeId(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        final String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (normalized.length() > 96 || !normalized.matches("[a-z0-9][a-z0-9._:-]*")) {
            throw new IllegalArgumentException("invalid id: " + raw);
        }
        return normalized;
    }
}
