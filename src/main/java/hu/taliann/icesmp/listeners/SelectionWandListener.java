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

/** Rectangle claim, polygon claim and territory selection wands. */
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

    @EventHandler
    public void onWandUse(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        final ItemStack held = event.getItem();
        if (held == null || !held.hasItemMeta()) return;
        final String kind = held.getItemMeta().getPersistentDataContainer()
                .get(WAND_KEY, PersistentDataType.STRING);
        if (kind == null) return;
        event.setCancelled(true);
        final Player player = event.getPlayer();
        final Location clicked = event.getClickedBlock().getLocation();
        switch (kind) {
            case "territory" -> handleTerritoryWand(player, clicked, event.getAction());
            case "claim-polygon" -> handleClaimPolygonWand(player, clicked, event.getAction());
            default -> handleClaimWand(player, clicked, event.getAction());
        }
    }

    private void handleClaimWand(final Player player, final Location clicked, final Action action) {
        if (action == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            player.performCommand("claim area");
            return;
        }
        final boolean first = action == Action.LEFT_CLICK_BLOCK;
        final int[] corner = claimManager.setCorner(player, first, clicked);
        player.sendMessage(messageManager.get(first ? "claim-pos1-set-3d" : "claim-pos2-set-3d",
                first ? "&aKijelölés 1. sarka: &f%s, %s, %s"
                        : "&aKijelölés 2. sarka: &f%s, %s, %s",
                corner[0], corner[1], corner[2]));
        final ClaimManager.SelectionInfo info = claimManager.getSelectionInfo(player.getUniqueId());
        if (info != null) {
            player.sendMessage(messageManager.get("claim-wand-preview",
                    "&7Téglalap: &f%s×%s&7, &f%s&7 oszlop%s — ár: &f%s",
                    info.width(), info.depth(), info.columns(),
                    info.overlaps() ? " &c(átfedés!)" : "",
                    info.cost() == 0.0D ? "ingyenes" : currencyManager.formatBalance(info.cost())));
        }
    }

    private void handleClaimPolygonWand(final Player player, final Location clicked, final Action action) {
        if (action == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            player.performCommand("claim polygon");
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK) {
            final int remaining = claimManager.undoPolygonPoint(player.getUniqueId());
            player.sendMessage(remaining < 0
                    ? messageManager.get("claim-polygon-point-none", "&eNincs visszavonható poligonpont.")
                    : messageManager.get("claim-polygon-point-undone",
                    "&aUtolsó poligonpont törölve; &f%s&a maradt.", remaining));
            return;
        }
        final int count = claimManager.addPolygonPoint(player, clicked);
        if (count < 0) {
            player.sendMessage(messageManager.get("claim-polygon-point-limit",
                    "&cElérted a poligonpont-limitet: &f%s&c.", -count));
            return;
        }
        player.sendMessage(messageManager.get("claim-polygon-wand-point",
                "&aClaim-határpont: &f%s, %s &7(összesen: &f%s&7). Jobb: vissza • SNEAK+jobb: foglalás",
                clicked.getBlockX(), clicked.getBlockZ(), count));
        final ClaimManager.PolygonSelectionInfo info =
                claimManager.getPolygonSelectionInfo(player.getUniqueId());
        if (info != null) {
            player.sendMessage(messageManager.get("claim-polygon-wand-preview",
                    "&7Kitöltött alakzat: &f%s&7 oszlop%s — ár: &f%s",
                    info.columns(), info.overlaps() ? " &c(átfedés!)" : "",
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
                "&aHatárpont felvéve: &f%s, %s &7(összesen: &f%s&7).",
                clicked.getBlockX(), clicked.getBlockZ(), count));
    }

    public static ItemStack createWand(final String kind) {
        final boolean territory = "territory".equals(kind);
        final boolean polygonClaim = "claim-polygon".equals(kind);
        final ItemStack wand = new ItemStack(territory
                ? org.bukkit.Material.BLAZE_ROD : polygonClaim
                ? org.bukkit.Material.BONE : org.bukkit.Material.STICK);
        wand.editMeta(meta -> {
            meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.STRING, kind);
            final String name = territory ? "⚑ Határkijelölő pálca"
                    : polygonClaim ? "⚑ Poligon-claim pálca" : "⚑ Birtokmérő pálca";
            meta.displayName(net.kyori.adventure.text.Component.text(name,
                            territory ? net.kyori.adventure.text.format.NamedTextColor.GOLD
                                    : net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            final String firstLine = territory
                    ? "Bal katt: territory poligonpont • jobb: vissza"
                    : polygonClaim
                    ? "Bal katt: claim poligonpont • jobb: vissza"
                    : "Bal katt: 1. sarok • jobb: 2. sarok";
            final String secondLine = territory ? "SNEAK+jobb: előnézet" : "SNEAK+jobb: foglalás";
            meta.lore(java.util.List.of(
                    net.kyori.adventure.text.Component.text(firstLine,
                                    net.kyori.adventure.text.format.NamedTextColor.GRAY)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    net.kyori.adventure.text.Component.text(secondLine,
                                    net.kyori.adventure.text.format.NamedTextColor.GRAY)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        });
        hu.taliann.icesmp.items.ItemDataFactory.applyItemModel(wand,
                territory ? "icesmp:selection_wand_territory" : "icesmp:selection_wand");
        return wand;
    }
}
