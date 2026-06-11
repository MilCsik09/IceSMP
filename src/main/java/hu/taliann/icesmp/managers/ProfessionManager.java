package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.ProfessionType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Manager for player professions (grind-based crafting/gathering progression).
 * Profession data is persisted in player PDC, mirroring the class system in JobManager.
 * A player can have a single profession; switching is admin-only by default.
 */
public final class ProfessionManager {

    public static final int MAX_PROFESSION_LEVEL = 50;

    private final NamespacedKey professionKey;
    private final NamespacedKey professionXpKey;

    public ProfessionManager(final JavaPlugin plugin) {
        this.professionKey = new NamespacedKey(plugin, "profession");
        this.professionXpKey = new NamespacedKey(plugin, "profession_xp");
    }

    public boolean hasProfession(final Player player) {
        return player.getPersistentDataContainer().has(professionKey, PersistentDataType.STRING);
    }

    public ProfessionType getProfession(final Player player) {
        final String rawProfession = player.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
        return ProfessionType.fromId(rawProfession);
    }

    public int getXp(final Player player) {
        return player.getPersistentDataContainer().getOrDefault(professionXpKey, PersistentDataType.INTEGER, 0);
    }

    public int getLevel(final Player player) {
        return getLevel(getXp(player));
    }

    /**
     * Selects a profession for a player who has none yet.
     *
     * @param player the player
     * @param profession the chosen profession
     * @return true if the profession was set
     */
    public boolean selectProfession(final Player player, final ProfessionType profession) {
        if (profession == null || hasProfession(player)) {
            return false;
        }

        setProfession(player, profession);
        return true;
    }

    /**
     * Sets (or overrides) the player's profession and resets the XP. Admin operation.
     *
     * @param player the player
     * @param profession the profession to set, or null to clear it
     */
    public void setProfession(final Player player, final ProfessionType profession) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (profession == null) {
            pdc.remove(professionKey);
            pdc.remove(professionXpKey);
            return;
        }

        pdc.set(professionKey, PersistentDataType.STRING, profession.getId());
        pdc.set(professionXpKey, PersistentDataType.INTEGER, 0);
    }

    public boolean addXp(final Player player, final int amount) {
        if (amount <= 0 || !hasProfession(player)) {
            return false;
        }

        return setXp(player, getXp(player) + amount);
    }

    public boolean setXp(final Player player, final int xp) {
        if (!hasProfession(player)) {
            return false;
        }

        player.getPersistentDataContainer().set(professionXpKey, PersistentDataType.INTEGER, Math.max(0, xp));
        return true;
    }

    /**
     * Awards XP only when the player's active profession matches the given one.
     *
     * @param player the player
     * @param profession the profession performing the activity
     * @param amount the XP amount
     * @return true if XP was granted
     */
    public boolean addXpFor(final Player player, final ProfessionType profession, final int amount) {
        if (profession == null || getProfession(player) != profession) {
            return false;
        }

        return addXp(player, amount);
    }

    public void cleanup(final UUID playerId) {
        // Profession data is persisted in PDC and must survive reconnects.
    }

    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }

    private int getLevel(final int xp) {
        return Math.min(MAX_PROFESSION_LEVEL, (Math.max(0, xp) / 100) + 1);
    }
}
