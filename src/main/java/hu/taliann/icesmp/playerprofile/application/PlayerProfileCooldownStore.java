package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;
import hu.taliann.icesmp.playerprofile.domain.section.QuestSection;
import hu.taliann.icesmp.playerprofile.domain.section.SpellbookSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Typed persistent timestamp/cooldown authority for gameplay systems. */
public final class PlayerProfileCooldownStore {

    public enum Domain { QUEST, FACTION, SPELLBOOK }

    public long read(final UUID playerId, final Domain domain, final String key) {
        final String id = key(key);
        return switch (Objects.requireNonNull(domain, "domain")) {
            case QUEST -> PlayerProfileAuthority.current().requireSection(playerId,
                    ProfileSectionId.QUESTS, QuestSection.class).cooldowns().getOrDefault(id, 0L);
            case FACTION -> PlayerProfileAuthority.current().requireSection(playerId,
                    ProfileSectionId.FACTION, FactionSection.class).cooldowns().getOrDefault(id, 0L);
            case SPELLBOOK -> PlayerProfileAuthority.current().requireSection(playerId,
                    ProfileSectionId.SPELLBOOK, SpellbookSection.class)
                    .persistentCooldowns().getOrDefault(id, 0L);
        };
    }

    /** Atomically reserves a timestamp if the existing value is not newer than {@code allowedBefore}. */
    public CompletionStage<Boolean> reserve(final UUID playerId, final Domain domain,
                                            final String key, final long allowedBefore,
                                            final long nextTimestamp) {
        if (allowedBefore < 0L || nextTimestamp < 0L) {
            throw new IllegalArgumentException("negative cooldown timestamp");
        }
        final String id = key(key);
        return switch (Objects.requireNonNull(domain, "domain")) {
            case QUEST -> PlayerProfileAuthority.current().mutateSectionConditional(
                    playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                        final long old = current.cooldowns().getOrDefault(id, 0L);
                        if (old > allowedBefore) {
                            return PlayerProfileService.ConditionalMutation.unchanged(false);
                        }
                        final LinkedHashMap<String, Long> map = new LinkedHashMap<>(current.cooldowns());
                        if (nextTimestamp == 0L) map.remove(id); else map.put(id, nextTimestamp);
                        return PlayerProfileService.ConditionalMutation.changed(
                                new QuestSection(current.active(), current.completed(),
                                        current.rewardReceipts(), map,
                                        current.communityContributions(), current.claimableRewards(),
                                        current.extensions()), true);
                    });
            case FACTION -> PlayerProfileAuthority.current().mutateSectionConditional(
                    playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                        final long old = current.cooldowns().getOrDefault(id, 0L);
                        if (old > allowedBefore) {
                            return PlayerProfileService.ConditionalMutation.unchanged(false);
                        }
                        final LinkedHashMap<String, Long> map = new LinkedHashMap<>(current.cooldowns());
                        if (nextTimestamp == 0L) map.remove(id); else map.put(id, nextTimestamp);
                        return PlayerProfileService.ConditionalMutation.changed(
                                new FactionSection(current.membershipId(), current.lastChosenFaction(),
                                        current.everChosen(), current.joinedAt(), current.leftAt(),
                                        current.history(), current.reputation(), map,
                                        current.extensions()), true);
                    });
            case SPELLBOOK -> PlayerProfileAuthority.current().mutateSectionConditional(
                    playerId, ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                        final long old = current.persistentCooldowns().getOrDefault(id, 0L);
                        if (old > allowedBefore) {
                            return PlayerProfileService.ConditionalMutation.unchanged(false);
                        }
                        final LinkedHashMap<String, Long> map =
                                new LinkedHashMap<>(current.persistentCooldowns());
                        if (nextTimestamp == 0L) map.remove(id); else map.put(id, nextTimestamp);
                        return PlayerProfileService.ConditionalMutation.changed(
                                new SpellbookSection(current.provenance(), current.selectedSpell(),
                                        current.favorites(), current.mastery(), map,
                                        current.uiState(), current.extensions()), true);
                    });
        };
    }

    public CompletionStage<Void> put(final UUID playerId, final Domain domain,
                                     final String key, final long timestamp) {
        if (timestamp < 0L) throw new IllegalArgumentException("negative cooldown timestamp");
        return reserve(playerId, domain, key, Long.MAX_VALUE, timestamp).thenApply(ignored -> null);
    }

    private static String key(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("cooldown key required");
        final String id = raw.trim().toLowerCase(Locale.ROOT);
        if (id.length() > 120 || !id.matches("[a-z0-9][a-z0-9._:-]*")) {
            throw new IllegalArgumentException("invalid cooldown key: " + raw);
        }
        return id;
    }
}
