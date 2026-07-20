package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.integration.SpyDisguise;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * G14 — kém-mechanika: a Suttogók álca-motívuma nyílt taktikai eszközként.
 * `/kem <célfrakció>`: rövid, hamis nevű játékos-álca (SpyDisguise híd) —
 * INFORMÁCIÓS játék, nem harci előny: raid harci szakaszában nem indítható,
 * és bármilyen PvP-interakció (adott VAGY kapott találat) azonnal lebuktat.
 * Az álca nem ment fel a bűn-szabályok alól (lopás/behatolás ugyanúgy számít).
 * Cooldown player-PDC-ben; az aktív álcák konkurrens mapben (kilépéskor takarítva).
 * Folia: minden művelet a játékos saját régió-szálán fut (parancs/damage-event).
 */
public final class SpyManager implements PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final RaidManager raidManager;
    private final MessageManager messageManager;
    private final NamespacedKey cooldownKey;
    private final Map<UUID, Long> activeUntil = new ConcurrentHashMap<>();

    public SpyManager(final JavaPlugin plugin, final ConfigManager configManager,
                      final RaidManager raidManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.raidManager = raidManager;
        this.messageManager = messageManager;
        this.cooldownKey = new NamespacedKey(plugin, "spy_cooldown");
    }

    public boolean isSpying(final UUID playerId) {
        final Long until = activeUntil.get(playerId);
        return until != null && System.currentTimeMillis() < until;
    }

    /** Álca indítása; hibakulcs vagy null. A játékos saját régió-szálán fut. */
    public String start(final Player player, final FactionType targetFaction) {
        if (!configManager.getBoolean("spy.enabled", true)) {
            return "spy-disabled";
        }
        if (!SpyDisguise.isAvailable()) {
            return "spy-no-library";
        }
        if (isSpying(player.getUniqueId())) {
            return "spy-active";
        }
        if (raidManager.isRaidActive()) {
            return "spy-raid"; // harci szakaszban tilos — csak felderítésre való
        }
        final long now = System.currentTimeMillis();
        final long readyAt = player.getPersistentDataContainer()
                .getOrDefault(cooldownKey, PersistentDataType.LONG, 0L);
        if (now < readyAt) {
            return "spy-cooldown";
        }
        final String fakeName = configManager.getString(
                "spy.fake-names." + targetFaction.name().toLowerCase(Locale.ROOT), "Vándor");
        if (!SpyDisguise.apply(player, fakeName)) {
            return "spy-no-library";
        }
        final int seconds = Math.max(10, configManager.getInt("spy.duration-seconds", 60));
        final long cooldownMinutes = Math.max(1, configManager.getInt("spy.cooldown-minutes", 15));
        activeUntil.put(player.getUniqueId(), now + seconds * 1000L);
        player.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG,
                now + cooldownMinutes * 60_000L);
        player.sendMessage(messageManager.getMessage("spy-started",
                "<dark_gray>🕵 Az álca felkerült: <white>{name}</white> — {seconds} mp. Ne feledd: egyetlen ütés (adott vagy kapott) lebuktat, és a bűn bűn marad.</dark_gray>",
                Map.of("name", fakeName, "seconds", String.valueOf(seconds))));
        // Időzített lejárat a játékos SAJÁT schedulerén (halál/kilépésnél magától nyugdíjazódik).
        player.getScheduler().runDelayed(plugin, task -> {
            if (isSpying(player.getUniqueId())) {
                reveal(player, "spy-expired", "<gray>🕵 Az álca lefoszlott — az idő lejárt.</gray>");
            }
        }, null, seconds * 20L);
        return null;
    }

    /** Lebuktatás/lejárat: álca le + üzenet. A játékos saját régió-szálán hívandó. */
    public void reveal(final Player player, final String messageKey, final String messageDefault) {
        if (activeUntil.remove(player.getUniqueId()) == null) {
            return;
        }
        SpyDisguise.remove(player);
        player.sendMessage(messageManager.getMessage(messageKey, messageDefault));
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        activeUntil.remove(playerId);
    }
}
