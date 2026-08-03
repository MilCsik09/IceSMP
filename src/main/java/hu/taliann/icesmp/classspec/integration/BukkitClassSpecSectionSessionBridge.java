package hu.taliann.icesmp.classspec.integration;

import hu.taliann.icesmp.classspec.application.*;
import hu.taliann.icesmp.managers.RespecService;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Folia owner-scheduler bridge with PlayerProfile-first admission and generation fencing. */
public final class BukkitClassSpecSectionSessionBridge {
    private final JavaPlugin plugin;
    private final PlayerProfileService playerProfiles;
    private final ClassSpecSectionLifecycleService lifecycle;
    private final ClassSpecProfileGateway gateway;
    private final ProfileSessionRegistry sessions;
    private final SpecializationManager specializationManager;
    private final BukkitClassSpecRuntimeAdapter runtime;
    private final RespecService respecService;
    private final AtomicBoolean accepting=new AtomicBoolean(true);
    private final Object lock=new Object();
    private final ConcurrentHashMap<UUID,CompletableFuture<Void>> pendingLogouts=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID,Long> playerProfileSessions=new ConcurrentHashMap<>();

    public BukkitClassSpecSectionSessionBridge(final JavaPlugin plugin,
                                               final PlayerProfileService playerProfiles,
                                               final ClassSpecSectionLifecycleService lifecycle,
                                               final ClassSpecProfileGateway gateway,
                                               final ProfileSessionRegistry sessions,
                                               final SpecializationManager specializationManager,
                                               final BukkitClassSpecRuntimeAdapter runtime,
                                               final RespecService respecService){
        this.plugin=Objects.requireNonNull(plugin);this.playerProfiles=Objects.requireNonNull(playerProfiles);
        this.lifecycle=Objects.requireNonNull(lifecycle);this.gateway=Objects.requireNonNull(gateway);
        this.sessions=Objects.requireNonNull(sessions);this.specializationManager=Objects.requireNonNull(specializationManager);
        this.runtime=Objects.requireNonNull(runtime);this.respecService=Objects.requireNonNull(respecService);
    }

    public void join(final Player player){
        if(!accepting.get())return;
        final UUID id=player.getUniqueId();final UUID token;final CompletableFuture<Void> prior;
        synchronized(lock){if(!accepting.get())return;token=sessions.begin(id);gateway.beginSessionActivation(id,token);prior=pendingLogouts.get(id);}
        final CompletionStage<Void> admission=prior==null?CompletableFuture.completedFuture(null):prior.handle((v,x)->null);
        admission.thenCompose(ignored->playerProfiles.join(id,player.getName())).whenComplete((profileSession,failure)->{
            if(!sessions.isCurrent(id,token))return;
            if(failure!=null||profileSession==null){gateway.blockSession(id,"PlayerProfile load failed: "+message(failure));return;}
            playerProfileSessions.put(id,profileSession.sessionGeneration());startJoin(id,token);
        });
    }

    public CompletionStage<Void> quit(final UUID id){
        Objects.requireNonNull(id);if(!accepting.get())return CompletableFuture.completedFuture(null);
        final UUID token=sessions.currentToken(id).orElse(null);if(token==null)return CompletableFuture.completedFuture(null);
        sessions.markClosing(id,token);final CompletableFuture<Void> completion=new CompletableFuture<>();
        final CompletableFuture<Void> previous=pendingLogouts.put(id,completion);
        final CompletionStage<Void> start=previous==null?CompletableFuture.completedFuture(null):previous.handle((v,x)->null);
        final long profileGeneration=playerProfileSessions.getOrDefault(id,-1L);
        respecService.closeSession(id);
        start.thenCompose(v->gateway.awaitPlayerMutations(id))
                .thenCompose(v->respecService.awaitPlayerTransactions(id))
                .thenCompose(v->lifecycle.logout(id))
                .thenCompose(v->profileGeneration<0L?CompletableFuture.completedFuture(null):playerProfiles.quit(id,profileGeneration))
                .whenComplete((v,x)->{
                    gateway.clearSession(id);respecService.clearSession(id);sessions.close(id,token);
                    playerProfileSessions.remove(id,profileGeneration);pendingLogouts.remove(id,completion);
                    if(x==null)completion.complete(null);else{plugin.getLogger().severe("PlayerProfile logout failed for "+id+": "+message(x));completion.completeExceptionally(x);}
                });
        return completion;
    }

