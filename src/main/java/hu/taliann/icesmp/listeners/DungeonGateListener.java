package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileDungeonAccessStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Dungeon border gate with PlayerProfile-backed pass and lockout state. */
public final class DungeonGateListener implements Listener {

    private static final NamespacedKey SIGNATURE_KEY = SignatureItemListener.SIGNATURE_PDC_KEY;
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TerritoryManager territoryManager;
    private final MessageManager messageManager;
    private final PlayerProfileDungeonAccessStore accessStore =
            new PlayerProfileDungeonAccessStore();
    /** Rebuildable hot-path projection; durable truth is QuestSection. */
    private final Map<Key, PlayerProfileDungeonAccessStore.Access> accessMirror =
            new ConcurrentHashMap<>();
    private volatile hu.taliann.icesmp.managers.PartyManager partyManagerRef;

    public DungeonGateListener(final JavaPlugin plugin, final ConfigManager configManager,
                               final TerritoryManager territoryManager,
                               final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.territoryManager = territoryManager;
        this.messageManager = messageManager;
    }

    public void setPartyManager(final hu.taliann.icesmp.managers.PartyManager partyManager) {
        this.partyManagerRef = partyManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) { handleBorderCross(event); }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(final org.bukkit.event.player.PlayerTeleportEvent event) {
        handleBorderCross(event);
    }

