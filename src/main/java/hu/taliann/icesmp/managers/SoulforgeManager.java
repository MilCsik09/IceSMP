package hu.taliann.icesmp.managers;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * E1 — Nekromanta lélek-kovácsolás: a lélekszilánk tartós, presztízs-befektetése
 * a minion-seregbe. 3 ág (ELET/SEBZES/LETSZAM), áganként 5 rang, növekvő
 * szilánk-árral; a rangok player-PDC-ben élnek, a SummonMinionsSpell cast-kor
 * olvassa a szorzókat (statikus híd — LootTable-minta). A Létszám-ág extra
 * minion-slotokat ad ([0,0,1,1,2,3] — max +3 az alap fölé, a spec cap-je).
 * Folia: minden művelet a játékos saját régió-szálán fut (parancs/cast).
 */
public final class SoulforgeManager implements hu.taliann.icesmp.session.PlayerStateCleanup {

    public enum Branch { ELET, SEBZES, LETSZAM }

    public static final int MAX_RANK = 5;
    private static final int[] EXTRA_SLOTS = {0, 0, 1, 1, 2, 3};

    private final ConfigManager configManager;
    private final JavaPlugin plugin;
    private final SoulShardManager soulShardManager;
    private final java.util.Map<Branch, NamespacedKey> keys = new java.util.EnumMap<>(Branch.class);
    private volatile hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway profileGateway;
    private final java.util.Map<java.util.UUID, java.util.Map<String,
            java.util.concurrent.CompletableFuture<String>>> upgradeReceipts =
            new java.util.concurrent.ConcurrentHashMap<>();

