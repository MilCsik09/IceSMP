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
    /**
     * KIADOTT engedély: UUID → lejárat. A megerősítés-ablaktól KÜLÖN kell tartani, mert a két
     * kapu két különböző eventet lát: a klikk-út a megerősítést az {@code InventoryClickEvent}-ben
     * fogyasztja el, a tényleges felszerelés-váltást viszont már az
     * {@code EntityEquipmentChangedEvent} látja. Egyetlen közös bejegyzésen a két handler
     * kioltotta egymást (a klikk elvette, az equipment-oldal megerősítetlennek látta és
     * visszavette a darabot) — így átkozott páncél EGYETLEN úton sem maradt fent.
     */
    private final Map<UUID, Long> equipGrantedUntil = new ConcurrentHashMap<>();
    /** Az engedélynek csak a következő tickig kell élnie (ott fut le az equipment-event). */
    private static final long GRANT_MILLIS = 2_000L;

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
        sweepExpired(now);
        final Long confirmedUntil = equipConfirmUntil.get(player.getUniqueId());
        if (confirmedUntil != null && confirmedUntil > now) {
            equipConfirmUntil.remove(player.getUniqueId());
            // A felvételt itt engedjük át, de a felszerelés-váltást már a másik handler látja:
            // engedélyt hagyunk neki, különben megerősítetlennek hinné és visszavenné a darabot.
            equipGrantedUntil.put(player.getUniqueId(), now + GRANT_MILLIS);
            return; // Megerősítve — a felvétel (és vele az elköteleződés) megtörténik.
        }
        event.setCancelled(true);
        warnAndArm(player, now);
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
     * szemantikája változatlan: az első kísérlet figyelmeztet, az ablakon belüli második vállal.
     *
     * <p>A visszavétel MAGA is felszerelés-váltás, ezért ez a handler újra lefut rá. Emiatt
     * kötelező, hogy a megerősítés-ellenőrzés az átok-szűrés UTÁN álljon: a visszavétel utáni
     * „air" váltásnál a szűrés üresen tér vissza, és a frissen kiadott megerősítés érintetlen marad.
     *
     * <p>Folia: az event az entitás SAJÁT régió-szálán fut, tehát az inventory-írás itt biztonságos.
     */
    @EventHandler
    public void onEquipmentChanged(final io.papermc.paper.event.entity.EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player) || !cursedGearService.isEnabled()) {
            return;
        }
        final long now = System.currentTimeMillis();
        sweepExpired(now);
        // ELŐBB azt kell eldönteni, került-e fel ÁTKOZOTT darab, és csak UTÁNA nyúlni a
        // megerősítéshez. Fordított sorrendben minden más felszerelés-váltás elfogyasztaná a
        // megerősítést — köztük a lentebbi visszavétel által kiváltott „air" váltás is, tehát a
        // rendszer a saját javítását ette volna meg, és a felvétel örökre hurokba került volna.
        final Map<org.bukkit.inventory.EquipmentSlot, ItemStack> cursedSlots = new java.util.LinkedHashMap<>(4);
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
            cursedSlots.put(slot, equipped);
        }
        if (cursedSlots.isEmpty()) {
            return;
        }

        final UUID playerId = player.getUniqueId();
        final Long granted = equipGrantedUntil.get(playerId);
        if (granted != null && granted > now) {
            equipGrantedUntil.remove(playerId); // A klikk-úton már megerősítette.
            return;
        }
        final Long confirmedUntil = equipConfirmUntil.get(playerId);
        if (confirmedUntil != null && confirmedUntil > now) {
            equipConfirmUntil.remove(playerId); // Második kísérlet az ablakon belül = vállalja.
            return;
        }
for (final Map.Entry<org.bukkit.inventory.EquipmentSlot, ItemStack> cursed : cursedSlots.entrySet()) {
            player.getEquipment().setItem(cursed.getKey(), null);
            for (final ItemStack overflow : player.getInventory().addItem(cursed.getValue().clone()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }
        warnAndArm(player, now);
    }

    /** Lejárt bejegyzések söprése MINDKÉT térképből — egyik sem szivároghat. */
    private void sweepExpired(final long now) {
        equipConfirmUntil.values().removeIf(until -> until < now);
        equipGrantedUntil.values().removeIf(until -> until < now);
    }

    /** Figyelmeztetés + a megerősítés-ablak felhúzása (mindkét kapu ugyanezt használja). */
    private void warnAndArm(final Player player, final long now) {
        final long window = cursedGearService.confirmMillis();
        equipConfirmUntil.put(player.getUniqueId(), now + window);
        player.sendActionBar(messageManager.getMessage("cursed-gear-confirm",
                "<dark_red>☠ Ez a tárgy ÁTKOZOTT: felvéve nem veheted le szabadon! "
                        + "Vedd fel újra {seconds} mp-en belül, ha vállalod.</dark_red>",
                Map.of("seconds", String.valueOf(Math.max(1L, window / 1000L)))));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4F, 1.6F);
    }

    private static boolean isArmorPiece(final ItemStack item) {
        final String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.equals("ELYTRA") || name.equals("TURTLE_HELMET") || name.equals("CARVED_PUMPKIN");
    }
}
