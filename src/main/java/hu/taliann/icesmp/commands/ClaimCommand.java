package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.data.ClaimShape;
import hu.taliann.icesmp.gui.ClaimTrustGUI;
import hu.taliann.icesmp.managers.ClaimManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Personal claims: quick/two-corner rectangles and territory-style polygons. */
public final class ClaimCommand implements BasicCommand {

    public static final String ADMIN_PERMISSION = "icesmp.admin.territory";

    private final ClaimManager claimManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public ClaimCommand(final ClaimManager claimManager, final CurrencyManager currencyManager,
                        final MessageManager messageManager) {
        this.claimManager = claimManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack source, final @NonNull String[] args) {
        final CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length == 0) {
            handleClaim(player);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "claim" -> handleClaim(player);
            case "unclaim" -> handleUnclaim(player);
            case "info" -> handleInfo(player);
            case "list" -> handleList(player);
            case "trust" -> handleTrust(player, args);
            case "untrust" -> handleUntrust(player, args);
            case "trustgui" -> ClaimTrustGUI.open(player, claimManager, messageManager);
            case "show" -> handleShow(player);
            case "pos1" -> handleCorner(player, true);
            case "pos2" -> handleCorner(player, false);
            case "wand", "palca" -> giveWand(player, "claim");
            case "polywand", "polygonwand", "hatarpalca" -> giveWand(player, "claim-polygon");
            case "area" -> handleArea(player);
            case "point", "pont" -> handlePolygonPoint(player);
            case "undo", "vissza" -> handlePolygonUndo(player);
            case "clearpoints", "pontoktorlese" -> handlePolygonClear(player);
            case "points", "pontok" -> handlePolygonPoints(player);
            case "polygon", "poligon" -> handlePolygonClaim(player);
            case "extend" -> handleExtend(player, args);
            case "admin" -> handleAdmin(player, args);
            case "help" -> sendHelp(player);
            default -> sendHelp(player);
        }
    }

    private void giveWand(final Player player, final String kind) {
        final var wand = hu.taliann.icesmp.listeners.SelectionWandListener.createWand(kind);
        for (final var overflow : player.getInventory().addItem(wand).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
        player.sendMessage("claim-polygon".equals(kind)
                ? messageManager.get("claim-polygon-wand-given",
                "&a⚑ Poligon-claim pálca: bal katt = határpont, jobb = visszavonás, SNEAK+jobb = foglalás.")
                : messageManager.get("claim-wand-given",
                "&a⚑ Birtokmérő pálca: bal katt = 1. sarok, jobb = 2. sarok, SNEAK+jobb = foglalás."));
    }

    private void handleClaim(final Player player) {
        final double cost = claimManager.nextClaimCost(player.getUniqueId());
        final String errorKey = claimManager.claimHere(player);
        if (errorKey != null) {
            sendError(player, errorKey);
            return;
        }
        final int count = claimManager.countClaims(player.getUniqueId());
        player.sendMessage(cost > 0.0D
                ? messageManager.get("claim-success-paid",
                "&aTerület lefoglalva! (&f%s&a claim) Ár: &f%s&a (elégett).",
                count, currencyManager.formatBalance(cost))
                : messageManager.get("claim-success-free",
                "&aTerület lefoglalva! (&f%s&a claim — még ingyenes)", count));
        claimManager.showBorder(player);
    }

    private void handleUnclaim(final Player player) {
        final String errorKey = claimManager.unclaimHere(player);
        if (errorKey != null) {
            sendError(player, errorKey);
            return;
        }
        player.sendMessage(messageManager.get("claim-unclaimed",
                "&aTerület felszabadítva. &7(Az ár nem jár vissza — elégett.)"));
    }

    private void handleInfo(final Player player) {
        final ClaimManager.Claim claim = claimManager.getClaimAt(player.getLocation());
        if (claim == null) {
            final double cost = claimManager.nextClaimCost(player.getUniqueId());
            player.sendMessage(messageManager.get("claim-info-wilderness",
                    "&7Ez szabad terület. Következő ár: &f%s",
                    cost == 0.0D ? "ingyenes" : currencyManager.formatBalance(cost)));
        } else {
            player.sendMessage(messageManager.get("claim-info-owner",
                    "&6Tulajdonos: &f%s &7• alakzat: &f%s &7• oszlop: &f%s &7• Y: &f%s..%s",
                    claim.getOwnerName(), claim.isPolygon() ? "poligon" : "téglalap",
                    claim.columns(), claim.minY(), claim.maxY()));
            final List<String> trustedNames = claimManager.trustedNamesAt(player.getLocation());
            if (!trustedNames.isEmpty()) {
                player.sendMessage(messageManager.get("claim-info-trusted",
                        "&7Megbízottak: &f%s", String.join(", ", trustedNames)));
            }
        }
        claimManager.showBorder(player);
    }

    private void handleList(final Player player) {
        final List<String> entries = claimManager.describeClaims(player.getUniqueId());
        if (entries.isEmpty()) {
            player.sendMessage(messageManager.get("claim-list-empty", "&7Nincs claimed."));
            return;
        }
        final double cost = claimManager.nextClaimCost(player.getUniqueId());
        player.sendMessage(messageManager.get("claim-list-header",
                "&6Claimjeid (&f%s&6 db) — köv. ár: &f%s",
                entries.size(), cost == 0.0D ? "ingyenes" : currencyManager.formatBalance(cost)));
        for (final String entry : entries) {
            player.sendMessage(messageManager.get("claim-list-entry", "&7- &f%s", entry));
        }
    }

    private void handleTrust(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("claim-usage-trust", "&cHasználat: /claim trust <név>"));
            return;
        }
        final String errorKey = claimManager.trust(player, args[1]);
        if (errorKey != null) {
            sendError(player, errorKey);
            return;
        }
        player.sendMessage(messageManager.get("claim-trusted",
                "&aMegbízott hozzáadva minden claimedhez: &f%s", args[1]));
    }

    private void handleUntrust(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("claim-usage-untrust", "&cHasználat: /claim untrust <név>"));
            return;
        }
        final String errorKey = claimManager.untrust(player, args[1]);
        if (errorKey != null) {
            sendError(player, errorKey);
            return;
        }
        player.sendMessage(messageManager.get("claim-untrusted", "&aMegbízott eltávolítva: &f%s", args[1]));
    }

    private void handleShow(final Player player) {
        claimManager.showBorder(player);
        player.sendMessage(messageManager.get("claim-show",
                "&7Claim-határok kirajzolva (zöld=saját/megbízott, piros=másé)."));
    }

    private void handleCorner(final Player player, final boolean first) {
        final int[] corner = claimManager.setCorner(player, first);
        player.sendMessage(messageManager.get(first ? "claim-pos1-set-3d" : "claim-pos2-set-3d",
                first ? "&aKijelölés 1. sarka: &f%s, %s, %s"
                        : "&aKijelölés 2. sarka: &f%s, %s, %s",
                corner[0], corner[1], corner[2]));
        final ClaimManager.SelectionInfo info = claimManager.getSelectionInfo(player.getUniqueId());
        if (info != null) {
            player.sendMessage(messageManager.get("claim-area-preview",
                    "&7Téglalap: &f%s×%s&7, &f%s&7 oszlop%s — ár: &f%s&7. Foglalás: &e/claim area",
                    info.width(), info.depth(), info.columns(),
                    info.overlaps() ? " &c(átfedés!)" : "",
                    info.cost() == 0.0D ? "ingyenes" : currencyManager.formatBalance(info.cost())));
        }
    }

    private void handleArea(final Player player) {
        final ClaimManager.SelectionInfo info = claimManager.getSelectionInfo(player.getUniqueId());
        final String errorKey = claimManager.claimSelection(player);
        if (errorKey != null) {
            sendError(player, errorKey);
            return;
        }
        player.sendMessage(messageManager.get("claim-area-success",
                "&aTéglalap-claim létrehozva: &f%s&a oszlop, alapból ±20 Y-blokk. Ár: &f%s&a.",
                info == null ? "?" : info.columns(),
                info == null || info.cost() == 0.0D ? "ingyenes" : currencyManager.formatBalance(info.cost())));
        claimManager.showBorder(player);
    }

    private void handlePolygonPoint(final Player player) {
        final int count = claimManager.addPolygonPoint(player);
        if (count < 0) {
            player.sendMessage(messageManager.get("claim-polygon-point-limit",
                    "&cElérted a poligonpont-limitet: &f%s&c.", -count));
            return;
        }
        player.sendMessage(messageManager.get("claim-polygon-point-added",
                "&aHatárpont hozzáadva (&f%s&a): &f%s, %s",
                count, player.getLocation().getBlockX(), player.getLocation().getBlockZ()));
        sendPolygonPreview(player);
    }

    private void handlePolygonUndo(final Player player) {
        final int count = claimManager.undoPolygonPoint(player.getUniqueId());
        player.sendMessage(count < 0
                ? messageManager.get("claim-polygon-point-none", "&eNincs visszavonható poligonpont.")
                : messageManager.get("claim-polygon-point-undone",
                "&aUtolsó poligonpont törölve; &f%s&a maradt.", count));
        sendPolygonPreview(player);
    }

    private void handlePolygonClear(final Player player) {
        claimManager.clearPolygonPoints(player.getUniqueId());
        player.sendMessage(messageManager.get("claim-polygon-points-cleared", "&aPoligon-kijelölés törölve."));
    }

    private void handlePolygonPoints(final Player player) {
        final List<ClaimShape.Point> points = claimManager.getPolygonPoints(player.getUniqueId());
        if (points.isEmpty()) {
            player.sendMessage(messageManager.get("claim-polygon-points-empty",
                    "&eNincs poligonpont. Használd a /claim point vagy /claim polywand eszközt."));
            return;
        }
        player.sendMessage(messageManager.get("claim-polygon-points-header",
                "&6Poligonpontok (&f%s&6):", points.size()));
        for (int i = 0; i < points.size(); i++) {
            final ClaimShape.Point point = points.get(i);
            player.sendMessage(messageManager.get("claim-polygon-points-line",
                    "&7%s. &f%s, %s", i + 1, point.x(), point.z()));
        }
        sendPolygonPreview(player);
    }

    private void sendPolygonPreview(final Player player) {
        final ClaimManager.PolygonSelectionInfo info = claimManager.getPolygonSelectionInfo(player.getUniqueId());
        if (info == null) return;
        player.sendMessage(messageManager.get("claim-polygon-preview",
                "&7Poligon: &f%s pont, %s oszlop%s &7— ár: &f%s&7. Foglalás: &e/claim polygon",
                info.points(), info.columns(), info.overlaps() ? " &c(átfedés!)" : "",
                info.cost() == 0.0D ? "ingyenes" : currencyManager.formatBalance(info.cost())));
    }

    private void handlePolygonClaim(final Player player) {
        final ClaimManager.PolygonSelectionInfo info = claimManager.getPolygonSelectionInfo(player.getUniqueId());
        final String errorKey = claimManager.claimPolygon(player);
        if (errorKey != null) {
            sendError(player, errorKey);
            return;
        }
        player.sendMessage(messageManager.get("claim-polygon-success",
                "&aPoligon-claim létrehozva: &f%s&a oszlop, alapból ±20 Y-blokk. Ár: &f%s&a.",
                info == null ? "?" : info.columns(),
                info == null || info.cost() == 0.0D ? "ingyenes" : currencyManager.formatBalance(info.cost())));
        claimManager.showBorder(player);
    }

    private void handleExtend(final Player player, final String[] args) {
        final boolean up = args.length < 2 || !"down".equalsIgnoreCase(args[1]);
        final double cost = claimManager.extendCostAt(player);
        final String errorKey = claimManager.extendClaim(player, up);
        if (errorKey != null) {
            sendError(player, errorKey);
            return;
        }
        player.sendMessage(messageManager.get(up ? "claim-extended-up" : "claim-extended-down",
                up ? "&aA claim teteje 5 blokkal megemelve. &7Ár: &f%s&7 (elégett)."
                        : "&aA claim alja 5 blokkal lejjebb víve. &7Ár: &f%s&7 (elégett).",
                cost <= 0.0D ? "ingyenes" : currencyManager.formatBalance(cost)));
        claimManager.showBorder(player);
    }

    private void handleAdmin(final Player player, final String[] args) {
        if (args.length < 2 || !"unclaim".equalsIgnoreCase(args[1])) {
            sendHelp(player);
            return;
        }
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre."));
            return;
        }
        player.sendMessage(claimManager.adminUnclaimAt(player.getLocation())
                ? messageManager.get("claim-admin-unclaimed", "&aA claim admin-jogon törölve.")
                : messageManager.get("claim-none-here", "&7Itt nincs claim."));
    }

    private void sendHelp(final Player player) {
        player.sendMessage(messageManager.get("claim-help-header", "&6/claim &7- Terület-claim parancsok:"));
        player.sendMessage(messageManager.get("claim-help-claim", "&e/claim &7- Gyors négyzetes claim."));
        player.sendMessage(messageManager.get("claim-help-area",
                "&e/claim pos1 + pos2 + area &7- Két sarkos téglalap."));
        player.sendMessage(messageManager.get("claim-help-polygon",
                "&e/claim point/undo/points/polygon &7- Territory-szerű többpontos poligon."));
        player.sendMessage(messageManager.get("claim-help-polywand",
                "&e/claim polywand &7- Poligonhatár kijelölő pálca."));
        player.sendMessage(messageManager.get("claim-help-extend",
                "&e/claim extend up|down &7- Y-határ bővítése 5 blokkal, pénzért."));
        player.sendMessage(messageManager.get("claim-help-management",
                "&e/claim unclaim/info/list/show/trust/untrust &7- Claimkezelés."));
    }

    private void sendError(final Player player, final String errorKey) {
        player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "claim-disabled" -> "&cA claim-rendszer ki van kapcsolva.";
            case "claim-already-yours", "claim-overlap-own", "claim-area-overlap-own",
                    "claim-polygon-overlap-own" -> "&cA kijelölés saját claimet fed.";
            case "claim-already-taken", "claim-area-foreign", "claim-polygon-foreign" ->
                    "&cA kijelölés más játékos claimjét fedi.";
            case "claim-in-protected-zone" -> "&cVédett territoryban nem claimelhetsz.";
            case "claim-in-territory" -> "&cFrakció-territóriumban nem claimelhetsz.";
            case "claim-in-protected-region" -> "&cWorldGuard-védett területet fed a kijelölés.";
            case "claim-limit-reached" -> "&cElérted a claim-oszloplimitedet.";
            case "claim-insufficient" -> "&cNincs elég frakció-valutád.";
            case "claim-none-here" -> "&7Itt nincs claim.";
            case "claim-not-owner" -> "&cEz a claim nem a tiéd.";
            case "claim-trust-self" -> "&cMagadat nem kell megbíznod.";
            case "claim-no-claims" -> "&7Nincs claimed.";
            case "claim-not-trusted" -> "&7Ez a játékos nem volt megbízva.";
            case "target-player-offline" -> "&cA célpont nem elérhető.";
            case "claim-area-incomplete" -> "&cElőbb állítsd be a két sarkot.";
            case "claim-area-cross-world", "claim-polygon-cross-world" -> "&cA kijelölés másik világban van.";
            case "claim-area-too-big", "claim-polygon-too-big" -> "&cTúl nagy terület.";
            case "claim-polygon-too-few" -> "&cLegalább 3 poligonpont szükséges.";
            case "claim-polygon-too-many" -> "&cTúl sok poligonpont.";
            case "claim-polygon-self-intersect" -> "&cA poligon határa önmagát keresztezi.";
            case "claim-polygon-invalid" -> "&cÉrvénytelen vagy nulla területű poligon.";
            case "claim-extend-at-limit" -> "&cA claim Y-határa már elérte a világ szélét.";
            default -> "&cA művelet nem sikerült.";
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        final CommandSender sender = source.getSender();
        final List<String> options = new ArrayList<>(List.of(
                "claim", "unclaim", "info", "list", "trust", "trustgui", "untrust", "show",
                "pos1", "pos2", "wand", "area", "point", "undo", "clearpoints", "points",
                "polygon", "polywand", "extend", "help"));
        if (sender.hasPermission(ADMIN_PERMISSION)) options.add("admin");
        final String first = prefixAt(args, 0);
        final boolean complete = options.contains(first);
        if (args.length == 0 || args.length == 1 && !complete) {
            return options.stream().filter(value -> value.startsWith(first)).toList();
        }
        if (("trust".equals(first) || "untrust".equals(first)) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if ("extend".equals(first) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return List.of("up", "down").stream()
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        if ("admin".equals(first) && args.length <= 2) {
            return List.of("unclaim").stream()
                    .filter(value -> value.startsWith(prefixAt(args, 1))).toList();
        }
        return List.of();
    }
}
