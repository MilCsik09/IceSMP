package hu.taliann.icesmp.motd;

/** Dependency-free deterministic selector used by the async server-list presentation. */
public final class MotdSelector {

    public enum Mode {
        TIME,
        RANDOM;

        public static Mode parse(final String value) {
            if (value == null) {
                throw new IllegalArgumentException("A MOTD választási mód hiányzik.");
            }
            try {
                return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (final IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Ismeretlen MOTD választási mód: " + value);
            }
        }
    }

    public enum ActiveEvent {
        BLOOD_MOON,
        WORLD_BOSS,
        SEASON_END,
        NONE
    }

    private MotdSelector() {
    }

    public static int selectIndex(final Mode mode, final int size, final long nowMillis,
                                  final long intervalMillis, final long seed) {
        if (mode == null) {
            throw new IllegalArgumentException("A választási mód nem lehet null.");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("A MOTD variánslista nem lehet üres.");
        }
        if (intervalMillis <= 0L) {
            throw new IllegalArgumentException("A MOTD intervallum csak pozitív lehet.");
        }
        final long bucket = Math.floorDiv(nowMillis, intervalMillis);
        if (mode == Mode.TIME) {
            return Math.floorMod(bucket, size);
        }
        return Math.floorMod(mix64(seed ^ (bucket * 0x9E3779B97F4A7C15L)), size);
    }

    public static ActiveEvent selectEvent(final boolean bloodMoonActive, final boolean worldBossActive,
                                          final long seasonEndMillis, final long nowMillis,
                                          final long seasonThresholdMillis) {
        if (bloodMoonActive) {
            return ActiveEvent.BLOOD_MOON;
        }
        if (worldBossActive) {
            return ActiveEvent.WORLD_BOSS;
        }
        final long remaining = seasonEndMillis - nowMillis;
        if (seasonThresholdMillis >= 0L && remaining >= 0L && remaining <= seasonThresholdMillis) {
            return ActiveEvent.SEASON_END;
        }
        return ActiveEvent.NONE;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
