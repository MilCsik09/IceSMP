#!/usr/bin/env python3
"""Move explicit spell provenance from player PDC to PlayerProfile spellbook authority."""
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


def write_store() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileSpellGrantStore.java"
    path.write_text('''package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.classspec.domain.SpellGrantLedger;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.SpellbookSection;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/** Canonical CAS-backed spell provenance store; PDC is never read as authority. */
public final class PlayerProfileSpellGrantStore {

    public SpellGrantLedger read(final UUID playerId) {
        final SpellbookSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.SPELLBOOK, SpellbookSection.class);
        return SpellGrantLedger.fromProvenance(section.provenance());
    }

    public CompletionStage<SpellGrantLedger.Mutation> add(final UUID playerId,
                                                           final String spellId,
                                                           final String source) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                    final SpellGrantLedger.Mutation mutation = SpellGrantLedger
                            .fromProvenance(current.provenance()).add(spellId, source);
                    if (!mutation.changed()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(mutation);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withLedger(current, mutation.ledger()), mutation);
                });
    }

    public CompletionStage<SpellGrantLedger.Mutation> remove(final UUID playerId,
                                                              final String spellId,
                                                              final String source) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                    final SpellGrantLedger.Mutation mutation = SpellGrantLedger
                            .fromProvenance(current.provenance()).remove(spellId, source);
                    if (!mutation.changed()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(mutation);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withLedger(current, mutation.ledger()), mutation);
                });
    }

    public CompletionStage<SpellGrantLedger.RevokeResult> revokeSources(
            final UUID playerId, final Predicate<String> sourceMatches) {
        Objects.requireNonNull(sourceMatches, "sourceMatches");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                    final SpellGrantLedger.RevokeResult result = SpellGrantLedger
                            .fromProvenance(current.provenance()).revokeSources(sourceMatches);
                    if (!result.changed()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(result);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withLedger(current, result.ledger()), result);
                });
    }

    public CompletionStage<SpellGrantLedger> replace(final UUID playerId,
                                                      final SpellGrantLedger requested) {
        Objects.requireNonNull(requested, "requested");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                    final SpellGrantLedger existing = SpellGrantLedger.fromProvenance(current.provenance());
                    if (existing.provenance().equals(requested.provenance())) {
                        return PlayerProfileService.ConditionalMutation.unchanged(existing);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withLedger(current, requested), requested);
                });
    }

    private static SpellbookSection withLedger(final SpellbookSection current,
                                                final SpellGrantLedger ledger) {
        return new SpellbookSection(ledger.provenance(), current.selectedSpell(),
                current.favorites(), current.mastery(), current.persistentCooldowns(),
                current.uiState(), current.extensions());
    }
}
''', encoding="utf-8")


def patch_ledger() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/classspec/domain/SpellGrantLedger.java"
    replace_once(path,
        '    public static SpellGrantLedger empty() { return new SpellGrantLedger(Map.of()); }\n',
        '    public static SpellGrantLedger empty() { return new SpellGrantLedger(Map.of()); }\n\n'
        '    public static SpellGrantLedger fromProvenance(final Map<String, Set<String>> grants) {\n'
        '        return new SpellGrantLedger(grants == null ? Map.of() : grants);\n'
        '    }\n\n'
        '    public Map<String, Set<String>> provenance() { return grants; }\n',
        "ledger provenance boundary")


