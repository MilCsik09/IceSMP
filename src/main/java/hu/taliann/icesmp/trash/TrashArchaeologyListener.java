package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.session.PlayerStateCleanup;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Player-owned, single-session Brush inspection runtime for the hidden Archaeology discipline. */
public final class TrashArchaeologyListener implements Listener, PlayerStateCleanup {

    static final int INSPECTION_TICKS = 30;
    private static final int PRESENTATION_CADENCE = 5;

    private final JavaPlugin plugin;
    private final TrashItemFactory items;
    private final TrashArchaeologyService archaeology;
    private final ArchaeologyTooltipBridge tooltip;
    private final ConcurrentMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    public TrashArchaeologyListener(final JavaPlugin plugin, final TrashItemFactory items,
                                    final TrashArchaeologyService archaeology,
                                    final ArchaeologyTooltipBridge tooltip) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.items = Objects.requireNonNull(items, "items");
        this.archaeology = Objects.requireNonNull(archaeology, "archaeology");
        this.tooltip = Objects.requireNonNull(tooltip, "tooltip");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        final Player player = event.getPlayer();
        final ItemStack brush = player.getInventory().getItemInMainHand();
        final ItemStack inspected = player.getInventory().getItemInOffHand();
        if (brush.getType() != Material.BRUSH || !items.isKnownItem(inspected)) return;
        event.setCancelled(true);
        start(player, inspected);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStopUsing(final PlayerStopUsingItemEvent event) {
        final Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.completing) return;
        cancelSession(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            tooltip.clear(player);
            cancelSession(player, true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            tooltip.clear(player);
            cancelSession(player, true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHeldChange(final PlayerItemHeldEvent event) {
        tooltip.clear(event.getPlayer());
        cancelSession(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(final PlayerSwapHandItemsEvent event) {
        tooltip.clear(event.getPlayer());
        cancelSession(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(final PlayerDropItemEvent event) {
        tooltip.clear(event.getPlayer());
        cancelSession(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(final PlayerDeathEvent event) {
        tooltip.clear(event.getEntity());
        cancelSession(event.getEntity(), true);
    }

    /** Hidden DEV route; the target inventory is only read from its owning entity scheduler. */
    public void forceInspection(final Player target) {
        Objects.requireNonNull(target, "target");
        target.getScheduler().run(plugin, ignored -> {
            final ItemStack inspected = target.getInventory().getItemInOffHand();
            if (!items.isKnownItem(inspected)) return;
            cancelSession(target, true);
            tooltip.clear(target);
            final Session session = new Session(inspected.clone(),
                    target.getInventory().getHeldItemSlot());
            session.completing = true;
            sessions.put(target.getUniqueId(), session);
            inspect(target, session);
        }, null);
    }

    private void start(final Player player, final ItemStack inspected) {
        cancelSession(player, true);
        tooltip.clear(player);
        final Session session = new Session(inspected.clone(),
                player.getInventory().getHeldItemSlot());
        sessions.put(player.getUniqueId(), session);
        try {
            player.startUsingItem(EquipmentSlot.HAND);
            final ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduled -> {
                if (sessions.get(player.getUniqueId()) != session) {
                    scheduled.cancel();
                    return;
                }
                if (!stillValid(player, session)) {
                    retire(player, session, true);
                    return;
                }
                session.elapsed++;
                if (session.elapsed % PRESENTATION_CADENCE == 0) presentBrush(player);
                if (session.elapsed < INSPECTION_TICKS) return;
                beginCompletion(player, session);
            }, () -> sessions.remove(player.getUniqueId(), session), 1L, 1L);
            session.task = task;
            if (task == null || sessions.get(player.getUniqueId()) != session) {
                sessions.remove(player.getUniqueId(), session);
                if (task != null) task.cancel();
            }
        } catch (final RuntimeException rejected) {
            sessions.remove(player.getUniqueId(), session);
            player.clearActiveItem();
        }
    }

    private boolean stillValid(final Player player, final Session session) {
        if (!player.isOnline() || player.isDead()
                || player.getInventory().getHeldItemSlot() != session.heldSlot
                || player.getInventory().getItemInMainHand().getType() != Material.BRUSH) {
            return false;
        }
        final ItemStack current = player.getInventory().getItemInOffHand();
        return current.getAmount() == session.snapshot.getAmount()
                && current.isSimilar(session.snapshot);
    }

    private static void presentBrush(final Player player) {
        player.swingMainHand();
        player.playSound(player.getLocation(), "minecraft:item.brush.brushing.generic", 0.35F, 1.0F);
        player.getWorld().spawnParticle(Particle.WHITE_SMOKE,
                player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.55D)),
                2, 0.08D, 0.08D, 0.08D, 0.005D);
    }

    private void beginCompletion(final Player player, final Session session) {
        if (sessions.get(player.getUniqueId()) != session) return;
        session.completing = true;
        final ScheduledTask task = session.task;
        if (task != null) task.cancel();
        player.clearActiveItem();
        inspect(player, session);
    }

    private void inspect(final Player player, final Session session) {
        final UUID playerId = player.getUniqueId();
        archaeology.inspect(playerId, session.snapshot).whenComplete((result, failure) ->
                player.getScheduler().run(plugin, ignored -> {
                    if (!sessions.remove(playerId, session)) return;
                    if (failure != null || result == null || !result.accepted()
                            || !player.isOnline()) return;
                    if (result.unlockedNow()) {
                        player.sendMessage(Component.text(
                                        "A régi tárgyakon hagyott nyomok egyre többet mondanak neked.",
                                        NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, true));
                        player.sendMessage(Component.text("Régészeti jártasság: 1",
                                        NamedTextColor.GOLD)
                                .decoration(TextDecoration.BOLD, true)
                                .decoration(TextDecoration.ITALIC, false));
                    }
                    final ItemStack current = player.getInventory().getItemInOffHand();
                    if (current.getAmount() != session.snapshot.getAmount()
                            || !current.isSimilar(session.snapshot)) return;
                    final List<String> observations = result.visibleFacts().stream()
                            .map(TrashArchaeologyFactEngine.Fact::text).toList();
                    if (!tooltip.show(player, session.snapshot, observations)) {
                        player.sendMessage(Component.text("Régészeti megfigyelések",
                                NamedTextColor.GOLD));
                        observations.forEach(line -> player.sendMessage(
                                Component.text("• " + line, NamedTextColor.GRAY)));
                    }
                }, () -> sessions.remove(playerId, session)));
    }

    private void cancelSession(final Player player, final boolean clearActiveItem) {
        final Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        final ScheduledTask task = session.task;
        if (task != null) task.cancel();
        if (clearActiveItem) player.clearActiveItem();
    }

    private void retire(final Player player, final Session session,
                        final boolean clearActiveItem) {
        if (!sessions.remove(player.getUniqueId(), session)) return;
        final ScheduledTask task = session.task;
        if (task != null) task.cancel();
        if (clearActiveItem) player.clearActiveItem();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        final Session session = sessions.remove(playerId);
        if (session != null && session.task != null) session.task.cancel();
        tooltip.clearPlayerState(playerId);
    }

    public void shutdown() {
        sessions.values().forEach(session -> {
            if (session.task != null) session.task.cancel();
        });
        sessions.clear();
        tooltip.shutdown();
    }

    private static final class Session {
        private final ItemStack snapshot;
        private final int heldSlot;
        private volatile ScheduledTask task;
        private volatile boolean completing;
        private int elapsed;

        private Session(final ItemStack snapshot, final int heldSlot) {
            this.snapshot = snapshot;
            this.heldSlot = heldSlot;
        }
    }
}
