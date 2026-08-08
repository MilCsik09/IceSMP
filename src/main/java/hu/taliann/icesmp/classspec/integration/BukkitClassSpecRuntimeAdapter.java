package hu.taliann.icesmp.classspec.integration;

import hu.taliann.icesmp.archer.ArcherGameplayService;
import hu.taliann.icesmp.classspec.application.ClassSpecRuntimePort;
import hu.taliann.icesmp.classspec.application.ProfileSessionRegistry;
import hu.taliann.icesmp.evoker.EvokerGameplayService;
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
import hu.taliann.icesmp.warrior.WarriorGameplayService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Scheduler-owning spell/companion/transient reconciliation after durable commits. */
public final class BukkitClassSpecRuntimeAdapter implements ClassSpecRuntimePort {
    private final JavaPlugin plugin;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final AbilityCatalystListener catalyst;
    private final SpellRegistry spells;
    private final ResourceManager resources;
    private final PetManager pets;
    private final List<PlayerStateCleanup> transientOwners = new CopyOnWriteArrayList<>();
    private final ProfileSessionRegistry sessions;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean runtimesWired = new AtomicBoolean(false);
    private volatile Consumer<UUID> loadoutSwitchCleanup = ignored -> { };
    private volatile Consumer<Player> postReconcile = ignored -> { };

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
        this.catalyst = Objects.requireNonNull(catalyst);
        this.spells = Objects.requireNonNull(spells);
        this.resources = Objects.requireNonNull(resources);
        this.sessions = Objects.requireNonNull(sessions);
        this.pets = Objects.requireNonNull(pets);
        transientOwners.add(catalyst);
        transientOwners.add(pets);
        transientOwners.add(resources);
    }

    /** Additional concrete transient owner; durable authority remains Profile v2. */
    public void registerTransientOwner(final PlayerStateCleanup owner) {
        transientOwners.add(Objects.requireNonNull(owner, "owner"));
    }

    /** Spec-local cleanup that deliberately preserves class-common state during slot switch. */
    public void setLoadoutSwitchCleanup(final Consumer<UUID> cleanup) {
        loadoutSwitchCleanup = cleanup == null ? ignored -> { } : cleanup;
    }

    /** Region-thread callback after grants are reconciled (e.g. physical spellbook refresh). */
    public void setPostReconcile(final Consumer<Player> callback) {
        postReconcile = callback == null ? ignored -> { } : callback;
    }

    private void ensureRuntimeWiring() {
        if (runtimesWired.get()) return;
        final WarriorGameplayService warrior = specs.warriorGameplayService().orElse(null);
        final EvokerGameplayService evoker = specs.evokerGameplayService().orElse(null);
        final ArcherGameplayService archer = specs.archerGameplayService().orElse(null);
        if (warrior == null || evoker == null || archer == null
                || !runtimesWired.compareAndSet(false, true)) return;
        catalyst.setWarriorGameplayService(warrior);
        catalyst.setEvokerGameplayService(evoker);
        catalyst.setArcherGameplayService(archer);
        resources.setHudSuffix(player -> warrior.hudSuffix(player)
                .append(evoker.hudSuffix(player))
                .append(archer.hudSuffix(player)));
        specs.setSwitchSafetyResource(resources);
        warrior.setCombatTracker(resources);
        evoker.setCombatTracker(resources);
        archer.setCombatTracker(resources);
        archer.setPetManager(pets);
        pets.setPetDeathHook(archer::onPetDeath);
        registerTransientOwner(warrior);
        registerTransientOwner(evoker);
        registerTransientOwner(archer);
        setLoadoutSwitchCleanup(playerId -> {
            warrior.clearSpecializationState(playerId);
            evoker.clearSpecializationState(playerId);
            archer.clearSpecializationState(playerId);
        });
        setPostReconcile(player -> {
            warrior.reconcileProfile(player);
            evoker.reconcileProfile(player);
            archer.reconcileProfile(player);
            catalyst.refreshSoulbond(player);
        });
    }

    @Override
    public CompletionStage<Void> profileCommitted(final UUID id, final UUID token,
                                                  final ClassSpecSection previous,
                                                  final ClassSpecSection durable,
                                                  final MutationKind kind) {
        Objects.requireNonNull(previous);
        Objects.requireNonNull(durable);
        ensureRuntimeWiring();
        if (!ClassSpecRuntimePort.requiresRuntimeReconciliation(kind)) {
            return current(id, token) ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.failedFuture(
                            new ProfileSessionRegistry.StaleSessionException(id, token));
        }
        // Selecting the second, inactive slot must not tear down/rebuild the active runtime.
        if (kind == MutationKind.SELECT
                && Objects.equals(previous.activeSlot(), durable.activeSlot())
                && activeSpec(previous).equals(activeSpec(durable))) {
            return current(id, token) ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.failedFuture(
                            new ProfileSessionRegistry.StaleSessionException(id, token));
        }
        return reconcile(id, token, durable, durable.isGameplayUsable(), kind);
    }

    @Override
    public CompletionStage<Void> failClosed(final UUID id, final UUID token,
                                            final String reason) {
        ensureRuntimeWiring();
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
                clearUuidOnly(id, kind);
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
                .thenCompose(ignored -> runOnOwner(id, token, player,
                        () -> clearUuidOnly(id, kind)))
                .thenCompose(ignored -> regrant
                        ? specs.applyClassSpecializationUnlocksV2(player, durable)
                        : CompletableFuture.completedFuture(null))
                .thenCompose(ignored -> runOnOwner(id, token, player,
                        () -> postReconcile.accept(player)))
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

    private void clearUuidOnly(final UUID id, final MutationKind kind) {
        final boolean switching = kind == MutationKind.LOADOUT_SWITCH;
        for (final PlayerStateCleanup owner : transientOwners) {
            if (switching && (owner == resources || owner == catalyst
                    || owner instanceof WarriorGameplayService
                    || owner instanceof EvokerGameplayService
                    || owner instanceof ArcherGameplayService)) {
                continue;
            }
            owner.clearPlayerState(id);
        }
        if (switching) loadoutSwitchCleanup.accept(id);
        for (final Spell spell : spells.getAll()) spell.clearPlayerState(id);
    }

    private static String activeSpec(final ClassSpecSection profile) {
        if (profile.activeSlot() == null) return "";
        return profile.loadout(profile.activeSlot()).specializationId();
    }

    public void stop() {
        accepting.set(false);
    }
}
