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

/**
 * Shared rules for CHANGING faction — used by both {@code /faction join} and
 * {@code /faction leave}, so leaving can never be a free/gate-less backdoor around
 * the switch cost, the cooldown or the neutral-capital gate.
 */
final class FactionSwitchRules {

    private FactionSwitchRules() {
    }

    /**
     * Gate for faction changes: once a player has chosen a faction, changes are only allowed
     * while standing in the NEUTRAL capital (config: factions.switch.only-in-neutral-capital).
     * Fail-open when no neutral capital zone has been defined yet, so an unbuilt world never
     * locks players out.
     *
     * @return true if the change may proceed; false if refused (a message was sent)
     */
    static boolean passesNeutralCapitalGate(final Player player, final TerritoryManager territoryManager,
                                            final ConfigManager configManager, final MessageManager messageManager) {
        if (!configManager.getBoolean("factions.switch.only-in-neutral-capital", true)) {
            return true;
        }
        final Territory neutralCapital = territoryManager.getCapital(FactionType.NEUTRAL);
        if (neutralCapital == null) {
            return true;
        }
        final Territory here = territoryManager.getTerritoryAt(player.getLocation());
        if (here != null && here.id().equals(neutralCapital.id())) {
            return true;
        }
        player.sendMessage(messageManager.get(
                "messages.faction-switch-only-in-neutral-capital",
                "&cFrakciót váltani csak a Menedék fővárosában, Caldesterában lehet (&f%s&c).",
                neutralCapital.name()));
        return false;
    }

    /**
     * Charges the faction-switch cost (in the player's CURRENT faction currency) and enforces the
     * switch cooldown. On success, deducts the cost atomically, records the switch timestamp and
     * notifies the player. Call only for an actual change away from a chosen, non-NEUTRAL faction.
     *
     * @return true if the switch may proceed, false if it was refused (a message was already sent)
     */
    /**
     * Season gates for ANY faction change after the first free choice — the paid ones AND the
     * free paths (leaving NEUTRAL, entering DARK) alike: a szezon-végi teljes váltás-zár
     * (lockout-final-days) és a szezononkénti plafon (max-per-season). A kényszer-száműzetés
     * (SinManager exile) nem erre az útra jön, azt a szabály szándékosan nem fogja.
     *
     * @return true if the change may proceed; false if refused (a message was sent)
     */
    static boolean passesSeasonRules(final Player player, final FactionManager factionManager,
                                     final MessageManager messageManager) {
        // Szezon-végi zár: a liga hajrájában (alap: utolsó 7 nap) egyáltalán nincs váltás.
        if (factionManager.isInSeasonEndLockout()) {
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-season-lockout",
                    "&cA szezon hajrájában (az utolsó &f%d nap&cban) már nem lehet frakciót váltani — a zászlód alatt fejezed be, amit elkezdtél.",
                    factionManager.getSwitchLockoutFinalDays()
            ));
            return false;
        }
        // Szezononkénti plafon (alap: 2 váltás / szezon, az első ingyenes választás nem számít).
        final int maxPerSeason = factionManager.getMaxSwitchesPerSeason();
        final int usedThisSeason = factionManager.getSwitchesThisSeason(player);
        if (maxPerSeason > 0 && usedThisSeason >= maxPerSeason) {
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-season-cap",
                    "&cEbben a szezonban már &f%d&c alkalommal váltottál frakciót — ez a szezononkénti maximum. A következő szezonban válthatsz újra.",
                    usedThisSeason
            ));
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
        if (remainingCooldownMillis > 0) {
            final double remainingHours = remainingCooldownMillis / 3_600_000.0D;
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-cooldown",
                    "&cFrakciót nemrég váltottál — még &f%.1f óra&c van hátra, mire újra válthatsz.",
                    remainingHours));
            return false;
        }

        final double configuredCost = factionManager.getSwitchCost();
        if (!Double.isFinite(configuredCost) || configuredCost < 0.0D) {
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-config-invalid",
                    "&cA frakcióváltás díja hibásan van konfigurálva; a váltás fail-closed leállt."));
            return false;
        }
        final double cost = configuredCost;
        final CurrencyType currency = CurrencyType.fromFactionType(currentFaction);
        final boolean committed;
        try {
            committed = factionManager.switchFactionDurably(player.getUniqueId(),
                    factionManager.getChosenFaction(player.getUniqueId()).orElse(null),
                    targetFaction, currency, cost);
        } catch (final RuntimeException | Error failure) {
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-persistence-failed",
                    "&cA frakcióváltás tartós tranzakciója nem zárult le. Ne próbáld újra; a recovery journal alapján adminellenőrzés szükséges."));
            throw failure;
        }
        if (!committed) {
            player.sendMessage(messageManager.get(
                    "messages.faction-switch-insufficient",
                    "&cA frakcióváltás ára &f%s %s&c, de csak &f%s&c van a bankodban, vagy a tagságod közben megváltozott.",
                    currencyManager.formatBalance(cost),
                    currency.getDisplayName(),
                    currencyManager.formatBalance(currencyManager.getBalance(player, currency))));
            return false;
        }

        factionManager.recordSwitch(player);
        player.sendMessage(messageManager.get(
                "messages.faction-switch-paid",
                "&aFrakcióváltás tartósan véglegesítve. Díj: &f%s %s &7(új egyenleg: &f%s&7).",
                currencyManager.formatBalance(cost),
                currency.getDisplayName(),
                currencyManager.formatBalance(currencyManager.getBalance(player, currency))));
        return true;
    }

}
