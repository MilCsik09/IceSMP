package hu.taliann.icesmp.classspec.integration;

import hu.taliann.icesmp.classspec.application.*;
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

/** Folia owner-scheduler bridge with generation fencing across every callback hop. */
public final class BukkitClassProfileSessionBridge {
    private final JavaPlugin plugin;private final ClassProfileLifecycleService lifecycle;private final ClassSpecProfileGateway gateway;private final ProfileSessionRegistry sessions;private final SpecializationManager specializationManager;private final BukkitClassSpecRuntimeAdapter runtime;private final RespecService respecService;private final AtomicBoolean accepting=new AtomicBoolean(true);private final Object lock=new Object();private final ConcurrentHashMap<UUID,CompletableFuture<Void>> pendingLogouts=new ConcurrentHashMap<>();
    public BukkitClassProfileSessionBridge(JavaPlugin plugin,ClassProfileLifecycleService lifecycle,ClassSpecProfileGateway gateway,ProfileSessionRegistry sessions,SpecializationManager specializationManager,BukkitClassSpecRuntimeAdapter runtime,RespecService respecService){this.plugin=Objects.requireNonNull(plugin);this.lifecycle=Objects.requireNonNull(lifecycle);this.gateway=Objects.requireNonNull(gateway);this.sessions=Objects.requireNonNull(sessions);this.specializationManager=Objects.requireNonNull(specializationManager);this.runtime=Objects.requireNonNull(runtime);this.respecService=Objects.requireNonNull(respecService);}
    public void join(Player player){if(!accepting.get())return;UUID id=player.getUniqueId(),token;CompletableFuture<Void> prior;synchronized(lock){if(!accepting.get())return;token=sessions.begin(id);gateway.beginSessionActivation(id,token);prior=pendingLogouts.get(id);}CompletionStage<Void> admission=prior==null?CompletableFuture.completedFuture(null):prior.handle((v,x)->null);admission.thenRun(()->startJoin(id,token));}
    public CompletionStage<Void> quit(UUID id){Objects.requireNonNull(id);if(!accepting.get())return CompletableFuture.completedFuture(null);UUID token=sessions.currentToken(id).orElse(null);if(token==null)return CompletableFuture.completedFuture(null);sessions.markClosing(id,token);CompletableFuture<Void> completion=new CompletableFuture<>(),previous=pendingLogouts.put(id,completion);CompletionStage<Void> start=previous==null?CompletableFuture.completedFuture(null):previous.handle((v,x)->null);respecService.closeSession(id);start.thenCompose(v->gateway.awaitPlayerMutations(id)).thenCompose(v->respecService.awaitPlayerTransactions(id)).thenCompose(v->lifecycle.logout(id)).whenComplete((v,x)->{gateway.clearSession(id);respecService.clearSession(id);sessions.close(id,token);pendingLogouts.remove(id,completion);if(x==null)completion.complete(null);else{plugin.getLogger().severe("Profile v2 logout failed for "+id+": "+message(x));completion.completeExceptionally(x);}});return completion;}
    public CompletionStage<Void> prepareDisable(){CompletableFuture<?>[] logouts;synchronized(lock){accepting.set(false);logouts=pendingLogouts.values().toArray(CompletableFuture[]::new);}return gateway.prepareShutdown().thenCompose(v->CompletableFuture.allOf(logouts)).thenCompose(v->lifecycle.prepareDisable());}
    public void stopRuntime(){runtime.stop();}
    private void startJoin(final UUID id, final UUID token) {
        if (!sessions.isCurrent(id, token)) return;
        respecService.openSession(id);
        lifecycle.join(id).whenComplete((result, failure) -> {
            if (!sessions.isCurrent(id, token)) return;
            final Player online = Bukkit.getPlayer(id);
            if (online == null) return;
            if (failure != null || result == null
                    || result.status() != ClassProfileLifecycleService.Status.READY) {
                final String reason = failure != null ? message(failure)
                        : result == null ? "missing profile result" : result.diagnostic();
                plugin.getLogger().warning("Profile v2 blocked for " + id + ": " + reason);
                gateway.blockSession(id, reason);
                return;
            }
            respecService.recoverPending(online).whenComplete((ignored, recoveryFailure) -> {
                if (!sessions.isCurrent(id, token)) return;
                if (recoveryFailure != null) {
                    gateway.blockSession(id, "Respec recovery blocked activation: "
                            + message(recoveryFailure));
                    return;
                }
                scheduleActivation(online, id, token);
            });
        });
    }

    private void scheduleActivation(final Player online, final UUID id, final UUID token) {
        online.getScheduler().run(plugin, task -> {
            if (!sessions.isCurrent(id, token)) return;
            specializationManager.reconcileDarkGates(online).whenComplete((gateResult, gateFailure) -> {
                if (!sessions.isCurrent(id, token)) return;
                if (gateFailure != null || gateResult == null) {
                    gateway.blockSession(id, gateFailure == null ? "missing DARK gate result"
                            : "DARK gate reconciliation failed: " + message(gateFailure));
                    return;
                }
                if (gateResult.status() != ProfileMutationResult.Status.NO_CHANGE
                        && !gateResult.committed()) {
                    gateway.blockSession(id, "DARK gate reconciliation blocked login: "
                            + gateResult.detail());
                    return;
                }
                final var durable = gateway.currentProfile(id).orElse(null);
                if (durable == null) {
                    gateway.blockSession(id, "Profile disappeared before runtime activation");
                    return;
                }
                if (gateResult.committed()) {
                    gateway.completeSessionActivation(id, token);
                    return;
                }
                runtime.profileCommitted(id, token, durable, durable,
                        ClassSpecRuntimePort.MutationKind.GATE_RECONCILE)
                        .whenComplete((runtimeIgnored, runtimeFailure) -> {
                            if (!sessions.isCurrent(id, token)) return;
                            if (runtimeFailure != null) {
                                gateway.blockSession(id, "Profile login runtime rebuild failed: "
                                        + message(runtimeFailure));
                            } else {
                                gateway.completeSessionActivation(id, token);
                            }
                        });
            });
        }, () -> {
            if (sessions.isCurrent(id, token)) {
                gateway.blockSession(id, "Player scheduler rejected Profile v2 activation");
            }
        });
    }
    private static String message(Throwable x){Throwable c=x;while(c instanceof java.util.concurrent.CompletionException&&c.getCause()!=null)c=c.getCause();return c.getMessage()==null?c.getClass().getSimpleName():c.getMessage();}
}
