package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.items.BlueprintItemFactory;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.listeners.ProfessionRecipeBookListener;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /iceitem — admin item-adó parancs: a plugin BÁRMELYIK saját tárgya kiadható vele,
 * a rendes játékmeneti úttal bitre azonos formában (ugyanazok a gyárak/stamp-láncok):
 * <ul>
 *   <li><b>unique:</b> profession-materials.yml unique anyagok ({@code UniqueMaterialFactory}
 *       — pl. emlekszilank, suttogas_meghivo, runapor);</li>
 *   <li><b>recept:</b> a recept-katalógus EREDMÉNY-tárgya a teljes stamp-lánccal
 *       (signature-PDC, custom enchant, crafted-by, affix-roll — {@code buildResult});</li>
 *   <li><b>relikvia:</b> RelicManager.giveRelic force-móddal (tulajdon-átírással);</li>
 *   <li><b>tervrajz:</b> a recept tervrajz-itemje ({@code BlueprintItemFactory}).</li>
 * </ul>
 * Használat: {@code /iceitem <unique|recept|relikvia|tervrajz> <id> [darab] [játékos]} —
 * játékos nélkül a kiadó kapja. Jog: {@code icesmp.admin.item}. Folia: másik játékosnak
 * adáskor a CÉL saját régió-schedulerén fut az inventory-írás.
 */
public final class ItemGiveCommand implements BasicCommand {

    public static final String PERMISSION = "icesmp.admin.item";
    private static final List<String> TYPES = List.of("unique", "recept", "relikvia", "tervrajz");

    private final JavaPlugin plugin;
    private final UniqueMaterialFactory uniqueMaterials;
    private final ProfessionRecipeCatalog catalog;
    private final ProfessionRecipeBookListener recipeBookListener;
    private final RelicManager relicManager;
    private final BlueprintItemFactory blueprintFactory;
    private final MessageManager messageManager;

