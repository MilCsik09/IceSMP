#!/usr/bin/env python3
"""Move per-player HUD visibility preferences from PDC to PlayerProfile."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def write_store() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileHudPreferenceStore.java"
    path.write_text('''package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.PreferenceSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** CAS-backed HUD visibility preferences. Runtime caches are rebuildable mirrors only. */
public final class PlayerProfileHudPreferenceStore {
    private static final String HIDDEN_KEY = "hud.hidden-sections";
    private static final Set<String> ALLOWED = Set.of(
            "frakcio", "valuta", "kaszt", "eroforras", "esemeny", "csapat", "mind");

    public Set<String> hidden(final UUID playerId) {
        return decode(PlayerProfileAuthority.current().requireSection(
                playerId, ProfileSectionId.PREFERENCES, PreferenceSection.class)
                .values().get(HIDDEN_KEY));
    }

    public CompletionStage<ToggleResult> toggle(final UUID playerId, final String rawSection) {
        final String section = normalize(rawSection);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.PREFERENCES, PreferenceSection.class, current -> {
                    final LinkedHashSet<String> hidden = new LinkedHashSet<>(
                            decode(current.values().get(HIDDEN_KEY)));
                    final boolean nowHidden;
                    if (hidden.remove(section)) {
                        nowHidden = false;
                    } else {
                        hidden.add(section);
                        nowHidden = true;
                    }
                    final LinkedHashMap<String, String> values = new LinkedHashMap<>(current.values());
                    if (hidden.isEmpty()) values.remove(HIDDEN_KEY);
                    else values.put(HIDDEN_KEY, encode(hidden));
                    final PreferenceSection next = new PreferenceSection(current.language(),
                            current.hudEnabled(), current.scoreboardEnabled(),
                            current.notificationsEnabled(), current.publicProfile(),
                            current.publicCompanion(), current.publicAchievements(),
                            current.publicClassFactionSpec(), current.apiVisible(), values,
                            current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new ToggleResult(Set.copyOf(hidden), nowHidden));
                });
    }

    private static String normalize(final String raw) {
        final String section = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED.contains(section)) {
            throw new IllegalArgumentException("Unsupported HUD section: " + raw);
        }
        return section;
    }

    private static Set<String> decode(final String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        final LinkedHashSet<String> result = new LinkedHashSet<>();
        for (final String token : raw.split(",")) {
            final String normalized = normalize(token);
            if (!result.add(normalized)) {
                throw new IllegalStateException("Duplicate HUD section in PlayerProfile: " + normalized);
            }
        }
        return Set.copyOf(result);
    }

    private static String encode(final Set<String> hidden) {
        final List<String> ordered = new ArrayList<>(hidden);
        ordered.sort(String::compareTo);
        return String.join(",", ordered);
    }

    public record ToggleResult(Set<String> hidden, boolean nowHidden) {
        public ToggleResult {
            hidden = Set.copyOf(hidden);
        }
    }
}
''', encoding="utf-8")


def patch_hud_manager() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/managers/HudManager.java"
    text = path.read_text(encoding="utf-8")
    if "PlayerProfileHudPreferenceStore preferenceStore" in text and "hud_hidden_sections" not in text:
        return
    for imp in (
        'import org.bukkit.NamespacedKey;\n',
        'import org.bukkit.persistence.PersistentDataContainer;\n',
        'import org.bukkit.persistence.PersistentDataType;\n',
        'import java.util.Arrays;\n',
    ):
        text = text.replace(imp, '')
    text = text.replace('''    private final NamespacedKey hiddenSectionsKey;
    /** Per-player /hud toggle state, cached in memory (PDC is only touched on load/save, never per tick). */
    private final ConcurrentHashMap<UUID, Set<String>> hiddenSectionsCache = new ConcurrentHashMap<>();
''', '''    /** Per-player /hud toggle state; rebuildable mirror of PlayerProfile preferences. */
    private final ConcurrentHashMap<UUID, Set<String>> hiddenSectionsCache = new ConcurrentHashMap<>();
    private final hu.taliann.icesmp.playerprofile.application.PlayerProfileHudPreferenceStore
            preferenceStore = new hu.taliann.icesmp.playerprofile.application.PlayerProfileHudPreferenceStore();
''')
    text = text.replace('        this.hiddenSectionsKey = new NamespacedKey(plugin, "hud_hidden_sections");\n', '')
    start = text.index('    public Set<String> hiddenSections(final Player player) {')
    end = text.index('\n    /** Whether the sidebar should render at all', start)
    replacement = '''    public Set<String> hiddenSections(final Player player) {
        return hiddenSectionsCache.computeIfAbsent(player.getUniqueId(), preferenceStore::hidden);
    }

    /** Whether the given section (or {@link #SECTION_ALL}) is currently hidden for the player. */
    public boolean isSectionHidden(final Player player, final String section) {
        return hiddenSections(player).contains(section);
    }

    /** Persists the toggle through PlayerProfile CAS before updating the runtime mirror. */
    public java.util.concurrent.CompletionStage<Boolean> toggleSection(
            final Player player, final String section) {
        return preferenceStore.toggle(player.getUniqueId(), section).thenApply(result -> {
            hiddenSectionsCache.put(player.getUniqueId(), result.hidden());
            return result.nowHidden();
        });
    }
'''
    text = text[:start] + replacement + text[end:]
    path.write_text(text, encoding="utf-8")


def patch_command() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/commands/HudCommand.java"
    text = path.read_text(encoding="utf-8")
    text = text.replace(' * /hud — a saját HUD-oldalsáv szekcióinak be/kikapcsolása (perzisztens, PDC-alapú).\n',
                        ' * /hud — a saját HUD-oldalsáv szekcióinak PlayerProfile-alapú be/kikapcsolása.\n')
    old = '''        final boolean nowHidden = hudManager.toggleSection(player, section);
        // A parancs a saját szálán fut (a játékos a saját PDC-jét írja), utána azonnal
        // frissítjük a láthatóságot, hogy a következő HUD-tick előtt is lássa a hatást.
        hudManager.update(player);

        if (HudManager.SECTION_ALL.equals(section)) {
            player.sendMessage(nowHidden
                    ? messageManager.get("hud-toggled-all-off", "&b[HUD] &7A teljes HUD-oldalsáv &ckikapcsolva&7.")
                    : messageManager.get("hud-toggled-all-on", "&b[HUD] &7A teljes HUD-oldalsáv &abekapcsolva&7."));
            return;
        }

        player.sendMessage(nowHidden
                ? messageManager.get("hud-toggled-off", "&b[HUD] &7%s szekció &ckikapcsolva&7.", displayName(section))
                : messageManager.get("hud-toggled-on", "&b[HUD] &7%s szekció &abekapcsolva&7.", displayName(section)));
'''
    new = '''        hudManager.toggleSection(player, section).whenComplete((nowHidden, failure) ->
                player.getScheduler().run(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(HudCommand.class), task -> {
                            if (failure != null) {
                                player.sendMessage(messageManager.get("hud-toggle-storage-failed",
                                        "&cA HUD-beállítás PlayerProfile mentése meghiúsult."));
                                return;
                            }
                            hudManager.update(player);
                            if (HudManager.SECTION_ALL.equals(section)) {
                                player.sendMessage(Boolean.TRUE.equals(nowHidden)
                                        ? messageManager.get("hud-toggled-all-off",
                                                "&b[HUD] &7A teljes HUD-oldalsáv &ckikapcsolva&7.")
                                        : messageManager.get("hud-toggled-all-on",
                                                "&b[HUD] &7A teljes HUD-oldalsáv &abekapcsolva&7."));
                            } else {
                                player.sendMessage(Boolean.TRUE.equals(nowHidden)
                                        ? messageManager.get("hud-toggled-off",
                                                "&b[HUD] &7%s szekció &ckikapcsolva&7.", displayName(section))
                                        : messageManager.get("hud-toggled-on",
                                                "&b[HUD] &7%s szekció &abekapcsolva&7.", displayName(section)));
                            }
                        }, null));
'''
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError(f"HudCommand toggle block count={text.count(old)}")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


def main() -> int:
    write_store()
    patch_hud_manager()
    patch_command()
    print("PlayerProfile HUD preference authority wave applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
