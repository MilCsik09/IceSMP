package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.listeners.AbilityCatalystListener;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

/**
 * /spellbook — opens the spellbook GUI: browse class + spec spells, see each one's
 * description/cost/cooldown, and click an unlocked spell to select it on the catalyst.
 * (Sneak + right-click on the catalyst opens the same book.)
 */
public final class SpellbookCommand implements BasicCommand {

    private final AbilityCatalystListener catalyst;
    private final MessageManager messageManager;

    public SpellbookCommand(final AbilityCatalystListener catalyst, final MessageManager messageManager) {
        this.catalyst = catalyst;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        catalyst.openSpellbook(player);
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        return List.of();
    }
}
