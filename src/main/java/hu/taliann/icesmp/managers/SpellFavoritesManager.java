package hu.taliann.icesmp.managers;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tracks each player's favorite spells directly in their PDC as a
 * comma-separated spell-id list under the "favorite_spells" key. Only ever touched
 * from the owning player's own thread (spellbook GUI clicks), so no in-memory
 * UUID-keyed cache — and therefore no session cleanup — is needed.
 */
public final class SpellFavoritesManager {

    private final NamespacedKey favoritesKey;

    public SpellFavoritesManager(final JavaPlugin plugin) {
        this.favoritesKey = new NamespacedKey(plugin, "favorite_spells");
    }

    public boolean isFavorite(final Player player, final String spellId) {
        return spellId != null && favorites(player).contains(spellId.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Toggles a spell's favorite status for the player.
     *
     * @return the spell's new favorite state (true if it is now a favorite)
     */
    public boolean toggle(final Player player, final String spellId) {
        if (player == null || spellId == null || spellId.isBlank()) {
            return false;
        }
        final String id = spellId.toLowerCase(java.util.Locale.ROOT);
        final Set<String> current = favorites(player);
        final boolean nowFavorite;
        if (current.remove(id)) {
            nowFavorite = false;
        } else {
            current.add(id);
            nowFavorite = true;
        }
        save(player, current);
        return nowFavorite;
    }

    /**
     * The player's favorite spell ids, or an empty set if none are marked.
     */
    public Set<String> favorites(final Player player) {
        final Set<String> result = new LinkedHashSet<>();
        if (player == null) {
            return result;
        }
        final String raw = player.getPersistentDataContainer().get(favoritesKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (final String spellId : raw.split(",")) {
            if (!spellId.isBlank()) {
                result.add(spellId.toLowerCase(java.util.Locale.ROOT));
            }
        }
        return result;
    }

    private void save(final Player player, final Set<String> favorites) {
        final PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (favorites.isEmpty()) {
            pdc.remove(favoritesKey);
            return;
        }
        pdc.set(favoritesKey, PersistentDataType.STRING, String.join(",", favorites));
    }
}
