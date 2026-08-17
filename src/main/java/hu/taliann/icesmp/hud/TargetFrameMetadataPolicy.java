package hu.taliann.icesmp.hud;

import hu.taliann.icesmp.pve.EliteAffix;
import hu.taliann.icesmp.pve.MobArchetype;
import hu.taliann.icesmp.pve.MobRank;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Fail-closed conversion of canonical mob PDC values into immutable HUD metadata. */
public final class TargetFrameMetadataPolicy {
    private TargetFrameMetadataPolicy() { }

    public static String templateId(final String raw, final Predicate<String> knownTemplate) {
        Objects.requireNonNull(knownTemplate, "knownTemplate");
        if (raw == null || raw.isBlank()) return "";
        final String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return knownTemplate.test(normalized) ? normalized : "";
    }

    public static int level(final Integer stored) {
        return stored != null && stored >= 1 && stored <= 200 ? stored : 0;
    }

    public static TargetHudState.Rank rank(final String stored, final boolean worldBoss) {
        if (stored == null || stored.isBlank()) {
            return worldBoss ? TargetHudState.Rank.WORLD_BOSS : TargetHudState.Rank.NORMAL;
        }
        try {
            return TargetHudState.Rank.valueOf(MobRank.parse(stored).name());
        } catch (final IllegalArgumentException invalid) {
            return worldBoss ? TargetHudState.Rank.WORLD_BOSS : TargetHudState.Rank.NORMAL;
        }
    }

    public static String affixStatus(final String raw) {
        if (raw == null || raw.isBlank()) return "";
        return Arrays.stream(raw.split(",")).map(String::trim)
                .filter(value -> {
                    try { EliteAffix.parse(value); return true; }
                    catch (final IllegalArgumentException ignored) { return false; }
                }).distinct().limit(2).map(TargetFrameMetadataPolicy::displayEnum)
                .collect(Collectors.joining(" • "));
    }

    public static String archetypeStatus(final String raw) {
        if (raw == null || raw.isBlank()) return "";
        try { return displayEnum(MobArchetype.parse(raw).name()); }
        catch (final IllegalArgumentException ignored) { return ""; }
    }

    private static String displayEnum(final String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
