package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.classspec.domain.ProfileOperationStatus;
import hu.taliann.icesmp.classspec.domain.ProfileOperationType;
import hu.taliann.icesmp.classspec.transaction.RespecRecoveryProtocol;
import hu.taliann.icesmp.classspec.transaction.RespecTransactionJournal;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileSpecializationProgressStore;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Restart-recoverable cross-store respec transaction coordinator. */
public final class RespecService {
    private static final String PROFESSION_PREFIX = "profession-respec:";

    public record Outcome(Status status, double cost, CurrencyType currency, int refundedTalentPoints) {
        public enum Status { OK, NOTHING_TO_RESPEC, INSUFFICIENT_FUNDS, PERSISTENCE_FAILED,
            RUNTIME_FAILED, REFUND_FAILED }
        public boolean ok() { return status == Status.OK; }
    }

    private final JavaPlugin plugin;
    private final SpecializationManager specializationManager;
    private final TalentManager talentManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final RespecTransactionJournal journal;
    private final PlayerProfileSpecializationProgressStore professionProgressStore =
            new PlayerProfileSpecializationProgressStore();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        final Thread thread = new Thread(task, "IceSMP-profile-v2-respec");
        thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Map<UUID, LinkedHashMap<String, CompletableFuture<Outcome>>> inFlight =
            new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Outcome>> professionInFlight = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> closingPlayers = ConcurrentHashMap.newKeySet();

    public RespecService(final JavaPlugin plugin, final SpecializationManager specializationManager,
                         final TalentManager talentManager, final CurrencyManager currencyManager,
                         final FactionManager factionManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.specializationManager = Objects.requireNonNull(specializationManager);
        this.talentManager = Objects.requireNonNull(talentManager);
        this.currencyManager = Objects.requireNonNull(currencyManager);
        this.factionManager = Objects.requireNonNull(factionManager);
        this.journal = new RespecTransactionJournal(new File(plugin.getDataFolder(),
                "class-profiles/respec-journal").toPath());
    }

    /**
     * Legacy synchronous profession-respec path is intentionally fail-closed. Calling code must use
     * {@link #respecProfessionV2(Player, String)} so wallet debit and Profile persistence cannot be
     * separated by a fire-and-forget reset.
     */
    @Deprecated
    public Outcome respec(final Player player, final boolean classPool) {
        return failureOutcome(player, Outcome.Status.PERSISTENCE_FAILED);
    }

    public CompletionStage<Outcome> respecProfessionV2(final Player player,
                                                        final String requestedOperationId) {
        Objects.requireNonNull(player, "player");
        final UUID playerId = player.getUniqueId();
        final CompletableFuture<Outcome> created = new CompletableFuture<>();
        final CompletableFuture<Outcome> existing = professionInFlight.putIfAbsent(playerId, created);
        if (existing != null) return existing;
        created.whenComplete((ignored, failure) -> professionInFlight.remove(playerId, created));
        if (!accepting.get() || closingPlayers.contains(playerId)) {
            created.complete(failureOutcome(player, Outcome.Status.PERSISTENCE_FAILED));
            return created;
        }
        try {
            executor.execute(() -> executeProfession(player, requestedOperationId, created));
        } catch (final RejectedExecutionException rejected) {
            created.complete(failureOutcome(player, Outcome.Status.PERSISTENCE_FAILED));
        }
        return created;
    }

