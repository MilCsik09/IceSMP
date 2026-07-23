package hu.taliann.icesmp.managers;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * E1 — Nekromanta lélek-kovácsolás: a lélekszilánk tartós, presztízs-befektetése
 * a minion-seregbe. 3 ág (ELET/SEBZES/LETSZAM), áganként 5 rang, növekvő
 * szilánk-árral; a rangok player-PDC-ben élnek, a SummonMinionsSpell cast-kor
 * olvassa a szorzókat (statikus híd — LootTable-minta). A Létszám-ág extra
 * minion-slotokat ad ([0,0,1,1,2,3] — max +3 az alap fölé, a spec cap-je).
 * Folia: minden művelet a játékos saját régió-szálán fut (parancs/cast).
 */
public final class SoulforgeManager {

    public enum Branch { ELET, SEBZES, LETSZAM }

    public static final int MAX_RANK = 5;
    private static final int[] EXTRA_SLOTS = {0, 0, 1, 1, 2, 3};

    private final ConfigManager configManager;
    private final SoulShardManager soulShardManager;
    private final java.util.Map<Branch, NamespacedKey> keys = new java.util.EnumMap<>(Branch.class);

    public SoulforgeManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final SoulShardManager soulShardManager) {
        this.configManager = configManager;
        this.soulShardManager = soulShardManager;
        for (final Branch branch : Branch.values()) {
            keys.put(branch, new NamespacedKey(plugin, "soulforge_" + branch.name().toLowerCase(Locale.ROOT)));
        }
    }

    public int getRank(final Player player, final Branch branch) {
        return Math.max(0, Math.min(MAX_RANK, player.getPersistentDataContainer()
                .getOrDefault(keys.get(branch), PersistentDataType.INTEGER, 0)));
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
}
