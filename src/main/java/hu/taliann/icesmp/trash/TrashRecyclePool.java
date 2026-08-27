package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Durable exact-item recycle authority and forward-recoverable vendor transaction journal. */
public final class TrashRecyclePool implements PersistentStore {

    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_PER_IDENTITY = 64;
    private static final int MAX_OPEN_SALES = 1_024;

    private final JavaPlugin plugin;
    private final TrashCatalog catalog;
    private final TrashItemFactory itemFactory;
    private final TrashHistoryService history;
    private final File file;
    private final Map<String, ArrayDeque<ItemStack>> pool = new LinkedHashMap<>();
    private final Map<UUID, SaleTransaction> openSales = new LinkedHashMap<>();

    public TrashRecyclePool(final JavaPlugin plugin, final TrashCatalog catalog,
                            final TrashItemFactory itemFactory,
                            final TrashHistoryService history) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        this.history = Objects.requireNonNull(history, "history");
        this.file = new File(plugin.getDataFolder(), "trash-recycle.yml");
        YamlStore.registerCriticalWrite(file);
    }

    @Override
    public synchronized void load() {
        pool.clear();
        openSales.clear();
        final YamlConfiguration yaml = YamlStore.loadTracked(file, plugin.getLogger());
        if (!file.exists()) return;
        if (yaml.getInt("schema-version", 0) != SCHEMA_VERSION) {
            YamlStore.failCorrupt(file, plugin.getLogger(),
                    "trash recycle schema-version must be exactly " + SCHEMA_VERSION);
        }
        loadPool(yaml.getConfigurationSection("pool"));
        loadSales(yaml.getConfigurationSection("vendor-transactions"));
        reconcileHistoryReceipts();
    }

    private void loadPool(final ConfigurationSection stored) {
        if (stored == null) return;
        for (final String id : stored.getKeys(false)) {
            if (catalog.find(id).isEmpty()) {
                corrupt("ismeretlen Trash recycle identity: " + id);
            }
            final List<?> serialized = stored.getList(id);
            if (serialized == null || serialized.size() > MAX_PER_IDENTITY) {
                corrupt("érvénytelen recycle lista: " + id);
            }
            final ArrayDeque<ItemStack> items = new ArrayDeque<>();
            for (final Object value : serialized) {
                if (!(value instanceof ItemStack)) {
                    corrupt("érvénytelen exact recycle instance: " + id);
                }
                final ItemStack item = ((ItemStack) value).clone();
                if (!validRecycleUnit(id, item)) {
                    corrupt("érvénytelen exact recycle instance: " + id);
                }
                items.addLast(item);
            }
            if (!items.isEmpty()) pool.put(id, items);
        }
    }

    private void loadSales(final ConfigurationSection transactions) {
        if (transactions == null) return;
        if (transactions.getKeys(false).size() > MAX_OPEN_SALES) {
            corrupt("túl sok nyitott Trash vendor tranzakció");
        }
        for (final String rawOperationId : transactions.getKeys(false)) {
            final UUID operationId = parseUuid(rawOperationId, "vendor operation");
            final ConfigurationSection section = transactions.getConfigurationSection(rawOperationId);
            if (section == null) corrupt("a Trash vendor tranzakció nem objektum");
            final UUID playerId = parseUuid(section.getString("player", ""), "vendor player");
            final SaleStage stage = parseStage(section.getString("stage", ""));
            final String trashId = section.getString("trash-id", "").trim();
            final ItemStack source = section.getItemStack("source");
            final int originalAmount = section.getInt("original-amount", 0);
            final int soldAmount = section.getInt("sold-amount", 0);
            final int slot = section.getInt("slot", -1);
            final String currency = section.getString("currency", "").trim();
            final long value = section.getLong("value", 0L);
            final long budgetDay = section.getLong("budget-day", -1L);
            final long budgetBefore = section.getLong("budget-before", -1L);
            final long createdAt = section.getLong("created-at", -1L);
            final List<ItemStack> recycleUnits = itemList(section.getList("recycle-units"));
            if (source == null || !itemFactory.isKnownItem(source)
                    || !trashId.equals(itemFactory.idOf(source).orElse(null))
                    || originalAmount < 1 || originalAmount != source.getAmount()
                    || soldAmount < 1 || soldAmount > originalAmount || slot < 0 || slot > 40
                    || currency.isBlank() || currency.length() > 64 || value < 1L
                    || budgetDay < 0L || budgetBefore < 0L || createdAt < 1L) {
                corrupt("érvénytelen Trash vendor tranzakció: " + rawOperationId);
            }
            final boolean exactRecycle = history.instanceIdOf(source).isPresent()
                    || !catalog.require(trashId).internalKind().isInert();
            final int expectedUnits = stage.ordinal() >= SaleStage.POOL_COMMITTED.ordinal()
                    && exactRecycle ? soldAmount : 0;
            if (recycleUnits.size() != expectedUnits
                    || recycleUnits.stream().anyMatch(item -> !validRecycleUnit(trashId, item))) {
                corrupt("érvénytelen Trash vendor recycle payload: " + rawOperationId);
            }
            final SaleTransaction sale = new SaleTransaction(operationId, playerId, stage,
                    trashId, source.clone(), originalAmount, soldAmount, slot, currency, value,
                    budgetDay, budgetBefore, createdAt, recycleUnits);
            if (openSales.putIfAbsent(operationId, sale) != null) {
                corrupt("duplikált Trash vendor operation: " + rawOperationId);
            }
        }
    }

    /** A committed recycle payload is authoritative; any surviving preparation receipt is stale. */
    private void reconcileHistoryReceipts() {
        for (final SaleTransaction sale : openSales.values()) {
            if (sale.stage().ordinal() >= SaleStage.POOL_COMMITTED.ordinal()) {
                history.completeVendorOperation(sale.operationId());
            }
        }
    }

    @Override
    public synchronized void save() {
        persist();
    }

    public synchronized SaleTransaction prepareSale(final UUID playerId, final int slot,
                                                    final ItemStack source, final int soldAmount,
                                                    final String currency, final long value,
                                                    final long budgetDay,
                                                    final long budgetBefore) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(source, "source");
        if (openSales.size() >= MAX_OPEN_SALES || !itemFactory.isKnownItem(source)
                || soldAmount < 1 || soldAmount > source.getAmount() || slot < 0 || slot > 40
                || currency == null || currency.isBlank() || currency.length() > 64
                || value < 1L || budgetDay < 0L || budgetBefore < 0L) {
            throw new IllegalArgumentException("érvénytelen Trash vendor tranzakció");
        }
        final String trashId = itemFactory.idOf(source).orElseThrow();
        history.validateVendorSale(source, soldAmount);
        final UUID operationId = UUID.randomUUID();
        final SaleTransaction sale = new SaleTransaction(operationId, playerId,
                SaleStage.PREPARED, trashId, source.clone(), source.getAmount(), soldAmount,
                slot, currency, value, budgetDay, budgetBefore, System.currentTimeMillis(),
                List.of());
        openSales.put(operationId, sale);
        persistOrRestore(() -> openSales.remove(operationId));
        return copy(sale);
    }

    public synchronized SaleTransaction markBudgetReserved(final UUID operationId) {
        return advance(operationId, SaleStage.PREPARED, SaleStage.BUDGET_RESERVED);
    }

    public synchronized SaleTransaction markItemRemoved(final UUID operationId) {
        return advance(operationId, SaleStage.BUDGET_RESERVED, SaleStage.ITEM_REMOVED);
    }

    public synchronized SaleTransaction commitRecycle(final UUID operationId) {
        final SaleTransaction current = requireSale(operationId);
        if (current.stage().ordinal() >= SaleStage.POOL_COMMITTED.ordinal()) return copy(current);
        if (current.stage() != SaleStage.ITEM_REMOVED) {
            throw new IllegalStateException("Trash vendor recycle commit rossz fázisban");
        }
        final List<ItemStack> units = history.prepareVendorUnits(operationId, current.source(),
                current.soldAmount(), current.playerId());
        final boolean exactRecycle = history.instanceIdOf(current.source()).isPresent()
                || !catalog.require(current.trashId()).internalKind().isInert();
        if (units.size() != (exactRecycle ? current.soldAmount() : 0)
                || units.stream().anyMatch(item -> !validRecycleUnit(current.trashId(), item))) {
            throw new IllegalStateException("érvénytelen Trash vendor recycle preparation");
        }
        final ArrayDeque<ItemStack> previous = copyDeque(pool.get(current.trashId()));
        final ArrayDeque<ItemStack> instances = pool.computeIfAbsent(
                current.trashId(), ignored -> new ArrayDeque<>());
        for (final ItemStack unit : units) {
            instances.addLast(unit.clone());
            while (instances.size() > MAX_PER_IDENTITY) instances.removeFirst();
        }
        final SaleTransaction next = current.withRecycleUnitsAndStage(
                units, SaleStage.POOL_COMMITTED);
        openSales.put(operationId, next);
        persistOrRestore(() -> {
            restoreDeque(current.trashId(), previous);
            openSales.put(operationId, current);
        });
        try {
            history.completeVendorOperation(operationId);
        } catch (final RuntimeException failure) {
            restoreDeque(current.trashId(), previous);
            openSales.put(operationId, current);
            persist();
            throw failure;
        }
        return copy(next);
    }

    public synchronized SaleTransaction markPaid(final UUID operationId) {
        return advance(operationId, SaleStage.POOL_COMMITTED, SaleStage.PAID);
    }

    public synchronized void completeSale(final UUID operationId) {
        final SaleTransaction current = requireSale(operationId);
        if (current.stage() != SaleStage.PAID) {
            throw new IllegalStateException("Trash vendor completion kifizetés előtt");
        }
        openSales.remove(operationId);
        persistOrRestore(() -> openSales.put(operationId, current));
    }

    public synchronized void cancelPrepared(final UUID operationId) {
        final SaleTransaction current = requireSale(operationId);
        if (current.stage() != SaleStage.PREPARED) {
            throw new IllegalStateException("csak előkészített Trash vendor tranzakció törölhető");
        }
        openSales.remove(operationId);
        persistOrRestore(() -> openSales.put(operationId, current));
    }

    public synchronized Optional<SaleTransaction> findSale(final UUID operationId) {
        final SaleTransaction sale = openSales.get(operationId);
        return sale == null ? Optional.empty() : Optional.of(copy(sale));
    }

    public synchronized List<SaleTransaction> openSales(final UUID playerId) {
        return openSales.values().stream().filter(sale -> sale.playerId().equals(playerId))
                .map(TrashRecyclePool::copy).toList();
    }

    /** Stores exact, already-individualized units without duplicating opaque instance tokens. */
    public synchronized void offerAll(final List<ItemStack> soldUnits) {
        Objects.requireNonNull(soldUnits, "soldUnits");
        for (final ItemStack unit : soldUnits) {
            if (!history.isValidTracked(unit)) {
                throw new IllegalArgumentException("csak current history-bearing Trash unit recycle-olható");
            }
        }
        final Map<String, ArrayDeque<ItemStack>> previous = new LinkedHashMap<>();
        for (final ItemStack unit : soldUnits) {
            final String id = itemFactory.idOf(unit).orElseThrow();
            previous.computeIfAbsent(id, ignored -> copyDeque(pool.get(id)));
            final ArrayDeque<ItemStack> instances = pool.computeIfAbsent(
                    id, ignored -> new ArrayDeque<>());
            instances.addLast(unit.clone());
            while (instances.size() > MAX_PER_IDENTITY) instances.removeFirst();
        }
        persistOrRestore(() -> previous.forEach(this::restoreDeque));
    }

    public synchronized Optional<ItemStack> take(final String id) {
        final ArrayDeque<ItemStack> instances = pool.get(id);
        if (instances == null || instances.isEmpty()) return Optional.empty();
        final ItemStack stored = instances.removeFirst();
        if (instances.isEmpty()) pool.remove(id);
        persistOrRestore(() -> pool.computeIfAbsent(id, ignored -> new ArrayDeque<>())
                .addFirst(stored));
        try {
            return Optional.of(history.recordRecycled(stored.clone()));
        } catch (final RuntimeException failure) {
            pool.computeIfAbsent(id, ignored -> new ArrayDeque<>()).addFirst(stored);
            persist();
            throw failure;
        }
    }

    public synchronized int pooledCount() {
        return pool.values().stream().mapToInt(ArrayDeque::size).sum();
    }

    synchronized int pooledCount(final String id) {
        final ArrayDeque<ItemStack> instances = pool.get(id);
        return instances == null ? 0 : instances.size();
    }

    private SaleTransaction advance(final UUID operationId, final SaleStage expected,
                                    final SaleStage target) {
        final SaleTransaction current = requireSale(operationId);
        if (current.stage().ordinal() >= target.ordinal()) return copy(current);
        if (current.stage() != expected) {
            throw new IllegalStateException("Trash vendor tranzakció rossz fázisban");
        }
        final SaleTransaction next = current.withStage(target);
        openSales.put(operationId, next);
        persistOrRestore(() -> openSales.put(operationId, current));
        return copy(next);
    }

    private SaleTransaction requireSale(final UUID operationId) {
        final SaleTransaction sale = openSales.get(Objects.requireNonNull(operationId, "operationId"));
        if (sale == null) throw new IllegalStateException("ismeretlen Trash vendor tranzakció");
        return sale;
    }

    private void persistOrRestore(final Runnable restore) {
        boolean committed = false;
        try {
            persist();
            committed = true;
        } finally {
            if (!committed) restore.run();
        }
    }

    private void persist() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA_VERSION);
        for (final Map.Entry<String, ArrayDeque<ItemStack>> entry : pool.entrySet()) {
            final List<ItemStack> copies = new ArrayList<>(entry.getValue().size());
            entry.getValue().forEach(item -> copies.add(item.clone()));
            yaml.set("pool." + entry.getKey(), copies);
        }
        for (final SaleTransaction sale : openSales.values()) {
            final String path = "vendor-transactions." + sale.operationId();
            yaml.set(path + ".player", sale.playerId().toString());
            yaml.set(path + ".stage", sale.stage().name());
            yaml.set(path + ".trash-id", sale.trashId());
            yaml.set(path + ".source", sale.source().clone());
            yaml.set(path + ".original-amount", sale.originalAmount());
            yaml.set(path + ".sold-amount", sale.soldAmount());
            yaml.set(path + ".slot", sale.slot());
            yaml.set(path + ".currency", sale.currency());
            yaml.set(path + ".value", sale.value());
            yaml.set(path + ".budget-day", sale.budgetDay());
            yaml.set(path + ".budget-before", sale.budgetBefore());
            yaml.set(path + ".created-at", sale.createdAt());
            yaml.set(path + ".recycle-units", sale.recycleUnits().stream()
                    .map(ItemStack::clone).toList());
        }
        try {
            YamlStore.saveAtomic(file, yaml);
        } catch (final IOException failure) {
            throw new IllegalStateException("Trash recycle pool mentése sikertelen", failure);
        }
    }

    private boolean validRecycleUnit(final String id, final ItemStack item) {
        return item != null && item.getAmount() == 1 && itemFactory.isKnownItem(item)
                && history.isValidTracked(item)
                && id.equals(itemFactory.idOf(item).orElse(null));
    }

    private List<ItemStack> itemList(final List<?> raw) {
        if (raw == null) return List.of();
        final List<ItemStack> result = new ArrayList<>(raw.size());
        for (final Object value : raw) {
            if (!(value instanceof ItemStack)) corrupt("a recycle payload nem item lista");
            result.add(((ItemStack) value).clone());
        }
        return List.copyOf(result);
    }

    private UUID parseUuid(final String raw, final String field) {
        try {
            return UUID.fromString(raw);
        } catch (final RuntimeException invalid) {
            corrupt("érvénytelen " + field + " UUID");
            throw new AssertionError("unreachable", invalid);
        }
    }

    private SaleStage parseStage(final String raw) {
        try {
            return SaleStage.valueOf(raw);
        } catch (final RuntimeException invalid) {
            corrupt("érvénytelen Trash vendor stage");
            throw new AssertionError("unreachable", invalid);
        }
    }

    private void corrupt(final String reason) {
        YamlStore.failCorrupt(file, plugin.getLogger(), reason);
    }

    private static ArrayDeque<ItemStack> copyDeque(final ArrayDeque<ItemStack> source) {
        final ArrayDeque<ItemStack> copy = new ArrayDeque<>();
        if (source != null) source.forEach(item -> copy.addLast(item.clone()));
        return copy;
    }

    private void restoreDeque(final String id, final ArrayDeque<ItemStack> previous) {
        if (previous == null || previous.isEmpty()) pool.remove(id);
        else pool.put(id, copyDeque(previous));
    }

    private static SaleTransaction copy(final SaleTransaction sale) {
        return new SaleTransaction(sale.operationId(), sale.playerId(), sale.stage(),
                sale.trashId(), sale.source().clone(), sale.originalAmount(), sale.soldAmount(),
                sale.slot(), sale.currency(), sale.value(), sale.budgetDay(), sale.budgetBefore(),
                sale.createdAt(), sale.recycleUnits());
    }

    public enum SaleStage {
        PREPARED,
        BUDGET_RESERVED,
        ITEM_REMOVED,
        POOL_COMMITTED,
        PAID
    }

    public record SaleTransaction(UUID operationId, UUID playerId, SaleStage stage,
                                  String trashId, ItemStack source, int originalAmount,
                                  int soldAmount, int slot, String currency, long value,
                                  long budgetDay, long budgetBefore, long createdAt,
                                  List<ItemStack> recycleUnits) {
        public SaleTransaction {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(trashId, "trashId");
            source = Objects.requireNonNull(source, "source").clone();
            Objects.requireNonNull(currency, "currency");
            recycleUnits = recycleUnits == null ? List.of()
                    : recycleUnits.stream().map(ItemStack::clone).toList();
        }

        private SaleTransaction withStage(final SaleStage next) {
            return new SaleTransaction(operationId, playerId, next, trashId, source,
                    originalAmount, soldAmount, slot, currency, value, budgetDay, budgetBefore,
                    createdAt, recycleUnits);
        }

        private SaleTransaction withRecycleUnitsAndStage(final List<ItemStack> units,
                                                         final SaleStage next) {
            return new SaleTransaction(operationId, playerId, next, trashId, source,
                    originalAmount, soldAmount, slot, currency, value, budgetDay, budgetBefore,
                    createdAt, units);
        }
    }
}
