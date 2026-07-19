package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Spell mastery / ranks (ROADMAP phase 3): players spend faction currency to
 * rank up an unlocked spell, which lowers its effective cooldown (a clean,
 * non-invasive "spell strength" that the cast pipeline applies). Ranks are
 * stored per player in PDC as "spellId:rank,..." (mirrors the talent storage).
 */
public final class SpellMasteryManager {

    public enum UpgradeResult { SUCCESS, MAX_RANK, INSUFFICIENT_FUNDS }

    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final NamespacedKey masteryKey;

    public SpellMasteryManager(final JavaPlugin plugin, final ConfigManager configManager,
                               final CurrencyManager currencyManager, final FactionManager factionManager) {
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.masteryKey = new NamespacedKey(plugin, "spell_mastery");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("spells.mastery.enabled", true);
    }

    public int getMaxRank() {
        return Math.max(0, configManager.getInt("spells.mastery.max-rank", 5));
    }

    public int getRank(final Player player, final String spellId) {
        if (player == null || spellId == null) {
            return 0;
        }
        return getRanks(player).getOrDefault(spellId.toLowerCase(Locale.ROOT), 0);
    }

    /**
     * The cooldown multiplier for the player's mastery of a spell:
     * {@code 1 - rank * reduction}, clamped to a configured floor.
     *
     * @param player the caster
     * @param spellId the spell id
     * @return a value in (0, 1]; 1.0 = no reduction
     */
    public double getCooldownMultiplier(final Player player, final String spellId) {
        if (!isEnabled()) {
            return 1.0D;
        }
        final double perRank = Math.max(0.0D, configManager.getDouble("spells.mastery.cooldown-reduction-per-rank", 0.08D));
        final double floor = Math.max(0.1D, Math.min(1.0D, configManager.getDouble("spells.mastery.min-cooldown-multiplier", 0.5D)));
        return Math.max(floor, 1.0D - (getRank(player, spellId) * perRank));
    }

    /**
     * The power multiplier for the player's mastery of a spell:
     * {@code 1 + rank * power-per-rank}, capped at a configured maximum. Scales
     * the spell's offensive output (damage, self-heal, effect duration); 1.0 =
     * no boost.
     *
     * @param player the caster
     * @param spellId the spell id
     * @return a value >= 1.0
     */
    public double getPowerMultiplier(final Player player, final String spellId) {
        if (!isEnabled()) {
            return 1.0D;
        }
        final double perRank = Math.max(0.0D, configManager.getDouble("spells.mastery.power-per-rank", 0.05D));
        final double cap = Math.max(1.0D, configManager.getDouble("spells.mastery.max-power-multiplier", 1.5D));
        return Math.min(cap, 1.0D + (getRank(player, spellId) * perRank));
    }

    /** The currency cost of the player's next rank for a spell. */
    public long getUpgradeCost(final Player player, final String spellId) {
        final long base = Math.max(1L, configManager.getLong("spells.mastery.upgrade-base-cost", 50L));
        return base * (getRank(player, spellId) + 1L);
    }

    public UpgradeResult upgrade(final Player player, final String spellId) {
        final String normalized = spellId.toLowerCase(Locale.ROOT);
        if (getRank(player, normalized) >= getMaxRank()) {
            return UpgradeResult.MAX_RANK;
        }

        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        final CurrencyType currency = CurrencyType.fromFactionType(faction);
        final long cost = getUpgradeCost(player, normalized);
        // Atomic deduct (no get+set race): a concurrent balance write can't be lost.
        if (!currencyManager.deductFromBalance(player.getUniqueId(), currency, cost)) {
            return UpgradeResult.INSUFFICIENT_FUNDS;
        }
        final Map<String, Integer> ranks = getRanks(player);
        ranks.merge(normalized, 1, Integer::sum);
        saveRanks(player, ranks);
        return UpgradeResult.SUCCESS;
    }

    private Map<String, Integer> getRanks(final Player player) {
        final Map<String, Integer> ranks = new LinkedHashMap<>();
        final String raw = player.getPersistentDataContainer().get(masteryKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return ranks;
        }
        for (final String token : raw.split(",")) {
            final String[] parts = token.split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                final int rank = Integer.parseInt(parts[1].trim());
                if (rank > 0) {
                    ranks.put(parts[0].trim().toLowerCase(Locale.ROOT), rank);
                }
            } catch (final NumberFormatException ignored) {
            }
        }
        return ranks;
    }

    private void saveRanks(final Player player, final Map<String, Integer> ranks) {
        if (ranks.isEmpty()) {
            player.getPersistentDataContainer().remove(masteryKey);
            return;
        }
        final StringBuilder serialized = new StringBuilder();
        for (final Map.Entry<String, Integer> entry : ranks.entrySet()) {
            if (!serialized.isEmpty()) {
                serialized.append(',');
            }
            serialized.append(entry.getKey()).append(':').append(entry.getValue());
        }
        player.getPersistentDataContainer().set(masteryKey, PersistentDataType.STRING, serialized.toString());
    }
}
