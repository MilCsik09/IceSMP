package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * /bounty — the wanted list: shows every online player whose sin count reaches
 * the bounty threshold, sorted by bounty, so hunters know whom to chase.
 * Killing a wanted criminal pays the bounty and clears their record (handled
 * in the SinListener); this command is purely the discovery surface.
 */
public final class BountyCommand implements BasicCommand {

    private final MetelytepoManager metelytepoManager;
    private final CurrencyManager currencyManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;

    public BountyCommand(final MetelytepoManager metelytepoManager, final CurrencyManager currencyManager,
                         final ConfigManager configManager, final MessageManager messageManager) {
        this.metelytepoManager = metelytepoManager;
        this.currencyManager = currencyManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (!configManager.getBoolean("factions.sins.bounty.enabled", true)) {
            sender.sendMessage(messageManager.get("bounty-disabled", "&7A fejvadász-rendszer ki van kapcsolva."));
            return;
        }

        final int minSins = Math.max(1, configManager.getInt("factions.sins.bounty.min-sins", 3));
        final double perSin = Math.max(0.0D, configManager.getDouble("factions.sins.bounty.reward-per-sin", 25.0D));
        final CurrencyType currency = resolveBountyCurrency();

        final List<Player> wanted = new ArrayList<>();
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (metelytepoManager.getSinCount(online) >= minSins) {
                wanted.add(online);
            }
        }
        wanted.sort(Comparator.comparingInt(metelytepoManager::getSinCount).reversed());

        if (wanted.isEmpty()) {
            sender.sendMessage(messageManager.get("bounty-none", "&7Jelenleg nincs körözött játékos a szerveren."));
            return;
        }

        sender.sendMessage(messageManager.get("bounty-header", "&6💰 Körözési lista (fejpénzek):"));
        for (final Player target : wanted) {
            final int sins = metelytepoManager.getSinCount(target);
            sender.sendMessage(messageManager.get(
                    "bounty-line",
                    "&e%s &7- bűnök: &f%s &7| fejpénz: &f%s %s",
                    target.getName(), sins,
                    currencyManager.formatBalance(sins * perSin),
                    currency == null ? "?" : currency.getDisplayName()
            ));
        }
    }

    private CurrencyType resolveBountyCurrency() {
        final CurrencyType configured = CurrencyType.fromInput(
                configManager.getString("factions.sins.bounty.currency", "NEUTRAL"));
        return configured == null ? CurrencyType.fromFactionType(FactionType.NEUTRAL) : configured;
    }

    @Override
    public @NonNull java.util.Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack,
                                                          final @NonNull String[] args) {
        return List.of();
    }
}
