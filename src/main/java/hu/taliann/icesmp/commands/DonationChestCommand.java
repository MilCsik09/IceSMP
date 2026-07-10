package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.gui.DonationChestGUI;
import hu.taliann.icesmp.managers.DonationChestManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /adomany — közösségi adomány-láda: (arg nélkül) böngészés GUI-ban;
 * add — a kézben tartott tárgy (teljes stack) adományozása a közös ládába.
 * Tiszta ajándékozás — nincs ár, nincs valuta.
 */
public final class DonationChestCommand implements BasicCommand {

    private final DonationChestManager donationChestManager;
    private final MessageManager messageManager;

    public DonationChestCommand(final DonationChestManager donationChestManager, final MessageManager messageManager) {
        this.donationChestManager = donationChestManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length == 0) {
            DonationChestGUI.open(player, donationChestManager, messageManager);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> handleAdd(player);
            default -> DonationChestGUI.open(player, donationChestManager, messageManager);
        }
    }

    private void handleAdd(final Player player) {
        final String errorKey = donationChestManager.donateHeldItem(player);
        if (errorKey != null) {
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey),
                    Map.of("limit", String.valueOf(donationChestManager.getMaxPerPlayer()))));
            return;
        }

        player.sendMessage(messageManager.get("donation-add-success",
                "&aA kezedben tartott tárgyat az adomány-ládába tetted — bárki elveheti."));
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "donation-chest-disabled" -> "&cAz adomány-láda jelenleg ki van kapcsolva.";
            case "donation-no-item" -> "&cNincs tárgy a kezedben — ezt adományoznád?";
            case "donation-chest-full" -> "&cAz adomány-láda megtelt — próbáld később, ha valaki elvitt valamit.";
            case "donation-per-player-limit" -> "&cElérted a saját adomány-limitedet ebben a ládában (&f{limit} tétel&c).";
            default -> "&cAz adományozás nem sikerült.";
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("add").stream().filter(option -> option.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
