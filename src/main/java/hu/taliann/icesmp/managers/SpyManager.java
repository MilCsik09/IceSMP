package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.integration.SpyDisguise;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileCooldownStore;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tactical disguise with durable PlayerProfile cooldown and runtime active state. */
public final class SpyManager implements PlayerStateCleanup {

    private static final String COOLDOWN = "spy.ready-at";
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final RaidManager raidManager;
    private final MessageManager messageManager;
    private final FactionManager factionManager;
    private final SeasonManager seasonManager;
    private final TerritoryManager territoryManager;
    private final PlayerProfileCooldownStore cooldowns = new PlayerProfileCooldownStore();
    private final Map<UUID, Long> activeUntil = new ConcurrentHashMap<>();
    private final Set<UUID> pendingStarts = ConcurrentHashMap.newKeySet();

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
    }

    public boolean isSpying(final UUID playerId) {
        final Long until = activeUntil.get(playerId);
        return until != null && System.currentTimeMillis() < until;
    }

    /** Starts asynchronously after the durable cooldown reservation commits. */
    public String start(final Player player, final FactionType targetFaction) {
        if (!configManager.getBoolean("spy.enabled", true)) return "spy-disabled";
        if (!factionManager.hasChosenFaction(player.getUniqueId())) return "spy-no-faction";
        if (!SpyDisguise.isAvailable()) return "spy-no-library";
        if (isSpying(player.getUniqueId()) || pendingStarts.contains(player.getUniqueId()))
            return "spy-active";
        if (raidManager.isRaidActive()) return "spy-raid";
        final long now = System.currentTimeMillis();
        final long readyAt;
        try {
            readyAt = cooldowns.read(player.getUniqueId(),
                    PlayerProfileCooldownStore.Domain.FACTION, COOLDOWN);
        } catch (final RuntimeException notReady) {
            return "spy-cooldown";
        }
        if (now < readyAt) return "spy-cooldown";

        final long cooldownMinutes = Math.max(1,
                configManager.getInt("spy.cooldown-minutes", 15));
        final long nextReady = Math.addExact(now, cooldownMinutes * 60_000L);
        if (!pendingStarts.add(player.getUniqueId())) return "spy-active";
        cooldowns.reserve(player.getUniqueId(), PlayerProfileCooldownStore.Domain.FACTION,
                        COOLDOWN, now, nextReady)
                .whenComplete((accepted, failure) -> player.getScheduler().run(plugin, task -> {
                    pendingStarts.remove(player.getUniqueId());
                    if (failure != null || !Boolean.TRUE.equals(accepted)) {
                        player.sendMessage(messageManager.get("spy-error.spy-cooldown",
                                "&cAz álca-mester még pihen — nézz vissza később."));
                        return;
                    }
                    applyDisguise(player, targetFaction, now);
                }, null));
        return null;
    }

    private void applyDisguise(final Player player, final FactionType targetFaction,
                               final long startedAt) {
        if (!player.isOnline() || raidManager.isRaidActive()) return;
        final String fakeName = configManager.getString(
                "spy.fake-names." + targetFaction.name().toLowerCase(Locale.ROOT), "Vándor");
        if (!SpyDisguise.apply(player, fakeName)) {
            player.sendMessage(messageManager.get("spy-error.spy-no-library",
                    "&cAz álca-mesterség most nem elérhető."));
            return;
        }
        final int seconds = Math.max(10, configManager.getInt("spy.duration-seconds", 60));
        final long sessionUntil = Math.addExact(startedAt, seconds * 1000L);
        activeUntil.put(player.getUniqueId(), sessionUntil);
        player.sendMessage(messageManager.getMessage("spy-started",
                "<dark_gray>🕵 Az álca felkerült: <white>{name}</white> — {seconds} mp. Ne feledd: egyetlen ütés lebuktat, és a bűn bűn marad.</dark_gray>",
                Map.of("name", fakeName, "seconds", Integer.toString(seconds))));
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline() && activeUntil.remove(player.getUniqueId(), sessionUntil)) {
                awardMissionPoints(player);
                SpyDisguise.remove(player);
                player.sendMessage(messageManager.getMessage("spy-expired",
                        "<gray>🕵 Az álca lefoszlott — az idő lejárt.</gray>"));
            }
        }, null, seconds * 20L);
    }

    private void awardMissionPoints(final Player player) {
        final FactionType own = factionManager.getChosenFaction(
                player.getUniqueId()).orElse(null);
        if (own == null) return;
        final hu.taliann.icesmp.data.Territory at =
                territoryManager.getTerritoryAt(player.getLocation());
        if (at == null || at.faction() == null || at.faction() == own) return;
        final int dailyLimit = Math.max(0,
                configManager.getInt("spy.points-daily-limit", 2));
        if (!hu.taliann.icesmp.utils.DailyBudget.tryConsumeOnOwnThread(
                player, "spy_points", dailyLimit, 1L)) return;
        seasonManager.addPoints(own,
                Math.max(0, configManager.getInt("spy.season-points", 2)), "spy");
        player.sendMessage(messageManager.getMessage("spy-mission-success",
                "<dark_gray>🕵 A küldetés sikerült — a Suttogók feljegyezték az érdemed.</dark_gray>"));
    }

    public void reveal(final Player player, final String messageKey,
                       final String messageDefault) {
        if (activeUntil.remove(player.getUniqueId()) == null) return;
        SpyDisguise.remove(player);
        player.sendMessage(messageManager.getMessage(messageKey, messageDefault));
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        pendingStarts.remove(playerId);
        if (activeUntil.remove(playerId) == null) return;
        final Player online = org.bukkit.Bukkit.getPlayer(playerId);
        if (online != null)
            online.getScheduler().run(plugin, task -> SpyDisguise.remove(online), null);
    }

    public void shutdown() {
        for (final UUID playerId : activeUntil.keySet()) {
            final Player online = org.bukkit.Bukkit.getPlayer(playerId);
            if (online != null)
                online.getScheduler().run(plugin, task -> SpyDisguise.remove(online), null);
        }
        activeUntil.clear();
        pendingStarts.clear();
    }
}
