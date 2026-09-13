package hu.taliann.icesmp.listeners;

import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** A temporary footprint must remain owned until its restoration journal is acknowledged. */
public final class TemporaryBlockProtection implements Listener {
    private final Predicate<Block> protectedBlock;
    private final Predicate<EntityChangeBlockEvent> allowedChange;
    public TemporaryBlockProtection(final Predicate<Block> protectedBlock) {
        this(protectedBlock, ignored -> false);
    }
    public TemporaryBlockProtection(final Predicate<Block> protectedBlock,
                                    final Predicate<EntityChangeBlockEvent> allowedChange) {
        this.protectedBlock = java.util.Objects.requireNonNull(protectedBlock);
        this.allowedChange = java.util.Objects.requireNonNull(allowedChange);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (protectedBlock.test(event.getBlock()) || event instanceof BlockMultiPlaceEvent multi
                && multi.getReplacedBlockStates().stream().anyMatch(s -> protectedBlock.test(s.getBlock())))
            event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        if (protectedBlock.test(event.getBlock())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(org.bukkit.event.player.PlayerBucketFillEvent event) {
        if (protectedBlock.test(event.getBlock())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (event.getBlocks().stream().anyMatch(s -> protectedBlock.test(s.getBlock()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrow(org.bukkit.event.world.StructureGrowEvent event) {
        if (event.getBlocks().stream().anyMatch(s -> protectedBlock.test(s.getBlock()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent event) {
        if (event.getBlocks().stream().anyMatch(s -> protectedBlock.test(s.getBlock()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { if (protectedBlock.test(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (protectedBlock.test(event.getBlock()) || protectedBlock.test(event.getToBlock())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> protectedBlock.test(b)
                || protectedBlock.test(b.getRelative(event.getDirection())))
                || protectedBlock.test(event.getBlock().getRelative(event.getDirection()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> protectedBlock.test(b)
                || protectedBlock.test(b.getRelative(event.getDirection())))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) { event.blockList().removeIf(protectedBlock); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) { event.blockList().removeIf(protectedBlock); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (protectedBlock.test(event.getBlock()) && !allowedChange.test(event)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { if (protectedBlock.test(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) { if (protectedBlock.test(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) { if (protectedBlock.test(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) { if (protectedBlock.test(event.getBlock())) event.setCancelled(true); }
}
