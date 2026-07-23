package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.JobType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * E25 + E32 — kaszt-erőforrás pool-bónuszok, egyetlen szál-biztos lookupban:
 * <ul>
 *   <li><b>Pakt (Boszorkánymester):</b> a rituálé-oltár pakt-ceremóniája tartós
 *       +20% max Lélekerőt ír a player-PDC-be; a gyorsítótár join-kor töltődik
 *       (a játékos SAJÁT régió-szálán — a ResourceManager.max() bármely szálról
 *       csak a concurrent cache-t olvassa, PDC-t soha).</li>
 *   <li><b>Sárkánytojás-töredék (Sárkányidéző):</b> amíg a relikvia az övé,
 *       az Eszencia-pool +10% (a relikvia-tulajdon a RelicManager concurrent
 *       nyilvántartásából, a kaszt a JobManager cache-éből olvasható).</li>
 * </ul>
 */
public final class ResourceBonusService implements Listener {

    private final ConfigManager configManager;
    private final JobManager jobManager;
    private final RelicManager relicManager;
    private final NamespacedKey paktKey;
    private final Map<UUID, Double> paktCache = new ConcurrentHashMap<>();
    /** Utolsó ismert „Sárkányidéző-e” flag — a PDC-t CSAK a játékos saját régió-szálán
     * olvassuk (isOwnedByCurrentRegion), idegen szálról ez a cache válaszol. */
    private final Map<UUID, Boolean> evokerCache = new ConcurrentHashMap<>();

    public ResourceBonusService(final JavaPlugin plugin, final ConfigManager configManager,
                                final JobManager jobManager, final RelicManager relicManager) {
        this.configManager = configManager;
        this.jobManager = jobManager;
        this.relicManager = relicManager;
        this.paktKey = new NamespacedKey(plugin, "warlock_pakt");
    }

    /** A max-pool szorzója az adott játékosra (ResourceManager.max() hívja). */
    public double maxMultiplier(final UUID playerId) {
        double multiplier = paktCache.getOrDefault(playerId, 1.0D);
        final String relicId = configManager.getString("pakt.dragon-relic-id", "sarkany_tojas");
        final hu.taliann.icesmp.relics.RelicOwnership ownership = relicManager.getOwnership(relicId);
        if (ownership != null && playerId.equals(ownership.owner()) && isEvoker(playerId)) {
            multiplier *= 1.0D + Math.max(0.0D,
                    configManager.getDouble("pakt.dragon-essence-bonus-percent", 10.0D)) / 100.0D;
        }
        return multiplier;
    }

    /**
     * Folia-biztos kaszt-lekérdezés: a max() bármely régió-szálról hívható (pl. a
     * sebzett fél szálán, projectile-harcban), a MÁSIK játékos PDC-jét ott tilos
     * olvasni. Saját régión élőben olvasunk és frissítjük a cache-t; idegenről az
     * utolsó ismert érték megy (a következő saját-szálas hívás úgyis frissíti).
     */
    private boolean isEvoker(final UUID playerId) {
        final Player online = org.bukkit.Bukkit.getPlayer(playerId);
        if (online != null && org.bukkit.Bukkit.isOwnedByCurrentRegion(online)) {
            final boolean evoker = jobManager.getPrimaryJob(online) == JobType.EVOKER;
            evokerCache.put(playerId, evoker);
            return evoker;
        }
        return evokerCache.getOrDefault(playerId, Boolean.FALSE);
    }

    /** Megvan-e már a pakt (nem halmozható). */
    public boolean hasPakt(final Player player) {
        return player.getPersistentDataContainer().has(paktKey, PersistentDataType.DOUBLE);
    }

    /** A pakt megkötése (a RitualManager hívja, a játékos saját régió-szálán). */
    public void sealPakt(final Player player) {
        final double multiplier = 1.0D + Math.max(0.0D,
                configManager.getDouble("pakt.bonus-percent", 20.0D)) / 100.0D;
        player.getPersistentDataContainer().set(paktKey, PersistentDataType.DOUBLE, multiplier);
        paktCache.put(player.getUniqueId(), multiplier);
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Double stored = event.getPlayer().getPersistentDataContainer()
                .get(paktKey, PersistentDataType.DOUBLE);
        if (stored != null) {
            paktCache.put(event.getPlayer().getUniqueId(), stored);
        }
        // A join a játékos saját régió-szálán fut — a kaszt-cache itt biztonsággal töltődik.
        evokerCache.put(event.getPlayer().getUniqueId(),
                jobManager.getPrimaryJob(event.getPlayer()) == JobType.EVOKER);
    }

    @EventHandler
    public void onQuit(final org.bukkit.event.player.PlayerQuitEvent event) {
        paktCache.remove(event.getPlayer().getUniqueId());
        evokerCache.remove(event.getPlayer().getUniqueId());
    }
}
