package hu.taliann.icesmp.commands.job;

import hu.taliann.icesmp.items.SpellbookItemFactory;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.List;

public final class JobGiveSpellbookSubcommand implements JobSubcommand {

    private static final String PERMISSION = "icesmp.job.admin";

    private final SpellbookItemFactory spellbookItemFactory;
    private final MessageManager messageManager;

    public JobGiveSpellbookSubcommand(final SpellbookItemFactory spellbookItemFactory, final MessageManager messageManager) {
        this.spellbookItemFactory = spellbookItemFactory;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "givespellbook";
    }

    @Override
    public String description() {
        return messageManager.get("messages.job-desc-givespellbook", "Varazskonyv adasa egy jatekosnak (admin).");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.job-usage-givespellbook", "/job givespellbook <player>");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messageManager.get("messages.job-givespellbook-usage", "&cHasznalat: %s", usage()));
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messageManager.get("messages.target-player-offline", "&cA celjatekos nem erheto el online."));
            return true;
        }

        final ItemStack spellbook = spellbookItemFactory.createSpellbook();
        final Map<Integer, ItemStack> leftover = target.getInventory().addItem(spellbook);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        }

        sender.sendMessage(messageManager.get(
                "messages.job-givespellbook-success",
                "&aSpellbook atadva: &f%s",
                target.getName()
        ));
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

