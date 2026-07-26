package hu.taliann.icesmp.storage;

import hu.taliann.icesmp.data.CurrencyType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Strict write-ahead journal used by the market/wallet/inventory transaction boundary. */
public final class TransactionJournal {

    private static final int FORMAT_VERSION = 1;
    private static final Set<String> KNOWN_TYPES = Set.of("LIST", "BUY", "BID", "SETTLE", "DELIVER");
    private static final Object CURRENCY_GATE = new Object();
    private static UUID activeTransaction;
    private static Thread activeOwner;
    private static final Set<UUID> recoveryMoneyEntries = new HashSet<>();

    public static final class Entry {
        private final UUID id;
        private final String type;
        private final long createdAt;
        private final YamlConfiguration data;

        private Entry(final UUID id, final String type, final long createdAt,
                      final YamlConfiguration data) {
            this.id = id;
            this.type = type;
            this.createdAt = createdAt;
            this.data = data;
        }

        public UUID id() { return id; }
        public String type() { return type; }
        public long createdAt() { return createdAt; }
        public ConfigurationSection data() { return data; }
    }

    private final File file;
    private final Logger logger;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private volatile boolean healthy = true;

    public TransactionJournal(final File file, final Logger logger) {
        this.file = file;
        this.logger = logger;
        YamlStore.registerCriticalWrite(file);
        YamlStore.registerCriticalWrite(new File(file.getParentFile(), "market.yml"));
    }

    public synchronized void load() {
        clearCurrencyGate();
        entries.clear();
        recoveryMoneyEntries.clear();
        final YamlConfiguration yaml = YamlStore.loadTracked(file, logger);
        healthy = true;
        final int version = yaml.getInt("format-version", FORMAT_VERSION);
        if (version != FORMAT_VERSION) {
            YamlStore.failCorrupt(file, logger, "Ismeretlen tranzakciós naplóverzió: " + version);
        }
        final ConfigurationSection section = yaml.getConfigurationSection("entries");
        if (section == null) {
            return;
        }
        final Set<UUID> seen = new HashSet<>();
        for (final String idKey : section.getKeys(false)) {
            final UUID id;
            try {
                id = UUID.fromString(idKey);
            } catch (final IllegalArgumentException invalidId) {
                YamlStore.failCorrupt(file, logger, "Érvénytelen tranzakcióazonosító: " + idKey);
                return;
            }
            if (!seen.add(id)) {
                YamlStore.failCorrupt(file, logger, "Duplikált tranzakcióazonosító: " + id);
            }
            final String type = section.getString(idKey + ".type", "").trim();
            final long createdAt = section.getLong(idKey + ".created-at", 0L);
            final ConfigurationSection stored = section.getConfigurationSection(idKey + ".data");
            if (!KNOWN_TYPES.contains(type) || createdAt <= 0L || stored == null) {
                YamlStore.failCorrupt(file, logger,
                        "Hiányos/ismeretlen naplóbejegyzés: " + id + " (type=" + type + ")");
            }
            final YamlConfiguration data = new YamlConfiguration();
            copyValues(stored, data, "");
            final Entry entry = new Entry(id, type, createdAt, data);
            validateEntry(entry);
            entries.put(id, entry);
            if (isMoneyEntry(entry)) {
                recoveryMoneyEntries.add(id);
            }
        }
        if (!recoveryMoneyEntries.isEmpty()) {
            synchronized (CURRENCY_GATE) {
                activeTransaction = null;
                activeOwner = Thread.currentThread();
            }
        }
    }

    public Entry create(final String type) {
        if (!KNOWN_TYPES.contains(type)) {
            throw new IllegalArgumentException("Ismeretlen tranzakciótípus: " + type);
        }
        final Entry entry = new Entry(UUID.randomUUID(), type, System.currentTimeMillis(),
                new YamlConfiguration());
        synchronized (CURRENCY_GATE) {
            if (activeTransaction != null || !recoveryMoneyEntries.isEmpty()) {
                throw new IllegalStateException("Másik piaci tranzakció még nyitott: "
                        + (activeTransaction != null ? activeTransaction : "recovery"));
            }
            if (YamlStore.hasCriticalWriteFailure()) {
                throw new IllegalStateException("A kritikus perzisztencia-kör megszakítója nyitva van.");
            }
            activeTransaction = entry.id();
            activeOwner = Thread.currentThread();
        }
        return entry;
    }