def patch_job_manager() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/managers/JobManager.java"
    text = path.read_text(encoding="utf-8")
    text = text.replace('    private final NamespacedKey spellGrantsKey;\n', '')
    text = text.replace('        this.spellGrantsKey = new NamespacedKey(plugin, "spell_grants");\n', '')
    field_anchor = '    private final NamespacedKey legacySecondaryXpKey;\n'
    field = ('    private final hu.taliann.icesmp.playerprofile.application.'
             'PlayerProfileSpellGrantStore spellGrantStore =\n'
             '            new hu.taliann.icesmp.playerprofile.application.PlayerProfileSpellGrantStore();\n')
    if field not in text:
        if text.count(field_anchor) != 1:
            raise RuntimeError("JobManager spell store field anchor mismatch")
        text = text.replace(field_anchor, field_anchor + field, 1)

    old_assign = '''                    return schedulePlayer(player, () -> {
                        mirrorClassState(player);
                        applyAutoUnlocks(player);
                        AdvancementService.award(player, "root");
                        AdvancementService.award(player, "first_class");
                    }).thenApply(ignored -> true);
'''
    new_assign = '''                    return schedulePlayer(player, () -> {
                        mirrorClassState(player);
                        AdvancementService.award(player, "root");
                        AdvancementService.award(player, "first_class");
                    }).thenCompose(ignored -> applyAutoUnlocksV2(player))
                            .thenApply(ignored -> true);
'''
    if new_assign not in text:
        if text.count(old_assign) != 1:
            raise RuntimeError("JobManager class assignment block mismatch")
        text = text.replace(old_assign, new_assign, 1)

    old_xp = '''                    return schedulePlayer(player, () -> {
                        mirrorClassState(player);
                        applyAutoUnlocks(player);
                        if (getPrimaryLevel(player) >= MAX_JOB_LEVEL) AdvancementService.award(player, "class_max");
                        final java.util.function.Consumer<Player> hook = xpChangeHook;
                        if (hook != null) hook.accept(player);
                    }).thenApply(ignored -> true);
'''
    new_xp = '''                    return schedulePlayer(player, () -> {
                        mirrorClassState(player);
                        if (getPrimaryLevel(player) >= MAX_JOB_LEVEL) AdvancementService.award(player, "class_max");
                        final java.util.function.Consumer<Player> hook = xpChangeHook;
                        if (hook != null) hook.accept(player);
                    }).thenCompose(ignored -> applyAutoUnlocksV2(player))
                            .thenApply(ignored -> true);
'''
    if new_xp not in text:
        if text.count(old_xp) != 1:
            raise RuntimeError("JobManager XP block mismatch")
        text = text.replace(old_xp, new_xp, 1)

    start = text.index('    public void applyAutoUnlocks(final Player player) {')
    end = text.index('\n    public List<String> getUnlockedSpellIds', start)
    replacement = '''    public CompletionStage<Void> applyAutoUnlocksV2(final Player player) {
        final JobType job = getPrimaryJob(player);
        if (job == null || configManager.getConfiguration() == null) {
            return CompletableFuture.completedFuture(null);
        }
        final ConfigurationSection unlocks = configManager.getConfiguration()
                .getConfigurationSection("classes." + job.getId() + ".spell-unlocks");
        if (unlocks == null) return CompletableFuture.completedFuture(null);
        final int level = getPrimaryLevel(player);
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final String spellId : unlocks.getKeys(false)) {
            final int required = unlocks.getInt(spellId, Integer.MAX_VALUE);
            if (level < required) continue;
            chain = chain.thenCompose(ignored -> unlockSpellV2(player, spellId,
                            SOURCE_BASE_PREFIX + job.getId())
                    .thenCompose(unlocked -> Boolean.TRUE.equals(unlocked)
                            ? schedulePlayer(player, () -> player.sendMessage(messageManager.getMessage(
                                    "job-spell-auto-unlocked",
                                    "&aÚj képesség feloldva: &e{spell} &7(szint {level})",
                                    Map.of("spell", messageManager.get(
                                                    "spell." + spellId.toLowerCase(Locale.ROOT) + ".name",
                                                    spellId.toLowerCase(Locale.ROOT)),
                                            "level", String.valueOf(required)))))
                            : CompletableFuture.completedFuture(null)));
        }
        return chain;
    }
'''
    text = text[:start] + replacement + text[end:]

    methods_start = text.index('    public List<String> getUnlockedSpellIds')
    methods_end = text.index('\n    /** No backfill exists in greenfield mode;', methods_start)
    backfill_end = text.index('\n    }', methods_end) + len('\n    }')
    methods = '''    public List<String> getUnlockedSpellIds(final Player player) {
        return List.copyOf(readLedger(player).spellIds());
    }

    public boolean hasUnlockedSpell(final Player player, final String spellId) {
        return spellId != null && !spellId.isBlank() && readLedger(player).contains(spellId);
    }

    public CompletionStage<Void> setUnlockedSpellIdsV2(final Player player,
                                                        final List<String> spellIds) {
        SpellGrantLedger ledger = SpellGrantLedger.empty();
        if (spellIds != null) {
            for (final String spellId : spellIds) {
                if (spellId != null && !spellId.isBlank()) {
                    ledger = ledger.add(spellId, SOURCE_ADMIN).ledger();
                }
            }
        }
        final SpellGrantLedger requested = ledger;
        return spellGrantStore.replace(player.getUniqueId(), requested)
                .thenCompose(committed -> schedulePlayer(player,
                        () -> mirrorSpellLedger(player, committed)));
    }

    public CompletionStage<Boolean> unlockSpellV2(final Player player,
                                                   final String spellId) {
        return unlockSpellV2(player, spellId, SOURCE_ADMIN);
    }

    public CompletionStage<Boolean> unlockSpellV2(final Player player,
                                                   final String spellId,
                                                   final String source) {
        return spellGrantStore.add(player.getUniqueId(), spellId, source)
                .thenCompose(mutation -> {
                    if (!mutation.changed()) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return schedulePlayer(player, () -> mirrorSpellLedger(player, mutation.ledger()))
                            .thenApply(ignored -> mutation.spellLockChanged());
                });
    }

    public CompletionStage<Boolean> revokeGrantV2(final Player player,
                                                   final String spellId,
                                                   final String source) {
        return spellGrantStore.remove(player.getUniqueId(), spellId, source)
                .thenCompose(mutation -> {
                    if (!mutation.changed()) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return schedulePlayer(player, () -> mirrorSpellLedger(player, mutation.ledger()))
                            .thenApply(ignored -> mutation.spellLockChanged());
                });
    }

    public CompletionStage<List<String>> revokeGrantsFromV2(
            final Player player, final Predicate<String> sourceMatches) {
        return spellGrantStore.revokeSources(player.getUniqueId(), sourceMatches)
                .thenCompose(result -> {
                    if (!result.changed()) {
                        return CompletableFuture.completedFuture(result.lockedSpellIds());
                    }
                    return schedulePlayer(player, () -> mirrorSpellLedger(player, result.ledger()))
                            .thenApply(ignored -> result.lockedSpellIds());
                });
    }

    public CompletionStage<Void> clearSpellGrantsV2(final Player player) {
        return spellGrantStore.replace(player.getUniqueId(), SpellGrantLedger.empty())
                .thenCompose(committed -> schedulePlayer(player,
                        () -> mirrorSpellLedger(player, committed)));
    }

    public Set<String> getGrantSources(final Player player, final String spellId) {
        if (spellId == null || spellId.isBlank()) return Set.of();
        return readLedger(player).sources(spellId);
    }

    /** Greenfield mode reads only the PlayerProfile spellbook section. */
    public void backfillSpellGrants(final Player player) {
        readLedger(player);
    }'''
    text = text[:methods_start] + methods + text[backfill_end:]

    text = text.replace('        revokeGrantsFrom(player, source -> source.startsWith(SOURCE_BASE_PREFIX)\n'
                        '                || source.startsWith(SOURCE_SPEC_PREFIX));\n', '')

    ledger_start = text.index('    private SpellGrantLedger readLedger(final Player player) {')
    ledger_end = text.index('\n    private void mirrorClassState', ledger_start)
    ledger_methods = '''    private SpellGrantLedger readLedger(final Player player) {
        return spellGrantStore.read(player.getUniqueId());
    }

    private void mirrorSpellLedger(final Player player, final SpellGrantLedger ledger) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (ledger.spellIds().isEmpty()) {
            pdc.remove(unlockedSpellsKey);
        } else {
            pdc.set(unlockedSpellsKey, PersistentDataType.STRING,
                    String.join(",", ledger.spellIds()));
        }
    }
'''
    text = text[:ledger_start] + ledger_methods + text[ledger_end:]
    path.write_text(text, encoding="utf-8")


