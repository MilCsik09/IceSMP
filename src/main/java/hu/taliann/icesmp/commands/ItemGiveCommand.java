package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.itemization.ItemMutationCoordinator;
import hu.taliann.icesmp.items.BlueprintItemFactory;
import hu.taliann.icesmp.items.DevItemFactory;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.listeners.ProfessionRecipeBookListener;
import hu.taliann.icesmp.managers.DevItemManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.managers.RelicManager;
import hu.taliann.icesmp.pve.EquippedCombatPowerService;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /iceitem admin item command. Normal issuance uses the canonical factories; inspect exposes
 * the Vanilla Crafting Boundary classification, while recovery resolves only durable
 * ItemMutationCoordinator BEFORE/AFTER witnesses.
 */
public final class ItemGiveCommand implements BasicCommand {

    public static final String PERMISSION = "icesmp.admin.item";
    private static final List<String> TYPES = List.of(
            "unique", "template", "recept", "relikvia", "tervrajz", "erszeny", "dev", "inspect", "recovery");

    private final JavaPlugin plugin;
    private final UniqueMaterialFactory uniqueMaterials;
    private final ProfessionRecipeCatalog catalog;
    private final ProfessionRecipeBookListener recipeBookListener;
    private final RelicManager relicManager;
    private final BlueprintItemFactory blueprintFactory;
    private final MessageManager messageManager;
    private final hu.taliann.icesmp.items.MoneyPouchItemFactory moneyPouchFactory;
    private final DevItemManager devItemManager;
    private final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentity;
    private final hu.taliann.icesmp.itemization.ItemTemplateRegistry itemTemplates;
    private final hu.taliann.icesmp.itemization.ItemTransformationPolicy transformations;

