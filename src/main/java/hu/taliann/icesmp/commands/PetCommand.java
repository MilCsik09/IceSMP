package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.items.CaptureItemFactory;
import hu.taliann.icesmp.managers.PetManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /pet — companion control for the Beast Master and Necromancer: get the capture
 * item, summon / dismiss / name / info. Capture itself is by right-clicking a mob
 * with the capture item.
 */
public final class PetCommand implements BasicCommand {

    private final PetManager petManager;
    private final CaptureItemFactory captureItemFactory;
    private final MessageManager messageManager;

    public PetCommand(final PetManager petManager, final CaptureItemFactory captureItemFactory,
                      final MessageManager messageManager) {
        this.petManager = petManager;
        this.captureItemFactory = captureItemFactory;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "item" -> {
                if (!petManager.canOwnPet(player)) {
                    player.sendMessage(messageManager.get("pet-not-allowed", "&cCsak Vadmester vagy Nekromanta tarthat társat."));
                    return;
                }
                final ItemStack item = petManager.isNecromancer(player)
                        ? captureItemFactory.createNecroItem(1)
                        : captureItemFactory.createBeastItem(1);
                player.getInventory().addItem(item).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                player.sendMessage(messageManager.get("pet-item-given", "&aMegkaptad a befogó eszközt — jobb katt egy lényen!"));
            }
            case "summon" -> {
                final String error = petManager.summon(player);
                if (error != null) {
                    player.sendMessage(messageManager.get(error, "&cMost nem tudsz társat idézni."));
                } else {
                    player.sendMessage(messageManager.get("pet-summoned", "&aA társad megjelent melletted."));
                }
            }
            case "dismiss" -> player.sendMessage(petManager.dismiss(player)
                    ? messageManager.get("pet-dismissed", "&7A társad eltűnt.")
                    : messageManager.get("pet-none", "&7Nincs aktív társad."));
            case "name" -> {
                if (args.length < 2) {
                    player.sendMessage(messageManager.get("pet-name-usage", "&cHasználat: /pet name <név>"));
                    return;
                }
                player.sendMessage(petManager.setName(player, args[1])
                        ? messageManager.get("pet-named", "&aA társad neve mostantól: &f%s", args[1])
                        : messageManager.get("pet-name-invalid", "&cÉrvénytelen név (max 24 karakter)."));
            }
            default -> player.sendMessage(messageManager.getMessage(
                    "pet-info",
                    "<dark_green>🐺 Társ: <white>{name}</white> &7| Szint: <white>{level}</white> &7| XP: <white>{xp}</white> &8(/pet summon|dismiss|name)</dark_green>",
                    Map.of(
                            "name", petManager.getName(player),
                            "level", String.valueOf(petManager.getLevel(player)),
                            "xp", String.valueOf(petManager.getXp(player))
                    )));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("item", "summon", "dismiss", "name", "info").stream().filter(o -> o.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
