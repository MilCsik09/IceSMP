#!/usr/bin/env python3
"""Add temporary non-PDC compatibility bridges while call sites become fully async."""
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "src/main/java/hu/taliann/icesmp/managers/JobManager.java"
text = path.read_text(encoding="utf-8")
anchor = '''    public CompletionStage<Void> clearSpellGrantsV2(final Player player) {
        return spellGrantStore.replace(player.getUniqueId(), SpellGrantLedger.empty())
                .thenCompose(committed -> schedulePlayer(player,
                        () -> mirrorSpellLedger(player, committed)));
    }

'''
bridge = '''    public CompletionStage<Void> clearSpellGrantsV2(final Player player) {
        return spellGrantStore.replace(player.getUniqueId(), SpellGrantLedger.empty())
                .thenCompose(committed -> schedulePlayer(player,
                        () -> mirrorSpellLedger(player, committed)));
    }

    /**
     * Temporary source-compatibility bridge. The preview is read from PlayerProfile and the
     * durable mutation is still written only through PlayerProfile; no PDC authority remains.
     * Callers are migrated to the CompletionStage variants in the following authority waves.
     */
    @Deprecated(forRemoval = true)
    public boolean unlockSpell(final Player player, final String spellId) {
        return unlockSpell(player, spellId, SOURCE_ADMIN);
    }

    @Deprecated(forRemoval = true)
    public boolean unlockSpell(final Player player, final String spellId, final String source) {
        final SpellGrantLedger.Mutation preview = readLedger(player).add(spellId, source);
        if (preview.changed()) {
            unlockSpellV2(player, spellId, source).exceptionally(failure -> {
                plugin.getLogger().severe("PlayerProfile spell grant commit failed for "
                        + player.getUniqueId() + ": " + failure.getMessage());
                return false;
            });
        }
        return preview.spellLockChanged();
    }

    @Deprecated(forRemoval = true)
    public boolean revokeGrant(final Player player, final String spellId, final String source) {
        final SpellGrantLedger.Mutation preview = readLedger(player).remove(spellId, source);
        if (preview.changed()) {
            revokeGrantV2(player, spellId, source).exceptionally(failure -> {
                plugin.getLogger().severe("PlayerProfile spell revoke commit failed for "
                        + player.getUniqueId() + ": " + failure.getMessage());
                return false;
            });
        }
        return preview.spellLockChanged();
    }

    @Deprecated(forRemoval = true)
    public List<String> revokeGrantsFrom(final Player player,
                                         final Predicate<String> sourceMatches) {
        final SpellGrantLedger.RevokeResult preview = readLedger(player).revokeSources(sourceMatches);
        if (preview.changed()) {
            revokeGrantsFromV2(player, sourceMatches).exceptionally(failure -> {
                plugin.getLogger().severe("PlayerProfile spell-source revoke commit failed for "
                        + player.getUniqueId() + ": " + failure.getMessage());
                return List.of();
            });
        }
        return preview.lockedSpellIds();
    }

    @Deprecated(forRemoval = true)
    public void clearSpellGrants(final Player player) {
        clearSpellGrantsV2(player).exceptionally(failure -> {
            plugin.getLogger().severe("PlayerProfile spellbook clear failed for "
                    + player.getUniqueId() + ": " + failure.getMessage());
            return null;
        });
    }

'''
if bridge not in text:
    if text.count(anchor) != 1:
        raise SystemExit(f"JobManager compatibility anchor count={text.count(anchor)}")
    text = text.replace(anchor, bridge, 1)
    path.write_text(text, encoding="utf-8")
print("PlayerProfile spellbook compatibility bridges applied.")
