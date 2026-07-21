package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.SpyManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

/** G14 — /kem <célfrakció>: rövid felderítő-álca (a logika a SpyManagerben). */
public final class SpyCommand implements BasicCommand {

    private final SpyManager spyManager;
    private final MessageManager messageManager;

    public SpyCommand(final SpyManager spyManager, final MessageManager messageManager) {
        this.spyManager = spyManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (!(commandSourceStack.getSender() instanceof Player player)) {
            commandSourceStack.getSender().sendMessage(messageManager.get(
                    "messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }
        final FactionType target = args.length == 0 ? null : FactionType.fromInput(args[0]);
        if (target == null) {
            player.sendMessage(messageManager.get("spy-usage",
                    "&cHasználat: /kem <célfrakció> &7— rövid felderítő-álca (ütés lebuktat)."));
            return;
        }
        final String error = spyManager.start(player, target);
        if (error != null) {
            player.sendMessage(messageManager.get("spy-error." + error, switch (error) {
                case "spy-no-library" -> "&cAz álca-mesterség most nem elérhető (hiányzó LibsDisguises).";
                case "spy-active" -> "&cMár álcában jársz.";
                case "spy-raid" -> "&cRaid alatt nincs álca — a háború nyílt sisakkal megy.";
                case "spy-cooldown" -> "&cAz álca-mester még pihen — nézz vissza később.";
                default -> "&cAz álca most nem vehető fel.";
            }));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack,
                                               final @NonNull String[] args) {
        if (args.length <= 1) {
            return List.of("red", "blue", "neutral", "dark");
        }
        return List.of();
    }
}
