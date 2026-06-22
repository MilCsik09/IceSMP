package hu.taliann.icesmp.commands;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Shared contract for the registry-style command dispatchers (currency, faction, job).
 * The per-area marker interfaces ({@code CurrencySubcommand}, {@code FactionSubcommand},
 * {@code JobSubcommand}) extend this so the (previously triplicated) method set lives in
 * exactly one place; existing implementations and command maps keep their area type.
 */
public interface Subcommand {

    String name();

    String description();

    String usage();

    boolean execute(CommandSender sender, String[] args);

    default List<String> tabComplete(final CommandSender sender, final String[] args) {
        return List.of();
    }
}
