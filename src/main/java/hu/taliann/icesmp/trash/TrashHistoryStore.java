package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
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
import java.util.function.BooleanSupplier;

/** Durable hidden provenance indexed by opaque item-instance UUID. */
public final class TrashHistoryStore implements PersistentStore {

    private static final int SCHEMA_VERSION = 6;
    private static final int JOURNAL_SCHEMA_VERSION = 4;
    private static final int MAX_DEVELOPER_OPERATIONS = 1_024;
    private static final int MAX_DEVELOPER_PROJECTION_CHARACTERS = 16 * 1024 * 1024;
    private static final int MAX_INSTANCES = 100_000;
    private static final int MAX_WALL_OPERATIONS = MAX_INSTANCES;
    private static final int MAX_UNRESOLVED_WALL_OPERATIONS = 1_024;
    private static final int MAX_EVENTS = 64;
    private static final int MAX_OWNERS = 64;
    private static final int MAX_VENDOR_OPERATIONS = 1_024;
    private static final int MAX_JOURNAL_RECORDS = 1_024;
    private static final int MAX_DETAIL_LENGTH = 96;
    private static final Set<Integer> OWNER_MILESTONES = Set.of(1, 3, 5, 10, 25, 50);

    private final java.util.logging.Logger logger;
    private final java.util.concurrent.locks.ReentrantLock stateLock = new java.util.concurrent.locks.ReentrantLock();
    private boolean readable;
    private final TrashCatalog catalog;
    private final File file;
    private final TrashHistoryJournal journal;
    private final JournalAppender journalAppender;
    private final Map<UUID, StoredHistory> histories = new LinkedHashMap<>();
    private final Map<UUID, StoredVendorReceipt> vendorReceipts = new LinkedHashMap<>();
    private final Map<UUID, WallReceipt> wallReceipts = new LinkedHashMap<>();
    private final Map<UUID, UUID> wallOperationByInstance = new java.util.HashMap<>();
    private final Set<UUID> unresolvedWallOperations = new java.util.LinkedHashSet<>();
    private final Map<UUID, TrashDeveloperReceipt> developerReceipts = new LinkedHashMap<>();
    private final Map<UUID, UUID> unresolvedDeveloperByInstance = new java.util.HashMap<>();
    private int developerProjectionCharacters;
    private long sequence;
    private int journalRecords;
    private TransactionFrame activeTransaction;
    private boolean replayingJournal;

    public TrashHistoryStore(final JavaPlugin plugin, final TrashCatalog catalog) {
        this(new File(plugin.getDataFolder(), "trash-history.yml"),
                new File(plugin.getDataFolder(), "trash-history.wal"), plugin.getLogger(), catalog);
    }

    TrashHistoryStore(final File file, final File journalFile,
                      final java.util.logging.Logger logger, final TrashCatalog catalog) {
        this(file, journalFile, logger, catalog, TrashHistoryJournal::append);
    }