    public ItemGiveCommand(final JavaPlugin plugin, final UniqueMaterialFactory uniqueMaterials,
                           final ProfessionRecipeCatalog catalog,
                           final ProfessionRecipeBookListener recipeBookListener,
                           final RelicManager relicManager, final BlueprintItemFactory blueprintFactory,
                           final MessageManager messageManager,
                           final hu.taliann.icesmp.items.MoneyPouchItemFactory moneyPouchFactory,
                           final DevItemManager devItemManager,
                           final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentity,
                           final hu.taliann.icesmp.itemization.ItemTemplateRegistry itemTemplates,
                           final hu.taliann.icesmp.itemization.ItemTransformationPolicy transformations) {
        this.plugin = plugin;
        this.uniqueMaterials = uniqueMaterials;
        this.catalog = catalog;
        this.recipeBookListener = recipeBookListener;
        this.relicManager = relicManager;
        this.blueprintFactory = blueprintFactory;
        this.messageManager = messageManager;
        this.moneyPouchFactory = moneyPouchFactory;
        this.devItemManager = devItemManager;
        this.itemIdentity = itemIdentity;
        this.itemTemplates = itemTemplates;
        this.transformations = transformations;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("no-permission", "&cNincs jogosultságod ehhez."));
            return;
        }
        if (args.length >= 1 && "inspect".equalsIgnoreCase(args[0])) {
            inspect(sender, args);
            return;
        }
        if (args.length < 2) {
            usage(sender);
            return;
        }
        final String type = args[0].toLowerCase(Locale.ROOT);
        final String id = args[1].toLowerCase(Locale.ROOT);
        if ("recovery".equals(type)) {
            handleRecovery(sender, args);
            return;
        }

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

        final int give = "dev".equals(type) ? 1 : amount;
        switch (type) {
            case "unique" -> {
                if (!uniqueMaterials.isDefined(id)) {
                    sender.sendMessage(messageManager.get("admin.iceitem.unknown-id",
                            "&cIsmeretlen azonosító: &f%s &7(tab-complete segít)", id));
                    return;
                }
                target.getScheduler().run(plugin, task -> {
                    final ItemStack stack = uniqueMaterials.create(id, give);
                    if (stack != null) {
                        giveStack(target, stack);
                        confirm(sender, target, uniqueMaterials.displayName(id), give);
                    }
                }, null);
            }
            case "template" -> {
                if (itemTemplates.find(id).isEmpty()) {
                    sender.sendMessage(messageManager.get("admin.iceitem.unknown-id",
                            "&cIsmeretlen azonosító: &f%s &7(tab-complete segít)", id));
                    return;
                }
                target.getScheduler().run(plugin, task -> {
                    for (int index = 0; index < give; index++) {
                        giveStack(target, itemIdentity.create(id, "admin:give", sender.getName(), null));
                    }
                    confirm(sender, target, "Authored template: " + id, give);
                }, null);
            }
            case "erszeny" -> {
                final long value;
                try {
                    value = Long.parseLong(id);
                } catch (final NumberFormatException exception) {
                    sender.sendMessage(messageManager.get("admin.iceitem.bad-amount", "&cÉrvénytelen összeg: &f%s", id));
                    return;
                }
                if (value <= 0L || moneyPouchFactory == null) {
                    sender.sendMessage(messageManager.get("admin.iceitem.bad-amount", "&cÉrvénytelen összeg: &f%s", id));
                    return;
                }
                target.getScheduler().run(plugin, task -> {
                    for (int i = 0; i < Math.min(give, 64); i++) giveStack(target, moneyPouchFactory.createRandom(value));
                    confirm(sender, target, "Kopott erszény (" + id + ")", Math.min(give, 64));
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
                    for (int i = 0; i < give; i++) {
                        final ItemStack stack = recipe.templateId() == null
                                ? recipeBookListener.buildResult(target, recipe)
                                : itemIdentity.create(recipe.templateId(), "admin:give", sender.getName(), null);
                        if (stack == null) {
                            sendFromTargetThread(sender, target, messageManager.get("admin.iceitem.build-failed",
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
                    if (relicManager.giveRelic(target, id, give, true)) confirm(sender, target, id, give);
                    else sendFromTargetThread(sender, target, messageManager.get("admin.iceitem.relic-failed",
                            "&cA relikvia nem adható ki: &f%s", id));
                }, null);
            }
            case "dev" -> {
                if (!DevItemFactory.BINGULUS_ID.equals(id)) {
                    sender.sendMessage(messageManager.get("admin.iceitem.unknown-id",
                            "&cIsmeretlen azonosító: &f%s &7(tab-complete segít)", id));
                    return;
                }
                if (!devItemManager.isOwner(target)) {
                    sender.sendMessage(messageManager.get("dev-item.wrong-owner",
                            "&cA Csodálatos Bingulus kizárólag a beállított tulajdonosnak adható."));
                    return;
                }
                target.getScheduler().run(plugin, task -> {
                    if (devItemManager.giveToOwner(target)) confirm(sender, target, "Csodálatos Bingulus", 1);
                    else sendFromTargetThread(sender, target, messageManager.get("dev-item.give-failed",
                            "&cA Csodálatos Bingulus nem adható át: a tulajdonos inventoryja tele van."));
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
                        if (stack == null) return;
                        giveStack(target, stack);
                    }
                    confirm(sender, target, "Tervrajz: " + id, give);
                }, null);
            }
            default -> usage(sender);
        }
    }

    private void handleRecovery(final CommandSender sender, final String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messageManager.get("admin.iceitem.recovery-usage",
                    "&cHasználat: /iceitem recovery <operation-id> <before|after> <játékos>"));
            return;
        }
        final UUID operationId;
        try {
            operationId = UUID.fromString(args[1]);
        } catch (final IllegalArgumentException invalid) {
            sender.sendMessage(messageManager.get("admin.iceitem.recovery-bad-id",
                    "&cÉrvénytelen mutation operation ID: &f%s", args[1]));
            return;
        }
        final ItemMutationCoordinator.ResolutionWitness witness;
        try {
            witness = ItemMutationCoordinator.ResolutionWitness.valueOf(args[2].trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException invalid) {
            sender.sendMessage(messageManager.get("admin.iceitem.recovery-bad-witness",
                    "&cA witness csak BEFORE vagy AFTER lehet."));
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[3]);
        if (target == null) {
            sender.sendMessage(messageManager.get("admin.iceitem.no-player", "&cNincs ilyen online játékos: &f%s", args[3]));
            return;
        }
        final ItemMutationCoordinator coordinator = ItemMutationCoordinator.current();
        if (coordinator == null) {
            sender.sendMessage(messageManager.get("admin.iceitem.recovery-unavailable",
                    "&cAz item mutation recovery authority nem érhető el."));
            return;
        }
        coordinator.resolveManual(target, operationId, witness, sender.getName(), outcome ->
                sendFromTargetThread(sender, target, messageManager.get(outcome.messageKey(), outcome.success()
                        ? "&aA mutation recovery tartósan lezárult."
                        : "&cA mutation recovery nem zárható le; a journal record megmaradt.")));
    }

    private void inspect(final CommandSender sender, final String[] args) {
        final Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(messageManager.get("admin.iceitem.no-player", "&cNincs ilyen online játékos: &f%s", args[1]));
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(messageManager.get("admin.iceitem.inspect-needs-player",
                    "&cKonzolról add meg a cél-játékost: /iceitem inspect <játékos>"));
            return;
        }
        target.getScheduler().run(plugin, task -> {
            final ItemStack item = target.getInventory().getItemInMainHand();
            final var classification = transformations.classify(item);
            final var inspection = itemIdentity.inspect(item);
            final String template = inspection.instance() == null ? "-" : inspection.instance().templateId();
            final String uuid = inspection.instance() == null ? "-" : inspection.instance().itemId().toString();
            sendFromTargetThread(sender, target, messageManager.get("admin.iceitem.inspect",
                    "&bItem inspect &7→ &f%s &8| &7identity=&f%s &8| &7template=&f%s &8| &7UUID=&f%s",
                    classification.domain().name(), inspection.status().name(), template, uuid));
            sendFromTargetThread(sender, target, messageManager.get("admin.iceitem.inspect-detail",
                    "&7Diagnózis: &f%s", inspection.diagnostic()));
            if (classification.domain() == hu.taliann.icesmp.itemization.ItemTransformationPolicy.Domain.CANONICAL_MMO_GEAR) {
                final String stationPolicy = java.util.stream.Stream.of(
                                hu.taliann.icesmp.itemization.ItemTransformationPolicy.Transformation.VANILLA_CRAFT_INPUT,
                                hu.taliann.icesmp.itemization.ItemTransformationPolicy.Transformation.ANVIL_RENAME,
                                hu.taliann.icesmp.itemization.ItemTransformationPolicy.Transformation.ANVIL_ITEM_REPAIR,
                                hu.taliann.icesmp.itemization.ItemTransformationPolicy.Transformation.ENCHANTING_TABLE,
                                hu.taliann.icesmp.itemization.ItemTransformationPolicy.Transformation.SMITHING_UPGRADE,
                                hu.taliann.icesmp.itemization.ItemTransformationPolicy.Transformation.ARMOR_TRIM,
                                hu.taliann.icesmp.itemization.ItemTransformationPolicy.Transformation.GRINDSTONE)
                        .map(operation -> operation.name() + '=' + transformations.decide(classification, operation).action().name())
                        .collect(java.util.stream.Collectors.joining(", "));
                sendFromTargetThread(sender, target, messageManager.get("admin.iceitem.inspect-policy",
                        "&7Station policy: &f%s", stationPolicy));
            }
        }, null);
    }

    private void usage(final CommandSender sender) {
        sender.sendMessage(messageManager.get("admin.iceitem.usage",
                "&cHasználat: /iceitem <unique|template|recept|relikvia|tervrajz|erszeny|dev> <id> [darab] [játékos], /iceitem inspect [játékos], vagy /iceitem recovery <operation-id> <before|after> <játékos>"));
    }

    private static void giveStack(final Player target, final ItemStack stack) {
        target.getInventory().addItem(stack).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        EquippedCombatPowerService.refreshAfterMutation(target);
    }

    private void confirm(final CommandSender sender, final Player target, final String name, final int amount) {
        sendFromTargetThread(sender, target, messageManager.get("admin.iceitem.given",
                "&a✔ Kiadva: &e%s &7×%s &a→ &f%s", name, String.valueOf(amount), target.getName()));
    }

    private void sendFromTargetThread(final CommandSender sender, final Player target, final String message) {
        if (sender instanceof Player player && !player.getUniqueId().equals(target.getUniqueId())) {
            player.getScheduler().run(plugin, task -> player.sendMessage(message), null);
            return;
        }
        sender.sendMessage(message);
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack,
                                               final @NonNull String[] args) {
        if (!commandSourceStack.getSender().hasPermission(PERMISSION)) return List.of();
        if (args.length <= 1) return filter(TYPES, args.length == 0 ? "" : args[0]);
        if (args.length == 2) {
            final String type = args[0].toLowerCase(Locale.ROOT);
            return switch (type) {
                case "inspect" -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
                case "unique" -> filter(uniqueMaterials.allIds(), args[1]);
                case "template" -> filter(new ArrayList<>(itemTemplates.snapshot().keySet()), args[1]);
                case "erszeny" -> filter(List.of("10", "25", "50", "100"), args[1]);
                case "recept", "tervrajz" -> filter(catalog.allIds(), args[1]);
                case "relikvia" -> filter(relicManager.getDefinitions().stream()
                        .map(definition -> definition.id().toLowerCase(Locale.ROOT)).toList(), args[1]);
                case "dev" -> filter(List.of(DevItemFactory.BINGULUS_ID), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            if ("recovery".equalsIgnoreCase(args[0])) return filter(List.of("before", "after"), args[2]);
            return "dev".equalsIgnoreCase(args[0])
                    ? filter(List.of("1"), args[2]) : filter(List.of("1", "8", "16", "64"), args[2]);
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
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) hits.add(option);
        }
        return hits;
    }
}
