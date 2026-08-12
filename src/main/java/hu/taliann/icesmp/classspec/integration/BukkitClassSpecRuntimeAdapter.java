package hu.taliann.icesmp.classspec.integration;

import hu.taliann.icesmp.archer.ArcherGameplayService;
import hu.taliann.icesmp.classspec.application.ClassSpecRuntimePort;
import hu.taliann.icesmp.classspec.application.ProfileSessionRegistry;
import hu.taliann.icesmp.demonhunter.DemonHunterGameplayService;
import hu.taliann.icesmp.assassin.AssassinGameplayService;
import hu.taliann.icesmp.managers.SoulforgeManager;
import hu.taliann.icesmp.warlock.WarlockGameplayService;
import hu.taliann.icesmp.wizard.WizardGameplayService;
import hu.taliann.icesmp.deathknight.DeathKnightGameplayService;
import hu.taliann.icesmp.druid.DruidGameplayService;
import hu.taliann.icesmp.priest.PriestGameplayService;
import hu.taliann.icesmp.evoker.EvokerGameplayService;
import hu.taliann.icesmp.monk.MonkGameplayService;
import hu.taliann.icesmp.paladin.PaladinGameplayService;
import hu.taliann.icesmp.shaman.ShamanGameplayService;
import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.managers.AdvancementService;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.BloodMoonManager;
import hu.taliann.icesmp.managers.MinionManager;
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
import java.util.EnumMap;
import hu.taliann.icesmp.data.JobType;

/** Scheduler-owning spell/companion/transient reconciliation after durable commits. */
public final class BukkitClassSpecRuntimeAdapter implements ClassSpecRuntimePort {
    private final JavaPlugin plugin;
    private final JobManager jobs;
    private final SpecializationManager specs;
    private final AbilityCatalystListener catalyst;
    private final SpellRegistry spells;
    private final ResourceManager resources;
    private final PetManager pets;
    private final BloodMoonManager bloodMoon;
    private final MinionManager minions;
    private final SoulforgeManager soulforge;
    private final List<PlayerStateCleanup> transientOwners = new CopyOnWriteArrayList<>();
    private final ProfileSessionRegistry sessions;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean runtimesWired = new AtomicBoolean(false);
    private volatile Consumer<UUID> loadoutSwitchCleanup = ignored -> { };
    private volatile Consumer<UUID> offlineLoadoutSwitchCleanup = ignored -> { };
    private volatile Consumer<Player> postReconcile = ignored -> { };

