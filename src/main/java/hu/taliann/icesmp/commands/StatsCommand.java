package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.StatsManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /stats — saját vagy más játékos statisztika-profilja: ölések,
 * halálok, K/D, mob-ölések, elsütött spellek, teljesített questek.
 *
 * <p>Másik játékos lekérdezésénél a célt kizárólag UUID/név alapján, a
 * {@link StatsManager} gettereivel érjük el — a célszemély entitását (PDC,
 * inventory, stb.) sosem érintjük, ezért ez bármely szálról biztonságos.
 */
public final class StatsCommand implements BasicCommand {

    private final StatsManager statsManager;
    private final MessageManager messageManager;

    public StatsCommand(final StatsManager statsManager, final MessageManager messageManager) {
        this.statsManager = statsManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        final UUID targetId;
        final String targetName;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
                return;
            }
            targetId = player.getUniqueId();
            targetName = player.getName();
        } else {
            final String queried = args[0];
            final Player online = Bukkit.getPlayerExact(queried);
            if (online != null) {
                targetId = online.getUniqueId();
                targetName = online.getName();
            } else {
                final UUID resolved = statsManager.findPlayerIdByName(queried);
                if (resolved == null) {
                    sender.sendMessage(messageManager.get("stats-unknown-player",
                            "&cIsmeretlen játékos: &f%s", queried));
                    return;
                }
                targetId = resolved;
                targetName = statsManager.getStoredName(resolved, queried);
            }
        }

        sendStats(sender, targetId, targetName);
    }

    private void sendStats(final CommandSender sender, final UUID targetId, final String targetName) {
        final int kills = statsManager.getKills(targetId);
        final int deaths = statsManager.getDeaths(targetId);
        final int mobKills = statsManager.getMobKills(targetId);
        final int spellCasts = statsManager.getSpellCasts(targetId);
        final int questsCompleted = statsManager.getQuestsCompleted(targetId);
        final double killDeathRatio = deaths == 0 ? kills : (double) kills / deaths;

        sender.sendMessage(messageManager.get("stats-header", "&b[Statisztika] &f%s", targetName));
        sender.sendMessage(messageManager.get("stats-kills", "&7Ölések (játékos): &f%s", kills));
        sender.sendMessage(messageManager.get("stats-deaths", "&7Halálok: &f%s", deaths));
        sender.sendMessage(messageManager.get("stats-kd", "&7K/D: &f%s",
                String.format(Locale.ROOT, "%.1f", killDeathRatio)));
        sender.sendMessage(messageManager.get("stats-mob-kills", "&7Mob-ölések: &f%s", mobKills));
        sender.sendMessage(messageManager.get("stats-spell-casts", "&7Elsütött spellek: &f%s", spellCasts));
        sender.sendMessage(messageManager.get("stats-quests-completed", "&7Teljesített questek: &f%s", questsCompleted));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
