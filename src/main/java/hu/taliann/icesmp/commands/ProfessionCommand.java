package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.data.ProfessionCategory;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WoW-style profession command: one gathering + one crafting primary
 * profession per player; secondary professions level for everyone.
 */
public final class ProfessionCommand implements BasicCommand {

    private static final String ADMIN_PERMISSION = "icesmp.admin.profession";

    private final JavaPlugin plugin;
    private final ProfessionManager professionManager;
    private final MessageManager messageManager;
    private final hu.taliann.icesmp.listeners.ProfessionRecipeBookListener recipeBook;
    private final hu.taliann.icesmp.managers.ProfessionRecipeCatalog recipeCatalog;
    private final hu.taliann.icesmp.items.BlueprintItemFactory blueprintFactory;

    public ProfessionCommand(final JavaPlugin plugin, final ProfessionManager professionManager,
                             final MessageManager messageManager,
                             final hu.taliann.icesmp.listeners.ProfessionRecipeBookListener recipeBook,
                             final hu.taliann.icesmp.managers.ProfessionRecipeCatalog recipeCatalog,
                             final hu.taliann.icesmp.items.BlueprintItemFactory blueprintFactory) {
        this.plugin = plugin;
        this.professionManager = professionManager;
        this.messageManager = messageManager;
        this.recipeBook = recipeBook;
        this.recipeCatalog = recipeCatalog;
        this.blueprintFactory = blueprintFactory;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> handleJoin(sender, args);
            case "info" -> handleInfo(sender);
            case "list" -> handleList(sender);
            case "recipes", "receptek", "book" -> handleRecipes(sender);
            case "blueprint", "tervrajz" -> handleBlueprint(sender, args);
            case "set" -> handleSet(sender, args);
            case "clear" -> handleClear(sender, args);
            case "addxp" -> handleAddXp(sender, args);
            default -> {
                sender.sendMessage(messageManager.get("profession-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
                sendHelp(sender);
            }
        }
    }

    private void handleJoin(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("profession-join-usage", "&cHasználat: /profession join <szakma>"));
            return;
        }

        final ProfessionType profession = ProfessionType.fromId(args[1]);
        if (profession == null) {
            sender.sendMessage(messageManager.get("profession-unknown", "&cIsmeretlen szakma: &f%s", args[1]));
            return;
        }

        if (profession.getCategory() == ProfessionCategory.SECONDARY) {
            sender.sendMessage(messageManager.get(
                    "profession-secondary-auto",
                    "&eA másodlagos szakmák (halász, szakács) mindenkinek automatikusan elérhetők."
            ));
            return;
        }

