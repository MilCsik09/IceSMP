package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.itemization.ItemRarity;
import hu.taliann.icesmp.itemization.ItemTemplate;
import hu.taliann.icesmp.itemization.LootDiversityState;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensions;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.StatisticsSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** CAS-backed Itemization 2.0 loot history stored in the canonical statistics section. */
public final class PlayerProfileLootDiversityStore {

    static final String EXTENSION_KEY = "itemization.loot-diversity.v1";

    public LootDiversityState current(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return read(PlayerProfileAuthority.current().requireSection(
                playerId, ProfileSectionId.STATISTICS, StatisticsSection.class));
    }

    public CompletionStage<Boolean> record(final UUID playerId,
                                           final LootDiversityState.Drop drop) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(drop, "drop");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.STATISTICS, StatisticsSection.class, current -> {
                    final LootDiversityState before = read(current);
                    final LootDiversityState after = before.record(drop);
                    if (before == after) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final StatisticsSection next = PlayerProfileSectionExtensions.put(
                            current, EXTENSION_KEY, encode(after));
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    public static LootDiversityState read(final StatisticsSection section) {
        Objects.requireNonNull(section, "section");
        final Object raw = section.extensions().get(EXTENSION_KEY);
        if (raw == null) return LootDiversityState.empty();
        if (!(raw instanceof List<?> entries)) {
            throw new IllegalStateException("Invalid loot diversity extension: " + raw);
        }
        if (entries.size() > LootDiversityState.MAX_DROPS) {
            throw new IllegalStateException("Loot diversity extension exceeds its durable bound");
        }
        final ArrayList<LootDiversityState.Drop> decoded = new ArrayList<>(entries.size());
        for (final Object entry : entries) decoded.add(decodeEntry(entry));
        return new LootDiversityState(decoded);
    }

    static List<String> encode(final LootDiversityState state) {
        Objects.requireNonNull(state, "state");
        return state.recentDrops().stream().map(drop -> String.join("|",
                drop.itemId().toString(), drop.templateId(), drop.rarity().id(),
                drop.slot().name().toLowerCase(Locale.ROOT),
                drop.family().name().toLowerCase(Locale.ROOT))).toList();
    }

    private static LootDiversityState.Drop decodeEntry(final Object raw) {
        if (!(raw instanceof String encoded)) {
            throw new IllegalStateException("Invalid loot diversity entry: " + raw);
        }
        final String[] fields = encoded.split("\\|", -1);
        if (fields.length != 5 || encoded.length() > 320) {
            throw new IllegalStateException("Invalid loot diversity entry: " + encoded);
        }
        try {
            return new LootDiversityState.Drop(UUID.fromString(fields[0]), fields[1],
                    ItemRarity.parse(fields[2]), enumValue(ItemTemplate.Slot.class, fields[3]),
                    enumValue(ItemTemplate.Family.class, fields[4]));
        } catch (final RuntimeException invalid) {
            throw new IllegalStateException("Invalid loot diversity entry: " + encoded, invalid);
        }
    }

    private static <T extends Enum<T>> T enumValue(final Class<T> type, final String raw) {
        return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
    }
}
