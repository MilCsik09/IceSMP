package hu.taliann.icesmp.commands;

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

/**
 * /claim — chunk-alapú terület-claim parancsok: az aktuális chunk lefoglalása
 * (első pár ingyen, utána frakció-valuta — elégetve), felszabadítás, infó és
 * lista lekérdezés, megbízottak (trust/untrust) kezelése minden claimedhez,
 * chunk-határ kirajzolása részecskékkel, valamint egy admin alparancs a claim
 * jog nélküli törléséhez.
 */
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
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
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
            case "show" -> handleShow(player);
            case "admin" -> handleAdmin(player, args);
            case "help" -> sendHelp(player);
            default -> sendHelp(player);
        }
    }

    private void handleClaim(final Player player) {
        final double cost = claimManager.nextClaimCost(player.getUniqueId());
        final String errorKey = claimManager.claimHere(player);
        if (errorKey != null) {
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
            return;
        }

        final int count = claimManager.countClaims(player.getUniqueId());
        if (cost > 0.0D) {
            player.sendMessage(messageManager.get("claim-success-paid",
                    "&aChunk lefoglalva! (&f%s&a claimed) Ár: &f%s&a (elégett — money sink).",
                    count, currencyManager.formatBalance(cost)));
        } else {
            player.sendMessage(messageManager.get("claim-success-free",
                    "&aChunk lefoglalva! (&f%s&a claimed — még ingyenes)", count));
        }
        claimManager.showBorder(player);
    }

    private void handleUnclaim(final Player player) {
        final String errorKey = claimManager.unclaimHere(player);
        if (errorKey != null) {
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
            return;
        }
        player.sendMessage(messageManager.get("claim-unclaimed",
                "&aChunk felszabadítva. &7(Az ár nem jár vissza — elégett.)"));
    }

    private void handleInfo(final Player player) {
        final ClaimManager.Claim claim = claimManager.getClaimAt(player.getLocation());
        if (claim == null) {
            final double cost = claimManager.nextClaimCost(player.getUniqueId());
            player.sendMessage(messageManager.get("claim-info-wilderness",
                    "&7Ez a chunk szabad terület — /claim paranccsal lefoglalhatod. Köv. ár: &f%s",
                    cost == 0.0D ? "ingyenes" : currencyManager.formatBalance(cost)));
        } else {
            player.sendMessage(messageManager.get("claim-info-owner", "&6Tulajdonos: &f%s", claim.getOwnerName()));
            final List<String> trustedNames = claimManager.trustedNamesAt(player.getLocation());
            if (!trustedNames.isEmpty()) {
                player.sendMessage(messageManager.get("claim-info-trusted", "&7Megbízottak: &f%s",
                        String.join(", ", trustedNames)));
            }
        }
        claimManager.showBorder(player);
    }

    private void handleList(final Player player) {
        final List<String> entries = claimManager.describeClaims(player.getUniqueId());
        if (entries.isEmpty()) {
            player.sendMessage(messageManager.get("claim-list-empty", "&7Nincs claimelt chunkod."));
            return;
        }

        final double cost = claimManager.nextClaimCost(player.getUniqueId());
        player.sendMessage(messageManager.get("claim-list-header", "&6Claimjeid (&f%s&6 db) — köv. ár: &f%s",
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
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
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
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
            return;
        }
        player.sendMessage(messageManager.get("claim-untrusted", "&aMegbízott eltávolítva: &f%s", args[1]));
    }

    private void handleShow(final Player player) {
        claimManager.showBorder(player);
        player.sendMessage(messageManager.get("claim-show",
                "&7Chunk-határ kirajzolva (zöld=szabad/sajátod, láng=másé)."));
    }

    private void handleAdmin(final Player player, final String[] args) {
        if (args.length < 2 || !"unclaim".equalsIgnoreCase(args[1])) {
            sendHelp(player);
            return;
        }
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        final boolean removed = claimManager.adminUnclaimAt(player.getLocation());
        if (removed) {
            player.sendMessage(messageManager.get("claim-admin-unclaimed", "&aA chunk claimje admin-jogon törölve."));
        } else {
            player.sendMessage(messageManager.get("claim-none-here", "&7Ez a chunk nincs claimelve."));
        }
    }

    private void sendHelp(final Player player) {
        player.sendMessage(messageManager.get("claim-help-header", "&6/claim &7- Terület-claim parancsok:"));
        player.sendMessage(messageManager.get("claim-help-claim",
                "&e/claim &7- Az aktuális chunk lefoglalása (első pár ingyen, utána frakció-valuta — elég)."));
        player.sendMessage(messageManager.get("claim-help-unclaim",
                "&e/claim unclaim &7- Az aktuális chunk felszabadítása."));
        player.sendMessage(messageManager.get("claim-help-info",
                "&e/claim info &7- Kié ez a chunk? (+ határ-kirajzolás)"));
        player.sendMessage(messageManager.get("claim-help-list", "&e/claim list &7- Saját claimjeid listája."));
        player.sendMessage(messageManager.get("claim-help-trust",
                "&e/claim trust/untrust <név> &7- Hozzáférés adása/elvétele minden claimedhez."));
        player.sendMessage(messageManager.get("claim-help-show",
                "&e/claim show &7- Chunk-határ kirajzolása részecskékkel."));
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "claim-disabled" -> "&cA claim-rendszer ki van kapcsolva.";
            case "claim-already-yours" -> "&7Ez a chunk már a tiéd.";
            case "claim-already-taken" -> "&cEzt a chunkot már más claimelte.";
            case "claim-in-territory" -> "&cFrakció-territóriumban nem claimelhetsz — az a királyság földje.";
            case "claim-in-protected-region" -> "&cVédett zónában (spawn/város) nem claimelhetsz.";
            case "claim-limit-reached" -> "&cElérted a claim-limitedet.";
            case "claim-insufficient" -> "&cNincs elég frakció-valutád a bankodban ehhez a chunkhoz.";
            case "claim-none-here" -> "&7Ez a chunk nincs claimelve.";
            case "claim-not-owner" -> "&cEz a claim nem a tiéd.";
            case "claim-trust-self" -> "&cMagadat nem kell megbíznod.";
            case "claim-no-claims" -> "&7Nincs claimelt chunkod.";
            case "claim-not-trusted" -> "&7Ez a játékos nem volt megbízva.";
            case "target-player-offline" -> "&cA célpont játékos nem elérhető.";
            default -> "&cA művelet nem sikerült.";
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            final List<String> options = new ArrayList<>(
                    List.of("claim", "unclaim", "info", "list", "trust", "untrust", "show", "help"));
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                options.add("admin");
            }
            return options.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if (args.length == 2 && ("trust".equalsIgnoreCase(args[0]) || "untrust".equalsIgnoreCase(args[0]))) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("unclaim").stream().filter(option -> option.startsWith(prefix)).toList();
        }

        return List.of();
    }
}
