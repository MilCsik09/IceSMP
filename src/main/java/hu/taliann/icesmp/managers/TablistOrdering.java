package hu.taliann.icesmp.managers;

import java.util.Locale;

/** Pure scoreboard-team ordering key builder, separated so the AFK ordering contract is testable. */
public final class TablistOrdering {

    private static final int MAX_NAME_LENGTH = 12;

    private TablistOrdering() {
    }

    public static String key(final int rankIndex, final String playerName, final boolean afk) {
        final char rank = (char) ('a' + Math.max(0, Math.min(24, rankIndex)));
        final String lower = playerName == null ? "" : playerName.toLowerCase(Locale.ROOT);
        final String namePart = lower.substring(0, Math.min(MAX_NAME_LENGTH, lower.length()));
        return new StringBuilder(2 + namePart.length())
                .append(rank)
                .append(afk ? '1' : '0')
                .append(namePart)
                .toString();
    }
}
