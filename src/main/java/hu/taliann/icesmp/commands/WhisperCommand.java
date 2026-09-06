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
        if (args.length == 1 && List.of("állapot", "allapot", "status").contains(args[0].toLowerCase(Locale.ROOT))) {
            try {
                final long remaining = whisperManager.returnRemainingMillis(player);
                player.sendMessage(messageManager.get("whisper-status", "&7Suttogó állapot: &f%s &7| Új rítus várakozása: &f%s mp",
                        whisperManager.getStage(player).displayName(), String.valueOf((remaining + 999L) / 1000L)));
            } catch (final RuntimeException unavailable) {
                player.sendMessage(messageManager.get("whisper-profile-unavailable", "&cA titkos profil most nem érhető el. Próbáld újra."));
            }
            return;
        }
        if (args.length == 1 && List.of("megbízás", "megbizas", "mission").contains(args[0].toLowerCase(Locale.ROOT))) {
            if (whisperManager.isWhisperer(player)) player.sendMessage(messageManager.get("whisper-mission",
                    "&5Titkos megbízás: aktív kultista rítusnál vagy hírvivőnél adj át egy közönséges ametisztszilánkot (főkéz, SHIFT + jobb katt). Sikeres esemény: egy fokozat fedezék és zsákmány. A szemtanúk pontos bizonyítékot kapnak. Eseményenként egy átadás számít."));
            else player.sendMessage(messageManager.get("whisper-not-heard", "&8…csak a szél zúg."));
            return;
        }
        if (args.length == 0) {
            player.sendMessage(messageManager.get("whisper-usage",
                    "&7/suttogas <üzenet> &8— titkos csatorna &7| /suttogas vád <játékos> &8— tanú-vád &7| /suttogas állapot"));
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
        whisperManager.recordAccusation(accuser.getUniqueId(), target.getUniqueId())
                .whenComplete((accepted, failure) -> accuser.getScheduler().run(plugin, task -> {
                    if (failure != null) {
                        accuser.sendMessage(messageManager.get("whisper-accusation-failed",
                                "&cNem kaptunk sikeres mentési visszaigazolást. A vád biztonságosan újrapróbálható."));
                    } else if (!Boolean.TRUE.equals(accepted)) {
                        accuser.sendMessage(messageManager.get("whisper-no-token",
                                "&cEhhez a játékoshoz nincs friss, pontos szemtanú-bizonyítékod."));
                    } else accuser.sendMessage(messageManager.get("whisper-accused",
                            "&7A vádad elhangzott a Számvevők előtt. Hogy igaz volt-e… az idő megmutatja."));
                }, null));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            return List.of("vád", "állapot", "megbízás");
        }
        if (args.length == 2 && isAccuseKeyword(args[0])) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .map(String::valueOf).toList();
        }
        return List.of();
    }
}