    public synchronized boolean prepare(final Entry entry) {
        if (!ownsCurrencyGate(entry) || !isHealthy()) {
            releaseCurrencyGate(entry);
            return false;
        }
        validateEntry(entry);
        entries.put(entry.id(), entry);
        if (flush()) {
            return true;
        }
        entries.remove(entry.id());
        releaseCurrencyGate(entry);
        return false;
    }

    public synchronized boolean complete(final Entry entry) {
        final Entry removed = entries.remove(entry.id());
        if (removed == null) {
            releaseCurrencyGate(entry);
            return true;
        }
        if (flush()) {
            releaseCurrencyGate(entry);
            return true;
        }
        entries.put(removed.id(), removed);
        return false;
    }

    public synchronized List<Entry> pending() { return List.copyOf(entries.values()); }
    public boolean isHealthy() { return healthy && !YamlStore.hasCriticalWriteFailure(); }

    public static <T> T withCurrencyMutationPermit(final Supplier<T> action, final T deniedValue) {
        synchronized (CURRENCY_GATE) {
            if (YamlStore.hasCriticalWriteFailure()) {
                return deniedValue;
            }
            if ((activeTransaction != null || !recoveryMoneyEntries.isEmpty())
                    && activeOwner != Thread.currentThread()) {
                return deniedValue;
            }
            return action.get();
        }
    }

    public static boolean runCurrencyMutation(final Runnable action) {
        return withCurrencyMutationPermit(() -> {
            action.run();
            return Boolean.TRUE;
        }, Boolean.FALSE);
    }

    /** True only on the thread currently repairing durable financial journal entries. */
    public static boolean isRecoveryOwnerThread() {
        synchronized (CURRENCY_GATE) {
            return !recoveryMoneyEntries.isEmpty() && activeOwner == Thread.currentThread();
        }
    }

    private boolean flush() {
        if (YamlStore.isLoadFailed(file) || YamlStore.hasCriticalWriteFailure()) {
            return false;
        }
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format-version", FORMAT_VERSION);
        for (final Entry entry : entries.values()) {
            final String base = "entries." + entry.id();
            yaml.set(base + ".type", entry.type());
            yaml.set(base + ".created-at", entry.createdAt());
            copyValues(entry.data, yaml, base + ".data.");
        }
        try {
            YamlStore.saveAtomic(file, yaml);
            return true;
        } catch (final IOException exception) {
            healthy = false;
            logger.severe("A tranzakciós napló (" + file.getName() + ") nem írható: "
                    + exception.getMessage());
            return false;
        }
    }

    private void validateEntry(final Entry entry) {
        final ConfigurationSection data = entry.data();
        switch (entry.type()) {
            case "LIST" -> {
                requireUuid(entry, data.getString("owner"), "owner");
                requireUuid(entry, data.getString("listing-id"), "listing-id");
                requireItem(entry, data.getItemStack("item"), "item");
            }
            case "BUY" -> {
                requireUuid(entry, data.getString("owner"), "owner");
                requireUuid(entry, data.getString("listing-id"), "listing-id");
                requireItem(entry, data.getItemStack("item"), "item");
                validateMoney(entry, 2);
            }
            case "BID" -> {
                requireUuid(entry, data.getString("owner"), "owner");
                requireUuid(entry, data.getString("listing-id"), "listing-id");
                requireItem(entry, data.getItemStack("item"), "item");
                validateMoney(entry, 1);
            }
            case "SETTLE" -> {
                requireUuid(entry, data.getString("listing-id"), "listing-id");
                requireItem(entry, data.getItemStack("item"), "item");
                validateMoney(entry, 0);
            }
            case "DELIVER" -> {
                requireUuid(entry, data.getString("owner"), "owner");
                final List<?> items = data.getList("items");
                if (items == null || items.isEmpty()) {
                    corrupt(entry, "üres items lista");
                }
                for (final Object raw : items) {
                    if (!(raw instanceof ItemStack item) || item.getType().isAir()) {
                        corrupt(entry, "érvénytelen items elem");
                    }
                }
            }
            default -> corrupt(entry, "ismeretlen type");
        }
    }

