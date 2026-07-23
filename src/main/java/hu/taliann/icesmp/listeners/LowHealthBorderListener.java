package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alacsony-HP piros vignetta (P8e): ha a játékos élete a küszöb alá esik, a képernyő
 * szélén vörös figyelmeztető köd jelenik meg — a vanília „világperem-vészjel" per-player
 * WorldBorderrel kihasználva. A perem MAGA nincs a közelben (óriási méret, központ 0,0),
 * csak a hatalmas warning-distance kelti a vignettát, tehát a JÁTÉKMENETRE nincs hatása.
 *
 * <p>Tisztán vizuális réteg — a HP-skálázás mester-kapcsolójától (health.enabled) FÜGGETLEN;
 * saját kulcsa van ({@code hud.low-hp-vignette.*}), élőben olvasva. Kikapcsolható.
 *
 * <p>Állapot-követés: csak a küszöb ÁTLÉPÉSEKOR küldünk perem-csomagot (nincs villódzás/spam).
 *
 * <p>Folia: a sebzés/gyógyulás-event a játékos régió-szálán fut, a {@code setWorldBorder} a
 * saját entitását érinti — nincs szükség hopra; az 1-tick késleltetett kiértékelés is a
 * játékos saját ütemezőjén megy.
 */
public final class LowHealthBorderListener implements Listener, PlayerStateCleanup {

    private static final double BORDER_SIZE = 59_999_968.0D;
    private static final int WARNING_BLOCKS = Integer.MAX_VALUE;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final java.util.Set<UUID> active = ConcurrentHashMap.newKeySet();

    public LowHealthBorderListener(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            scheduleEvaluate(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegain(final EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) {
            scheduleEvaluate(player);
        }
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        // Újracsatlakozáskor a per-player perem nem marad meg, de a belső állapotot tisztítjuk.
        active.remove(event.getPlayer().getUniqueId());
    }

    /** A sebzés/gyógyulás az event ELŐTT áll be a HP-ba — 1 tick múlva olvassuk a végleges életet. */
    private void scheduleEvaluate(final Player player) {
        player.getScheduler().runDelayed(plugin, task -> evaluate(player), null, 1L);
    }

    private void evaluate(final Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        if (!configManager.getBoolean("hud.low-hp-vignette.enabled", true)) {
            clear(player);
            return;
        }
        final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null || maxHealth.getValue() <= 0.0D) {
            return;
        }
        final double percent = player.getHealth() / maxHealth.getValue() * 100.0D;
        final double threshold = configManager.getDouble("hud.low-hp-vignette.threshold-percent", 30.0D);
        if (percent <= threshold) {
            apply(player);
        } else {
            clear(player);
        }
    }

    private void apply(final Player player) {
        if (!active.add(player.getUniqueId())) {
            return;
        }
        final WorldBorder border = plugin.getServer().createWorldBorder();
        border.setCenter(0.0D, 0.0D);
        border.setSize(BORDER_SIZE);
        border.setWarningDistance(WARNING_BLOCKS);
        player.setWorldBorder(border);
    }

    private void clear(final Player player) {
        if (!active.remove(player.getUniqueId())) {
            return;
        }
        // Vissza a világ VALÓDI peremére (null a per-player override-ot törli).
        player.setWorldBorder(player.getWorld().getWorldBorder());
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        active.remove(playerId);
    }
}
