package hu.taliann.icesmp.utils;

import org.bukkit.entity.Player;

public final class ExperienceUtil {

    private ExperienceUtil() {
    }

    public static int getTotalExperience(final Player player) {
        if (player == null) {
            return 0;
        }

        final int level = Math.max(0, player.getLevel());
        final int baseXp = getExperienceForLevel(level);
        final int progressXp = (int) Math.floor(player.getExp() * player.getExpToLevel());
        return Math.max(0, baseXp + Math.max(0, progressXp));
    }

    public static void setTotalExperience(final Player player, final int totalXP) {
        if (player == null) {
            return;
        }

        final int normalized = Math.max(0, totalXP);
        final int targetLevel = resolveLevelForTotalXp(normalized);
        final int levelBaseXp = getExperienceForLevel(targetLevel);
        final int intoLevel = Math.max(0, normalized - levelBaseXp);
        final int toNextLevel = Math.max(1, getXpToNextLevel(targetLevel));
        final float progress = Math.min(1.0F, Math.max(0.0F, intoLevel / (float) toNextLevel));

        player.setLevel(targetLevel);
        player.setExp(progress);
        player.setTotalExperience(normalized);
    }

    private static int resolveLevelForTotalXp(final int totalXP) {
        int low = 0;
        int high = 1;
        while (getExperienceForLevel(high) <= totalXP) {
            high *= 2;
        }

        while (low <= high) {
            final int mid = low + ((high - low) / 2);
            final int required = getExperienceForLevel(mid);
            if (required == totalXP) {
                return mid;
            }

            if (required < totalXP) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return Math.max(0, high);
    }

    private static int getXpToNextLevel(final int level) {
        if (level <= 15) {
            return (2 * level) + 7;
        }

        if (level <= 30) {
            return (5 * level) - 38;
        }

        return (9 * level) - 158;
    }

    private static int getExperienceForLevel(final int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }

        if (level <= 31) {
            return (int) Math.floor(2.5D * level * level - 40.5D * level + 360.0D);
        }

        return (int) Math.floor(4.5D * level * level - 162.5D * level + 2220.0D);
    }
}

