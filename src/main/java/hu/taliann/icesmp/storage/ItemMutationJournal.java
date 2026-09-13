package hu.taliann.icesmp.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import hu.taliann.icesmp.itemization.ItemDeveloperMutation;

/** Narrow WAL for one-player itemization inventory snapshots; not a general transaction framework. */
public final class ItemMutationJournal {

    private static final int MAX_PENDING_OPERATIONS = 256;
    private static final int MAX_ENCODED_SLOT_LENGTH = 1_048_576;
    private static final int MAX_DEVELOPER_RECEIPTS = 1024;
    private static final long MAX_DEVELOPER_PROJECTION_CHARS = 16L * 1024 * 1024;
    private static final Set<String> OPERATION_TYPES = Set.of("REROLL", "RUNE", "ASCEND", "SALVAGE");

    public record Entry(UUID operationId, UUID playerId, String type, UUID itemId,
                        List<String> beforeInventory, List<String> afterInventory,
                        long createdAt) {
        public Entry {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(itemId, "itemId");
            type = Objects.requireNonNull(type, "type").trim();
            beforeInventory = List.copyOf(beforeInventory);
            afterInventory = List.copyOf(afterInventory);
            if (!knownOperation(type) || createdAt < 0L
                    || beforeInventory.size() != afterInventory.size()
                    || beforeInventory.size() > 64
                    || beforeInventory.stream().anyMatch(ItemMutationJournal::invalidSlot)
                    || afterInventory.stream().anyMatch(ItemMutationJournal::invalidSlot)) {
                throw new IllegalArgumentException("invalid item mutation inventory snapshot");
            }
        }
    }

    private final File file;
    private final Logger logger;
    private final Consumer<Runnable> scheduler;
    private final Writer writer;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, ItemDeveloperReceipt> developerReceipts = new LinkedHashMap<>();
    private volatile boolean healthy = true;
    private record DeveloperReadView(boolean available, Map<UUID, ItemDeveloperReceipt> receipts, Set<UUID> pendingPlayers) { }
    private volatile DeveloperReadView developerView = new DeveloperReadView(false, Map.of(), Set.of());

    @FunctionalInterface
    interface Writer { void save(File file, YamlConfiguration yaml) throws IOException; }

    public ItemMutationJournal(final JavaPlugin plugin, final File file, final Logger logger) {
        this(file, logger, action -> plugin.getServer().getAsyncScheduler().runNow(plugin, task -> action.run()),
                YamlStore::saveAtomic);
    }

    ItemMutationJournal(final File file, final Logger logger, final Consumer<Runnable> scheduler,
                        final Writer writer) {
        this.file = file;
        this.logger = logger;
        this.scheduler = Objects.requireNonNull(scheduler);
        this.writer = Objects.requireNonNull(writer);
        YamlStore.registerCriticalWrite(file);
    }

