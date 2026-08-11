package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.FactionTreasuryManager;
import hu.taliann.icesmp.managers.KingManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Faction treasury view + admin withdrawal:
 * /faction treasury — saját frakció kasszája (admin: mindegyik)
 * /faction treasury withdraw <összeg> — kivét a saját frakció kasszájából a bankodba (admin)
 */
public final class FactionTreasurySubcommand implements FactionSubcommand {

    private static final String ADMIN_PERMISSION = hu.taliann.icesmp.core.Permissions.FACTION;

    private final FactionTreasuryManager treasuryManager;
    private final FactionManager factionManager;
    private final CurrencyManager currencyManager;
    private final KingManager kingManager;
    private final MessageManager messageManager;
    private final hu.taliann.icesmp.managers.ConfigManager configManager;
    /** frakció → {nap, ma kivett összeg} — a tanácsi keret KÖZÖS (memóriában él, a capnek elég). */
    private final java.util.concurrent.ConcurrentHashMap<hu.taliann.icesmp.data.FactionType, double[]> councilWithdrawnToday =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final hu.taliann.icesmp.playerprofile.application.PlayerProfileTreasuryWithdrawalStore
            withdrawalStore = new hu.taliann.icesmp.playerprofile.application.PlayerProfileTreasuryWithdrawalStore();
    private final org.bukkit.plugin.java.JavaPlugin plugin =
            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(FactionTreasurySubcommand.class);

    public FactionTreasurySubcommand(final FactionTreasuryManager treasuryManager, final FactionManager factionManager,
                                     final CurrencyManager currencyManager, final KingManager kingManager,
                                     final MessageManager messageManager,
                                     final hu.taliann.icesmp.managers.ConfigManager configManager) {
        this.treasuryManager = treasuryManager;
        this.factionManager = factionManager;
        this.currencyManager = currencyManager;
        this.kingManager = kingManager;
        this.messageManager = messageManager;
        this.configManager = configManager;
    }

