package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.items.CrateKeyFactory;
import hu.taliann.icesmp.managers.CrateManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /crate — natív láda-rendszer (a CrazyCrates kiváltása): kulcsvásárlás
 * valuta-sinkkel, láda-infó (jutalom-esélyek %-ban), admin láda-blokk kijelölés és
 * kulcs-adás. A tényleges kinyitás blokk-kattra történik ({@link hu.taliann.icesmp.listeners.CrateListener}).
 */
public final class CrateCommand implements BasicCommand {

    public static final String ADMIN_PERMISSION = "icesmp.admin.crate";

    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.#");

    private final JavaPlugin plugin;
    private final CrateManager crateManager;
    private final CrateKeyFactory crateKeyFactory;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public CrateCommand(final JavaPlugin plugin, final CrateManager crateManager, final CrateKeyFactory crateKeyFactory,
                        final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.crateManager = crateManager;
        this.crateKeyFactory = crateKeyFactory;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length == 0) {
            sendHelp(player);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "buy" -> handleBuy(player, args);
            case "info" -> handleInfo(player, args);
            case "set" -> handleSet(player, args);
            case "remove" -> handleRemove(player);
            case "give" -> handleGive(player, args);
            case "list" -> handleList(player);
            default -> sendHelp(player);
        }
    }

    private void handleBuy(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("crate-usage-buy", "&cHasználat: /crate buy <láda-id> [darab]"));
            return;
        }
        final String crateId = args[1].toLowerCase(Locale.ROOT);
        final int amount = args.length >= 3 ? parseAmount(args[2]) : 1;
        final String errorKey = crateManager.buyKey(player, crateId, amount);
        if (errorKey != null) {
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
            return;
        }
        player.sendMessage(messageManager.get("crate-key-bought",
                "&aMegvetted: &f%s db %s &7(&c-%s %s&7 — elégett).",
                amount, crateManager.displayName(crateId),
                currencyManager.formatBalance(crateManager.keyPriceAmount(crateId) * amount),
                crateManager.keyPriceCurrency(crateId).getDisplayName()));
    }

    private void handleInfo(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("crate-info-header", "&6Elérhető ládák: &7(/crate info <id> a részletekhez)"));
            for (final String crateId : crateManager.crateIds()) {
                player.sendMessage(messageManager.get("crate-info-entry", "&e- %s &7(&f%s&7): &f%s %s",
                        crateId, crateManager.displayName(crateId),
                        currencyManager.formatBalance(crateManager.keyPriceAmount(crateId)),
                        crateManager.keyPriceCurrency(crateId).getDisplayName()));
            }
            return;
        }

        final String crateId = args[1].toLowerCase(Locale.ROOT);
        if (!crateManager.crateIds().contains(crateId)) {
            player.sendMessage(messageManager.get("crate-unknown", "&cNincs ilyen láda: &f%s", crateId));
            return;
        }
        player.sendMessage(messageManager.get("crate-info-title", "&6%s &7— kulcs ára: &f%s %s",
                crateManager.displayName(crateId),
                currencyManager.formatBalance(crateManager.keyPriceAmount(crateId)),
                crateManager.keyPriceCurrency(crateId).getDisplayName()));
        for (final CrateManager.RewardOdds odds : crateManager.rewardOdds(crateId)) {
            player.sendMessage(messageManager.get("crate-info-odds", "&7- &f%s&7: &e%s%%", odds.description(), PERCENT_FORMAT.format(odds.percent())));
        }
    }

    private void handleSet(final Player player, final String[] args) {
        if (!requireAdmin(player)) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(messageManager.get("crate-usage-set", "&cHasználat: /crate set <láda-id> (a nézett blokkra)"));
            return;
        }
        final String crateId = args[1].toLowerCase(Locale.ROOT);
        final Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(messageManager.get("crate-no-target", "&cNincs blokk a látómeződben (max 5 blokk távolság)."));
            return;
        }
        if (!crateManager.setCrate(target.getLocation(), crateId)) {
            player.sendMessage(messageManager.get("crate-unknown", "&cNincs ilyen láda: &f%s", crateId));
            return;
        }
        player.sendMessage(messageManager.get("crate-set", "&aA nézett blokk mostantól láda: &f%s", crateManager.displayName(crateId)));
    }

    private void handleRemove(final Player player) {
        if (!requireAdmin(player)) {
            return;
        }
        final Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(messageManager.get("crate-no-target", "&cNincs blokk a látómeződben (max 5 blokk távolság)."));
            return;
        }
        if (!crateManager.removeCrate(target.getLocation())) {
            player.sendMessage(messageManager.get("crate-not-a-crate", "&7A nézett blokk nem láda."));
            return;
        }
        player.sendMessage(messageManager.get("crate-removed", "&aA nézett blokk láda-jelölése törölve."));
    }

    private void handleGive(final Player player, final String[] args) {
        if (!requireAdmin(player)) {
            return;
        }
        if (args.length < 3) {
            player.sendMessage(messageManager.get("crate-usage-give", "&cHasználat: /crate give <játékos> <láda-id> [darab]"));
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(messageManager.get("player-not-found", "&cNincs ilyen online játékos: &f%s", args[1]));
            return;
        }
        final String crateId = args[2].toLowerCase(Locale.ROOT);
        if (!crateManager.crateIds().contains(crateId)) {
            player.sendMessage(messageManager.get("crate-unknown", "&cNincs ilyen láda: &f%s", crateId));
            return;
        }
        final int amount = args.length >= 4 ? parseAmount(args[3]) : 1;

        // Folia: a célpont inventoryját CSAK a saját régió-szálán szabad érinteni.
        target.getScheduler().run(plugin, task -> {
            crateManager.giveKeys(target, crateId, amount);
            target.sendMessage(messageManager.get("crate-key-received", "&aKaptál: &f%s db %s", amount, crateManager.displayName(crateId)));
        }, null);
        player.sendMessage(messageManager.get("crate-key-given", "&aÁtadva: &f%s db %s kulcs &7-> &f%s", amount, crateId, target.getName()));
    }

    private void handleList(final Player player) {
        if (!requireAdmin(player)) {
            return;
        }
        final List<String> entries = crateManager.listCrates();
        if (entries.isEmpty()) {
            player.sendMessage(messageManager.get("crate-list-empty", "&7Nincs regisztrált láda-blokk."));
            return;
        }
        player.sendMessage(messageManager.get("crate-list-header", "&6Regisztrált láda-blokkok (&f%s&6 db):", entries.size()));
        for (final String entry : entries) {
            player.sendMessage(messageManager.get("crate-list-entry", "&7- &f%s", entry));
        }
    }

    private boolean requireAdmin(final Player player) {
        if (player.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        player.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
        return false;
    }

    private void sendHelp(final Player player) {
        player.sendMessage(messageManager.get("crate-help-header", "&6/crate &7- Láda parancsok:"));
        player.sendMessage(messageManager.get("crate-help-buy", "&e/crate buy <id> [darab] &7- Kulcs vásárlása."));
        player.sendMessage(messageManager.get("crate-help-info", "&e/crate info [id] &7- Ládák és jutalom-esélyek."));
        if (player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(messageManager.get("crate-help-admin",
                    "&e/crate set <id>|remove|give <játékos> <id> [darab]|list &7- Admin."));
        }
    }

    private static int parseAmount(final String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (final NumberFormatException exception) {
            return 1;
        }
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "crate-unknown" -> "&cNincs ilyen láda.";
            case "crate-insufficient-funds" -> "&cNincs elég pénzed ehhez a kulcshoz.";
            default -> "&cA művelet nem sikerült.";
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        final List<String> options = new ArrayList<>(List.of("buy", "info"));
        final boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        if (admin) {
            options.addAll(List.of("set", "remove", "give", "list"));
        }

        final String first = prefixAt(args, 0);
        if (args.length <= 1) {
            return options.stream().filter(option -> option.startsWith(first)).toList();
        }

        if (("buy".equals(first) || "info".equals(first) || (admin && "set".equals(first))) && args.length == 2) {
            final String prefix = prefixAt(args, 1);
            return crateManager.crateIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }

        if (admin && "give".equals(first)) {
            if (args.length == 2) {
                final String prefix = prefixAt(args, 1);
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
            }
            if (args.length == 3) {
                final String prefix = prefixAt(args, 2);
                return crateManager.crateIds().stream().filter(id -> id.startsWith(prefix)).toList();
            }
        }

        return List.of();
    }

}
