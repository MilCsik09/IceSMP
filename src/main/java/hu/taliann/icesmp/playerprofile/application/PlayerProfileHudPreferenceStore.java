package hu.taliann.icesmp.playerprofile.application;

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