    public synchronized void load() {
        developerView = new DeveloperReadView(false, Map.of(), Set.of());
        entries.clear();
        developerReceipts.clear();
        if (!file.exists()) { publishDeveloperView(); return; }
        try {
            if (file.length() > 64L * 1024 * 1024) throw new IllegalStateException("oversized item mutation journal");
            final YamlConfiguration yaml = YamlStore.loadTracked(file, logger);
            final int schema = yaml.getInt("schema", 1);
            if (schema < 1 || schema > 3) throw new IllegalStateException("unsupported item mutation journal schema");
            final ConfigurationSection root = yaml.getConfigurationSection("operations");
            if (yaml.contains("operations") && root == null) throw new IllegalStateException("invalid operations section");
            if (root != null && root.getKeys(false).size() > MAX_PENDING_OPERATIONS) {
                throw new IllegalStateException("too many pending item mutations");
            }
            final Set<UUID> pendingPlayers = new HashSet<>();
            for (final String rawId : root == null ? Set.<String>of() : root.getKeys(false)) {
                final UUID id = UUID.fromString(rawId);
                final String path = "operations." + rawId + '.';
                final Entry entry = readEntry(yaml, path, id);
                if (!pendingPlayers.add(entry.playerId())) {
                    throw new IllegalStateException("multiple pending mutations for one player");
                }
                entries.put(id, entry);
            }
            final var receipts = yaml.getConfigurationSection("developer-receipts");
            if (yaml.contains("developer-receipts") && receipts == null) throw new IllegalStateException("invalid developer receipts section");
            if (receipts != null && (schema < 2 || receipts.getKeys(false).size() > MAX_DEVELOPER_RECEIPTS)) {
                throw new IllegalStateException("invalid developer receipt count/schema");
            }
            long retained = 0;
            for (final String rawId : receipts == null ? Set.<String>of() : receipts.getKeys(false)) {
                final UUID id = UUID.fromString(rawId);
                final String path = "developer-receipts." + rawId + '.';
                final var receipt = new ItemDeveloperReceipt(readEntry(yaml, path, id),
                        ItemDeveloperMutation.Kind.valueOf(yaml.getString(path + "kind", "")),
                        yaml.getInt(path + "source-slot", -1), yaml.getInt(path + "target-slot", -1),
                        UUID.fromString(yaml.getString(path + "result-item", "")),
                        yaml.getLong(path + "before-revision", -1), yaml.getLong(path + "after-revision", -1),
                        ItemDeveloperReceipt.State.valueOf(yaml.getString(path + "state", "")),
                        yaml.getLong(path + "resolved-at", -1), readReversal(yaml, path, schema));
                if (receipt.state() == ItemDeveloperReceipt.State.PENDING
                        ? !receipt.entry().equals(entries.get(id)) : entries.containsKey(id)) {
                    throw new IllegalStateException("developer pending/terminal receipt conflict");
                }
                retained += projectionChars(receipt.entry());
                if (retained > MAX_DEVELOPER_PROJECTION_CHARS) throw new IllegalStateException("developer receipt projection capacity");
                developerReceipts.put(id, receipt);
            }
            for (final var receipt : developerReceipts.values()) {
                if (!validReversal(receipt)) throw new IllegalStateException("invalid developer compensation chain");
            }
            if (entries.values().stream().anyMatch(entry -> entry.type().startsWith("DEV_")
                    && !developerReceipts.containsKey(entry.operationId()))) {
                throw new IllegalStateException("orphan developer item intent");
            }
            publishDeveloperView();
        } catch (final Exception corrupt) {
            healthy = false;
            YamlStore.failCorrupt(file, logger, "Item mutation journal: " + corrupt.getMessage());
        }
    }

    public boolean isHealthy() { return healthy && !YamlStore.isLoadFailed(file); }

    public synchronized List<Entry> entriesFor(final UUID playerId) {
        return entries.values().stream().filter(entry -> entry.playerId().equals(playerId)).toList();
    }

    /** Exact operation lookup used by the audited admin resolution path. */
    public synchronized Optional<Entry> find(final UUID operationId) {
        return Optional.ofNullable(entries.get(operationId));
    }

    /** Unavailable storage must not be confused with an operation that never existed. */
    public Optional<ItemDeveloperReceipt> findDeveloper(final UUID operationId) {
        final var view = developerView;
        if (!isHealthy() || !view.available()) throw new IllegalStateException("Item mutation journal unavailable");
        return Optional.ofNullable(view.receipts().get(operationId));
    }

    /** Owner callbacks never wait on the async writer's fsync monitor. */
    public boolean hasPendingForDeveloper(final UUID playerId) {
        final var view = developerView;
        if (!isHealthy() || !view.available()) throw new IllegalStateException("Item mutation journal unavailable");
        return view.pendingPlayers().contains(playerId);
    }

    public Optional<ItemDeveloperReceipt> pendingDeveloper(final UUID playerId) {
        final var view = developerView;
        if (!isHealthy() || !view.available()) throw new IllegalStateException("Item mutation journal unavailable");
        return view.receipts().values().stream().filter(receipt -> receipt.entry().playerId().equals(playerId)
                && receipt.state() == ItemDeveloperReceipt.State.PENDING).findFirst();
    }

    public CompletionStage<Boolean> prepareDeveloper(final ItemDeveloperReceipt receipt) {
        Objects.requireNonNull(receipt);
        if (receipt.state() != ItemDeveloperReceipt.State.PENDING) return CompletableFuture.completedFuture(false);
        return update(() -> {
            final Entry entry = receipt.entry();
            if (!canPrepare(entry) || !validReversal(receipt) || developerReceipts.size() >= MAX_DEVELOPER_RECEIPTS
                    || developerReceipts.values().stream().mapToLong(value -> projectionChars(value.entry())).sum()
                    + projectionChars(entry) > MAX_DEVELOPER_PROJECTION_CHARS) return false;
            entries.put(entry.operationId(), entry);
            developerReceipts.put(entry.operationId(), receipt);
            // On an uncertain write retain the intent in memory and close the writer. A new
            // instance must read actual disk before it can make an idempotency decision.
            return flush();
        });
    }