def patch_unlock_command() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/commands/job/JobUnlockSpellSubcommand.java"
    text = path.read_text(encoding="utf-8")
    old = '''        // Folia: unlockSpell writes the target's PDC, so it must run on the target's own region
        // thread (the target may be in a different region than the admin). sender.sendMessage is safe
        // from there.
        target.getScheduler().run(plugin, task -> {
            if (!jobManager.unlockSpell(target, spell.getId(),
                    hu.taliann.icesmp.managers.JobManager.SOURCE_ADMIN)) {
                sender.sendMessage(messageManager.get("messages.job-spell-already-unlocked", "&eEz a varázslat már fel van oldva."));
                return;
            }

            sender.sendMessage(messageManager.get(
                    "messages.job-unlockspell-success",
                    "&aVarázslat feloldva: &f%s &7-> &e%s",
                    target.getName(),
                    spell.getId()
            ));
        }, null);
'''
    new = '''        jobManager.unlockSpellV2(target, spell.getId(), JobManager.SOURCE_ADMIN)
                .whenComplete((unlocked, failure) -> target.getScheduler().run(plugin, task -> {
                    if (failure != null) {
                        sender.sendMessage(messageManager.get("messages.job-spell-storage-failed",
                                "&cA PlayerProfile spellbook mentése meghiúsult."));
                    } else if (!Boolean.TRUE.equals(unlocked)) {
                        sender.sendMessage(messageManager.get("messages.job-spell-already-unlocked",
                                "&eEz a varázslat már fel van oldva."));
                    } else {
                        sender.sendMessage(messageManager.get(
                                "messages.job-unlockspell-success",
                                "&aVarázslat feloldva: &f%s &7-> &e%s",
                                target.getName(), spell.getId()));
                    }
                }, null));
'''
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError("JobUnlockSpellSubcommand block mismatch")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


