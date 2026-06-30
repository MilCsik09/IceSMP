package hu.taliann.icesmp.commands.job;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public final class JobGiveCatalystSubcommand implements JobSubcommand {

    private static final String PERMISSION = "icesmp.job.admin";

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
    public String name() {
        return "givecatalyst";
    }

    @Override
    public String description() {
        return messageManager.get("messages.job-desc-givecatalyst", "Képesség Katalizátor adása egy játékosnak (admin).");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.job-usage-givecatalyst", "/job givecatalyst <player>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageManager.get("messages.job-givecatalyst-usage", "&cHasználat: %s", usage()));
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cA céljátékos nem érhető el online."));
            return true;
        }

        // Folia: the target may be in a different region than the admin running the command,
        // so all target reads/mutations (PDC job lookup, inventory, world drop) run on the
        // target's own region thread. sender.sendMessage is safe from there.
        target.getScheduler().run(plugin, task -> {
            final JobType primaryJob = jobManager.getPrimaryJob(target);
            if (primaryJob == null) {
                sender.sendMessage(messageManager.get(
                        "messages.job-givecatalyst-no-class",
                        "&cA célpontnak nincs elsődleges kasztja, így nincs katalizátora sem."
                ));
                return;
            }

            final ItemStack catalyst = catalystItemFactory.createCatalyst(primaryJob);
            final Map<Integer, ItemStack> leftover = target.getInventory().addItem(catalyst);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
            }

            sender.sendMessage(messageManager.getMessage(
                    "job-givecatalyst-success",
                    "&aKatalizátor átadva: &e{catalyst} &7-> &f{player}",
                    Map.of(
                            "catalyst", catalystItemFactory.getDisplayNamePlain(primaryJob),
                            "player", target.getName()
                    )
            ));
        }, null);
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        return List.of();
    }
}
