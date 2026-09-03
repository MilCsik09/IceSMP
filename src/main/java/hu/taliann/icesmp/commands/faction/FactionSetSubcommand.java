package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class FactionSetSubcommand implements FactionSubcommand {

    private static final String PERMISSION = hu.taliann.icesmp.core.Permissions.FACTION;

    private final JavaPlugin plugin;
    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final MessageManager messageManager;
    private volatile hu.taliann.icesmp.managers.SpecializationManager specializationManager;

    public void setSpecializationManager(
            final hu.taliann.icesmp.managers.SpecializationManager specializationManager) {
        this.specializationManager = specializationManager;
    }

    public FactionSetSubcommand(final JavaPlugin plugin, final FactionManager factionManager, final SinManager sinManager,
                                final MessageManager messageManager) {
        this.plugin = plugin;
        this.factionManager = factionManager;
        this.sinManager = sinManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "set";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-set", "Játékos frakciójának beállítása (admin).");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-set", "/faction set <player> <faction>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("messages.faction-set-usage", "&cHasználat: %s", usage()));
            return true;
        }

        // Never Bukkit.getOfflinePlayer(name): for unknown names it fires a BLOCKING Mojang
        // lookup on the region thread. Online-exact first, then the local profile cache.
        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
        }
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.faction-set-player-unknown",
                    "&cIsmeretlen játékos (még sosem járt a szerveren): &f%s", args[0]));
            return true;
        }
        final FactionType factionType = FactionType.fromInput(args[1]);

        if (factionType == null) {
            sender.sendMessage(messageManager.get("messages.faction-unknown", "&cIsmeretlen frakció: &f%s", args[1]));
            return true;
        }

        final FactionType previous = factionManager.getChosenFaction(target.getUniqueId()).orElse(null);
        final UUID targetId = target.getUniqueId();
        final String targetName = target.getName() == null ? targetId.toString() : target.getName();
        final Player onlineTarget = target.getPlayer();
        if (onlineTarget == null && (previous == FactionType.DARK || factionType == FactionType.DARK)) {
            sender.sendMessage(messageManager.get("messages.faction-set-dark-online-required",
                    "&cDARK tagságot csak online játékosnál módosíthatsz, mert a paktum és a specializáció PDC-állapotát ugyanabban az atomi átmenetben kell egyeztetni."));
            return true;
        }

        if (onlineTarget != null && (previous == FactionType.DARK || factionType == FactionType.DARK)) {
            final String retiredMessage = messageManager.get(
                    "messages.faction-set-target-retired",
                    "&cA frakcióváltás meghiúsult, mert a céljátékos közben lecsatlakozott: &f%s",
                    targetName);
            final io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled =
                    onlineTarget.getScheduler().run(plugin, task -> {
                        final FactionType livePrevious = factionManager.getChosenFaction(targetId).orElse(null);
                        // A DARK membership must never commit without its prerequisites. Sealing
                        // them first is safe because Exile and Oath may exist without membership.
                        if (factionType == FactionType.DARK) {
                            sinManager.sealDarkForFactionOverride(onlineTarget);
                        }
                        factionManager.setFaction(targetId, factionType);
                        if (livePrevious == FactionType.DARK && factionType != FactionType.DARK) {
                            sinManager.clearDarkPactForFactionOverride(onlineTarget);
                        }
                        final hu.taliann.icesmp.managers.SpecializationManager specs = specializationManager;
                        if (specs != null) {
                            specs.reconcileDarkGates(onlineTarget);
                        }
                        sendSuccess(sender, targetName, factionType);
                    }, () -> sendSafely(sender, retiredMessage));
            if (scheduled == null) {
                sendSafely(sender, retiredMessage);
            }
            return true;
        } else {
            factionManager.setFaction(targetId, factionType);
        }

        sendSuccess(sender, targetName, factionType);
        return true;
    }

    private void sendSuccess(final CommandSender sender, final String targetName,
                             final FactionType factionType) {
        sendSafely(sender, messageManager.get(
                "messages.faction-set-target-success",
                "&aFrakció beállítva: &f%s &7-> &f%s",
                targetName,
                factionType.getDisplayName()
        ));
    }

    private void sendSafely(final CommandSender sender, final String message) {
        if (sender instanceof Player player) {
            player.getScheduler().run(plugin, task -> player.sendMessage(message), null);
        } else {
            sender.sendMessage(message);
        }
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("red", "blue", "neutral", "dark").stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
