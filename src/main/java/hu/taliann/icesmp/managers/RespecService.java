package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.classspec.domain.ProfileOperationStatus;
import hu.taliann.icesmp.classspec.domain.ProfileOperationType;
import hu.taliann.icesmp.classspec.transaction.RespecRecoveryProtocol;
import hu.taliann.icesmp.classspec.transaction.RespecTransactionJournal;
import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        final Thread thread = new Thread(task, "IceSMP-profile-v2-respec");
        thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Map<UUID, LinkedHashMap<String, CompletableFuture<Outcome>>> inFlight =
            new ConcurrentHashMap<>();
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

    /** Profession respec remains outside the class Profile v2 aggregate. */
    public Outcome respec(final Player player, final boolean classPool) {
        if (classPool) return failureOutcome(player, Outcome.Status.PERSISTENCE_FAILED);
        final double cost = specializationManager.getRespecCost();
        final CurrencyType currency = CurrencyType.fromFactionType(
                factionManager.getEconomyFaction(player.getUniqueId()));
        if (specializationManager.getProfessionSpecialization(player) == null) {
            return new Outcome(Outcome.Status.NOTHING_TO_RESPEC, cost, currency, 0);
        }
        if (cost > 0 && !currencyManager.deductFromBalance(player.getUniqueId(), currency, cost)) {
            return new Outcome(Outcome.Status.INSUFFICIENT_FUNDS, cost, currency, 0);
        }
        specializationManager.resetProfessionSpecialization(player);
        return new Outcome(Outcome.Status.OK, cost, currency, 0);
    }

    public CompletionStage<Outcome> respecV2(final Player player, final String operationId) {
        Objects.requireNonNull(player, "player");
        if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operationId required");
        final UUID playerId = player.getUniqueId();
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
            if (!result.durableMutationApplied()) {
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
                        recoverEntry(player, entry);
                    }
                    completion.complete(null);
                } catch (final Throwable failure) { completion.completeExceptionally(failure); }
            });
        } catch (final RejectedExecutionException rejected) { completion.completeExceptionally(rejected); }
        return completion;
    }

    private void recoverEntry(final Player player, final RespecTransactionJournal.Entry entry) {
        final ClassSpecProfileGateway gateway = specializationManager.profileGateway();
        final boolean profileCommitted = gateway.operation(entry.playerId(), entry.operationId())
                .filter(operation -> operation.type() == ProfileOperationType.RESPEC
                        && operation.status() == ProfileOperationStatus.COMMITTED).isPresent();
        final CurrencyManager.DurableWalletOperation wallet = currencyManager
                .durableOperation(entry.operationId()).orElse(null);
        final RespecRecoveryProtocol.WalletWitness witness;
        if (entry.amount() == 0.0D) witness = RespecRecoveryProtocol.WalletWitness.NOT_REQUIRED;
        else if (wallet == null) witness = RespecRecoveryProtocol.WalletWitness.MISSING;
        else witness = switch (wallet.status()) {
            case DEBITED -> RespecRecoveryProtocol.WalletWitness.DEBITED;
            case COMMITTED -> RespecRecoveryProtocol.WalletWitness.COMMITTED;
            case ROLLED_BACK -> RespecRecoveryProtocol.WalletWitness.ROLLED_BACK;
        };
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

    private CompletionStage<Integer> completePlayerEffects(final Player player, final UUID sessionToken) {
        final CompletableFuture<Integer> result = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            final ClassSpecProfileGateway gateway = specializationManager.profileGateway();
            if (!gateway.isCurrentSession(player.getUniqueId(), sessionToken)) {
                result.completeExceptionally(new IllegalStateException("stale respec session completion"));
                return;
            }
            try { result.complete(talentManager.refundUnavailableTalents(player, true)); }
            catch (final Throwable failure) { result.completeExceptionally(failure); }
        }, () -> result.completeExceptionally(new IllegalStateException("Player scheduler rejected respec completion")));
        return result;
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
        if (operations == null) return;
        synchronized (operations) {
            operations.entrySet().removeIf(entry -> entry.getValue().isDone());
            if (operations.isEmpty()) inFlight.remove(playerId, operations);
        }
    }
    public void closeSession(final UUID playerId) { if (playerId != null) closingPlayers.add(playerId); }
    public void openSession(final UUID playerId) { if (playerId != null && accepting.get()) closingPlayers.remove(playerId); }

    public CompletionStage<Void> awaitPlayerTransactions(final UUID playerId) {
        final LinkedHashMap<String, CompletableFuture<Outcome>> operations = inFlight.get(playerId);
        if (operations == null) return CompletableFuture.completedFuture(null);
        synchronized (operations) {
            return CompletableFuture.allOf(operations.values().toArray(CompletableFuture[]::new));
        }
    }

    public boolean prepareShutdown(final long timeoutMillis) {
        accepting.set(false); closingPlayers.addAll(inFlight.keySet()); executor.shutdown();
        try { return executor.awaitTermination(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS); }
        catch (final InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
    }
}