    @Override
    public String name() {
        return "treasury";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-treasury", "Frakciókassza megtekintése.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-treasury", "/faction treasury [withdraw <összeg>]");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        if (args.length >= 2 && "withdraw".equalsIgnoreCase(args[0])) {
            return handleWithdraw(player, args[1]);
        }

        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.faction-treasury-header", "&6Frakciókasszák:"));
            for (final FactionType faction : FactionType.values()) {
                sender.sendMessage(messageManager.get(
                        "messages.faction-treasury-line",
                        "&e%s&7: &f%s",
                        faction.getDisplayName(),
                        currencyManager.formatBalance(treasuryManager.getBalance(faction))
                ));
            }
            return true;
        }

        final FactionType faction = factionManager.getChosenFaction(player.getUniqueId()).orElse(null);
        if (faction == null) {
            sender.sendMessage(messageManager.get("messages.faction-choose-first",
                    "&cFrakciókasszához előbb válassz frakciót: &f/faction join <frakció>&c."));
            return true;
        }
        sender.sendMessage(messageManager.get(
                "messages.faction-treasury-own",
                "&6A(z) &f%s &6frakció kasszája: &f%s",
                faction.getDisplayName(),
                currencyManager.formatBalance(treasuryManager.getBalance(faction))
        ));
        return true;
    }

    /** A Vének Tanácsa (setterrel kötve): a NEUTRAL tanácstag is nyúlhat a kasszához. */
    private volatile hu.taliann.icesmp.managers.CouncilManager councilManager;

    public void setCouncilManager(final hu.taliann.icesmp.managers.CouncilManager councilManager) {
        this.councilManager = councilManager;
    }

    private boolean isNeutralCouncillor(final Player player) {
        final hu.taliann.icesmp.managers.CouncilManager councilRef = councilManager;
        return factionManager.isMember(player.getUniqueId(), FactionType.NEUTRAL)
                && councilRef != null && councilRef.isCouncillor(player.getUniqueId());
    }

    private boolean handleWithdraw(final Player player, final String rawAmount) {
        final FactionType faction = factionManager.getChosenFaction(player.getUniqueId()).orElse(null);
        if (faction == null) {
            player.sendMessage(messageManager.get("messages.faction-choose-first",
                    "&cFrakciókasszához előbb válassz frakciót: &f/faction join <frakció>&c."));
            return true;
        }
        // The crowned king commands their own faction's treasury; admins can always withdraw.
        // A Menedéknek nincs királya — ott a Vének Tanácsának tagjai vehetnek ki (saját kerettel).
        if (!player.hasPermission(ADMIN_PERMISSION) && !kingManager.isKing(player)
                && !isNeutralCouncillor(player)) {
            player.sendMessage(messageManager.get("messages.faction-treasury-king-only", "&cA kasszából csak a frakció királya (a Menedékben: a Vének Tanácsa) vagy admin vehet ki."));
            return true;
        }

        final double amount;
        try {
            amount = Double.parseDouble(rawAmount);
        } catch (final NumberFormatException exception) {
            player.sendMessage(messageManager.get("messages.invalid-amount", "&cÉrvénytelen összeg."));
            return true;
        }

        if (!Double.isFinite(amount) || amount <= 0.0D) {
            player.sendMessage(messageManager.get("messages.amount-must-be-positive", "&cAz összegnek pozitívnak kell lennie."));
            return true;
        }

        // Bank-only szabály: a kassza-kivét FIZIKAI veretben érkezik a király kezébe
        // (számlára pénz csak bankbefizetéssel kerülhet) — és napi limit fékezi, hogy
        // egy király ne üríthesse egy mozdulattal a kasszát (élő kulcs, 0 = korlátlan).
        // A tanácsi keret a FRAKCIÓ KÖZÖS számlálója (nem fejenkénti): a 3 tanácstag
        // együtt sem viheti a királyi keret fölé.
        final boolean councilPath = !kingManager.isKing(player) && isNeutralCouncillor(player);
        final double dailyCap = councilPath
                ? configManager.getDouble("factions.council.withdraw-daily-cap", 400.0D)
                : configManager.getDouble("factions.treasury.withdraw-daily-cap", 1000.0D);
        final long today = System.currentTimeMillis() / 86_400_000L;
        if (councilPath) {
            final double[] shared = councilWithdrawnToday.get(faction);
            final double takenToday = shared != null && (long) shared[0] == today ? shared[1] : 0.0D;
            if (dailyCap > 0.0D && takenToday + amount > dailyCap) {
                player.sendMessage(messageManager.get(
                        "messages.faction-treasury-daily-cap",
                        "&cA mai kassza-kivét keret elfogyott (&f%s&c/nap). Holnap folytathatod.",
                        currencyManager.formatBalance(dailyCap)));
                return true;
            }
            if (!treasuryManager.withdraw(faction, amount)) {
                player.sendMessage(messageManager.get("messages.faction-treasury-insufficient",
                        "&cNincs ennyi a frakciókasszában."));
                return true;
            }
            councilWithdrawnToday.compute(faction, (key, old) ->
                    old == null || (long) old[0] != today
                            ? new double[]{today, amount} : new double[]{today, old[1] + amount});
            finishWithdrawal(player, faction, amount);
            return true;
        }

        withdrawalStore.reserve(player.getUniqueId(), today, amount, dailyCap)
                .whenComplete((reservation, reserveFailure) -> {
                    if (reserveFailure != null) {
                        runOnOwner(player, () -> player.sendMessage(messageManager.get(
                                "messages.faction-treasury-profile-failed",
                                "&cA napi kivételi keret PlayerProfile mentése meghiúsult; a kassza nem változott.")));
                        return;
                    }
                    if (reservation == null || !reservation.allowed()) {
                        runOnOwner(player, () -> player.sendMessage(messageManager.get(
                                "messages.faction-treasury-daily-cap",
                                "&cA mai kassza-kivét keret elfogyott (&f%s&c/nap). Holnap folytathatod.",
                                currencyManager.formatBalance(dailyCap))));
                        return;
                    }
                    final boolean withdrawn;
                    try {
                        withdrawn = treasuryManager.withdraw(faction, amount);
                    } catch (final RuntimeException failure) {
                        withdrawalStore.rollback(player.getUniqueId(), reservation);
                        runOnOwner(player, () -> player.sendMessage(messageManager.get(
                                "messages.faction-treasury-persistence-failed",
                                "&cA frakciókassza tartós kivéte meghiúsult; a napi keret kompenzálása elindult.")));
                        return;
                    }
                    if (!withdrawn) {
                        withdrawalStore.rollback(player.getUniqueId(), reservation)
                                .whenComplete((rolledBack, rollbackFailure) -> runOnOwner(player, () ->
                                        player.sendMessage(messageManager.get(
                                                "messages.faction-treasury-insufficient",
                                                "&cNincs ennyi a frakciókasszában."))));
                        return;
                    }
                    runOnOwner(player, () -> finishWithdrawal(player, faction, amount));
                });
        return true;
    }

    private void finishWithdrawal(final Player player, final FactionType faction,
                                  final double amount) {
        currencyManager.payOutTokens(player, CurrencyType.fromFactionType(faction),
                (long) Math.floor(amount));
        player.sendMessage(messageManager.get(
                "messages.faction-treasury-withdraw-success",
                "&aKivét a kasszából: &f%s %s &7(veretben, a kezedbe).",
                currencyManager.formatBalance(amount), faction.getDisplayName()));
    }

    private void runOnOwner(final Player player, final Runnable action) {
        player.getScheduler().run(plugin, ignored -> action.run(), null);
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        final boolean canWithdraw = sender.hasPermission(ADMIN_PERMISSION)
                || (sender instanceof Player player && kingManager.isKing(player));
        // Két hosszal: 0 = "/faction treasury " (üres prefix), 1 = gépelés közben (args[0] prefix).
        if (args.length <= 1 && canWithdraw) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return "withdraw".startsWith(prefix) ? List.of("withdraw") : List.of();
        }
        return List.of();
    }
}
