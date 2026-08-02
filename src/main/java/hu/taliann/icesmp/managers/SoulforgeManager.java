package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Restart-durable, atomic Profile v2 Soulforge upgrades. */
public final class SoulforgeManager implements PlayerStateCleanup {
    public enum Branch { ELET, SEBZES, LETSZAM }
    public static final int MAX_RANK = 5;
    private static final int[] EXTRA_SLOTS = {0, 0, 1, 1, 2, 3};

    private final ConfigManager configManager;
    private volatile ClassSpecProfileGateway profileGateway;

    public SoulforgeManager(final org.bukkit.plugin.java.JavaPlugin plugin,
                            final ConfigManager configManager,
                            final SoulShardManager soulShardManager) {
        Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        Objects.requireNonNull(soulShardManager, "soulShardManager");
    }

    public void setProfileGateway(final ClassSpecProfileGateway gateway) {
        profileGateway = Objects.requireNonNull(gateway, "profileGateway");
    }

    private ClassSpecProfileGateway gateway() {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null) throw new IllegalStateException("Profile v2 gateway is not initialized");
        return gateway;
    }

    public int getRank(final Player player, final Branch branch) {
        if (player == null || branch == null) return 0;
        final String key = "necromancer.soulforge." + branch.name().toLowerCase(Locale.ROOT);
        try {
            final int rank = gateway().activeMechanic(player.getUniqueId(), key)
                    .map(Integer::parseInt).orElse(0);
            if (rank < 0 || rank > MAX_RANK) {
                gateway().blockSession(player.getUniqueId(), "Invalid durable Soulforge rank");
                return 0;
            }
            return rank;
        } catch (final NumberFormatException invalid) {
            gateway().blockSession(player.getUniqueId(), "Invalid durable Soulforge rank");
            return 0;
        }
    }

    /** No legacy PDC snapshot is supported in greenfield mode. */
    public java.util.Map<Branch, Integer> snapshotPersistedRanks(final Player player) {
        final java.util.EnumMap<Branch, Integer> ranks = new java.util.EnumMap<>(Branch.class);
        for (final Branch branch : Branch.values()) ranks.put(branch, getRank(player, branch));
        return java.util.Map.copyOf(ranks);
    }

    public int nextCost(final Player player, final Branch branch) {
        final int rank = getRank(player, branch);
        if (rank >= MAX_RANK) return -1;
        final List<Integer> configured = configManager.getConfiguration() == null ? List.of()
                : configManager.getConfiguration().getIntegerList("soulforge.rank-costs");
        final int cost = configured.size() >= MAX_RANK ? configured.get(rank)
                : switch (rank) { case 0 -> 5; case 1 -> 8; case 2 -> 12; case 3 -> 18; default -> 25; };
        if (cost <= 0) throw new IllegalStateException("Soulforge rank cost must be positive");
        return cost;
    }

    /** Legacy direct upgrade is disabled. */
    public String upgrade(final Player player, final Branch branch) {
        return "soulforge-profile-v2-async-required";
    }

    public CompletionStage<String> upgradeV2(final Player player, final Branch branch) {
        if (player == null || branch == null || !configManager.getBoolean("soulforge.enabled", true)) {
            return CompletableFuture.completedFuture("soulforge-disabled");
        }
        final ClassSpecProfileGateway gateway = gateway();
        if (!gateway.isSessionReady(player.getUniqueId())
                || gateway.activeSpecId(player.getUniqueId()).filter("necromancer"::equals).isEmpty()) {
            return CompletableFuture.completedFuture("soulforge-necromancer-only");
        }
        final int cost = nextCost(player, branch);
        if (cost < 0) return CompletableFuture.completedFuture("soulforge-max");
        final long revision = gateway.diagnostic(player.getUniqueId()).revision();
        final String operationId = "soulforge:" + player.getUniqueId() + ':'
                + branch.name().toLowerCase(Locale.ROOT) + ':' + revision;
        return upgradeV2(player, branch, cost, operationId);
    }

    /** Explicit operation-id entry used by deterministic retry tests and recovery paths. */
    public CompletionStage<String> upgradeV2(final Player player, final Branch branch,
                                              final int cost, final String operationId) {
        final ClassSpecProfileGateway gateway = gateway();
        if (player == null || branch == null || cost <= 0 || operationId == null || operationId.isBlank()) {
            return CompletableFuture.completedFuture("soulforge-invalid-operation");
        }
        return gateway.incrementSoulforge(player.getUniqueId(),
                        branch.name().toLowerCase(Locale.ROOT), cost, operationId)
                .handle((result, failure) -> {
                    if (failure != null) return "soulforge-persistence-failed";
                    if (result.committed() || result.status() == ProfileMutationResult.Status.NO_CHANGE) return null;
                    if (result.status() == ProfileMutationResult.Status.RUNTIME_EFFECT_FAILED) {
                        gateway.blockSession(player.getUniqueId(),
                                "Soulforge durable commit requires runtime reconciliation: " + result.detail());
                        return "soulforge-runtime-failed";
                    }
                    return result.detail().contains("insufficient") ? "soulforge-poor"
                            : "soulforge-persistence-failed";
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

    public int extraSlots(final Player player) { return EXTRA_SLOTS[getRank(player, Branch.LETSZAM)]; }
    @Override public void clearPlayerState(final UUID playerId) { }
}
