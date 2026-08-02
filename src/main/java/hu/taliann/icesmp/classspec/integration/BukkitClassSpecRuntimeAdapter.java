package hu.taliann.icesmp.classspec.integration;

import hu.taliann.icesmp.classspec.application.ClassSpecRuntimePort;
import hu.taliann.icesmp.classspec.application.ProfileSessionRegistry;
import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.managers.*;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.spells.Spell;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/** Scheduler-owning spell/companion/transient reconciliation after durable commits. */
public final class BukkitClassSpecRuntimeAdapter implements ClassSpecRuntimePort {
    private final JavaPlugin plugin;private final JobManager jobs;private final SpecializationManager specs;private final SpellRegistry spells;private final List<PlayerStateCleanup> transientOwners;private final ProfileSessionRegistry sessions;private final AtomicBoolean accepting=new AtomicBoolean(true);
    public BukkitClassSpecRuntimeAdapter(JavaPlugin plugin,JobManager jobs,SpecializationManager specs,AbilityCatalystListener catalyst,PetManager pets,ResourceManager resources,SpellRegistry spells,ProfileSessionRegistry sessions){this.plugin=Objects.requireNonNull(plugin);this.jobs=Objects.requireNonNull(jobs);this.specs=Objects.requireNonNull(specs);this.spells=Objects.requireNonNull(spells);this.sessions=Objects.requireNonNull(sessions);transientOwners=List.of(Objects.requireNonNull(catalyst),Objects.requireNonNull(pets),Objects.requireNonNull(resources));}
    @Override public CompletionStage<Void> profileCommitted(UUID id,UUID token,ClassProfile previous,ClassProfile durable,MutationKind kind){Objects.requireNonNull(previous);Objects.requireNonNull(durable);if(!ClassSpecRuntimePort.requiresRuntimeReconciliation(kind))return current(id,token)?CompletableFuture.completedFuture(null):CompletableFuture.failedFuture(new ProfileSessionRegistry.StaleSessionException(id,token));return schedule(id,token,durable,durable.isGameplayUsable(),kind);}
    @Override public CompletionStage<Void> failClosed(UUID id,UUID token,String reason){return schedule(id,token,null,false,null);}
    private CompletionStage<Void> schedule(UUID id,UUID token,ClassProfile durable,boolean regrant,MutationKind kind){CompletableFuture<Void> done=new CompletableFuture<>();if(!accepting.get()){done.completeExceptionally(new IllegalStateException("Profile runtime adapter stopped"));return done;}if(!current(id,token)){done.completeExceptionally(new ProfileSessionRegistry.StaleSessionException(id,token));return done;}Player player=Bukkit.getPlayer(id);if(player==null){try{if(current(id,token))clearUuidOnly(id);else throw new ProfileSessionRegistry.StaleSessionException(id,token);done.complete(null);}catch(Throwable x){done.completeExceptionally(x);}return done;}player.getScheduler().run(plugin,task->{if(!accepting.get()){done.completeExceptionally(new IllegalStateException("Profile runtime adapter stopped"));return;}if(!current(id,token)){done.completeExceptionally(new ProfileSessionRegistry.StaleSessionException(id,token));return;}try{Predicate<String> revoke=kind==MutationKind.ADMIN_RESET?source->source.startsWith(JobManager.SOURCE_BASE_PREFIX)||source.startsWith(JobManager.SOURCE_SPEC_PREFIX):source->source.startsWith(JobManager.SOURCE_SPEC_PREFIX);jobs.revokeGrantsFrom(player,revoke);clearUuidOnly(id);if(regrant)specs.applyClassSpecializationUnlocksV2(player,durable);if(kind==MutationKind.SELECT)AdvancementService.award(player,"first_spec");done.complete(null);}catch(Throwable x){done.completeExceptionally(x);}},()->done.completeExceptionally(new IllegalStateException("Player scheduler rejected Profile v2 reconciliation")));return done;}
    private boolean current(UUID id,UUID token){return accepting.get()&&sessions.isCurrent(id,token);}
    private void clearUuidOnly(UUID id){for(PlayerStateCleanup owner:transientOwners)owner.clearPlayerState(id);for(Spell spell:spells.getAll())spell.clearPlayerState(id);}
    public void stop(){accepting.set(false);}
}
