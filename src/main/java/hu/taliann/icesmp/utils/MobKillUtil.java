package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.MinionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** Shared Folia-safe kill eligibility and immutable reward snapshot. */
public final class MobKillUtil {

    private record Claim(String key, long stamp) {
    }

    private static final NamespacedKey SPAWNER_MOB_KEY =
            NamespacedKey.fromString("icesmp:spawner_mob");
    private static final ConcurrentHashMap<String, Long> CLAIMED = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Claim> CLAIM_ORDER = new ConcurrentLinkedQueue<>();
    private static final long CLAIM_TTL_MILLIS = 60_000L;
    private static final int CLAIM_CAP = 4096;

    private MobKillUtil() {
    }

    /** O(expired) queue pruning; never clears active claim locks at the capacity boundary. */
    private static void pruneClaims(final long now) {
        final long cutoff = now - CLAIM_TTL_MILLIS;
        while (true) {
            final Claim oldest = CLAIM_ORDER.peek();
            if (oldest == null || oldest.stamp() >= cutoff) {
                return;
            }
            CLAIM_ORDER.poll();
            CLAIMED.remove(oldest.key(), oldest.stamp());
        }
    }

    public enum RewardKind {
        FAUCET,
        PROGRESSION,
        TRACKING
    }

    /** Immutable kill snapshot created only on the victim's owning region thread. */
    public static final class KillContext {

        private final UUID killerId;
        private final UUID victimId;
        private final EntityType victimType;
        private final UUID victimWorldId;
        private final double victimX;
        private final double victimY;
        private final double victimZ;
        private final long dropSeed;

        private KillContext(final UUID killerId, final LivingEntity victim) {
            this.killerId = killerId;
            this.victimId = victim.getUniqueId();
            this.victimType = victim.getType();
            final Location location = victim.getLocation();
            this.victimWorldId = location.getWorld() == null
                    ? null : location.getWorld().getUID();
            this.victimX = location.getX();
            this.victimY = location.getY();
            this.victimZ = location.getZ();
            this.dropSeed = victimId.getMostSignificantBits()
                    ^ victimId.getLeastSignificantBits();
        }

        public UUID killerId() {
            return killerId;
        }

        public UUID victimId() {
            return victimId;
        }

        public EntityType victimType() {
            return victimType;
        }

        public UUID victimWorldId() {
            return victimWorldId;
        }

        public World victimWorld() {
            return victimWorldId == null ? null : Bukkit.getWorld(victimWorldId);
        }

        public double victimX() {
            return victimX;
        }

        public double victimY() {
            return victimY;
        }

        public double victimZ() {
            return victimZ;
        }

        public Location victimLocation() {
            return new Location(victimWorld(), victimX, victimY, victimZ);
        }

        public long dropSeed() {
            return dropSeed;
        }

        public Random dropRandom(final String channel) {
            return new Random(dropSeed * 31L
                    + (channel == null ? 0 : channel.hashCode()));
        }

        /**
         * One victim/channel can win once. At capacity, new claims are denied fail-closed instead of
         * clearing live locks and reopening every current kill for duplicate rewards.
         */
        public boolean claimOnce(final String channel) {
            final long now = System.currentTimeMillis();
            pruneClaims(now);
            if (CLAIMED.size() >= CLAIM_CAP) {
                return false;
            }
            final String key = victimId + "\u0000" + (channel == null ? "" : channel);
            if (CLAIMED.putIfAbsent(key, now) != null) {
                return false;
            }
            CLAIM_ORDER.add(new Claim(key, now));
            return true;
        }

        public void runOnKiller(final JavaPlugin plugin,
                                final Consumer<Player> action) {
            if (plugin == null || action == null) {
                return;
            }
            final Player killer = Bukkit.getPlayer(killerId);
            if (killer == null) {
                return;
            }
            killer.getScheduler().run(plugin,
                    task -> action.accept(killer), null);
        }
    }

    public static KillContext eligibleKill(final LivingEntity victim,
                                           final RewardKind kind,
                                           final ConfigManager configManager,
                                           final AfkManager afkManager) {
        if (victim == null || kind == null) {
            return null;
        }
        final Player killer = victim.getKiller();
        if (killer == null) {
            return null;
        }
        final UUID killerId = killer.getUniqueId();
        if (excludeMinions(configManager) && MinionManager.isMinionTagged(victim)) {
            return null;
        }
        if (kind != RewardKind.TRACKING) {
            if (requireSurvival(configManager)) {
                if (!GameModeCache.isKnown(killerId)) {
                    GameModeCache.requestRefresh(killerId);
                    return null;
                }
                if (!GameModeCache.isSurvival(killerId)) {
                    return null;
                }
            }
            if (isAfkRewardBlocked(killerId, configManager, afkManager)) {
                return null;
            }
        }
        if (kind == RewardKind.FAUCET && excludeSpawnerMobs(configManager)
                && isSpawnerSpawned(victim)) {
            return null;
        }
        return new KillContext(killerId, victim);
    }

    /** Tracking snapshot without leaking a live killer Player across region threads. */
    public static KillContext eligibleTrackingKill(final LivingEntity victim) {
        if (victim == null || MinionManager.isMinionTagged(victim)) {
            return null;
        }
        final Player killer = victim.getKiller();
        return killer == null ? null
                : new KillContext(killer.getUniqueId(), victim);
    }

    public static boolean isSpawnerSpawned(final Entity entity) {
        return entity != null && SPAWNER_MOB_KEY != null
                && entity.getPersistentDataContainer().has(
                SPAWNER_MOB_KEY, PersistentDataType.BYTE);
    }

    /** Shared direct-reward gate for non-kill interactions and dedicated boss lifecycles. */
    public static boolean isAfkRewardBlocked(final UUID playerId,
                                             final ConfigManager configManager,
                                             final AfkManager afkManager) {
        return playerId != null && afkManager != null && blockAfkRewards(configManager)
                && afkManager.isAfk(playerId);
    }

    private static boolean blockAfkRewards(final ConfigManager configManager) {
        if (configManager == null) {
            return true;
        }
        return configManager.getBoolean("kill-rewards.afk-block",
                configManager.getBoolean("afk.block-rewards", true));
    }

    private static boolean excludeSpawnerMobs(final ConfigManager configManager) {
        return configManager == null || configManager.getBoolean(
                "kill-rewards.exclude-spawner-mobs", true);
    }

    private static boolean excludeMinions(final ConfigManager configManager) {
        return configManager == null || configManager.getBoolean(
                "kill-rewards.exclude-minions", true);
    }

    private static boolean requireSurvival(final ConfigManager configManager) {
        return configManager == null || configManager.getBoolean(
                "kill-rewards.require-survival", true);
    }
}
