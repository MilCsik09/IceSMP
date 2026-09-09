package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.itemization.ItemPrototypePolicy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.Supplier;

/** Physical custody only; native transformation, economic and equipment authorities also refuse prototypes. */
public final class ItemPrototypeProtectionListener implements Listener {
    private record StoredContainer(int x, int y, int z) { }
    private final JavaPlugin plugin;
    private final Supplier<UUID> primaryDeveloper;
    public ItemPrototypeProtectionListener(JavaPlugin plugin, Supplier<UUID> primaryDeveloper) {
        this.plugin = Objects.requireNonNull(plugin); this.primaryDeveloper = Objects.requireNonNull(primaryDeveloper);
    }
    private static boolean restricted(ItemStack item) { return ItemPrototypePolicy.scan(item) != ItemPrototypePolicy.Scan.CLEAN; }
    private UUID primary() { try { return primaryDeveloper.get(); } catch (RuntimeException unavailable) { return null; } }
    private boolean allowed(Player player, ItemStack item) {
        return ItemPrototypePolicy.allowedCustody(item, player.getUniqueId(), primary());
    }
    private void clean(Player player) {
        ItemPrototypePolicy.quarantinePlayer(player, primary());
    }
    private static ItemStack held(Player player, EquipmentSlot hand) { return player.getInventory().getItem(hand); }
    private static boolean hasHeld(Player player) {
        return restricted(player.getInventory().getItemInMainHand()) || restricted(player.getInventory().getItemInOffHand());
    }
    private void cleanNext(Player player) {
        final UUID id = player.getUniqueId(); final var scheduler = player.getScheduler();
        scheduler.run(plugin, task -> {
            final var current = Bukkit.getPlayer(id);
            if (current != null && Bukkit.isOwnedByCurrentRegion(current) && current.isOnline()) clean(current);
        }, null);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onJoin(PlayerJoinEvent event) { clean(event.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST) public void onRespawn(PlayerRespawnEvent event) { cleanNext(event.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST) public void onHeld(PlayerItemHeldEvent event) { clean(event.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST) public void onDrop(PlayerDropItemEvent event) {
        if (restricted(event.getItemDrop().getItemStack())) { event.setCancelled(true); cleanNext(event.getPlayer()); }
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(item -> ItemPrototypePolicy.scan(item) == ItemPrototypePolicy.Scan.PROTOTYPE);
        event.getItemsToKeep().removeIf(item -> ItemPrototypePolicy.scan(item) == ItemPrototypePolicy.Scan.PROTOTYPE);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onSpawn(ItemSpawnEvent event) {
        if (restricted(event.getEntity().getItemStack())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onPickup(EntityPickupItemEvent event) {
        if (!Bukkit.isOwnedByCurrentRegion(event.getItem())) { event.setCancelled(true); return; }
        if (restricted(event.getItem().getItemStack())) {
            event.setCancelled(true);
            if (ItemPrototypePolicy.scan(event.getItem().getItemStack()) == ItemPrototypePolicy.Scan.PROTOTYPE
                    && Bukkit.isOwnedByCurrentRegion(event.getItem())) event.getItem().remove();
        }
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onHopperPickup(InventoryPickupItemEvent event) {
        if (!Bukkit.isOwnedByCurrentRegion(event.getItem())) { event.setCancelled(true); return; }
        if (restricted(event.getItem().getItemStack())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onMerge(ItemMergeEvent event) {
        if (!Bukkit.isOwnedByCurrentRegion(event.getEntity())) { event.setCancelled(true); return; }
        if (restricted(event.getEntity().getItemStack())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onMove(InventoryMoveItemEvent event) {
        if (restricted(event.getItem())) { event.setCancelled(true); cleanContainer(event.getSource()); }
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onUse(PlayerInteractEvent event) {
        if (restricted(event.getItem())) { event.setCancelled(true); event.setUseItemInHand(Event.Result.DENY); event.setUseInteractedBlock(Event.Result.DENY); }
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onEntityUse(PlayerInteractEntityEvent event) {
        if (restricted(held(event.getPlayer(), event.getHand()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onEntityUseAt(PlayerInteractAtEntityEvent event) {
        if (restricted(held(event.getPlayer(), event.getHand()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onStand(PlayerArmorStandManipulateEvent event) {
        if (restricted(event.getPlayerItem()) || restricted(event.getArmorStandItem())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onConsume(PlayerItemConsumeEvent event) { if (restricted(event.getItem())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onPlace(BlockPlaceEvent event) { if (restricted(event.getItemInHand())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onDispense(BlockDispenseEvent event) { if (restricted(event.getItem())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onBreak(BlockBreakEvent event) { if (hasHeld(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onHarvest(PlayerHarvestBlockEvent event) { if (hasHeld(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onFish(PlayerFishEvent event) { if (hasHeld(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        if (hasHeld(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onLaunch(com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent event) {
        if (restricted(event.getItemStack()) || hasHeld(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onShoot(EntityShootBowEvent event) {
        if (restricted(event.getBow()) || restricted(event.getConsumable())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onLoadCrossbow(io.papermc.paper.event.entity.EntityLoadCrossbowEvent event) {
        if (restricted(event.getCrossbow())) { event.setCancelled(true); return; }
        if (event.getEntity() instanceof Player player) {
            if (!Bukkit.isOwnedByCurrentRegion(player)) { event.setCancelled(true); return; }
            for (final var item : player.getInventory().getContents()) {
                if (item != null && Set.of(Material.ARROW, Material.SPECTRAL_ARROW, Material.TIPPED_ARROW, Material.FIREWORK_ROCKET).contains(item.getType())
                        && restricted(item)) { event.setCancelled(true); return; }
            }
        }
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onBucketFill(PlayerBucketFillEvent event) { if (hasHeld(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onBucketEmpty(PlayerBucketEmptyEvent event) { if (hasHeld(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onBucketEntity(PlayerBucketEntityEvent event) { if (hasHeld(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void onCreative(InventoryCreativeEvent event) {
        if (restricted(event.getCursor()) || restricted(event.getCurrentItem())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onCraft(CraftItemEvent event) {
        if (Arrays.stream(event.getInventory().getMatrix()).anyMatch(ItemPrototypeProtectionListener::restricted)
                || restricted(event.getCurrentItem())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onSmith(SmithItemEvent event) {
        if (Arrays.stream(event.getInventory().getContents()).anyMatch(ItemPrototypeProtectionListener::restricted)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onCrafter(CrafterCraftEvent event) {
        if (restricted(event.getResult()) || event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter
                && Arrays.stream(crafter.getInventory().getContents()).anyMatch(ItemPrototypeProtectionListener::restricted)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onDrag(InventoryDragEvent event) {
        if (!restricted(event.getOldCursor())) return;
        if (!(event.getWhoClicked() instanceof Player player) || !allowed(player, event.getOldCursor())
                || event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                || event.getRawSlots().stream().anyMatch(slot -> !(event.getView().getInventory(slot) instanceof PlayerInventory))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        final var current = event.getCurrentItem(); final var cursor = event.getCursor();
        final var hotbar = event.getHotbarButton() < 0 ? null : player.getInventory().getItem(event.getHotbarButton());
        final var offhand = event.getClick() == ClickType.SWAP_OFFHAND ? player.getInventory().getItemInOffHand() : null;
        final var candidates = Arrays.asList(current, cursor, hotbar, offhand);
        if (candidates.stream().noneMatch(ItemPrototypeProtectionListener::restricted)) return;
        final boolean invalid = candidates.stream().anyMatch(item -> restricted(item) && !allowed(player, item));
        if (invalid || event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                || !(event.getClickedInventory() instanceof PlayerInventory)
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction().name().startsWith("DROP")
                || current != null && current.getType() == Material.BUNDLE || cursor != null && cursor.getType() == Material.BUNDLE) event.setCancelled(true);
        if (invalid) cleanNext(player);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) clean(player);
        cleanContainer(event.getInventory());
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) clean(player);
        cleanContainer(event.getInventory());
    }
    private void cleanContainer(Inventory inventory) {
        if (inventory instanceof PlayerInventory) return;
        final var holder = inventory.getHolder(false);
        if (holder instanceof Entity entity && !Bukkit.isOwnedByCurrentRegion(entity)) return;
        final var location = inventory.getLocation();
        if (location != null && !Bukkit.isOwnedByCurrentRegion(location)) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (ItemPrototypePolicy.scan(inventory.getItem(slot)) == ItemPrototypePolicy.Scan.PROTOTYPE) inventory.setItem(slot, null);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        final var chunk = event.getChunk(); final UUID world = chunk.getWorld().getUID();
        final var locations = new ArrayList<StoredContainer>();
        for (final var state : chunk.getTileEntities()) if (state instanceof org.bukkit.block.Container) {
            locations.add(new StoredContainer(state.getX(), state.getY(), state.getZ()));
        }
        cleanLoadedContainers(world, chunk.getX(), chunk.getZ(), List.copyOf(locations), 0);
    }
    private void cleanLoadedContainers(UUID worldId, int chunkX, int chunkZ, List<StoredContainer> locations, int offset) {
        if (offset >= locations.size()) return;
        final var world = Bukkit.getWorld(worldId);
        if (world == null) return;
        plugin.getServer().getRegionScheduler().run(plugin, world, chunkX, chunkZ, task -> {
            final var current = Bukkit.getWorld(worldId);
            if (current == null || !current.isChunkLoaded(chunkX, chunkZ)) return;
            final int end = Math.min(offset + 32, locations.size());
            for (int index = offset; index < end; index++) {
                final var at = locations.get(index);
                final var block = current.getBlockAt(at.x(), at.y(), at.z());
                if (Bukkit.isOwnedByCurrentRegion(block) && block.getState() instanceof org.bukkit.block.Container container) cleanContainer(container.getInventory());
            }
            cleanLoadedContainers(worldId, chunkX, chunkZ, locations, end);
        });
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onLoad(EntitiesLoadEvent event) {
        for (final var entity : event.getEntities()) {
            final UUID id = entity.getUniqueId();
            entity.getScheduler().run(plugin, task -> {
                final var current = Bukkit.getEntity(id);
                if (current == null || !Bukkit.isOwnedByCurrentRegion(current)) return;
                if (current instanceof Item item && ItemPrototypePolicy.scan(item.getItemStack()) == ItemPrototypePolicy.Scan.PROTOTYPE) item.remove();
                if (current instanceof ItemFrame frame && ItemPrototypePolicy.scan(frame.getItem()) == ItemPrototypePolicy.Scan.PROTOTYPE) frame.setItem(null);
                if (current instanceof InventoryHolder holder) cleanContainer(holder.getInventory());
                if (current instanceof LivingEntity living && !(living instanceof Player) && living.getEquipment() != null) {
                    for (final var slot : EquipmentSlot.values()) {
                        final var item = living.getEquipment().getItem(slot);
                        if (ItemPrototypePolicy.scan(item) == ItemPrototypePolicy.Scan.PROTOTYPE) living.getEquipment().setItem(slot, null);
                    }
                }
            }, null);
        }
    }
}
