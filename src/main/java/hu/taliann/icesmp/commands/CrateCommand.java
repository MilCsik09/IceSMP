package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.core.Permissions;
import hu.taliann.icesmp.crates.CrateRules;
import hu.taliann.icesmp.crates.CrateTaskSubmission;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;

/** Player list/preview/buy and permission-protected native crate administration. */
public final class CrateCommand implements BasicCommand {

    public static final String ADMIN_PERMISSION = Permissions.CRATE;
    private static final int MAX_COMMAND_AMOUNT = hu.taliann.icesmp.crates.CrateRules.MAX_KEY_AMOUNT;

    private final JavaPlugin plugin;
    private final CrateManager crateManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public CrateCommand(final JavaPlugin plugin, final CrateManager crateManager,
                        final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.crateManager = crateManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack source, final @NonNull String[] args) {
        final CommandSender sender = source.getSender();
        if (args.length == 0) {
            if (sender instanceof Player player) {
                crateManager.openBrowser(player);
            } else {
                sendHelp(sender);
            }
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "buy" -> withPlayer(sender, player -> handleBuy(player, args));
            case "info" -> withPlayer(sender, player -> handleInfo(player, args));
            case "preview" -> withPlayer(sender, player -> handlePreview(player, args));
            case "set" -> withAdminPlayer(sender, player -> handleSet(player, args));
            case "remove" -> withAdminPlayer(sender, this::handleRemove);
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender);
            case "stats" -> handleStats(sender, args);
            case "resetstats" -> handleResetStats(sender, args);
            case "status" -> handleStatus(sender);
            default -> sendHelp(sender);
        }
    }

    private void handleBuy(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("crate-usage-buy", "&cHasználat: /crate buy <láda-id> [darab]"));
            return;
        }
        final String crateId = args[1].toLowerCase(Locale.ROOT);
        final Integer amount = args.length >= 3 ? parseAmount(args[2]) : 1;
        if (amount == null) {
            player.sendMessage(messageManager.get("crate-invalid-amount", "&cA darabszám 1..%s lehet.", MAX_COMMAND_AMOUNT));
            return;
        }
        crateManager.buyKeyAsync(player, crateId, amount, result -> {
            if (!result.success()) {
                player.sendMessage(messageManager.get(result.errorKey(), defaultError(result.errorKey()), crateId));
                return;
            }
            player.sendMessage(messageManager.get("crate-key-bought",
                    "&aMegvetted: &f%s db %s &7(&c-%s %s&7 — elégett).",
                    result.amount(), result.displayName(),
                    currencyManager.formatBalance(result.totalPrice()), result.currency().getDisplayName()));
        });
    }

    private void handleInfo(final Player player, final String[] args) {
        if (args.length < 2) {
            crateManager.openBrowser(player);
            return;
        }
        final String crateId = args[1].toLowerCase(Locale.ROOT);
        final CrateManager.CrateDefinition definition = crateManager.definition(crateId);
        final CrateManager.AccessDecision access = crateManager.accessDecision(player, definition);
        if (!access.allowed()) {
            player.sendMessage(messageManager.get(access.errorKey(), defaultError(access.errorKey()), crateId));
            return;
        }
        player.sendMessage(messageManager.get("crate-info-title",
                "&6%s &7— kulcs ára: &f%s %s &7| kulcs/nyitás: &f%s",
                definition.displayName(), currencyManager.formatBalance(definition.keyPriceAmount()),
                definition.keyPriceCurrency().getDisplayName(), definition.requiredKeys()));
        final long cooldown = crateManager.cooldownRemaining(player.getUniqueId(), crateId);
        player.sendMessage(messageManager.get("crate-info-policy",
                "&7Cooldown: &f%s &7| többszörös nyitás: &f%s &7| nálad lévő kulcs: &f%s",
                cooldown <= 0L ? "nincs" : Math.max(1L, (cooldown + 999L) / 1000L) + " mp",
                definition.massOpenEnabled() ? "max " + definition.massOpenMaximum() : "kikapcsolva",
                crateManager.keyCount(player, crateId)));
        for (final CrateManager.RewardOdds odds : crateManager.rewardOdds(crateId)) {
            player.sendMessage(messageManager.get("crate-info-odds", "&7- &f%s&7: &e%s%%",
                    odds.description(), CrateRules.formatPercent(odds.percent())));
        }
    }

    private void handlePreview(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("crate-usage-preview", "&cHasználat: /crate preview <láda-id>"));
            return;
        }
        final String crateId = args[1].toLowerCase(Locale.ROOT);
        final CrateManager.AccessDecision access = crateManager.accessDecision(player, crateManager.definition(crateId));
        if (!access.allowed()) {
            player.sendMessage(messageManager.get(access.errorKey(), defaultError(access.errorKey()), crateId));
            return;
        }
        if (!crateManager.openPreview(player, crateId)) {
            player.sendMessage(messageManager.get("crate-opening-changed",
                    "&eA láda hozzáférési feltételei közben megváltoztak."));
        }
    }

    private void handleSet(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("crate-usage-set", "&cHasználat: /crate set <láda-id> (a nézett blokkra)"));
            return;
        }
        final String crateId = args[1].toLowerCase(Locale.ROOT);
        final Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(messageManager.get("crate-no-target", "&cNincs blokk a látómeződben (max 5 blokk)."));
            return;
        }
        crateManager.setCrateAsync(target.getLocation(), crateId, result -> reply(player, result,
                "crate-set", "&aA nézett blokk mostantól láda: &f%s", crateManager.displayName(crateId)));
    }

    private void handleRemove(final Player player) {
        final Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage(messageManager.get("crate-no-target", "&cNincs blokk a látómeződben (max 5 blokk)."));
            return;
        }
        crateManager.removeCrateAsync(target.getLocation(), result -> reply(player, result,
                "crate-removed", "&aA nézett blokk láda-jelölése törölve."));
    }

    private void reply(final Player player, final CrateManager.MutationResult result,
                       final String successKey, final String successFallback, final Object... args) {
        CrateTaskSubmission.entity(plugin, player.getScheduler(), () -> {
            if (result.success()) {
                player.sendMessage(messageManager.get(successKey, successFallback, args));
            } else {
                player.sendMessage(messageManager.get(result.errorKey(), defaultError(result.errorKey())));
            }
        }, () -> { });
    }

    private void handleGive(final CommandSender sender, final String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("crate-usage-give", "&cHasználat: /crate give <játékos> <láda-id> [darab]"));
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        final String crateId = args[2].toLowerCase(Locale.ROOT);
        final Integer amount = args.length >= 4 ? parseAmount(args[3]) : 1;
        if (target == null) {
            sender.sendMessage(messageManager.get("player-not-found", "&cNincs ilyen online játékos: &f%s", args[1]));
            return;
        }
        if (crateManager.definition(crateId) == null) {
            sender.sendMessage(messageManager.get("crate-unknown", "&cNincs ilyen láda: &f%s", crateId));
            return;
        }
        if (amount == null) {
            sender.sendMessage(messageManager.get("crate-invalid-amount", "&cA darabszám 1..%s lehet.", MAX_COMMAND_AMOUNT));
            return;
        }
        final String targetName = target.getName();
        CrateTaskSubmission.entity(plugin, target.getScheduler(), () -> {
            if (!crateManager.giveKeys(target, crateId, amount)) {
                sendToSender(sender, messageManager.get("crate-broken", "&cA kulcs nem hozható létre biztonságosan."));
                return;
            }
            target.sendMessage(messageManager.get("crate-key-received", "&aKaptál: &f%s db %s",
                    amount, crateManager.displayName(crateId)));
            sendToSender(sender, messageManager.get("crate-key-given",
                    "&aÁtadva: &f%s db %s kulcs &7-> &f%s", amount, crateId, targetName));
        }, () -> sendToSender(sender, messageManager.get("player-not-found",
                "&cA céljátékos közben kilépett: &f%s", targetName)));
    }

    private void handleList(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        final List<String> entries = crateManager.listCrates();
        if (entries.isEmpty()) {
            sender.sendMessage(messageManager.get("crate-list-empty", "&7Nincs regisztrált láda-blokk."));
            return;
        }
        sender.sendMessage(messageManager.get("crate-list-header", "&6Regisztrált láda-blokkok (&f%s&6 db):", entries.size()));
        entries.forEach(entry -> sender.sendMessage(messageManager.get("crate-list-entry", "&7- &f%s", entry)));
    }

    private void handleStats(final CommandSender sender, final String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        final UUID playerId;
        if (args.length < 2 && sender instanceof Player player) {
            playerId = player.getUniqueId();
        } else if (args.length >= 2) {
            final Player online = Bukkit.getPlayerExact(args[1]);
            playerId = online == null ? crateManager.findStatsPlayer(args[1]) : online.getUniqueId();
        } else {
            sender.sendMessage(messageManager.get("crate-usage-stats", "&cHasználat: /crate stats <játékos|uuid> [láda-id]"));
            return;
        }
        if (playerId == null) {
            sender.sendMessage(messageManager.get("crate-stats-missing", "&eNincs crate-statisztika ehhez a játékoshoz."));
            return;
        }
        final CrateManager.StatsView stats = crateManager.stats(playerId);
        sender.sendMessage(messageManager.get("crate-stats-header", "&6Crate stat: &f%s &7(%s) — összesen: &e%s",
                stats.lastKnownName() == null ? "ismeretlen" : stats.lastKnownName(), playerId, stats.total()));
        final String filter = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : null;
        for (final Map.Entry<String, Long> entry : stats.perCrate().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (filter == null || filter.equals(entry.getKey())) {
                sender.sendMessage(messageManager.get("crate-stats-entry", "&7- &f%s&7: &e%s", entry.getKey(), entry.getValue()));
            }
        }
    }

    private void handleResetStats(final CommandSender sender, final String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("crate-usage-resetstats", "&cHasználat: /crate resetstats <játékos|uuid> [láda-id|all]"));
            return;
        }
        final Player online = Bukkit.getPlayerExact(args[1]);
        final UUID playerId = online == null ? crateManager.findStatsPlayer(args[1]) : online.getUniqueId();
        if (playerId == null) {
            sender.sendMessage(messageManager.get("crate-stats-missing", "&eNincs crate-statisztika ehhez a játékoshoz."));
            return;
        }
        final String crateId = args.length < 3 || "all".equalsIgnoreCase(args[2])
                ? null : args[2].toLowerCase(Locale.ROOT);
        if (crateId != null && crateManager.definition(crateId) == null) {
            sender.sendMessage(messageManager.get("crate-unknown", "&cNincs ilyen láda: &f%s", crateId));
            return;
        }
        crateManager.resetStatsAsync(playerId, crateId, result ->
                sendToSender(sender, result.success()
                        ? messageManager.get("crate-stats-reset", "&aCrate-statisztika törölve: &f%s &7(%s)", playerId,
                        crateId == null ? "összes" : crateId)
                        : messageManager.get(result.errorKey(), defaultError(result.errorKey()))));
    }

    private void handleStatus(final CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        sender.sendMessage(messageManager.get("crate-status-header", "&6Crate status: &f%s érvényes láda, %s hiba, %s manuális recovery.",
                crateManager.crateIds().size(), crateManager.configErrors().size(),
                crateManager.manualRecoveryCount()));
        crateManager.configErrors().forEach(error -> sender.sendMessage(
                messageManager.get("crate-status-error", "&c- %s", error)));
    }

    private void sendToSender(final CommandSender sender, final String message) {
        if (sender instanceof Player player) {
            CrateTaskSubmission.entity(plugin, player.getScheduler(), () -> player.sendMessage(message), () -> { });
        } else {
            CrateTaskSubmission.global(plugin, Bukkit.getGlobalRegionScheduler(),
                    () -> sender.sendMessage(message), () -> { });
        }
    }

    private void withPlayer(final CommandSender sender, final java.util.function.Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
        } else {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
        }
    }

    private void withAdminPlayer(final CommandSender sender, final java.util.function.Consumer<Player> action) {
        if (!requireAdmin(sender)) {
            return;
        }
        withPlayer(sender, action);
    }

    private boolean requireAdmin(final CommandSender sender) {
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
        return false;
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("crate-help-header", "&6/crate &7- Láda parancsok:"));
        sender.sendMessage(messageManager.get("crate-help-player",
                "&e/crates &7| &e/crate preview <id> &7| &e/crate info <id> &7| &e/crate buy <id> [darab]"));
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("crate-help-admin",
                    "&e/crate set|remove|give|list|stats|resetstats|status &7- Admin."));
        }
    }

    private static Integer parseAmount(final String raw) {
        try {
            final int amount = Integer.parseInt(raw);
            return amount >= 1 && amount <= MAX_COMMAND_AMOUNT ? amount : null;
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private static String defaultError(final String errorKey) {
        return switch (errorKey) {
            case "crate-unknown" -> "&cNincs ilyen vagy érvényes láda.";
            case "crate-insufficient-funds" -> "&cNincs elég pénzed ehhez a kulcshoz.";
            case "crate-no-permission" -> "&cNincs jogosultságod ehhez a ládához.";
            case "crate-world-disabled" -> "&cEz a láda ebben a világban nem használható.";
            case "crate-opening-changed" -> "&eA láda konfigurációja vagy helye közben megváltozott.";
            case "crate-broken" -> "&cA láda definíciója vagy jutalma hibás.";
            case "crate-invalid-amount" -> "&cÉrvénytelen darabszám.";
            case "crate-not-a-crate" -> "&7A nézett blokk nem láda.";
            case "crate-storage-unavailable" -> "&cA crate state nem menthető biztonságosan; a művelet visszavonva.";
            case "crate-opening-busy" -> "&eA játékosnak folyamatban lévő ládanyitása van.";
            default -> "&cA művelet nem sikerült.";
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack source,
                                                final @NonNull String[] args) {
        final CommandSender sender = source.getSender();
        final boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        final List<String> firstOptions = new ArrayList<>(List.of("buy", "info", "preview"));
        if (admin) {
            firstOptions.addAll(List.of("set", "remove", "give", "list", "stats", "resetstats", "status"));
        }
        if (args.length <= 1) {
            final String prefix = prefixAt(args, 0);
            return firstOptions.stream().filter(option -> option.startsWith(prefix)).toList();
        }
        final String first = args[0].toLowerCase(Locale.ROOT);
        if (List.of("buy", "info", "preview", "set").contains(first) && args.length == 2
                && (!"set".equals(first) || admin)) {
            final String prefix = prefixAt(args, 1);
            final List<String> ids;
            if ("set".equals(first) || !(sender instanceof Player player)) {
                ids = crateManager.crateIds();
            } else {
                ids = crateManager.accessibleCrateIds(player);
            }
            return ids.stream().filter(id -> id.startsWith(prefix)).toList();
        }
        if (admin && List.of("give", "stats", "resetstats").contains(first) && args.length == 2) {
            final String prefix = prefixAt(args, 1);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (admin && "give".equals(first) && args.length == 3) {
            final String prefix = prefixAt(args, 2);
            return crateManager.crateIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }
        if (admin && ("stats".equals(first) || "resetstats".equals(first)) && args.length == 3) {
            final String prefix = prefixAt(args, 2);
            final List<String> ids = new ArrayList<>(crateManager.crateIds());
            if ("resetstats".equals(first)) {
                ids.add("all");
            }
            return ids.stream().filter(id -> id.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
