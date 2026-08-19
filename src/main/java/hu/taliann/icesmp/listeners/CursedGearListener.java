package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.itemization.ItemTemplate;
import hu.taliann.icesmp.managers.CursedGearService;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B54 — az Átkozott felszerelés viselkedése. A curse-bónusz és a levételi lock csak olyan
 * canonical gearre érvényes, amely az EquipmentProficiencyService szerint ténylegesen ACTIVE;
 * BASIC/NOT_MANAGED gear megőrzi a korábbi policyját.
 */
public final class CursedGearListener implements Listener {

    private final CursedGearService cursedGearService;
    private final MessageManager messageManager;
    private final Map<UUID, Long> equipConfirmUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> equipGrantedUntil = new ConcurrentHashMap<>();
    private final Set<UUID> reconciling = ConcurrentHashMap.newKeySet();
    private static final long GRANT_MILLIS = 2_000L;

    public CursedGearListener(final CursedGearService cursedGearService, final MessageManager messageManager) {
        this.cursedGearService = cursedGearService;
        this.messageManager = messageManager;
    }

    /** Az átok ereje: ACTIVE darabonkénti kimenő sebzés-bónusz, plafonnal. */
    @EventHandler(ignoreCancelled = true)
    public void onDealDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !cursedGearService.isEnabled()) {
            return;
        }
        final int pieces = cursedGearService.cursedPieceCount(attacker);
        if (pieces <= 0) return;
        final double bonus = Math.min(cursedGearService.damageBonusCap(),
                pieces * cursedGearService.damageBonusPerPiece());
        if (bonus > 0.0D) event.setDamage(event.getDamage() * (1.0D + bonus));
    }

    /** A levételi zár + a tudatos-felvétel megerősítés (a saját inventory-nézetben). */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !cursedGearService.isEnabled()) {
            return;
        }
        final ItemTemplate.Slot currentSlot = armorSlot(event.getCurrentItem());
        // Wrong-family / managed-invalid / runtime-suppressed cursed gear nem softlockolhatja a slotot.
        if (event.getSlotType() == InventoryType.SlotType.ARMOR && currentSlot != null
                && cursedGearService.isActiveCurse(player, event.getCurrentItem(), currentSlot)) {
            event.setCancelled(true);
            player.sendActionBar(messageManager.getMessage("cursed-gear-locked",
                    "<dark_red>☠ Az átok nem ereszt — csak az oltár Átok-törése oldhatja le.</dark_red>"));
            return;
        }

        final ItemTemplate.Slot cursorSlot = armorSlot(event.getCursor());
        final boolean placingIntoArmor = event.getSlotType() == InventoryType.SlotType.ARMOR
                && cursorSlot != null
                && cursedGearService.isActiveCurse(player, event.getCursor(), cursorSlot);
        final ItemTemplate.Slot shiftedSlot = armorSlot(event.getCurrentItem());
        final boolean shiftEquipping = event.isShiftClick() && shiftedSlot != null
                && cursedGearService.isActiveCurse(player, event.getCurrentItem(), shiftedSlot)
                && event.getView().getTopInventory().getType() == InventoryType.CRAFTING;
        if (!placingIntoArmor && !shiftEquipping) return;

        final long now = System.currentTimeMillis();
        sweepExpired(now);
        final Long confirmedUntil = equipConfirmUntil.get(player.getUniqueId());
        if (confirmedUntil != null && confirmedUntil > now) {
            equipConfirmUntil.remove(player.getUniqueId());
            equipGrantedUntil.put(player.getUniqueId(), now + GRANT_MILLIS);
            return;
        }
        event.setCancelled(true);
        warnAndArm(player, now);
    }

    /**
     * Covers right-click/dispenser/plugin equipment changes. The physical slot is authoritative:
     * a rejected cursed item is moved from the live slot exactly once. Overflow is never world-
     * dropped; if storage cannot accept the unstackable gear, the remainder is restored to the
     * physical slot and its curse is transiently suppressed so it stays inert and removable.
     */
    @EventHandler
    public void onEquipmentChanged(final io.papermc.paper.event.entity.EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player) || !cursedGearService.isEnabled()) return;
        final UUID playerId = player.getUniqueId();
        if (!reconciling.add(playerId)) return;
        try {
            final long now = System.currentTimeMillis();
            sweepExpired(now);
            final Map<EquipmentSlot, ItemStack> cursedSlots = new java.util.LinkedHashMap<>(4);
            for (final Map.Entry<EquipmentSlot,
                    io.papermc.paper.event.entity.EntityEquipmentChangedEvent.EquipmentChange> change
                    : event.getEquipmentChanges().entrySet()) {
                final ItemTemplate.Slot canonicalSlot = canonicalSlot(change.getKey());
                if (canonicalSlot == null || canonicalSlot == ItemTemplate.Slot.MAIN_HAND
                        || canonicalSlot == ItemTemplate.Slot.OFF_HAND) continue;
                final ItemStack equipped = change.getValue().newItem();
                cursedGearService.reconcileRuntimeSuppression(player, equipped, canonicalSlot);
                if (equipped == null || equipped.getType().isAir()
                        || !cursedGearService.isActiveCurse(player, equipped, canonicalSlot)) continue;
                cursedSlots.put(change.getKey(), equipped);
            }
            if (cursedSlots.isEmpty()) return;

            final Long granted = equipGrantedUntil.get(playerId);
            if (granted != null && granted > now) {
                equipGrantedUntil.remove(playerId);
                cursedSlots.keySet().forEach(slot ->
                        cursedGearService.clearRuntimeSuppression(player, canonicalSlot(slot)));
                return;
            }
            final Long confirmedUntil = equipConfirmUntil.get(playerId);
            if (confirmedUntil != null && confirmedUntil > now) {
                equipConfirmUntil.remove(playerId);
                cursedSlots.keySet().forEach(slot ->
                        cursedGearService.clearRuntimeSuppression(player, canonicalSlot(slot)));
                return;
            }

            for (final EquipmentSlot equipmentSlot : cursedSlots.keySet()) {
                final ItemTemplate.Slot slot = canonicalSlot(equipmentSlot);
                final ItemStack physical = player.getEquipment().getItem(equipmentSlot);
                if (slot == null || physical == null || physical.getType().isAir()
                        || !cursedGearService.isActiveCurse(player, physical, slot)) continue;
                final ItemStack moving = physical.clone();
                player.getEquipment().setItem(equipmentSlot, null);
                final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(moving);
                if (!leftovers.isEmpty()) {
                    final ItemStack remainder = leftovers.values().iterator().next();
                    player.getEquipment().setItem(equipmentSlot, remainder);
                    cursedGearService.suppressRuntimeCurse(player, remainder, slot);
                } else {
                    cursedGearService.clearRuntimeSuppression(player, slot);
                }
            }
            warnAndArm(player, now);
        } finally {
            reconciling.remove(playerId);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID id = event.getPlayer().getUniqueId();
        equipConfirmUntil.remove(id);
        equipGrantedUntil.remove(id);
        reconciling.remove(id);
        cursedGearService.clearRuntimeState(id);
    }

    private void sweepExpired(final long now) {
        equipConfirmUntil.values().removeIf(until -> until < now);
        equipGrantedUntil.values().removeIf(until -> until < now);
    }

    private void warnAndArm(final Player player, final long now) {
        final long window = cursedGearService.confirmMillis();
        equipConfirmUntil.put(player.getUniqueId(), now + window);
        player.sendActionBar(messageManager.getMessage("cursed-gear-confirm",
                "<dark_red>☠ Ez a tárgy ÁTKOZOTT: felvéve nem veheted le szabadon! "
                        + "Vedd fel újra {seconds} mp-en belül, ha vállalod.</dark_red>",
                Map.of("seconds", String.valueOf(Math.max(1L, window / 1000L)))));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4F, 1.6F);
    }

    private static ItemTemplate.Slot canonicalSlot(final EquipmentSlot slot) {
        if (slot == null) return null;
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

    private static ItemTemplate.Slot armorSlot(final ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        final String name = item.getType().name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET") || name.equals("CARVED_PUMPKIN")) {
            return ItemTemplate.Slot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) return ItemTemplate.Slot.CHEST;
        if (name.endsWith("_LEGGINGS")) return ItemTemplate.Slot.LEGS;
        if (name.endsWith("_BOOTS")) return ItemTemplate.Slot.FEET;
        return null;
    }
}