    public CompletionStage<Void> prepareDisable(){
        final CompletableFuture<?>[] logouts;synchronized(lock){accepting.set(false);logouts=pendingLogouts.values().toArray(CompletableFuture[]::new);}
        return gateway.prepareShutdown().thenCompose(v->CompletableFuture.allOf(logouts)).thenCompose(v->lifecycle.prepareDisable());
    }
    public void stopRuntime(){runtime.stop();}

    private void startJoin(final UUID id, final UUID token) {
        if (!sessions.isCurrent(id, token)) return;
        respecService.openSession(id);
        lifecycle.join(id).whenComplete((result, failure) -> {
            if (!sessions.isCurrent(id, token)) return;
            final Player online = Bukkit.getPlayer(id);
            if (online == null) return;
            if (failure != null || result == null || result.status() != ClassSpecSectionLifecycleService.Status.READY) {
                final String reason = failure != null ? message(failure) : result == null ? "missing class-spec result" : result.diagnostic();
                plugin.getLogger().warning("PlayerProfile class-spec blocked for " + id + ": " + reason);gateway.blockSession(id, reason);return;
            }
            respecService.recoverPending(online).whenComplete((ignored, recoveryFailure) -> {
                if (!sessions.isCurrent(id, token)) return;
                if (recoveryFailure != null) {gateway.blockSession(id, "Respec recovery blocked activation: " + message(recoveryFailure));return;}
                scheduleActivation(online, id, token);
            });
        });
    }

    private void scheduleActivation(final Player online, final UUID id, final UUID token) {
        online.getScheduler().run(plugin, task -> {
            if (!sessions.isCurrent(id, token)) return;
            specializationManager.reconcileDarkGates(online).whenComplete((gateResult, gateFailure) -> {
                if (!sessions.isCurrent(id, token)) return;
                if (gateFailure != null || gateResult == null) {gateway.blockSession(id, gateFailure == null ? "missing DARK gate result" : "DARK gate reconciliation failed: " + message(gateFailure));return;}
                if (gateResult.status() != ProfileMutationResult.Status.NO_CHANGE && !gateResult.committed()) {gateway.blockSession(id, "DARK gate reconciliation blocked login: " + gateResult.detail());return;}
                final var durable = gateway.currentProfile(id).orElse(null);
                if (durable == null) {gateway.blockSession(id, "Class-spec section disappeared before runtime activation");return;}
                if (gateResult.committed()) {gateway.completeSessionActivation(id, token);return;}
                runtime.profileCommitted(id, token, durable, durable, ClassSpecRuntimePort.MutationKind.GATE_RECONCILE)
                        .whenComplete((runtimeIgnored, runtimeFailure) -> {
                            if (!sessions.isCurrent(id, token)) return;
                            if (runtimeFailure != null) gateway.blockSession(id, "PlayerProfile runtime rebuild failed: " + message(runtimeFailure));
                            else gateway.completeSessionActivation(id, token);
                        });
            });
        }, () -> {if (sessions.isCurrent(id, token)) gateway.blockSession(id, "Player scheduler rejected PlayerProfile activation");});
    }

    private static String message(final Throwable failure){
        if(failure==null)return "unknown failure";Throwable current=failure;
        while((current instanceof java.util.concurrent.CompletionException||current instanceof java.util.concurrent.ExecutionException)&&current.getCause()!=null)current=current.getCause();
        return current.getMessage()==null?current.getClass().getSimpleName():current.getMessage();
    }
}
