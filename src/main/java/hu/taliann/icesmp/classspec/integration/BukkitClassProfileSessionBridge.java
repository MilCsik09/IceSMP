package hu.taliann.icesmp.classspec.integration;

import hu.taliann.icesmp.classspec.application.ClassProfileLifecycleService;
import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.ClassSpecRuntimePort;
import hu.taliann.icesmp.classspec.migration.BukkitLegacyProfileSnapshotReader;
import hu.taliann.icesmp.classspec.migration.LegacyProfileSnapshot;
import hu.taliann.icesmp.managers.RespecService;
import hu.taliann.icesmp.managers.SpecializationManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Paper/Folia owner-scheduler bridge for join, logout and disable profile lifecycle. */
public final class BukkitClassProfileSessionBridge {

    private final JavaPlugin plugin;
    private final BukkitLegacyProfileSnapshotReader legacyReader;
    private final ClassProfileLifecycleService lifecycle;
    private final ClassSpecProfileGateway gateway;
    private final SpecializationManager specializationManager;
    private final BukkitClassSpecRuntimeAdapter runtime;
    private final RespecService respecService;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Object sessionLock = new Object();
    private final ConcurrentHashMap<UUID, UUID> sessionGenerations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> pendingLogouts =
            new ConcurrentHashMap<>();

    public BukkitClassProfileSessionBridge(final JavaPlugin plugin,
                                           final BukkitLegacyProfileSnapshotReader legacyReader,
                                           final ClassProfileLifecycleService lifecycle,
                                           final ClassSpecProfileGateway gateway,
                                           final SpecializationManager specializationManager,
                                           final BukkitClassSpecRuntimeAdapter runtime,
                                           final RespecService respecService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.legacyReader = Objects.requireNonNull(legacyReader, "legacyReader");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.specializationManager = Objects.requireNonNull(specializationManager, "specializationManager");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.respecService = Objects.requireNonNull(respecService, "respecService");
    }

    /** Called on the joining player's owner thread; only the immutable snapshot crosses threads. */
    public void join(final Player player) {
        if (!gateway.enabled() || !accepting.get()) {
            return;
        }
        final LegacyProfileSnapshot snapshot = legacyReader.read(player);
        final UUID playerId = snapshot.playerId();
        final UUID generation;
        final CompletableFuture<Void> priorLogout;
        synchronized (sessionLock) {
            if (!accepting.get()) {
                return;
            }
            generation = nextGeneration(playerId);
            gateway.beginSessionActivation(playerId);
            priorLogout = pendingLogouts.get(playerId);
        }
        final CompletionStage<Void> admission = priorLogout == null
                ? CompletableFuture.completedFuture(null)
                : priorLogout.handle((ignored, failure) -> null);
        admission.thenRun(() -> startJoin(snapshot, generation));
    }

