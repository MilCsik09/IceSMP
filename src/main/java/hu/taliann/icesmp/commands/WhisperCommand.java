package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.WhisperManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /suttogas — K9 Suttogó-csatorna és tanú-vád.
 * <ul>
 *   <li>{@code /suttogas <üzenet>} — a titkos csatorna: csak Suttogók hallják.</li>
 *   <li>{@code /suttogas vad <játékos>} — pontos, célhoz kötött bizonyíték beváltása;
 *       minden érvényes vád pontosan egy leleplezési fokozatot léptet.</li>
 * </ul>
 * A parancs a hívó saját régió-szálán fut; a megvádolt állapotírása a cél schedulerén.
 */
public final class WhisperCommand implements BasicCommand {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final WhisperManager whisperManager;
    private final MessageManager messageManager;

    public WhisperCommand(final org.bukkit.plugin.java.JavaPlugin plugin,
                          final WhisperManager whisperManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.whisperManager = whisperManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (!(commandSourceStack.getSender() instanceof Player player)) {
            commandSourceStack.getSender().sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }
        if (!whisperManager.isEnabled()) {
            player.sendMessage(messageManager.get("whisper-disabled", "&7A Suttogás most néma."));
            return;
        }
        if (args.length >= 2 && isAccuseKeyword(args[0])) {
            accuse(player, args[1]);
            return;
        }
        if (args.length == 0) {
            player.sendMessage(messageManager.get("whisper-usage",
                    "&7/suttogas <üzenet> &8— titkos csatorna &7| /suttogas vád <játékos> &8— tanú-vád"));
            return;
        }
        if (!whisperManager.canHearWhispers(player)) {
            // Kívülállónak a csatorna nem létezik — a válasz szándékosan semmitmondó.
            player.sendMessage(messageManager.get("whisper-not-heard", "&8…csak a szél zúg."));
            return;
        }
        whisperManager.deliverWhisper(player, String.join(" ", args));
    }

    /**
     * A vád-alparancs elfogadott alakjai. Az ékezetes „vád" a KIÍRT forma (ékezet nélkül a szó
     * magyarul mást jelent), de az ékezet nélküli és az angol alak is működik, hogy ne kelljen
     * ékezetet gépelni chat-parancsban.
     */
    private static boolean isAccuseKeyword(final String argument) {
        return "vád".equalsIgnoreCase(argument)
                || "vad".equalsIgnoreCase(argument)
                || "accuse".equalsIgnoreCase(argument);
    }

    private void accuse(final Player accuser, final String targetName) {
        final Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            // A shippelt messages/profession.yml-ben ez a kulcs %s-t tartalmaz — argumentum
            // nélkül a formázás kimarad, és a játékos a nyers %s-t látná.
            accuser.sendMessage(messageManager.get("player-not-found",
                    "&cNincs ilyen online játékos: &f%s", targetName));
            return;
        }
        if (!whisperManager.consumeEvidence(accuser.getUniqueId(), target.getUniqueId())) {
            accuser.sendMessage(messageManager.get("whisper-no-token",
                    "&cEhhez a játékoshoz nincs friss, pontos szemtanú-bizonyítékod."));
            return;
        }
        // A megvádolt MÁSIK entitás — az állapotírás a saját régió-szálán (Folia).
        target.getScheduler().run(plugin, task -> whisperManager.recordAccusation(target), null);
        // A vádló sosem tudja meg, talált-e — a nyomozás bizonytalansága a játék része.
        accuser.sendMessage(messageManager.get("whisper-accused",
                "&7A vádad elhangzott a Számvevők előtt. Hogy igaz volt-e… az idő megmutatja."));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            return List.of("vád");
        }
        if (args.length == 2 && isAccuseKeyword(args[0])) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .map(String::valueOf).toList();
        }
        return List.of();
    }
}