        professionManager.selectProfession(player, profession)
                .whenComplete((selected, failure) -> professionManager.runOnOwnerThread(player, () -> {
                    if (failure != null) {
                        player.sendMessage(messageManager.get("profession-storage-failed",
                                "&cA PlayerProfile szakma mentése meghiúsult."));
                    } else if (!Boolean.TRUE.equals(selected)) {
                        player.sendMessage(messageManager.get(
                                "profession-slot-taken",
                                "&cMár van %s szakmád. Szakmaváltást csak admin végezhet.",
                                profession.getCategory().getDisplayName().toLowerCase(Locale.ROOT)));
                    } else {
                        player.sendMessage(messageManager.getMessage("profession-join-success",
                                        "&aSzakma kiválasztva:")
                                .append(Component.space()).append(profession.getDisplayName()));
                    }
                }));
    }

    private void handleRecipes(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        recipeBook.open(player);
    }

    /** Admin: hands a blueprint item for a recipe to a player (so NPC shops / rewards can distribute it). */
    private void handleBlueprint(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("profession-blueprint-usage", "&cHasználat: /profession blueprint <játékos> <recept-id>"));
            return;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("player-not-found", "&cNincs ilyen online játékos: &f%s", args[1]));
            return;
        }
        final var item = blueprintFactory.create(args[2].toLowerCase(Locale.ROOT));
        if (item == null) {
            sender.sendMessage(messageManager.get("profession-blueprint-unknown", "&cNincs ilyen recept: &f%s", args[2]));
            return;
        }
        // Folia: mutating the target's inventory must run on the target's region thread (mirrors the
        // sibling handleSet/handleAddXp handlers below).
        target.getScheduler().run(plugin, task -> {
            for (final var overflow : target.getInventory().addItem(item).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), overflow);
            }
            sender.sendMessage(messageManager.get("profession-blueprint-given", "&aTervrajz átadva: &e%s &7-> &f%s", args[2].toLowerCase(Locale.ROOT), target.getName()));
        }, null);
    }

    private void handleInfo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        sender.sendMessage(messageManager.get("profession-info-header", "&6Szakmáid:"));
        sendSlotLine(player, ProfessionCategory.GATHERING);
        sendSlotLine(player, ProfessionCategory.CRAFTING);

        for (final ProfessionType professionType : ProfessionType.values()) {
            if (professionType.getCategory() == ProfessionCategory.SECONDARY) {
                player.sendMessage(buildProfessionLine(player, professionType));
            }
        }
    }

    private void sendSlotLine(final Player player, final ProfessionCategory category) {
        final ProfessionType selected = professionManager.getProfession(player, category);
        if (selected == null) {
            player.sendMessage(messageManager.get(
                    "profession-slot-empty",
                    "&7%s szakma: &8nincs &7(/profession join <szakma>)",
                    category.getDisplayName()
            ));
            return;
        }

        player.sendMessage(buildProfessionLine(player, selected));
    }

    private Component buildProfessionLine(final Player player, final ProfessionType professionType) {
        return Component.text(" - ")
                .append(professionType.getDisplayName())
                .append(messageManager.getMessage(
                        "profession-line-stats",
                        "&7 | Szint: &f{level}&7/&f{max} &7| XP: &f{xp}",
                        Map.of(
                                "level", String.valueOf(professionManager.getLevel(player, professionType)),
                                "max", String.valueOf(ProfessionManager.MAX_PROFESSION_LEVEL),
                                "xp", String.valueOf(professionManager.getXp(player, professionType))
                        )
                ));
    }

    private void handleList(final CommandSender sender) {
        for (final ProfessionCategory category : ProfessionCategory.values()) {
            final StringBuilder ids = new StringBuilder();
            for (final ProfessionType professionType : ProfessionType.values()) {
                if (professionType.getCategory() != category) {
                    continue;
                }
                if (!ids.isEmpty()) {
                    ids.append(", ");
                }
                ids.append(professionType.getId());
            }
            sender.sendMessage(messageManager.get(
                    "profession-list-category",
                    "&6%s szakmák: &e%s",
                    category.getDisplayName(),
                    ids.toString()
            ));
        }
    }

    private void handleSet(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.get("profession-set-usage", "&cHasználat: /profession set <játékos> <szakma>"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("target-player-offline", "&cA célpont játékos nem elérhető."));
            return;
        }

        final ProfessionType profession = ProfessionType.fromId(args[2]);
        if (profession == null || profession.getCategory() == ProfessionCategory.SECONDARY) {
            sender.sendMessage(messageManager.get("profession-unknown", "&cIsmeretlen (vagy másodlagos) szakma: &f%s", args[2]));
            return;
        }

        professionManager.setProfession(target, profession)
                .whenComplete((changed, failure) -> professionManager.runOnOwnerThread(target, () -> {
                    if (failure != null) {
                        sender.sendMessage(messageManager.get("profession-storage-failed",
                                "&cA PlayerProfile szakma mentése meghiúsult."));
                    } else {
                        sender.sendMessage(messageManager.get("profession-set-success",
                                "&aSzakma beállítva: &f%s &7-> &e%s",
                                target.getName(), profession.getId()));
                    }
                }));
    }

    private void handleClear(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.get("profession-clear-usage", "&cHasználat: /profession clear <játékos> <gathering|crafting>"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("target-player-offline", "&cA célpont játékos nem elérhető."));
            return;
        }

        final ProfessionCategory category;
        try {
            category = ProfessionCategory.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            sender.sendMessage(messageManager.get("profession-clear-usage", "&cHasználat: /profession clear <játékos> <gathering|crafting>"));
            return;
        }

        if (category == ProfessionCategory.SECONDARY) {
            sender.sendMessage(messageManager.get("profession-clear-usage", "&cHasználat: /profession clear <játékos> <gathering|crafting>"));
            return;
        }

        professionManager.clearProfession(target, category)
                .whenComplete((changed, failure) -> professionManager.runOnOwnerThread(target, () -> {
                    if (failure != null) {
                        sender.sendMessage(messageManager.get("profession-storage-failed",
                                "&cA PlayerProfile szakma mentése meghiúsult."));
                    } else {
                        sender.sendMessage(messageManager.get("profession-clear-success",
                                "&aSzakma slot törölve: &f%s &7(%s)",
                                target.getName(), category.getDisplayName()));
                    }
                }));
    }

    private void handleAddXp(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(messageManager.get("profession-addxp-usage", "&cHasználat: /profession addxp <játékos> <szakma> <mennyiség>"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("target-player-offline", "&cA célpont játékos nem elérhető."));
            return;
        }

        final ProfessionType profession = ProfessionType.fromId(args[2]);
        if (profession == null) {
            sender.sendMessage(messageManager.get("profession-unknown", "&cIsmeretlen szakma: &f%s", args[2]));
            return;
        }

        final int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("invalid-amount", "&cÉrvénytelen összeg."));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(messageManager.get("profession-addxp-failed", "&cNem sikerült XP-t adni (hibás összeg)."));
            return;
        }

        professionManager.addXp(target, profession, amount)
                .whenComplete((change, failure) -> professionManager.runOnOwnerThread(target, () -> {
                    if (failure != null || change == null || !change.changed()) {
                        sender.sendMessage(messageManager.get("profession-addxp-failed",
                                "&cNem sikerült XP-t adni (érvénytelen kérés vagy tárolási hiba)."));
                        return;
                    }
                    sender.sendMessage(messageManager.get(
                            "profession-addxp-success",
                            "&aXP hozzáadva: &f%s &7| %s +&f%s XP &7| Szint: &f%s",
                            target.getName(), profession.getId(), amount, change.level()));
                }));
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("profession-help-header", "&6/profession &7- Elérhető parancsok:"));
        sender.sendMessage(messageManager.get("profession-help-join", "&e/profession join <szakma> &7- Fő szakma kiválasztása (1 gyűjtögető + 1 készítő)."));
        sender.sendMessage(messageManager.get("profession-help-info", "&e/profession info &7- Szakmáid és szintjeid."));
        sender.sendMessage(messageManager.get("profession-help-list", "&e/profession list &7- Elérhető szakmák kategóriánként."));
        sender.sendMessage(messageManager.get("profession-help-recipes", "&e/profession recipes &7- Recept-könyv (tanult/zárolt receptek, craftolás)."));
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("profession-help-blueprint", "&e/profession blueprint <játékos> <recept-id> &7- Tervrajz átadása (Admin)."));
            sender.sendMessage(messageManager.get("profession-help-set", "&e/profession set <játékos> <szakma> &7- Szakma beállítása (Admin)."));
            sender.sendMessage(messageManager.get("profession-help-clear", "&e/profession clear <játékos> <gathering|crafting> &7- Szakma slot törlése (Admin)."));
            sender.sendMessage(messageManager.get("profession-help-addxp", "&e/profession addxp <játékos> <szakma> <mennyiség> &7- XP hozzáadása (Admin)."));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        final List<String> subcommands = sender.hasPermission(ADMIN_PERMISSION)
                ? List.of("join", "info", "list", "recipes", "blueprint", "set", "clear", "addxp")
                : List.of("join", "info", "list", "recipes");

        final String subcommand = prefixAt(args, 0);
        final boolean subcommandComplete = subcommands.contains(subcommand);

        // Két hosszal: 0 = "/profession " (üres prefix), 1 = gépelés közben — kivéve, ha az
        // args[0] már pontos egyezés, akkor a P=1 pozíció javaslatai jönnek.
        if (args.length == 0 || (args.length == 1 && !subcommandComplete)) {
            return subcommands.stream().filter(option -> option.startsWith(subcommand)).toList();
        }

        if ("join".equals(subcommand) && args.length <= 2) {
            return primaryProfessionIds(prefixAt(args, 1));
        }

        final boolean targetComplete = args.length >= 2 && Bukkit.getPlayerExact(args[1]) != null;
        if (("set".equals(subcommand) || "clear".equals(subcommand) || "addxp".equals(subcommand)
                || "blueprint".equals(subcommand))
                && (args.length <= 1 || (args.length == 2 && !targetComplete))) {
            final String prefix = prefixAt(args, 1);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        if ("blueprint".equals(subcommand) && ((args.length == 2 && targetComplete) || args.length == 3)) {
            final String prefix = prefixAt(args, 2);
            return recipeCatalog.blueprintRecipeIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }

        if ("set".equals(subcommand) && ((args.length == 2 && targetComplete) || args.length == 3)) {
            return primaryProfessionIds(prefixAt(args, 2));
        }

        if ("clear".equals(subcommand) && ((args.length == 2 && targetComplete) || args.length == 3)) {
            final String prefix = prefixAt(args, 2);
            return List.of("gathering", "crafting").stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if ("addxp".equals(subcommand) && ((args.length == 2 && targetComplete) || args.length == 3)) {
            final String prefix = prefixAt(args, 2);
            return Arrays.stream(ProfessionType.values())
                    .map(ProfessionType::getId)
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }

        return List.of();
    }


    private List<String> primaryProfessionIds(final String rawPrefix) {
        final String prefix = rawPrefix == null ? "" : rawPrefix.toLowerCase(Locale.ROOT);
        final List<String> options = new ArrayList<>();
        for (final ProfessionType professionType : ProfessionType.values()) {
            if (professionType.getCategory() != ProfessionCategory.SECONDARY
                    && professionType.getId().startsWith(prefix)) {
                options.add(professionType.getId());
            }
        }
        return options;
    }
}
