package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

/**
 * /afk — önkéntes AFK-jelölés. Azonnal él a tablist ⌚-jelölés, a rangon belüli
 * hátrasorolás és a jutalomkapu; bármilyen aktivitás (mozgás/chat/parancs) törli.
 * (A parancs-kiadás maga is aktivitás-eseményt vált ki, de az ELŐBB fut le, mint a toggle,
 * így a bekapcsolás megmarad.)
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
            commandSourceStack.getSender().sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }
        final boolean nowAfk = afkManager.toggleManualAfk(player.getUniqueId());
        player.sendMessage(nowAfk
                ? messageManager.get("afk-on", "&7⌚ Mostantól &fAFK&7 vagy — bármilyen mozgás/üzenet visszahoz.")
                : messageManager.get("afk-off", "&aÜdv újra! Az AFK-jelölésed törölve."));
    }
}
