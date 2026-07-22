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
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> hu.taliann.icesmp.gui.PetGUI.open(player, petManager, messageManager);
            case "stance" -> {
                if (!petManager.canOwnPet(player)) {
                    player.sendMessage(messageManager.get("pet-not-allowed", "&cCsak Vadmester, Nekromanta, Szentségtelen vagy Boszorkánymester tarthat társat."));
                    return;
                }
                final hu.taliann.icesmp.managers.MinionManager.Stance stance = args.length < 2 ? null
                        : switch (args[1].toLowerCase(Locale.ROOT)) {
                            case "aktiv", "active" -> hu.taliann.icesmp.managers.MinionManager.Stance.ACTIVE;
                            case "passziv", "passive" -> hu.taliann.icesmp.managers.MinionManager.Stance.PASSIVE;
                            case "marad", "stay" -> hu.taliann.icesmp.managers.MinionManager.Stance.STAY;
                            default -> null;
                        };
                if (stance == null) {
                    player.sendMessage(messageManager.get("pet-stance-usage", "&cHasználat: /pet stance <aktiv|passziv|marad>"));
                    return;
                }
                petManager.setStance(player, stance);
                player.sendMessage(messageManager.getMessage(
                        "pet-stance",
                        "<gray>Társ parancs: <gold>{stance}</gold></gray>",
                        Map.of("stance", switch (stance) {
                            case ACTIVE -> "Támadás";
                            case PASSIVE -> "Passzív";
                            case STAY -> "Maradj";
                        })));
            }
            case "item" -> {
                if (!petManager.canOwnPet(player)) {
                    player.sendMessage(messageManager.get("pet-not-allowed", "&cCsak Vadmester, Nekromanta, Szentségtelen vagy Boszorkánymester tarthat társat."));
                    return;
                }
                if (petManager.isUnholy(player) || petManager.isWarlock(player)) {
                    // A Szentségtelen/Boszorkánymester társa nem befogható: rituálé idézi.
                    player.sendMessage(messageManager.get("pet-ritual-hint",
                            "&5A társadat rituálé idézi, nem befogás. Kellék: &fNyughatatlan Szív&5 "
                                    + "(élőholt-kill) / &fDémon-pecsét&5 (boszorka- és illager-kill) — "
                                    + "éjjel, jobb katt a kellékkel a kezedben."));
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
            return List.of("menu", "item", "summon", "dismiss", "name", "stance", "info").stream().filter(o -> o.startsWith(prefix)).toList();
        }
        if (args.length == 2 && "stance".equalsIgnoreCase(args[0])) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("aktiv", "passziv", "marad").stream().filter(o -> o.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
