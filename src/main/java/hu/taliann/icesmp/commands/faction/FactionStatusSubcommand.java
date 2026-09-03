package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.managers.WhisperManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Compact command-only view of faction/crime state and the explicit DARK oath. */
public final class FactionStatusSubcommand implements FactionSubcommand {

    private final FactionManager factionManager;
    private final SinManager sinManager;
    private final WhisperManager whisperManager;
    private final MessageManager messageManager;

    public FactionStatusSubcommand(final FactionManager factionManager,
                                   final SinManager sinManager,
                                   final WhisperManager whisperManager,
                                   final MessageManager messageManager) {
        this.factionManager = factionManager;
        this.sinManager = sinManager;
        this.whisperManager = whisperManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-status",
                "Frakció- és bűnállapot megtekintése; DARK eskü.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-status", "/faction status [eskü]");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékos használhatja."));
            return true;
        }
        if (args.length > 0 && isOath(args[0])) {
            return swear(player);
        }
        final FactionType faction = factionManager.getChosenFaction(
                player.getUniqueId()).orElse(null);
        player.sendMessage(messageManager.get("messages.faction-status",
                "&6Frakció: &f%s &8| &6Infamy: &f%s &8| &6Wanted: &f%s &8| "
                        + "&6Exile: &f%s &8| &6DARK eskü: &f%s &8| &6Suttogás: &f%s",
                faction == null ? "nincs" : faction.getDisplayName(),
                sinManager.getInfamy(player), yesNo(sinManager.isWanted(player)),
                yesNo(sinManager.isExiled(player)), yesNo(sinManager.hasOath(player)),
                whisperManager.getStage(player).displayName()));
        return true;
    }

    private boolean swear(final Player player) {
        if (!sinManager.isExiled(player)) {
            player.sendMessage(messageManager.get("messages.faction-oath-not-exiled",
                    "&cDARK esküt csak száműzött játékos tehet."));
            return true;
        }
        if (sinManager.hasOath(player)) {
            player.sendMessage(messageManager.get("messages.faction-oath-already",
                    "&7A DARK esküt már letetted."));
            return true;
        }
        sinManager.sealDarkPact(player);
        player.sendMessage(messageManager.get("messages.faction-oath-sworn",
                "&5Letetted a DARK esküt. A tagságot külön erősítsd meg: &f/faction join dark"));
        return true;
    }

    private static boolean isOath(final String input) {
        final String value = input.toLowerCase(Locale.ROOT);
        return "eskü".equals(value) || "esku".equals(value) || "oath".equals(value);
    }

    private static String yesNo(final boolean value) {
        return value ? "igen" : "nem";
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("eskü").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
