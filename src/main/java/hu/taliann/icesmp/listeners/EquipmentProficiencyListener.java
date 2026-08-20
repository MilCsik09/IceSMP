package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.itemization.EquipmentProficiencyPolicy;
import hu.taliann.icesmp.itemization.EquipmentProficiencyService;
import hu.taliann.icesmp.itemization.EquipmentRehomeTransaction;
import hu.taliann.icesmp.itemization.ItemIdentityService;
import hu.taliann.icesmp.itemization.ItemTemplate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cancellable armor equip gates plus a fail-closed catch-all for every equipment mutation. */
public final class EquipmentProficiencyListener implements Listener {
    private static final long FEEDBACK_COOLDOWN_MILLIS = 1_500L;

    private final JavaPlugin plugin;
    private final EquipmentProficiencyService proficiency;
    private final ItemIdentityService identities;
    private final Map<UUID, Long> lastHint = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> reconciling = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> pendingEquipmentReconcile = ConcurrentHashMap.newKeySet();

    public EquipmentProficiencyListener(final JavaPlugin plugin,
                                        final EquipmentProficiencyService proficiency,
                                        final ItemIdentityService identities) {
        this.plugin = plugin;
        this.proficiency = proficiency;
        this.identities = identities;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack candidate = null;
        ItemTemplate.Slot targetSlot = null;
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            targetSlot = armorSlot(event.getRawSlot());
            candidate = event.getCursor();
            if (event.getHotbarButton() >= 0) candidate = player.getInventory().getItem(event.getHotbarButton());
        } else if (event.isShiftClick()
                && event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            candidate = event.getCurrentItem();
            final ItemIdentityService.Inspection inspection = identities.inspect(candidate);
            if (inspection.status() == ItemIdentityService.Status.VALID
                    && inspection.template().isArmorFamilyEquipment()) {
                targetSlot = inspection.template().slot();
            }
        }
        final Denial denial = denial(player, candidate, targetSlot);
        if (denial == null) return;
        event.setCancelled(true);
        notify(player, denial.decision());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        for (final Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            if (event.getView().getSlotType(entry.getKey()) != InventoryType.SlotType.ARMOR) continue;
            final Denial denial = denial(player, entry.getValue(), armorSlot(entry.getKey()));
            if (denial == null) continue;
            event.setCancelled(true);
            notify(player, denial.decision());
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(final BlockDispenseArmorEvent event) {
        if (!(event.getTargetEntity() instanceof Player player)) return;
        final ItemIdentityService.Inspection inspection = identities.inspect(event.getItem());
        if (inspection.status() != ItemIdentityService.Status.VALID
                || !inspection.template().isArmorFamilyEquipment()) return;
        final Denial denial = denial(player, event.getItem(), inspection.template().slot());
        if (denial == null) return;
        event.setCancelled(true);
        notify(player, denial.decision());
    }

    /**
     * Paper's equipment event is the common catch-all for right click, dispenser, hotbar/main-hand
     * and off-hand changes. Every represented slot schedules the same reconciliation; only armor
     * family denials are physically rehomed after the vanilla mutation settles. Weapons/offhands may
     * remain physically present but are attribute/effect-suppressed by the central authority.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentChanged(
            final io.papermc.paper.event.entity.EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || reconciling.contains(player.getUniqueId())) return;
        boolean deniedArmor = false;
        for (final Map.Entry<org.bukkit.inventory.EquipmentSlot,
                io.papermc.paper.event.entity.EntityEquipmentChangedEvent.EquipmentChange> entry
                : event.getEquipmentChanges().entrySet()) {
            final ItemTemplate.Slot slot = slot(entry.getKey());
            if (slot == null) continue;
            final ItemStack equipped = entry.getValue().newItem();
            final Denial denial = denial(player, equipped, slot);
            if (denial == null) {
                continue;
            }
            if (!isArmorSlot(entry.getKey())) continue;
            deniedArmor = true;
            notify(player, denial.decision());
        }
        if (deniedArmor) {
            scheduleEquipmentReconcile(player);
        } else {
            proficiency.reconcileNextTick(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        proficiency.reconcile(event.getPlayer());
        scheduleReconcile(event.getPlayer(), 20L);
        scheduleReconcile(event.getPlayer(), 100L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(final PlayerRespawnEvent event) { scheduleReconcile(event.getPlayer(), 1L); }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) { clear(event.getPlayer()); }

    @EventHandler
    public void onKick(final PlayerKickEvent event) { clear(event.getPlayer()); }

    private Denial denial(final Player player, final ItemStack item,
                          final ItemTemplate.Slot targetSlot) {
        final ItemIdentityService.Inspection inspection = identities.inspect(item);
        if (inspection.status() != ItemIdentityService.Status.VALID
                || !inspection.template().isArmorFamilyEquipment()) return null;
        final EquipmentProficiencyPolicy.Decision decision = proficiency.decision(player, inspection.template());
        if (targetSlot != null && inspection.template().slot() != targetSlot) {
            return new Denial(inspection, new EquipmentProficiencyPolicy.Decision(
                    EquipmentProficiencyPolicy.Status.INVALID_TEMPLATE,
                    inspection.template().armorFamily(), decision.classFamily()));
        }
        return proficiency.profileReady(player) && decision.allowed() ? null : new Denial(inspection, decision);
    }

    private void scheduleEquipmentReconcile(final Player player) {
        final UUID playerId = player.getUniqueId();
        if (!pendingEquipmentReconcile.add(playerId)) return;
        try {
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled =
                    player.getScheduler().runDelayed(plugin, task -> {
                        pendingEquipmentReconcile.remove(playerId);
                        reconcileEquipmentChange(player);
                    }, () -> pendingEquipmentReconcile.remove(playerId), 1L);
            if (scheduled == null) pendingEquipmentReconcile.remove(playerId);
        } catch (final RuntimeException rejected) {
            pendingEquipmentReconcile.remove(playerId);
        }
    }

    private void reconcileEquipmentChange(final Player player) {
        final UUID playerId = player.getUniqueId();
        if (!player.isOnline() || !reconciling.add(playerId)) return;
        try {
            for (final org.bukkit.inventory.EquipmentSlot slot : java.util.List.of(
                    org.bukkit.inventory.EquipmentSlot.HEAD,
                    org.bukkit.inventory.EquipmentSlot.CHEST,
                    org.bukkit.inventory.EquipmentSlot.LEGS,
                    org.bukkit.inventory.EquipmentSlot.FEET)) {
                final ItemStack equipped = getArmor(player, slot);
                final Denial denial = denial(player, equipped, slot(slot));
                if (denial == null) continue;
                identities.setEquipmentSuppressed(equipped, denial.inspection().template(),
                        denial.inspection().instance(), true);
                final boolean rehomed = EquipmentRehomeTransaction.rehome(
                        new EquipmentRehomeTransaction.Adapter() {
                            @Override public ItemStack equipped() { return getArmor(player, slot); }
                            @Override public ItemStack[] storageContents() {
                                return player.getInventory().getStorageContents();
                            }
                            @Override public void setEquipped(final ItemStack item) {
                                setArmor(player, slot, item);
                            }
                            @Override public Map<Integer, ItemStack> addToStorage(final ItemStack item) {
                                return player.getInventory().addItem(item);
                            }
                            @Override public void restoreStorage(final ItemStack[] snapshot) {
                                player.getInventory().setStorageContents(snapshot);
                            }
                        });
                if (!rehomed) {
                    player.sendActionBar(proficiency.denialMessage(
                            new EquipmentProficiencyPolicy.Decision(
                                    EquipmentProficiencyPolicy.Status.INVALID_TEMPLATE,
                                    denial.inspection().template().armorFamily(),
                                    denial.decision().classFamily())));
                }
                notify(player, denial.decision());
            }
        } finally {
            reconciling.remove(playerId);
            proficiency.reconcileNextTick(player);
        }
    }

    private void scheduleReconcile(final Player player, final long delay) {
        try {
            player.getScheduler().runDelayed(plugin, task -> proficiency.reconcile(player), null, delay);
        } catch (final RuntimeException ignored) {
            // Owner-thread rejection leaves every canonical consumer fail-closed.
        }
    }

    private void notify(final Player player, final EquipmentProficiencyPolicy.Decision decision) {
        final long now = System.currentTimeMillis();
        final Long previous = lastHint.put(player.getUniqueId(), now);
        if (previous != null && now - previous < FEEDBACK_COOLDOWN_MILLIS) return;
        final net.kyori.adventure.text.Component message = proficiency.denialMessage(decision);
        if (org.bukkit.Bukkit.isOwnedByCurrentRegion(player)) {
            player.sendActionBar(message);
            return;
        }
        player.getScheduler().run(plugin, task -> player.sendActionBar(message), null);
    }

    private void clear(final Player player) {
        lastHint.remove(player.getUniqueId());
        reconciling.remove(player.getUniqueId());
        pendingEquipmentReconcile.remove(player.getUniqueId());
    }

    private static ItemTemplate.Slot armorSlot(final int rawSlot) {
        return switch (rawSlot) {
            case 5 -> ItemTemplate.Slot.HEAD;
            case 6 -> ItemTemplate.Slot.CHEST;
            case 7 -> ItemTemplate.Slot.LEGS;
            case 8 -> ItemTemplate.Slot.FEET;
            default -> null;
        };
    }

    private static ItemTemplate.Slot slot(final org.bukkit.inventory.EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ItemTemplate.Slot.HEAD;
            case CHEST -> ItemTemplate.Slot.CHEST;
            case LEGS -> ItemTemplate.Slot.LEGS;
            case FEET -> ItemTemplate.Slot.FEET;
            case HAND -> ItemTemplate.Slot.MAIN_HAND;
            case OFF_HAND -> ItemTemplate.Slot.OFF_HAND;
            default -> null;
        };
    }

    private static void setArmor(final Player player,
                                 final org.bukkit.inventory.EquipmentSlot slot,
                                 final ItemStack item) {
        switch (slot) {
            case HEAD -> player.getInventory().setHelmet(item);
            case CHEST -> player.getInventory().setChestplate(item);
            case LEGS -> player.getInventory().setLeggings(item);
            case FEET -> player.getInventory().setBoots(item);
            default -> { }
        }
    }

    private static ItemStack getArmor(final Player player,
                                      final org.bukkit.inventory.EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> player.getInventory().getHelmet();
            case CHEST -> player.getInventory().getChestplate();
            case LEGS -> player.getInventory().getLeggings();
            case FEET -> player.getInventory().getBoots();
            default -> null;
        };
    }

    private static boolean isArmorSlot(final org.bukkit.inventory.EquipmentSlot slot) {
        return slot == org.bukkit.inventory.EquipmentSlot.HEAD
                || slot == org.bukkit.inventory.EquipmentSlot.CHEST
                || slot == org.bukkit.inventory.EquipmentSlot.LEGS
                || slot == org.bukkit.inventory.EquipmentSlot.FEET;
    }

    private record Denial(ItemIdentityService.Inspection inspection,
                          EquipmentProficiencyPolicy.Decision decision) { }
}