    TrashHistoryStore(final File file, final File journalFile,
                      final java.util.logging.Logger logger, final TrashCatalog catalog,
                      final JournalAppender journalAppender) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.file = Objects.requireNonNull(file, "file");
        this.journal = new TrashHistoryJournal(logger, journalFile);
        this.journalAppender = Objects.requireNonNull(journalAppender, "journalAppender");
        YamlStore.registerCriticalWrite(file);
    }

    @Override
    public void load() {
        stateLock.lock();
        try {
            readable = false;
            histories.clear();
            vendorReceipts.clear();
            wallReceipts.clear();
            wallOperationByInstance.clear();
            unresolvedWallOperations.clear();
            developerReceipts.clear();
            unresolvedDeveloperByInstance.clear();
            developerProjectionCharacters = 0;
            sequence = 0L;
            journalRecords = 0;
            activeTransaction = null;
            replayingJournal = false;
            if (file.exists()) {
                final YamlConfiguration yaml = YamlStore.loadTracked(file, logger);
                final int schema = yaml.getInt("schema-version", 0);
                if (schema < 3 || schema > SCHEMA_VERSION) {
                    YamlStore.failCorrupt(file, logger,
                            "trash history schema-version must be between 3 and " + SCHEMA_VERSION);
                }
                sequence = yaml.getLong("last-sequence", -1L);
                if (sequence < 0L) corrupt("érvénytelen Trash history snapshot sequence");
                loadHistories(yaml.getConfigurationSection("instances"));
                loadVendorReceipts(yaml.getConfigurationSection("vendor-operations"));
                if (schema == 3 && yaml.contains("wall-operations")) corrupt("wall receipt in legacy schema");
                loadWallReceipts(yaml, "wall-operations", schema >= 5, false);
                if (schema < 6 && yaml.contains("developer-operations")) corrupt("developer receipt in legacy snapshot");
                loadDeveloperReceipts(yaml, "developer-operations", false);
            }
            final TrashHistoryJournal.LoadResult recovered = journal.loadAfter(sequence);
            for (final TrashHistoryJournal.Record record : recovered.records()) {
                applyJournalRecord(record);
            }
            sequence = recovered.sequence();
            journalRecords = recovered.completeRecords();
            for (final var entry : histories.entrySet()) validateDeveloperEventReceipts(entry.getKey(), entry.getValue());
            readable = true;
        } finally {
            stateLock.unlock();
        }
    }

    private void loadHistories(final ConfigurationSection root) {
        if (root == null) return;
        if (root.getKeys(false).size() > MAX_INSTANCES) {
            corrupt("túl sok Trash history instance");
        }
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

    private void loadWallReceipts(final ConfigurationSection root, final String path,
                                  final boolean observationSchema, final boolean updates) {
        if (!root.contains(path)) return;
        final ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) corrupt("wall receipts are not an object");
        if (section.getKeys(false).size() > MAX_WALL_OPERATIONS) corrupt("too many retained wall operations");
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection value = section.getConfigurationSection(key);
            if (value == null) corrupt("wall receipt is not an object");
            if (observationSchema ? !value.isBoolean("removal-observed") : value.contains("removal-observed")) {
                corrupt("wall removal observation has the wrong schema or type");
            }
            for (final String numeric : List.of("revision", "before-revision", "consumed-at", "expires-at")) {
                if (!value.isLong(numeric) && !value.isInt(numeric)) corrupt("wall receipt integer has the wrong type");
            }
            final WallReceipt receipt;
            try {
                receipt = new WallReceipt(parseUuid(key, "wall operation"),
                        parseUuid(value.getString("actor", ""), "wall actor"),
                        parseUuid(value.getString("world", ""), "wall world"),
                        parseUuid(value.getString("projectile", ""), "wall projectile"),
                        parseUuid(value.getString("instance", ""), "wall instance"),
                        value.getLong("revision", -1L), value.getString("trash-id", ""),
                        value.getString("phase", ""), value.getLong("consumed-at", -1L),
                        new TrashRuleFieldService.RuleField(parseUuid(key, "wall field"),
                                TrashRuleFieldService.FieldKind.PROJECTILE_WALL,
                                new TrashRuleFieldService.Point(parseUuid(value.getString("world", ""), "wall world"),
                                        value.getDouble("x", Double.NaN), value.getDouble("y", Double.NaN),
                                        value.getDouble("z", Double.NaN)), value.getDouble("radius", Double.NaN),
                                value.getLong("expires-at", -1L), parseUuid(value.getString("actor", ""), "wall owner"),
                                value.getString("reservation", "")), value.getLong("before-revision", -1L),
                        observationSchema && value.getBoolean("removal-observed"));
                validateWallReceipt(receipt);
            } catch (final RuntimeException invalid) {
                corrupt("invalid unresolved wall receipt");
                throw new AssertionError("unreachable", invalid);
            }
            final WallReceipt previous = wallReceipts.get(receipt.operationId());
            if (previous == null && wallReceipts.size() >= MAX_WALL_OPERATIONS) corrupt("too many retained wall operations");
            if (updates && previous == null && receipt.removalObserved()) corrupt("observed wall has no acknowledged consume");
            if (previous != null && (!updates || previous.removalObserved()
                    || !previous.withObservedRemoval().equals(receipt))) {
                corrupt("invalid wall operation transition");
            }
            replaceWallReceipt(receipt);
        }
    }

    private void validateWallReceipt(final WallReceipt receipt) {
        final StoredHistory history = histories.get(receipt.instanceId());
        final TrashDefinition definition = catalog.find(receipt.baseId()).orElseThrow();
        if (!definition.behavior().equals(TrashRelicBehavior.TEGLA.name())
                || !definition.successPhase().equals(receipt.phase()) || history == null
                || !history.baseId().equals(receipt.baseId()) || !history.phase().equals(receipt.phase())
                || history.revision() < receipt.revision()
                || !receipt.removalObserved() && history.revision() != receipt.revision()
                || history.events().isEmpty()) {
            throw new IllegalStateException("wall receipt does not match consumed native history");
        }
        final var event = history.events().stream().filter(entry -> entry.revision() == receipt.revision()).findFirst();
        if (event.isPresent() ? event.orElseThrow().type() != TrashHistoryEvent.TRANSFORMED
                || !receipt.actor().equals(event.orElseThrow().actor())
                : history.events().getFirst().revision() <= receipt.revision()) {
            throw new IllegalStateException("wall receipt does not match retained consumption evidence");
        }
        final UUID existingOperation = wallOperationByInstance.get(receipt.instanceId());
        if (existingOperation != null && !existingOperation.equals(receipt.operationId())
                || !receipt.removalObserved() && unresolvedWallOperations.stream()
                .filter(id -> !id.equals(receipt.operationId())).map(wallReceipts::get)
                .anyMatch(existing -> existing.projectileId().equals(receipt.projectileId()))) {
            throw new IllegalStateException("wall instance or projectile already has an unresolved operation");
        }
        if (!receipt.removalObserved() && !unresolvedWallOperations.contains(receipt.operationId())
                && unresolvedWallOperations.size() >= MAX_UNRESOLVED_WALL_OPERATIONS) {
            throw new IllegalStateException("unresolved wall operation capacity reached");
        }
    }

    // These indices are rebuilt from native receipts and updated under the same transaction lock.
    private void replaceWallReceipt(final WallReceipt receipt) {
        wallReceipts.put(receipt.operationId(), receipt);
        wallOperationByInstance.put(receipt.instanceId(), receipt.operationId());
        if (receipt.removalObserved()) unresolvedWallOperations.remove(receipt.operationId());
        else unresolvedWallOperations.add(receipt.operationId());
    }
    private void removeWallReceipt(final UUID operation) {
        final var removed = wallReceipts.remove(operation);
        if (removed != null) wallOperationByInstance.remove(removed.instanceId(), operation);
        unresolvedWallOperations.remove(operation);
    }

    private void validateDeveloperReceipt(final TrashDeveloperReceipt receipt) {
        final StoredHistory history = histories.get(receipt.instanceId());
        if (history == null || !history.baseId().equals(receipt.baseId())
                || !catalog.isKnownPhase(receipt.baseId(), receipt.beforePhase())
                || !catalog.isKnownPhase(receipt.baseId(), receipt.afterPhase())
                || history.revision() < receipt.afterRevision()
                || !receipt.projectionObserved() && (history.revision() != receipt.afterRevision()
                    || !history.phase().equals(receipt.afterPhase()))
                || receipt.kind() == TrashDeveloperReceipt.Kind.TRANSITION_SUCCESS
                    && !catalog.require(receipt.baseId()).successPhase().equals(receipt.afterPhase())) {
            throw new IllegalStateException("Developer receipt differs from native history");
        }
        final var event = history.events().stream().filter(entry -> entry.revision() == receipt.afterRevision()).findFirst();
        if (event.isPresent() ? event.orElseThrow().type() != receipt.kind().event()
                || !receipt.actor().equals(event.orElseThrow().actor())
                || !receipt.operationId().toString().equals(event.orElseThrow().detail())
                || receipt.recordedAt() != event.orElseThrow().at()
                : history.events().isEmpty() || history.events().getFirst().revision() <= receipt.afterRevision()) {
            throw new IllegalStateException("Developer receipt lacks its exact native event");
        }
        final UUID pending = unresolvedDeveloperByInstance.get(receipt.instanceId());
        final UUID wall = wallOperationByInstance.get(receipt.instanceId());
        if (!receipt.projectionObserved() && (pending != null && !pending.equals(receipt.operationId())
                || wall != null && unresolvedWallOperations.contains(wall))) {
            throw new IllegalStateException("Instance already has an unresolved native operation");
        }
    }

    private void replaceDeveloperReceipt(final TrashDeveloperReceipt receipt) {
        removeDeveloperReceipt(receipt.operationId());
        developerReceipts.put(receipt.operationId(), receipt);
        developerProjectionCharacters += receipt.projectionCharacters();
        if (!receipt.projectionObserved()) unresolvedDeveloperByInstance.put(receipt.instanceId(), receipt.operationId());
    }

    private void removeDeveloperReceipt(final UUID operation) {
        final var old = developerReceipts.remove(operation);
        if (old != null) {
            developerProjectionCharacters -= old.projectionCharacters();
            unresolvedDeveloperByInstance.remove(old.instanceId(), operation);
        }
    }

    /** Only the native mutation transaction can bind physical projection to its own history change. */
    void putDeveloperReceipt(final TrashDeveloperReceipt receipt) {
        stateLock.lock();
        try {
            requireLoadedAcknowledgement();
            final var frame = requireTransaction();
            Objects.requireNonNull(receipt);
            if (receipt.projectionObserved() || developerReceipts.containsKey(receipt.operationId())
                    || developerReceipts.size() >= MAX_DEVELOPER_OPERATIONS
                    || developerProjectionCharacters + receipt.projectionCharacters() > MAX_DEVELOPER_PROJECTION_CHARACTERS
                    || !frame.historyBefore().containsKey(receipt.instanceId())) {
                throw new IllegalStateException("Developer receipt admission refused");
            }
            final StoredHistory before = frame.historyBefore().get(receipt.instanceId());
            if (before == null ? receipt.beforeRevision() != 0
                    : before.revision() != receipt.beforeRevision() || !before.phase().equals(receipt.beforePhase())
                        || !before.baseId().equals(receipt.baseId())) {
                throw new IllegalStateException("Developer receipt does not describe this transaction's before state");
            }
            validateDeveloperReceipt(receipt);
            frame.developerBefore().put(receipt.operationId(), null);
            replaceDeveloperReceipt(receipt);
        } finally { stateLock.unlock(); }
    }

    /** Outer absence means unavailable storage; inner absence is assessed missing operation. */
    public Optional<Optional<TrashDeveloperReceipt>> tryInspectDeveloperReceipt(final UUID operation) {
        Objects.requireNonNull(operation);
        if (stateLock.isHeldByCurrentThread() || !stateLock.tryLock()) return Optional.empty();
        try { return readable ? Optional.of(Optional.ofNullable(developerReceipts.get(operation))) : Optional.empty(); }
        finally { stateLock.unlock(); }
    }

    boolean developerMutationAvailable(final UUID operation, final UUID instance) {
        if (!stateLock.isHeldByCurrentThread()) throw new IllegalStateException("Native history admission lock required");
        requireLoadedAcknowledgement();
        return !developerReceipts.containsKey(operation) && !unresolvedDeveloperByInstance.containsKey(instance)
                && developerReceipts.size() < MAX_DEVELOPER_OPERATIONS
                && developerProjectionCharacters < MAX_DEVELOPER_PROJECTION_CHARACTERS;
    }

    public Optional<List<TrashDeveloperReceipt>> tryInspectDeveloperReceipts(final UUID actor) {
        Objects.requireNonNull(actor);
        if (stateLock.isHeldByCurrentThread() || !stateLock.tryLock()) return Optional.empty();
        try { return readable ? Optional.of(developerReceipts.values().stream()
                .filter(receipt -> receipt.actor().equals(actor)).toList()) : Optional.empty(); }
        finally { stateLock.unlock(); }
    }

    /** A fresh owner-local exact projection observation is required, never inferred from missing UUIDs. */
    public boolean tryConfirmDeveloperProjection(final TrashDeveloperReceipt expected, final BooleanSupplier observation) {
        Objects.requireNonNull(expected); Objects.requireNonNull(observation);
        return tryTransact(() -> !expected.projectionObserved() && expected.equals(developerReceipts.get(expected.operationId()))
                && observation.getAsBoolean(), () -> {
            final var frame = requireTransaction();
            frame.developerBefore().put(expected.operationId(), expected);
            replaceDeveloperReceipt(expected.withObservedProjection());
        }, null);
    }

    /** Pending native projection only; completed operations never authorize recreating a missing item. */
    boolean tryRestoreDeveloperProjection(final TrashDeveloperReceipt expected, final BooleanSupplier admission,
            final Runnable projection, final Runnable restoreExternal) {
        Objects.requireNonNull(expected); Objects.requireNonNull(admission); Objects.requireNonNull(projection);
        return tryTransact(() -> !expected.projectionObserved() && expected.equals(developerReceipts.get(expected.operationId()))
                && histories.get(expected.instanceId()).revision() == expected.afterRevision() && admission.getAsBoolean(),
                projection, restoreExternal);
    }

    /** Authored phase change with an explicit developer event, never a fabricated natural TRANSFORMED event. */
    Snapshot transitionDeveloper(final UUID instance, final String baseId, final String from, final String to,
            final UUID actor, final UUID operation) {
        stateLock.lock();
        try {
            final StoredHistory current = requireMatching(instance, baseId, from);
            if (!hu.taliann.icesmp.security.HiddenDevAuthority.isDeveloper(actor) || !from.equals("base")
                    || to.equals("base") || !catalog.require(baseId).successPhase().equals(to)) {
                throw new IllegalArgumentException("Invalid developer success transition");
            }
            final var changed = new StoredHistory(baseId, to, current.revision(), current.createdAt(),
                    current.updatedAt(), current.events(), current.owners());
            final var recorded = append(changed, TrashHistoryEvent.DEV_TRANSITIONED, actor,
                    Objects.requireNonNull(operation).toString(), System.currentTimeMillis());
            putHistory(instance, recorded);
            return snapshot(instance, recorded);
        } finally { stateLock.unlock(); }
    }

    private void loadDeveloperReceipts(final ConfigurationSection root, final String path, final boolean updates) {
        if (!root.contains(path)) return;
        final var section = root.getConfigurationSection(path);
        if (section == null || section.getKeys(false).size() > MAX_DEVELOPER_OPERATIONS) corrupt("Invalid developer receipt object or count");
        for (final String key : section.getKeys(false)) {
            final var value = section.getConfigurationSection(key);
            if (value == null || !value.isBoolean("projection-observed") || !value.isList("slots")) corrupt("Invalid developer receipt fields");
            for (final String numeric : List.of("before-revision", "after-revision", "recorded-at")) {
                if (!value.isLong(numeric) && !value.isInt(numeric)) corrupt("Invalid developer receipt number");
            }
            final TrashDeveloperReceipt receipt;
            try {
                final List<TrashDeveloperReceipt.SlotChange> slots = new ArrayList<>();
                for (final Object raw : value.getList("slots", List.of())) {
                    if (!(raw instanceof Map<?, ?> slot) || !(slot.get("slot") instanceof Integer index)
                            || !(slot.get("before") instanceof String before) || !(slot.get("after") instanceof String after)) {
                        throw new IllegalArgumentException("Invalid developer slot projection");
                    }
                    slots.add(new TrashDeveloperReceipt.SlotChange(index, before, after));
                }
                receipt = new TrashDeveloperReceipt(parseUuid(key, "developer operation"),
                        parseUuid(value.getString("actor", ""), "developer actor"),
                        TrashDeveloperReceipt.Kind.valueOf(value.getString("kind", "")),
                        parseUuid(value.getString("instance", ""), "developer instance"),
                        value.getString("trash-id", ""), value.getString("before-phase", ""), value.getLong("before-revision"),
                        value.getString("after-phase", ""), value.getLong("after-revision"), value.getLong("recorded-at"),
                        slots, value.getBoolean("projection-observed"));
                validateDeveloperReceipt(receipt);
            } catch (final RuntimeException invalid) {
                corrupt("Invalid native developer receipt"); throw new AssertionError("unreachable", invalid);
            }
            final var previous = developerReceipts.get(receipt.operationId());
            if (previous == null && developerReceipts.size() >= MAX_DEVELOPER_OPERATIONS
                    || developerProjectionCharacters + receipt.projectionCharacters()
                        - (previous == null ? 0 : previous.projectionCharacters()) > MAX_DEVELOPER_PROJECTION_CHARACTERS
                    || updates && previous == null && receipt.projectionObserved()
                    || previous != null && (!updates || previous.projectionObserved()
                        || !previous.withObservedProjection().equals(receipt))) {
                corrupt("Invalid developer receipt transition or capacity");
            }
            replaceDeveloperReceipt(receipt);
        }
    }

    private static void writeDeveloperReceipt(final YamlConfiguration yaml, final String path, final TrashDeveloperReceipt receipt) {
        yaml.set(path + ".actor", receipt.actor().toString());
        yaml.set(path + ".kind", receipt.kind().name());
        yaml.set(path + ".instance", receipt.instanceId().toString());
        yaml.set(path + ".trash-id", receipt.baseId());
        yaml.set(path + ".before-phase", receipt.beforePhase());
        yaml.set(path + ".before-revision", receipt.beforeRevision());
        yaml.set(path + ".after-phase", receipt.afterPhase());
        yaml.set(path + ".after-revision", receipt.afterRevision());
        yaml.set(path + ".recorded-at", receipt.recordedAt());
        yaml.set(path + ".projection-observed", receipt.projectionObserved());
        yaml.set(path + ".slots", receipt.slots().stream().map(slot -> Map.of(
                "slot", slot.slot(), "before", slot.before(), "after", slot.after())).toList());
    }

    /** Records the consumed item and pending effect in the same native history transaction. */
    public void putWallReceipt(final WallReceipt receipt) {
        stateLock.lock();
        try {
            requireLoadedAcknowledgement();
            final TransactionFrame frame = requireTransaction();
            Objects.requireNonNull(receipt, "receipt");
            if (receipt.removalObserved() || wallReceipts.size() >= MAX_WALL_OPERATIONS
                    || wallReceipts.containsKey(receipt.operationId())) {
                throw new IllegalStateException("wall receipt admission refused");
            }
            validateWallReceipt(receipt);
            final StoredHistory before = frame.historyBefore().get(receipt.instanceId());
            if (before == null || !before.phase().equals("base") || before.revision() != receipt.beforeRevision()) {
                throw new IllegalStateException("wall receipt requires consumption in this transaction");
            }
            frame.wallBefore().putIfAbsent(receipt.operationId(), null);
            replaceWallReceipt(receipt);
        } finally {
            stateLock.unlock();
        }
    }

    /** Busy/unassessed state is unavailable, never a fabricated empty recovery inventory. */
    public Optional<List<WallReceipt>> tryInspectWallReceipts() {
        if (stateLock.isHeldByCurrentThread() || !stateLock.tryLock()) return Optional.empty();
        try {
            return readable ? Optional.of(unresolvedWallOperations.stream().map(wallReceipts::get).toList()) : Optional.empty();
        } finally { stateLock.unlock(); }
    }

    /** Bounded owner-captured instance queries include retained observed completions without scanning history. */
    public Optional<Map<UUID, WallReceipt>> tryInspectWallRecoveryReceipts(final Set<UUID> instances) {
        final Set<UUID> requested = Set.copyOf(instances);
        if (requested.size() > 64) throw new IllegalArgumentException("wall recovery query exceeds inventory bound");
        if (stateLock.isHeldByCurrentThread() || !stateLock.tryLock()) return Optional.empty();
        try {
            if (!readable) return Optional.empty();
            final Map<UUID, WallReceipt> result = new LinkedHashMap<>();
            for (final UUID instance : requested) {
                final UUID operation = wallOperationByInstance.get(instance);
                if (operation != null) result.put(instance, wallReceipts.get(operation));
            }
            return Optional.of(Map.copyOf(result));
        } finally { stateLock.unlock(); }
    }

    /** The caller must supply a fresh owner-local removal observation; no inference from UUID absence. */
    public boolean tryConfirmWallRemoval(final WallReceipt expected, final BooleanSupplier observedRemoved) {
        Objects.requireNonNull(expected, "expected");
        return tryTransact(() -> !expected.removalObserved() && expected.equals(wallReceipts.get(expected.operationId()))
                        && observedRemoved.getAsBoolean(),
                () -> {
                    final TransactionFrame frame = requireTransaction();
                    frame.wallBefore().put(expected.operationId(), expected);
                    replaceWallReceipt(expected.withObservedRemoval());
                }, null);
    }

    /** Reads only an exact durable effect acknowledgement; this is not a Minecraft inventory-save receipt. */
    public Optional<Boolean> tryInspectObservedWallRemoval(final WallReceipt expected) {
        Objects.requireNonNull(expected);
        if (stateLock.isHeldByCurrentThread() || !stateLock.tryLock()) return Optional.empty();
        try {
            return readable ? Optional.of(expected.withObservedRemoval().equals(wallReceipts.get(expected.operationId())))
                    : Optional.empty();
        } finally { stateLock.unlock(); }
    }

    /** Reprojects an already acknowledged consume; it neither replays effects nor appends history. */
    public boolean tryRestoreWallProjection(final WallReceipt expected, final BooleanSupplier admission,
                                             final java.util.function.Consumer<Snapshot> projection,
                                             final Runnable restoreExternal) {
        Objects.requireNonNull(expected); Objects.requireNonNull(admission); Objects.requireNonNull(projection);
        return tryTransact(() -> expected.equals(wallReceipts.get(expected.operationId()))
                        && histories.get(expected.instanceId()).revision() == expected.revision() && admission.getAsBoolean(),
                () -> projection.accept(snapshot(expected.instanceId(), histories.get(expected.instanceId()))),
                restoreExternal);
    }

    @Override
    public void save() {
        stateLock.lock();
        try {
            requireLoadedAcknowledgement();
            persistSnapshotAndResetJournal();
        } catch (final RuntimeException | Error failure) {
            readable = false;
            throw failure;
        } finally {
            stateLock.unlock();
        }
    }

    /** Serializes a history mutation, its item projection and the durable write. */
    public <T> T transact(final Supplier<T> mutation,
                                       final Runnable restoreExternal) {
        stateLock.lock();
        try {
            Objects.requireNonNull(mutation, "mutation");
            requireLoadedAcknowledgement();
            if (activeTransaction != null) {
                throw new IllegalStateException("nested Trash history transaction");
            }
            if (journalRecords >= MAX_JOURNAL_RECORDS) save();
            final TransactionFrame frame = new TransactionFrame();
            activeTransaction = frame;
            boolean enteredWrite = false;
            try {
                final T result = mutation.get();
                if (frame.changed()) {
                    for (final UUID instance : frame.historyBefore().keySet()) {
                        final var changed = histories.get(instance);
                        if (changed != null) validateDeveloperEventReceipts(instance, changed);
                    }
                    final long nextSequence = Math.addExact(sequence, 1L);
                    final String payload = journalPayload(frame);
                    enteredWrite = true;
                    journalAppender.append(journal, nextSequence, payload);
                    sequence = nextSequence;
                    journalRecords++;
                }
                return result;
            } catch (final RuntimeException | Error failure) {
                if (enteredWrite) readable = false;
                rollback(frame);
                if (restoreExternal != null) {
                    try {
                        restoreExternal.run();
                    } catch (final RuntimeException | Error restoreFailure) {
                        failure.addSuppressed(restoreFailure);
                    }
                }
                throw failure;
            } finally {
                activeTransaction = null;
            }
        } finally {
            stateLock.unlock();
        }
    }

    /** Refuses another writer immediately; admitted work still uses the native synchronous WAL transaction. */
    public boolean tryTransact(final BooleanSupplier admission, final Runnable mutation,
                               final Runnable restoreExternal) {
        return tryTransact(admission, () -> true, mutation, restoreExternal);
    }

    /** Repeatable freshness surrounds compaction; a single-use permit is claimed only at final mutation admission. */
    public boolean tryTransact(final BooleanSupplier admission, final BooleanSupplier finalAdmission,
                               final Runnable mutation, final Runnable restoreExternal) {
        Objects.requireNonNull(admission, "admission");
        Objects.requireNonNull(finalAdmission, "finalAdmission");
        Objects.requireNonNull(mutation, "mutation");
        if (stateLock.isHeldByCurrentThread() || !stateLock.tryLock()) return false;
        try {
            requireLoadedAcknowledgement();
            if (!admission.getAsBoolean()) return false;
            return transact(() -> {
                if (!admission.getAsBoolean() || !finalAdmission.getAsBoolean()) return false;
                mutation.run();
                return true;
            }, restoreExternal);
        } finally {
            stateLock.unlock();
        }
    }

    public Snapshot createAndRecord(final UUID instanceId, final String baseId,
                                                 final String phase, final TrashHistoryEvent event,
                                                 final UUID actor, final String detail) {
        stateLock.lock();
        try {
            Objects.requireNonNull(instanceId, "instanceId");
            validateIdentity(baseId, phase);
            if (histories.containsKey(instanceId)) {
                throw new IllegalStateException("a Trash instance UUID már létezik");
            }
            if (histories.size() >= MAX_INSTANCES) {
                throw new IllegalStateException("a Trash history instance hard cap betelt");
            }
            final long now = System.currentTimeMillis();
            final StoredHistory initial = new StoredHistory(baseId, phase, 0L, now, now,
                    List.of(), Set.of());
            final StoredHistory recorded = append(initial, event, actor, detail, now);
            putHistory(instanceId, recorded);
            return snapshot(instanceId, recorded);
        } finally {
            stateLock.unlock();
        }
    }

    public Snapshot record(final UUID instanceId, final String baseId,
                                        final String phase, final TrashHistoryEvent event,
                                        final UUID actor, final String detail) {
        stateLock.lock();
        try {
            final StoredHistory current = requireMatching(instanceId, baseId, phase);
            final StoredHistory recorded = append(current, event, actor, detail,
                    System.currentTimeMillis());
            putHistory(instanceId, recorded);
            return snapshot(instanceId, recorded);
        } finally {
            stateLock.unlock();
        }
    }

    public Snapshot observeOwner(final UUID instanceId, final String baseId,
                                              final String phase, final UUID owner,
                                              final boolean king) {
        stateLock.lock();
        try {
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
            putHistory(instanceId, current);
            return snapshot(instanceId, current);
        } finally {
            stateLock.unlock();
        }
    }

    public Snapshot transform(final UUID instanceId, final String baseId,
                                           final String fromPhase, final String toPhase,
                                           final UUID actor) {
        stateLock.lock();
        try {
            final StoredHistory current = requireMatching(instanceId, baseId, fromPhase);
            if (!catalog.isKnownPhase(baseId, toPhase) || "base".equals(toPhase)) {
                throw new IllegalArgumentException("ismeretlen vagy érvénytelen Trash célphase");
            }
            final StoredHistory transitioned = new StoredHistory(current.baseId(), toPhase,
                    current.revision(), current.createdAt(), current.updatedAt(), current.events(),
                    current.owners());
            final StoredHistory recorded = append(transitioned, TrashHistoryEvent.TRANSFORMED,
                    actor, fromPhase + "->" + toPhase, System.currentTimeMillis());
            putHistory(instanceId, recorded);
            return snapshot(instanceId, recorded);
        } finally {
            stateLock.unlock();
        }
    }

    public void putVendorReceipt(final UUID operationId, final UUID actor,
                                              final String baseId, final String phase,
                                              final List<Snapshot> units) {
        stateLock.lock();
        try {
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
            if (vendorReceipts.containsKey(operationId)) {
                throw new IllegalStateException("a Trash vendor receipt már létezik");
            }
            putVendorReceipt(operationId, receipt);
        } finally {
            stateLock.unlock();
        }
    }

    public Optional<VendorReceipt> findVendorReceipt(final UUID operationId) {
        stateLock.lock();
        try {
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
        } finally {
            stateLock.unlock();
        }
    }

    public boolean removeVendorReceipt(final UUID operationId) {
        stateLock.lock();
        try {
            if (!vendorReceipts.containsKey(operationId)) return false;
            removeVendorReceiptInternal(operationId);
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    public Optional<Snapshot> find(final UUID instanceId) {
        stateLock.lock();
        try {
            final StoredHistory history = histories.get(instanceId);
            return history == null ? Optional.empty() : Optional.of(snapshot(instanceId, history));
        } finally {
            stateLock.unlock();
        }
    }

    public boolean matches(final UUID instanceId, final String baseId,
                                        final String phase, final long revision) {
        stateLock.lock();
        try {
            final StoredHistory history = histories.get(instanceId);
            return history != null && history.baseId().equals(baseId) && history.phase().equals(phase)
                    && history.revision() == revision && !unresolvedDeveloperByInstance.containsKey(instanceId);
        } finally {
            stateLock.unlock();
        }
    }

    public int size() {
        stateLock.lock();
        try {
            return histories.size();
        } finally {
            stateLock.unlock();
        }
    }

    /** Refuses rather than waiting for storage or exposing a transaction's unacknowledged candidate. */
    public Optional<Inspection> tryInspect(final UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (stateLock.isHeldByCurrentThread() || !stateLock.tryLock()) return Optional.empty();
        try {
            if (!readable || unresolvedDeveloperByInstance.containsKey(instanceId)) return Optional.empty();
            final StoredHistory history = histories.get(instanceId);
            final UUID operation = wallOperationByInstance.get(instanceId);
            final WallReceipt receipt = operation == null ? null : wallReceipts.get(operation);
            return Optional.of(new Inspection(sequence, history == null
                    ? Optional.empty() : Optional.of(snapshot(instanceId, history)),
                    receipt == null || receipt.removalObserved() ? Optional.empty() : Optional.of(receipt)));
        } finally {
            stateLock.unlock();
        }
    }

    public record Inspection(long sequence, Optional<Snapshot> history, Optional<WallReceipt> pendingWall) {
        public Inspection { Objects.requireNonNull(history, "history"); Objects.requireNonNull(pendingWall, "pendingWall"); }
    }

    private void requireLoadedAcknowledgement() {
        // Rolled-back memory cannot overwrite a write whose durable outcome has not been assessed.
        if (!readable) throw new IllegalStateException("Trash history requires successful load assessment");
    }

    @FunctionalInterface
    interface JournalAppender {
        void append(TrashHistoryJournal journal, long sequence, String payload);
    }

    private void putHistory(final UUID instanceId, final StoredHistory history) {
        final TransactionFrame frame = requireTransaction();
        final UUID wall = wallOperationByInstance.get(instanceId);
        if (wall != null && unresolvedWallOperations.contains(wall)) {
            throw new IllegalStateException("unresolved wall consumption fences further instance mutation");
        }
        if (unresolvedDeveloperByInstance.containsKey(instanceId)) {
            throw new IllegalStateException("unobserved developer projection fences further instance mutation");
        }
        if (!frame.historyBefore().containsKey(instanceId)) {
            frame.historyBefore().put(instanceId, histories.get(instanceId));
        }
        histories.put(instanceId, history);
    }

    private void putVendorReceipt(final UUID operationId, final StoredVendorReceipt receipt) {
        final TransactionFrame frame = requireTransaction();
        if (!frame.receiptBefore().containsKey(operationId)) {
            frame.receiptBefore().put(operationId, vendorReceipts.get(operationId));
        }
        vendorReceipts.put(operationId, receipt);
    }

    private void removeVendorReceiptInternal(final UUID operationId) {
        final TransactionFrame frame = requireTransaction();
        if (!frame.receiptBefore().containsKey(operationId)) {
            frame.receiptBefore().put(operationId, vendorReceipts.get(operationId));
        }
        vendorReceipts.remove(operationId);
    }

    private TransactionFrame requireTransaction() {
        if (activeTransaction == null) {
            throw new IllegalStateException("Trash history mutation durable transaction nélkül");
        }
        return activeTransaction;
    }

    private void rollback(final TransactionFrame frame) {
        for (final Map.Entry<UUID, StoredHistory> entry : frame.historyBefore().entrySet()) {
            if (entry.getValue() == null) histories.remove(entry.getKey());
            else histories.put(entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<UUID, StoredVendorReceipt> entry : frame.receiptBefore().entrySet()) {
            if (entry.getValue() == null) vendorReceipts.remove(entry.getKey());
            else vendorReceipts.put(entry.getKey(), entry.getValue());
        }
        for (final var entry : frame.wallBefore().entrySet()) {
            if (entry.getValue() == null) removeWallReceipt(entry.getKey());
            else replaceWallReceipt(entry.getValue());
        }
        for (final var entry : frame.developerBefore().entrySet()) {
            removeDeveloperReceipt(entry.getKey());
            if (entry.getValue() != null) replaceDeveloperReceipt(entry.getValue());
        }
    }

    private String journalPayload(final TransactionFrame frame) {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", JOURNAL_SCHEMA_VERSION);
        final List<String> removedHistories = new ArrayList<>();
        for (final UUID instanceId : frame.historyBefore().keySet()) {
            final StoredHistory history = histories.get(instanceId);
            if (history == null) removedHistories.add(instanceId.toString());
            else writeHistory(yaml, "histories." + instanceId, history);
        }
        yaml.set("removed-histories", removedHistories);
        final List<String> removedReceipts = new ArrayList<>();
        for (final UUID operationId : frame.receiptBefore().keySet()) {
            final StoredVendorReceipt receipt = vendorReceipts.get(operationId);
            if (receipt == null) removedReceipts.add(operationId.toString());
            else writeVendorReceipt(yaml, "vendor-receipts." + operationId, receipt);
        }
        yaml.set("removed-vendor-receipts", removedReceipts);
        final List<String> removedWalls = new ArrayList<>();
        for (final UUID operation : frame.wallBefore().keySet()) {
            final WallReceipt receipt = wallReceipts.get(operation);
            if (receipt == null) removedWalls.add(operation.toString());
            else writeWallReceipt(yaml, "wall-receipts." + operation, receipt);
        }
        yaml.set("removed-wall-receipts", removedWalls);
        for (final UUID operation : frame.developerBefore().keySet()) {
            writeDeveloperReceipt(yaml, "developer-receipts." + operation, developerReceipts.get(operation));
        }
        return yaml.saveToString();
    }

    private void applyJournalRecord(final TrashHistoryJournal.Record record) {
        replayingJournal = true;
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.loadFromString(record.payload());
            } catch (final InvalidConfigurationException malformed) {
                journal.corrupt("nem értelmezhető Trash history journal payload");
                throw new AssertionError("unreachable", malformed);
            }
            final int schema = yaml.getInt("schema-version", 0);
            if (schema < 1 || schema > JOURNAL_SCHEMA_VERSION) {
                journal.corrupt("ismeretlen Trash history journal payload schema");
            }
            if (schema == 1 && (yaml.contains("wall-receipts") || yaml.contains("removed-wall-receipts"))) {
                journal.corrupt("wall receipt in legacy journal schema");
            }
            if (schema < 4 && yaml.contains("developer-receipts") || yaml.contains("removed-developer-receipts")) {
                journal.corrupt("invalid developer receipt journal schema or retirement");
            }
            for (final String raw : yaml.getStringList("removed-histories")) {
                histories.remove(parseJournalUuid(raw, "removed history"));
            }
            final ConfigurationSection changedHistories =
                    yaml.getConfigurationSection("histories");
            if (changedHistories != null) {
                final Set<String> keys = changedHistories.getKeys(false);
                for (final String key : keys) {
                    histories.remove(parseJournalUuid(key, "history key"));
                }
                if (histories.size() + keys.size() > MAX_INSTANCES) {
                    journal.corrupt("a Trash history journal túllépi az instance hard capet");
                }
                loadHistories(changedHistories);
            }
            for (final String raw : yaml.getStringList("removed-vendor-receipts")) {
                vendorReceipts.remove(parseJournalUuid(raw, "removed vendor receipt"));
            }
            final ConfigurationSection changedReceipts =
                    yaml.getConfigurationSection("vendor-receipts");
            if (changedReceipts != null) {
                final Set<String> keys = changedReceipts.getKeys(false);
                for (final String key : keys) {
                    vendorReceipts.remove(parseJournalUuid(key, "vendor receipt key"));
                }
                if (vendorReceipts.size() + keys.size() > MAX_VENDOR_OPERATIONS) {
                    journal.corrupt("a Trash history journal túllépi a vendor receipt hard capet");
                }
                loadVendorReceipts(changedReceipts);
            }
            if (yaml.contains("removed-wall-receipts") && !yaml.isList("removed-wall-receipts")) {
                journal.corrupt("removed wall receipts are not a list");
            }
            for (final Object raw : yaml.getList("removed-wall-receipts", List.of())) {
                if (!(raw instanceof String)) journal.corrupt("removed wall receipt is not a UUID string");
                if (schema >= 3) journal.corrupt("observed wall receipts cannot be retired without physical durability evidence");
                final UUID operation = parseJournalUuid((String) raw, "removed wall receipt");
                final WallReceipt previous = wallReceipts.get(operation);
                if (previous == null || previous.removalObserved()) {
                    journal.corrupt("completion references an unknown wall operation");
                }
                // Legacy schema 2 removal is an explicit native observed-effect acknowledgement.
                // Retain its existing immutable receipt; absence in an old snapshot proves nothing.
                replaceWallReceipt(previous.withObservedRemoval());
            }
            loadWallReceipts(yaml, "wall-receipts", schema >= 3, true);
            loadDeveloperReceipts(yaml, "developer-receipts", true);
            final Set<String> changedInstances = new java.util.HashSet<>(yaml.getStringList("removed-histories"));
            if (changedHistories != null) changedInstances.addAll(changedHistories.getKeys(false));
            for (final String instance : changedInstances) {
                final UUID operation = wallOperationByInstance.get(parseJournalUuid(instance, "changed history"));
                if (operation != null) {
                    try { validateWallReceipt(wallReceipts.get(operation)); }
                    catch (final RuntimeException invalid) { journal.corrupt("stale retained wall receipt"); }
                }
            }
            for (final TrashDeveloperReceipt receipt : developerReceipts.values()) {
                if (changedInstances.contains(receipt.instanceId().toString())) {
                    try { validateDeveloperReceipt(receipt); }
                    catch (final RuntimeException invalid) { journal.corrupt("stale retained developer receipt"); }
                }
            }
        } finally {
            replayingJournal = false;
        }
    }

    private UUID parseJournalUuid(final String raw, final String field) {
        try {
            return UUID.fromString(raw);
        } catch (final RuntimeException invalid) {
            journal.corrupt("érvénytelen Trash history journal " + field);
            throw new AssertionError("unreachable", invalid);
        }
    }

    private void persistSnapshotAndResetJournal() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        yaml.set("last-sequence", sequence);
        for (final Map.Entry<UUID, StoredHistory> entry : histories.entrySet()) {
            final String path = "instances." + entry.getKey();
            writeHistory(yaml, path, entry.getValue());
        }
        for (final Map.Entry<UUID, StoredVendorReceipt> entry : vendorReceipts.entrySet()) {
            final String path = "vendor-operations." + entry.getKey();
            writeVendorReceipt(yaml, path, entry.getValue());
        }
        for (final WallReceipt receipt : wallReceipts.values()) {
            writeWallReceipt(yaml, "wall-operations." + receipt.operationId(), receipt);
        }
        for (final TrashDeveloperReceipt receipt : developerReceipts.values()) {
            writeDeveloperReceipt(yaml, "developer-operations." + receipt.operationId(), receipt);
        }
        try {
            YamlStore.saveAtomic(file, yaml);
        } catch (final IOException failure) {
            throw new IllegalStateException("Trash history mentése sikertelen", failure);
        }
        journal.reset();
        journalRecords = 0;
    }

    private static void writeHistory(final YamlConfiguration yaml, final String path,
                                     final StoredHistory history) {
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

    private static void writeVendorReceipt(final YamlConfiguration yaml, final String path,
                                           final StoredVendorReceipt receipt) {
        yaml.set(path + ".actor", receipt.actor().toString());
        yaml.set(path + ".trash-id", receipt.baseId());
        yaml.set(path + ".phase", receipt.phase());
        yaml.set(path + ".amount", receipt.amount());
        yaml.set(path + ".units", receipt.units().stream()
                .map(unit -> unit.instanceId() + ":" + unit.revision()).toList());
    }

    private static void writeWallReceipt(final YamlConfiguration yaml, final String path,
                                         final WallReceipt receipt) {
        yaml.set(path + ".actor", receipt.actor().toString());
        yaml.set(path + ".world", receipt.worldId().toString());
        yaml.set(path + ".projectile", receipt.projectileId().toString());
        yaml.set(path + ".instance", receipt.instanceId().toString());
        yaml.set(path + ".revision", receipt.revision());
        yaml.set(path + ".trash-id", receipt.baseId());
        yaml.set(path + ".phase", receipt.phase());
        yaml.set(path + ".consumed-at", receipt.consumedAt());
        yaml.set(path + ".before-revision", receipt.beforeRevision());
        yaml.set(path + ".x", receipt.field().center().x());
        yaml.set(path + ".y", receipt.field().center().y());
        yaml.set(path + ".z", receipt.field().center().z());
        yaml.set(path + ".radius", receipt.field().radius());
        yaml.set(path + ".expires-at", receipt.field().expiresAt());
        yaml.set(path + ".reservation", receipt.field().reservationToken());
        yaml.set(path + ".removal-observed", receipt.removalObserved());
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
        validateDeveloperEvent(event, actor, detail);
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
                validateDeveloperEvent(type, actor, detail);
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

    private static void validateDeveloperEvent(final TrashHistoryEvent event, final UUID actor, final String detail) {
        if (!event.developer()) return;
        if (!hu.taliann.icesmp.security.HiddenDevAuthority.isDeveloper(actor)
                || !UUID.fromString(detail).toString().equals(detail)) {
            throw new IllegalArgumentException("Developer history requires primary actor and exact operation UUID");
        }
    }

    private void validateDeveloperEventReceipts(final UUID instance, final StoredHistory history) {
        for (final var event : history.events()) {
            if (!event.type().developer()) continue;
            final var receipt = developerReceipts.get(UUID.fromString(event.detail()));
            if (receipt == null || !receipt.instanceId().equals(instance) || !receipt.actor().equals(event.actor())
                    || !receipt.baseId().equals(history.baseId()) || receipt.afterRevision() < event.revision()) {
                if (activeTransaction != null) throw new IllegalStateException("Developer event requires its native transaction receipt");
                corrupt("Developer history event has no matching native receipt");
            }
        }
    }

    private void corrupt(final String reason) {
        if (replayingJournal) {
            journal.corrupt(reason);
            return;
        }
        YamlStore.failCorrupt(file, logger, reason);
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

    private record TransactionFrame(Map<UUID, StoredHistory> historyBefore,
                                    Map<UUID, StoredVendorReceipt> receiptBefore,
                                    Map<UUID, WallReceipt> wallBefore,
                                    Map<UUID, TrashDeveloperReceipt> developerBefore) {
        private TransactionFrame() {
            this(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        private boolean changed() {
            return !historyBefore.isEmpty() || !receiptBefore.isEmpty() || !wallBefore.isEmpty() || !developerBefore.isEmpty();
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

    public record WallReceipt(UUID operationId, UUID actor, UUID worldId, UUID projectileId,
                              UUID instanceId, long revision, String baseId, String phase, long consumedAt,
                              TrashRuleFieldService.RuleField field, long beforeRevision, boolean removalObserved) {
        public WallReceipt(UUID operationId, UUID actor, UUID worldId, UUID projectileId,
                           UUID instanceId, long revision, String baseId, String phase, long consumedAt,
                           TrashRuleFieldService.RuleField field, long beforeRevision) {
            this(operationId, actor, worldId, projectileId, instanceId, revision, baseId, phase, consumedAt,
                    field, beforeRevision, false);
        }
        public WallReceipt {
            Objects.requireNonNull(operationId); Objects.requireNonNull(actor);
            Objects.requireNonNull(worldId); Objects.requireNonNull(projectileId);
            Objects.requireNonNull(instanceId); Objects.requireNonNull(baseId); Objects.requireNonNull(phase);
            Objects.requireNonNull(field);
            if (beforeRevision < 1 || revision <= beforeRevision || consumedAt < 1
                    || consumedAt >= field.expiresAt() || baseId.isBlank() || phase.isBlank()
                    || field.kind() != TrashRuleFieldService.FieldKind.PROJECTILE_WALL
                    || field.reservationToken() == null || !operationId.equals(field.id())
                    || !actor.equals(field.owner()) || !worldId.equals(field.center().world())) {
                throw new IllegalArgumentException("invalid wall receipt");
            }
        }
        private WallReceipt withObservedRemoval() {
            return new WallReceipt(operationId, actor, worldId, projectileId, instanceId, revision, baseId, phase,
                    consumedAt, field, beforeRevision, true);
        }
    }

    public record VendorReceipt(UUID operationId, UUID actor, String baseId, String phase,
                                int amount, List<Snapshot> units) {
        public VendorReceipt {
            units = List.copyOf(units);
        }
    }
}