    public SoulforgeManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final SoulShardManager soulShardManager) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.configManager = configManager;
        this.soulShardManager = soulShardManager;
        for (final Branch branch : Branch.values()) {
            keys.put(branch, new NamespacedKey(plugin, "soulforge_" + branch.name().toLowerCase(Locale.ROOT)));
        }
    }

    public void setProfileGateway(
            final hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway profileGateway) {
        this.profileGateway = java.util.Objects.requireNonNull(profileGateway, "profileGateway");
    }

    public boolean isProfileV2Enabled() {
        final var gateway = profileGateway;
        return gateway != null && gateway.enabled();
    }

    public int getRank(final Player player, final Branch branch) {
        final var gateway = profileGateway;
        if (gateway != null && gateway.enabled()) {
            final String key = "necromancer.soulforge." + branch.name().toLowerCase(Locale.ROOT);
            try {
                return gateway.activeMechanic(player.getUniqueId(), key)
                        .map(Integer::parseInt).map(rank -> Math.max(0, Math.min(MAX_RANK, rank)))
                        .orElse(0);
            } catch (final NumberFormatException invalid) {
                return 0;
            }
        }
        return Math.max(0, Math.min(MAX_RANK, player.getPersistentDataContainer()
                .getOrDefault(keys.get(branch), PersistentDataType.INTEGER, 0)));
    }

    /**
     * Read-only migration view of the exact persisted ranks. Unlike
     * {@link #getRank(Player, Branch)}, values are not clamped so corrupt or
     * future legacy data can be sent to migration review instead of silently
     * changing it.
     */
    public Map<Branch, Integer> snapshotPersistedRanks(final Player player) {
        final Map<Branch, Integer> ranks = new EnumMap<>(Branch.class);
        final var pdc = player.getPersistentDataContainer();
        for (final Branch branch : Branch.values()) {
            final NamespacedKey key = keys.get(branch);
            if (pdc.has(key, PersistentDataType.INTEGER)) {
                ranks.put(branch, pdc.getOrDefault(key, PersistentDataType.INTEGER, 0));
            }
        }
        return Map.copyOf(ranks);
    }

    /** A következő rang szilánk-ára (rang-index szerint a config-listából). */
    public int nextCost(final Player player, final Branch branch) {
        final int rank = getRank(player, branch);
        if (rank >= MAX_RANK) {
            return -1;
        }
        final List<Integer> costs = configManager.getConfiguration() == null ? List.of()
                : configManager.getConfiguration().getIntegerList("soulforge.rank-costs");
        if (costs.size() >= MAX_RANK) {
            return costs.get(rank);
        }
        return switch (rank) { case 0 -> 5; case 1 -> 8; case 2 -> 12; case 3 -> 18; default -> 25; };
    }

    /** Fejlesztés; hibakulcs vagy null. A szilánk-levonás a SoulShardManageren át. */
    public String upgrade(final Player player, final Branch branch) {
        final var gateway = profileGateway;
        if (gateway != null && gateway.enabled()) {
            return "soulforge-profile-v2-async-required";
        }
        if (!configManager.getBoolean("soulforge.enabled", true)) {
            return "soulforge-disabled";
        }
        final int cost = nextCost(player, branch);
        if (cost < 0) {
            return "soulforge-max";
        }
        if (!soulShardManager.spendShards(player, cost)) {
            return "soulforge-poor";
        }
        player.getPersistentDataContainer().set(keys.get(branch), PersistentDataType.INTEGER,
                getRank(player, branch) + 1);
        return null;
    }

    /** Revision/CAS protected Profile v2 upgrade with exact shard refund on failure. */
    public java.util.concurrent.CompletionStage<String> upgradeV2(final Player player,
                                                                  final Branch branch) {
        final var gateway = profileGateway;
        if (gateway == null || !gateway.enabled()
                || !gateway.activeSpecId(player.getUniqueId()).filter("necromancer"::equals).isPresent()) {
            return java.util.concurrent.CompletableFuture.completedFuture("soulforge-necromancer-only");
        }
        final int cost = nextCost(player, branch);
        if (cost < 0) {
            return java.util.concurrent.CompletableFuture.completedFuture("soulforge-max");
        }
        final long revision = gateway.diagnostic(player.getUniqueId()).revision();
        final String operationId = "soulforge:" + player.getUniqueId() + ":"
                + branch.name() + ":" + revision;
        final var playerReceipts = upgradeReceipts.computeIfAbsent(player.getUniqueId(),
                ignored -> new java.util.concurrent.ConcurrentHashMap<>());
        return playerReceipts.computeIfAbsent(operationId, ignored -> {
            final java.util.concurrent.CompletableFuture<String> completion =
                    new java.util.concurrent.CompletableFuture<>();
            if (!soulShardManager.spendShards(player, cost)) {
                completion.complete("soulforge-poor");
                return completion;
            }
            gateway.incrementSoulforge(player.getUniqueId(), branch.name(), operationId)
                    .whenComplete((result, failure) -> {
                        if (failure == null && result != null && result.durableMutationApplied()) {
                            if (result.committed()) {
                                completion.complete(null);
                            } else {
                                gateway.blockSession(player.getUniqueId(),
                                        "Soulforge rank committed, but runtime reconciliation failed: "
                                                + result.detail());
                                completion.complete("soulforge-runtime-failed");
                            }
                            return;
                        }
                        player.getScheduler().run(plugin, task -> {
                                    try {
                                        soulShardManager.addShards(player, cost);
                                        completion.complete("soulforge-persistence-failed");
                                    } catch (final Throwable refundFailure) {
                                        gateway.blockSession(player.getUniqueId(),
                                                "Soulforge shard refund failed after Profile v2 mutation failure");
                                        completion.complete("soulforge-refund-failed");
                                    }
                                }, () -> {
                                    gateway.blockSession(player.getUniqueId(),
                                            "Soulforge shard refund scheduler rejected");
                                    completion.complete("soulforge-refund-failed");
                                });
                    });
            return completion;
        });
    }

    public double healthMultiplier(final Player player) {
        return 1.0D + getRank(player, Branch.ELET)
                * Math.max(0.0D, configManager.getDouble("soulforge.health-per-rank", 0.08D));
    }

    public double damageMultiplier(final Player player) {
        return 1.0D + getRank(player, Branch.SEBZES)
                * Math.max(0.0D, configManager.getDouble("soulforge.damage-per-rank", 0.06D));
    }

    public int extraSlots(final Player player) {
        return EXTRA_SLOTS[getRank(player, Branch.LETSZAM)];
    }

    @Override
    public void clearPlayerState(final java.util.UUID playerId) {
        upgradeReceipts.remove(playerId);
    }
}
