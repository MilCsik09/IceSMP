package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class FactionJoinSubcommand implements FactionSubcommand {

    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final CurrencyManager currencyManager;
    private final TerritoryManager territoryManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public FactionJoinSubcommand(final FactionManager factionManager, final SinManager sinManager,
                                 final CurrencyManager currencyManager, final TerritoryManager territoryManager,
                                 final ConfigManager configManager, final MessageManager messageManager) {
        this.factionManager = factionManager;
        this.sinManager = sinManager;
        this.currencyManager = currencyManager;
        this.territoryManager = territoryManager;
        this.configManager = configManager;
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

        // Frakciót VÁLTANI (ha már választottál) csak a semleges fővárosban lehet —
        // gyakorlatban a semleges királyság spawn-területén, a leendő frakció-NPC-nél.
        // Fail-open: ha még nincs kijelölt semleges főváros, a kapu nem zár.
        if (hasFaction && !FactionSwitchRules.passesNeutralCapitalGate(player, territoryManager, configManager, messageManager)) {
            return true;
        }

        // Első csatlakozás mindig ingyenes és időzítetlen. Frakcióváltás fizetős és
        // cooldownhoz kötött, KIVÉVE: a Semlegesből (alapértelmezett kezdő frakció)
        // bárhová ingyen léphetsz, és a Sötétbe lépés is ingyenes (annak a sinner-
        // feltétel + az örök paktum az ára).
        final boolean isSwitch = hasFaction
                && currentFaction != FactionType.NEUTRAL
                && factionType != FactionType.DARK;

        if (factionType == FactionType.DARK) {
            if (!sinManager.isSinner(player)) {
                sender.sendMessage(messageManager.get(
                        "messages.faction-dark-sinners-only",
                        "&5A Sötét frakcióba csak bűnösök léphetnek be."
                ));
                return true;
            }

            if (isSwitch && !FactionSwitchRules.chargeSwitch(player, currentFaction, factionManager, currencyManager, messageManager)) {
                return true;
            }

            factionManager.setFaction(uuid, FactionType.DARK);
            sinManager.sealDarkPact(player);
            sender.sendMessage(messageManager.get(
                    "messages.faction-dark-pact-sealed",
                    "&5A sötét paktum megköttetett. A bűnöd mostantól örökre veled marad."
            ));
            teleportToFactionSpawn(player, FactionType.DARK);
            return true;
        }

        if (isSwitch && !FactionSwitchRules.chargeSwitch(player, currentFaction, factionManager, currencyManager, messageManager)) {
            return true;
        }

        factionManager.setFaction(uuid, factionType);
        sender.sendMessage(messageManager.get("messages.faction-set-self-success", "&aFrakció beállítva: &f%s", factionType.getDisplayName()));
        teleportToFactionSpawn(player, factionType);
        return true;
    }

    /**
     * Welcomes the player into the chosen kingdom by teleporting them to its admin-set spawn
     * (config: factions.spawn.teleport-on-join; no-op while the spawn is unset). Runs after the
     * join succeeded, on the player's own region thread — teleportAsync is Folia-safe.
     */
    private void teleportToFactionSpawn(final Player player, final FactionType factionType) {
        if (!configManager.getBoolean("factions.spawn.teleport-on-join", true)) {
            return;
        }
        final Location spawn = territoryManager.getFactionSpawn(factionType);
        if (spawn == null) {
            return;
        }
        player.teleportAsync(spawn).thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                player.sendMessage(messageManager.get(
                        "messages.faction-spawn-welcome",
                        "&aÜdvözöl a(z) &f%s&a! A királyság spawnjára kerültél.",
                        factionType.getDisplayName()));
            }
        });
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        // Két hosszal: 0 = "/faction join " (üres prefix), 1 = gépelés közben (args[0] prefix).
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("red", "blue", "neutral", "dark").stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}



