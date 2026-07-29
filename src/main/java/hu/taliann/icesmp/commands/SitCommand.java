package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.core.Permissions;
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

/** Thin command adapter over the shared native sit-only manager. */
public final class SitCommand implements BasicCommand {

    private static final String STAND_UP_ARG = "fel";
    private final SitManager sitManager;
    private final MessageManager messageManager;

    public SitCommand(final SitManager sitManager, final MessageManager messageManager) {
        this.sitManager = sitManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack stack, final @NonNull String[] args) {
        final CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only",
                    "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (!player.hasPermission(Permissions.SIT)) {
            player.sendMessage(messageManager.get("messages.permission-denied",
                    "&cNincs jogosultságod erre a parancsra."));
            return;
        }
        final String argument = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (args.length > 1 || (!argument.isEmpty() && !STAND_UP_ARG.equals(argument))) {
            player.sendMessage(messageManager.get("sit.usage", "&eHasználat: &f/sit [fel]"));
            return;
        }

        final boolean active = sitManager.hasActiveState(player.getUniqueId());
        if (STAND_UP_ARG.equals(argument) || active) {
            if (!active) {
                player.sendMessage(messageManager.get("sit.not-active", "&cJelenleg nem ülsz."));
                return;
            }
            sitManager.resetPlayer(player);
            player.sendMessage(messageManager.get("sit.up", "&b[Ülés] &7Felálltál."));
            return;
        }

        final Block feet = player.getLocation().getBlock();
        final Block support = sitManager.isConfiguredSeatBlock(feet)
                ? feet : feet.getRelative(BlockFace.DOWN);
        final SitManager.SitResult result = sitManager.sit(player, support, SitManager.SitOrigin.COMMAND);
        if (result == SitManager.SitResult.OK) {
            player.sendMessage(messageManager.get("sit.down", "&b[Ülés] &7Leültél."));
        } else {
            sendFailure(player, result);
        }
    }

    private void sendFailure(final Player player, final SitManager.SitResult result) {
        switch (result) {
            case DISABLED -> player.sendMessage(messageManager.get("sit.disabled", "&cAz ülés jelenleg ki van kapcsolva."));
            case ALREADY_SITTING -> player.sendMessage(messageManager.get("sit.already-sitting", "&cMár ülsz."));
            case IN_VEHICLE -> player.sendMessage(messageManager.get("sit.in-vehicle", "&cJárműben nem tudsz leülni."));
            case IN_LIQUID -> player.sendMessage(messageManager.get("sit.in-liquid", "&cFolyadékban nem tudsz leülni."));
            case NOT_ON_GROUND -> player.sendMessage(messageManager.get("sit.not-on-ground", "&cCsak szilárd talajon tudsz leülni."));
            case WORLD_DISABLED -> player.sendMessage(messageManager.get("sit.world-disabled", "&cEbben a világban az ülés nincs engedélyezve."));
            case MATERIAL_NOT_ALLOWED -> player.sendMessage(messageManager.get("sit.material-not-allowed", "&cErre a blokkra nem lehet leülni."));
            case TOO_FAR -> player.sendMessage(messageManager.get("sit.too-far", "&cEz az ülőhely túl messze van."));
            case UNSAFE -> player.sendMessage(messageManager.get("sit.unsafe", "&cEz a hely nem biztonságos üléshez."));
            case OCCUPIED -> player.sendMessage(messageManager.get("sit.occupied", "&cEzen a helyen már ül valaki."));
            case FOREIGN_REGION -> player.sendMessage(messageManager.get("sit.foreign-region", "&cAz ülőhely régióváltás alatt áll; próbáld újra."));
            case OBSTRUCTED -> player.sendMessage(messageManager.get("sit.obstructed", "&cItt nem tudsz leülni."));
            case OK -> { }
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack stack,
                                                final @NonNull String[] args) {
        if (args.length != 1 || !(stack.getSender() instanceof Player player)
                || !player.hasPermission(Permissions.SIT)) {
            return List.of();
        }
        final String prefix = args[0].toLowerCase(Locale.ROOT);
        return STAND_UP_ARG.startsWith(prefix) ? List.of(STAND_UP_ARG) : List.of();
    }
}
