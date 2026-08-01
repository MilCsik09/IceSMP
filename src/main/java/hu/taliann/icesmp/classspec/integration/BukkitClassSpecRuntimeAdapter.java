package hu.taliann.icesmp.classspec.integration;

import hu.taliann.icesmp.classspec.application.ClassSpecRuntimePort;
import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.PetManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellRegistry;
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

/** Scheduler-owning spell/companion/transient cleanup after a durable Profile v2 commit. */
public final class BukkitClassSpecRuntimeAdapter implements ClassSpecRuntimePort {

    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final SpecializationManager specializationManager;
    private final SpellRegistry spellRegistry;
    private final List<PlayerStateCleanup> transientOwners;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public BukkitClassSpecRuntimeAdapter(final JavaPlugin plugin, final JobManager jobManager,
                                         final SpecializationManager specializationManager,
                                         final AbilityCatalystListener abilityCatalystListener,
                                         final PetManager petManager,
                                         final hu.taliann.icesmp.managers.ResourceManager resourceManager,
                                         final SpellRegistry spellRegistry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.jobManager = Objects.requireNonNull(jobManager, "jobManager");
        this.specializationManager = Objects.requireNonNull(specializationManager, "specializationManager");
        this.spellRegistry = Objects.requireNonNull(spellRegistry, "spellRegistry");
        this.transientOwners = List.of(
                Objects.requireNonNull(abilityCatalystListener, "abilityCatalystListener"),
                Objects.requireNonNull(petManager, "petManager"),
                Objects.requireNonNull(resourceManager, "resourceManager"));
    }

    @Override
    public CompletionStage<Void> profileCommitted(final UUID playerId, final ClassProfile previous,
                                                   final ClassProfile durable,
                                                   final MutationKind kind) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(durable, "durable");
        if (!ClassSpecRuntimePort.requiresRuntimeReconciliation(kind)) {
            return CompletableFuture.completedFuture(null);
        }
        return scheduleCleanup(playerId, durable.isGameplayUsable(), kind);
    }

    @Override
    public CompletionStage<Void> failClosed(final UUID playerId, final String reason) {
        return scheduleCleanup(playerId, false, null);
    }

    private CompletionStage<Void> scheduleCleanup(final UUID playerId, final boolean regrantActive,
                                                   final MutationKind kind) {
        Objects.requireNonNull(playerId, "playerId");
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        if (!accepting.get()) {
            clearUuidOnly(playerId);
            completion.completeExceptionally(new IllegalStateException("Profile runtime adapter stopped"));
            return completion;
        }
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            clearUuidOnly(playerId);
            completion.complete(null);
            return completion;
        }
        player.getScheduler().run(plugin, task -> {
            if (!accepting.get()) {
                clearUuidOnly(playerId);
                completion.completeExceptionally(new IllegalStateException("Profile runtime adapter stopped"));
                return;
            }
            try {
                jobManager.backfillSpellGrants(player);
                jobManager.revokeGrantsFrom(player,
                        source -> source.startsWith(JobManager.SOURCE_SPEC_PREFIX));
                clearUuidOnly(playerId);
                specializationManager.mirrorActiveClassSpecializationV2(player, durable);
                if (regrantActive) {
                    specializationManager.applyClassSpecializationUnlocksV2(player, durable);
                }
                if (kind == MutationKind.SELECT) {
                    hu.taliann.icesmp.managers.AdvancementService.award(player, "first_spec");
                }
                completion.complete(null);
            } catch (final Throwable failure) {
                completion.completeExceptionally(failure);
            }
        }, () -> {
            clearUuidOnly(playerId);
            completion.completeExceptionally(new IllegalStateException(
                    "Player scheduler rejected Profile v2 cleanup"));
        });
        return completion;
    }

    private void clearUuidOnly(final UUID playerId) {
        for (final PlayerStateCleanup owner : transientOwners) {
            owner.clearPlayerState(playerId);
        }
        for (final Spell spell : spellRegistry.getAll()) {
            spell.clearPlayerState(playerId);
        }
    }

    /** Prevents callbacks from scheduling work after plugin-disable has begun. */
    public void stop() {
        accepting.set(false);
    }
}
