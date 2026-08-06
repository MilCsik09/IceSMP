package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Wallet;
import hu.taliann.icesmp.items.CurrencyItemFactory;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileEconomyStore;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileEconomyStore.WalletState;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.TransactionJournal;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * PlayerProfile-backed wallet facade.
 *
 * <p>The canonical balances and durable debit witnesses live in the typed
 * {@code EconomySection}. The in-memory maps below are projections only: they are rebuilt from
 * PlayerProfile, discarded on logout and never serialized independently. Ordinary legacy-shaped
 * synchronous calls reserve a deterministic per-player mutation and enqueue section CAS in order;
 * durable transaction APIs wait for the section commit before returning.</p>
 */
@SuppressWarnings("unused")
public final class CurrencyManager implements PlayerStateCleanup, PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyItemFactory itemFactory;
    private final PlayerProfileEconomyStore economyStore = new PlayerProfileEconomyStore();

    /** Online/touched runtime projection. Never a persistence authority. */
    private final ConcurrentMap<UUID, WalletState> mirrors = new ConcurrentHashMap<>();
    /** Durable-owner projection used only for supply/operation lookup. */
    private final ConcurrentMap<UUID, WalletState> durableProjection = new ConcurrentHashMap<>();
    /** Serializes asynchronous CAS writes per player. */
    private final ConcurrentMap<UUID, CompletableFuture<Void>> mutationTails = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private volatile AutoCloseable profileSubscription;
    private volatile CurrencyType defaultCurrencyType = CurrencyType.NEUTRAL;

    /** Exact before/after wallet snapshot used by market/crate compensation. */
    public record DurableMutation(UUID playerId, boolean previousPresent,
                                  Map<CurrencyType, Double> previous,
                                  Map<CurrencyType, Double> expected) {
        public DurableMutation {
            Objects.requireNonNull(playerId, "playerId");
            previous = normalizeDoubleWallet(previous);
            expected = normalizeDoubleWallet(expected);
        }
    }

    public enum DurableWalletOperationStatus { DEBITED, COMMITTED, ROLLED_BACK }

    public record DurableWalletOperation(String operationId, UUID playerId, CurrencyType currency,
                                         double amount, long createdAtEpochMillis,
                                         DurableWalletOperationStatus status,
                                         boolean previousPresent,
                                         Map<CurrencyType, Double> previous,
                                         Map<CurrencyType, Double> expected) {
        public DurableWalletOperation {
            if (operationId == null || operationId.isBlank() || operationId.length() > 192) {
                throw new IllegalArgumentException("invalid durable operation id");
            }
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(status, "status");
            if (!Double.isFinite(amount) || amount <= 0.0D || createdAtEpochMillis <= 0L) {
                throw new IllegalArgumentException("invalid durable wallet operation");
            }
            previous = normalizeDoubleWallet(previous);
            expected = normalizeDoubleWallet(expected);
        }
    }

    private record LocalDecision<R>(boolean changed, WalletState wallet, R result) {
        static <R> LocalDecision<R> unchanged(final R result) {
            return new LocalDecision<>(false, null, result);
        }
        static <R> LocalDecision<R> changed(final WalletState wallet, final R result) {
            return new LocalDecision<>(true, Objects.requireNonNull(wallet, "wallet"), result);
        }
    }

    public CurrencyManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.itemFactory = new CurrencyItemFactory(plugin, configManager);
    }

    /** Rebuilds projections only; no legacy wallet file is read. */
    @Override
    public void load() {
        defaultCurrencyType = resolveDefaultCurrencyType();
        final Optional<PlayerProfileAuthority> installed = PlayerProfileAuthority.installed();
        if (installed.isEmpty()) {
            plugin.getLogger().warning("PlayerProfile authority is not installed while CurrencyManager loads; "
                    + "wallet projections will initialize lazily.");
            return;
        }
        if (profileSubscription == null) {
            profileSubscription = installed.orElseThrow().service().subscribe((playerId, revision, changed) -> {
                if (changed.contains(ProfileSectionId.ECONOMY)) refreshProjection(playerId);
            });
        }
        installed.orElseThrow().repository().listPlayerIds()
                .thenAccept(ids -> ids.forEach(this::refreshProjection))
                .exceptionally(failure -> {
                    failStorage("wallet owner enumeration", failure);
                    return null;
                });
    }

    /** Every mutation already commits through PlayerProfile; disable waits for queued CAS writes. */
    @Override
    public void save() {
        try {
            final CompletableFuture<?>[] pending = mutationTails.values()
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(pending).join();
            PlayerProfileAuthority.installed().ifPresent(authority ->
                    authority.repository().flushAll().toCompletableFuture().join());
        } catch (final CompletionException failure) {
            throw unavailable("A PlayerProfile wallet drain sikertelen: " + rootMessage(failure));
        }
    }

    /** Compatibility hook: there is no debounced secondary wallet store anymore. */
    public void requestSave() {
        // Intentionally empty. Each accepted mutation owns a section-CAS future.
    }

    public boolean isStorageHealthy() {
        return healthy.get() && PlayerProfileAuthority.installed().isPresent();
    }

    public FactionType getDefaultCurrencyType() {
        return defaultCurrencyType.toFactionType();
    }

    public CurrencyType getDefaultCurrency() {
        return defaultCurrencyType;
    }

    public ItemStack createCurrencyItem(final FactionType currencyType, final long amount) {
        return itemFactory.create(currencyType == null ? defaultCurrencyType
                : CurrencyType.fromFactionType(currencyType), amount);
    }

    public ItemStack createCurrencyItem(final CurrencyType currencyType, final long amount) {
        return itemFactory.create(currencyType == null ? defaultCurrencyType : currencyType, amount);
    }

    public boolean isCurrencyItem(final ItemStack itemStack) {
        return itemFactory.isCurrencyItem(itemStack);
    }

    /** Physical token payout; it deliberately does not mutate the bank ledger. */
    public void payOutTokens(final Player player, final CurrencyType currencyType, final long amount) {
        if (player == null) return;
        final CurrencyType currency = currencyType == null ? defaultCurrencyType : currencyType;
        long left = Math.max(0L, amount);
        while (left > 0L) {
            final long batch = Math.min(64L, left);
            left -= batch;
            for (final ItemStack overflow : player.getInventory()
                    .addItem(itemFactory.create(currency, batch)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }
    }

    public CurrencyType getCurrencyType(final ItemStack itemStack) {
        return itemFactory.getCurrencyType(itemStack);
    }

    public Wallet getWallet(final Player player) {
        return new Wallet(player.getUniqueId(), getBalances(player));
    }

    public double getBalance(final Player player) {
        return player == null ? 0.0D : getBalance(player, defaultCurrencyType);
    }

    public double getBalance(final Player player, final FactionType currencyType) {
        return player == null ? 0.0D : getBalance(player, currencyType == null
                ? defaultCurrencyType : CurrencyType.fromFactionType(currencyType));
    }

    public double getBalance(final Player player, final CurrencyType currencyType) {
        return player == null ? 0.0D : getBalance(player.getUniqueId(),
                currencyType == null ? defaultCurrencyType : currencyType);
    }

    public double getBalance(final UUID playerId, final CurrencyType currencyType) {
        if (playerId == null || currencyType == null) return 0.0D;
        return stateForRead(playerId).amount(currencyType);
    }

    public double getTotalBalance(final Player player) {
        if (player == null) return 0.0D;
        final WalletState state = stateForRead(player.getUniqueId());
        double total = 0.0D;
        for (final CurrencyType type : CurrencyType.values()) total += state.amount(type);
        return total;
    }

    public Map<FactionType, Double> getBalances(final Player player) {
        final EnumMap<FactionType, Double> result = new EnumMap<>(FactionType.class);
        if (player != null) {
            final WalletState state = stateForRead(player.getUniqueId());
            for (final CurrencyType type : CurrencyType.values()) {
                result.put(type.toFactionType(), state.amount(type));
            }
        }
        for (final FactionType type : FactionType.values()) result.putIfAbsent(type, 0.0D);
        return Map.copyOf(result);
    }

    public String describeBalances(final Player player) {
        final Map<FactionType, Double> balances = getBalances(player);
        final StringJoiner joiner = new StringJoiner(", ");
        for (final FactionType faction : FactionType.values()) {
            joiner.add(faction.getDisplayName() + ": "
                    + formatBalance(balances.getOrDefault(faction, 0.0D)));
        }
        return joiner.toString();
    }

    public String formatBalance(final double amount) {
        return new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT))
                .format(amount);
    }

    public boolean deductFromBalance(final UUID playerId, final CurrencyType currencyType,
                                     final double amount) {
        if (playerId == null || currencyType == null || !Double.isFinite(amount) || amount <= 0.0D) {
            return false;
        }
        final long milli;
        try { milli = PlayerProfileEconomyStore.toPositiveMilli(amount); }
        catch (final RuntimeException invalid) { return false; }
        return enqueueMutation(playerId, before -> {
            final long current = before.milli(currencyType);
            return current < milli ? LocalDecision.unchanged(false)
                    : LocalDecision.changed(before.with(currencyType, current - milli), true);
        }, false, ignored -> { }, failure -> { });
    }

    public void addToBalance(final UUID playerId, final CurrencyType currencyType,
                             final double amount) {
        if (playerId == null || currencyType == null || !Double.isFinite(amount) || amount <= 0.0D) return;
        addBalances(playerId, Map.of(currencyType, amount));
    }

    public void addBalances(final UUID playerId, final Map<CurrencyType, Double> additions) {
        final EnumMap<CurrencyType, Long> validated = validatePositiveBatch(additions);
        final boolean accepted = enqueueMutation(playerId, before -> {
            WalletState after = before;
            for (final Map.Entry<CurrencyType, Long> entry : validated.entrySet()) {
                after = after.add(entry.getKey(), entry.getValue());
            }
            return LocalDecision.changed(after, true);
        }, false, ignored -> { }, failure -> { });
        if (!accepted) throw unavailable("A PlayerProfile wallet-módosítás nem indítható.");
    }

    /** Synchronous section-CAS reward batch used by journaled transactions. */
    public DurableMutation addBalancesDurably(final UUID playerId,
                                               final Map<CurrencyType, Double> additions) {
        Objects.requireNonNull(playerId, "playerId");
        final EnumMap<CurrencyType, Long> validated = validatePositiveBatch(additions);
        awaitPending(playerId);
        try {
            final DurableMutation mutation = economyStore.mutate(playerId, before -> {
                WalletState after = before;
                for (final Map.Entry<CurrencyType, Long> entry : validated.entrySet()) {
                    after = after.add(entry.getKey(), entry.getValue());
                }
                return PlayerProfileEconomyStore.Decision.changed(after,
                        toDurableMutation(playerId, before, after));
            }).toCompletableFuture().join();
            installProjection(playerId, stateFrom(mutation.expected()));
            return mutation;
        } catch (final CompletionException failure) {
            failStorage("durable wallet reward", failure);
            throw unavailable(rootMessage(failure));
        }
    }

    public DurableMutation planDurableDeduction(final UUID playerId,
                                                final CurrencyType currencyType,
                                                final double amount) {
        if (playerId == null || currencyType == null || !Double.isFinite(amount) || amount <= 0.0D) {
            throw new IllegalArgumentException("Érvénytelen tartós wallet levonás.");
        }
        awaitPending(playerId);
        final WalletState before = stateForMutation(playerId);
        final long wanted = PlayerProfileEconomyStore.toPositiveMilli(amount);
        if (before.milli(currencyType) < wanted) return null;
        return toDurableMutation(playerId, before,
                before.with(currencyType, before.milli(currencyType) - wanted));
    }

    public void applyDurably(final DurableMutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        awaitPending(mutation.playerId());
        final WalletState before = stateFrom(mutation.previous());
        final WalletState after = stateFrom(mutation.expected());
        try {
            economyStore.replace(mutation.playerId(), before, after).toCompletableFuture().join();
            installProjection(mutation.playerId(), after);
        } catch (final CompletionException failure) {
            failStorage("durable wallet apply", failure);
            throw unavailable(rootMessage(failure));
        }
    }

    public Map<CurrencyType, Double> walletSnapshot(final UUID playerId) {
        return toDoubleWallet(stateForRead(playerId));
    }

    public boolean walletMatches(final DurableMutation mutation, final boolean expectedAfter) {
        if (mutation == null) return false;
        final WalletState live = stateForRead(mutation.playerId());
        final WalletState expected = stateFrom(expectedAfter
                ? mutation.expected() : mutation.previous());
        return live.equals(expected) && (expectedAfter || live.present() == mutation.previousPresent());
    }

    public DurableMutation deductDurably(final UUID playerId, final CurrencyType currencyType,
                                         final double amount) {
        final DurableMutation mutation = planDurableDeduction(playerId, currencyType, amount);
        if (mutation != null) applyDurably(mutation);
        return mutation;
    }

    public void rollbackDurably(final DurableMutation mutation) {
        if (mutation == null) return;
        awaitPending(mutation.playerId());
        final WalletState expected = stateFrom(mutation.expected());
        final WalletState previous = stateFrom(mutation.previous());
        try {
            economyStore.replace(mutation.playerId(), expected, previous).toCompletableFuture().join();
            installProjection(mutation.playerId(), previous);
        } catch (final CompletionException failure) {
            failStorage("durable wallet rollback", failure);
            throw unavailable(rootMessage(failure));
        }
    }

    public DurableWalletOperation debitOperation(final UUID playerId, final CurrencyType currency,
                                                  final double amount, final String operationId) {
        awaitPending(playerId);
        try {
            final PlayerProfileEconomyStore.DurableOperation operation = economyStore
                    .debitOperation(playerId, currency, amount, operationId)
                    .toCompletableFuture().join();
            if (operation == null) return null;
            installProjection(playerId, operation.expected());
            return fromStore(operation);
        } catch (final CompletionException failure) {
            failStorage("durable wallet debit", failure);
            throw unavailable(rootMessage(failure));
        }
    }

    public Optional<DurableWalletOperation> durableOperation(final String operationId) {
        if (operationId == null || operationId.isBlank()) return Optional.empty();
        for (final UUID playerId : List.copyOf(durableProjection.keySet())) {
            try {
                final Optional<PlayerProfileEconomyStore.DurableOperation> found =
                        economyStore.operationCached(playerId, operationId);
                if (found.isPresent()) return found.map(CurrencyManager::fromStore);
            } catch (final RuntimeException ignored) {
                // A profile may have been invalidated between the projection snapshot and lookup.
            }
        }
        return Optional.empty();
    }

    public List<DurableWalletOperation> durableOperationsByPrefix(final String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("operation prefix cannot be blank");
        }
        final List<DurableWalletOperation> result = new ArrayList<>();
        for (final UUID playerId : List.copyOf(durableProjection.keySet())) {
            try {
                economyStore.operationsByPrefixCached(playerId, prefix).stream()
                        .map(CurrencyManager::fromStore).forEach(result::add);
            } catch (final RuntimeException ignored) {
                // Fail-closed callers will see no unproven operation from an unavailable profile.
            }
        }
        result.sort(Comparator.comparingLong(DurableWalletOperation::createdAtEpochMillis)
                .thenComparing(DurableWalletOperation::operationId));
        return List.copyOf(result);
    }

    public DurableWalletOperation commitOperation(final String operationId) {
        return transitionOperation(operationId, PlayerProfileEconomyStore.OperationStatus.COMMITTED);
    }

    public DurableWalletOperation rollbackOperation(final String operationId) {
        return transitionOperation(operationId, PlayerProfileEconomyStore.OperationStatus.ROLLED_BACK);
    }

    private DurableWalletOperation transitionOperation(
            final String operationId,
            final PlayerProfileEconomyStore.OperationStatus target) {
        final DurableWalletOperation current = durableOperation(operationId)
                .orElseThrow(() -> new IllegalStateException("Unknown durable wallet operation"));
        awaitPending(current.playerId());
        try {
            final PlayerProfileEconomyStore.DurableOperation updated = economyStore
                    .transitionOperation(current.playerId(), operationId, target)
                    .toCompletableFuture().join();
            installProjection(current.playerId(), target == PlayerProfileEconomyStore.OperationStatus.ROLLED_BACK
                    ? updated.previous() : updated.expected());
            return fromStore(updated);
        } catch (final CompletionException failure) {
            failStorage("durable wallet transition", failure);
            throw unavailable(rootMessage(failure));
        }
    }

    public double getTotalSupply(final CurrencyType currencyType) {
        if (currencyType == null) return 0.0D;
        long total = 0L;
        for (final WalletState state : durableProjection.values()) {
            total = Math.addExact(total, state.milli(currencyType));
        }
        return PlayerProfileEconomyStore.fromMilli(total);
    }

    public void setBalance(final Player player, final long amount) {
        setBalance(player, defaultCurrencyType, amount);
    }

    public void setBalance(final Player player, final FactionType currencyType, final long amount) {
        setBalance(player, currencyType == null ? defaultCurrencyType
                : CurrencyType.fromFactionType(currencyType), (double) amount);
    }

    public void setBalance(final Player player, final FactionType currencyType, final double amount) {
        setBalance(player, currencyType == null ? defaultCurrencyType
                : CurrencyType.fromFactionType(currencyType), amount);
    }

    public void setBalance(final Player player, final CurrencyType currencyType, final double amount) {
        if (player == null || !Double.isFinite(amount)) return;
        final CurrencyType type = currencyType == null ? defaultCurrencyType : currencyType;
        final long milli = PlayerProfileEconomyStore.toMilli(Math.max(0.0D, amount));
        final boolean accepted = enqueueMutation(player.getUniqueId(), before ->
                        LocalDecision.changed(before.with(type, milli), true),
                false, ignored -> { }, failure -> { });
        if (!accepted) throw unavailable("A PlayerProfile wallet beállítása nem indítható.");
    }

    /**
     * Reserves all physical currency stacks and commits their value to PlayerProfile. On a durable
     * failure the removed stacks are restored on the player's owner scheduler.
     */
    public double deposit(final Player player) {
        if (player == null) return 0.0D;
        final PlayerInventory inventory = player.getInventory();
        final ItemStack[] original = inventory.getContents();
        final ItemStack[] nextContents = original.clone();
        final List<ItemStack> removed = new ArrayList<>();
        final EnumMap<CurrencyType, Long> additions = new EnumMap<>(CurrencyType.class);
        long totalMilli = 0L;
        for (int slot = 0; slot < nextContents.length; slot++) {
            final ItemStack stack = nextContents[slot];
            if (!itemFactory.isCurrencyItem(stack)) continue;
            final CurrencyType type = itemFactory.getCurrencyType(stack);
            if (type == null) continue;
            final ItemStack copy = stack.clone();
            removed.add(copy);
            final long value = Math.multiplyExact((long) stack.getAmount(),
                    PlayerProfileEconomyStore.SCALE);
            additions.merge(type, value, Math::addExact);
            totalMilli = Math.addExact(totalMilli, value);
            nextContents[slot] = null;
        }
        if (totalMilli == 0L) return 0.0D;
        final long depositedMilli = totalMilli;
        final boolean accepted = enqueueMutation(player.getUniqueId(), before -> {
            WalletState after = before;
            for (final Map.Entry<CurrencyType, Long> entry : additions.entrySet()) {
                after = after.add(entry.getKey(), entry.getValue());
            }
            return LocalDecision.changed(after, true);
        }, false, ignored -> { }, failure -> player.getScheduler().run(plugin, task -> {
            for (final ItemStack stack : removed) {
                player.getInventory().addItem(stack).values().forEach(left ->
                        player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
        }, null));
        if (!accepted) return 0.0D;
        inventory.setContents(nextContents);
        return PlayerProfileEconomyStore.fromMilli(depositedMilli);
    }

    public void refreshPlayerCurrencyItems(final Player player) {
        if (player == null) return;
        final PlayerInventory inventory = player.getInventory();
        final ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            if (!itemFactory.isCurrencyItem(contents[slot])) continue;
            contents[slot] = itemFactory.refresh(contents[slot]);
            changed = true;
        }
        if (changed) inventory.setContents(contents);
    }

    public boolean withdraw(final Player player, final FactionType currencyType, final int amount) {
        return withdraw(player, currencyType == null ? defaultCurrencyType
                : CurrencyType.fromFactionType(currencyType), amount);
    }

    /** Physical items are delivered only after the durable section-CAS commits. */
    public boolean withdraw(final Player player, final CurrencyType currencyType, final int amount) {
        if (player == null || amount <= 0) return false;
        final CurrencyType type = currencyType == null ? defaultCurrencyType : currencyType;
        final long milli = Math.multiplyExact((long) amount, PlayerProfileEconomyStore.SCALE);
        return enqueueMutation(player.getUniqueId(), before -> {
            final long current = before.milli(type);
            return current < milli ? LocalDecision.unchanged(false)
                    : LocalDecision.changed(before.with(type, current - milli), true);
        }, false, success -> player.getScheduler().run(plugin,
                task -> giveCurrency(player, type, amount), null), failure -> { });
    }

    public boolean transfer(final Player from, final Player to, final long amount) {
        return transfer(from, to, defaultCurrencyType.toFactionType(), amount);
    }

    /**
     * Cross-owner transfer uses two durable CAS commits with deterministic compensation. It does
     * not publish either runtime projection until the receiver commit succeeds.
     */
    public boolean transfer(final Player from, final Player to, final FactionType currencyType,
                            final long amount) {
        if (from == null || to == null || amount <= 0L || from.getUniqueId().equals(to.getUniqueId())) {
            return false;
        }
        final CurrencyType type = currencyType == null ? defaultCurrencyType
                : CurrencyType.fromFactionType(currencyType);
        final UUID sender = from.getUniqueId();
        final UUID receiver = to.getUniqueId();
        awaitPending(sender);
        awaitPending(receiver);
        final long milli = Math.multiplyExact(amount, PlayerProfileEconomyStore.SCALE);
        try {
            final DurableMutation debit = economyStore.mutate(sender, before -> {
                if (before.milli(type) < milli) {
                    return PlayerProfileEconomyStore.Decision.unchanged(null);
                }
                final WalletState after = before.with(type, before.milli(type) - milli);
                return PlayerProfileEconomyStore.Decision.changed(after,
                        toDurableMutation(sender, before, after));
            }).toCompletableFuture().join();
            if (debit == null) return false;
            try {
                final WalletState receiverAfter = economyStore.mutate(receiver, before -> {
                    final WalletState after = before.add(type, milli);
                    return PlayerProfileEconomyStore.Decision.changed(after, after);
                }).toCompletableFuture().join();
                installProjection(sender, stateFrom(debit.expected()));
                installProjection(receiver, receiverAfter);
                return true;
            } catch (final RuntimeException receiverFailure) {
                economyStore.replace(sender, stateFrom(debit.expected()), stateFrom(debit.previous()))
                        .toCompletableFuture().join();
                installProjection(sender, stateFrom(debit.previous()));
                throw receiverFailure;
            }
        } catch (final CompletionException failure) {
            failStorage("wallet transfer", failure);
            throw unavailable(rootMessage(failure));
        }
    }

    public long exchange(final Player player, final FactionType from, final FactionType to,
                         final long amount, final double rate, final double feePercent) {
        if (player == null || from == null || to == null || amount <= 0L
                || !Double.isFinite(rate) || rate <= 0.0D
                || !Double.isFinite(feePercent) || feePercent < 0.0D) return -1L;
        final CurrencyType source = CurrencyType.fromFactionType(from);
        final CurrencyType target = CurrencyType.fromFactionType(to);
        if (source == target) return -1L;
        final double gross = amount * rate;
        final double fee = gross * feePercent / 100.0D;
        if (!Double.isFinite(gross) || !Double.isFinite(fee)) return -1L;
        final long credited = Math.round(Math.max(0.0D, gross - fee));
        if (credited <= 0L) return -1L;
        final long sourceMilli = Math.multiplyExact(amount, PlayerProfileEconomyStore.SCALE);
        final long targetMilli = Math.multiplyExact(credited, PlayerProfileEconomyStore.SCALE);
        return enqueueMutation(player.getUniqueId(), before -> {
            if (before.milli(source) < sourceMilli) return LocalDecision.unchanged(-1L);
            WalletState after = before.with(source, before.milli(source) - sourceMilli);
            after = after.add(target, targetMilli);
            return LocalDecision.changed(after, credited);
        }, -1L, ignored -> { }, failure -> { });
    }

    private void giveCurrency(final Player player, final CurrencyType currencyType,
                              final long amount) {
        long remaining = amount;
        while (remaining > 0L) {
            final int stackAmount = (int) Math.min(64L, remaining);
            final ItemStack currencyItem = createCurrencyItem(currencyType, stackAmount);
            player.getInventory().addItem(currencyItem).values().forEach(item ->
                    player.getWorld().dropItemNaturally(player.getLocation(), item));
            remaining -= stackAmount;
        }
    }

    private <R> R enqueueMutation(final UUID playerId,
                                  final Function<WalletState, LocalDecision<R>> planner,
                                  final R deniedValue,
                                  final Consumer<R> afterCommit,
                                  final Consumer<Throwable> afterFailure) {
        if (playerId == null || !isStorageHealthy()) return deniedValue;
        return TransactionJournal.withCurrencyMutationPermit(() -> {
            final Object lock = playerLocks.computeIfAbsent(playerId, ignored -> new Object());
            synchronized (lock) {
                if (!isStorageHealthy()) return deniedValue;
                final WalletState before;
                try { before = stateForMutation(playerId); }
                catch (final RuntimeException unavailable) { return deniedValue; }
                final LocalDecision<R> decision = Objects.requireNonNull(
                        planner.apply(before), "wallet planner result");
                if (!decision.changed()) return decision.result();
                final WalletState after = Objects.requireNonNull(decision.wallet(), "wallet after");
                installProjection(playerId, after);

                final CompletableFuture<Void> previous = mutationTails.getOrDefault(playerId,
                        CompletableFuture.completedFuture(null));
                final CompletableFuture<Void> next = previous.thenCompose(ignored ->
                        economyStore.replace(playerId, before, after).thenApply(committed -> null))
                        .toCompletableFuture();
                mutationTails.put(playerId, next);
                next.whenComplete((ignored, failure) -> {
                    mutationTails.remove(playerId, next);
                    if (failure == null) {
                        try { afterCommit.accept(decision.result()); }
                        catch (final RuntimeException callbackFailure) {
                            plugin.getLogger().severe("Wallet post-commit callback failed for "
                                    + playerId + ": " + callbackFailure.getMessage());
                        }
                        return;
                    }
                    mirrors.remove(playerId);
                    refreshProjection(playerId);
                    failStorage("queued wallet mutation", failure);
                    try { afterFailure.accept(unwrap(failure)); }
                    catch (final RuntimeException callbackFailure) {
                        plugin.getLogger().severe("Wallet compensation callback failed for "
                                + playerId + ": " + callbackFailure.getMessage());
                    }
                });
                return decision.result();
            }
        }, deniedValue);
    }

    private WalletState stateForRead(final UUID playerId) {
        if (playerId == null) return new WalletState(Map.of());
        final WalletState mirror = mirrors.get(playerId);
        if (mirror != null) return mirror;
        final WalletState projected = durableProjection.get(playerId);
        if (projected != null) return projected;
        try {
            final WalletState loaded = economyStore.readCached(playerId);
            installProjection(playerId, loaded);
            return loaded;
        } catch (final RuntimeException notReady) {
            return new WalletState(Map.of());
        }
    }

    private WalletState stateForMutation(final UUID playerId) {
        final WalletState mirror = mirrors.get(playerId);
        if (mirror != null) return mirror;
        final WalletState loaded = economyStore.readCached(playerId);
        installProjection(playerId, loaded);
        return loaded;
    }

    private void installProjection(final UUID playerId, final WalletState state) {
        mirrors.put(playerId, state);
        durableProjection.put(playerId, state);
    }

    private void refreshProjection(final UUID playerId) {
        economyStore.load(playerId).thenAccept(state -> {
            durableProjection.put(playerId, state);
            if (mirrors.containsKey(playerId)) mirrors.put(playerId, state);
        }).exceptionally(failure -> {
            failStorage("wallet projection rebuild", failure);
            return null;
        });
    }

    private void awaitPending(final UUID playerId) {
        final CompletableFuture<Void> pending = mutationTails.get(playerId);
        if (pending != null) {
            try { pending.join(); }
            catch (final CompletionException failure) {
                throw unavailable("A korábbi wallet-módosítás meghiúsult: " + rootMessage(failure));
            }
        }
        if (!isStorageHealthy()) throw unavailable("A PlayerProfile wallet authority nem elérhető.");
    }

    private EnumMap<CurrencyType, Long> validatePositiveBatch(
            final Map<CurrencyType, Double> additions) {
        if (additions == null || additions.isEmpty()) {
            throw new IllegalArgumentException("A wallet batch nem lehet üres.");
        }
        final EnumMap<CurrencyType, Long> result = new EnumMap<>(CurrencyType.class);
        for (final Map.Entry<CurrencyType, Double> entry : additions.entrySet()) {
            final CurrencyType type = Objects.requireNonNull(entry.getKey(), "currency");
            final Double amount = Objects.requireNonNull(entry.getValue(), "amount");
            final long milli = PlayerProfileEconomyStore.toPositiveMilli(amount);
            result.merge(type, milli, Math::addExact);
        }
        return result;
    }

    private CurrencyType resolveDefaultCurrencyType() {
        final CurrencyType parsed = CurrencyType.fromInput(configManager.getString(
                "currency.default-type", CurrencyType.NEUTRAL.name()));
        return parsed == null ? CurrencyType.NEUTRAL : parsed;
    }

    private void failStorage(final String operation, final Throwable failure) {
        healthy.set(false);
        plugin.getLogger().severe("PlayerProfile " + operation + " failed: "
                + rootMessage(failure));
    }

    private CurrencyStorageUnavailableException unavailable(final String message) {
        return new CurrencyStorageUnavailableException(message);
    }

    private static DurableMutation toDurableMutation(final UUID playerId,
                                                      final WalletState before,
                                                      final WalletState after) {
        return new DurableMutation(playerId, before.present(),
                toDoubleWallet(before), toDoubleWallet(after));
    }

    private static DurableWalletOperation fromStore(
            final PlayerProfileEconomyStore.DurableOperation operation) {
        return new DurableWalletOperation(operation.operationId(), operation.playerId(),
                operation.currency(), operation.amount(), operation.createdAtEpochMillis(),
                DurableWalletOperationStatus.valueOf(operation.status().name()),
                operation.previousPresent(), toDoubleWallet(operation.previous()),
                toDoubleWallet(operation.expected()));
    }

    private static WalletState stateFrom(final Map<CurrencyType, Double> values) {
        final EnumMap<CurrencyType, Long> result = new EnumMap<>(CurrencyType.class);
        for (final CurrencyType type : CurrencyType.values()) {
            result.put(type, PlayerProfileEconomyStore.toMilli(
                    values == null ? 0.0D : values.getOrDefault(type, 0.0D)));
        }
        return new WalletState(result);
    }

    private static Map<CurrencyType, Double> toDoubleWallet(final WalletState state) {
        final EnumMap<CurrencyType, Double> result = new EnumMap<>(CurrencyType.class);
        for (final CurrencyType type : CurrencyType.values()) result.put(type, state.amount(type));
        return Map.copyOf(result);
    }

    private static Map<CurrencyType, Double> normalizeDoubleWallet(
            final Map<CurrencyType, Double> values) {
        final EnumMap<CurrencyType, Double> result = new EnumMap<>(CurrencyType.class);
        for (final CurrencyType type : CurrencyType.values()) {
            final double value = values == null ? 0.0D : values.getOrDefault(type, 0.0D);
            if (!Double.isFinite(value) || value < 0.0D) {
                throw new IllegalArgumentException("invalid wallet snapshot");
            }
            result.put(type, value);
        }
        return Map.copyOf(result);
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static String rootMessage(final Throwable failure) {
        final Throwable root = unwrap(failure);
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @Override
    public void cleanup(final UUID playerId) {
        final CompletableFuture<Void> pending = mutationTails.get(playerId);
        if (pending == null) {
            mirrors.remove(playerId);
            playerLocks.remove(playerId);
            return;
        }
        pending.whenComplete((ignored, failure) -> {
            mirrors.remove(playerId);
            playerLocks.remove(playerId);
        });
    }

    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }
}