    private void executeProfession(final Player player, final String requestedOperationId,
                                   final CompletableFuture<Outcome> completion) {
        final UUID playerId = player.getUniqueId();
        final double cost = specializationManager.getRespecCost();
        final CurrencyType currency = CurrencyType.fromFactionType(
                factionManager.getEconomyFaction(playerId));
        String operationId = null;
        try {
            operationId = pendingProfessionOperation(playerId, requestedOperationId);
            RespecTransactionJournal.Entry entry = journal.load(operationId);
            if (entry == null) {
                if (professionProgressStore.professionSpecialization(playerId).isEmpty()) {
                    completion.complete(new Outcome(Outcome.Status.NOTHING_TO_RESPEC,
                            cost, currency, 0));
                    return;
                }
                entry = new RespecTransactionJournal.Entry(operationId, playerId,
                        RespecTransactionJournal.Stage.INTENT, currency.name(), cost,
                        System.currentTimeMillis(), 0L, false, false, Map.of(), Map.of(),
                        "profession respec intent persisted");
                journal.save(entry);
            } else if (!entry.playerId().equals(playerId) || entry.amount() != cost
                    || !entry.currencyId().equals(currency.name()) || !isProfession(entry)) {
                completion.complete(new Outcome(Outcome.Status.PERSISTENCE_FAILED,
                        cost, currency, 0));
                return;
            }

            CurrencyManager.DurableWalletOperation wallet =
                    currencyManager.durableOperation(operationId).orElse(null);
            if (cost > 0.0D) {
                if (wallet == null) {
                    wallet = currencyManager.debitOperation(playerId, currency, cost, operationId);
                    if (wallet == null) {
                        journal.delete(operationId);
                        completion.complete(new Outcome(Outcome.Status.INSUFFICIENT_FUNDS,
                                cost, currency, 0));
                        return;
                    }
                } else {
                    requireWalletIdentity(wallet, playerId, currency, cost);
                    if (wallet.status() == CurrencyManager.DurableWalletOperationStatus.ROLLED_BACK) {
                        journal.delete(operationId);
                        completion.complete(new Outcome(Outcome.Status.PERSISTENCE_FAILED,
                                cost, currency, 0));
                        return;
                    }
                }
                if (!entry.walletTokenPresent()) {
                    entry = entry.withWallet(wallet.previousPresent(), stringify(wallet.previous()),
                            stringify(wallet.expected()));
                    journal.save(entry);
                }
            }

            boolean profileCommitted = professionProgressStore.professionSpecialization(playerId).isEmpty();
            if (!profileCommitted) {
                final boolean reset = professionProgressStore.resetProfessionSpecialization(playerId)
                        .toCompletableFuture().join();
                profileCommitted = reset
                        || professionProgressStore.professionSpecialization(playerId).isEmpty();
                if (!profileCommitted) {
                    throw new IllegalStateException("profession specialization reset was not persisted");
                }
            }
            journal.save(entry.withStage(RespecTransactionJournal.Stage.PROFILE_COMMITTED,
                    "PlayerProfile profession specialization reset committed"));

            if (wallet != null) {
                if (wallet.status() == CurrencyManager.DurableWalletOperationStatus.DEBITED) {
                    currencyManager.commitOperation(operationId);
                } else if (wallet.status() != CurrencyManager.DurableWalletOperationStatus.COMMITTED) {
                    throw new IllegalStateException("profession respec wallet is not committable");
                }
            }
            // The durable Profile authority already says there is no active profession spec. This
            // compatibility call only clears the manager projection; its duplicate durable reset is a no-op.
            specializationManager.resetProfessionSpecialization(player);
            journal.delete(operationId);
            completion.complete(new Outcome(Outcome.Status.OK, cost, currency, 0));
        } catch (final Throwable failure) {
            final String resolvedOperationId = operationId;
            if (resolvedOperationId != null) {
                try {
                    final boolean profileCommitted = professionProgressStore
                            .professionSpecialization(playerId).isEmpty();
                    final CurrencyManager.DurableWalletOperation wallet = currencyManager
                            .durableOperation(resolvedOperationId).orElse(null);
                    if (!profileCommitted && wallet != null
                            && wallet.status() == CurrencyManager.DurableWalletOperationStatus.DEBITED) {
                        currencyManager.rollbackOperation(resolvedOperationId);
                        journal.delete(resolvedOperationId);
                    }
                    // If the Profile reset is already durable, do NOT refund here. The open journal
                    // is the recovery witness that will commit the exact debit after restart/retry.
                } catch (final Throwable recoveryFailure) {
                    failure.addSuppressed(recoveryFailure);
                }
            }
            plugin.getLogger().severe("Profession respec transaction failed for " + playerId
                    + ": " + safeMessage(failure));
            completion.complete(new Outcome(Outcome.Status.PERSISTENCE_FAILED, cost, currency, 0));
        }
    }

