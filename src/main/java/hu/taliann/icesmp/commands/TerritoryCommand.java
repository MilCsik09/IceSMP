package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Admin command for marking faction territories and capitals:
 * /territory setcapital <faction> <radius> [name...]
 * /territory claim <faction> <id> <radius> [name...]
 * /territory remove <id> | list | info
 */
public final class TerritoryCommand implements BasicCommand {

    private static final String PERMISSION = "icesmp.admin.territory";

    private final TerritoryManager territoryManager;
    private final MessageManager messageManager;

    public TerritoryCommand(final TerritoryManager territoryManager, final MessageManager messageManager) {
        this.territoryManager = territoryManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setcapital" -> handleSetCapital(sender, args);
            case "claim" -> handleClaim(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender);
            default -> {
                sender.sendMessage(messageManager.get("territory-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
                sendHelp(sender);
            }
        }
    }

    private void handleSetCapital(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.get("territory-setcapital-usage",
                    "&cHasználat: /territory setcapital <frakció> <sugár> [név...]"));
            return;
        }

        final FactionType faction = FactionType.fromInput(args[1]);
        if (faction == null) {
            sender.sendMessage(messageManager.get("faction-unknown", "&cIsmeretlen frakció: &f%s", args[1]));
            return;
        }

        final Integer radius = parseRadius(sender, args[2]);
        if (radius == null) {
            return;
        }

        final String name = args.length > 3
                ? String.join(" ", Arrays.copyOfRange(args, 3, args.length))
                : faction.getDisplayName() + " főváros";
        final Territory territory = territoryManager.define(
                faction.name().toLowerCase(Locale.ROOT) + "-capital",
                faction,
                name,
                player.getLocation(),
                radius,
                true
        );

        sender.sendMessage(messageManager.get(
                "territory-setcapital-success",
                "&aFőváros kijelölve: &f%s &7(%s, sugár: %s, középpont: %s, %s)",
                territory.name(),
                faction.getDisplayName(),
                territory.radius(),
                territory.x(),
                territory.z()
        ));
    }

    private void handleClaim(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(messageManager.get("territory-claim-usage",
                    "&cHasználat: /territory claim <frakció> <azonosító> <sugár> [név...]"));
            return;
        }

        final FactionType faction = FactionType.fromInput(args[1]);
        if (faction == null) {
            sender.sendMessage(messageManager.get("faction-unknown", "&cIsmeretlen frakció: &f%s", args[1]));
            return;
        }

        final Integer radius = parseRadius(sender, args[3]);
        if (radius == null) {
            return;
        }

        final String name = args.length > 4
                ? String.join(" ", Arrays.copyOfRange(args, 4, args.length))
                : args[2];
        final Territory territory = territoryManager.define(args[2], faction, name, player.getLocation(), radius, false);

        sender.sendMessage(messageManager.get(
                "territory-claim-success",
                "&aTerület kijelölve: &f%s &7(%s, sugár: %s, középpont: %s, %s)",
                territory.name(),
                faction.getDisplayName(),
                territory.radius(),
                territory.x(),
                territory.z()
        ));
    }

    private void handleRemove(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("territory-remove-usage", "&cHasználat: /territory remove <azonosító>"));
            return;
        }

        if (!territoryManager.remove(args[1])) {
            sender.sendMessage(messageManager.get("territory-unknown", "&cIsmeretlen terület: &f%s", args[1]));
            return;
        }

        sender.sendMessage(messageManager.get("territory-remove-success", "&aTerület törölve: &f%s", args[1]));
    }

    private void handleList(final CommandSender sender) {
        final Collection<Territory> territories = territoryManager.all();
        if (territories.isEmpty()) {
            sender.sendMessage(messageManager.get("territory-list-empty", "&eNincsenek kijelölt területek."));
            return;
        }

        sender.sendMessage(messageManager.get("territory-list-header", "&6Kijelölt területek:"));
        for (final Territory territory : territories) {
            sender.sendMessage(messageManager.get(
                    "territory-list-line",
                    "&e%s &7(%s) | %s | sugár: %s | középpont: %s, %s (%s)%s",
                    territory.id(),
                    territory.name(),
                    territory.faction().getDisplayName(),
                    territory.radius(),
                    territory.x(),
                    territory.z(),
                    territory.world(),
                    territory.capital() ? " &6[FŐVÁROS]" : ""
            ));
        }
    }

    private void handleInfo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final Territory territory = territoryManager.getTerritoryAt(player.getLocation());
        if (territory == null) {
            sender.sendMessage(messageManager.get("territory-info-wilderness", "&7Itt nincs kijelölt terület (vadon)."));
            return;
        }

        sender.sendMessage(messageManager.get(
                "territory-info-line",
                "&6Terület: &f%s &7| Frakció: &f%s &7| Azonosító: &f%s%s",
                territory.name(),
                territory.faction().getDisplayName(),
                territory.id(),
                territory.capital() ? " &6[FŐVÁROS]" : ""
        ));
    }

    private Integer parseRadius(final CommandSender sender, final String rawRadius) {
        try {
            final int radius = Integer.parseInt(rawRadius);
            if (radius <= 0) {
                sender.sendMessage(messageManager.get("amount-must-be-positive", "&cAz összegnek pozitívnak kell lennie."));
                return null;
            }
            return radius;
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("invalid-amount", "&cÉrvénytelen összeg."));
            return null;
        }
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("territory-help-header", "&6/territory &7- Elérhető parancsok (Admin):"));
        sender.sendMessage(messageManager.get("territory-help-setcapital",
                "&e/territory setcapital <frakció> <sugár> [név...] &7- Főváros kijelölése a pozíciódnál."));
        sender.sendMessage(messageManager.get("territory-help-claim",
                "&e/territory claim <frakció> <azonosító> <sugár> [név...] &7- Terület kijelölése."));
        sender.sendMessage(messageManager.get("territory-help-remove", "&e/territory remove <azonosító> &7- Terület törlése."));
        sender.sendMessage(messageManager.get("territory-help-list", "&e/territory list &7- Területek listája."));
        sender.sendMessage(messageManager.get("territory-help-info", "&e/territory info &7- Az aktuális pozíció területe."));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }

        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("setcapital", "claim", "remove", "list", "info").stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && ("setcapital".equals(subcommand) || "claim".equals(subcommand))) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return Arrays.stream(FactionType.values())
                    .map(faction -> faction.name().toLowerCase(Locale.ROOT))
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2 && "remove".equals(subcommand)) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return territoryManager.all().stream()
                    .map(Territory::id)
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }
}
