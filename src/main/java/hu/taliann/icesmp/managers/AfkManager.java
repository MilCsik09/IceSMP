package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.selection.CuboidSelectionService;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Native IceSMP AFK system: global inactivity tracking plus multiple strictly validated,
 * configurable reward zones backed by the shared 3D cuboid selection service.
 *
 * <p>Folia: {@link #tick()} is called from the global scheduler and immediately hops to every
 * player's entity scheduler. Player location/inventory/title/bossbar access therefore stays with
 * the owning entity. Console rewards are dispatched on the global-region scheduler. The only
 * cross-thread activity method is {@link #recordActivity(UUID)}, intentionally limited to
 * concurrent scalar state.</p>
 */
public final class AfkManager implements PlayerStateCleanup {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;
    private final CuboidSelectionService selectionService;

    private final ConcurrentHashMap<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> currentZone = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> zoneProgress = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> zoneLastTick = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Set<UUID> manualAfk = ConcurrentHashMap.newKeySet();

    /** Reload publishes one fully validated immutable snapshot. */
    private volatile AfkZoneCatalog.Snapshot catalog = AfkZoneCatalog.Snapshot.empty();

    public AfkManager(final JavaPlugin plugin, final ConfigManager configManager,
                      final CurrencyManager currencyManager, final MessageManager messageManager,
                      final CuboidSelectionService selectionService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
        this.selectionService = selectionService;
        reloadZones();
    }

    public void recordActivity(final UUID playerId) {
        if (playerId != null) {
            lastActivity.put(playerId, System.currentTimeMillis());
            manualAfk.remove(playerId);
        }
    }

    public boolean toggleManualAfk(final UUID playerId) {
        if (manualAfk.remove(playerId)) {
            return false;
        }
        manualAfk.add(playerId);
        return true;
    }

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
        final long afterMillis = Math.max(1L,
                configManager.getLong("afk.afk-after-seconds", 180L)) * 1000L;
        return System.currentTimeMillis() - last >= afterMillis;
    }

    /** Rebuilds and atomically publishes the valid zone set; bad siblings remain isolated. */
    public void reloadZones() {
        final AfkZoneCatalog.Snapshot loaded = AfkZoneCatalog.load(configManager.getConfiguration());
        catalog = loaded;
        for (final Map.Entry<String, List<String>> entry : loaded.errors().entrySet()) {
            for (final String problem : entry.getValue()) {
                plugin.getLogger().warning("AFK-zóna '" + entry.getKey() + "' letiltva: " + problem);
            }
        }
        plugin.getLogger().info("AFK-zónák betöltve: " + loaded.zones().size()
                + " aktív definíció, " + loaded.errors().size() + " hibás definíció.");
    }

    public Set<String> zoneIds() {
        return catalog.zones().keySet();
    }

    public AfkZoneCatalog.Zone zone(final String id) {
        return catalog.zones().get(normalizeId(id));
    }

    public List<String> zoneProblems(final String id) {
        return catalog.errors().getOrDefault(normalizeId(id), List.of());
    }

    public Map<String, List<String>> allZoneProblems() {
        return catalog.errors();
    }

    /** Periodic global-driver entrypoint; all player work hops to the player's scheduler. */
    public void tick() {
        if (!configManager.getBoolean("afk.enabled", true)) {
            releaseAllZones();
            return;
        }
        final AfkZoneCatalog.Snapshot snapshot = catalog;
        if (snapshot.zones().isEmpty()) {
            releaseAllZones();
            return;
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> tickPlayer(player, snapshot), null);
        }
    }

    private void releaseAllZones() {
        if (currentZone.isEmpty()) {
            return;
        }
        final AfkZoneCatalog.Snapshot snapshot = catalog;
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (currentZone.containsKey(player.getUniqueId())) {
                player.getScheduler().run(plugin, task -> onZoneLeave(player,
                        snapshot.zones().get(currentZone.get(player.getUniqueId()))), null);
            }
        }
        currentZone.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void tickPlayer(final Player player, final AfkZoneCatalog.Snapshot snapshot) {
        final UUID playerId = player.getUniqueId();
        final AfkZoneCatalog.Zone next = findZone(player, snapshot.zones().values());
        final String previousId = currentZone.get(playerId);
        final AfkZoneCatalog.Zone previous = previousId == null ? null : snapshot.zones().get(previousId);

        if (next == null) {
            if (previousId != null) {
                onZoneLeave(player, previous);
            }
            return;
        }

        final long now = System.currentTimeMillis();
        if (previousId == null) {
            onZoneEnter(player, next, now);
        } else if (!previousId.equals(next.id())) {
            onZoneLeave(player, previous);
            onZoneEnter(player, next, now);
        }

        advanceProgress(player, next, now);
        updateUi(player, next);
    }

    private AfkZoneCatalog.Zone findZone(final Player player,
                                         final java.util.Collection<AfkZoneCatalog.Zone> zones) {
        final Location location = player.getLocation();
        for (final AfkZoneCatalog.Zone zone : zones) {
            if (!zone.permission().isBlank() && !player.hasPermission(zone.permission())) {
                continue;
            }
            if (zone.contains(location)) {
                return zone;
            }
        }
        return null;
    }

    private void onZoneEnter(final Player player, final AfkZoneCatalog.Zone zone, final long now) {
        final UUID playerId = player.getUniqueId();
        currentZone.put(playerId, zone.id());
        zoneLastTick.put(playerId, now);
        zoneProgress.put(playerId, 0L);
        if (!zone.enterMessage().isBlank()) {
            player.sendMessage(messageManager.render(replace(zone.enterMessage(), player, zone, zone.intervalMillis())));
        }
        if (!zone.title().isBlank() || !zone.subtitle().isBlank()) {
            player.showTitle(Title.title(
                    messageManager.render(replace(zone.title(), player, zone, zone.intervalMillis())),
                    messageManager.render(replace(zone.subtitle(), player, zone, zone.intervalMillis())),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }
        if (configManager.getBoolean("afk.bossbar.enabled", true) && !zone.bossbarText().isBlank()) {
            final BossBar old = bossBars.remove(playerId);
            if (old != null) {
                player.hideBossBar(old);
            }
            final BossBar bar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(), 0.0F,
                    zone.bossbarColor(), zone.bossbarOverlay());
            bossBars.put(playerId, bar);
            player.showBossBar(bar);
        }
    }

    private void onZoneLeave(final Player player, final AfkZoneCatalog.Zone previous) {
        final UUID playerId = player.getUniqueId();
        currentZone.remove(playerId);
        zoneProgress.remove(playerId);
        zoneLastTick.remove(playerId);
        if (previous != null && !previous.leaveMessage().isBlank()) {
            player.sendMessage(messageManager.render(replace(previous.leaveMessage(), player, previous, 0L)));
        } else {
            player.sendMessage(messageManager.getMessage("afk-zone-leave",
                    "&7Elhagytad az AFK-zónát — az időzítő nullázódott."));
        }
        final BossBar bar = bossBars.remove(playerId);
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private void advanceProgress(final Player player, final AfkZoneCatalog.Zone zone, final long now) {
        final UUID playerId = player.getUniqueId();
        final long previousTick = zoneLastTick.getOrDefault(playerId, now);
        final long delta = Math.max(0L, now - previousTick);
        zoneLastTick.put(playerId, now);
        final AfkRewardClock.Advance advance = AfkRewardClock.advance(
                zoneProgress.getOrDefault(playerId, 0L), delta, zone.intervalMillis());
        zoneProgress.put(playerId, advance.remainderMillis());
        if (advance.rewardDue()) {
            payRewardCycle(player, zone);
        }
    }

    private void payRewardCycle(final Player player, final AfkZoneCatalog.Zone zone) {
        for (int roll = 0; roll < zone.rollCount(); roll++) {
            final AfkZoneCatalog.Reward reward = AfkZoneCatalog.pick(zone,
                    ThreadLocalRandom.current().nextDouble());
            deliverReward(player, zone, reward);
        }
    }

    private void deliverReward(final Player player, final AfkZoneCatalog.Zone zone,
                               final AfkZoneCatalog.Reward reward) {
        switch (reward.type()) {
            case CURRENCY -> currencyManager.payOutTokens(player, reward.currency(), reward.currencyAmount());
            case ITEM -> {
                final ItemStack item = new ItemStack(reward.material(), reward.itemAmount());
                for (final ItemStack overflow : player.getInventory().addItem(item).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                }
            }
            case COMMAND -> {
                final String command = reward.command()
                        .replace("{player}", player.getName())
                        .replace("{uuid}", player.getUniqueId().toString())
                        .replace("{zone}", zone.id());
                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                    try {
                        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                            plugin.getLogger().warning("AFK command reward nem ismert parancs: " + command);
                        }
                    } catch (final RuntimeException failure) {
                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "AFK command reward hibázott: " + command, failure);
                    }
                });
            }
        }
        plugin.getLogger().info("AFK reward: player=" + player.getUniqueId() + ", zone=" + zone.id()
                + ", type=" + reward.type() + ", reward=" + reward.description());
        player.sendMessage(messageManager.getMessage("afk-reward-generic",
                "&b⌚ AFK-jutalom: &f{reward}", Map.of("reward", reward.description(), "zone", zone.displayName())));
    }

    private void updateUi(final Player player, final AfkZoneCatalog.Zone zone) {
        final long progress = zoneProgress.getOrDefault(player.getUniqueId(), 0L);
        final long remainingMillis = Math.max(0L, zone.intervalMillis() - progress);
        final BossBar bar = bossBars.get(player.getUniqueId());
        if (bar != null) {
            bar.name(messageManager.render(replace(zone.bossbarText(), player, zone, remainingMillis)));
            bar.progress(Math.max(0.0F, Math.min(1.0F,
                    (float) progress / (float) zone.intervalMillis())));
        }
        if (!zone.actionbar().isBlank()) {
            player.sendActionBar(messageManager.render(replace(zone.actionbar(), player, zone, remainingMillis)));
        }
    }

    /** Creates a final IceSMP zone definition in the shared config override, not a legacy file. */
    public synchronized String createZone(final String rawId, final String displayName,
                                          final CuboidSelectionService.Cuboid cuboid) {
        final String id = normalizeId(rawId);
        if (!validId(rawId, id)) {
            return "afk-zone-invalid-id";
        }
        final FileConfiguration config = configManager.getConfiguration();
        final String root = "afk.zones." + id;
        if (config != null && config.isConfigurationSection(root)
                && !config.getBoolean(root + ".deleted", false)) {
            return "afk-zone-exists";
        }
        if (!applyZoneOverrides(defaultZoneUpdates(id, displayName, cuboid), "create " + id)) {
            return "afk-zone-save-failed";
        }
        reloadZones();
        if (!zoneProblems(id).isEmpty() || zone(id) == null) {
            final Map<String, Object> rollback = new LinkedHashMap<>();
            rollback.put("afk.zones." + id + ".enabled", false);
            rollback.put("afk.zones." + id + ".deleted", true);
            if (!applyZoneOverrides(rollback, "rollback " + id)) {
                plugin.getLogger().severe("Az érvénytelen AFK-zóna rollbackje sem menthető: " + id);
                return "afk-zone-save-failed";
            }
            reloadZones();
            return "afk-zone-invalid-config";
        }
        return null;
    }

    public synchronized String replaceZoneArea(final String rawId,
                                               final CuboidSelectionService.Cuboid cuboid) {
        final String id = normalizeId(rawId);
        if (zone(id) == null && zoneProblems(id).isEmpty()) {
            return "afk-zone-unknown";
        }
        final String root = "afk.zones." + id;
        final Map<String, Object> updates = new LinkedHashMap<>();
        addCuboidUpdates(updates, root, cuboid);
        if (!applyZoneOverrides(updates, "replace " + id)) {
            return "afk-zone-save-failed";
        }
        reloadZones();
        return zone(id) == null ? "afk-zone-invalid-config" : null;
    }

    public synchronized String deleteZone(final String rawId) {
        final String id = normalizeId(rawId);
        final FileConfiguration config = configManager.getConfiguration();
        if (config == null || !config.isConfigurationSection("afk.zones." + id)
                || config.getBoolean("afk.zones." + id + ".deleted", false)) {
            return "afk-zone-unknown";
        }
        final Map<String, Object> deletion = new LinkedHashMap<>();
        deletion.put("afk.zones." + id + ".enabled", false);
        deletion.put("afk.zones." + id + ".deleted", true);
        if (!applyZoneOverrides(deletion, "delete " + id)) {
            return "afk-zone-save-failed";
        }
        reloadZones();
        return null;
    }

    public CuboidSelectionService.Result showSelection(final Player player) {
        return selectionService.show(player,
                configManager.getInt("selection.preview-seconds", 8));
    }

    public boolean showZone(final Player player, final String id) {
        final AfkZoneCatalog.Zone zone = zone(id);
        if (zone == null) {
            return false;
        }
        selectionService.showCuboid(player, zone.cuboid(),
                configManager.getInt("selection.preview-seconds", 8));
        return true;
    }

    public Location teleportTarget(final String id) {
        final AfkZoneCatalog.Zone zone = zone(id);
        if (zone == null) {
            return null;
        }
        final World world = Bukkit.getWorld(zone.cuboid().worldId());
        if (world == null) {
            return null;
        }
        return new Location(world,
                midpoint(zone.cuboid().minX(), zone.cuboid().maxX()),
                midpoint(zone.cuboid().minY(), zone.cuboid().maxY()),
                midpoint(zone.cuboid().minZ(), zone.cuboid().maxZ()));
    }

    public String describeZone(final String id) {
        final AfkZoneCatalog.Zone zone = zone(id);
        if (zone == null) {
            final List<String> problems = zoneProblems(id);
            return problems.isEmpty() ? null : String.join(" | ", problems);
        }
        return zone.id() + " — " + zone.displayName() + " — " + zone.cuboid().worldName()
                + " [" + zone.cuboid().minX() + "," + zone.cuboid().minY() + "," + zone.cuboid().minZ()
                + " → " + zone.cuboid().maxX() + "," + zone.cuboid().maxY() + "," + zone.cuboid().maxZ()
                + "] — " + zone.rewards().size() + " reward, " + zone.rollCount() + " roll/"
                + (zone.intervalMillis() / 1000L) + "s";
    }

    private Map<String, Object> defaultZoneUpdates(final String id, final String displayName,
                                                    final CuboidSelectionService.Cuboid cuboid) {
        final String root = "afk.zones." + id;
        final Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(root + ".deleted", false);
        updates.put(root + ".enabled", true);
        updates.put(root + ".display-name", displayName == null || displayName.isBlank() ? id : displayName.trim());
        updates.put(root + ".permission", "");
        updates.put(root + ".reward-interval-seconds",
                configManager.getLong("afk.zone-defaults.reward-interval-seconds", 600L));
        updates.put(root + ".roll-count", configManager.getInt("afk.zone-defaults.roll-count", 1));
        final FileConfiguration config = configManager.getConfiguration();
        final List<Map<?, ?>> defaults = config == null ? List.of()
                : new ArrayList<>(config.getMapList("afk.zone-defaults.rewards"));
        updates.put(root + ".rewards", defaults.isEmpty() ? List.of(Map.of(
                "type", "CURRENCY", "weight", 1.0D, "currency", "NEUTRAL", "amount", 2L)) : defaults);
        updates.put(root + ".messages.enter", configManager.getString("afk.zone-defaults.messages.enter",
                "&b⌚ Beléptél: &f{zone}&b."));
        updates.put(root + ".messages.leave", configManager.getString("afk.zone-defaults.messages.leave",
                "&7Elhagytad: &f{zone}&7."));
        updates.put(root + ".title", configManager.getString("afk.zone-defaults.title", ""));
        updates.put(root + ".subtitle", configManager.getString("afk.zone-defaults.subtitle", ""));
        updates.put(root + ".actionbar", configManager.getString("afk.zone-defaults.actionbar", ""));
        updates.put(root + ".bossbar.text", configManager.getString("afk.zone-defaults.bossbar.text",
                "⌚ {zone} — {minutes}p {seconds}mp"));
        updates.put(root + ".bossbar.color", configManager.getString("afk.zone-defaults.bossbar.color", "BLUE"));
        updates.put(root + ".bossbar.overlay", configManager.getString("afk.zone-defaults.bossbar.overlay", "PROGRESS"));
        addCuboidUpdates(updates, root, cuboid);
        return updates;
    }


    private boolean applyZoneOverrides(final Map<String, ?> updates, final String operation) {
        try {
            configManager.applyOverrides(updates);
            return true;
        } catch (final RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "AFK-zóna configművelet nem menthető: " + operation, failure);
            return false;
        }
    }

    private static double midpoint(final int minimum, final int maximum) {
        return ((long) minimum + (long) maximum + 1L) / 2.0D;
    }

    private static void addCuboidUpdates(final Map<String, Object> updates, final String root,
                                         final CuboidSelectionService.Cuboid cuboid) {
        updates.put(root + ".world-uuid", cuboid.worldId().toString());
        updates.put(root + ".world", cuboid.worldName());
        updates.put(root + ".min.x", cuboid.minX());
        updates.put(root + ".min.y", cuboid.minY());
        updates.put(root + ".min.z", cuboid.minZ());
        updates.put(root + ".max.x", cuboid.maxX());
        updates.put(root + ".max.y", cuboid.maxY());
        updates.put(root + ".max.z", cuboid.maxZ());
    }

    private static String replace(final String template, final Player player,
                                  final AfkZoneCatalog.Zone zone, final long remainingMillis) {
        final long secondsTotal = Math.max(0L, remainingMillis) / 1000L;
        return template
                .replace("{player}", player.getName())
                .replace("{zone}", zone.displayName())
                .replace("{minutes}", String.valueOf(secondsTotal / 60L))
                .replace("{seconds}", String.valueOf(secondsTotal % 60L));
    }

    private static String normalizeId(final String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private static boolean validId(final String raw, final String normalized) {
        return raw != null && raw.trim().equals(normalized) && normalized.length() >= 2 && normalized.length() <= 32;
    }

    public void cleanup(final Player player) {
        if (player == null) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        lastActivity.remove(playerId);
        currentZone.remove(playerId);
        zoneProgress.remove(playerId);
        zoneLastTick.remove(playerId);
        manualAfk.remove(playerId);
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
        lastActivity.remove(playerId);
        currentZone.remove(playerId);
        zoneProgress.remove(playerId);
        zoneLastTick.remove(playerId);
        bossBars.remove(playerId);
        manualAfk.remove(playerId);
    }
}