    public CompletionStage<Void> quit(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!gateway.enabled() || !accepting.get()) {
            return CompletableFuture.completedFuture(null);
        }
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        final UUID generation;
        final CompletableFuture<Void> previous;
        synchronized (sessionLock) {
            if (!accepting.get()) {
                return CompletableFuture.completedFuture(null);
            }
            generation = nextGeneration(playerId);
            respecService.closeSession(playerId);
            previous = pendingLogouts.put(playerId, completion);
        }
        final CompletionStage<Void> start = previous == null
                ? CompletableFuture.completedFuture(null)
                : previous.handle((ignored, failure) -> null);
        start.thenCompose(ignored -> respecService.awaitPlayerTransactions(playerId))
                .thenCompose(ignored -> lifecycle.logout(playerId))
                .whenComplete((ignored, failure) -> {
                    synchronized (sessionLock) {
                        if (sessionGenerations.remove(playerId, generation)) {
                            gateway.cancelSessionActivation(playerId);
                        }
                    }
                    gateway.clearSession(playerId);
                    respecService.clearSession(playerId);
                    if (failure != null) {
                        plugin.getLogger().severe("Profile v2 logout flush failed for " + playerId
                                + ": " + safeMessage(failure));
                        completion.completeExceptionally(failure);
                    } else {
                        completion.complete(null);
                    }
                    pendingLogouts.remove(playerId, completion);
                });
        return completion;
    }

    public CompletionStage<Void> prepareDisable() {
        final CompletableFuture<?>[] logouts;
        synchronized (sessionLock) {
            accepting.set(false);
            logouts = pendingLogouts.values().toArray(CompletableFuture[]::new);
        }
        return gateway.prepareShutdown()
                .thenCompose(ignored -> CompletableFuture.allOf(logouts))
                .thenCompose(ignored -> lifecycle.prepareDisable());
    }

    public void stopRuntime() {
        runtime.stop();
    }

    private void startJoin(final LegacyProfileSnapshot snapshot, final UUID generation) {
        final UUID playerId = snapshot.playerId();
        if (!isCurrent(playerId, generation)) {
            return;
        }
        respecService.openSession(playerId);
        lifecycle.join(snapshot).whenComplete((result, failure) -> {
            if (!isCurrent(playerId, generation)) {
                return;
            }
            final Player online = Bukkit.getPlayer(playerId);
            if (online == null) {
                return;
            }
            online.getScheduler().run(plugin, task -> {
                if (!isCurrent(playerId, generation)) {
                    return;
                }
                if (failure != null || result == null
                        || result.status() != ClassProfileLifecycleService.Status.READY) {
                    final String reason = failure != null ? safeMessage(failure)
                            : result == null ? "missing profile load result" : result.diagnostic();
                    plugin.getLogger().warning("Profile v2 blocked for " + playerId + ": " + reason);
                    gateway.blockSession(playerId, reason);
                    return;
                }
                final var profile = result.profileOptional().orElseThrow();
                specializationManager.reconcileDarkGates(online).whenComplete((gateResult, gateFailure) -> {
                    if (!isCurrent(playerId, generation)) {
                        return;
                    }
                    if (gateFailure != null || gateResult == null) {
                        gateway.blockSession(playerId, gateFailure == null
                                ? "missing DARK gate reconcile result"
                                : "DARK gate reconcile failed: " + safeMessage(gateFailure));
                        return;
                    }
                    if (gateResult.committed()) {
                        gateway.completeSessionActivation(playerId);
                        return;
                    }
                    if (gateResult.status()
                            != hu.taliann.icesmp.classspec.application.ProfileMutationResult.Status.NO_CHANGE) {
                        gateway.blockSession(playerId,
                                "DARK gate reconcile blocked login: " + gateResult.detail());
                        return;
                    }
                    runtime.profileCommitted(playerId, profile, profile,
                                    ClassSpecRuntimePort.MutationKind.GATE_RECONCILE)
                            .whenComplete((runtimeIgnored, runtimeFailure) -> {
                                if (!isCurrent(playerId, generation)) {
                                    return;
                                }
                                if (runtimeFailure != null) {
                                    gateway.blockSession(playerId,
                                            "Profile login runtime rebuild failed: "
                                                    + safeMessage(runtimeFailure));
                                } else {
                                    gateway.completeSessionActivation(playerId);
                                }
                            });
                });
            }, () -> {
                if (isCurrent(playerId, generation)) {
                    gateway.blockSession(playerId, "Player scheduler rejected Profile v2 login activation");
                }
            });
        });
    }

    private UUID nextGeneration(final UUID playerId) {
        final UUID token = UUID.randomUUID();
        sessionGenerations.put(playerId, token);
        return token;
    }

    private boolean isCurrent(final UUID playerId, final UUID generation) {
        return accepting.get() && generation.equals(sessionGenerations.get(playerId));
    }

    private static String safeMessage(final Throwable failure) {
        Throwable current = failure;
        while (current instanceof java.util.concurrent.CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
