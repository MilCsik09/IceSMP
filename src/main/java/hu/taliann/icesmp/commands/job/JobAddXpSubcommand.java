package hu.taliann.icesmp.commands.job;

import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class JobAddXpSubcommand implements JobSubcommand {

    private static final String PERMISSION = "icesmp.job.admin";

    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final MessageManager messageManager;

    public JobAddXpSubcommand(final JavaPlugin plugin, final JobManager jobManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "addxp";
    }

    @Override
    public String description() {
        return messageManager.get("messages.job-desc-addxp", "Admin XP hozzaadas kasztokhoz.");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.job-usage-addxp", "/job addxp <player> <primary|secondary> <amount>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.get("messages.job-addxp-usage", "&cHasznalat: %s", usage()));
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cA celjatekos nem erheto el online."));
            return true;
        }

        final Boolean primarySlot = parseSlot(args[1]);
        if (primarySlot == null) {
            sender.sendMessage(messageManager.get("messages.job-invalid-slot", "&cA slot csak primary vagy secondary lehet."));
            return true;
        }

        final int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("messages.invalid-amount", "&cErvenytelen osszeg."));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(messageManager.get("messages.amount-must-be-positive", "&cAz osszegnek pozitivnak kell lennie."));
            return true;
        }

        // Folia: addXpToJob writes the target's PDC and may message them on level-up, so it must run
        // on the target's own region thread (the target may be in a different region than the admin).
        // sender.sendMessage is safe from there.
        target.getScheduler().run(plugin, task -> {
            if (!jobManager.addXpToJob(target, primarySlot, amount)) {
                sender.sendMessage(messageManager.get("messages.job-slot-not-set", "&cA valasztott kaszt slot nincs beallitva."));
                return;
            }

            final int currentXp = jobManager.getXp(target, primarySlot);
            final int currentLevel = jobManager.getLevel(target, primarySlot);
            sender.sendMessage(messageManager.get(
                    "messages.job-addxp-success",
                    "&aXP hozzaadva: &f%s &7| Slot: &f%s &7| +&f%s XP &7| Uj XP: &f%s &7| Szint: &f%s",
                    target.getName(),
                    primarySlot ? "primary" : "secondary",
                    amount,
                    currentXp,
                    currentLevel
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

        if (args.length == 2) {
            return List.of("primary", "secondary").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return List.of();
    }

    private Boolean parseSlot(final String input) {
        if ("primary".equalsIgnoreCase(input) || "p".equalsIgnoreCase(input)) {
            return true;
        }

        if ("secondary".equalsIgnoreCase(input) || "s".equalsIgnoreCase(input)) {
            return false;
        }

        return null;
    }
}

