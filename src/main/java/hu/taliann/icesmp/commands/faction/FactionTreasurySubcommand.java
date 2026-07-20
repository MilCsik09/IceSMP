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
    private final org.bukkit.NamespacedKey withdrawDayKey =
            org.bukkit.NamespacedKey.fromString("icesmp:treasury_withdraw_day");
    private final org.bukkit.NamespacedKey withdrawSumKey =
            org.bukkit.NamespacedKey.fromString("icesmp:treasury_withdraw_sum");

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

        final FactionType faction = factionManager.getFaction(player.getUniqueId());
        sender.sendMessage(messageManager.get(
                "messages.faction-treasury-own",
                "&6A(z) &f%s &6frakció kasszája: &f%s",
                faction.getDisplayName(),
                currencyManager.formatBalance(treasuryManager.getBalance(faction))
        ));
        return true;
    }

    private boolean handleWithdraw(final Player player, final String rawAmount) {
        // The crowned king commands their own faction's treasury; admins can always withdraw.
        if (!player.hasPermission(ADMIN_PERMISSION) && !kingManager.isKing(player)) {
            player.sendMessage(messageManager.get("messages.faction-treasury-king-only", "&cA kasszából csak a frakció királya (vagy admin) vehet ki."));
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

        final FactionType faction = factionManager.getFaction(player.getUniqueId());

        // Bank-only szabály: a kassza-kivét FIZIKAI veretben érkezik a király kezébe
        // (számlára pénz csak bankbefizetéssel kerülhet) — és napi limit fékezi, hogy
        // egy király ne üríthesse egy mozdulattal a kasszát (élő kulcs, 0 = korlátlan).
        final double dailyCap = configManager.getDouble("factions.treasury.withdraw-daily-cap", 1000.0D);
        final long today = System.currentTimeMillis() / 86_400_000L;
        final long storedDay = player.getPersistentDataContainer()
                .getOrDefault(withdrawDayKey, org.bukkit.persistence.PersistentDataType.LONG, -1L);
        final double takenToday = storedDay == today ? player.getPersistentDataContainer()
                .getOrDefault(withdrawSumKey, org.bukkit.persistence.PersistentDataType.DOUBLE, 0.0D) : 0.0D;
        if (dailyCap > 0.0D && takenToday + amount > dailyCap) {
            player.sendMessage(messageManager.get(
                    "messages.faction-treasury-daily-cap",
                    "&cA mai kassza-kivét kereted elfogyott (&f%s&c/nap). Holnap folytathatod.",
                    currencyManager.formatBalance(dailyCap)));
            return true;
        }

        if (!treasuryManager.withdraw(faction, amount)) {
            player.sendMessage(messageManager.get("messages.faction-treasury-insufficient", "&cNincs ennyi a frakciókasszában."));
            return true;
        }

        player.getPersistentDataContainer().set(withdrawDayKey, org.bukkit.persistence.PersistentDataType.LONG, today);
        player.getPersistentDataContainer().set(withdrawSumKey, org.bukkit.persistence.PersistentDataType.DOUBLE, takenToday + amount);
        currencyManager.payOutTokens(player, CurrencyType.fromFactionType(faction), (long) Math.floor(amount));
        player.sendMessage(messageManager.get(
                "messages.faction-treasury-withdraw-success",
                "&aKivét a kasszából: &f%s %s &7(veretben, a kezedbe).",
                currencyManager.formatBalance(amount),
                faction.getDisplayName()
        ));
        return true;
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
