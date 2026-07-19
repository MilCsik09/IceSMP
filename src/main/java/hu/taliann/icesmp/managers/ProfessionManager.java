package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.ProfessionCategory;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WoW-style profession manager: every player may select ONE gathering and ONE
 * crafting primary profession, while the secondary professions (fisherman,
 * cook) are available to everyone automatically. XP is tracked per profession
 * in player PDC, and levels follow a progressive curve (each level costs more
 * XP than the previous one, configurable under 'professions.leveling').
 */
public final class ProfessionManager implements PlayerStateCleanup {

    public static final int MAX_PROFESSION_LEVEL = 50;

    private final ConfigManager configManager;
    private final NamespacedKey gatheringKey;
    private final NamespacedKey craftingKey;
    private final NamespacedKey learnedRecipesKey;
    private final Map<ProfessionType, NamespacedKey> xpKeys = new EnumMap<>(ProfessionType.class);

    public ProfessionManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.configManager = configManager;
        this.gatheringKey = new NamespacedKey(plugin, "profession_gathering");
        this.craftingKey = new NamespacedKey(plugin, "profession_crafting");
        this.learnedRecipesKey = new NamespacedKey(plugin, "learned_recipes");
        for (final ProfessionType professionType : ProfessionType.values()) {
            xpKeys.put(professionType, new NamespacedKey(plugin, "profession_xp_" + professionType.getId()));
        }
    }

    /**
     * Gets the player's selected primary profession in the given category.
     *
     * @param player the player
     * @param category GATHERING or CRAFTING (SECONDARY has no slot)
     * @return the selected profession, or null if the slot is empty
     */
    public ProfessionType getProfession(final Player player, final ProfessionCategory category) {
        final NamespacedKey slotKey = slotKey(category);
        if (slotKey == null) {
            return null;
        }

        return ProfessionType.fromId(player.getPersistentDataContainer().get(slotKey, PersistentDataType.STRING));
    }

    /**
     * Checks whether the player practices the given profession:
     * secondary professions belong to everyone, primaries must occupy their slot.
     *
     * @param player the player
     * @param professionType the profession
     * @return true if the profession is active for the player
     */
    public boolean hasProfession(final Player player, final ProfessionType professionType) {
        if (professionType == null) {
            return false;
        }

        if (professionType.getCategory() == ProfessionCategory.SECONDARY) {
            return true;
        }

        return getProfession(player, professionType.getCategory()) == professionType;
    }

    /**
     * Gets every profession the player actively practices:
     * the selected gathering + crafting primaries plus all secondaries.
     *
     * @param player the player
     * @return the active professions
     */
    public List<ProfessionType> getActiveProfessions(final Player player) {
        final List<ProfessionType> active = new ArrayList<>();
        final ProfessionType gathering = getProfession(player, ProfessionCategory.GATHERING);
        if (gathering != null) {
            active.add(gathering);
        }

        final ProfessionType crafting = getProfession(player, ProfessionCategory.CRAFTING);
        if (crafting != null) {
            active.add(crafting);
        }

        for (final ProfessionType professionType : ProfessionType.values()) {
            if (professionType.getCategory() == ProfessionCategory.SECONDARY) {
                active.add(professionType);
            }
        }

        return active;
    }

    /**
     * Selects a primary profession into its empty category slot.
     *
     * @param player the player
     * @param professionType the chosen profession (must be GATHERING or CRAFTING)
     * @return true if the profession was set
     */
    public boolean selectProfession(final Player player, final ProfessionType professionType) {
        if (professionType == null || professionType.getCategory() == ProfessionCategory.SECONDARY) {
            return false;
        }

        if (getProfession(player, professionType.getCategory()) != null) {
            return false;
        }

        player.getPersistentDataContainer().set(slotKey(professionType.getCategory()),
                PersistentDataType.STRING, professionType.getId());
        return true;
    }

    /**
     * Sets (or overrides) a primary profession slot. Admin operation; XP is preserved
     * per profession, so re-learning a dropped profession resumes its old level.
     *
     * @param player the player
     * @param professionType the profession to set (its category determines the slot)
     */
    public void setProfession(final Player player, final ProfessionType professionType) {
        if (professionType == null || professionType.getCategory() == ProfessionCategory.SECONDARY) {
            return;
        }

        player.getPersistentDataContainer().set(slotKey(professionType.getCategory()),
                PersistentDataType.STRING, professionType.getId());
    }

    /**
     * Clears a primary profession slot. Admin operation.
     *
     * @param player the player
     * @param category the slot to clear (GATHERING or CRAFTING)
     */
    public void clearProfession(final Player player, final ProfessionCategory category) {
        final NamespacedKey slotKey = slotKey(category);
        if (slotKey != null) {
            player.getPersistentDataContainer().remove(slotKey);
        }
    }

    public int getXp(final Player player, final ProfessionType professionType) {
        if (professionType == null) {
            return 0;
        }

        return player.getPersistentDataContainer().getOrDefault(xpKeys.get(professionType), PersistentDataType.INTEGER, 0);
    }

    public boolean setXp(final Player player, final ProfessionType professionType, final int xp) {
        if (professionType == null) {
            return false;
        }

        player.getPersistentDataContainer().set(xpKeys.get(professionType), PersistentDataType.INTEGER, Math.max(0, xp));
        return true;
    }

    public boolean addXp(final Player player, final ProfessionType professionType, final int amount) {
        if (amount <= 0 || professionType == null) {
            return false;
        }

        return setXp(player, professionType, getXp(player, professionType) + amount);
    }

    /**
     * Awards activity XP only when the player actively practices the profession.
     *
     * @param player the player
     * @param professionType the profession performing the activity
     * @param amount the XP amount
     * @return true if XP was granted
     */
    public boolean addXpFor(final Player player, final ProfessionType professionType, final int amount) {
        if (!hasProfession(player, professionType)) {
            return false;
        }

        return addXp(player, professionType, amount);
    }

    /**
     * Gets the profession level on a progressive curve:
     * level n -> n+1 costs base-xp + (n-1) * increment-per-level XP.
     *
     * @param player the player
     * @param professionType the profession
     * @return the level (1..MAX_PROFESSION_LEVEL)
     */
    public int getLevel(final Player player, final ProfessionType professionType) {
        final int baseXp = Math.max(1, configManager.getInt("professions.leveling.base-xp", 100));
        final int increment = Math.max(0, configManager.getInt("professions.leveling.increment-per-level", 15));

        int level = 1;
        int remaining = Math.max(0, getXp(player, professionType));
        while (level < MAX_PROFESSION_LEVEL) {
            final int levelCost = baseXp + ((level - 1) * increment);
            if (remaining < levelCost) {
                break;
            }
            remaining -= levelCost;
            level++;
        }

        return level;
    }

    /** Whether the player has learned a blueprint-gated recipe (level-gated recipes need no store). */
    public boolean hasLearnedRecipe(final Player player, final String recipeId) {
        if (recipeId == null) {
            return false;
        }
        final String raw = player.getPersistentDataContainer().getOrDefault(learnedRecipesKey, PersistentDataType.STRING, "");
        for (final String id : raw.split(",")) {
            if (id.equalsIgnoreCase(recipeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records a learned blueprint recipe on the player.
     *
     * @return true if it was newly learned, false if already known
     */
    public boolean learnRecipe(final Player player, final String recipeId) {
        if (recipeId == null || recipeId.isBlank() || hasLearnedRecipe(player, recipeId)) {
            return false;
        }
        final String raw = player.getPersistentDataContainer().getOrDefault(learnedRecipesKey, PersistentDataType.STRING, "");
        final String updated = raw.isBlank() ? recipeId : raw + "," + recipeId;
        player.getPersistentDataContainer().set(learnedRecipesKey, PersistentDataType.STRING, updated);
        return true;
    }

    public void cleanup(final UUID playerId) {
        // Profession data is persisted in PDC and must survive reconnects.
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }

    private NamespacedKey slotKey(final ProfessionCategory category) {
        return switch (category) {
            case GATHERING -> gatheringKey;
            case CRAFTING -> craftingKey;
            case SECONDARY -> null;
        };
    }
}