    private void validateMoney(final Entry entry, final int minimumEntries) {
        final ConfigurationSection money = entry.data().getConfigurationSection("money");
        if (money == null) {
            if (minimumEntries == 0) {
                return;
            }
            corrupt(entry, "hiányzó money szakasz");
        }
        final Set<String> keys = money.getKeys(false);
        if (keys.size() < minimumEntries) {
            corrupt(entry, "kevés money bejegyzés");
        }
        for (final String index : keys) {
            requireUuid(entry, money.getString(index + ".player"), "money." + index + ".player");
            if (CurrencyType.fromInput(money.getString(index + ".currency", "")) == null) {
                corrupt(entry, "ismeretlen valuta: money." + index + ".currency");
            }
            final double before = money.getDouble(index + ".before", Double.NaN);
            final double delta = money.getDouble(index + ".delta", Double.NaN);
            if (!Double.isFinite(before) || before < 0.0D || !Double.isFinite(delta)
                    || Math.abs(delta) < 0.0000001D) {
                corrupt(entry, "nem véges/érvénytelen pénzmező: money." + index);
            }
        }
    }

    private void requireUuid(final Entry entry, final String raw, final String field) {
        try {
            UUID.fromString(raw == null ? "" : raw);
        } catch (final IllegalArgumentException invalid) {
            corrupt(entry, "érvénytelen UUID: " + field);
        }
    }

    private void requireItem(final Entry entry, final ItemStack item, final String field) {
        if (item == null || item.getType() == Material.AIR) {
            corrupt(entry, "hiányzó/üres tárgy: " + field);
        }
    }

    private void corrupt(final Entry entry, final String reason) {
        YamlStore.failCorrupt(file, logger,
                "Érvénytelen tranzakció " + entry.id() + " (" + entry.type() + "): " + reason);
    }

    private static boolean ownsCurrencyGate(final Entry entry) {
        synchronized (CURRENCY_GATE) {
            return entry != null && entry.id().equals(activeTransaction)
                    && activeOwner == Thread.currentThread();
        }
    }

    private static void releaseCurrencyGate(final Entry entry) {
        synchronized (CURRENCY_GATE) {
            if (entry == null || activeOwner != Thread.currentThread()) {
                return;
            }
            if (entry.id().equals(activeTransaction)) {
                activeTransaction = null;
                activeOwner = null;
                CURRENCY_GATE.notifyAll();
                return;
            }
            if (recoveryMoneyEntries.remove(entry.id()) && recoveryMoneyEntries.isEmpty()) {
                activeOwner = null;
                CURRENCY_GATE.notifyAll();
            }
        }
    }

    private static boolean isMoneyEntry(final Entry entry) {
        return entry != null && switch (entry.type()) {
            case "BUY", "BID", "SETTLE" -> true;
            default -> false;
        };
    }

    private static void clearCurrencyGate() {
        synchronized (CURRENCY_GATE) {
            activeTransaction = null;
            activeOwner = null;
            recoveryMoneyEntries.clear();
            CURRENCY_GATE.notifyAll();
        }
    }

    private static void copyValues(final ConfigurationSection source,
                                   final YamlConfiguration target, final String prefix) {
        for (final String key : source.getKeys(true)) {
            final Object value = source.get(key);
            if (value instanceof ConfigurationSection) {
                continue;
            }
            target.set(prefix + key, value);
        }
    }
}
