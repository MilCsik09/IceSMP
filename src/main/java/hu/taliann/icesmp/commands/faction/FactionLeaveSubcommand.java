package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class FactionLeaveSubcommand implements FactionSubcommand {

    private final FactionManager factionManager;
    private final JavaPlugin plugin;
    private final hu.taliann.icesmp.managers.SinManager sinManager;
    private final CurrencyManager currencyManager;
    private final TerritoryManager territoryManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    /** Setter-injektált: DARK-elhagyáskor a sötét spec elengedéséhez. */
    private volatile hu.taliann.icesmp.managers.SpecializationManager specializationManager;

    public void setSpecializationManager(final hu.taliann.icesmp.managers.SpecializationManager specializationManager) {
        this.specializationManager = specializationManager;
    }

    public FactionLeaveSubcommand(final JavaPlugin plugin, final FactionManager factionManager,
                                  final hu.taliann.icesmp.managers.SinManager sinManager,
                                  final CurrencyManager currencyManager,
                                  final TerritoryManager territoryManager, final ConfigManager configManager,
                                  final MessageManager messageManager) {
        this.plugin = plugin;
        this.factionManager = factionManager;
        this.sinManager = sinManager;
        this.currencyManager = currencyManager;
        this.territoryManager = territoryManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "leave";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-leave", "Kilépés a jelenlegi frakcióból.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-leave", "/faction leave");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }

        // A kilépés szabályos frakcióváltásnak számít Semlegesbe: ugyanaz a főváros-kapu,
        // ár és cooldown vonatkozik rá, mint a /faction join váltásra — különben a
        // leave+join páros ingyenes, kapu nélküli kerülőút lenne.
        final FactionType currentFaction = factionManager.getFaction(player.getUniqueId());
        // Az örök paktum nem pénz-kérdés: paktumos Kitaszított nem léphet ki — az
        // egyetlen kiút a vezeklés-lánc (breakDarkPact); anélkül a leave fizetős
        // forgóajtóvá tenné a száműzetést.
        if (currentFaction == FactionType.DARK && sinManager.hasDarkPact(player)) {
            sender.sendMessage(messageManager.get("messages.faction-dark-pact-locked",
                    "&5A sötét paktum örök — a Kitaszítottak közül nem vezet ki pénz. "
                            + "Az egyetlen út a vezeklés-küldetéslánc."));
            return true;
        }
        final boolean leavingKingdom = factionManager.hasChosenFaction(player.getUniqueId())
                && currentFaction != FactionType.NEUTRAL;
        if (leavingKingdom) {
            if (!FactionSwitchRules.passesNeutralCapitalGate(player, territoryManager, configManager, messageManager)) {
                return true;
            }
            if (!FactionSwitchRules.passesSeasonRules(player, factionManager, messageManager)) {
                return true;
            }
            if (!FactionSwitchRules.chargeSwitch(player, currentFaction, factionManager, currencyManager, messageManager)) {
                return true;
            }
        }

        final boolean leavingDark = currentFaction == FactionType.DARK;
        // EXPLICIT Semleges, nem a bejegyzés törlése: a törölt hozzárendelést a következő
        // /faction join „első választásnak" látta, ezért a leave+join páros megkerülte a
        // semleges-főváros kaput, a szezon-hajrá zárát és a váltás-cooldownt. „Nincs
        // bejegyzés" mostantól csak a valóban új játékos állapota.
        factionManager.setFaction(player.getUniqueId(), FactionType.NEUTRAL);
        final hu.taliann.icesmp.managers.SpecializationManager specs = this.specializationManager;
        if (leavingDark && specs != null) {
            if (specs.profileV2Enabled()) {
                specs.reconcileDarkGates(player).whenComplete((result, failure) ->
                        player.getScheduler().run(plugin, task -> {
                            if (failure == null && result != null && result.committed()) {
                                player.sendMessage(messageManager.get("messages.dark-spec-sealed",
                                        "&5A sötét specializációd lezárult, de minden fejlődése megmaradt."));
                            }
                        }, null));
            } else if (specs.resetDarkGatedSpecialization(player)) {
                player.sendMessage(messageManager.get("messages.dark-spec-lost",
                        "&5A Kitaszítottakat elhagyva a sötét utad is lezárult — a specializációd elveszett."));
            }
        }
        sender.sendMessage(messageManager.get("messages.faction-left", "&eKiléptél a frakciódból."));
        return true;
    }
}

