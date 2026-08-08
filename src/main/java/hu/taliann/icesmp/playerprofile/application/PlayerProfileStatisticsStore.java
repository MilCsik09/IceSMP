package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSnapshot;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.StatisticsSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** CAS-backed durable player statistics and explicitly derived leaderboard snapshots. */
public final class PlayerProfileStatisticsStore {
    public static final String RAID_KILLS = "raid-kills";
    public static final String KILLS = "kills";
    public static final String DEATHS = "deaths";
    public static final String MOB_KILLS = "mob-kills";
    public static final String SPELL_CASTS = "spell-casts";
    public static final String QUESTS_COMPLETED = "quests-completed";
    private static final String LEVEL_SNAPSHOT = "leaderboard.level-snapshot";
    private static final String WEALTH_MILLI_SNAPSHOT = "leaderboard.wealth-milli-snapshot";
    private static final long SCALE = 1_000L;

    /** Faj-szintű bestiárium-kulcsok: a fix allowlist helyett prefix+charset validáció. */
    private static final String BESTIARY_KILL_PREFIX = "bestiary.kills.";
    private static final String BESTIARY_FIRST_PREFIX = "bestiary.first.";

    public long read(final UUID playerId, final String key) {
        return section(playerId).lifetime().getOrDefault(validateKey(key), 0L);
    }

    public long speciesKills(final UUID playerId, final String speciesEntry) {
        return section(playerId).lifetime()
                .getOrDefault(BESTIARY_KILL_PREFIX + validateSpecies(speciesEntry), 0L);
    }

    public long speciesFirstKillAt(final UUID playerId, final String speciesEntry) {
        return section(playerId).lifetime()
                .getOrDefault(BESTIARY_FIRST_PREFIX + validateSpecies(speciesEntry), 0L);
    }

    /**
     * A mob-kill összesítő és a faj-szintű számláló/első-elejtés időbélyeg EGY section-commitban
     * frissül — a bestiárium-mélység nem adhat második írást a meglévő per-kill útvonal mellé.
     * {@code speciesEntry == null} esetén csak az összesítő nő (nem-szörny elejtés).
     */
    public CompletionStage<Long> recordMobKill(final UUID playerId, final String speciesEntry,
                                               final long nowEpochMillis) {
        final String species = speciesEntry == null ? null : validateSpecies(speciesEntry);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.STATISTICS, StatisticsSection.class, current -> {
                    final LinkedHashMap<String, Long> lifetime = new LinkedHashMap<>(current.lifetime());
                    lifetime.merge(MOB_KILLS, 1L, Math::addExact);
                    long speciesAfter = 0L;
                    if (species != null) {
                        speciesAfter = lifetime.merge(BESTIARY_KILL_PREFIX + species, 1L, Math::addExact);
                        lifetime.putIfAbsent(BESTIARY_FIRST_PREFIX + species, nowEpochMillis);
                    }
                    final StatisticsSection next = new StatisticsSection(lifetime,
                            current.season(), current.claimedMilestones(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, speciesAfter);
                });
    }

    private static String validateSpecies(final String species) {
        if (species == null || species.isBlank() || species.length() > 64
                || !species.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("invalid bestiary species entry: " + species);
        }
        return species;
    }

    public CompletionStage<Long> increment(final UUID playerId, final String key) {
        final String normalized = validateKey(key);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.STATISTICS, StatisticsSection.class, current -> {
                    final long before = current.lifetime().getOrDefault(normalized, 0L);
                    final long after = Math.addExact(before, 1L);
                    final LinkedHashMap<String, Long> lifetime = new LinkedHashMap<>(current.lifetime());
                    lifetime.put(normalized, after);
                    final StatisticsSection next = new StatisticsSection(lifetime,
                            current.season(), current.claimedMilestones(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, after);
                });
    }

    public CompletionStage<LeaderboardSnapshot> snapshot(final UUID playerId,
                                                          final int level,
                                                          final double wealth) {
        if (level < 0 || !Double.isFinite(wealth) || wealth < 0.0D) {
            throw new IllegalArgumentException("invalid leaderboard snapshot");
        }
        final long wealthMilli = Math.round(wealth * SCALE);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.STATISTICS, StatisticsSection.class, current -> {
                    final long oldLevel = current.lifetime().getOrDefault(LEVEL_SNAPSHOT, 0L);
                    final long oldWealth = current.lifetime().getOrDefault(WEALTH_MILLI_SNAPSHOT, 0L);
                    final LeaderboardSnapshot result = new LeaderboardSnapshot(level, wealthMilli);
                    if (oldLevel == level && oldWealth == wealthMilli) {
                        return PlayerProfileService.ConditionalMutation.unchanged(result);
                    }
                    final LinkedHashMap<String, Long> lifetime = new LinkedHashMap<>(current.lifetime());
                    lifetime.put(LEVEL_SNAPSHOT, (long) level);
                    lifetime.put(WEALTH_MILLI_SNAPSHOT, wealthMilli);
                    final StatisticsSection next = new StatisticsSection(lifetime,
                            current.season(), current.claimedMilestones(), current.extensions());
                    return PlayerProfileService.ConditionalMutation.changed(next, result);
                });
    }

    public LeaderboardSnapshot leaderboard(final PlayerProfileSnapshot profile) {
        final Map<String, Long> values = profile.statistics().value().lifetime();
        return new LeaderboardSnapshot(
                Math.toIntExact(values.getOrDefault(LEVEL_SNAPSHOT, 0L)),
                values.getOrDefault(WEALTH_MILLI_SNAPSHOT, 0L));
    }

    public CounterSnapshot counters(final PlayerProfileSnapshot profile) {
        final Map<String, Long> values = profile.statistics().value().lifetime();
        return new CounterSnapshot(value(values, RAID_KILLS), value(values, KILLS),
                value(values, DEATHS), value(values, MOB_KILLS),
                value(values, SPELL_CASTS), value(values, QUESTS_COMPLETED));
    }

    private StatisticsSection section(final UUID playerId) {
        return PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.STATISTICS, StatisticsSection.class);
    }

    private static String validateKey(final String key) {
        if (!java.util.Set.of(RAID_KILLS, KILLS, DEATHS, MOB_KILLS,
                SPELL_CASTS, QUESTS_COMPLETED).contains(key)) {
            throw new IllegalArgumentException("unsupported statistics key: " + key);
        }
        return key;
    }

    private static int value(final Map<String, Long> source, final String key) {
        return Math.toIntExact(source.getOrDefault(key, 0L));
    }

    public record LeaderboardSnapshot(int level, long wealthMilli) {
        public LeaderboardSnapshot {
            if (level < 0 || wealthMilli < 0L) {
                throw new IllegalArgumentException("negative leaderboard snapshot");
            }
        }
        public double wealth() { return wealthMilli / (double) SCALE; }
    }

    public record CounterSnapshot(int raidKills, int kills, int deaths,
                                  int mobKills, int spellCasts, int questsCompleted) {
        public CounterSnapshot {
            if (raidKills < 0 || kills < 0 || deaths < 0 || mobKills < 0
                    || spellCasts < 0 || questsCompleted < 0) {
                throw new IllegalArgumentException("negative counter snapshot");
            }
        }
    }
}