    /** Only the native owner observation path settles a developer result; it never erases it. */
    public CompletionStage<Boolean> resolveDeveloper(final ItemDeveloperReceipt expected,
                                                      final ItemDeveloperReceipt.State state) {
        Objects.requireNonNull(expected); Objects.requireNonNull(state);
        if (state == ItemDeveloperReceipt.State.PENDING) return CompletableFuture.completedFuture(false);
        return update(() -> {
            if (!isHealthy()) return false;
            final UUID id = expected.entry().operationId();
            final var current = developerReceipts.get(id);
            if (current == null || !sameRequest(current, expected)) return false;
            if (current.state() != ItemDeveloperReceipt.State.PENDING) return current.state() == state;
            if (!current.entry().equals(entries.get(id))) return false;
            developerReceipts.put(id, current.resolve(state, System.currentTimeMillis()));
            entries.remove(id);
            return flush();
        });
    }

    private static boolean sameRequest(ItemDeveloperReceipt left, ItemDeveloperReceipt right) {
        return left.entry().equals(right.entry()) && left.kind() == right.kind()
                && left.sourceSlot() == right.sourceSlot() && left.targetSlot() == right.targetSlot()
                && left.resultItemId().equals(right.resultItemId()) && left.beforeRevision() == right.beforeRevision()
                && left.afterRevision() == right.afterRevision() && left.reverses().equals(right.reverses());
    }

    private boolean validReversal(ItemDeveloperReceipt receipt) {
        if (receipt.reverses().isEmpty()) return true;
        final UUID originalId = receipt.reverses().orElseThrow();
        final var original = developerReceipts.get(originalId);
        if (original == null || original.state() != ItemDeveloperReceipt.State.OBSERVED || original.reverses().isPresent()
                || receipt.kind() != original.kind() || !receipt.entry().playerId().equals(original.entry().playerId())
                || !receipt.entry().itemId().equals(original.resultItemId()) || receipt.sourceSlot() != original.targetSlot()
                || receipt.beforeRevision() != original.afterRevision()) return false;
        return receipt.state() == ItemDeveloperReceipt.State.ABORTED || developerReceipts.values().stream().noneMatch(other ->
                !other.entry().operationId().equals(receipt.entry().operationId()) && other.reverses().equals(receipt.reverses())
                        && other.state() != ItemDeveloperReceipt.State.ABORTED);
    }

