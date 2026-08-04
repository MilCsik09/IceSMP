package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/** Single-entry WAL for one serialized tax/debt collection transaction. */
public final class FactionTaxJournal {

    private static final int FORMAT_VERSION = 1;

    public record DomainState(double treasuryBalance, double debtAmount, int evasionStrikes) {
        public DomainState {
            if (!Double.isFinite(treasuryBalance) || treasuryBalance < 0.0D
                    || !Double.isFinite(debtAmount) || debtAmount < 0.0D
                    || evasionStrikes < 0) {
                throw new IllegalArgumentException("Invalid faction tax domain state");
            }
        }
    }

    public record Entry(UUID id, UUID playerId, FactionType origin,
                        DomainState before, DomainState after,
                        CurrencyManager.DurableMutation walletMutation) {
        public Entry {
            if (id == null || playerId == null || origin == null || before == null || after == null
                    || walletMutation != null && !playerId.equals(walletMutation.playerId())) {
                throw new IllegalArgumentException("Invalid faction tax journal entry");
            }
        }
    }

    private final File file;
    private final Logger logger;
    private Entry pending;

    public FactionTaxJournal(final File file, final Logger logger) {
        this.file = file;
        this.logger = logger;
        YamlStore.registerCriticalWrite(file);
    }

    public synchronized void load() {
        pending = null;
        final YamlConfiguration yaml = YamlStore.loadTracked(file, logger);
        if (!yaml.isSet("entry")) {
            return;
        }
        if (yaml.getInt("format-version", FORMAT_VERSION) != FORMAT_VERSION) {
            YamlStore.failCorrupt(file, logger, "Unknown faction-tax journal version");
        }
        final ConfigurationSection section = yaml.getConfigurationSection("entry");
        if (section == null) {
            YamlStore.failCorrupt(file, logger, "Malformed faction-tax journal entry");
            return;
        }
        try {
            final UUID id = UUID.fromString(section.getString("id", ""));
            final UUID playerId = UUID.fromString(section.getString("player", ""));
            final FactionType origin = FactionType.fromInput(section.getString("origin", ""));
            if (origin == null) {
                throw new IllegalArgumentException("Unknown origin faction");
            }
            final DomainState before = readDomain(section, "before");
            final DomainState after = readDomain(section, "after");
            final CurrencyManager.DurableMutation wallet = section.getBoolean("wallet.present", false)
                    ? readWallet(section, playerId) : null;
            pending = new Entry(id, playerId, origin, before, after, wallet);
        } catch (final RuntimeException invalid) {
            YamlStore.failCorrupt(file, logger,
                    "Malformed faction-tax journal: " + invalid.getMessage());
        }
    }

    public synchronized Entry prepare(final UUID playerId, final FactionType origin,
                                      final DomainState before, final DomainState after,
                                      final CurrencyManager.DurableMutation wallet) {
        if (pending != null) {
            throw new IllegalStateException("Another tax transaction is awaiting recovery");
        }
        final Entry entry = new Entry(UUID.randomUUID(), playerId, origin, before, after, wallet);
        write(entry);
        pending = entry;
        return entry;
    }

    public synchronized Entry pending() {
        return pending;
    }

    public synchronized void complete(final Entry entry) {
        if (entry == null || pending == null || !pending.id().equals(entry.id())) {
            return;
        }
        write(null);
        pending = null;
    }

    public void failCorrupt(final String reason) {
        YamlStore.failCorrupt(file, logger, reason);
    }

    private void write(final Entry entry) {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format-version", FORMAT_VERSION);
        if (entry != null) {
            yaml.set("entry.id", entry.id().toString());
            yaml.set("entry.player", entry.playerId().toString());
            yaml.set("entry.origin", entry.origin().name());
            writeDomain(yaml, "entry.before", entry.before());
            writeDomain(yaml, "entry.after", entry.after());
            yaml.set("entry.wallet.present", entry.walletMutation() != null);
            if (entry.walletMutation() != null) {
                final CurrencyManager.DurableMutation wallet = entry.walletMutation();
                yaml.set("entry.wallet.previous-present", wallet.previousPresent());
                writeWallet(yaml, "entry.wallet.previous", wallet.previous());
                writeWallet(yaml, "entry.wallet.expected", wallet.expected());
            }
        }
        try {
            YamlStore.saveAtomic(file, yaml);
        } catch (final IOException exception) {
            throw new IllegalStateException("Faction-tax journal write failed", exception);
        }
    }

    private static void writeDomain(final YamlConfiguration yaml, final String path,
                                    final DomainState state) {
        yaml.set(path + ".treasury", state.treasuryBalance());
        yaml.set(path + ".debt", state.debtAmount());
        yaml.set(path + ".strikes", state.evasionStrikes());
    }

    private static DomainState readDomain(final ConfigurationSection section, final String path) {
        return new DomainState(
                finiteNonNegative(section.get(path + ".treasury")),
                finiteNonNegative(section.get(path + ".debt")),
                nonNegativeInt(section.get(path + ".strikes")));
    }

    private static CurrencyManager.DurableMutation readWallet(
            final ConfigurationSection section, final UUID playerId) {
        return new CurrencyManager.DurableMutation(playerId,
                section.getBoolean("wallet.previous-present", false),
                readWalletMap(section, "wallet.previous"),
                readWalletMap(section, "wallet.expected"));
    }

    private static void writeWallet(final YamlConfiguration yaml, final String path,
                                    final Map<CurrencyType, Double> wallet) {
        for (final CurrencyType type : CurrencyType.values()) {
            yaml.set(path + "." + type.name(), wallet.getOrDefault(type, 0.0D));
        }
    }

    private static Map<CurrencyType, Double> readWalletMap(
            final ConfigurationSection section, final String path) {
        final EnumMap<CurrencyType, Double> result = new EnumMap<>(CurrencyType.class);
        for (final CurrencyType type : CurrencyType.values()) {
            final Object raw = section.get(path + "." + type.name());
            result.put(type, raw == null ? 0.0D : finiteNonNegative(raw));
        }
        return result;
    }

    private static double finiteNonNegative(final Object raw) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("Expected numeric value");
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("Expected finite non-negative value");
        }
        return value;
    }

    private static int nonNegativeInt(final Object raw) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("Expected integral value");
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.0D || value > Integer.MAX_VALUE
                || value != Math.rint(value)) {
            throw new IllegalArgumentException("Expected non-negative int value");
        }
        return (int) value;
    }
}
