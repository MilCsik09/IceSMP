package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

public final class SinnerCommand implements BasicCommand {

    private static final String PERMISSION = "icesmp.admin";

    private final JavaPlugin plugin;
    private final SinManager sinManager;
    private final MessageManager messageManager;

    public SinnerCommand(final JavaPlugin plugin, final SinManager sinManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.sinManager = sinManager;
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
            sender.sendMessage(messageManager.getMessage("sinner.usage", "<yellow>Hasznalat: /sinner <player> <set|clear|add|status></yellow>"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messageManager.getMessage("sinner.target-offline", "<red>A celjatekos nem erheto el online.</red>"));
            return;
        }

        final String action = args[1].toLowerCase();
        if (!List.of("set", "clear", "add", "status").contains(action)) {
            sender.sendMessage(messageManager.getMessage("sinner.invalid-action", "<red>Érvénytelen művelet. Használd: set, clear, add vagy status.</red>"));
            return;
        }

        target.getScheduler().run(plugin, task -> {
            switch (action) {
                case "add" -> {
                    final int newCount = sinManager.addSin(target, 1);
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
                                "sinner", sinManager.isSinner(target) ? "igen" : "nem",
                                "count", String.valueOf(sinManager.getSinCount(target)),
                                "pact", sinManager.hasDarkPact(target) ? "igen" : "nem"
                        )
                ));
                case "set" -> {
                    sinManager.markAsSinner(target);
                    sender.sendMessage(messageManager.getMessage(
                            "sinner.set-success",
                            "<green>A jatekos bunosse lett teve: <white>{player}</white></green>",
                            java.util.Map.of("player", target.getName())
                    ));
                }
                case "clear" -> {
                    if (!sinManager.clearSinner(target)) {
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
                default -> throw new IllegalStateException("Unexpected sinner action: " + action);
            }
        }, null);
    }

    @Override
    public Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
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
