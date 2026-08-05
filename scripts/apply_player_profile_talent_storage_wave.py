#!/usr/bin/env python3
"""Move talent ranks and bonus pools from player PDC to PlayerProfile."""
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
    path = ROOT / "src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileTalentStore.java"
    path.write_text('''package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.TalentSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** CAS-backed talent rank and bonus-pool authority. */
public final class PlayerProfileTalentStore {

    public enum Pool {
        CLASS("class:"), PROFESSION("profession:");
        private final String prefix;
        Pool(final String prefix) { this.prefix = prefix; }
        String prefix() { return prefix; }
    }

    public Map<String, Integer> ranks(final UUID playerId, final Pool pool) {
        return ranks(section(playerId), pool);
    }

    public int bonus(final UUID playerId, final Pool pool) {
        final TalentSection current = section(playerId);
        return pool == Pool.CLASS ? current.points() : current.bonusPoints();
    }

    public CompletionStage<Integer> grantBonus(final UUID playerId, final Pool pool,
                                                final int amount) {
        if (amount <= 0) throw new IllegalArgumentException("bonus amount must be positive");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.TALENTS, TalentSection.class, current -> {
                    final int before = pool == Pool.CLASS ? current.points() : current.bonusPoints();
                    final int after = Math.addExact(before, amount);
                    final TalentSection next = new TalentSection(
                            pool == Pool.CLASS ? after : current.points(),
                            pool == Pool.PROFESSION ? after : current.bonusPoints(),
                            current.purchased(), current.provenance(), current.loadout(),
                            current.refundReceipts(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, after);
                });
    }

    public CompletionStage<Map<String, Integer>> replaceRanks(
            final UUID playerId, final Pool pool, final Map<String, Integer> requested) {
        final Map<String, Integer> normalized = normalize(requested);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.TALENTS, TalentSection.class, current -> {
                    final Map<String, Integer> existing = ranks(current, pool);
                    if (existing.equals(normalized)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(existing);
                    }
                    final LinkedHashMap<String, String> loadout = new LinkedHashMap<>(current.loadout());
                    loadout.keySet().removeIf(key -> key.startsWith(pool.prefix()));
                    normalized.forEach((id, rank) -> loadout.put(pool.prefix() + id,
                            Integer.toString(rank)));
                    final TalentSection next = new TalentSection(current.points(),
                            current.bonusPoints(), current.purchased(), current.provenance(),
                            loadout, current.refundReceipts(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, normalized);
                });
    }

    private TalentSection section(final UUID playerId) {
        return PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.TALENTS, TalentSection.class);
    }

    private static Map<String, Integer> ranks(final TalentSection section, final Pool pool) {
        final LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        section.loadout().forEach((key, value) -> {
            if (!key.startsWith(pool.prefix())) return;
            final String id = key.substring(pool.prefix().length());
            if (id.isBlank()) throw new IllegalStateException("blank talent id in PlayerProfile");
            final int rank;
            try { rank = Integer.parseInt(value); }
            catch (final NumberFormatException malformed) {
                throw new IllegalStateException("invalid talent rank for " + id, malformed);
            }
            if (rank <= 0) throw new IllegalStateException("non-positive talent rank for " + id);
            result.put(id, rank);
        });
        return Map.copyOf(result);
    }

    private static Map<String, Integer> normalize(final Map<String, Integer> source) {
        if (source == null || source.isEmpty()) return Map.of();
        final LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        source.forEach((rawId, rawRank) -> {
            final String id = rawId == null ? "" : rawId.trim().toLowerCase(java.util.Locale.ROOT);
            final int rank = rawRank == null ? 0 : rawRank;
            if (id.isBlank() || !id.matches("[a-z0-9._-]+") || rank <= 0) {
                throw new IllegalArgumentException("invalid talent rank entry");
            }
            result.put(id, rank);
        });
        return Map.copyOf(result);
    }
}
''', encoding="utf-8")


