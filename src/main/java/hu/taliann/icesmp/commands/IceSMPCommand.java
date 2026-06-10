package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

public final class IceSMPCommand implements BasicCommand {

    private static final String PERMISSION = "icesmp.admin.reload";

    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public IceSMPCommand(final ConfigManager configManager, final MessageManager messageManager) {
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultsagod erre a parancsra."));
            return;
        }

        if (args.length == 0 || !"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(messageManager.get("admin.icesmp.reload.usage", "&cHasznalat: /icesmp reload"));
            return;
        }

        configManager.reload();
        messageManager.reload();
        sender.sendMessage(messageManager.get("admin.icesmp.reload.success", "<green>Plugin konfiguracio sikeresen ujratoltve!</green>"));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return List.of("reload").stream().filter(option -> option.startsWith(prefix)).toList();
        }

        return List.of();
    }
}

