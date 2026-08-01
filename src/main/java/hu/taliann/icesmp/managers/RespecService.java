package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A specializáció-visszaváltás (respec) EGYETLEN végrehajtó helye: feltétel-ellenőrzés,
 * ár levonása, spec törlése és a talentpont-visszatérítés.
 *
 * <p>Két belépési pont hívja ({@code /spec respec} és a Karakter-GUI), ezért a folyamat nem
 * élhet egyikben sem: pénzt mozgat, és ha a két példány szétcsúszik, az egyik úton rosszul
 * számol. A hívók CSAK megjelenítenek — a szolgáltatás adatot ad vissza, nem üzenetet, hogy a
 * parancs és a GUI megtarthassa a saját szövegét/hangját.
 *
 * <p>Külön osztály (nem a {@code SpecializationManager} metódusa), mert a művelethez
 * {@link TalentManager} is kell, az pedig a DI-sorrendben a SpecializationManager UTÁN épül —
 * ott a hivatkozás kört zárna.
 */
public final class RespecService {

    /** A respec kimenete; a szöveget/hangot a hívó adja hozzá. */
    public record Outcome(Status status, double cost, CurrencyType currency, int refundedTalentPoints) {

        public enum Status {
            /** Sikerült: az ár levonva, a spec törölve. */
            OK,
            /** Nincs mit visszaváltani (nincs ilyen specializáció). */
            NOTHING_TO_RESPEC,
            /** Nincs elég egyenleg az árra. */
            INSUFFICIENT_FUNDS,
            /** A Profile v2 commit meghiúsult; a levonás vissza lett görgetve. */
            PERSISTENCE_FAILED,
            /** A profil már tartósan commitolt, de a scheduler/runtime befejezés auditot igényel. */
            RUNTIME_FAILED,
            /** A profil hibázott és az automatikus valuta-visszatérítés is auditot igényel. */
            REFUND_FAILED
        }

        public boolean ok() {
            return status == Status.OK;
        }
    }

