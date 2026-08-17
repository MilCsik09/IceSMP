package hu.taliann.icesmp.pve;

import hu.taliann.icesmp.itemization.ItemIdentityService;
import hu.taliann.icesmp.itemization.ItemInstance;
import hu.taliann.icesmp.itemization.ItemTemplate;
import hu.taliann.icesmp.managers.JobManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owner-thread equipment sampler with an immutable cross-region encounter cache. */
public final class EquippedCombatPowerService implements Listener {
    private static final long PERIODIC_REFRESH_TICKS = 40L;

    private record Candidate(UUID itemId, EquippedCombatPowerModel.GearSignal signal) { }

    private final JavaPlugin plugin;
    private final ItemIdentityService identities;
    private final JobManager jobs;
    private final Map<UUID, Double> cache = new ConcurrentHashMap<>();
    private final Set<UUID> refreshLoops = ConcurrentHashMap.newKeySet();

    public EquippedCombatPowerService(final JavaPlugin plugin,
                                      final ItemIdentityService identities,
                                      final JobManager jobs) {
        this.plugin = plugin;
        this.identities = identities;
        this.jobs = jobs;
    }

    /** Safe from another region: only reads the last owner-thread projection. */
    public double powerOf(final UUID playerId, final double fallback) {
        final double safeFallback = Double.isFinite(fallback)
                ? Math.max(1.0D, Math.min(10_000.0D, fallback)) : 1.0D;
        final double value = cache.getOrDefault(playerId, safeFallback);
        return Double.isFinite(value) ? Math.max(1.0D, Math.min(10_000.0D, value)) : safeFallback;
    }

    public void refresh(final Player player) {
        if (player == null || !player.isOnline()) return;
        final ArrayList<Candidate> candidates = new ArrayList<>(6);
        add(candidates, player.getInventory().getItemInMainHand(), ItemTemplate.Slot.MAIN_HAND);
        add(candidates, player.getInventory().getItemInOffHand(), ItemTemplate.Slot.OFF_HAND);
        add(candidates, player.getInventory().getHelmet(), ItemTemplate.Slot.HEAD);
        add(candidates, player.getInventory().getChestplate(), ItemTemplate.Slot.CHEST);
        add(candidates, player.getInventory().getLeggings(), ItemTemplate.Slot.LEGS);
        add(candidates, player.getInventory().getBoots(), ItemTemplate.Slot.FEET);
        final HashMap<UUID, Integer> counts = new HashMap<>();
        for (final Candidate candidate : candidates) counts.merge(candidate.itemId(), 1, Integer::sum);
        final List<EquippedCombatPowerModel.GearSignal> unique = candidates.stream()
                .filter(candidate -> counts.getOrDefault(candidate.itemId(), 0) == 1)
                .map(Candidate::signal).toList();
        cache.put(player.getUniqueId(), EquippedCombatPowerModel.estimate(
                jobs.getPrimaryLevel(player), unique));
    }

    private void add(final List<Candidate> result, final ItemStack item,
                     final ItemTemplate.Slot equippedSlot) {
        final ItemIdentityService.Inspection inspection = identities.inspect(item);
        if (inspection.status() != ItemIdentityService.Status.VALID) return;
        final ItemTemplate template = inspection.template();
        if (!fits(template.slot(), equippedSlot)) return;
        final ItemInstance instance = inspection.instance();
        final String stage = instance.ascension().stageId();
        final HashMap<String, Double> rolls = new HashMap<>();
        instance.rolls().forEach((id, roll) -> rolls.put(id, roll.value()));
        result.add(new Candidate(instance.itemId(), new EquippedCombatPowerModel.GearSignal(
                template.slot(), instance.itemLevel(), template.rarity(), template.baseDamage(),
                template.baseArmor(), template.fixedStatsAt(stage), rolls,
                instance.runes().size(), template.signatureTierAt(stage), template.setId())));
    }

    private static boolean fits(final ItemTemplate.Slot authored, final ItemTemplate.Slot equipped) {
        return authored == equipped || (equipped == ItemTemplate.Slot.MAIN_HAND
                && authored == ItemTemplate.Slot.TWO_HAND);
    }

    private void refreshNextTick(final Player player) {
        try {
            player.getScheduler().runDelayed(plugin, task -> refresh(player),
                    () -> cache.remove(player.getUniqueId()), 1L);
        } catch (final RuntimeException rejected) {
            cache.remove(player.getUniqueId());
        }
    }

    /**
     * Event hooks give near-immediate updates, while this owner-thread sampler closes mutation
     * paths that Bukkit has no reliable event for (/clear, plugin armor edits, scripted rerolls,
     * ascension/rune updates). It never reads a Player from another region.
     */
    private void ensureRefreshLoop(final Player player) {
        final UUID playerId = player.getUniqueId();
        if (!refreshLoops.add(playerId)) return;
        try {
            player.getScheduler().runAtFixedRate(plugin, task -> refresh(player), () -> {
                refreshLoops.remove(playerId);
                cache.remove(playerId);
            }, PERIODIC_REFRESH_TICKS, PERIODIC_REFRESH_TICKS);
        } catch (final RuntimeException rejected) {
            refreshLoops.remove(playerId);
            cache.remove(playerId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        refreshNextTick(event.getPlayer());
        ensureRefreshLoop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(final PlayerRespawnEvent event) {
        refreshNextTick(event.getPlayer());
        ensureRefreshLoop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(final InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) refreshNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(final InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) refreshNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(final PlayerItemHeldEvent event) { refreshNextTick(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(final PlayerSwapHandItemsEvent event) { refreshNextTick(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) { refreshNextTick(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) refreshNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(final PlayerItemBreakEvent event) { refreshNextTick(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(final PlayerDeathEvent event) { cache.remove(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(final PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        refreshLoops.remove(playerId);
        cache.remove(playerId);
    }
}
