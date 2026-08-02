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
    private final FactionManager factionManager;
    private final SeasonManager seasonManager;
    private final TerritoryManager territoryManager;
    private final NamespacedKey cooldownKey;
    private final Map<UUID, Long> activeUntil = new ConcurrentHashMap<>();

    public SpyManager(final JavaPlugin plugin, final ConfigManager configManager,
                      final RaidManager raidManager, final MessageManager messageManager,
                      final FactionManager factionManager, final SeasonManager seasonManager,
                      final TerritoryManager territoryManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.raidManager = raidManager;
        this.messageManager = messageManager;
        this.factionManager = factionManager;
        this.seasonManager = seasonManager;
        this.territoryManager = territoryManager;
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
                awardMissionPoints(player);
                reveal(player, "spy-expired", "<gray>🕵 Az álca lefoszlott — az idő lejárt.</gray>");
            }
        }, null, seconds * 20L);
        return null;
    }

    /**
     * Liga-pont sikeres küldetésért — de a "küldetés" nem a puszta túlélés (az AFK-ban
     * is menne, 15 percenként ingyen pont lenne): CSAK akkor jár, ha az álca lejártakor
     * a kém IDEGEN frakció territóriumában áll (valódi beszivárgás), és a napi
     * pont-limit (spy.points-daily-limit) még nem fogyott el. A játékos saját
     * régió-szálán fut (a runDelayed onnan hívja) — a hely/PDC-olvasás biztonságos.
     */
    private void awardMissionPoints(final Player player) {
        final FactionType own = factionManager.getChosenFaction(
                player.getUniqueId()).orElse(null);
        if (own == null) {
            return;
        }
        final hu.taliann.icesmp.data.Territory at = territoryManager.getTerritoryAt(player.getLocation());
        if (at == null || at.faction() == null || at.faction() == own) {
            return; // nem ellenséges földön járt le az álca — nincs pont
        }
        final int dailyLimit = Math.max(0, configManager.getInt("spy.points-daily-limit", 2));
        if (!hu.taliann.icesmp.utils.DailyBudget.tryConsumeOnOwnThread(player, "spy_points", dailyLimit, 1L)) {
            return;
        }
        seasonManager.addPoints(own, Math.max(0, configManager.getInt("spy.season-points", 2)), "spy");
        player.sendMessage(messageManager.getMessage("spy-mission-success",
                "<dark_gray>🕵 A küldetés sikerült — a Suttogók feljegyezték az érdemed (liga-pont a frakciódnak).</dark_gray>"));
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
        if (activeUntil.remove(playerId) == null) {
            return;
        }
        final Player online = org.bukkit.Bukkit.getPlayer(playerId);
        if (online != null) {
            online.getScheduler().run(plugin, task -> SpyDisguise.remove(online), null);
        }
    }

    /** Plugin-leállás/reload: az élő álcák levétele (különben az új példány már nem tudna róluk). */
    public void shutdown() {
        for (final UUID playerId : activeUntil.keySet()) {
            final Player online = org.bukkit.Bukkit.getPlayer(playerId);
            if (online != null) {
                online.getScheduler().run(plugin, task -> SpyDisguise.remove(online), null);
            }
        }
        activeUntil.clear();
    }
}
