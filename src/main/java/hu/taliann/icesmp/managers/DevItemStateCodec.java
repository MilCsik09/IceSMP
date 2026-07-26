package hu.taliann.icesmp.managers;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;

/**
 * Strict, side-effect-free codec for {@link DevItemManager}'s authoritative state.
 *
 * <p>Unlike Bukkit's default-value getters, this codec never turns malformed or partially missing
 * state into a plausible empty value. A caller must quarantine the file when decoding fails.</p>
 */
final class DevItemStateCodec {

    static final int SCHEMA_VERSION = 1;
    private static final Set<String> RARITIES = Set.of(
            "kozonseges", "nem_mindennapi", "ritka", "epikus", "legendas", "ereklye");

    private DevItemStateCodec() {
    }

    record Snapshot(UUID owner, boolean issued, UUID instanceId, long progressMillis,
                    String pendingRarity, String pendingEntry, ItemStack pendingItem,
                    int sinceRare, int sinceEpic, int sinceLegendary) {

        Snapshot {
            if (owner == null || instanceId == null) {
                throw new IllegalArgumentException("A DEV-item owner és instance UUID kötelező");
            }
            pendingRarity = pendingRarity == null ? "" : pendingRarity;
            pendingEntry = pendingEntry == null ? "" : pendingEntry;
            pendingItem = pendingItem == null ? null : pendingItem.clone();
        }

        @Override
        public ItemStack pendingItem() {
            return pendingItem == null ? null : pendingItem.clone();
        }
    }

    static Snapshot empty(final UUID owner, final UUID instanceId) {
        return new Snapshot(owner, false, instanceId, 0L, "", "", null,
                0, 0, 0);
    }

    static Snapshot decode(final YamlConfiguration yaml) {
        if (yaml == null) {
            throw new IllegalArgumentException("Hiányzó DEV-item YAML");
        }

        final Object rawVersion = yaml.get("bingulus.schema-version");
        if (rawVersion != null) {
            final int version = requireNonNegativeInt(rawVersion, "bingulus.schema-version");
            if (version > SCHEMA_VERSION) {
                throw new IllegalArgumentException("Nem támogatott DEV-item schema-version: " + version);
            }
        }

        final UUID owner = requireUuid(yaml.get("bingulus.owner"), "bingulus.owner");
        final UUID instance = requireUuid(yaml.get("bingulus.instance"), "bingulus.instance");
        final boolean issued = optionalBoolean(yaml.get("bingulus.issued"), false, "bingulus.issued");
        final long progress = optionalNonNegativeLong(
                yaml.get("bingulus.progress-millis"), 0L, "bingulus.progress-millis");

        final String rarity = optionalString(yaml.get("bingulus.pending.rarity"),
                "bingulus.pending.rarity");
        final String entry = optionalString(yaml.get("bingulus.pending.entry"),
                "bingulus.pending.entry");
        final Object rawItem = yaml.get("bingulus.pending.item");
        if (rawItem != null && !(rawItem instanceof ItemStack)) {
            throw new IllegalArgumentException("A bingulus.pending.item nem ItemStack");
        }
        final ItemStack pendingItem = rawItem == null ? null : ((ItemStack) rawItem).clone();
        if (pendingItem != null && (pendingItem.getType() == Material.AIR || pendingItem.getAmount() <= 0)) {
            throw new IllegalArgumentException("A bingulus.pending.item üres vagy AIR");
        }

        final int sinceRare = optionalNonNegativeInt(
                yaml.get("bingulus.pity.since-rare"), 0, "bingulus.pity.since-rare");
        final int sinceEpic = optionalNonNegativeInt(
                yaml.get("bingulus.pity.since-epic"), 0, "bingulus.pity.since-epic");
        final int sinceLegendary = optionalNonNegativeInt(
                yaml.get("bingulus.pity.since-legendary"), 0, "bingulus.pity.since-legendary");

        final Snapshot snapshot = new Snapshot(owner, issued, instance, progress, rarity, entry,
                pendingItem, sinceRare, sinceEpic, sinceLegendary);
        validateSnapshot(snapshot);
        return snapshot;
    }

