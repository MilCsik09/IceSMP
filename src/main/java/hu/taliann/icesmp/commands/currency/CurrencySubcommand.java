package hu.taliann.icesmp.commands.currency;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface CurrencySubcommand {

    String name();

    String description();

    String usage();

    boolean execute(CommandSender sender, String[] args);

    default List<String> tabComplete(final CommandSender sender, final String[] args) {
        return List.of();
    }
}