    private final JavaPlugin plugin;
    private final SpecializationManager specializationManager;
    private final TalentManager talentManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final ExecutorService transactionExecutor = Executors.newSingleThreadExecutor(task -> {
        final Thread thread = new Thread(task, "IceSMP-profile-v2-respec");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Map<UUID, LinkedHashMap<String, CompletableFuture<Outcome>>> receipts =
            new ConcurrentHashMap<>();
    private final java.util.Set<UUID> closingPlayers = ConcurrentHashMap.newKeySet();

    public RespecService(final JavaPlugin plugin, final SpecializationManager specializationManager,
                         final TalentManager talentManager,
                         final CurrencyManager currencyManager, final FactionManager factionManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.specializationManager = specializationManager;
        this.talentManager = talentManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
    }

    /**
     * Respec végrehajtása.
     *
     * @param classPool true = kaszt-specializáció, false = szakma-specializáció
     */
    public Outcome respec(final Player player, final boolean classPool) {
        if (classPool && specializationManager.profileV2Enabled()) {
            final FactionType faction = factionManager.getFaction(player.getUniqueId());
            return new Outcome(Outcome.Status.PERSISTENCE_FAILED,
                    specializationManager.getRespecCost(), CurrencyType.fromFactionType(faction), 0);
        }
        final double cost = specializationManager.getRespecCost();
        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        final CurrencyType currency = CurrencyType.fromFactionType(faction);

        final boolean hasSpec = classPool
                ? specializationManager.getClassSpecialization(player) != null
                : specializationManager.getProfessionSpecialization(player) != null;
        if (!hasSpec) {
            return new Outcome(Outcome.Status.NOTHING_TO_RESPEC, cost, currency, 0);
        }
        // Atomi levonás (nincs get+set verseny): konkurens egyenleg-írás nem veszhet el.
        if (cost > 0.0D && !currencyManager.deductFromBalance(player.getUniqueId(), currency, cost)) {
            return new Outcome(Outcome.Status.INSUFFICIENT_FUNDS, cost, currency, 0);
        }

        int refunded = 0;
        if (classPool) {
            specializationManager.resetClassSpecialization(player);
            // A spec-hez kötött talentek elvesznek a speccel; a pontjuk visszakerül a készletbe.
            refunded = talentManager.refundUnavailableTalents(player, true);
        } else {
            specializationManager.resetProfessionSpecialization(player);
        }
        return new Outcome(Outcome.Status.OK, cost, currency, refunded);
    }

    /**
     * Profile v2 respec transaction: durable wallet deduction, CAS profile reset, scheduler-owned
     * PDC side effects. A failed profile commit rolls the exact wallet token back durably.
     */
    public CompletionStage<Outcome> respecV2(final Player player, final String operationId) {
        Objects.requireNonNull(player, "player");
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId must be non-blank");
        }
        final UUID playerId = player.getUniqueId();
        final LinkedHashMap<String, CompletableFuture<Outcome>> playerReceipts = receipts.computeIfAbsent(
                playerId, ignored -> new LinkedHashMap<>());
        synchronized (playerReceipts) {
            final CompletableFuture<Outcome> existing = playerReceipts.get(operationId);
            if (existing != null) {
                return existing;
            }
            final CompletableFuture<Outcome> created = new CompletableFuture<>();
            playerReceipts.put(operationId, created);
            trimCompletedReceipts(playerReceipts);
            created.whenComplete((ignored, failure) -> {
                synchronized (playerReceipts) {
                    trimCompletedReceipts(playerReceipts);
                }
            });
            if (!accepting.get() || closingPlayers.contains(playerId)) {
                created.complete(failureOutcome(player, Outcome.Status.PERSISTENCE_FAILED));
                return created;
            }
            final double cost = specializationManager.getRespecCost();
            final CurrencyType currency = CurrencyType.fromFactionType(
                    factionManager.getFaction(playerId));
            try {
                transactionExecutor.execute(() -> executeV2(
                        player, playerId, cost, currency, operationId, created));
            } catch (final RejectedExecutionException rejected) {
                created.complete(failureOutcome(player, Outcome.Status.PERSISTENCE_FAILED));
            }
            return created;
        }
    }

    private void executeV2(final Player player, final UUID playerId,
                           final double cost, final CurrencyType currency,
                           final String operationId,
                           final CompletableFuture<Outcome> completion) {
        if (!specializationManager.profileV2Enabled()
                || specializationManager.profileGateway().activeSpecId(playerId).isEmpty()) {
            completion.complete(new Outcome(Outcome.Status.NOTHING_TO_RESPEC, cost, currency, 0));
            return;
        }

        CurrencyManager.DurableMutation walletToken = null;
        boolean profileCommitted = false;
        try {
            if (cost > 0.0D) {
                walletToken = currencyManager.deductDurably(playerId, currency, cost);
                if (walletToken == null) {
                    completion.complete(new Outcome(Outcome.Status.INSUFFICIENT_FUNDS,
                            cost, currency, 0));
                    return;
                }
            }
            final var result = specializationManager.resetClassProfileV2(playerId, false, operationId)
                    .toCompletableFuture().join();
            profileCommitted = result.durableMutationApplied();
            if (result.status()
                    == hu.taliann.icesmp.classspec.application.ProfileMutationResult.Status.RUNTIME_EFFECT_FAILED) {
                specializationManager.profileGateway().blockSession(playerId,
                        "Respec profile committed, but runtime reconciliation failed: " + result.detail());
                completion.complete(new Outcome(Outcome.Status.RUNTIME_FAILED,
                        cost, currency, 0));
                return;
            }
            if (!result.committed()) {
                if (walletToken != null) {
                    currencyManager.rollbackDurably(walletToken);
                }
                completion.complete(new Outcome(Outcome.Status.PERSISTENCE_FAILED,
                        cost, currency, 0));
                return;
            }
            final CompletableFuture<Integer> refund = new CompletableFuture<>();
            player.getScheduler().run(plugin, task -> {
                try {
                    refund.complete(talentManager.refundUnavailableTalents(player, true));
                } catch (final Throwable failure) {
                    refund.completeExceptionally(failure);
                }
            }, () -> refund.completeExceptionally(
                    new IllegalStateException("Player scheduler rejected respec completion")));
            completion.complete(new Outcome(Outcome.Status.OK, cost, currency, refund.join()));
        } catch (final Throwable failure) {
            if (profileCommitted) {
                specializationManager.profileGateway().blockSession(playerId,
                        "Respec profile committed, but talent/runtime completion failed: "
                                + safeMessage(failure));
                completion.complete(new Outcome(Outcome.Status.RUNTIME_FAILED,
                        cost, currency, 0));
            } else if (walletToken != null) {
                try {
                    currencyManager.rollbackDurably(walletToken);
                    completion.complete(new Outcome(Outcome.Status.PERSISTENCE_FAILED,
                            cost, currency, 0));
                } catch (final Throwable rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                    completion.complete(new Outcome(Outcome.Status.REFUND_FAILED,
                            cost, currency, 0));
                }
            } else {
                completion.complete(new Outcome(Outcome.Status.PERSISTENCE_FAILED,
                        cost, currency, 0));
            }
        }
    }

    private static String safeMessage(final Throwable failure) {
        Throwable current = failure;
        while (current instanceof java.util.concurrent.CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName() : current.getMessage();
    }

    private Outcome failureOutcome(final Player player, final Outcome.Status status) {
        final CurrencyType currency = CurrencyType.fromFactionType(
                factionManager.getFaction(player.getUniqueId()));
        return new Outcome(status, specializationManager.getRespecCost(), currency, 0);
    }

    public void clearSession(final UUID playerId) {
        receipts.remove(playerId);
    }

    /** Closes one logout session before its already-admitted economic tasks are drained. */
    public void closeSession(final UUID playerId) {
        if (playerId != null) {
            closingPlayers.add(playerId);
        }
    }

    /** Opens a fresh login generation after the previous generation fully drained. */
    public void openSession(final UUID playerId) {
        if (playerId != null && accepting.get()) {
            closingPlayers.remove(playerId);
        }
    }

    public CompletionStage<Void> awaitPlayerTransactions(final UUID playerId) {
        final LinkedHashMap<String, CompletableFuture<Outcome>> playerReceipts = receipts.get(playerId);
        if (playerReceipts == null) {
            return CompletableFuture.completedFuture(null);
        }
        final CompletableFuture<?>[] pending;
        synchronized (playerReceipts) {
            pending = playerReceipts.values().toArray(CompletableFuture[]::new);
        }
        return CompletableFuture.allOf(pending);
    }

    private static void trimCompletedReceipts(
            final LinkedHashMap<String, CompletableFuture<Outcome>> playerReceipts) {
        final var iterator = playerReceipts.entrySet().iterator();
        while (playerReceipts.size() > 32 && iterator.hasNext()) {
            if (iterator.next().getValue().isDone()) {
                iterator.remove();
            }
        }
    }

    /** Drains all already-admitted economic/profile transactions before disable flush. */
    public boolean prepareShutdown(final long timeoutMillis) {
        accepting.set(false);
        closingPlayers.addAll(receipts.keySet());
        transactionExecutor.shutdown();
        try {
            return transactionExecutor.awaitTermination(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