    private static Optional<UUID> readReversal(YamlConfiguration yaml, String path, int schema) {
        final Object raw = yaml.get(path + "reverses", "");
        if (!(raw instanceof String value) || schema < 3 && !value.isEmpty()) throw new IllegalStateException("invalid compensation schema");
        return value.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(value));
    }

    private boolean canPrepare(Entry entry) {
        return isHealthy() && !entries.containsKey(entry.operationId())
                && !developerReceipts.containsKey(entry.operationId()) && entries.size() < MAX_PENDING_OPERATIONS
                && entries.values().stream().noneMatch(existing -> existing.playerId().equals(entry.playerId()));
    }

    private CompletionStage<Boolean> update(Supplier<Boolean> change) {
        final var result = new CompletableFuture<Boolean>();
        try { scheduler.accept(() -> {
            try { synchronized (this) { result.complete(change.get()); } }
            catch (RuntimeException failure) { healthy = false; result.completeExceptionally(failure); }
        }); } catch (RuntimeException rejected) { result.completeExceptionally(rejected); }
        return result;
    }

    public CompletionStage<Boolean> prepare(final Entry entry) {
        if (entry.type().startsWith("DEV_")) return CompletableFuture.completedFuture(false);
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        try {
            scheduler.accept(() -> {
                synchronized (this) {
                    if (!canPrepare(entry)) {
                        result.complete(false);
                        return;
                    }
                    if (entries.size() >= MAX_PENDING_OPERATIONS
                            || entries.values().stream().anyMatch(existing ->
                            existing.playerId().equals(entry.playerId()))) {
                        result.complete(false);
                        return;
                    }
                    entries.put(entry.operationId(), entry);
                    if (flush()) result.complete(true);
                    else {
                        entries.remove(entry.operationId());
                        result.complete(false);
                    }
                }
            });
        } catch (final RuntimeException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }

    public CompletionStage<Boolean> complete(final UUID operationId) {
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        try {
            scheduler.accept(() -> {
                synchronized (this) {
                    if (!isHealthy() || developerReceipts.containsKey(operationId)) {
                        result.complete(false);
                        return;
                    }
                    final Entry removed = entries.remove(operationId);
                    if (removed == null) result.complete(true);
                    else if (flush()) result.complete(true);
                    else {
                        entries.put(operationId, removed);
                        result.complete(false);
                    }
                }
            });
        } catch (final RuntimeException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }

    private boolean flush() {
        if (!isHealthy()) return false;
        developerView = new DeveloperReadView(false, Map.of(), Set.of());
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 3);
        for (final Entry entry : entries.values()) {
            final String path = "operations." + entry.operationId() + '.';
            writeEntry(yaml, path, entry);
        }
        for (final var receipt : developerReceipts.values()) {
            final String path = "developer-receipts." + receipt.entry().operationId() + '.';
            writeEntry(yaml, path, receipt.entry());
            yaml.set(path + "kind", receipt.kind().name());
            yaml.set(path + "source-slot", receipt.sourceSlot());
            yaml.set(path + "target-slot", receipt.targetSlot());
            yaml.set(path + "result-item", receipt.resultItemId().toString());
            yaml.set(path + "before-revision", receipt.beforeRevision());
            yaml.set(path + "after-revision", receipt.afterRevision());
            yaml.set(path + "state", receipt.state().name());
            yaml.set(path + "resolved-at", receipt.resolvedAt());
            yaml.set(path + "reverses", receipt.reverses().map(UUID::toString).orElse(""));
        }
        try {
            writer.save(file, yaml);
            publishDeveloperView();
            return true;
        } catch (final IOException failure) {
            healthy = false;
            logger.severe("Item mutation journal save failed: " + failure.getMessage());
            return false;
        }
    }

    private void publishDeveloperView() {
        developerView = new DeveloperReadView(isHealthy(), Map.copyOf(developerReceipts), entries.values().stream()
                .map(Entry::playerId).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    private static Entry readEntry(YamlConfiguration yaml, String path, UUID id) {
        return new Entry(id, UUID.fromString(yaml.getString(path + "player", "")),
                yaml.getString(path + "type", ""), UUID.fromString(yaml.getString(path + "item", "")),
                yaml.getStringList(path + "before"), yaml.getStringList(path + "after"),
                yaml.getLong(path + "created-at", -1));
    }
    private static void writeEntry(YamlConfiguration yaml, String path, Entry entry) {
        yaml.set(path + "player", entry.playerId().toString());
        yaml.set(path + "type", entry.type());
        yaml.set(path + "item", entry.itemId().toString());
        yaml.set(path + "before", entry.beforeInventory());
        yaml.set(path + "after", entry.afterInventory());
        yaml.set(path + "created-at", entry.createdAt());
    }
    private static long projectionChars(Entry entry) {
        return entry.beforeInventory().stream().mapToLong(String::length).sum()
                + entry.afterInventory().stream().mapToLong(String::length).sum();
    }
    private static boolean knownOperation(String type) {
        if (OPERATION_TYPES.contains(type)) return true;
        return java.util.Arrays.stream(ItemDeveloperMutation.Kind.values()).anyMatch(kind -> type.equals("DEV_" + kind.name())
                || kind != ItemDeveloperMutation.Kind.REFRESH_PRESENTATION && type.equals("DEV_REVERT_" + kind.name()));
    }

    public static List<String> encodeInventory(final ItemStack[] contents) {
        final ArrayList<String> encoded = new ArrayList<>(contents.length);
        for (final ItemStack item : contents) {
            encoded.add(item == null || item.getType().isAir() ? "-"
                    : Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        return List.copyOf(encoded);
    }

    public static ItemStack[] decodeInventory(final List<String> encoded) {
        final ItemStack[] contents = new ItemStack[encoded.size()];
        for (int slot = 0; slot < encoded.size(); slot++) {
            final String value = encoded.get(slot);
            contents[slot] = "-".equals(value) ? null
                    : ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
        }
        return contents;
    }

    private static boolean invalidSlot(final String encoded) {
        return encoded == null || encoded.length() > MAX_ENCODED_SLOT_LENGTH
                || (!"-".equals(encoded) && encoded.isBlank());
    }
}
