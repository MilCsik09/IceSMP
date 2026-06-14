package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

public final class SinnerCommand implements BasicCommand {

    private static final String PERMISSION = "icesmp.admin";

    private final MetelytepoManager metelytepoManager;
    private final MessageManager messageManager;

    public SinnerCommand(final MetelytepoManager metelytepoManager, final MessageManager messageManager) {
        this.metelytepoManager = metelytepoManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.getMessage("permission-denied", "<red>Nincs jogod ehhez a parancshoz.</red>"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.getMessage("sinner.usage", "<yellow>Hasznalat: /sinner <player> <set|clear></yellow>"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messageManager.getMessage("sinner.target-offline", "<red>A celjatekos nem erheto el online.</red>"));
            return;
        }

        final String action = args[1].toLowerCase();
        switch (action) {
            case "add" -> {
                final int newCount = metelytepoManager.addSin(target, 1);
                sender.sendMessage(messageManager.getMessage(
                        "sinner.add-success",
                        "<green>Bűn rögzítve: <white>{player}</white> <gray>(bűnök: <white>{count}</white>)</gray></green>",
                        java.util.Map.of("player", target.getName(), "count", String.valueOf(newCount))
                ));
            }
            case "status" -> sender.sendMessage(messageManager.getMessage(
                    "sinner.status",
                    "<gray>{player}: bűnös: <white>{sinner}</white> | bűnök: <white>{count}</white> | sötét paktum: <white>{pact}</white></gray>",
                    java.util.Map.of(
                            "player", target.getName(),
                            "sinner", metelytepoManager.isSinner(target) ? "igen" : "nem",
                            "count", String.valueOf(metelytepoManager.getSinCount(target)),
                            "pact", metelytepoManager.hasDarkPact(target) ? "igen" : "nem"
                    )
            ));
            case "set" -> {
                metelytepoManager.markAsSinner(target);
                sender.sendMessage(messageManager.getMessage(
                        "sinner.set-success",
                        "<green>A jatekos bunosse lett teve: <white>{player}</white></green>",
                        java.util.Map.of("player", target.getName())
                ));
            }
            case "clear" -> {
                if (!metelytepoManager.clearSinner(target)) {
                    sender.sendMessage(messageManager.getMessage(
                            "sinner.clear-blocked-dark-pact",
                            "<dark_purple>A sötét paktum örök: <white>{player}</white> bűne nem tisztítható le.</dark_purple>",
                            java.util.Map.of("player", target.getName())
                    ));
                    return;
                }
                sender.sendMessage(messageManager.getMessage(
                        "sinner.clear-success",
                        "<green>A jatekos megtisztult: <white>{player}</white></green>",
                        java.util.Map.of("player", target.getName())
                ));
            }
            default -> sender.sendMessage(messageManager.getMessage("sinner.invalid-action", "<red>Érvénytelen művelet. Használd: set, clear, add vagy status.</red>"));
        }
    }

    @Override
    public Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length == 0) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }

        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            return java.util.stream.Stream.of("set", "clear", "add", "status")
                    .filter(option -> option.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return List.of();
    }
}