def patch_manager() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/managers/TalentManager.java"
    text = path.read_text(encoding="utf-8")
    for line in (
        '    private final NamespacedKey classTalentsKey;\n',
        '    private final NamespacedKey professionTalentsKey;\n',
        '    /** K8 Emlékszilánk-beváltásból származó extra talentpontok (PDC, additív). */\n',
        '    private final NamespacedKey bonusClassPointsKey;\n',
        '    private final NamespacedKey bonusProfessionPointsKey;\n',
        '        this.classTalentsKey = new NamespacedKey(plugin, "talents_class");\n',
        '        this.professionTalentsKey = new NamespacedKey(plugin, "talents_profession");\n',
        '        this.bonusClassPointsKey = new NamespacedKey(plugin, "talent_bonus_class");\n',
        '        this.bonusProfessionPointsKey = new NamespacedKey(plugin, "talent_bonus_profession");\n',
    ):
        text = text.replace(line, '')
    field_anchor = '    private final NamespacedKey damageModifierKey;\n'
    field = ('    private final hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore '
             'talentStore = new hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore();\n')
    if field not in text:
        if text.count(field_anchor) != 1:
            raise RuntimeError("TalentManager store field anchor mismatch")
        text = text.replace(field_anchor, field_anchor + field, 1)

    start = text.index('    public Map<String, Integer> getRanks(final Player player, final boolean classPool) {')
    end = text.index('\n    public int getRank(', start)
    ranks = '''    public Map<String, Integer> getRanks(final Player player, final boolean classPool) {
        return talentStore.ranks(player.getUniqueId(), classPool
                ? hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool.CLASS
                : hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool.PROFESSION);
    }
'''
    text = text[:start] + ranks + text[end:]

    bonus_start = text.index('    public int getBonusPoints(final Player player, final boolean classPool) {')
    bonus_end = text.index('\n    /**\n     * K8: extra talentpont', bonus_start)
    bonus = '''    public int getBonusPoints(final Player player, final boolean classPool) {
        return talentStore.bonus(player.getUniqueId(), classPool
                ? hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool.CLASS
                : hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool.PROFESSION);
    }
'''
    text = text[:bonus_start] + bonus + text[bonus_end:]

    grant_start = text.index('    public void grantBonusPoints(final Player player, final boolean classPool, final int amount) {')
    grant_end = text.index('\n    /**\n     * Checks whether a talent is available', grant_start)
    grant = '''    public void grantBonusPoints(final Player player, final boolean classPool, final int amount) {
        if (amount <= 0) return;
        talentStore.grantBonus(player.getUniqueId(), classPool
                        ? hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool.CLASS
                        : hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool.PROFESSION,
                        amount)
                .exceptionally(failure -> {
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TalentManager.class)
                            .getLogger().severe("PlayerProfile talent bonus commit failed for "
                                    + player.getUniqueId() + ": " + failure.getMessage());
                    return 0;
                });
    }
'''
    text = text[:grant_start] + grant + text[grant_end:]

    save_start = text.index('    private void saveRanks(final Player player, final boolean classPool,')
    if not text.endswith('}\n'):
        raise RuntimeError("TalentManager must end with a single class-closing brace")
    save_end = len(text) - len('}\n')
    save = '''    private void saveRanks(final Player player, final boolean classPool,
                           final Map<String, Integer> ranks) {
        talentStore.replaceRanks(player.getUniqueId(), classPool
                        ? hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool.CLASS
                        : hu.taliann.icesmp.playerprofile.application.PlayerProfileTalentStore.Pool.PROFESSION,
                        ranks)
                .exceptionally(failure -> {
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TalentManager.class)
                            .getLogger().severe("PlayerProfile talent rank commit failed for "
                                    + player.getUniqueId() + ": " + failure.getMessage());
                    return Map.of();
                });
    }
'''
    text = text[:save_start] + save + text[save_end:]
    text = text.replace('import org.bukkit.persistence.PersistentDataType;\n', '')
    path.write_text(text, encoding="utf-8")


def main() -> int:
    write_store()
    patch_manager()
    print("PlayerProfile talent storage authority wave applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
