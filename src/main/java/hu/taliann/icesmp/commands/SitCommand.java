package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.SitManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /sit — leül a lába alatti blokkra, vagy ha már ül, feláll. {@code /sit fel} explicit felállás.
 * A tényleges ülés-logika a {@link SitManager}-ben van; a parancs a saját szálán fut, tehát a
 * manager-hívások itt régió-lokálisak (lásd SitManager Folia-jegyzete).
 */
public final class SitCommand implements BasicCommand {

    private static final String STAND_UP_ARG = "fel";

    private final SitManager sitManager;
    private final MessageManager messageManager;

    public SitCommand(final SitManager sitManager, final MessageManager messageManager) {
        this.sitManager = sitManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final boolean sitting = sitManager.isSitting(player.getUniqueId());
        final boolean explicitStandUp = args.length > 0 && STAND_UP_ARG.equalsIgnoreCase(args[0]);

        if (explicitStandUp || sitting) {
            if (!sitting) {
                player.sendMessage(messageManager.get("sit-not-sitting", "&cJelenleg nem ülsz."));
                return;
            }
            sitManager.standUp(player);
            player.sendMessage(messageManager.get("sit-up", "&b[Ülés] &7Felálltál."));
            return;
        }

        final Block below = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        final SitManager.SitResult result = sitManager.sit(player, sitManager.computeSeatLocation(below));

        switch (result) {
            case OK -> player.sendMessage(messageManager.get("sit-down", "&b[Ülés] &7Leültél."));
            case ALREADY_SITTING -> player.sendMessage(messageManager.get("sit-already-sitting", "&cMár ülsz."));
            case IN_VEHICLE -> player.sendMessage(messageManager.get("sit-in-vehicle", "&cJárműben ülve nem tudsz leülni."));
            case IN_LIQUID -> player.sendMessage(messageManager.get("sit-in-liquid", "&cVízben nem tudsz leülni."));
            case NOT_ON_GROUND -> player.sendMessage(messageManager.get("sit-not-on-ground", "&cCsak szilárd talajon tudsz leülni."));
            case OBSTRUCTED -> player.sendMessage(messageManager.get("sit-obstructed", "&cItt nem tudsz leülni."));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length == 1) {
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            return STAND_UP_ARG.startsWith(prefix) ? List.of(STAND_UP_ARG) : List.of();
        }
        return List.of();
    }
}
