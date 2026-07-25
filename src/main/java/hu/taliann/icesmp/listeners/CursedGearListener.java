package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.CursedGearService;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B54 — az Átkozott felszerelés viselkedése:
 * <ul>
 *   <li><b>Erő:</b> viselt/forgatott átkozott darabonként kimenő sebzés-bónusz (cap-pal);</li>
 *   <li><b>Elköteleződés:</b> az átkozott páncél a páncél-slotból NEM vehető ki — csak a
 *       rituálé-oltár Átok-törése után;</li>
 *   <li><b>Tudatos felvétel:</b> az első felhelyezési kísérlet (páncél-slotba tétel vagy
 *       shift-katt) figyelmeztet, és csak a {@code confirm-seconds}-on belüli második
 *       kísérlet erősíti meg (a buktató-követelmény szerinti megerősítő lépés).</li>
 * </ul>
 * Folia: minden esemény a játékos saját régió-szálán fut; a megerősítés-várólista
 * concurrent map, kilépéskor takarítva ({@code PlayerSessionCleanupListener} nélkül is
 * önlejáró — időbélyeges).
 */
public final class CursedGearListener implements Listener {

    private final CursedGearService cursedGearService;
    private final MessageManager messageManager;
    /** Felvétel-megerősítés: UUID → az ablak lejárata (millis). Önlejáró, nem szivárog tartósan. */
    private final Map<UUID, Long> equipConfirmUntil = new ConcurrentHashMap<>();

    public CursedGearListener(final CursedGearService cursedGearService, final MessageManager messageManager) {
        this.cursedGearService = cursedGearService;
        this.messageManager = messageManager;
    }

    /** Az átok ereje: kimenő sebzés-bónusz darabonként, plafonnal. */
    @EventHandler(ignoreCancelled = true)
    public void onDealDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !cursedGearService.isEnabled()) {
            return;
        }
        final int pieces = cursedGearService.cursedPieceCount(attacker);
        if (pieces <= 0) {
            return;
        }
        final double bonus = Math.min(cursedGearService.damageBonusCap(),
                pieces * cursedGearService.damageBonusPerPiece());
        if (bonus > 0.0D) {
            event.setDamage(event.getDamage() * (1.0D + bonus));
        }
    }

    /** A levételi zár + a tudatos-felvétel megerősítés (a saját inventory-nézetben). */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !cursedGearService.isEnabled()) {
            return;
        }
        // 1) LEVÉTEL-ZÁR: átkozott tárgy a páncél-slotban — a kattintás (kivétel/csere) tiltott.
        if (event.getSlotType() == InventoryType.SlotType.ARMOR
                && cursedGearService.isCursed(event.getCurrentItem())) {
            event.setCancelled(true);
            player.sendActionBar(messageManager.getMessage("cursed-gear-locked",
                    "<dark_red>☠ Az átok nem ereszt — csak az oltár Átok-törése oldhatja le.</dark_red>"));
            return;
        }
        // 2) TUDATOS FELVÉTEL: átkozott páncél kerülne a páncél-slotba (odatétel vagy
        // shift-katt) — az első kísérlet figyelmeztet, a gyors második megerősít.
        final boolean placingIntoArmor = event.getSlotType() == InventoryType.SlotType.ARMOR
                && cursedGearService.isCursed(event.getCursor());
        final boolean shiftEquipping = event.isShiftClick()
                && cursedGearService.isCursed(event.getCurrentItem())
                && isArmorPiece(event.getCurrentItem())
                && event.getView().getTopInventory().getType() == InventoryType.CRAFTING;
        if (!placingIntoArmor && !shiftEquipping) {
            return;
        }
        final long now = System.currentTimeMillis();
        // Lejárt (soha meg nem erősített) bejegyzések söprése — a map nem szivároghat.
        equipConfirmUntil.values().removeIf(until -> until < now);
        final Long confirmedUntil = equipConfirmUntil.get(player.getUniqueId());
        if (confirmedUntil != null && confirmedUntil > now) {
            equipConfirmUntil.remove(player.getUniqueId());
            return; // Megerősítve — a felvétel (és vele az elköteleződés) megtörténik.
        }
        event.setCancelled(true);
        equipConfirmUntil.put(player.getUniqueId(), now + 5_000L);
        player.sendActionBar(messageManager.getMessage("cursed-gear-confirm",
                "<dark_red>☠ Ez a tárgy ÁTKOZOTT: felvéve nem veheted le szabadon! Kattints újra 5 mp-en belül, ha vállalod.</dark_red>"));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4F, 1.6F);
    }

    /**
     * Zárja a felvétel-megerősítés kiskapuját: az {@code InventoryClickEvent} CSAK a
     * páncél-slot kattintását és a shift-felvételt látja, a legtermészetesebb módot — az
     * armor JOBB-KATTAL felvételét — nem. Ezen az úton az átkozott páncél megerősítés nélkül
     * került fel, és onnan már a levétel-zár tartotta bent.
     *
     * <p>Az {@code EntityEquipmentChangedEvent} MINDEN felszerelés-váltást lát, függetlenül
     * attól, hogyan történt. Az event nem cancel-elhető, ezért a felkerült átkozott darabot
     * visszavesszük (az inventoryba, tele hátizsáknál a földre) — a megerősítés-ablak
     * szemantikája változatlan: az első kísérlet figyelmeztet, a második 5 mp-en belül vállal.
     *
     * <p>Folia: az event az entitás SAJÁT régió-szálán fut, tehát az inventory-írás itt biztonságos.
     */
    @EventHandler
    public void onEquipmentChanged(final io.papermc.paper.event.entity.EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player) || !cursedGearService.isEnabled()) {
            return;
        }
        final long now = System.currentTimeMillis();
        equipConfirmUntil.values().removeIf(until -> until < now);
        final Long confirmedUntil = equipConfirmUntil.get(player.getUniqueId());
        if (confirmedUntil != null && confirmedUntil > now) {
            equipConfirmUntil.remove(player.getUniqueId());
            return; // Megerősítve — a felvétel megtörténhet.
        }
        boolean reverted = false;
        for (final Map.Entry<org.bukkit.inventory.EquipmentSlot,
                io.papermc.paper.event.entity.EntityEquipmentChangedEvent.EquipmentChange> change
                : event.getEquipmentChanges().entrySet()) {
            final org.bukkit.inventory.EquipmentSlot slot = change.getKey();
            if (slot == org.bukkit.inventory.EquipmentSlot.HAND
                    || slot == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
                continue; // A kézben tartás nem „viselés" — az átok a páncél-slotokra szól.
            }
            final ItemStack equipped = change.getValue().newItem();
            if (equipped == null || equipped.getType().isAir() || !cursedGearService.isCursed(equipped)) {
                continue;
            }
            final ItemStack copy = equipped.clone();
            player.getEquipment().setItem(slot, null);
            for (final ItemStack overflow : player.getInventory().addItem(copy).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
            reverted = true;
        }
        if (!reverted) {
            return;
        }
        equipConfirmUntil.put(player.getUniqueId(), now + 5_000L);
        player.sendActionBar(messageManager.getMessage("cursed-gear-confirm",
                "<dark_red>☠ Ez a tárgy ÁTKOZOTT: felvéve nem veheted le szabadon! Vedd fel újra 5 mp-en belül, ha vállalod.</dark_red>"));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4F, 1.6F);
    }

    private static boolean isArmorPiece(final ItemStack item) {
        final String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.equals("ELYTRA") || name.equals("TURTLE_HELMET") || name.equals("CARVED_PUMPKIN");
    }
}
