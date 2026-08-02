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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class FactionJoinSubcommand implements FactionSubcommand {

    /**
     * Gameplay-audit (newbie-trap): a DARK-belépés visszafordíthatatlan (örök paktum) —
     * kétlépcsős megerősítés kell. UUID → az első kérés időbélyege; az ablakon túli
     * bejegyzések minden híváskor törlődnek (nem szivárog).
     */
    private final java.util.concurrent.ConcurrentHashMap<UUID, Long> darkConfirmPending =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final FactionManager factionManager;
    private final JavaPlugin plugin;
    private final SinManager sinManager;
    private final CurrencyManager currencyManager;
    private final TerritoryManager territoryManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    /** Setter-injektált: DARK-elhagyáskor a sötét spec elengedéséhez. */
    private volatile hu.taliann.icesmp.managers.SpecializationManager specializationManager;

    public void setSpecializationManager(final hu.taliann.icesmp.managers.SpecializationManager specializationManager) {
        this.specializationManager = specializationManager;
    }

    public FactionJoinSubcommand(final JavaPlugin plugin, final FactionManager factionManager,
                                 final SinManager sinManager,
                                 final CurrencyManager currencyManager, final TerritoryManager territoryManager,
                                 final ConfigManager configManager, final MessageManager messageManager) {
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

        // Az örök paktum nem pénz-kérdés: paktumos Kitaszított nem válthat ki más
        // frakcióba — az egyetlen kiút a vezeklés-lánc (breakDarkPact).
        if (hasFaction && currentFaction == FactionType.DARK && factionType != FactionType.DARK
                && sinManager.hasDarkPact(player)) {
            sender.sendMessage(messageManager.get("messages.faction-dark-pact-locked",
                    "&5A sötét paktum örök — a Kitaszítottak közül nem vezet ki pénz. "
                            + "Az egyetlen út a vezeklés-küldetéslánc."));
            return true;
        }

        // Frakciót VÁLTANI (ha már választottál) csak a semleges fővárosban lehet —
        // gyakorlatban a semleges királyság spawn-területén, a leendő frakció-NPC-nél.
        // Fail-open: ha még nincs kijelölt semleges főváros, a kapu nem zár.
        if (hasFaction && !FactionSwitchRules.passesNeutralCapitalGate(player, territoryManager, configManager, messageManager)) {
            return true;
        }

        // Szezon-szabályok MINDEN váltásra (az ingyenes utakra is): hajrá-zár + szezon-plafon.
        if (hasFaction && !FactionSwitchRules.passesSeasonRules(player, factionManager, messageManager)) {
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
                        "&5A Kitaszítottak közé csak bűnösök léphetnek be."
                ));
                return true;
            }

            // Kétlépcsős megerősítés: az örök paktumot nem lehet "véletlenül" megkötni.
            final long confirmWindowMillis = Math.max(0L,
                    configManager.getLong("factions.dark.join-confirm-seconds", 60L)) * 1000L;
            if (confirmWindowMillis > 0L) {
                final long now = System.currentTimeMillis();
                darkConfirmPending.values().removeIf(stamp -> now - stamp > confirmWindowMillis);
                final Long firstAsk = darkConfirmPending.get(uuid);
                if (firstAsk == null) {
                    darkConfirmPending.put(uuid, now);
                    sender.sendMessage(messageManager.get(
                            "messages.faction-dark-confirm-warning",
                            "&5⚠ A Kitaszítottak paktuma ÖRÖK: a bűnöd sosem tisztul le, és nincs visszaút "
                                    + "más frakcióba. Ha biztos vagy benne, írd be újra &f%s&5 másodpercen belül: "
                                    + "&f/faction join dark",
                            String.valueOf(confirmWindowMillis / 1000L)));
                    return true;
                }
                darkConfirmPending.remove(uuid);
            }

            if (isSwitch && !FactionSwitchRules.chargeSwitch(player, currentFaction, factionManager, currencyManager, messageManager)) {
                return true;
            }

            factionManager.setFaction(uuid, FactionType.DARK);
            if (hasFaction) {
                factionManager.recordSeasonSwitch(player); // ingyenes út is a szezon-plafonba számít
            }
            sinManager.sealDarkPact(player);
            // Ugyanaz a bejegyzés, mint a kényszerű száműzetésnél: a „redeemed" ennek a
            // gyereke, e nélkül az önként belépő vezeklése szülő nélküli csomópontra érkezne.
            hu.taliann.icesmp.managers.AdvancementService.award(player, "exiled");
            sender.sendMessage(messageManager.get(
                    "messages.faction-dark-pact-sealed",
                    "&5A sötét paktum megköttetett. A bűnöd mostantól örökre veled marad."
            ));
            if (specializationManager != null) {
                specializationManager.reconcileDarkGates(player);
            }
            teleportToFactionSpawn(player, FactionType.DARK);
            return true;
        }

        if (isSwitch && !FactionSwitchRules.chargeSwitch(player, currentFaction, factionManager, currencyManager, messageManager)) {
            return true;
        }

        final boolean leavingDark = hasFaction && currentFaction == FactionType.DARK;
        factionManager.setFaction(uuid, factionType);
        if (leavingDark && specializationManager != null) {
            specializationManager.reconcileDarkGates(player).whenComplete((result, failure) ->
                    player.getScheduler().run(plugin, task -> {
                        if (failure == null && result != null && result.committed()) {
                            player.sendMessage(messageManager.get("messages.dark-spec-sealed",
                                    "&5A sötét specializációd lezárult, de minden fejlődése megmaradt."));
                        }
                    }, null));
        }
        if (hasFaction && !isSwitch) {
            factionManager.recordSeasonSwitch(player); // Semlegesből ingyen váltás is számít
        }
        sender.sendMessage(messageManager.get("messages.faction-set-self-success", "&aFrakció beállítva: &f%s",
                factionType.getDisplayName() + " (" + factionType.getFullName() + ")"));
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
                        factionType.getFullName()));
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
