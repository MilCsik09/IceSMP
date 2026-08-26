package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.utils.ConfigMaterialResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict, restart-only loader for the packaged 330-identity Trash authority. */
public final class TrashCatalog {

    public static final String RESOURCE = "content/trash/catalog.yml";
    public static final int BASE_IDENTITY_COUNT = 330;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*");
    private static final Map<TrashKind, Integer> EXPECTED_COUNTS = Map.of(
            TrashKind.MUNDANE, 190,
            TrashKind.STORY, 75,
            TrashKind.ANOMALY, 42,
            TrashKind.TRASH_RELIC, 23);
    private static final Set<String> FORBIDDEN_PLAYER_MARKERS = Set.of("anomália", "trash relic");

    private final JavaPlugin plugin;
    private volatile Map<String, TrashDefinition> definitions = Map.of();
    private volatile String rarityLabel = "";

    public TrashCatalog(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public synchronized void load() {
        try (InputStream input = plugin.getResource(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("hiányzó packaged Trash catalog: " + RESOURCE);
            }
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            final Parsed parsed = parse(yaml);
            definitions = parsed.definitions();
            rarityLabel = parsed.rarityLabel();
            plugin.getLogger().info("Trash catalog ready: " + definitions.size() + " identities.");
        } catch (final java.io.IOException impossibleForResourceStream) {
            throw new IllegalStateException("nem olvasható packaged Trash catalog: " + RESOURCE,
                    impossibleForResourceStream);
        }
    }

    public Optional<TrashDefinition> find(final String rawId) {
        if (rawId == null || rawId.isBlank()) return Optional.empty();
        return Optional.ofNullable(definitions.get(normalize(rawId)));
    }

    public TrashDefinition require(final String rawId) {
        return find(rawId).orElseThrow(() -> new IllegalArgumentException("ismeretlen Trash identity: " + rawId));
    }

    public Map<String, TrashDefinition> snapshot() {
        return definitions;
    }

    public String rarityLabel() {
        return rarityLabel;
    }

    static Parsed parse(final YamlConfiguration yaml) {
        Objects.requireNonNull(yaml, "yaml");
        if (yaml.getInt("schema-version", 0) != 1) {
            throw new IllegalStateException("Trash catalog schema-version must be exactly 1");
        }
        final String rarityId = normalize(yaml.getString("player-presentation.rarity-id", ""));
        final String rarityLabel = required(yaml.getString("player-presentation.rarity-label"),
                "player-presentation.rarity-label");
        if (!"ocska".equals(rarityId) || !"Ócska".equals(rarityLabel)) {
            throw new IllegalStateException("Trash player presentation must remain exactly Ócska/ocska");
        }
        final ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items == null) throw new IllegalStateException("Trash catalog items section missing");

        final LinkedHashMap<String, TrashDefinition> parsed = new LinkedHashMap<>();
        final EnumMap<TrashKind, Integer> counts = new EnumMap<>(TrashKind.class);
        final Set<String> displayNames = new HashSet<>();
        final Set<String> models = new HashSet<>();
        final Set<String> textures = new HashSet<>();
        final List<String> errors = new ArrayList<>();
        for (final String rawId : items.getKeys(false)) {
            final ConfigurationSection section = items.getConfigurationSection(rawId);
            try {
                if (section == null) throw new IllegalArgumentException("az identity nem objektum");
                final TrashDefinition definition = parseDefinition(rawId, section);
                unique(displayNames, definition.displayName(), "display-name");
                unique(models, definition.itemModel(), "item-model");
                unique(textures, definition.texture(), "texture");
                if (parsed.putIfAbsent(definition.id(), definition) != null) {
                    throw new IllegalArgumentException("duplikált identity ID");
                }
                counts.merge(definition.internalKind(), 1, Integer::sum);
            } catch (final RuntimeException invalid) {
                errors.add(rawId + ": " + invalid.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Hibás Trash catalog: " + String.join("; ", errors));
        }
        if (parsed.size() != BASE_IDENTITY_COUNT) {
            throw new IllegalStateException("Trash catalog must contain exactly " + BASE_IDENTITY_COUNT
                    + " base identities, found " + parsed.size());
        }
        if (!EXPECTED_COUNTS.equals(counts)) {
            throw new IllegalStateException("Trash kind counts drifted from the reviewed authority: " + counts);
        }
        return new Parsed(Map.copyOf(parsed), rarityLabel);
    }

    private static TrashDefinition parseDefinition(final String rawId, final ConfigurationSection section) {
        final String id = normalize(rawId);
        if (!ID_PATTERN.matcher(id).matches() || !id.equals(rawId)) {
            throw new IllegalArgumentException("az ID csak lower_snake_case lehet");
        }
        final String displayName = required(section.getString("display-name"), "display-name");
        final String playerRarity = normalize(section.getString("player-rarity", ""));
        if (!"ocska".equals(playerRarity)) {
            throw new IllegalArgumentException("player-rarity csak ocska lehet");
        }
        final String rawMaterial = required(section.getString("material"), "material");
        final Material material = ConfigMaterialResolver.match(rawMaterial);
        if (material == null) throw new IllegalArgumentException("ismeretlen material: " + rawMaterial);
        if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
            throw new IllegalArgumentException("a material nem item: " + material);
        }
        final String itemModel = required(section.getString("item-model"), "item-model");
        if (!("icesmp:trash/" + id).equals(itemModel)) {
            throw new IllegalArgumentException("az item-model nem az identity saját canonical modellje");
        }
        final String texture = required(section.getString("texture"), "texture");
        if (!("icesmp:item/trash/" + id).equals(texture)) {
            throw new IllegalArgumentException("a texture nem az identity saját canonical textúrája");
        }
        final int vendorValue = section.getInt("vendor-value", 0);
        if (vendorValue < 1 || vendorValue > 5) {
            throw new IllegalArgumentException("vendor-value csak 1..5 lehet");
        }
        final List<String> lore = List.copyOf(section.getStringList("lore"));
        validatePlayerText(displayName, "display-name");
        lore.forEach(line -> validatePlayerText(line, "lore"));
        final String sourceBias = required(section.getString("source-bias"), "source-bias");
        final TrashKind kind = TrashKind.parse(section.getString("internal.kind"));
        final String behavior = required(section.getString("internal.behavior"), "internal.behavior");
        if (kind.isInert() != "NONE".equals(behavior)) {
            throw new IllegalArgumentException("az inert kind behaviora NONE, a special kindé explicit kulcs kell legyen");
        }
        if (!kind.isInert() && !id.toUpperCase(Locale.ROOT).equals(behavior)) {
            throw new IllegalArgumentException("a special behavior kulcsnak identity-specifikusnak kell lennie");
        }
        return new TrashDefinition(id, displayName, playerRarity, material, itemModel, texture,
                vendorValue, lore, sourceBias, kind, behavior);
    }

    private static void validatePlayerText(final String text, final String field) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("üres " + field);
        final String normalized = text.toLowerCase(Locale.ROOT);
        for (final String marker : FORBIDDEN_PLAYER_MARKERS) {
            if (normalized.contains(marker)) {
                throw new IllegalArgumentException(field + " belső kategóriát fed fel: " + marker);
            }
        }
    }

    private static void unique(final Set<String> values, final String value, final String field) {
        if (!values.add(value)) throw new IllegalArgumentException("duplikált " + field + ": " + value);
    }

    private static String required(final String value, final String path) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("hiányzó " + path);
        return value.trim();
    }

    private static String normalize(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    record Parsed(Map<String, TrashDefinition> definitions, String rarityLabel) {
        Parsed {
            definitions = Map.copyOf(definitions);
        }
    }
}
