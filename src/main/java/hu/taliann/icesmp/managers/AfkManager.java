package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native AFK system (replaces the external AxAFKZone plugin): a global inactivity timer
 * (any player, anywhere) plus configured AFK-zone boxes ({@code config/afk.yml}) that pay a
 * small, timed currency "faucet" while a player stands inside — moving around within the zone
 * still counts, since the zone timer tracks presence, not stillness.
 *
 * <p>Folia: {@link #tick()} runs on the global region scheduler; every per-player read (location,
 * bossbar, sendMessage) hops onto that player's own region thread via {@code player.getScheduler()}
 * before touching anything about that player — no other entity is ever touched off-thread.
 * {@link #recordActivity(UUID)} is the sole exception: it is a bare map-write, safe to call from
 * any thread (including the async chat event), by design.
 */
public final class AfkManager implements PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    /** Last observed activity (any thread — bare map-write only, see {@link #recordActivity}). */
    private final ConcurrentHashMap<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    /** The AFK-zone id the player currently stands in (absent = not in any zone). */
    private final ConcurrentHashMap<UUID, String> currentZone = new ConcurrentHashMap<>();
    /** Millis accumulated in the current zone stay, towards the next reward interval. */
    private final ConcurrentHashMap<UUID, Long> zoneProgress = new ConcurrentHashMap<>();
    /** Timestamp of the last tick the player was confirmed in a zone (for the progress delta). */
    private final ConcurrentHashMap<UUID, Long> zoneEnteredAt = new ConcurrentHashMap<>();
    /** Per-player AFK-zone boss-bar (only shown while inside a zone). */
    private final ConcurrentHashMap<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    public AfkManager(final JavaPlugin plugin, final ConfigManager configManager,
                      final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    /**
     * Records activity for the given player. Called from move/chat/command/interact listeners —
     * including the async chat event — so it deliberately does nothing but write a map entry.
     * Safe to call from any thread.
     */
    public void recordActivity(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastActivity.put(playerId, System.currentTimeMillis());
        manualAfk.remove(playerId);
    }

    /** Önkéntes AFK-jelölések (/afk) — bármilyen aktivitás törli. */
    private final java.util.Set<UUID> manualAfk = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * /afk — önkéntes AFK-váltás. Bekapcsolva azonnal él a ⌚-jelölés, a hátrasorolás és a
     * jutalomkapu; bármilyen aktivitás (mozgás/chat/parancs) törli.
     *
     * @return az ÚJ állapot (true = mostantól AFK)
     */
    public boolean toggleManualAfk(final UUID playerId) {
        if (manualAfk.remove(playerId)) {
            return false;
        }
        manualAfk.add(playerId);
        return true;
    }

    /**
     * Whether the player counts as AFK: either they have been idle (anywhere on the server) past
     * {@code afk.afk-after-seconds}, or they are currently standing in an AFK zone — a zone stay
     * always flags AFK for the tablist, even while the player is moving around inside it, since
     * that is the entire point of the zone (idle-but-present).
     */
    public boolean isAfk(final UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (manualAfk.contains(playerId) || currentZone.containsKey(playerId)) {
            return true;
        }
        final Long last = lastActivity.get(playerId);
        if (last == null) {
            return false;
        }
        final long afterMillis = Math.max(1L, configManager.getLong("afk.afk-after-seconds", 180L)) * 1000L;
        return System.currentTimeMillis() - last >= afterMillis;
    }

    /**
     * Periodic tick, called from the global region scheduler: hops onto every online player's own
     * region thread to check their zone membership, advance their zone timer, pay out rewards and
     * refresh their boss-bar.
     */
    public void tick() {
        if (!configManager.getBoolean("afk.enabled", true)) {
            return;
        }
        final List<Zone> zones = loadZones();
        if (zones.isEmpty()) {
            return;
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> tickPlayer(player, zones), null);
        }
    }

    /** Runs entirely on the player's own region thread (scheduled by {@link #tick()}). */
    private void tickPlayer(final Player player, final List<Zone> zones) {
        final UUID playerId = player.getUniqueId();
        final Zone zone = findZone(player.getLocation(), zones);
        final String previousZoneId = currentZone.get(playerId);

        if (zone == null) {
            if (previousZoneId != null) {
                onZoneLeave(player);
            }
            return;
        }

        final long now = System.currentTimeMillis();
        if (previousZoneId == null) {
            onZoneEnter(player, zone, now);
        } else if (!previousZoneId.equals(zone.id())) {
            // Moved straight from one zone into another: leave the old one, enter the new.
            onZoneLeave(player);
            onZoneEnter(player, zone, now);
        }

        advanceProgress(player, now);
        updateBossBar(player);
    }

    private void onZoneEnter(final Player player, final Zone zone, final long now) {
        final UUID playerId = player.getUniqueId();
        currentZone.put(playerId, zone.id());
        zoneEnteredAt.put(playerId, now);
        zoneProgress.putIfAbsent(playerId, 0L);
        player.sendMessage(messageManager.getMessage("afk-zone-enter",
                "&b⌚ Beléptél egy &fAFK-zónába&b — időzítve jutalom jár, amíg bent maradsz."));
        if (configManager.getBoolean("afk.bossbar.enabled", true)) {
            final BossBar bar = BossBar.bossBar(Component.empty(), 0.0F, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            bossBars.put(playerId, bar);
            player.showBossBar(bar);
        }
    }

    private void onZoneLeave(final Player player) {
        final UUID playerId = player.getUniqueId();
        currentZone.remove(playerId);
        zoneProgress.remove(playerId);
        zoneEnteredAt.remove(playerId);
        player.sendMessage(messageManager.getMessage("afk-zone-leave",
                "&7Elhagytad az AFK-zónát — az időzítő nullázódott."));
        final BossBar bar = bossBars.remove(playerId);
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    /** Advances the zone timer by the elapsed time since the last tick and pays out on interval. */
    private void advanceProgress(final Player player, final long now) {
        final UUID playerId = player.getUniqueId();
        final long lastTick = zoneEnteredAt.getOrDefault(playerId, now);
        final long delta = Math.max(0L, now - lastTick);
        zoneEnteredAt.put(playerId, now);

        final long intervalMillis = Math.max(1L, configManager.getLong("afk.reward.interval-seconds", 600L)) * 1000L;
        final long progress = zoneProgress.merge(playerId, delta, Long::sum);
        if (progress >= intervalMillis) {
            // Keep the remainder instead of resetting to 0, so a slightly-late tick doesn't cost
            // the player part of their next interval.
            zoneProgress.put(playerId, progress % intervalMillis);
            payReward(player);
        }
    }

    /** Small, intentional faucet — see the comment in {@code config/afk.yml}. */
    private void payReward(final Player player) {
        final CurrencyType currencyType = resolveCurrency();
        final double amount = configManager.getDouble("afk.reward.amount", 2.0D);
        if (amount <= 0.0D) {
            return;
        }
        currencyManager.addToBalance(player.getUniqueId(), currencyType, amount);
        player.sendMessage(messageManager.get("afk-reward-received",
                "&b⌚ AFK-jutalom: &f+%s &b%s",
                currencyManager.formatBalance(amount), currencyType.getDisplayName()));
    }

    private void updateBossBar(final Player player) {
        if (!configManager.getBoolean("afk.bossbar.enabled", true)) {
            return;
        }
        final BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) {
            return;
        }
        final long intervalMillis = Math.max(1L, configManager.getLong("afk.reward.interval-seconds", 600L)) * 1000L;
        final long progress = zoneProgress.getOrDefault(player.getUniqueId(), 0L);
        final long remainingSeconds = Math.max(0L, intervalMillis - progress) / 1000L;

        final String template = configManager.getString("afk.bossbar.text",
                "⌚ ᴀꜰᴋ-ᴢóɴᴀ — köv. jutalom: {perc} perc {mp} mp");
        final String text = template
                .replace("{perc}", String.valueOf(remainingSeconds / 60L))
                .replace("{mp}", String.valueOf(remainingSeconds % 60L));
        bar.name(Component.text(text, NamedTextColor.AQUA));
        bar.progress(Math.max(0.0F, Math.min(1.0F, (float) progress / (float) intervalMillis)));
    }

    private CurrencyType resolveCurrency() {
        final CurrencyType parsed = CurrencyType.fromInput(configManager.getString("afk.reward.currency", "NEUTRAL"));
        return parsed == null ? CurrencyType.NEUTRAL : parsed;
    }

    private Zone findZone(final Location location, final List<Zone> zones) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        for (final Zone zone : zones) {
            if (zone.contains(location)) {
                return zone;
            }
        }
        return null;
    }

    /** Rebuilt from config on every tick (cheap: a handful of boxes) — always reflects a reload. */
    private List<Zone> loadZones() {
        final List<Zone> zones = new ArrayList<>();
        final FileConfiguration configuration = configManager.getConfiguration();
        if (configuration == null) {
            return zones;
        }
        final ConfigurationSection zonesSection = configuration.getConfigurationSection("afk.zones");
        if (zonesSection == null) {
            return zones;
        }
        for (final String key : zonesSection.getKeys(false)) {
            final ConfigurationSection zoneSection = zonesSection.getConfigurationSection(key);
            if (zoneSection == null) {
                continue;
            }
            final ConfigurationSection min = zoneSection.getConfigurationSection("min");
            final ConfigurationSection max = zoneSection.getConfigurationSection("max");
            if (min == null || max == null) {
                continue;
            }
            final String world = zoneSection.getString("world", "world");
            zones.add(new Zone(key, world,
                    Math.min(min.getInt("x"), max.getInt("x")),
                    Math.min(min.getInt("y"), max.getInt("y")),
                    Math.min(min.getInt("z"), max.getInt("z")),
                    Math.max(min.getInt("x"), max.getInt("x")),
                    Math.max(min.getInt("y"), max.getInt("y")),
                    Math.max(min.getInt("z"), max.getInt("z"))));
        }
        return zones;
    }

    /**
     * Boss-bar hide needs a live {@link Player}; called both directly (e.g. from a quit listener
     * that already has the Player) and from {@link #clearPlayerState(UUID)} once it resolves one.
     */
    public void cleanup(final Player player) {
        if (player == null) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        lastActivity.remove(playerId);
        currentZone.remove(playerId);
        zoneProgress.remove(playerId);
        zoneEnteredAt.remove(playerId);
        final BossBar bar = bossBars.remove(playerId);
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        final Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            cleanup(player);
            return;
        }
        // Offline already (no Player to hide the boss-bar on) — just drop the maps.
        lastActivity.remove(playerId);
        currentZone.remove(playerId);
        zoneProgress.remove(playerId);
        zoneEnteredAt.remove(playerId);
        bossBars.remove(playerId);
        manualAfk.remove(playerId);
    }

    /** One configured AFK-zone box (normalized min/max, inclusive block bounds). */
    private record Zone(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(final Location location) {
            return world.equalsIgnoreCase(location.getWorld().getName())
                    && location.getBlockX() >= minX && location.getBlockX() <= maxX
                    && location.getBlockY() >= minY && location.getBlockY() <= maxY
                    && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
        }
    }
}
