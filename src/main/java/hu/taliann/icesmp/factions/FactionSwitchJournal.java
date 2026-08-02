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

/** Write-ahead journal for paid membership switches. At most one switch is prepared at a time. */
public final class FactionSwitchJournal {

    private static final int FORMAT_VERSION = 1;

    public record Entry(UUID id, UUID playerId,
                        FactionMembershipMutation.Snapshot membershipBefore,
                        FactionType targetFaction, CurrencyType currency, double cost,
                        CurrencyManager.DurableMutation walletMutation) {
        public Entry {
            if (id == null || playerId == null || membershipBefore == null
                    || !playerId.equals(membershipBefore.playerId())
                    || targetFaction == null || currency == null
                    || walletMutation == null || !Double.isFinite(cost) || cost <= 0.0D
                    || !playerId.equals(walletMutation.playerId())) {
                throw new IllegalArgumentException("Invalid faction-switch journal entry");
            }
        }
    }

    private final File file;
    private final Logger logger;
    private Entry pending;

    public FactionSwitchJournal(final File file, final Logger logger) {
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
            YamlStore.failCorrupt(file, logger, "Unknown faction-switch journal version");
        }
        final ConfigurationSection section = yaml.getConfigurationSection("entry");
        if (section == null) {
            YamlStore.failCorrupt(file, logger, "Malformed faction-switch journal entry");
            return;
        }
        try {
            final UUID id = UUID.fromString(section.getString("id", ""));
            final UUID playerId = UUID.fromString(section.getString("player", ""));
            final boolean assignmentPresent = section.getBoolean(
                    "membership-before.assignment-present", false);
            final FactionType assignment = assignmentPresent
                    ? FactionType.fromInput(section.getString(
                    "membership-before.assignment", "")) : null;
            final boolean historyPresent = section.getBoolean(
                    "membership-before.history-present", false);
            final FactionType history = historyPresent
                    ? FactionType.fromInput(section.getString(
                    "membership-before.history", "")) : null;
            final FactionMembershipMutation.Snapshot membershipBefore =
                    new FactionMembershipMutation.Snapshot(playerId, assignmentPresent,
                            assignment, historyPresent, history);
            final FactionType target = FactionType.fromInput(section.getString("target-faction", ""));
            final CurrencyType currency = CurrencyType.fromInput(section.getString("currency", ""));
            final double cost = finitePositive(section.get("cost"));
            final boolean previousPresent = section.getBoolean("wallet.previous-present", false);
            final Map<CurrencyType, Double> walletPrevious = readWallet(section, "wallet.previous");
            final Map<CurrencyType, Double> walletExpected = readWallet(section, "wallet.expected");
            if (target == null || currency == null) {
                throw new IllegalArgumentException("Unknown faction/currency");
            }
            pending = new Entry(id, playerId, membershipBefore, target, currency, cost,
                    new CurrencyManager.DurableMutation(playerId, previousPresent,
                            walletPrevious, walletExpected));
        } catch (final RuntimeException invalid) {
            YamlStore.failCorrupt(file, logger,
                    "Malformed faction-switch journal: " + invalid.getMessage());
        }
    }

    public synchronized Entry prepare(
            final FactionMembershipMutation.Snapshot membershipBefore,
            final FactionType target, final CurrencyType currency,
            final double cost, final CurrencyManager.DurableMutation mutation) {
        if (pending != null) {
            throw new IllegalStateException("Another faction switch is awaiting recovery");
        }
        final Entry entry = new Entry(UUID.randomUUID(), membershipBefore.playerId(),
                membershipBefore, target, currency, cost, mutation);
        write(entry);
        pending = entry;
        return entry;
    }

    public synchronized Entry pending() {
        return pending;
    }

    public void failCorrupt(final String reason) {
        YamlStore.failCorrupt(file, logger, reason);
    }

    public synchronized void complete(final Entry entry) {
        if (entry == null || pending == null || !pending.id().equals(entry.id())) {
            return;
        }
        write(null);
        pending = null;
    }

    private void write(final Entry entry) {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format-version", FORMAT_VERSION);
        if (entry != null) {
            yaml.set("entry.id", entry.id().toString());
            yaml.set("entry.player", entry.playerId().toString());
            final FactionMembershipMutation.Snapshot before = entry.membershipBefore();
            yaml.set("entry.membership-before.assignment-present", before.hadAssignment());
            if (before.hadAssignment()) {
                yaml.set("entry.membership-before.assignment", before.assignment().name());
            }
            yaml.set("entry.membership-before.history-present", before.hadHistory());
            if (before.hadHistory()) {
                yaml.set("entry.membership-before.history", before.lastChosenFaction().name());
            }
            yaml.set("entry.target-faction", entry.targetFaction().name());
            yaml.set("entry.currency", entry.currency().name());
            yaml.set("entry.cost", entry.cost());
            yaml.set("entry.wallet.previous-present", entry.walletMutation().previousPresent());
            writeWallet(yaml, "entry.wallet.previous", entry.walletMutation().previous());
            writeWallet(yaml, "entry.wallet.expected", entry.walletMutation().expected());
        }
        try {
            YamlStore.saveAtomic(file, yaml);
        } catch (final IOException exception) {
            throw new IllegalStateException("Faction-switch journal write failed", exception);
        }
    }

    private static void writeWallet(final YamlConfiguration yaml, final String path,
                                    final Map<CurrencyType, Double> wallet) {
        for (final CurrencyType type : CurrencyType.values()) {
            yaml.set(path + "." + type.name(), wallet.getOrDefault(type, 0.0D));
        }
    }

    private static Map<CurrencyType, Double> readWallet(final ConfigurationSection section,
                                                         final String path) {
        final EnumMap<CurrencyType, Double> wallet = new EnumMap<>(CurrencyType.class);
        for (final CurrencyType type : CurrencyType.values()) {
            final Object raw = section.get(path + "." + type.name());
            final double amount = raw == null ? 0.0D : finiteNonNegative(raw);
            wallet.put(type, amount);
        }
        return wallet;
    }

    private static double finitePositive(final Object raw) {
        final double value = finiteNonNegative(raw);
        if (value <= 0.0D) {
            throw new IllegalArgumentException("Expected positive amount");
        }
        return value;
    }

    private static double finiteNonNegative(final Object raw) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("Expected number");
        }
        final double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("Expected finite non-negative amount");
        }
        return value;
    }
}
