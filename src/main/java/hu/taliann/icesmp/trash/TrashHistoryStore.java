package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Durable hidden provenance indexed by opaque item-instance UUID. */
public final class TrashHistoryStore implements PersistentStore {

    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_EVENTS = 64;
    private static final int MAX_OWNERS = 64;
    private static final int MAX_VENDOR_OPERATIONS = 1_024;
    private static final int MAX_DETAIL_LENGTH = 96;
    private static final Set<Integer> OWNER_MILESTONES = Set.of(1, 3, 5, 10, 25, 50);

    private final JavaPlugin plugin;
    private final TrashCatalog catalog;
    private final File file;
    private final Map<UUID, StoredHistory> histories = new LinkedHashMap<>();
    private final Map<UUID, StoredVendorReceipt> vendorReceipts = new LinkedHashMap<>();

    public TrashHistoryStore(final JavaPlugin plugin, final TrashCatalog catalog) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.file = new File(plugin.getDataFolder(), "trash-history.yml");
        YamlStore.registerCriticalWrite(file);
    }

    @Override
    public synchronized void load() {
        histories.clear();
        vendorReceipts.clear();
        final YamlConfiguration yaml = YamlStore.loadTracked(file, plugin.getLogger());
        if (!file.exists()) return;
        if (yaml.getInt("schema-version", 0) != SCHEMA_VERSION) {
            YamlStore.failCorrupt(file, plugin.getLogger(),
                    "trash history schema-version must be exactly " + SCHEMA_VERSION);
        }
        loadHistories(yaml.getConfigurationSection("instances"));
        loadVendorReceipts(yaml.getConfigurationSection("vendor-operations"));
    }

    private void loadHistories(final ConfigurationSection root) {
        if (root == null) return;
        for (final String rawInstanceId : root.getKeys(false)) {
            final UUID instanceId = parseUuid(rawInstanceId, "instance key");
            final ConfigurationSection section = root.getConfigurationSection(rawInstanceId);
            if (section == null) corrupt("a Trash history instance nem objektum: " + rawInstanceId);
            final String baseId = section.getString("trash-id", "").trim();
            final String phase = section.getString("phase", "").trim();
            if (catalog.find(baseId).isEmpty() || !catalog.isKnownPhase(baseId, phase)) {
                corrupt("ismeretlen Trash history identity/phase: " + baseId + "/" + phase);
            }
            final long revision = section.getLong("revision", -1L);
            final long createdAt = section.getLong("created-at", -1L);
            final long updatedAt = section.getLong("updated-at", -1L);
            if (revision < 1L || createdAt < 1L || updatedAt < createdAt) {
                corrupt("érvénytelen Trash history revision/idő: " + rawInstanceId);
            }
            final LinkedHashSet<UUID> owners = new LinkedHashSet<>();
            for (final String rawOwner : section.getStringList("owners")) {
                if (owners.size() >= MAX_OWNERS) corrupt("túl sok Trash history owner: " + rawInstanceId);
                if (!owners.add(parseUuid(rawOwner, "owner"))) {
                    corrupt("duplikált Trash history owner: " + rawInstanceId);
                }
            }
            final List<HistoryEntry> events = parseEvents(section, rawInstanceId, revision);
            final StoredHistory stored = new StoredHistory(baseId, phase, revision, createdAt,
                    updatedAt, events, owners);
            if (histories.putIfAbsent(instanceId, stored) != null) {
                corrupt("duplikált Trash history instance: " + rawInstanceId);
            }
        }
    }

    private void loadVendorReceipts(final ConfigurationSection root) {
        if (root == null) return;
        if (root.getKeys(false).size() > MAX_VENDOR_OPERATIONS) {
            corrupt("túl sok nyitott Trash history vendor operation");
        }
        for (final String rawOperationId : root.getKeys(false)) {
            final UUID operationId = parseUuid(rawOperationId, "vendor operation");
            final ConfigurationSection section = root.getConfigurationSection(rawOperationId);
            if (section == null) corrupt("a Trash history vendor receipt nem objektum");
            final UUID actor = parseUuid(section.getString("actor", ""), "vendor actor");
            final String baseId = section.getString("trash-id", "").trim();
            final String phase = section.getString("phase", "").trim();
            final int amount = section.getInt("amount", 0);
            final List<InstanceRevision> units = new ArrayList<>();
            for (final String rawUnit : section.getStringList("units")) {
                final int separator = rawUnit.lastIndexOf(':');
                if (separator <= 0 || separator == rawUnit.length() - 1) {
                    corrupt("érvénytelen Trash history vendor unit");
                }
                final UUID instanceId = parseUuid(rawUnit.substring(0, separator),
                        "vendor unit instance");
                final long revision;
                try {
                    revision = Long.parseLong(rawUnit.substring(separator + 1));
                } catch (final NumberFormatException invalid) {
                    corrupt("érvénytelen Trash history vendor unit revision");
                    throw new AssertionError("unreachable", invalid);
                }
                final StoredHistory history = histories.get(instanceId);
                if (revision < 1L || history == null || history.revision() != revision
                        || !history.baseId().equals(baseId) || !history.phase().equals(phase)) {
                    corrupt("stale Trash history vendor receipt unit");
                }
                units.add(new InstanceRevision(instanceId, revision));
            }
            if (amount < 1 || units.size() != amount
                    || catalog.find(baseId).isEmpty() || !catalog.isKnownPhase(baseId, phase)) {
                corrupt("érvénytelen Trash history vendor receipt");
            }
            final StoredVendorReceipt receipt = new StoredVendorReceipt(
                    actor, baseId, phase, amount, units);
            if (vendorReceipts.putIfAbsent(operationId, receipt) != null) {
                corrupt("duplikált Trash history vendor operation");
            }
        }
    }

    @Override
    public synchronized void save() {
        persist();
    }

    /** Serializes a history mutation, its item projection and the durable write. */
    public synchronized <T> T transact(final Supplier<T> mutation,
                                       final Runnable restoreExternal) {
        Objects.requireNonNull(mutation, "mutation");
        final Map<UUID, StoredHistory> historyBefore = new LinkedHashMap<>(histories);
        final Map<UUID, StoredVendorReceipt> receiptsBefore =
                new LinkedHashMap<>(vendorReceipts);
        try {
            final T result = mutation.get();
            persist();
            return result;
        } catch (final RuntimeException | Error failure) {
            histories.clear();
            histories.putAll(historyBefore);
            vendorReceipts.clear();
            vendorReceipts.putAll(receiptsBefore);
            if (restoreExternal != null) {
                try {
                    restoreExternal.run();
                } catch (final RuntimeException | Error restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
            throw failure;
        }
    }

    public synchronized Snapshot createAndRecord(final UUID instanceId, final String baseId,
                                                 final String phase, final TrashHistoryEvent event,
                                                 final UUID actor, final String detail) {
        Objects.requireNonNull(instanceId, "instanceId");
        validateIdentity(baseId, phase);
        if (histories.containsKey(instanceId)) {
            throw new IllegalStateException("a Trash instance UUID már létezik");
        }
        final long now = System.currentTimeMillis();
        final StoredHistory initial = new StoredHistory(baseId, phase, 0L, now, now,
                List.of(), Set.of());
        final StoredHistory recorded = append(initial, event, actor, detail, now);
        histories.put(instanceId, recorded);
        return snapshot(instanceId, recorded);
    }

    public synchronized Snapshot record(final UUID instanceId, final String baseId,
                                        final String phase, final TrashHistoryEvent event,
                                        final UUID actor, final String detail) {
        final StoredHistory current = requireMatching(instanceId, baseId, phase);
        final StoredHistory recorded = append(current, event, actor, detail,
                System.currentTimeMillis());
        histories.put(instanceId, recorded);
        return snapshot(instanceId, recorded);
    }

    public synchronized Snapshot observeOwner(final UUID instanceId, final String baseId,
                                              final String phase, final UUID owner,
                                              final boolean king) {
        Objects.requireNonNull(owner, "owner");
        StoredHistory current = requireMatching(instanceId, baseId, phase);
        if (!current.owners().contains(owner) && current.owners().size() < MAX_OWNERS) {
            final LinkedHashSet<UUID> owners = new LinkedHashSet<>(current.owners());
            owners.add(owner);
            current = new StoredHistory(current.baseId(), current.phase(), current.revision(),
                    current.createdAt(), current.updatedAt(), current.events(), owners);
            current = append(current, TrashHistoryEvent.OWNER_OBSERVED, owner, "",
                    System.currentTimeMillis());
            if (OWNER_MILESTONES.contains(owners.size())) {
                current = append(current, TrashHistoryEvent.OWNER_COUNT_MILESTONE, null,
                        Integer.toString(owners.size()), System.currentTimeMillis());
            }
        }
        if (king && current.events().stream().noneMatch(entry ->
                entry.type() == TrashHistoryEvent.HELD_BY_KING && owner.equals(entry.actor()))) {
            current = append(current, TrashHistoryEvent.HELD_BY_KING, owner, "",
                    System.currentTimeMillis());
        }
        histories.put(instanceId, current);
        return snapshot(instanceId, current);
    }

    public synchronized Snapshot transform(final UUID instanceId, final String baseId,
                                           final String fromPhase, final String toPhase,
                                           final UUID actor) {
        final StoredHistory current = requireMatching(instanceId, baseId, fromPhase);
        if (!catalog.isKnownPhase(baseId, toPhase) || "base".equals(toPhase)) {
            throw new IllegalArgumentException("ismeretlen vagy érvénytelen Trash célphase");
        }
        final StoredHistory transitioned = new StoredHistory(current.baseId(), toPhase,
                current.revision(), current.createdAt(), current.updatedAt(), current.events(),
                current.owners());
        final StoredHistory recorded = append(transitioned, TrashHistoryEvent.TRANSFORMED,
                actor, fromPhase + "->" + toPhase, System.currentTimeMillis());
        histories.put(instanceId, recorded);
        return snapshot(instanceId, recorded);
    }

    public synchronized void putVendorReceipt(final UUID operationId, final UUID actor,
                                              final String baseId, final String phase,
                                              final List<Snapshot> units) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(actor, "actor");
        if (vendorReceipts.size() >= MAX_VENDOR_OPERATIONS || units == null || units.isEmpty()) {
            throw new IllegalStateException("érvénytelen vagy túl sok Trash vendor receipt");
        }
        final List<InstanceRevision> references = new ArrayList<>(units.size());
        for (final Snapshot unit : units) {
            if (!unit.baseId().equals(baseId) || !unit.phase().equals(phase)
                    || !matches(unit.instanceId(), baseId, phase, unit.revision())) {
                throw new IllegalStateException("stale Trash vendor receipt unit");
            }
            references.add(new InstanceRevision(unit.instanceId(), unit.revision()));
        }
        final StoredVendorReceipt receipt = new StoredVendorReceipt(
                actor, baseId, phase, units.size(), references);
        if (vendorReceipts.putIfAbsent(operationId, receipt) != null) {
            throw new IllegalStateException("a Trash vendor receipt már létezik");
        }
    }

    public synchronized Optional<VendorReceipt> findVendorReceipt(final UUID operationId) {
        final StoredVendorReceipt stored = vendorReceipts.get(operationId);
        if (stored == null) return Optional.empty();
        final List<Snapshot> units = new ArrayList<>(stored.units().size());
        for (final InstanceRevision reference : stored.units()) {
            final StoredHistory history = histories.get(reference.instanceId());
            if (history == null || history.revision() != reference.revision()
                    || !history.baseId().equals(stored.baseId())
                    || !history.phase().equals(stored.phase())) {
                throw new IllegalStateException("stale Trash vendor receipt");
            }
            units.add(snapshot(reference.instanceId(), history));
        }
        return Optional.of(new VendorReceipt(operationId, stored.actor(), stored.baseId(),
                stored.phase(), stored.amount(), units));
    }

    public synchronized boolean removeVendorReceipt(final UUID operationId) {
        return vendorReceipts.remove(operationId) != null;
    }

    public synchronized Optional<Snapshot> find(final UUID instanceId) {
        final StoredHistory history = histories.get(instanceId);
        return history == null ? Optional.empty() : Optional.of(snapshot(instanceId, history));
    }

    public synchronized boolean matches(final UUID instanceId, final String baseId,
                                        final String phase, final long revision) {
        final StoredHistory history = histories.get(instanceId);
        return history != null && history.baseId().equals(baseId) && history.phase().equals(phase)
                && history.revision() == revision;
    }

    public synchronized int size() {
        return histories.size();
    }

    private void persist() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        for (final Map.Entry<UUID, StoredHistory> entry : histories.entrySet()) {
            final String path = "instances." + entry.getKey();
            final StoredHistory history = entry.getValue();
            yaml.set(path + ".trash-id", history.baseId());
            yaml.set(path + ".phase", history.phase());
            yaml.set(path + ".revision", history.revision());
            yaml.set(path + ".created-at", history.createdAt());
            yaml.set(path + ".updated-at", history.updatedAt());
            yaml.set(path + ".owners", history.owners().stream().map(UUID::toString).toList());
            final List<Map<String, Object>> events = new ArrayList<>(history.events().size());
            for (final HistoryEntry event : history.events()) {
                final Map<String, Object> serialized = new LinkedHashMap<>();
                serialized.put("revision", event.revision());
                serialized.put("type", event.type().name());
                serialized.put("at", event.at());
                if (event.actor() != null) serialized.put("actor", event.actor().toString());
                if (!event.detail().isBlank()) serialized.put("detail", event.detail());
                events.add(serialized);
            }
            yaml.set(path + ".events", events);
        }
        for (final Map.Entry<UUID, StoredVendorReceipt> entry : vendorReceipts.entrySet()) {
            final String path = "vendor-operations." + entry.getKey();
            final StoredVendorReceipt receipt = entry.getValue();
            yaml.set(path + ".actor", receipt.actor().toString());
            yaml.set(path + ".trash-id", receipt.baseId());
            yaml.set(path + ".phase", receipt.phase());
            yaml.set(path + ".amount", receipt.amount());
            yaml.set(path + ".units", receipt.units().stream()
                    .map(unit -> unit.instanceId() + ":" + unit.revision()).toList());
        }
        try {
            YamlStore.saveAtomic(file, yaml);
        } catch (final IOException failure) {
            throw new IllegalStateException("Trash history mentése sikertelen", failure);
        }
    }

    private StoredHistory requireMatching(final UUID instanceId, final String baseId,
                                          final String phase) {
        Objects.requireNonNull(instanceId, "instanceId");
        validateIdentity(baseId, phase);
        final StoredHistory current = histories.get(instanceId);
        if (current == null || !current.baseId().equals(baseId) || !current.phase().equals(phase)) {
            throw new IllegalStateException("a Trash item és a history authority eltér");
        }
        return current;
    }

    private void validateIdentity(final String baseId, final String phase) {
        if (catalog.find(baseId).isEmpty() || !catalog.isKnownPhase(baseId, phase)) {
            throw new IllegalArgumentException("ismeretlen Trash identity/phase");
        }
    }

    private static StoredHistory append(final StoredHistory current,
                                        final TrashHistoryEvent event, final UUID actor,
                                        final String rawDetail, final long now) {
        Objects.requireNonNull(event, "event");
        final String detail = normalizeDetail(rawDetail);
        final long revision = Math.addExact(current.revision(), 1L);
        final ArrayList<HistoryEntry> events = new ArrayList<>(current.events());
        events.add(new HistoryEntry(revision, event, now, actor, detail));
        while (events.size() > MAX_EVENTS) events.remove(0);
        return new StoredHistory(current.baseId(), current.phase(), revision,
                current.createdAt(), now, events, current.owners());
    }

    private List<HistoryEntry> parseEvents(final ConfigurationSection section,
                                           final String instanceId, final long revision) {
        final List<Map<?, ?>> rawEvents = new ArrayList<>();
        for (final Map<?, ?> raw : section.getMapList("events")) rawEvents.add(raw);
        if (rawEvents.isEmpty() || rawEvents.size() > MAX_EVENTS) {
            corrupt("érvénytelen Trash history event count: " + instanceId);
        }
        final List<HistoryEntry> events = new ArrayList<>(rawEvents.size());
        long previous = 0L;
        for (final Map<?, ?> raw : rawEvents) {
            final long eventRevision = number(raw.get("revision"), -1L);
            final long at = number(raw.get("at"), -1L);
            if (eventRevision <= previous || eventRevision > revision || at < 1L) {
                corrupt("érvénytelen Trash history event sorrend: " + instanceId);
            }
            final TrashHistoryEvent type;
            try {
                type = TrashHistoryEvent.valueOf(String.valueOf(raw.get("type")));
            } catch (final IllegalArgumentException invalid) {
                corrupt("ismeretlen Trash history event: " + raw.get("type"));
                throw new AssertionError("unreachable", invalid);
            }
            final Object rawActor = raw.get("actor");
            final UUID actor = rawActor == null ? null
                    : parseUuid(String.valueOf(rawActor), "event actor");
            final String detail;
            try {
                detail = normalizeDetail(raw.get("detail") == null
                        ? "" : String.valueOf(raw.get("detail")));
            } catch (final IllegalArgumentException invalid) {
                corrupt("érvénytelen Trash history event detail: " + instanceId);
                throw new AssertionError("unreachable", invalid);
            }
            events.add(new HistoryEntry(eventRevision, type, at, actor, detail));
            previous = eventRevision;
        }
        if (previous != revision) {
            corrupt("a Trash history utolsó event revisionje eltér: " + instanceId);
        }
        return events;
    }

    private void corrupt(final String reason) {
        YamlStore.failCorrupt(file, plugin.getLogger(), reason);
    }

    private UUID parseUuid(final String raw, final String field) {
        try {
            return UUID.fromString(raw);
        } catch (final RuntimeException invalid) {
            corrupt("érvénytelen " + field + " UUID");
            throw new AssertionError("unreachable", invalid);
        }
    }

    private static long number(final Object value, final long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static String normalizeDetail(final String raw) {
        final String detail = raw == null ? "" : raw.trim();
        if (detail.length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException("a Trash history detail túl hosszú");
        }
        return detail;
    }

    private static Snapshot snapshot(final UUID instanceId, final StoredHistory history) {
        return new Snapshot(instanceId, history.baseId(), history.phase(), history.revision(),
                history.createdAt(), history.updatedAt(), history.events(), history.owners());
    }

    private record StoredHistory(String baseId, String phase, long revision,
                                 long createdAt, long updatedAt,
                                 List<HistoryEntry> events, Set<UUID> owners) {
        private StoredHistory {
            events = List.copyOf(events);
            owners = Set.copyOf(owners);
        }
    }

    private record InstanceRevision(UUID instanceId, long revision) {
        private InstanceRevision { Objects.requireNonNull(instanceId, "instanceId"); }
    }

    private record StoredVendorReceipt(UUID actor, String baseId, String phase, int amount,
                                       List<InstanceRevision> units) {
        private StoredVendorReceipt {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(baseId, "baseId");
            Objects.requireNonNull(phase, "phase");
            units = List.copyOf(units);
        }
    }

    public record HistoryEntry(long revision, TrashHistoryEvent type, long at,
                               UUID actor, String detail) {
        public HistoryEntry {
            Objects.requireNonNull(type, "type");
            detail = detail == null ? "" : detail;
        }
    }

    public record Snapshot(UUID instanceId, String baseId, String phase, long revision,
                           long createdAt, long updatedAt,
                           List<HistoryEntry> events, Set<UUID> owners) {
        public Snapshot {
            events = List.copyOf(events);
            owners = Set.copyOf(owners);
        }
    }

    public record VendorReceipt(UUID operationId, UUID actor, String baseId, String phase,
                                int amount, List<Snapshot> units) {
        public VendorReceipt {
            units = List.copyOf(units);
        }
    }
}