def patch_quest() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/managers/QuestManager.java"
    text = path.read_text(encoding="utf-8")
    old = '''        if (unlockSpell != null && !unlockSpell.isBlank()) {
            jobManager.unlockSpell(player, unlockSpell,
                    JobManager.SOURCE_QUEST_PREFIX + quest.getName().toLowerCase(Locale.ROOT));
        }
'''
    new = '''        if (unlockSpell != null && !unlockSpell.isBlank()) {
            jobManager.unlockSpellV2(player, unlockSpell,
                            JobManager.SOURCE_QUEST_PREFIX + quest.getName().toLowerCase(Locale.ROOT))
                    .exceptionally(failure -> {
                        plugin.getLogger().severe("Quest spell reward PlayerProfile commit failed for "
                                + player.getUniqueId() + ": " + failure.getMessage());
                        return false;
                    });
        }
'''
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError("QuestManager spell reward block mismatch")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


def patch_specialization() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/managers/SpecializationManager.java"
    text = path.read_text(encoding="utf-8")
    old = '''            if (classLevel >= required && jobManager.unlockSpell(player, spellId,
                    JobManager.SOURCE_SPEC_PREFIX + specialization.getId())) {
                player.sendMessage(messageManager.getMessage("spec-spell-unlocked",
                        "&5Specializációs képesség feloldva: &e{spell} &7(szint {level})",
                        Map.of("spell", spellId.toLowerCase(Locale.ROOT),
                                "level", String.valueOf(required))));
            }
'''
    new = '''            if (classLevel >= required) {
                jobManager.unlockSpellV2(player, spellId,
                                JobManager.SOURCE_SPEC_PREFIX + specialization.getId())
                        .whenComplete((unlocked, failure) -> player.getScheduler().run(
                                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(SpecializationManager.class),
                                task -> {
                                    if (failure == null && Boolean.TRUE.equals(unlocked)) {
                                        player.sendMessage(messageManager.getMessage("spec-spell-unlocked",
                                                "&5Specializációs képesség feloldva: &e{spell} &7(szint {level})",
                                                Map.of("spell", spellId.toLowerCase(Locale.ROOT),
                                                        "level", String.valueOf(required))));
                                    }
                                }, null));
            }
'''
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError("SpecializationManager spell block mismatch")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


def main() -> int:
    write_store()
    patch_ledger()
    patch_job_manager()
    patch_unlock_command()
    patch_quest()
    patch_specialization()
    print("PlayerProfile spellbook ledger authority wave applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
