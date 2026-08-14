package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class DonationChestManager implements PersistentStore {

    public record DonationEntry(UUID id, UUID donorId, String donorName,
                                ItemStack item, long donatedAt) {
        public DonationEntry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(donorId, "donorId");
            donorName = donorName == null ? "?" : donorName;
            if (isEmpty(item)) throw new IllegalArgumentException("donation item is empty");
            item = item.clone();
        }

        @Override
        public ItemStack item() {
            return item.clone();
        }
    }

    private record DurableDonation(DonationEntry entry, DonationTransferLifecycle.State state,
                                   UUID claimantId) {
        private DurableDonation {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(state, "state");
        }

        private DurableDonation withState(final DonationTransferLifecycle.State next, final UUID claimant) {
            return new DurableDonation(entry, next, claimant);
        }
    }

    @FunctionalInterface
    private interface ItemWriter {
        void write(ItemStack item);
    }

    private record ItemSource(java.util.function.Supplier<ItemStack> reader, ItemWriter writer) { }

    private static final String DEPOSIT_MARKER = "deposit:";
    private static final String CLAIM_MARKER = "claim:";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final File storageFile;
    private final NamespacedKey transferKey;
    private final Map<UUID, DurableDonation> records = new ConcurrentHashMap<>();
    private final Set<UUID> visibleEntries = ConcurrentHashMap.newKeySet();

    public DonationChestManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.storageFile = new File(plugin.getDataFolder(), "donations.yml");
        this.transferKey = new NamespacedKey(plugin, "donation-transfer");
        plugin.getDataFolder().mkdirs();
    }

    public boolean isEnabled() {
        return configManager.getBoolean("donation-chest.enabled", true);
    }

    public int getMaxItems() {
        return Math.max(1, configManager.getInt("donation-chest.max-items", 270));
    }

    public int getMaxPerPlayer() {
        return configManager.getInt("donation-chest.max-per-player", 27);
    }

    @Override
    public synchronized void load() {
        records.clear();
        visibleEntries.clear();
        if (!storageFile.exists()) return;
        try {
            final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
            final ConfigurationSection section = yaml.getConfigurationSection("entries");
            if (section != null) {
                for (final String idKey : section.getKeys(false)) loadEntry(section, idKey);
            }
            hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager,
                    "Loaded " + records.size() + " donation chest entr(y/ies).");
        } catch (final Exception failure) {
            plugin.getLogger().severe("Failed to load donations.yml: " + failure.getMessage());
        }
    }

    private void loadEntry(final ConfigurationSection section, final String idKey) {
        try {
            final UUID id = UUID.fromString(idKey);
            final UUID donorId = UUID.fromString(section.getString(idKey + ".donor-id", ""));
            final ItemStack item = section.getItemStack(idKey + ".item");
            if (isEmpty(item)) return;
            final DonationEntry entry = new DonationEntry(id, donorId,
                    section.getString(idKey + ".donor-name", "?"), withoutMarker(item),
                    section.getLong(idKey + ".donated-at", System.currentTimeMillis()));
            final DonationTransferLifecycle.State state = parseState(
                    section.getString(idKey + ".state", "AVAILABLE"));
            final String claimantRaw = section.getString(idKey + ".claimant-id", "");
            final UUID claimant = claimantRaw == null || claimantRaw.isBlank()
                    ? null : UUID.fromString(claimantRaw);
            if (state == DonationTransferLifecycle.State.CLAIM_PREPARED && claimant == null) return;
            records.put(id, new DurableDonation(entry, state, claimant));
            if (state == DonationTransferLifecycle.State.AVAILABLE) visibleEntries.add(id);
        } catch (final IllegalArgumentException malformed) {
            plugin.getLogger().warning("Skipping malformed donation entry " + idKey
                    + ": " + malformed.getMessage());
        }
    }

    private static DonationTransferLifecycle.State parseState(final String raw) {
        try {
            return DonationTransferLifecycle.State.valueOf(raw == null ? "AVAILABLE" : raw);
        } catch (final IllegalArgumentException unknown) {
            return DonationTransferLifecycle.State.AVAILABLE;
        }
    }

    @Override
    public synchronized void save() {
        try {
            writeSnapshot();
        } catch (final IOException failure) {
            plugin.getLogger().severe("Failed to save donations.yml: " + failure.getMessage());
            throw new UncheckedIOException("Failed to save donations.yml", failure);
        }
    }

    private void writeSnapshot() throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final DurableDonation record : records.values()) {
            final DonationEntry entry = record.entry();
            final String base = "entries." + entry.id();
            yaml.set(base + ".donor-id", entry.donorId().toString());
            yaml.set(base + ".donor-name", entry.donorName());
            yaml.set(base + ".item", withoutMarker(entry.item()));
            yaml.set(base + ".donated-at", entry.donatedAt());
            yaml.set(base + ".state", record.state().name());
            yaml.set(base + ".claimant-id",
                    record.claimantId() == null ? null : record.claimantId().toString());
        }
        YamlStore.saveAtomic(storageFile, yaml);
    }

    public List<DonationEntry> getEntriesSorted() {
        return visibleEntries.stream()
                .map(records::get)
                .filter(Objects::nonNull)
                .map(DurableDonation::entry)
                .sorted(Comparator.comparingLong(DonationEntry::donatedAt).reversed())
                .toList();
    }

    public int count() {
        return visibleEntries.size();
    }

    private long countLiveOf(final UUID donorId) {
        return records.values().stream()
                .filter(record -> record.entry().donorId().equals(donorId))
                .count();
    }

    public CompletionStage<String> donateCursor(final Player donor, final ItemStack expected,
                                                final int amount) {
        return donateFromSource(donor, expected, amount,
                new ItemSource(donor::getItemOnCursor, donor::setItemOnCursor));
    }

    public CompletionStage<String> donateInventorySlot(final Player donor, final int slot,
                                                       final ItemStack expected, final int amount) {
        if (slot < 0 || slot >= 36) return CompletableFuture.completedFuture("donation-invalid-source");
        return donateFromSource(donor, expected, amount, new ItemSource(
                () -> donor.getInventory().getItem(slot),
                item -> donor.getInventory().setItem(slot, item)));
    }

    public CompletionStage<String> donateOffHand(final Player donor, final ItemStack expected,
                                                 final int amount) {
        return donateFromSource(donor, expected, amount, new ItemSource(
                () -> donor.getInventory().getItemInOffHand(),
                item -> donor.getInventory().setItemInOffHand(
                        isEmpty(item) ? new ItemStack(Material.AIR) : item)));
    }

    public CompletionStage<String> donateHeldItem(final Player donor) {
        final int slot = donor.getInventory().getHeldItemSlot();
        final ItemStack held = donor.getInventory().getItem(slot);
        return donateInventorySlot(donor, slot, cloneItem(held), isEmpty(held) ? 0 : held.getAmount());
    }

    private CompletionStage<String> donateFromSource(final Player donor, final ItemStack expected,
                                                     final int requestedAmount,
                                                     final ItemSource source) {
        final CompletableFuture<String> result = new CompletableFuture<>();
        final long sessionGeneration = currentSession(donor);
        if (sessionGeneration < 1L) {
            return CompletableFuture.completedFuture("donation-session-stale");
        }
        final UUID id;
        final DonationEntry entry;
        final ItemStack current;
        synchronized (this) {
            final String problem = validateDeposit(donor, expected, requestedAmount, source);
            if (problem != null) return CompletableFuture.completedFuture(problem);
            current = cloneItem(source.reader().get());
            id = UUID.randomUUID();
            final ItemStack donated = current.clone();
            donated.setAmount(requestedAmount);
            entry = new DonationEntry(id, donor.getUniqueId(), donor.getName(), withoutMarker(donated),
                    System.currentTimeMillis());
            source.writer().write(withMarker(current, DEPOSIT_MARKER + id));
            records.put(id, new DurableDonation(entry,
                    DonationTransferLifecycle.State.DEPOSIT_PREPARED, null));
        }

        persistAsync().whenComplete((ignored, prepareFailure) -> {
            if (prepareFailure != null) {
                rollbackUncommittedDeposit(donor, sessionGeneration, source,
                        id, result, prepareFailure);
                return;
            }
            scheduleOwner(donor, sessionGeneration,
                    () -> completePreparedDeposit(donor, sessionGeneration, source, current,
                            requestedAmount, id, result),
                    () -> result.complete("donation-pending-recovery"));
        });
        return result;
    }

    private String validateDeposit(final Player donor, final ItemStack expected,
                                   final int requestedAmount, final ItemSource source) {
        if (!isEnabled()) return "donation-chest-disabled";
        final ItemStack current = cloneItem(source.reader().get());
        if (!sameItem(current, expected)) return "donation-invalid-source";
        if (isEmpty(current) || requestedAmount <= 0) return "donation-no-item";
        if (requestedAmount > current.getAmount()) return "donation-invalid-source";
        if (records.size() >= getMaxItems()) return "donation-chest-full";
        final int maxPerPlayer = getMaxPerPlayer();
        return maxPerPlayer > 0 && countLiveOf(donor.getUniqueId()) >= maxPerPlayer
                ? "donation-per-player-limit" : null;
    }

    private void completePreparedDeposit(final Player donor, final long sessionGeneration,
                                         final ItemSource source,
                                         final ItemStack original, final int amount,
                                         final UUID id, final CompletableFuture<String> result) {
        final ItemStack reserved = cloneItem(source.reader().get());
        if (!hasMarker(reserved, DEPOSIT_MARKER + id)) {
            result.complete("donation-pending-recovery");
            return;
        }
        final ItemStack clean = withoutMarker(reserved);
        if (!sameItem(clean, original) || amount > clean.getAmount()) {
            rollbackUncommittedDeposit(donor, sessionGeneration, source, id, result,
                    new IllegalStateException("reserved donation source changed"));
            return;
        }
        final ItemStack remaining = clean.clone();
        remaining.setAmount(clean.getAmount() - amount);
        source.writer().write(remaining.getAmount() <= 0 ? null : remaining);
        synchronized (this) {
            final DurableDonation record = records.get(id);
            if (record == null || record.state() != DonationTransferLifecycle.State.DEPOSIT_PREPARED) {
                source.writer().write(original);
                result.complete("donation-transaction-failed");
                return;
            }
            records.put(id, record.withState(DonationTransferLifecycle.State.AVAILABLE, null));
        }
        persistAsync().whenComplete((ignored, commitFailure) -> {
            if (commitFailure == null) {
                visibleEntries.add(id);
                result.complete(null);
            } else {
                synchronized (this) {
                    final DurableDonation record = records.get(id);
                    if (record != null) records.put(id,
                            record.withState(DonationTransferLifecycle.State.DEPOSIT_PREPARED, null));
                }
                logFailure("Donation commit", id, commitFailure);
                result.complete("donation-pending-recovery");
            }
        });
    }

    private void rollbackUncommittedDeposit(final Player donor, final long sessionGeneration,
                                            final ItemSource source,
                                            final UUID id, final CompletableFuture<String> result,
                                            final Throwable failure) {
        final DurableDonation removed;
        synchronized (this) {
            removed = records.remove(id);
            visibleEntries.remove(id);
        }
        persistAsync().whenComplete((ignored, rollbackFailure) -> {
            if (rollbackFailure != null) {
                if (removed != null) records.putIfAbsent(id, removed);
                logFailure("Donation prepare rollback", id, rollbackFailure);
                result.complete("donation-pending-recovery");
                return;
            }
            scheduleOwner(donor, sessionGeneration, () -> {
                final ItemStack reserved = cloneItem(source.reader().get());
                if (hasMarker(reserved, DEPOSIT_MARKER + id)) {
                    source.writer().write(withoutMarker(reserved));
                }
                logFailure("Donation prepare", id, failure);
                result.complete("donation-transaction-failed");
            }, () -> result.complete("donation-transaction-failed"));
        });
    }

    public CompletionStage<String> takeEntry(final Player claimant, final UUID id) {
        if (id == null) return CompletableFuture.completedFuture("donation-take-gone");
        final long sessionGeneration = currentSession(claimant);
        if (sessionGeneration < 1L) {
            return CompletableFuture.completedFuture("donation-session-stale");
        }
        if (!isEmpty(claimant.getItemOnCursor())) {
            return CompletableFuture.completedFuture("donation-take-cursor");
        }
        final DurableDonation available;
        synchronized (this) {
            available = records.get(id);
            if (available == null || available.state() != DonationTransferLifecycle.State.AVAILABLE
                    || !visibleEntries.remove(id)) {
                return CompletableFuture.completedFuture("donation-take-gone");
            }
            records.put(id, available.withState(DonationTransferLifecycle.State.CLAIM_PREPARED,
                    claimant.getUniqueId()));
        }
        final CompletableFuture<String> result = new CompletableFuture<>();
        persistAsync().whenComplete((ignored, claimFailure) -> {
            if (claimFailure != null) {
                synchronized (this) {
                    records.put(id, available);
                }
                persistAsync().whenComplete((rollbackIgnored, rollbackFailure) -> {
                    if (rollbackFailure == null) {
                        visibleEntries.add(id);
                        result.complete("donation-take-failed");
                    } else {
                        synchronized (this) {
                            records.put(id, available.withState(
                                    DonationTransferLifecycle.State.CLAIM_PREPARED,
                                    claimant.getUniqueId()));
                        }
                        logFailure("Donation claim prepare rollback", id, rollbackFailure);
                        result.complete("donation-pending-recovery");
                    }
                });
                return;
            }
            scheduleOwner(claimant, sessionGeneration,
                    () -> deliverPreparedClaim(claimant, sessionGeneration, id, result),
                    () -> result.complete("donation-pending-recovery"));
        });
        return result;
    }

    private void deliverPreparedClaim(final Player claimant, final long sessionGeneration,
                                      final UUID id,
                                      final CompletableFuture<String> result) {
        final DurableDonation record = records.get(id);
        if (record == null || record.state() != DonationTransferLifecycle.State.CLAIM_PREPARED
                || !claimant.getUniqueId().equals(record.claimantId())) {
            result.complete("donation-take-gone");
            return;
        }
        if (!isEmpty(claimant.getItemOnCursor())) {
            restoreAvailable(id, record, result, "donation-take-cursor");
            return;
        }
        claimant.setItemOnCursor(withMarker(record.entry().item(), CLAIM_MARKER + id));
        result.complete(null);
        finalizeClaim(claimant, sessionGeneration, id);
    }

    private void restoreAvailable(final UUID id, final DurableDonation claim,
                                  final CompletableFuture<String> result, final String error) {
        synchronized (this) {
            records.put(id, claim.withState(DonationTransferLifecycle.State.AVAILABLE, null));
        }
        persistAsync().whenComplete((ignored, failure) -> {
            if (failure == null) {
                visibleEntries.add(id);
                result.complete(error);
            } else {
                synchronized (this) {
                    records.put(id, claim);
                }
                logFailure("Donation claim rollback", id, failure);
                result.complete("donation-pending-recovery");
            }
        });
    }

    private void finalizeClaim(final Player claimant, final long sessionGeneration,
                               final UUID id) {
        final DurableDonation removed;
        synchronized (this) {
            removed = records.remove(id);
            visibleEntries.remove(id);
        }
        persistAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                if (removed != null) records.putIfAbsent(id, removed);
                logFailure("Donation claim finalization", id, failure);
                return;
            }
            scheduleOwner(claimant, sessionGeneration,
                    () -> clearMarker(claimant, CLAIM_MARKER + id), () -> { });
        });
    }

    public boolean recover(final Player player) {
        final long sessionGeneration = currentSession(player);
        if (sessionGeneration < 1L) return false;
        clearOrphanMarkers(player);
        final List<DurableDonation> pending = records.values().stream()
                .filter(record -> (record.state() == DonationTransferLifecycle.State.DEPOSIT_PREPARED
                        && record.entry().donorId().equals(player.getUniqueId()))
                        || (record.state() == DonationTransferLifecycle.State.CLAIM_PREPARED
                        && player.getUniqueId().equals(record.claimantId())))
                .toList();
        for (final DurableDonation record : pending) {
            if (record.state() == DonationTransferLifecycle.State.DEPOSIT_PREPARED) {
                recoverDeposit(player, sessionGeneration, record);
            } else recoverClaim(player, sessionGeneration, record);
        }
        return true;
    }

    private void clearOrphanMarkers(final Player player) {
        final Set<String> liveMarkers = new HashSet<>();
        for (final DurableDonation record : records.values()) {
            final String prefix = record.state() == DonationTransferLifecycle.State.DEPOSIT_PREPARED
                    ? DEPOSIT_MARKER : record.state() == DonationTransferLifecycle.State.CLAIM_PREPARED
                    ? CLAIM_MARKER : null;
            if (prefix != null) liveMarkers.add(prefix + record.entry().id());
        }
        final ItemStack cursor = player.getItemOnCursor();
        final String cursorMarker = marker(cursor);
        if (cursorMarker != null && !liveMarkers.contains(cursorMarker)) {
            player.setItemOnCursor(withoutMarker(cursor));
        }
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            final String itemMarker = marker(contents[slot]);
            if (itemMarker != null && !liveMarkers.contains(itemMarker)) {
                player.getInventory().setItem(slot, withoutMarker(contents[slot]));
            }
        }
    }

    private void recoverDeposit(final Player player, final long sessionGeneration,
                                final DurableDonation record) {
        final UUID id = record.entry().id();
        final boolean markerPresent = findMarkedItem(player, DEPOSIT_MARKER + id) != null;
        if (DonationTransferLifecycle.recovery(record.state(), markerPresent)
                == DonationTransferLifecycle.Recovery.ROLLBACK_DEPOSIT) {
            synchronized (this) {
                records.remove(id, record);
            }
            persistAsync().whenComplete((ignored, failure) -> {
                if (failure == null) {
                    scheduleOwner(player, sessionGeneration,
                            () -> clearMarker(player, DEPOSIT_MARKER + id), () -> { });
                } else {
                    records.putIfAbsent(id, record);
                    logFailure("Donation deposit rollback recovery", id, failure);
                }
            });
            return;
        }
        synchronized (this) {
            final DurableDonation current = records.get(id);
            if (current == null
                    || current.state() != DonationTransferLifecycle.State.DEPOSIT_PREPARED) return;
            records.put(id, current.withState(DonationTransferLifecycle.State.AVAILABLE, null));
        }
        persistAsync().whenComplete((ignored, failure) -> {
            if (failure == null) {
                visibleEntries.add(id);
            } else {
                synchronized (this) {
                    final DurableDonation current = records.get(id);
                    if (current != null) records.put(id, current.withState(
                            DonationTransferLifecycle.State.DEPOSIT_PREPARED, null));
                }
                logFailure("Donation deposit recovery", id, failure);
            }
        });
    }

    private void recoverClaim(final Player player, final long sessionGeneration,
                              final DurableDonation record) {
        final UUID id = record.entry().id();
        final boolean markerPresent = findMarkedItem(player, CLAIM_MARKER + id) != null;
        if (DonationTransferLifecycle.recovery(record.state(), markerPresent)
                == DonationTransferLifecycle.Recovery.FINALIZE_CLAIM) {
            finalizeClaim(player, sessionGeneration, id);
            return;
        }
        if (isEmpty(player.getItemOnCursor())) {
            player.setItemOnCursor(withMarker(record.entry().item(), CLAIM_MARKER + id));
            finalizeClaim(player, sessionGeneration, id);
            return;
        }
        final int emptySlot = player.getInventory().firstEmpty();
        if (emptySlot >= 0) {
            player.getInventory().setItem(emptySlot,
                    withMarker(record.entry().item(), CLAIM_MARKER + id));
            finalizeClaim(player, sessionGeneration, id);
        }
    }

    public boolean isTransferMarked(final ItemStack item) {
        return marker(item) != null;
    }

    private CompletableFuture<Void> persistAsync() {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    synchronized (this) {
                        writeSnapshot();
                    }
                    result.complete(null);
                } catch (final Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (final RuntimeException unavailable) {
            result.completeExceptionally(unavailable);
        }
        return result;
    }

    private void scheduleOwner(final Player player, final long sessionGeneration,
                               final Runnable action, final Runnable retired) {
        try {
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline() && currentSession(player) == sessionGeneration) action.run();
                else retired.run();
            }, retired);
        } catch (final RuntimeException unavailable) {
            retired.run();
        }
    }

    private static long currentSession(final Player player) {
        return hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority.current()
                .service().currentSessionGeneration(player.getUniqueId()).orElse(-1L);
    }

    private ItemStack findMarkedItem(final Player player, final String expectedMarker) {
        if (hasMarker(player.getItemOnCursor(), expectedMarker)) return player.getItemOnCursor();
        for (final ItemStack item : player.getInventory().getContents()) {
            if (hasMarker(item, expectedMarker)) return item;
        }
        return null;
    }

    private void clearMarker(final Player player, final String expectedMarker) {
        if (hasMarker(player.getItemOnCursor(), expectedMarker)) {
            player.setItemOnCursor(withoutMarker(player.getItemOnCursor()));
        }
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (hasMarker(contents[slot], expectedMarker)) {
                player.getInventory().setItem(slot, withoutMarker(contents[slot]));
            }
        }
    }

    private ItemStack withMarker(final ItemStack item, final String value) {
        final ItemStack marked = cloneItem(item);
        final ItemMeta meta = marked.getItemMeta();
        meta.getPersistentDataContainer().set(transferKey, PersistentDataType.STRING, value);
        marked.setItemMeta(meta);
        return marked;
    }

    private ItemStack withoutMarker(final ItemStack item) {
        final ItemStack clean = cloneItem(item);
        if (clean == null) return null;
        final ItemMeta meta = clean.getItemMeta();
        meta.getPersistentDataContainer().remove(transferKey);
        clean.setItemMeta(meta);
        return clean;
    }

    private String marker(final ItemStack item) {
        if (isEmpty(item)) return null;
        final ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(transferKey, PersistentDataType.STRING);
    }

    private boolean hasMarker(final ItemStack item, final String expected) {
        return Objects.equals(marker(item), expected);
    }

    private void logFailure(final String operation, final UUID id, final Throwable failure) {
        plugin.getLogger().warning(operation + " failed for " + id + ": " + failure);
    }

    public static String defaultErrorFor(final String errorKey) {
        return switch (errorKey == null ? "" : errorKey) {
            case "donation-chest-disabled" -> "&cAz adomány-láda jelenleg ki van kapcsolva.";
            case "donation-no-item" -> "&cNincs adományozható tárgy a kiválasztott helyen.";
            case "donation-chest-full" -> "&cAz adomány-láda megtelt — próbáld később.";
            case "donation-per-player-limit" -> "&cElérted a saját adomány-limitedet (&f{limit} tétel&c).";
            case "donation-invalid-source" -> "&cA tárgy közben megváltozott; próbáld újra.";
            case "donation-session-stale" -> "&eA játékosprofilod még nem áll készen; próbáld újra.";
            case "donation-pending-recovery" -> "&eAz adomány tartós lezárása belépéskor folytatódik.";
            default -> "&cAz adományozás nem sikerült.";
        };
    }

    private static ItemStack cloneItem(final ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private static boolean sameItem(final ItemStack first, final ItemStack second) {
        if (isEmpty(first) || isEmpty(second)) return isEmpty(first) && isEmpty(second);
        return first.equals(second);
    }

    private static boolean isEmpty(final ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }
}