    public ItemGiveCommand(final JavaPlugin plugin, final UniqueMaterialFactory uniqueMaterials,
                           final ProfessionRecipeCatalog catalog,
                           final ProfessionRecipeBookListener recipeBookListener,
                           final RelicManager relicManager, final BlueprintItemFactory blueprintFactory,
                           final MessageManager messageManager) {
        this.plugin = plugin;
        this.uniqueMaterials = uniqueMaterials;
        this.catalog = catalog;
        this.recipeBookListener = recipeBookListener;
        this.relicManager = relicManager;
        this.blueprintFactory = blueprintFactory;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final var sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("no-permission", "&cNincs jogosultságod ehhez."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("admin.iceitem.usage",
                    "&cHasználat: /iceitem <unique|recept|relikvia|tervrajz> <id> [darab] [játékos]"));
            return;
        }
        final String type = args[0].toLowerCase(Locale.ROOT);
        final String id = args[1].toLowerCase(Locale.ROOT);
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(2304, Integer.parseInt(args[2])));
            } catch (final NumberFormatException exception) {
                sender.sendMessage(messageManager.get("admin.iceitem.bad-amount",
                        "&cÉrvénytelen darabszám: &f%s", args[2]));
                return;
            }
        }
        final Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                sender.sendMessage(messageManager.get("admin.iceitem.no-player",
                        "&cNincs ilyen online játékos: &f%s", args[3]));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(messageManager.get("admin.iceitem.console-needs-player",
                    "&cKonzolról add meg a cél-játékost is: /iceitem <típus> <id> <darab> <játékos>"));
            return;
        }

        final int give = amount;
        switch (type) {
            case "unique" -> {
                if (!uniqueMaterials.isDefined(id)) {
                    sender.sendMessage(messageManager.get("admin.iceitem.unknown-id",
                            "&cIsmeretlen azonosító: &f%s &7(tab-complete segít)", id));
                    return;
                }
                // Folia: az inventory-írás a CÉL saját régió-szálán.
                target.getScheduler().run(plugin, task -> {
                    final ItemStack stack = uniqueMaterials.create(id, give);
                    if (stack != null) {
                        giveStack(target, stack);
                        confirm(sender, target, uniqueMaterials.displayName(id), give);
                    }
                }, null);
            }
            case "recept" -> {
                final ProfessionRecipeCatalog.Recipe recipe = catalog.get(id);
                if (recipe == null) {
                    sender.sendMessage(messageManager.get("admin.iceitem.unknown-id",
                            "&cIsmeretlen azonosító: &f%s &7(tab-complete segít)", id));
                    return;
                }
                target.getScheduler().run(plugin, task -> {
                    // darab = ennyi CRAFT-eredmény (affix-roll példányonként, mint kézi craftnál).
                    for (int i = 0; i < give; i++) {
                        final ItemStack stack = recipeBookListener.buildResult(target, recipe);
                        if (stack == null) {
                            sender.sendMessage(messageManager.get("admin.iceitem.build-failed",
                                    "&cA recept eredménye nem építhető fel: &f%s", id));
                            return;
                        }
                        giveStack(target, stack);
                    }
                    confirm(sender, target, recipe.displayName(), give);
                }, null);
            }
            case "relikvia" -> {
                if (relicManager.getDefinition(id) == null) {
                    sender.sendMessage(messageManager.get("admin.iceitem.unknown-id",
                            "&cIsmeretlen azonosító: &f%s &7(tab-complete segít)", id));
                    return;
                }
                target.getScheduler().run(plugin, task -> {
                    if (relicManager.giveRelic(target, id, give, true)) {
                        confirm(sender, target, id, give);
                    } else {
                        sender.sendMessage(messageManager.get("admin.iceitem.relic-failed",
                                "&cA relikvia nem adható ki: &f%s", id));
                    }
                }, null);
            }
            case "tervrajz" -> {
                if (catalog.get(id) == null) {
                    sender.sendMessage(messageManager.get("admin.iceitem.unknown-id",
                            "&cIsmeretlen azonosító: &f%s &7(tab-complete segít)", id));
                    return;
                }
                target.getScheduler().run(plugin, task -> {
                    for (int i = 0; i < give; i++) {
                        final ItemStack stack = blueprintFactory.create(id);
                        if (stack == null) {
                            return;
                        }
                        giveStack(target, stack);
                    }
                    confirm(sender, target, "Tervrajz: " + id, give);
                }, null);
            }
            default -> sender.sendMessage(messageManager.get("admin.iceitem.usage",
                    "&cHasználat: /iceitem <unique|recept|relikvia|tervrajz> <id> [darab] [játékos]"));
        }
    }

    /** A cél szálán fut: ami nem fér az inventoryba, a lába elé esik. */
    private static void giveStack(final Player target, final ItemStack stack) {
        target.getInventory().addItem(stack).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
    }

    private void confirm(final org.bukkit.command.CommandSender sender, final Player target,
                         final String name, final int amount) {
        sender.sendMessage(messageManager.get("admin.iceitem.given",
                "&a✔ Kiadva: &e%s &7×%s &a→ &f%s", name, String.valueOf(amount), target.getName()));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack,
                                               final @NonNull String[] args) {
        if (!commandSourceStack.getSender().hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length <= 1) {
            return filter(TYPES, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2) {
            final String type = args[0].toLowerCase(Locale.ROOT);
            return switch (type) {
                case "unique" -> filter(uniqueMaterials.allIds(), args[1]);
                case "recept", "tervrajz" -> filter(catalog.allIds(), args[1]);
                case "relikvia" -> filter(relicManager.getDefinitions().stream()
                        .map(definition -> definition.id().toLowerCase(Locale.ROOT)).toList(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return filter(List.of("1", "8", "16", "64"), args[2]);
        }
        if (args.length == 4) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        }
        return List.of();
    }

    private static List<String> filter(final List<String> options, final String prefix) {
        final String needle = prefix.toLowerCase(Locale.ROOT);
        final List<String> hits = new ArrayList<>();
        for (final String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                hits.add(option);
            }
        }
        return hits;
    }
}