    @EventHandler
    public void onVehicleMove(final org.bukkit.event.vehicle.VehicleMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        if (!configManager.getBoolean("territory.dungeon.enabled", true)) return;
        for (final org.bukkit.entity.Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player && !mayCross(player, from, to)) {
                event.getVehicle().eject();
                player.teleportAsync(from);
            }
        }
    }

    private void handleBorderCross(final PlayerMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        if (!configManager.getBoolean("territory.dungeon.enabled", true)) return;
        if (!mayCross(event.getPlayer(), from, to)) event.setCancelled(true);
    }

    private boolean mayCross(final Player player, final Location from, final Location to) {
        final Territory target = territoryManager.getTerritoryAt(to);
        if (target == null || target.type() != TerritoryType.DUNGEON) return true;
        final Territory previous = territoryManager.getTerritoryAt(from);
        if (previous != null && previous.id().equals(target.id())) return true;
        if (player.hasPermission(
                hu.taliann.icesmp.managers.TerritoryProtectionService.ADMIN_BYPASS)) return true;

        final String zone = target.id().toLowerCase(Locale.ROOT);
        final long now = System.currentTimeMillis();
        final Key mirrorKey = new Key(player.getUniqueId(), zone);
        final PlayerProfileDungeonAccessStore.Access access = access(mirrorKey);
        if (access.passUntil() > now) return true;
        if (access.lockUntil() > now) {
            final long daysLeft = Math.max(1L,
                    (access.lockUntil() - now + 86_399_999L) / 86_400_000L);
            player.sendActionBar(messageManager.getMessage("dungeon-locked",
                    "<red>⛨ {name} pecsétje még friss rajtad — {days} nap múlva térhetsz vissza.</red>",
                    Map.of("name", target.name(), "days", Long.toString(daysLeft))));
            return false;
        }

        final ItemStack key = findKey(player, zone);
        if (key == null) {
            player.sendActionBar(messageManager.getMessage("dungeon-no-key",
                    "<red>⛨ {name} kapuja zárva — a Mélység Népe csarnokába kulcs kell.</red>",
                    Map.of("name", target.name())));
            return false;
        }

        final long passHours = Math.max(1L,
                configManager.getLong("territory.dungeon.pass-hours", 2L));
        final long lockDays = Math.max(0L,
                configManager.getLong("territory.dungeon.lockout-days", 7L));
        final long passUntil = Math.addExact(now, passHours * 3_600_000L);
        final long lockUntil = lockDays > 0L
                ? Math.addExact(now, lockDays * 86_400_000L) : 0L;
        accessMirror.put(mirrorKey,
                new PlayerProfileDungeonAccessStore.Access(passUntil, lockUntil));
        key.setAmount(key.getAmount() - 1);

        accessStore.grant(player.getUniqueId(), zone, now, passUntil, lockUntil)
                .whenComplete((grant, failure) -> player.getScheduler().run(plugin, task -> {
                    if (failure != null || grant == null || !grant.granted()) {
                        accessMirror.remove(mirrorKey);
                        restoreKey(player, zone);
                        player.sendMessage(messageManager.getMessage("dungeon-profile-failed",
                                "<red>A kazamata-hozzáférés PlayerProfile mentése meghiúsult; a kulcsod visszakaptad.</red>"));
                        return;
                    }
                    accessMirror.put(mirrorKey, grant.access());
                }, null));

        player.playSound(player.getLocation(),
                org.bukkit.Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.0F, 0.7F);
        player.sendMessage(messageManager.getMessage("dungeon-entered",
                "<gold>🗝 A kulcs porrá omlik a zárban — {name} megnyílt előtted ({hours} órád van; a pecsét {days} napig tart).</gold>",
                Map.of("name", target.name(), "hours", Long.toString(passHours),
                        "days", Long.toString(lockDays))));
        sharePartyAccess(player, target, zone, now, passUntil, lockUntil);
        return true;
    }

    private void sharePartyAccess(final Player player, final Territory target,
                                  final String zone, final long now,
                                  final long passUntil, final long lockUntil) {
        if (!configManager.getBoolean("territory.dungeon.party-shared-key", true)
                || partyManagerRef == null) return;
        final hu.taliann.icesmp.managers.PartyManager.Party party =
                partyManagerRef.getParty(player.getUniqueId());
        if (party == null) return;
        for (final UUID memberId : party.getMembers()) {
            if (memberId.equals(player.getUniqueId())) continue;
            final Player member = org.bukkit.Bukkit.getPlayer(memberId);
            final Location position = hu.taliann.icesmp.utils.PositionCache.get(memberId);
            if (member == null || position == null || position.getWorld() == null
                    || !position.getWorld().getUID().equals(player.getWorld().getUID())
                    || position.distanceSquared(player.getLocation()) > 256.0D) continue;
            member.getScheduler().run(plugin, task -> {
                final Key key = new Key(memberId, zone);
                final var current = access(key);
                if (current.lockUntil() > System.currentTimeMillis()) return;
                accessMirror.put(key,
                        new PlayerProfileDungeonAccessStore.Access(passUntil, lockUntil));
                accessStore.grant(memberId, zone, now, passUntil, lockUntil)
                        .whenComplete((grant, failure) -> member.getScheduler().run(plugin, owner -> {
                            if (failure != null || grant == null || !grant.granted()) {
                                accessMirror.remove(key);
                                return;
                            }
                            accessMirror.put(key, grant.access());
                            member.sendMessage(messageManager.getMessage("dungeon-party-entry",
                                    "<gold>🗝 A csapatod kulcsa neked is utat nyitott — {name} vár.</gold>",
                                    Map.of("name", target.name())));
                        }, null));
            }, null);
        }
    }

    private PlayerProfileDungeonAccessStore.Access access(final Key key) {
        final var mirror = accessMirror.get(key);
        if (mirror != null) return mirror;
        try {
            final var durable = accessStore.read(key.playerId(), key.zone());
            accessMirror.put(key, durable);
            return durable;
        } catch (final RuntimeException notReady) {
            return new PlayerProfileDungeonAccessStore.Access(0L, Long.MAX_VALUE);
        }
    }

    private ItemStack findKey(final Player player, final String zone) {
        final String wanted = "dungeonkulcs_" + zone;
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()
                    && wanted.equals(item.getItemMeta().getPersistentDataContainer()
                    .get(SIGNATURE_KEY, PersistentDataType.STRING))) return item;
        }
        return null;
    }

    private void restoreKey(final Player player, final String zone) {
        final ItemStack replacement = new ItemStack(org.bukkit.Material.TRIPWIRE_HOOK, 1);
        replacement.editMeta(meta -> meta.getPersistentDataContainer().set(SIGNATURE_KEY,
                PersistentDataType.STRING, "dungeonkulcs_" + zone));
        player.getInventory().addItem(replacement).values().forEach(left ->
                player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private record Key(UUID playerId, String zone) { }
}
