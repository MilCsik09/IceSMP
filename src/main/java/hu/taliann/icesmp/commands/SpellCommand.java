package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.managers.JobManager;
import hu.taliann.icesmp.managers.SpellMasteryManager;
import hu.taliann.icesmp.managers.SpellRegistry;
import hu.taliann.icesmp.spells.Spell;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /spell — view and upgrade spell mastery. Higher mastery
 * lowers a spell's cooldown; upgrades cost faction currency.
 */
public final class SpellCommand implements BasicCommand {

    private final JobManager jobManager;
    private final SpellRegistry spellRegistry;
    private final SpellMasteryManager masteryManager;
    private final MessageManager messageManager;

    public SpellCommand(final JobManager jobManager, final SpellRegistry spellRegistry,
                        final SpellMasteryManager masteryManager, final MessageManager messageManager) {
        this.jobManager = jobManager;
        this.spellRegistry = spellRegistry;
        this.masteryManager = masteryManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length >= 2 && "upgrade".equalsIgnoreCase(args[0])) {
            handleUpgrade(player, args[1]);
            return;
        }

        handleInfo(player);
    }

    private void handleInfo(final Player player) {
        player.sendMessage(messageManager.get("spell-mastery-header", "&6Spell-mesterség &7(magasabb rang = rövidebb cooldown):"));
        final List<String> unlocked = jobManager.getUnlockedSpellIds(player);
        if (unlocked.isEmpty()) {
            player.sendMessage(messageManager.get("spell-mastery-none", "&7Nincs feloldott képességed."));
            return;
        }
        for (final String spellId : unlocked) {
            final Spell spell = spellRegistry.getById(spellId);
            if (spell == null) {
                continue;
            }
            final int rank = masteryManager.getRank(player, spellId);
            final String cost = rank >= masteryManager.getMaxRank()
                    ? "MAX"
                    : masteryManager.getUpgradeCost(player, spellId) + " valuta";
            player.sendMessage(messageManager.get(
                    "spell-mastery-line",
                    "&e%s &7[★%s/%s] &8| &7köv. rang: &f%s &8(&7/spell upgrade %s&8)",
                    spell.getName(), rank, masteryManager.getMaxRank(), cost, spellId));
        }
    }

    private void handleUpgrade(final Player player, final String rawId) {
        final String spellId = rawId.toLowerCase(Locale.ROOT);
        if (!jobManager.hasUnlockedSpell(player, spellId) || spellRegistry.getById(spellId) == null) {
            player.sendMessage(messageManager.get("spell-mastery-not-unlocked", "&cEzt a képességet nem oldottad fel."));
            return;
        }

        final long cost = masteryManager.getUpgradeCost(player, spellId);
        final SpellMasteryManager.UpgradeResult result = masteryManager.upgrade(player, spellId);
        switch (result) {
            case SUCCESS -> player.sendMessage(messageManager.getMessage(
                    "spell-mastery-upgraded",
                    "&aMesterség fejlesztve: &e{spell} &7(rang &f{rank}&7, ár: &f{cost}&7)",
                    Map.of(
                            "spell", spellRegistry.getById(spellId).getName(),
                            "rank", String.valueOf(masteryManager.getRank(player, spellId)),
                            "cost", String.valueOf(cost)
                    )));
            case MAX_RANK -> player.sendMessage(messageManager.get("spell-mastery-max", "&7Ez a képesség már maximális mesterségű."));
            case INSUFFICIENT_FUNDS -> player.sendMessage(messageManager.get("spell-mastery-poor", "&cNincs elég frakcióvalutád (&f%s&c kellene).", cost));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        final String first = prefixAt(args, 0);
        final boolean firstComplete = "info".equals(first) || "upgrade".equals(first);

        // Két hosszal: 0 = "/spell " (üres prefix), 1 = gépelés közben — kivéve, ha az args[0]
        // már pontos egyezés, akkor a P=1 (spellId) pozíció javaslatai jönnek.
        if (args.length == 0 || (args.length == 1 && !firstComplete)) {
            return List.of("info", "upgrade").stream().filter(option -> option.startsWith(first)).toList();
        }
        if ("upgrade".equals(first) && args.length <= 2 && sender instanceof Player player) {
            final String prefix = prefixAt(args, 1);
            return jobManager.getUnlockedSpellIds(player).stream()
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

}
