package hu.taliann.icesmp.classspec.integration;

import hu.taliann.icesmp.classspec.application.ClassSpecRuntimePort;
import hu.taliann.icesmp.classspec.application.ProfileSessionRegistry;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.managers.AdvancementService;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.PetManager;
import hu.taliann.icesmp.managers.ResourceManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
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
    private final JavaPlugin plugin;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final SpellRegistry spells;
    private final List<PlayerStateCleanup> transientOwners;
    private final ProfileSessionRegistry sessions;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public BukkitClassSpecRuntimeAdapter(final JavaPlugin plugin,
                                         final JobManager jobs,
                                         final SpecializationManager specs,
                                         final AbilityCatalystListener catalyst,
                                         final PetManager pets,
                                         final ResourceManager resources,
                                         final SpellRegistry spells,
                                         final ProfileSessionRegistry sessions) {
        this.plugin = Objects.requireNonNull(plugin);
        this.jobs = Objects.requireNonNull(jobs);
        this.specs = Objects.requireNonNull(specs);
        this.spells = Objects.requireNonNull(spells);
        this.sessions = Objects.requireNonNull(sessions);
        this.transientOwners = List.of(Objects.requireNonNull(catalyst),
                Objects.requireNonNull(pets), Objects.requireNonNull(resources));
    }

    @Override
    public CompletionStage<Void> profileCommitted(final UUID id, final UUID token,
                                                  final ClassSpecSection previous,
                                                  final ClassSpecSection durable,
                                                  final MutationKind kind) {
        Objects.requireNonNull(previous);
        Objects.requireNonNull(durable);
        if (!ClassSpecRuntimePort.requiresRuntimeReconciliation(kind)) {
            return current(id, token) ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.failedFuture(
                            new ProfileSessionRegistry.StaleSessionException(id, token));
        }
        return reconcile(id, token, durable, durable.isGameplayUsable(), kind);
    }

    @Override
    public CompletionStage<Void> failClosed(final UUID id, final UUID token,
                                            final String reason) {
        return reconcile(id, token, null, false, null);
    }

    private CompletionStage<Void> reconcile(final UUID id, final UUID token,
                                            final ClassSpecSection durable,
                                            final boolean regrant,
                                            final MutationKind kind) {
        if (!accepting.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Profile runtime adapter stopped"));
        }
        if (!current(id, token)) {
            return CompletableFuture.failedFuture(
                    new ProfileSessionRegistry.StaleSessionException(id, token));
        }
        final Player player = Bukkit.getPlayer(id);
        if (player == null) {
            try {
                if (!current(id, token)) {
                    throw new ProfileSessionRegistry.StaleSessionException(id, token);
                }
                clearUuidOnly(id);
                return CompletableFuture.completedFuture(null);
            } catch (final Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
        final Predicate<String> revoke = kind == MutationKind.ADMIN_RESET
                ? source -> source.startsWith(JobManager.SOURCE_BASE_PREFIX)
                        || source.startsWith(JobManager.SOURCE_SPEC_PREFIX)
                : source -> source.startsWith(JobManager.SOURCE_SPEC_PREFIX);
        return jobs.revokeGrantsFromV2(player, revoke)
                .thenCompose(ignored -> runOnOwner(id, token, player, () -> clearUuidOnly(id)))
                .thenCompose(ignored -> regrant
                        ? specs.applyClassSpecializationUnlocksV2(player, durable)
                        : CompletableFuture.completedFuture(null))
                .thenCompose(ignored -> kind == MutationKind.SELECT
                        ? runOnOwner(id, token, player,
                                () -> AdvancementService.award(player, "first_spec"))
                        : CompletableFuture.completedFuture(null));
    }

    private CompletionStage<Void> runOnOwner(final UUID id, final UUID token,
                                             final Player player,
                                             final Runnable action) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            if (!accepting.get()) {
                result.completeExceptionally(
                        new IllegalStateException("Profile runtime adapter stopped"));
                return;
            }
            if (!current(id, token)) {
                result.completeExceptionally(
                        new ProfileSessionRegistry.StaleSessionException(id, token));
                return;
            }
            try {
                action.run();
                result.complete(null);
            } catch (final Throwable failure) {
                result.completeExceptionally(failure);
            }
        }, () -> result.completeExceptionally(
                new IllegalStateException("Player scheduler rejected Profile v2 reconciliation")));
        return result;
    }

    private boolean current(final UUID id, final UUID token) {
        return accepting.get() && sessions.isCurrent(id, token);
    }

    private void clearUuidOnly(final UUID id) {
        for (final PlayerStateCleanup owner : transientOwners) owner.clearPlayerState(id);
        for (final Spell spell : spells.getAll()) spell.clearPlayerState(id);
    }

    public void stop() {
        accepting.set(false);
    }
}
