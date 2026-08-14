package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.QuestSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Atomic dungeon pass and lockout authority. */
public final class PlayerProfileDungeonAccessStore {

    public record Access(long passUntil, long lockUntil) {
        public Access {
            if (passUntil < 0L || lockUntil < 0L) throw new IllegalArgumentException("negative dungeon access");
        }
    }

    public record Grant(boolean granted, Access access) {
        public Grant { Objects.requireNonNull(access, "access"); }
    }

    public Access read(final UUID playerId, final String zoneId) {
        final String zone = zone(zoneId);
        final QuestSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.QUESTS, QuestSection.class);
        return new Access(section.cooldowns().getOrDefault(passKey(zone), 0L),
                section.cooldowns().getOrDefault(lockKey(zone), 0L));
    }

    public CompletionStage<Grant> grant(final UUID playerId, final String zoneId,
                                        final long now, final long passUntil,
                                        final long lockUntil) {
        if (now < 0L || passUntil < now || lockUntil < 0L) {
            throw new IllegalArgumentException("invalid dungeon grant");
        }
        final String zone = zone(zoneId);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.QUESTS, QuestSection.class, current -> {
                    final Access before = new Access(
                            current.cooldowns().getOrDefault(passKey(zone), 0L),
                            current.cooldowns().getOrDefault(lockKey(zone), 0L));
                    if (before.passUntil() > now) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new Grant(true, before));
                    }
                    if (before.lockUntil() > now) {
                        return PlayerProfileService.ConditionalMutation.unchanged(
                                new Grant(false, before));
                    }
                    final LinkedHashMap<String, Long> cooldowns =
                            new LinkedHashMap<>(current.cooldowns());
                    cooldowns.put(passKey(zone), passUntil);
                    if (lockUntil > 0L) cooldowns.put(lockKey(zone), lockUntil);
                    else cooldowns.remove(lockKey(zone));
                    final QuestSection next = new QuestSection(current.active(), current.completed(),
                            current.rewardReceipts(), cooldowns,
                            current.communityContributions(), current.claimableRewards(),
                            current.extensions());
                    final Access after = new Access(passUntil, lockUntil);
                    return PlayerProfileService.ConditionalMutation.changed(next,
                            new Grant(true, after));
                });
    }

    private static String passKey(final String zone) { return "dungeon.pass:" + zone; }
    private static String lockKey(final String zone) { return "dungeon.lock:" + zone; }

    private static String zone(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("dungeon zone required");
        final String zone = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
        if (zone.isBlank() || zone.length() > 96) throw new IllegalArgumentException("invalid dungeon zone");
        return zone;
    }
}
