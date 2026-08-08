package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;

/** Shared faction-change rules for join and leave flows. */
final class FactionSwitchRules {

    private FactionSwitchRules() { }

    static boolean passesNeutralCapitalGate(final Player player,
                                            final TerritoryManager territoryManager,
                                            final ConfigManager configManager,
                                            final MessageManager messageManager) {
        if (!configManager.getBoolean("factions.switch.only-in-neutral-capital", true)) {
            return true;
        }
        final Territory neutralCapital = territoryManager.getCapital(FactionType.NEUTRAL);
        if (neutralCapital == null) return true;
        final Territory here = territoryManager.getTerritoryAt(player.getLocation());
        if (here != null && here.id().equals(neutralCapital.id())) return true;
        player.sendMessage(messageManager.get(
                "messages.faction-switch-only-in-neutral-capital",
                "&cFrakciót váltani csak a Menedék fővárosában, Caldesterában lehet (&f%s&c).",
                neutralCapital.name()));
        return false;
    }

    static boolean passesSeasonRules(final Player player,
                                     final FactionManager factionManager,
                                     final MessageManager messageManager) {
        if (factionManager.isInSeasonEndLockout()) {
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-season-lockout",
                    "&cA szezon hajrájában (az utolsó &f%d nap&cban) már nem lehet frakciót váltani — a zászlód alatt fejezed be, amit elkezdtél.",
                    factionManager.getSwitchLockoutFinalDays()));
            return false;
        }
        final int maxPerSeason = factionManager.getMaxSwitchesPerSeason();
        final int usedThisSeason = factionManager.getSwitchesThisSeason(player);
        if (maxPerSeason > 0 && usedThisSeason >= maxPerSeason) {
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-season-cap",
                    "&cEbben a szezonban már &f%d&c alkalommal váltottál frakciót — ez a szezononkénti maximum. A következő szezonban válthatsz újra.",
                    usedThisSeason));
            return false;
        }
        return true;
    }

    static boolean commitPaidSwitch(final Player player,
                                    final FactionType currentFaction,
                                    final FactionType targetFaction,
                                    final FactionManager factionManager,
                                    final CurrencyManager currencyManager,
                                    final MessageManager messageManager) {
        final long remainingCooldownMillis = factionManager.getRemainingSwitchCooldownMillis(player);
        if (remainingCooldownMillis > 0L) {
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-cooldown",
                    "&cFrakciót nemrég váltottál — még &f%.1f óra&c van hátra, mire újra válthatsz.",
                    remainingCooldownMillis / 3_600_000.0D));
            return false;
        }

        final double cost = factionManager.getSwitchCost();
        if (!Double.isFinite(cost) || cost < 0.0D) {
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-config-invalid",
                    "&cA frakcióváltás díja hibásan van konfigurálva; a váltás fail-closed leállt."));
            return false;
        }
        final CurrencyType currency = CurrencyType.fromFactionType(currentFaction);
        final boolean committed;
        try {
            committed = factionManager.switchFactionDurably(player.getUniqueId(),
                    factionManager.getChosenFaction(player.getUniqueId()).orElse(null),
                    targetFaction, currency, cost);
        } catch (final RuntimeException | Error failure) {
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-persistence-failed",
                    "&cA frakcióváltás PlayerProfile tranzakciója nem zárult le. A váltás nem tekinthető sikeresnek."));
            throw failure;
        }
        if (!committed) {
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-insufficient",
                    "&cA frakcióváltás ára &f%s %s&c, de csak &f%s&c van a bankodban, vagy a tagságod közben megváltozott.",
                    currencyManager.formatBalance(cost), currency.getDisplayName(),
                    currencyManager.formatBalance(currencyManager.getBalance(player, currency))));
            return false;
        }

        player.sendMessage(messageManager.get(
                "messages.faction-switch-paid",
                "&aFrakcióváltás tartósan véglegesítve. Díj: &f%s %s &7(új egyenleg: &f%s&7).",
                currencyManager.formatBalance(cost), currency.getDisplayName(),
                currencyManager.formatBalance(currencyManager.getBalance(player, currency))));
        return true;
    }
}