    static YamlConfiguration encode(final Snapshot snapshot) {
        validateSnapshot(snapshot);
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("bingulus.schema-version", SCHEMA_VERSION);
        yaml.set("bingulus.owner", snapshot.owner().toString());
        yaml.set("bingulus.issued", snapshot.issued());
        yaml.set("bingulus.instance", snapshot.instanceId().toString());
        yaml.set("bingulus.progress-millis", snapshot.progressMillis());
        yaml.set("bingulus.pending.rarity", snapshot.pendingRarity());
        yaml.set("bingulus.pending.entry", snapshot.pendingEntry());
        yaml.set("bingulus.pending.item", snapshot.pendingItem());
        yaml.set("bingulus.pity.since-rare", snapshot.sinceRare());
        yaml.set("bingulus.pity.since-epic", snapshot.sinceEpic());
        yaml.set("bingulus.pity.since-legendary", snapshot.sinceLegendary());
        return yaml;
    }


    private static void validateSnapshot(final Snapshot snapshot) {
        if (snapshot.progressMillis() < 0L || snapshot.sinceRare() < 0
                || snapshot.sinceEpic() < 0 || snapshot.sinceLegendary() < 0) {
            throw new IllegalArgumentException("Negatív DEV-item progressz vagy pity");
        }
        validatePendingTuple(snapshot.pendingRarity(), snapshot.pendingEntry(),
                snapshot.pendingItem());
        if (!snapshot.issued() && (snapshot.progressMillis() != 0L
                || !snapshot.pendingRarity().isEmpty() || snapshot.pendingItem() != null
                || snapshot.sinceRare() != 0 || snapshot.sinceEpic() != 0
                || snapshot.sinceLegendary() != 0)) {
            throw new IllegalArgumentException(
                    "Nem kiadott DEV-itemhez progressz, pity vagy függő jutalom tartozik");
        }
    }

    private static void validatePendingTuple(final String rarity, final String entry,
                                             final ItemStack item) {
        final boolean hasRarity = !rarity.isBlank();
        final boolean hasEntry = !entry.isBlank();
        if (hasRarity != hasEntry) {
            throw new IllegalArgumentException("A függő jutalom rarity/entry párja hiányos");
        }
        if (!hasRarity) {
            if (item != null) {
                throw new IllegalArgumentException("Árva függő jutalom-item");
            }
            return;
        }
        if (!RARITIES.contains(rarity)) {
            throw new IllegalArgumentException("Ismeretlen függő jutalomritkaság: " + rarity);
        }
        // Legacy state may contain the selected rarity+entry before the exact ItemStack was built.
        // The manager materializes and persists the exact item before attempting delivery.
    }

    private static UUID requireUuid(final Object raw, final String path) {
        if (!(raw instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("Hiányzó vagy nem szöveges UUID: " + path);
        }
        try {
            return UUID.fromString(value);
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Érvénytelen UUID: " + path, invalid);
        }
    }

    private static String optionalString(final Object raw, final String path) {
        if (raw == null) {
            return "";
        }
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException("Nem szöveges mező: " + path);
        }
        return value.trim();
    }

    private static boolean optionalBoolean(final Object raw, final boolean fallback,
                                           final String path) {
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Boolean value)) {
            throw new IllegalArgumentException("Nem boolean mező: " + path);
        }
        return value;
    }

    private static long optionalNonNegativeLong(final Object raw, final long fallback,
                                                final String path) {
        return raw == null ? fallback : requireNonNegativeLong(raw, path);
    }

    private static long requireNonNegativeLong(final Object raw, final String path) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("Nem numerikus mező: " + path);
        }
        final double asDouble = number.doubleValue();
        final long value = number.longValue();
        if (!Double.isFinite(asDouble) || asDouble > Long.MAX_VALUE
                || asDouble != Math.rint(asDouble) || value < 0L) {
            throw new IllegalArgumentException("Nemnegatív egész szám szükséges: " + path);
        }
        return value;
    }

    private static int optionalNonNegativeInt(final Object raw, final int fallback,
                                              final String path) {
        return raw == null ? fallback : requireNonNegativeInt(raw, path);
    }

    private static int requireNonNegativeInt(final Object raw, final String path) {
        final long value = requireNonNegativeLong(raw, path);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Túl nagy egész szám: " + path);
        }
        return (int) value;
    }
}
