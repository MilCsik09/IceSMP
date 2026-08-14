package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.gui.BestiaryGUI;
import hu.taliann.icesmp.managers.BestiaryManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

/** B21 — /bestiarium: a krónikás-lajstrom lapozható, csak olvasható GUI-jának belépője. */
public final class BestiaryCommand implements BasicCommand {

    private final BestiaryManager bestiaryManager;
    private final ProfessionRecipeCatalog recipeCatalog;
    private final TerritoryManager territoryManager;
    private final MessageManager messageManager;

    public BestiaryCommand(final BestiaryManager bestiaryManager,
                           final ProfessionRecipeCatalog recipeCatalog,
                           final TerritoryManager territoryManager,
                           final MessageManager messageManager) {
        this.bestiaryManager = bestiaryManager;
        this.recipeCatalog = recipeCatalog;
        this.territoryManager = territoryManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (!(commandSourceStack.getSender() instanceof Player player)) {
            commandSourceStack.getSender().sendMessage(messageManager.get(
                    "messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }
        if (!bestiaryManager.isEnabled()) {
            player.sendMessage(messageManager.get("bestiary-disabled", "&cA bestiárium jelenleg nem elérhető."));
            return;
        }
        BestiaryGUI.openMain(player, bestiaryManager, recipeCatalog, territoryManager);
    }

    @Override
    public java.util.@NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack,
                                                         final @NonNull String[] args) {
        return List.of();
    }
}
