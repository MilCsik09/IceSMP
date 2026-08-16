package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.hud.HudComponent;
import hu.taliann.icesmp.hud.HudComponentLayout;
import hu.taliann.icesmp.hud.HudLayoutSnapshot;
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
    private static final String LAYOUT_PREFIX = "hud.layout-v2.";
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

    public HudLayoutSnapshot layout(final UUID playerId, final HudLayoutSnapshot global) {
        final PreferenceSection preferences = PlayerProfileAuthority.current().requireSection(
                playerId, ProfileSectionId.PREFERENCES, PreferenceSection.class);
        return applyLayoutOverrides(global, preferences.values());
    }

    public boolean hasLayoutOverrides(final UUID playerId) {
        return PlayerProfileAuthority.current().requireSection(
                        playerId, ProfileSectionId.PREFERENCES, PreferenceSection.class)
                .values().keySet().stream().anyMatch(key -> key.startsWith(LAYOUT_PREFIX));
    }

    public CompletionStage<LayoutSaveResult> saveLayout(final UUID playerId,
                                                         final HudLayoutSnapshot effective,
                                                         final HudLayoutSnapshot global) {
        final Map<String, String> overrides = encodeLayoutOverrides(effective, global);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.PREFERENCES, PreferenceSection.class, current -> {
                    final LinkedHashMap<String, String> values = withoutLayout(current.values());
                    values.putAll(overrides);
                    final LayoutSaveResult result = new LayoutSaveResult(
                            !values.equals(current.values()), overrides.size());
                    if (!result.changed()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(result);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withValues(current, values), result);
                });
    }

    public CompletionStage<Boolean> resetLayout(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.PREFERENCES, PreferenceSection.class, current -> {
                    final LinkedHashMap<String, String> values = withoutLayout(current.values());
                    if (values.equals(current.values())) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withValues(current, values), true);
                });
    }

    static HudLayoutSnapshot applyLayoutOverrides(final HudLayoutSnapshot rawGlobal,
                                                   final Map<String, String> values) {
        final HudLayoutSnapshot global = rawGlobal == null ? HudLayoutSnapshot.defaults() : rawGlobal;
        HudLayoutSnapshot result = new HudLayoutSnapshot(
                integer(values, LAYOUT_PREFIX + "x", HudLayoutSnapshot.MIN_X_OFFSET,
                        HudLayoutSnapshot.MAX_X_OFFSET, global.xOffsetPixels()),
                integer(values, LAYOUT_PREFIX + "y", HudLayoutSnapshot.MIN_Y_OFFSET,
                        HudLayoutSnapshot.MAX_Y_OFFSET, global.yOffsetPixels()),
                integer(values, LAYOUT_PREFIX + "margin", HudLayoutSnapshot.MIN_SAFE_MARGIN,
                        HudLayoutSnapshot.MAX_SAFE_MARGIN, global.safeMarginPixels()),
                scaleIndex(values, LAYOUT_PREFIX + "scale", global.scaleIndex()),
                global.components());
        for (final HudComponent component : HudComponent.editableValues()) {
            final HudComponentLayout base = global.componentLayout(component);
            final String prefix = LAYOUT_PREFIX + component.id() + ".";
            result = result.withComponent(component, new HudComponentLayout(
                    integer(values, prefix + "x", HudLayoutSnapshot.MIN_X_OFFSET,
                            HudLayoutSnapshot.MAX_X_OFFSET, base.xOffsetPixels()),
                    integer(values, prefix + "y", HudLayoutSnapshot.MIN_Y_OFFSET,
                            HudLayoutSnapshot.MAX_Y_OFFSET, base.yOffsetPixels()),
                    scaleIndex(values, prefix + "scale", base.scaleIndex()),
                    bool(values, prefix + "visible", base.visible())));
        }
        return result;
    }

    static Map<String, String> encodeLayoutOverrides(final HudLayoutSnapshot rawEffective,
                                                      final HudLayoutSnapshot rawGlobal) {
        final HudLayoutSnapshot effective = rawEffective == null ? HudLayoutSnapshot.defaults() : rawEffective;
        final HudLayoutSnapshot global = rawGlobal == null ? HudLayoutSnapshot.defaults() : rawGlobal;
        final LinkedHashMap<String, String> values = new LinkedHashMap<>();
        different(values, LAYOUT_PREFIX + "x", effective.xOffsetPixels(), global.xOffsetPixels());
        different(values, LAYOUT_PREFIX + "y", effective.yOffsetPixels(), global.yOffsetPixels());
        different(values, LAYOUT_PREFIX + "margin", effective.safeMarginPixels(), global.safeMarginPixels());
        different(values, LAYOUT_PREFIX + "scale", effective.scalePermille(), global.scalePermille());
        for (final HudComponent component : HudComponent.editableValues()) {
            final HudComponentLayout personal = effective.componentLayout(component);
            final HudComponentLayout base = global.componentLayout(component);
            final String prefix = LAYOUT_PREFIX + component.id() + ".";
            different(values, prefix + "x", personal.xOffsetPixels(), base.xOffsetPixels());
            different(values, prefix + "y", personal.yOffsetPixels(), base.yOffsetPixels());
            different(values, prefix + "scale", scalePermille(personal), scalePermille(base));
            if (personal.visible() != base.visible()) {
                values.put(prefix + "visible", Boolean.toString(personal.visible()));
            }
        }
        return Map.copyOf(values);
    }

    private static PreferenceSection withValues(final PreferenceSection current,
                                                final Map<String, String> values) {
        return new PreferenceSection(current.language(), current.hudEnabled(),
                current.scoreboardEnabled(), current.notificationsEnabled(), current.publicProfile(),
                current.publicCompanion(), current.publicAchievements(),
                current.publicClassFactionSpec(), current.apiVisible(), values, current.extensions());
    }

    private static LinkedHashMap<String, String> withoutLayout(final Map<String, String> current) {
        final LinkedHashMap<String, String> values = new LinkedHashMap<>(current);
        values.keySet().removeIf(key -> key.startsWith(LAYOUT_PREFIX));
        return values;
    }

    private static void different(final Map<String, String> target, final String key,
                                  final int personal, final int global) {
        if (personal != global) target.put(key, Integer.toString(personal));
    }

    private static int integer(final Map<String, String> values, final String key,
                               final int minimum, final int maximum, final int fallback) {
        try {
            final int value = Integer.parseInt(values.getOrDefault(key, ""));
            return value >= minimum && value <= maximum ? value : fallback;
        } catch (final NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int scaleIndex(final Map<String, String> values, final String key,
                                  final int fallback) {
        final int permille = integer(values, key, HudLayoutSnapshot.SCALE_PERMILLE.getFirst(),
                HudLayoutSnapshot.SCALE_PERMILLE.getLast(), -1);
        final int index = HudLayoutSnapshot.SCALE_PERMILLE.indexOf(permille);
        return index < 0 ? fallback : index;
    }

    private static int scalePermille(final HudComponentLayout layout) {
        return HudLayoutSnapshot.SCALE_PERMILLE.get(layout.scaleIndex());
    }

    private static boolean bool(final Map<String, String> values, final String key,
                                final boolean fallback) {
        final String raw = values.get(key);
        if ("true".equals(raw)) return true;
        if ("false".equals(raw)) return false;
        return fallback;
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

    public record LayoutSaveResult(boolean changed, int overrideCount) { }
}
