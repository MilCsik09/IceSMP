package hu.taliann.icesmp.commands.job;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public final class JobGiveCatalystSubcommand implements JobSubcommand {

    private static final String PERMISSION = hu.taliann.icesmp.core.Permissions.JOB;

    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final CatalystItemFactory catalystItemFactory;
    private final MessageManager messageManager;

    public JobGiveCatalystSubcommand(final JavaPlugin plugin, final JobManager jobManager,
                                     final CatalystItemFactory catalystItemFactory,
                                     final MessageManager messageManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.catalystItemFactory = catalystItemFactory;
        this.messageManager = messageManager;
    }

    @Override
    public String name() { return "givecatalyst"; }

    @Override
    public String description() {
        return messageManager.get("messages.job-desc-givecatalyst",
                "Lélekkapocs helyreállítása egy játékosnak (admin).");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.job-usage-givecatalyst",
                "/job givecatalyst <player>");
    }

    @Override
    public String permission() { return PERMISSION; }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied",
                    "&cNincs jogod ehhez a parancshoz."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(messageManager.get("messages.job-givecatalyst-usage",
                    "&cHasználat: %s", usage()));
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.target-player-offline",
                    "&cA céljátékos nem érhető el online."));
            return true;
        }

        target.getScheduler().run(plugin, task -> {
            final JobType primaryJob = jobManager.getPrimaryJob(target);
            if (primaryJob == null) {
                tell(sender, messageManager.getComponent(
                        "messages.job-givecatalyst-no-class",
                        "&cA célpontnak nincs elsődleges kasztja, így nincs Lélekkapcsa sem."));
                return;
            }
            for (final ItemStack stack : target.getInventory().getContents()) {
                if (catalystItemFactory.isPersonalCopyFor(
                        stack, target.getUniqueId(), primaryJob)) {
                    tell(sender, messageManager.getComponent(
                            "job-givecatalyst-already-owned",
                            "&eA célpont személyes Lélekkapcsa már jelen van."));
                    return;
                }
            }
            if (target.getInventory().firstEmpty() < 0) {
                tell(sender, messageManager.getComponent(
                        "soulbond.inventory-full-admin",
                        "&cA célpont inventoryja tele van; a Lélekkapocs nem került a földre."));
                return;
            }
            target.getInventory().addItem(
                    catalystItemFactory.createCatalyst(primaryJob, target.getUniqueId()));
            tell(sender, messageManager.getMessage(
                    "job-givecatalyst-success",
                    "&aLélekkapocs helyreállítva: &e{catalyst} &7-> &f{player}",
                    Map.of("catalyst", catalystItemFactory.getDisplayNamePlain(primaryJob),
                            "player", target.getName())));
        }, null);
        return true;
    }

    private void tell(final CommandSender sender, final Component message) {
        if (sender instanceof Player player) {
            player.getScheduler().run(plugin, task -> player.sendMessage(message), null);
        } else {
            sender.sendMessage(message);
        }
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