    public BukkitClassSpecRuntimeAdapter(final JavaPlugin plugin,
                                         final JobManager jobs,
                                         final SpecializationManager specs,
                                         final AbilityCatalystListener catalyst,
                                         final PetManager pets,
                                         final BloodMoonManager bloodMoon,
                                         final MinionManager minions,
                                         final SoulforgeManager soulforge,
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
        this.bloodMoon = Objects.requireNonNull(bloodMoon);
        this.minions = Objects.requireNonNull(minions);
        this.soulforge = Objects.requireNonNull(soulforge);
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
        final ShamanGameplayService shaman = specs.shamanGameplayService().orElse(null);
        final MonkGameplayService monk = specs.monkGameplayService().orElse(null);
        final PaladinGameplayService paladin = specs.paladinGameplayService().orElse(null);
        final DemonHunterGameplayService demonHunter = specs.demonHunterGameplayService().orElse(null);
        final DruidGameplayService druid = specs.druidGameplayService().orElse(null);
        final PriestGameplayService priest = specs.priestGameplayService().orElse(null);
        final DeathKnightGameplayService deathKnight = specs.deathKnightGameplayService().orElse(null);
        final AssassinGameplayService assassin = specs.assassinGameplayService().orElse(null);
        final WarlockGameplayService warlock = specs.warlockGameplayService().orElse(null);
        final WizardGameplayService wizard = specs.wizardGameplayService().orElse(null);
        if (warrior == null || evoker == null || archer == null || shaman == null
                || monk == null || paladin == null || demonHunter == null || druid == null || priest == null || deathKnight == null || assassin == null || warlock == null || wizard == null
                || !runtimesWired.compareAndSet(false, true)) return;
        catalyst.setWarriorGameplayService(warrior);
        catalyst.setEvokerGameplayService(evoker);
        catalyst.setArcherGameplayService(archer);
        catalyst.setShamanGameplayService(shaman);
        catalyst.setMonkGameplayService(monk);
        catalyst.setPaladinGameplayService(paladin);
        catalyst.setDemonHunterGameplayService(demonHunter);
        catalyst.setDruidGameplayService(druid);
        catalyst.setPriestGameplayService(priest);
        catalyst.setDeathKnightGameplayService(deathKnight);
        catalyst.setAssassinGameplayService(assassin);
        catalyst.setWarlockGameplayService(warlock);
        catalyst.setWizardGameplayService(wizard);
        resources.setHudSuffix(player -> warrior.hudSuffix(player)
                .append(evoker.hudSuffix(player))
                .append(archer.hudSuffix(player))
                .append(shaman.hudSuffix(player))
                .append(monk.hudSuffix(player))
                .append(paladin.hudSuffix(player))
                .append(demonHunter.hudSuffix(player))
                .append(druid.hudSuffix(player))
                .append(priest.hudSuffix(player))
                .append(deathKnight.hudSuffix(player))
                .append(assassin.hudSuffix(player))
                .append(warlock.hudSuffix(player))
                .append(wizard.hudSuffix(player)));
        final EnumMap<JobType, ClassHudStateAdapter> hudAdapters = new EnumMap<>(JobType.class);
        hudAdapters.put(JobType.WARRIOR, new ClassHudStateAdapter(JobType.WARRIOR, specs::getClassSpecialization, warrior::hudState));
        hudAdapters.put(JobType.EVOKER, new ClassHudStateAdapter(JobType.EVOKER, specs::getClassSpecialization, evoker::hudState));
        hudAdapters.put(JobType.ARCHER, new ClassHudStateAdapter(JobType.ARCHER, specs::getClassSpecialization, archer::hudState));
        hudAdapters.put(JobType.SHAMAN, new ClassHudStateAdapter(JobType.SHAMAN, specs::getClassSpecialization, shaman::hudState));
        hudAdapters.put(JobType.MONK, new ClassHudStateAdapter(JobType.MONK, specs::getClassSpecialization, monk::hudState));
        hudAdapters.put(JobType.PALADIN, new ClassHudStateAdapter(JobType.PALADIN, specs::getClassSpecialization, paladin::hudState));
        hudAdapters.put(JobType.DEMON_HUNTER, new ClassHudStateAdapter(JobType.DEMON_HUNTER, specs::getClassSpecialization, demonHunter::hudState));
        hudAdapters.put(JobType.DRUID, new ClassHudStateAdapter(JobType.DRUID, specs::getClassSpecialization, druid::hudState));
        hudAdapters.put(JobType.PRIEST, new ClassHudStateAdapter(JobType.PRIEST, specs::getClassSpecialization, priest::hudState));
        hudAdapters.put(JobType.DEATH_KNIGHT, new ClassHudStateAdapter(JobType.DEATH_KNIGHT, specs::getClassSpecialization, deathKnight::hudState));
        hudAdapters.put(JobType.ASSASSIN, new ClassHudStateAdapter(JobType.ASSASSIN, specs::getClassSpecialization, assassin::hudState));
        hudAdapters.put(JobType.WARLOCK, new ClassHudStateAdapter(JobType.WARLOCK, specs::getClassSpecialization, warlock::hudState));
        hudAdapters.put(JobType.WIZARD, new ClassHudStateAdapter(JobType.WIZARD, specs::getClassSpecialization, wizard::hudState));
        resources.setClassHudState(player -> {
            final ClassHudStateAdapter adapter = hudAdapters.get(jobs.getPrimaryJob(player));
            return adapter == null ? ClassHudState.empty() : adapter.snapshot(player);
        });
        specs.setSwitchSafetyResource(resources);
        warrior.setCombatTracker(resources);
        evoker.setCombatTracker(resources);
        archer.setCombatTracker(resources);
        shaman.setCombatTracker(resources);
        monk.setCombatTracker(resources);
        paladin.setCombatTracker(resources);
        demonHunter.setCombatTracker(resources);
        druid.setCombatTracker(resources);
        priest.setCombatTracker(resources);
        deathKnight.setCombatTracker(resources);
        assassin.setCombatTracker(resources);
        warlock.setCombatTracker(resources);
        warlock.setPetManager(pets);
        wizard.setCombatTracker(resources);
        wizard.setSoulforgeManager(soulforge);
        wizard.setPetManager(pets);
        assassin.setBloodMoonManager(bloodMoon);
        assassin.setMinionManager(minions);
        archer.setPetManager(pets);
        pets.setPetDeathHook(archer::onPetDeath);
        registerTransientOwner(warrior);
        registerTransientOwner(evoker);
        registerTransientOwner(archer);
        registerTransientOwner(shaman);
        registerTransientOwner(monk);
        registerTransientOwner(paladin);
        registerTransientOwner(demonHunter);
        registerTransientOwner(druid);
        registerTransientOwner(priest);
        registerTransientOwner(deathKnight);
        registerTransientOwner(assassin);
        registerTransientOwner(warlock);
        registerTransientOwner(wizard);
        setLoadoutSwitchCleanup(playerId -> {
            warrior.clearSpecializationState(playerId);
            evoker.clearSpecializationState(playerId);
            archer.clearSpecializationState(playerId);
            shaman.clearSpecializationState(playerId);
            monk.clearSpecializationState(playerId);
            paladin.clearSpecializationState(playerId);
            demonHunter.clearSpecializationState(playerId);
            druid.clearSpecializationState(playerId);
            priest.clearSpecializationState(playerId);
            deathKnight.clearSpecializationState(playerId);
            assassin.clearSpecializationState(playerId);
            warlock.clearSpecializationState(playerId);
            wizard.clearSpecializationState(playerId);
        });
        offlineLoadoutSwitchCleanup = playerId -> {
            warrior.clearSpecializationState(playerId);
            evoker.clearSpecializationState(playerId);
            archer.clearSpecializationState(playerId);
            shaman.clearSpecializationState(playerId);
            monk.clearSpecializationStateOffline(playerId);
            paladin.clearSpecializationState(playerId);
            demonHunter.clearSpecializationState(playerId);
            druid.clearSpecializationState(playerId);
            priest.clearSpecializationState(playerId);
            deathKnight.clearSpecializationState(playerId);
            assassin.clearSpecializationState(playerId);
            warlock.clearSpecializationState(playerId);
            wizard.clearSpecializationState(playerId);
        };
        setPostReconcile(player -> {
            warrior.reconcileProfile(player);
            evoker.reconcileProfile(player);
            archer.reconcileProfile(player);
            shaman.reconcileProfile(player);
            monk.reconcileProfile(player);
            paladin.reconcileProfile(player);
            demonHunter.reconcileProfile(player);
            druid.reconcileProfile(player);
            priest.reconcileProfile(player);
            deathKnight.reconcileProfile(player);
            assassin.reconcileProfile(player);
            warlock.reconcileProfile(player);
            wizard.reconcileProfile(player);
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
                final boolean cleared = sessions.runIfCurrent(id, token, () -> {
                    if (Bukkit.getPlayer(id) != null) {
                        throw new ProfileSessionRegistry.StaleSessionException(id, token);
                    }
                    clearUuidOnly(id, kind, false);
                });
                if (!cleared) {
                    throw new ProfileSessionRegistry.StaleSessionException(id, token);
                }
                if (!regrant) {
                    return spellbookStateStore.selectWhile(id, "", () -> current(id, token))
                            .thenCompose(ignored -> current(id, token)
                                    ? CompletableFuture.completedFuture(null)
                                    : CompletableFuture.failedFuture(
                                            new ProfileSessionRegistry.StaleSessionException(id, token)));
                }
                return CompletableFuture.completedFuture(null);
            } catch (final Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
        // BASE and SPEC are both pure derivatives of the durable ClassSpecSection. Rebuild both
        // on every full reconciliation so a durable commit followed by a stale callback cannot
        // leave missing new BASE grants or stale BASE/SPEC grants after reconnect. Other
        // provenance (TALENT/QUEST/ADMIN) is deliberately untouched.
        final Predicate<String> revoke = source -> source.startsWith(JobManager.SOURCE_BASE_PREFIX)
                || source.startsWith(JobManager.SOURCE_SPEC_PREFIX);
        return jobs.revokeGrantsFromV2(player, revoke)
                .thenCompose(ignored -> runOnOwner(id, token, player,
                        () -> clearUuidOnly(id, kind, true)))
                .thenCompose(ignored -> !regrant
                        ? spellbookStateStore.selectWhile(id, "", () -> current(id, token))
                                .thenApply(selected -> null)
                        : CompletableFuture.completedFuture(null))
                .thenCompose(ignored -> regrant
                        ? jobs.applyAutoUnlocksV2(player, durable)
                        : CompletableFuture.completedFuture(null))
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

    private void clearUuidOnly(final UUID id, final MutationKind kind,
                               final boolean playerOwnerThread) {
        final boolean switching = kind == MutationKind.LOADOUT_SWITCH;
        for (final PlayerStateCleanup owner : transientOwners) {
            if (switching && (owner == resources || owner == catalyst
                    || owner instanceof WarriorGameplayService
                    || owner instanceof EvokerGameplayService
                    || owner instanceof ArcherGameplayService
                    || owner instanceof ShamanGameplayService
                    || owner instanceof MonkGameplayService
                    || owner instanceof PaladinGameplayService
                    || owner instanceof DemonHunterGameplayService
                    || owner instanceof DruidGameplayService
                    || owner instanceof PriestGameplayService
                    || owner instanceof DeathKnightGameplayService
                    || owner instanceof AssassinGameplayService
                    || owner instanceof WarlockGameplayService
                    || owner instanceof WizardGameplayService)) {
                continue;
            }
            owner.clearPlayerState(id);
        }
        if (switching) {
            (playerOwnerThread ? loadoutSwitchCleanup : offlineLoadoutSwitchCleanup).accept(id);
        }
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
