#!/usr/bin/env python3
"""Remove temporary synchronous spell bridges and await every durable mutation."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def patch_job_manager() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/managers/JobManager.java"
    text = path.read_text(encoding="utf-8")
    start_marker = '''    /**
     * Temporary source-compatibility bridge. The preview is read from PlayerProfile and the
'''
    end_marker = '''    public Set<String> getGrantSources(final Player player, final String spellId) {
'''
    if start_marker in text:
        start = text.index(start_marker)
        end = text.index(end_marker, start)
        text = text[:start] + text[end:]
    path.write_text(text, encoding="utf-8")


def patch_job_admin() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/commands/job/JobAdminSubcommand.java"
    text = path.read_text(encoding="utf-8")
    if 'import java.util.concurrent.CompletableFuture;' not in text:
        text = text.replace('import java.util.List;\n',
                            'import java.util.List;\nimport java.util.concurrent.CompletableFuture;\nimport java.util.concurrent.CompletionStage;\n', 1)

    old_unlock = '''            if ("unlockallskills".equals(action)) {
                // Az ADMIN forrás sosem esik automatikus visszavonás alá (spec/talent reset).
                for (final Spell spell : spellRegistry.getAll()) {
                    jobManager.unlockSpell(target, spell.getId(), hu.taliann.icesmp.managers.JobManager.SOURCE_ADMIN);
                }
                sender.sendMessage(messageManager.get(
                        "admin.job.unlock-all.success",
                        "&aAz osszes varazslat feloldva: &f%s",
                        target.getName()
                ));
                target.sendMessage(messageManager.get("admin.job.unlock-all.notify", "&eEgy admin feloldotta neked az osszes varazslatot."));
                return;
            }
'''
    new_unlock = '''            if ("unlockallskills".equals(action)) {
                CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
                for (final Spell spell : spellRegistry.getAll()) {
                    chain = chain.thenCompose(ignored -> jobManager.unlockSpellV2(
                                    target, spell.getId(), JobManager.SOURCE_ADMIN)
                            .thenApply(changed -> null));
                }
                chain.whenComplete((ignored, failure) -> target.getScheduler().run(plugin, followup -> {
                    if (failure != null) {
                        sender.sendMessage(messageManager.get("admin.job.unlock-all.persistence-failed",
                                "&cA PlayerProfile spellbook frissítése meghiúsult: &f%s",
                                target.getName()));
                        return;
                    }
                    sender.sendMessage(messageManager.get(
                            "admin.job.unlock-all.success",
                            "&aAz összes varázslat tartósan feloldva: &f%s",
                            target.getName()));
                    target.sendMessage(messageManager.get("admin.job.unlock-all.notify",
                            "&eEgy admin feloldotta neked az összes varázslatot."));
                }, null));
                return;
            }
'''
    if new_unlock not in text:
        if text.count(old_unlock) != 1:
            raise RuntimeError(f"JobAdmin unlock block count={text.count(old_unlock)}")
        text = text.replace(old_unlock, new_unlock, 1)

    old_reset = '''            if ("resetskills".equals(action)) {
                jobManager.clearSpellGrants(target);
                abilityCatalystListener.resetAllSpellState(target);
                sender.sendMessage(messageManager.get(
                        "admin.job.reset-skills.success",
                        "&aMinden varazslat allapot alaphelyzetbe allitva: &f%s",
                        target.getName()
                ));
                target.sendMessage(messageManager.get("admin.job.reset-skills.notify", "&eEgy admin alaphelyzetbe allitotta a varazslataidat."));
                return;
            }
'''
    new_reset = '''            if ("resetskills".equals(action)) {
                jobManager.clearSpellGrantsV2(target)
                        .whenComplete((ignored, failure) -> target.getScheduler().run(plugin, followup -> {
                            if (failure != null) {
                                sender.sendMessage(messageManager.get("admin.job.reset-skills.persistence-failed",
                                        "&cA PlayerProfile spellbook törlése meghiúsult: &f%s",
                                        target.getName()));
                                return;
                            }
                            abilityCatalystListener.resetAllSpellState(target);
                            sender.sendMessage(messageManager.get(
                                    "admin.job.reset-skills.success",
                                    "&aMinden varázslat állapot tartósan alaphelyzetbe állítva: &f%s",
                                    target.getName()));
                            target.sendMessage(messageManager.get("admin.job.reset-skills.notify",
                                    "&eEgy admin alaphelyzetbe állította a varázslataidat."));
                        }, null));
                return;
            }
'''
    if new_reset not in text:
        if text.count(old_reset) != 1:
            raise RuntimeError(f"JobAdmin reset block count={text.count(old_reset)}")
        text = text.replace(old_reset, new_reset, 1)
    path.write_text(text, encoding="utf-8")


def patch_specialization_manager() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/managers/SpecializationManager.java"
    text = path.read_text(encoding="utf-8")
    start = text.index('    public void applyClassSpecializationUnlocks(final Player player) {')
    class_end = text.rfind('\n}')
    replacement = '''    public void applyClassSpecializationUnlocks(final Player player) {
        applyClassSpecializationUnlocks(player, getClassSpecialization(player),
                jobManager.getPrimaryJob(player), jobManager.getPrimaryLevel(player))
                .exceptionally(failure -> {
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(SpecializationManager.class)
                            .getLogger().severe("PlayerProfile spec spell reconcile failed for "
                                    + player.getUniqueId() + ": " + failure.getMessage());
                    return null;
                });
    }

    public CompletionStage<Void> applyClassSpecializationUnlocksV2(
            final Player player, final ClassSpecSection durable) {
        Objects.requireNonNull(durable, "durable");
        final SpecializationType specialization = durable.activeSlot() == null ? null
                : SpecializationType.fromId(durable.loadout(durable.activeSlot()).specializationId());
        return applyClassSpecializationUnlocks(player, specialization,
                JobType.fromId(durable.primaryClassId()), durable.classLevel());
    }

    private CompletionStage<Void> applyClassSpecializationUnlocks(
            final Player player, final SpecializationType specialization,
            final JobType primaryJob, final int classLevel) {
        if (specialization == null || primaryJob != specialization.getParentJob()
                || configManager.getConfiguration() == null) {
            return CompletableFuture.completedFuture(null);
        }
        final ConfigurationSection unlocks = configManager.getConfiguration()
                .getConfigurationSection("specializations." + specialization.getId() + ".spell-unlocks");
        if (unlocks == null) return CompletableFuture.completedFuture(null);

        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final String spellId : unlocks.getKeys(false)) {
            final int required = unlocks.getInt(spellId, Integer.MAX_VALUE);
            if (classLevel < required) continue;
            chain = chain.thenCompose(ignored -> jobManager.unlockSpellV2(player, spellId,
                            JobManager.SOURCE_SPEC_PREFIX + specialization.getId())
                    .thenCompose(unlocked -> Boolean.TRUE.equals(unlocked)
                            ? notifySpecSpellUnlocked(player, spellId, required)
                            : CompletableFuture.completedFuture(null)));
        }
        return chain;
    }

    private CompletionStage<Void> notifySpecSpellUnlocked(final Player player,
                                                           final String spellId,
                                                           final int required) {
        final CompletableFuture<Void> result = new CompletableFuture<>();
        player.getScheduler().run(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(SpecializationManager.class),
                task -> {
                    player.sendMessage(messageManager.getMessage("spec-spell-unlocked",
                            "&5Specializációs képesség feloldva: &e{spell} &7(szint {level})",
                            Map.of("spell", spellId.toLowerCase(Locale.ROOT),
                                    "level", String.valueOf(required))));
                    result.complete(null);
                }, () -> result.completeExceptionally(
                        new IllegalStateException("Player scheduler rejected spec spell notification")));
        return result;
    }
'''
    text = text[:start] + replacement + text[class_end:]
    path.write_text(text, encoding="utf-8")


def patch_runtime_adapter() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/classspec/integration/BukkitClassSpecRuntimeAdapter.java"
    path.write_text('''package hu.taliann.icesmp.classspec.integration;

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
''', encoding="utf-8")


def patch_talent_manager() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/managers/TalentManager.java"
    text = path.read_text(encoding="utf-8")
    start_marker = '''    /**
     * Only THIS talent's claim on the spell is dropped: a spell the class level or a
'''
    end_marker = '''    public void runOnOwnerThread(final Player player, final Runnable action) {
'''
    if start_marker in text:
        start = text.index(start_marker)
        end = text.index(end_marker, start)
        text = text[:start] + text[end:]
    path.write_text(text, encoding="utf-8")


def patch_profession_recipe() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/listeners/ProfessionRecipeBookListener.java"
    old = '''        if (craftXp > 0) {
            professionManager.addXpFor(player, recipe.profession(), craftXp);
        }
'''
    new = '''        if (craftXp > 0) {
            final int durableCraftXp = craftXp;
            professionManager.addXpFor(player, recipe.profession(), durableCraftXp)
                    .whenComplete((change, failure) -> {
                        if (failure == null) return;
                        plugin.getLogger().severe("Craft XP PlayerProfile commit failed for "
                                + player.getUniqueId() + " / " + recipe.id() + ": "
                                + failure.getMessage());
                        professionManager.runOnOwnerThread(player, () -> {
                            if (player.isOnline()) {
                                player.sendMessage(messageManager.get("profession-craft-xp-storage-failed",
                                        "&eA tárgy elkészült, de a szakma-XP mentése meghiúsult; az adminok értesítést kaptak."));
                            }
                        });
                    });
        }
'''
    replace_once(path, old, new, "profession recipe durable XP")


def main() -> int:
    patch_job_manager()
    patch_job_admin()
    patch_specialization_manager()
    patch_runtime_adapter()
    patch_talent_manager()
    patch_profession_recipe()
    print("PlayerProfile async authority bridge cleanup applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
