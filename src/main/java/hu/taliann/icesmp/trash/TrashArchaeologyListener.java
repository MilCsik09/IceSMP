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
    private final java.util.function.BiFunction<UUID, ItemStack,
            java.util.concurrent.CompletionStage<TrashArchaeologyService.Result>> inspections;
    private final ArchaeologyTooltipBridge tooltip;
    private final TrashRuntimeTelemetry telemetry;
    private final ConcurrentMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    public TrashArchaeologyListener(final JavaPlugin plugin, final TrashItemFactory items,
                                    final TrashArchaeologyService archaeology,
                                    final ArchaeologyTooltipBridge tooltip,
                                    final TrashRuntimeTelemetry telemetry) {
        this(plugin, Objects.requireNonNull(archaeology, "archaeology")::inspect, tooltip, telemetry);
        Objects.requireNonNull(items, "items");
    }

    TrashArchaeologyListener(final JavaPlugin plugin,
            final java.util.function.BiFunction<UUID, ItemStack,
                    java.util.concurrent.CompletionStage<TrashArchaeologyService.Result>> inspections,
            final ArchaeologyTooltipBridge tooltip, final TrashRuntimeTelemetry telemetry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.inspections = Objects.requireNonNull(inspections, "inspections");
        this.tooltip = Objects.requireNonNull(tooltip, "tooltip");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getHand() == null || event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        final Player player = event.getPlayer();
        final EquipmentSlot brushHand = brushHand(player);
        if (brushHand == null) return;
        final EquipmentSlot inspectedHand = otherHand(brushHand);
        final ItemStack inspected = held(player, inspectedHand);
        if (inspected.getType().isAir()) return;
        // Predicted no-op air clicks arrive cancelled. Inspecting owned inventory never uses the block.
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        final Session active = sessions.get(player.getUniqueId());
        if (active != null) {
            if (!stillValid(player, active)) { retire(player, active, true); return; }
            active.idleTicks = 0;
            if (!active.completing && active.elapsed >= INSPECTION_TICKS) beginCompletion(player, active);
            return;
        }
        start(player, inspected, brushHand);
    }

    static EquipmentSlot brushHand(final Player player) {
        if (player.getInventory().getItemInOffHand().getType() == Material.BRUSH) return EquipmentSlot.OFF_HAND;
        if (player.getInventory().getItemInMainHand().getType() == Material.BRUSH) return EquipmentSlot.HAND;
        return null;
    }

    private static EquipmentSlot otherHand(final EquipmentSlot hand) {
        return hand == EquipmentSlot.HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
    }

    private static ItemStack held(final Player player, final EquipmentSlot hand) {
        return hand == EquipmentSlot.HAND ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
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
            final EquipmentSlot brush = brushHand(target);
            final EquipmentSlot inspectedHand = brush == null ? EquipmentSlot.HAND : otherHand(brush);
            final ItemStack inspected = held(target, inspectedHand);
            if (inspected.getType().isAir()) return;
            cancelSession(target, true);
            tooltip.clear(target);
            final Session session = new Session(inspected.clone(),
                    target.getInventory().getHeldItemSlot(), otherHand(inspectedHand));
            session.completing = true;
            sessions.put(target.getUniqueId(), session);
            telemetry.recordInspectionStarted();
            inspect(target, session);
        }, null);
    }

    private void start(final Player player, final ItemStack inspected, final EquipmentSlot brushHand) {
        cancelSession(player, true);
        tooltip.clear(player);
        final Session session = new Session(inspected.clone(),
                player.getInventory().getHeldItemSlot(), brushHand);
        sessions.put(player.getUniqueId(), session);
        try {
            // Native Brush ticks ray-trace the world and can release in air or excavate a block.
            // Cancelled interactions resync native use; a held button repeats its input every four ticks.
            player.clearActiveItem();
            final ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, scheduled -> {
                if (sessions.get(player.getUniqueId()) != session) {
                    scheduled.cancel();
                    return;
                }
                if (!stillValid(player, session)) {
                    retire(player, session, true);
                    return;
                }
                if (++session.idleTicks > 8 && (!session.completing || session.finished)) {
                    retire(player, session, true);
                    return;
                }
                if (session.elapsed < INSPECTION_TICKS) session.elapsed++;
                if (!session.completing && session.elapsed % PRESENTATION_CADENCE == 0)
                    presentBrush(player, session.brushHand);
                // Completion requires a fresh held-button pulse after the minimum duration.
            }, () -> {
                if (sessions.remove(player.getUniqueId(), session)) {
                    telemetry.recordInspectionCancelled();
                }
            }, 1L, 1L);
            session.task = task;
            if (task == null || sessions.get(player.getUniqueId()) != session) {
                if (sessions.remove(player.getUniqueId(), session)) {
                    telemetry.recordInspectionCancelled();
                }
                if (task != null) task.cancel();
            } else {
                telemetry.recordInspectionStarted();
            }
        } catch (final RuntimeException rejected) {
            sessions.remove(player.getUniqueId(), session);
            player.clearActiveItem();
            telemetry.recordBehaviorRuntimeError();
        }
    }

    private boolean stillValid(final Player player, final Session session) {
        if (!player.isOnline() || player.isDead()
                || player.getInventory().getHeldItemSlot() != session.heldSlot
                || held(player, session.brushHand).getType() != Material.BRUSH) {
            return false;
        }
        final ItemStack current = held(player, otherHand(session.brushHand));
        return current.getAmount() == session.snapshot.getAmount()
                && current.isSimilar(session.snapshot);
    }

    private static void presentBrush(final Player player, final EquipmentSlot brushHand) {
        if (brushHand == EquipmentSlot.HAND) player.swingMainHand(); else player.swingOffHand();
        player.playSound(player.getLocation(), "minecraft:item.brush.brushing.generic", 0.35F, 1.0F);
        player.getWorld().spawnParticle(Particle.WHITE_SMOKE,
                player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.55D)),
                2, 0.08D, 0.08D, 0.08D, 0.005D);
    }

    private void beginCompletion(final Player player, final Session session) {
        if (sessions.get(player.getUniqueId()) != session) return;
        session.completing = true;
        player.clearActiveItem();
        inspect(player, session);
    }

    private void inspect(final Player player, final Session session) {
        final UUID playerId = player.getUniqueId();
        inspections.apply(playerId, session.snapshot).whenComplete((result, failure) -> {
            try {
                player.getScheduler().run(plugin, ignored -> {
                    if (sessions.get(playerId) != session) return;
                    session.finished = true;
                    if (session.task == null) sessions.remove(playerId, session);
                    if (failure != null || result == null) {
                        telemetry.recordBehaviorRuntimeError();
                        return;
                    }
                    if (!result.accepted() || !player.isOnline()) {
                        telemetry.recordInspectionCancelled();
                        return;
                    }
                    telemetry.recordInspectionCompleted();
                    if (result.unlockedNow()) {
                        telemetry.recordArchaeologyUnlock();
                        player.sendMessage(Component.text(
                                        "A régi tárgyakon hagyott nyomok egyre többet mondanak neked.",
                                        NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, true));
                        player.sendMessage(Component.text("Régészeti jártasság: 1",
                                        NamedTextColor.GOLD)
                                .decoration(TextDecoration.BOLD, true)
                                .decoration(TextDecoration.ITALIC, false));
                    }
                    final ItemStack current = held(player, otherHand(session.brushHand));
                    if (current.getAmount() != session.snapshot.getAmount()
                            || !current.isSimilar(session.snapshot)) return;
                    final List<String> observations = result.visibleFacts().stream()
                            .map(TrashArchaeologyFactEngine.Fact::text).toList();
                    if (!tooltip.show(player, otherHand(session.brushHand), session.snapshot, observations)) {
                        telemetry.recordTooltipTextFallback();
                        player.sendMessage(Component.text("Régészeti megfigyelések",
                                NamedTextColor.GOLD));
                        observations.forEach(line -> player.sendMessage(
                                Component.text("• " + line, NamedTextColor.GRAY)));
                    }
                }, () -> {
                    if (sessions.remove(playerId, session)) {
                        telemetry.recordInspectionCancelled();
                    }
                });
            } catch (final RuntimeException rejected) {
                if (sessions.remove(playerId, session)) {
                    telemetry.recordInspectionCancelled();
                }
                telemetry.recordBehaviorRuntimeError();
            }
        });
    }

    private void cancelSession(final Player player, final boolean clearActiveItem) {
        final Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        if (!session.completing) telemetry.recordInspectionCancelled();
        final ScheduledTask task = session.task;
        if (task != null) task.cancel();
        if (clearActiveItem) player.clearActiveItem();
    }

    private void retire(final Player player, final Session session,
                        final boolean clearActiveItem) {
        if (!sessions.remove(player.getUniqueId(), session)) return;
        if (!session.completing) telemetry.recordInspectionCancelled();
        final ScheduledTask task = session.task;
        if (task != null) task.cancel();
        if (clearActiveItem) player.clearActiveItem();
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        final Session session = sessions.remove(playerId);
        if (session != null) {
            telemetry.recordInspectionCancelled();
            if (session.task != null) session.task.cancel();
        }
        tooltip.clearPlayerState(playerId);
    }

    public void shutdown() {
        sessions.values().forEach(session -> {
            telemetry.recordInspectionCancelled();
            if (session.task != null) session.task.cancel();
        });
        sessions.clear();
        tooltip.shutdown();
    }

    private static final class Session {
        private final ItemStack snapshot;
        private final int heldSlot;
        private final EquipmentSlot brushHand;
        private volatile ScheduledTask task;
        private volatile boolean completing;
        private int elapsed;
        private int idleTicks;
        private boolean finished;

        private Session(final ItemStack snapshot, final int heldSlot, final EquipmentSlot brushHand) {
            this.brushHand = brushHand;
            this.snapshot = snapshot;
            this.heldSlot = heldSlot;
        }
    }
}