    private String pendingProfessionOperation(final UUID playerId, final String requestedOperationId) {
        for (final RespecTransactionJournal.Entry pending : journal.findByPlayer(playerId)) {
            if (isProfession(pending)) return pending.operationId();
        }
        if (requestedOperationId == null || requestedOperationId.isBlank()) {
            throw new IllegalArgumentException("profession respec operationId required");
        }
        final String operationId = requestedOperationId.trim();
        final String expectedPrefix = PROFESSION_PREFIX + playerId + ':';
        if (!operationId.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("profession respec operationId is not player-bound");
        }
        return operationId;
    }

    private static boolean isProfession(final RespecTransactionJournal.Entry entry) {
        return entry != null && entry.operationId().startsWith(PROFESSION_PREFIX);
    }

    private static void requireWalletIdentity(final CurrencyManager.DurableWalletOperation wallet,
                                              final UUID playerId, final CurrencyType currency,
                                              final double amount) {
        if (!wallet.playerId().equals(playerId) || wallet.currency() != currency
                || Double.compare(wallet.amount(), amount) != 0) {
            throw new IllegalStateException("profession respec wallet operation identity mismatch");
        }
    }

    public CompletionStage<Outcome> respecV2(final Player player, final String requestedOperationId) {
        Objects.requireNonNull(player, "player");
        final UUID playerId = player.getUniqueId();
        final String operationId = attemptOperationId(playerId, requestedOperationId);
        final LinkedHashMap<String, CompletableFuture<Outcome>> playerOps =
                inFlight.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>());
        synchronized (playerOps) {
            final CompletableFuture<Outcome> existing = playerOps.get(operationId);
            if (existing != null) return existing;
            final CompletableFuture<Outcome> created = new CompletableFuture<>();
            playerOps.put(operationId, created);
            if (!accepting.get() || closingPlayers.contains(playerId)) {
                created.complete(failureOutcome(player, Outcome.Status.PERSISTENCE_FAILED));
                return created;
            }
            try { executor.execute(() -> execute(player, operationId, created)); }
            catch (final RejectedExecutionException rejected) {
                created.complete(failureOutcome(player, Outcome.Status.PERSISTENCE_FAILED));
            }
            return created;
        }
    }

    static String attemptOperationId(final UUID playerId, final String requestedOperationId) {
        Objects.requireNonNull(playerId, "playerId");
        if (requestedOperationId == null || requestedOperationId.isBlank()) {
            throw new IllegalArgumentException("operationId required");
        }
        final String requested = requestedOperationId.trim();
        final String legacyPrefix = "player-respec:" + playerId + ':';
        if (requested.startsWith(legacyPrefix)) {
            final String suffix = requested.substring(legacyPrefix.length());
            if (suffix.matches("-?[0-9]+")) {
                return legacyPrefix + "attempt:" + UUID.randomUUID();
            }
        }
        return requested;
    }

    private void execute(final Player player, final String operationId,
                         final CompletableFuture<Outcome> completion) {
        final UUID playerId = player.getUniqueId();
        final ClassSpecProfileGateway gateway = specializationManager.profileGateway();
        final double cost = specializationManager.getRespecCost();
        final CurrencyType currency = CurrencyType.fromFactionType(factionManager.getEconomyFaction(playerId));
        final UUID sessionToken = gateway.currentSessionToken(playerId).orElse(null);
        if (sessionToken == null || !gateway.isSessionReady(playerId)
                || gateway.activeSpecId(playerId).isEmpty()) {
            completion.complete(new Outcome(Outcome.Status.NOTHING_TO_RESPEC, cost, currency, 0));
            return;
        }
        final long revision = gateway.diagnostic(playerId).revision();
        RespecTransactionJournal.Entry entry = journal.load(operationId);
        if (entry == null) {
            entry = new RespecTransactionJournal.Entry(operationId, playerId,
                    RespecTransactionJournal.Stage.INTENT, currency.name(), cost,
                    System.currentTimeMillis(), revision, false, false, Map.of(), Map.of(),
                    "respec intent persisted");
            journal.save(entry);
        } else if (!entry.playerId().equals(playerId) || entry.amount() != cost
                || !entry.currencyId().equals(currency.name())) {
            gateway.blockSession(playerId, "Respec operation identity conflict: " + operationId);
            completion.complete(new Outcome(Outcome.Status.PERSISTENCE_FAILED, cost, currency, 0));
            return;
        }

        try {
            CurrencyManager.DurableWalletOperation wallet = null;
            if (cost > 0.0D) {
                wallet = currencyManager.debitOperation(playerId, currency, cost, operationId);
                if (wallet == null) {
                    journal.delete(operationId);
                    completion.complete(new Outcome(Outcome.Status.INSUFFICIENT_FUNDS, cost, currency, 0));
                    return;
                }
                entry = entry.withWallet(wallet.previousPresent(), stringify(wallet.previous()),
                        stringify(wallet.expected()));
                journal.save(entry);
            }
            final ProfileMutationResult<?> result = specializationManager.profileGateway().reset(playerId,
                    new ClassSpecProfileGateway.ResetRequest(ClassSpecProfileGateway.ResetMode.LOADOUT_RESPEC,
                            java.util.Optional.ofNullable(gateway.diagnostic(playerId).activeSlot()
                                    .orElse(hu.taliann.icesmp.classspec.domain.LoadoutSlot.FIRST)),
                            operationId, Double.toString(cost), currency.name()))
                    .toCompletableFuture().join();
            if (!result.durableOutcomeAccepted()) {
                if (wallet != null) currencyManager.rollbackOperation(operationId);
                journal.delete(operationId);
                completion.complete(new Outcome(Outcome.Status.PERSISTENCE_FAILED, cost, currency, 0));
                return;
            }
            journal.save(entry.withStage(RespecTransactionJournal.Stage.PROFILE_COMMITTED,
                    "Profile v2 respec receipt committed"));
            if (wallet != null) currencyManager.commitOperation(operationId);
            if (result.status() == ProfileMutationResult.Status.RUNTIME_EFFECT_FAILED) {
                journal.save(entry.withStage(RespecTransactionJournal.Stage.RUNTIME_PENDING,
                        result.detail()));
                gateway.blockSession(playerId, "Respec runtime reconciliation pending: " + result.detail());
                completion.complete(new Outcome(Outcome.Status.RUNTIME_FAILED, cost, currency, 0));
                return;
            }
            final RespecTransactionJournal.Entry committedEntry = entry;
            completePlayerEffects(player, sessionToken).whenComplete((refunded, effectFailure) -> {
                if (effectFailure != null) {
                    journal.save(committedEntry.withStage(RespecTransactionJournal.Stage.RUNTIME_PENDING,
                            safeMessage(effectFailure)));
                    gateway.blockSession(playerId, "Respec post-commit effects failed: "
                            + safeMessage(effectFailure));
                    completion.complete(new Outcome(Outcome.Status.RUNTIME_FAILED, cost, currency, 0));
                } else {
                    journal.delete(operationId);
                    completion.complete(new Outcome(Outcome.Status.OK, cost, currency, refunded));
                }
            });
        } catch (final Throwable failure) {
            gateway.blockSession(playerId, "Respec transaction failed: " + safeMessage(failure));
            completion.complete(new Outcome(Outcome.Status.PERSISTENCE_FAILED, cost, currency, 0));
        }
    }

    public CompletionStage<Void> recoverPending(final Player player) {
        Objects.requireNonNull(player, "player");
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    for (final RespecTransactionJournal.Entry entry : journal.findByPlayer(player.getUniqueId())) {
                        if (isProfession(entry)) recoverProfessionEntry(player, entry);
                        else recoverEntry(player, entry);
                    }
                    completion.complete(null);
                } catch (final Throwable failure) { completion.completeExceptionally(failure); }
            });
        } catch (final RejectedExecutionException rejected) { completion.completeExceptionally(rejected); }
        return completion;
    }

    private void recoverProfessionEntry(final Player player, final RespecTransactionJournal.Entry entry) {
        final UUID playerId = player.getUniqueId();
        final boolean profileCommitted = professionProgressStore
                .professionSpecialization(playerId).isEmpty();
        final CurrencyManager.DurableWalletOperation wallet = currencyManager
                .durableOperation(entry.operationId()).orElse(null);
        final RespecRecoveryProtocol.WalletWitness witness = walletWitness(entry.amount(), wallet);
        final RespecRecoveryProtocol.Decision decision = RespecRecoveryProtocol.decide(
                profileCommitted, entry.amount(), witness);
        switch (decision.action()) {
            case COMPLETE -> {
                if (profileCommitted) specializationManager.resetProfessionSpecialization(player);
                journal.delete(entry.operationId());
            }
            case COMMIT_WALLET_AND_COMPLETE -> {
                currencyManager.commitOperation(entry.operationId());
                specializationManager.resetProfessionSpecialization(player);
                journal.delete(entry.operationId());
            }
            case ROLLBACK_WALLET_AND_ABORT -> {
                currencyManager.rollbackOperation(entry.operationId());
                journal.delete(entry.operationId());
            }
            case DELETE_INTENT_AND_ABORT -> journal.delete(entry.operationId());
            case BLOCK_INCONSISTENT -> throw new IllegalStateException(
                    "Profession respec recovery inconsistent: " + decision.reason());
        }
    }

    private void recoverEntry(final Player player, final RespecTransactionJournal.Entry entry) {
        final ClassSpecProfileGateway gateway = specializationManager.profileGateway();
        final boolean profileCommitted = gateway.operation(entry.playerId(), entry.operationId())
                .filter(operation -> operation.type() == ProfileOperationType.RESPEC
                        && operation.status() == ProfileOperationStatus.COMMITTED).isPresent();
        final CurrencyManager.DurableWalletOperation wallet = currencyManager
                .durableOperation(entry.operationId()).orElse(null);
        final RespecRecoveryProtocol.WalletWitness witness = walletWitness(entry.amount(), wallet);
        final RespecRecoveryProtocol.Decision decision = RespecRecoveryProtocol.decide(
                profileCommitted, entry.amount(), witness);
        switch (decision.action()) {
            case COMPLETE -> {
                if (profileCommitted) completePlayerEffects(player,
                        gateway.currentSessionToken(entry.playerId()).orElseThrow()).toCompletableFuture().join();
                journal.delete(entry.operationId());
            }
            case COMMIT_WALLET_AND_COMPLETE -> {
                currencyManager.commitOperation(entry.operationId());
                completePlayerEffects(player, gateway.currentSessionToken(entry.playerId()).orElseThrow())
                        .toCompletableFuture().join();
                journal.delete(entry.operationId());
            }
            case ROLLBACK_WALLET_AND_ABORT -> {
                currencyManager.rollbackOperation(entry.operationId());
                journal.delete(entry.operationId());
            }
            case DELETE_INTENT_AND_ABORT -> journal.delete(entry.operationId());
            case BLOCK_INCONSISTENT -> {
                gateway.blockSession(entry.playerId(), "Respec recovery inconsistent: " + decision.reason());
                throw new IllegalStateException(decision.reason());
            }
        }
    }

    private static RespecRecoveryProtocol.WalletWitness walletWitness(
            final double amount, final CurrencyManager.DurableWalletOperation wallet) {
        if (amount == 0.0D) return RespecRecoveryProtocol.WalletWitness.NOT_REQUIRED;
        if (wallet == null) return RespecRecoveryProtocol.WalletWitness.MISSING;
        return switch (wallet.status()) {
            case DEBITED -> RespecRecoveryProtocol.WalletWitness.DEBITED;
            case COMMITTED -> RespecRecoveryProtocol.WalletWitness.COMMITTED;
            case ROLLED_BACK -> RespecRecoveryProtocol.WalletWitness.ROLLED_BACK;
        };
    }

    private CompletionStage<Integer> completePlayerEffects(final Player player, final UUID sessionToken) {
        final ClassSpecProfileGateway gateway = specializationManager.profileGateway();
        final TalentManager.EligibilityContext eligibility = gateway.currentProfile(player.getUniqueId())
                .map(TalentManager.EligibilityContext::from)
                .orElseThrow(() -> new IllegalStateException(
                        "durable class/spec state unavailable during respec recovery"));
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            if (!gateway.isCurrentSession(player.getUniqueId(), sessionToken)) {
                gate.completeExceptionally(new IllegalStateException("stale respec session completion"));
                return;
            }
            gate.complete(null);
        }, () -> gate.completeExceptionally(
                new IllegalStateException("Player scheduler rejected respec completion")));
        return gate.thenCompose(ignored ->
                talentManager.refundUnavailableTalents(player, true, eligibility));
    }

    private static Map<String, Double> stringify(final Map<CurrencyType, Double> values) {
        final Map<String, Double> result = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key.name(), value));
        return Map.copyOf(result);
    }

    private static String safeMessage(final Throwable failure) {
        Throwable current = failure;
        while (current instanceof java.util.concurrent.CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private Outcome failureOutcome(final Player player, final Outcome.Status status) {
        return new Outcome(status, specializationManager.getRespecCost(), CurrencyType.fromFactionType(
                factionManager.getEconomyFaction(player.getUniqueId())), 0);
    }

    public void clearSession(final UUID playerId) {
        final LinkedHashMap<String, CompletableFuture<Outcome>> operations = inFlight.get(playerId);
        if (operations != null) {
            synchronized (operations) {
                operations.entrySet().removeIf(entry -> entry.getValue().isDone());
                if (operations.isEmpty()) inFlight.remove(playerId, operations);
            }
        }
        final CompletableFuture<Outcome> profession = professionInFlight.get(playerId);
        if (profession != null && profession.isDone()) professionInFlight.remove(playerId, profession);
    }

    public void closeSession(final UUID playerId) { if (playerId != null) closingPlayers.add(playerId); }
    public void openSession(final UUID playerId) { if (playerId != null && accepting.get()) closingPlayers.remove(playerId); }

    public CompletionStage<Void> awaitPlayerTransactions(final UUID playerId) {
        final java.util.ArrayList<CompletableFuture<?>> pending = new java.util.ArrayList<>();
        final LinkedHashMap<String, CompletableFuture<Outcome>> operations = inFlight.get(playerId);
        if (operations != null) {
            synchronized (operations) { pending.addAll(operations.values()); }
        }
        final CompletableFuture<Outcome> profession = professionInFlight.get(playerId);
        if (profession != null) pending.add(profession);
        return pending.isEmpty() ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
    }

    public boolean prepareShutdown(final long timeoutMillis) {
        accepting.set(false);
        closingPlayers.addAll(inFlight.keySet());
        closingPlayers.addAll(professionInFlight.keySet());
        executor.shutdown();
        try { return executor.awaitTermination(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS); }
        catch (final InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
    }
}
