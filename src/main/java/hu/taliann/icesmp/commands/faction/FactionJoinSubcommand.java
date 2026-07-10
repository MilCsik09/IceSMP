package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class FactionJoinSubcommand implements FactionSubcommand {

    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public FactionJoinSubcommand(final FactionManager factionManager, final SinManager sinManager,
                                 final CurrencyManager currencyManager, final MessageManager messageManager) {
        this.factionManager = factionManager;
        this.sinManager = sinManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "join";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-join", "Belépés egy frakcióba.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-join", "/faction join <faction>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageManager.get("messages.faction-join-usage", "&cHasználat: %s", usage()));
            return true;
        }

        final FactionType factionType = FactionType.fromInput(args[0]);
        if (factionType == null) {
            sender.sendMessage(messageManager.get("messages.faction-unknown", "&cIsmeretlen frakció: &f%s", args[0]));
            return true;
        }

        final UUID uuid = player.getUniqueId();
        final boolean hasFaction = factionManager.hasChosenFaction(uuid);
        final FactionType currentFaction = factionManager.getFaction(uuid);

        if (hasFaction && currentFaction == factionType) {
            sender.sendMessage(messageManager.get("messages.faction-already-member", "&cMár tagja vagy ennek a frakciónak!"));
            return true;
        }

        // Első csatlakozás mindig ingyenes és időzítetlen; frakcióváltás (van már frakciód,
        // és másikba lépsz) fizetős és cooldownhoz kötött — a DARK ágra is vonatkozik.
        final boolean isSwitch = hasFaction;

        if (factionType == FactionType.DARK) {
            if (!sinManager.isSinner(player)) {
                sender.sendMessage(messageManager.get(
                        "messages.faction-dark-sinners-only",
                        "&5A Sötét frakcióba csak bűnösök léphetnek be."
                ));
                return true;
            }

            if (isSwitch && !chargeSwitch(player, currentFaction)) {
                return true;
            }

            factionManager.setFaction(uuid, FactionType.DARK);
            sinManager.sealDarkPact(player);
            sender.sendMessage(messageManager.get(
                    "messages.faction-dark-pact-sealed",
                    "&5A sötét paktum megköttetett. A bűnöd mostantól örökre veled marad."
            ));
            return true;
        }

        if (isSwitch && !chargeSwitch(player, currentFaction)) {
            return true;
        }

        factionManager.setFaction(uuid, factionType);
        sender.sendMessage(messageManager.get("messages.faction-set-self-success", "&aFrakció beállítva: &f%s", factionType.getDisplayName()));
        return true;
    }

    /**
     * Charges the faction-switch cost (in the player's CURRENT faction currency) and enforces the
     * switch cooldown. On success, deducts the cost, records the switch timestamp and notifies the
     * player. Only called when the player already has a faction and is moving to a different one.
     *
     * @param player the switching player
     * @param currentFaction the player's faction before the switch
     * @return true if the switch may proceed, false if it was refused (a message was already sent)
     */
    private boolean chargeSwitch(final Player player, final FactionType currentFaction) {
        final long remainingCooldownMillis = factionManager.getRemainingSwitchCooldownMillis(player);
        if (remainingCooldownMillis > 0) {
            final double remainingHours = remainingCooldownMillis / 3_600_000.0D;
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-cooldown",
                    "&cFrakciót nemrég váltottál — még &f%.1f óra&c van hátra, mire újra válthatsz.",
                    remainingHours
            ));
            return false;
        }

        final double cost = factionManager.getSwitchCost();
        final CurrencyType currency = CurrencyType.fromFactionType(currentFaction);
        final double balance = currencyManager.getBalance(player, currency);
        if (balance < cost) {
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-insufficient",
                    "&cA frakcióváltás ára &f%s %s&c, de csak &f%s&c van a bankodban.",
                    currencyManager.formatBalance(cost),
                    currency.getDisplayName(),
                    currencyManager.formatBalance(balance)
            ));
            return false;
        }

        currencyManager.setBalance(player, currency, balance - cost);
        factionManager.recordSwitch(player);
        player.sendMessage(messageManager.get(
                "messages.faction-switch-paid",
                "&aFrakcióváltás díja levonva: &f%s %s &7(új egyenleg: &f%s&7).",
                currencyManager.formatBalance(cost),
                currency.getDisplayName(),
                currencyManager.formatBalance(balance - cost)
        ));
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            return List.of("red", "blue", "neutral", "dark");
        }
        return List.of();
    }
}



