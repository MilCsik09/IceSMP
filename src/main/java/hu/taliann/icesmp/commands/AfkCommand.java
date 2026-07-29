package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

/**
 * {@code /afk} toggles the player's overall AFK state. Invoking it while automatically or
 * manually AFK returns the player to active state and starts a fresh inactivity window.
 */
public final class AfkCommand implements BasicCommand {

    private final AfkManager afkManager;
    private final MessageManager messageManager;

    public AfkCommand(final AfkManager afkManager, final MessageManager messageManager) {
        this.afkManager = afkManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (!(commandSourceStack.getSender() instanceof Player player)) {
            commandSourceStack.getSender().sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékos használhatja."));
            return;
        }
        final boolean nowAfk = afkManager.toggleAfk(player.getUniqueId());
        player.sendMessage(nowAfk
                ? messageManager.get("afk-on",
                        "&7⌚ Mostantól &fAFK&7 vagy — aktivitás vagy egy újabb /afk visszahoz.")
                : messageManager.get("afk-off", "&aÜdv újra! Az AFK-jelölésed törölve."));
    }
}
