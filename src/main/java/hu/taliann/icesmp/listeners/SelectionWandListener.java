package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ClaimManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * N24 — kijelölő-pálcák (teszter-kérés: a kézi pos1/pos2 „macerás"):
 * <ul>
 *   <li><b>Birtokmérő pálca</b> (claim, mindenkinek): BAL katt blokkra = 1. sarok,
 *       JOBB katt = 2. sarok (ár-előnézettel), SNEAK+JOBB = foglalás (/claim area).</li>
 *   <li><b>Határkijelölő pálca</b> (territory, admin): BAL katt = poligon-pont a
 *       kattintott blokkon, JOBB katt = utolsó pont visszavonása, SNEAK+JOBB =
 *       határ-előnézet (/territory show).</li>
 * </ul>
 * A pálca-tag PDC-ben él (icesmp:selection_wand = claim|territory). A kattintott
 * blokk koordinátája megy a kijelölésbe (nem a játékos helye) — pontos sarkok.
 * Folia: az event a játékos saját régió-szálán fut, minden érintett API oda való.
 * A blokk-interakciót canceljük (a pálca ne üssön/nyisson semmit).
 */
public final class SelectionWandListener implements Listener {

    public static final NamespacedKey WAND_KEY = NamespacedKey.fromString("icesmp:selection_wand");

    private final ClaimManager claimManager;
    private final TerritoryManager territoryManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public SelectionWandListener(final ClaimManager claimManager, final TerritoryManager territoryManager,
                                 final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.claimManager = claimManager;
        this.territoryManager = territoryManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    // Nincs ignoreCancelled: a bal katt blokkra "cancelled"-ként érkezhet védett zónában,
    // de a kijelölést ott is engedjük (nem módosít blokkot).
    @EventHandler
    public void onWandUse(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        final ItemStack held = event.getItem();
        if (held == null || !held.hasItemMeta()) {
            return;
        }
        final String kind = held.getItemMeta().getPersistentDataContainer()
                .get(WAND_KEY, PersistentDataType.STRING);
        if (kind == null) {
            return;
        }
        event.setCancelled(true);
        final Player player = event.getPlayer();
        final Location clicked = event.getClickedBlock().getLocation();
        if ("territory".equals(kind)) {
            handleTerritoryWand(player, clicked, event.getAction());
        } else {
            handleClaimWand(player, clicked, event.getAction());
        }
    }

    private void handleClaimWand(final Player player, final Location clicked, final Action action) {
        if (action == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            player.performCommand("claim area"); // foglalás — a teljes ár/limit-logika a parancsban él
            return;
        }
        final boolean first = action == Action.LEFT_CLICK_BLOCK;
        final int[] corner = claimManager.setCorner(player, first, clicked);
        player.sendMessage(messageManager.get(first ? "claim-pos1-set-3d" : "claim-pos2-set-3d",
                first ? "&aKijelölés 1. sarka: &f%s, %s, %s &7(X/Y/Z blokk)"
                        : "&aKijelölés 2. sarka: &f%s, %s, %s &7(X/Y/Z blokk)",
                corner[0], corner[1], corner[2]));
        final ClaimManager.SelectionInfo info = claimManager.getSelectionInfo(player.getUniqueId());
        if (info != null) {
            player.sendMessage(messageManager.get("claim-wand-preview",
                    "&7Kijelölve: &f%s×%s&7 blokk (&f%s&7 oszlop)%s &7— ár: &f%s&7. Foglalás: &eSNEAK+jobb katt",
                    info.width(), info.depth(), info.columns(),
                    info.overlaps() ? " &c(meglévő claimet fed!)" : "",
                    info.cost() == 0.0D ? "ingyenes" : currencyManager.formatBalance(info.cost())));
        }
    }

    private void handleTerritoryWand(final Player player, final Location clicked, final Action action) {
        if (!player.hasPermission("icesmp.admin.territory")) {
            player.sendMessage(messageManager.get("messages.permission-denied",
                    "&cNincs jogosultságod a parancs használatához."));
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK) {
            player.performCommand(player.isSneaking() ? "territory show" : "territory undo");
            return;
        }
        final int count = territoryManager.addPoint(player, clicked);
        player.sendMessage(messageManager.get("territory-wand-point",
                "&aHatárpont felvéve: &f%s, %s &7(összesen: &f%s&7). Visszavonás: jobb katt • előnézet: SNEAK+jobb • létrehozás: &e/territory create",
                clicked.getBlockX(), clicked.getBlockZ(), count));
    }

    /** Pálca-item gyártás (a /claim wand és /territory wand adja). */
    public static ItemStack createWand(final String kind) {
        final boolean territory = "territory".equals(kind);
        final ItemStack wand = new ItemStack(territory ? org.bukkit.Material.BLAZE_ROD : org.bukkit.Material.STICK);
        wand.editMeta(meta -> {
            meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.STRING, kind);
            meta.displayName(net.kyori.adventure.text.Component.text(
                            territory ? "⚑ Határkijelölő pálca" : "⚑ Birtokmérő pálca",
                            territory ? net.kyori.adventure.text.format.NamedTextColor.GOLD
                                    : net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(
                    net.kyori.adventure.text.Component.text(territory
                                    ? "Bal katt: poligon-pont • jobb: visszavon"
                                    : "Bal katt: 1. sarok • jobb: 2. sarok",
                            net.kyori.adventure.text.format.NamedTextColor.GRAY)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    net.kyori.adventure.text.Component.text(territory
                                    ? "SNEAK+jobb: előnézet"
                                    : "SNEAK+jobb: foglalás",
                            net.kyori.adventure.text.format.NamedTextColor.GRAY)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        });
        hu.taliann.icesmp.items.ItemDataFactory.applyItemModel(wand,
                territory ? "icesmp:selection_wand_territory" : "icesmp:selection_wand");
        return wand;
    }
}
