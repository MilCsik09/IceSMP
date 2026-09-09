package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.itemization.ItemPrototypePolicy;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Shared native use entry point. Event disposition is separate from the actual owner-local behavior. */
public final class TrashAnomalyActivationService {
    public enum Gesture { LEFT, RIGHT }
    public enum Result {
        IGNORED(false, false), CONTINUE(false, false), DENY_ITEM(true, false), CANCEL(true, true), REFUSED(true, true);
        private final boolean denyItem, cancel;
        Result(boolean denyItem, boolean cancel) { this.denyItem = denyItem; this.cancel = cancel; }
        public boolean denyItem() { return denyItem; }
        public boolean cancel() { return cancel; }
    }
    @FunctionalInterface interface NativeUse {
        Result apply(Player player, EquipmentSlot hand, Gesture gesture, Block target, ItemStack held,
                TrashAnomalyBehavior behavior, BooleanSupplier lifetime);
    }
    private final TrashCatalog catalog;
    private final TrashItemFactory items;
    private final NativeUse nativeUse;
    private final Lifecycle lifecycle;

    static final class Lifecycle {
        private final BooleanSupplier pluginOpen;
        private final AtomicLong generation = new AtomicLong();
        private volatile boolean open;
        Lifecycle(BooleanSupplier pluginOpen) { this.pluginOpen = Objects.requireNonNull(pluginOpen); }
        void start() { generation.incrementAndGet(); open = true; }
        void close() { open = false; generation.incrementAndGet(); }
        boolean active() { return open && pluginOpen.getAsBoolean(); }
        BooleanSupplier lifetime() {
            final long expected = generation.get();
            return () -> active() && generation.get() == expected;
        }
    }

    TrashAnomalyActivationService(TrashCatalog catalog, TrashItemFactory items, BooleanSupplier pluginOpen, NativeUse nativeUse) {
        this.catalog = Objects.requireNonNull(catalog); this.items = Objects.requireNonNull(items);
        this.lifecycle = new Lifecycle(pluginOpen); this.nativeUse = Objects.requireNonNull(nativeUse);
    }
    void start() { lifecycle.start(); }
    void close() { lifecycle.close(); }
    /** A delayed native consequence belongs to the lifecycle that scheduled it, including cooperative restart. */
    BooleanSupplier lifetime() {
        return lifecycle.lifetime();
    }
    public Optional<TrashAnomalyBehavior> behaviorOf(ItemStack item) {
        if (ItemPrototypePolicy.direct(item) || !items.isBaseIdentity(item)) return Optional.empty();
        final String id = items.idOf(item).orElse(null);
        if (id == null) return Optional.empty();
        final var definition = catalog.require(id);
        if (definition.internalKind() != TrashKind.ANOMALY) return Optional.empty();
        return Optional.of(TrashAnomalyBehavior.parse(definition.behavior()));
    }
    /** No event is synthesized. Live arguments are used only during this owner callback and never retained. */
    public Result useOnOwner(Player player, EquipmentSlot hand, Gesture gesture, Block target,
            ItemStack expected, BooleanSupplier admission) {
        Objects.requireNonNull(player); Objects.requireNonNull(gesture); Objects.requireNonNull(admission);
        final var active = lifecycle.lifetime();
        if (!active.getAsBoolean() || !Bukkit.isOwnedByCurrentRegion(player)) return Result.REFUSED;
        if (!player.isOnline() || !player.isValid() || player.isDead() || hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND)
            return Result.REFUSED;
        if (target != null && !Bukkit.isOwnedByCurrentRegion(target)) return Result.REFUSED;
        final ItemStack held = hand == EquipmentSlot.HAND ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        if (expected == null || !expected.equals(held)) return Result.REFUSED;
        final var behavior = behaviorOf(held);
        if (behavior.isEmpty()) return Result.IGNORED;
        final var before = held.clone();
        if (!admission.getAsBoolean()) return Result.REFUSED;
        if (!active.getAsBoolean() || !Bukkit.isOwnedByCurrentRegion(player) || !player.isOnline() || !player.isValid() || player.isDead()
                || target != null && !Bukkit.isOwnedByCurrentRegion(target)) return Result.REFUSED;
        final ItemStack current = hand == EquipmentSlot.HAND ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        if (!before.equals(current)) return Result.REFUSED;
        return Objects.requireNonNull(nativeUse.apply(player, hand, gesture, target, current, behavior.orElseThrow(), active));
    }
}
