package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.NpcBindingManager;
import hu.taliann.icesmp.managers.NpcBindingManager.Binding;
import hu.taliann.icesmp.managers.NpcBindingManager.BindingType;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.managers.ShopManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /npcbind — admin command that detaches NPC integration from NPC-name
 * semantics: binds any FancyNpcs NPC (by its internal name) to a quest, a
 * shop, a banker, or a currency exchanger, instead of relying on the legacy
 * {@code giver-npc: <name>} / {@code faction-shops.<name>} conventions.
 *
 * <p>Works even without FancyNpcs installed — bindings persist through
 * {@link NpcBindingManager} regardless; they simply have no runtime effect
 * until {@link hu.taliann.icesmp.integration.FancyNpcsQuestBridge} is active.
 */
public final class NpcBindCommand implements BasicCommand {

    private static final String PERMISSION = "icesmp.admin.npc";
    private static final List<String> ACTIONS = List.of("quest", "shop", "bank", "exchange", "faction", "command", "clear");

    private final NpcBindingManager npcBindingManager;
    private final QuestManager questManager;
    private final ShopManager shopManager;
    private final MessageManager messageManager;

    public NpcBindCommand(final NpcBindingManager npcBindingManager, final QuestManager questManager,
                          final ShopManager shopManager, final MessageManager messageManager) {
        this.npcBindingManager = npcBindingManager;
        this.questManager = questManager;
        this.shopManager = shopManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        if ("list".equalsIgnoreCase(args[0])) {
            handleList(sender);
            return;
        }

        final String npcName = args[0];
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "quest" -> handleQuest(sender, npcName, args);
            case "shop" -> handleShop(sender, npcName, args);
            case "bank" -> handleBankOrExchange(sender, npcName, BindingType.BANK);
            case "exchange" -> handleBankOrExchange(sender, npcName, BindingType.EXCHANGE);
            case "faction" -> handleFaction(sender, npcName);
            case "command" -> handleCommand(sender, npcName, args);
            case "clear" -> handleClear(sender, npcName);
            default -> {
                sender.sendMessage(messageManager.get("npcbind-unknown-action", "&cIsmeretlen kötés-típus: &f%s", args[1]));
                sendHelp(sender);
            }
        }
    }

    private void handleQuest(final CommandSender sender, final String npcName, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("npcbind-quest-usage", "&cHasználat: /npcbind <npc> quest <küldetés-id>"));
            return;
        }

        final String questId = args[2];
        if (questManager.getQuestSection(questId) == null) {
            sender.sendMessage(messageManager.get("npcbind-quest-unknown", "&cIsmeretlen küldetés: &f%s", questId));
            return;
        }

        npcBindingManager.bind(npcName, BindingType.QUEST, questId.toLowerCase(Locale.ROOT));
        sender.sendMessage(messageManager.get(
                "npcbind-quest-success",
                "&aKötve: &e%s &7NPC most a(z) &e%s &7küldetést adja.",
                npcName, questId.toLowerCase(Locale.ROOT)
        ));
    }

    private void handleShop(final CommandSender sender, final String npcName, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("npcbind-shop-usage", "&cHasználat: /npcbind <npc> shop <bolt-név>"));
            return;
        }

        final String shopName = args[2];
        npcBindingManager.bind(npcName, BindingType.SHOP, shopName.toLowerCase(Locale.ROOT));
        if (shopManager.hasShop(shopName)) {
            sender.sendMessage(messageManager.get(
                    "npcbind-shop-success",
                    "&aKötve: &e%s &7NPC most a(z) &e%s &7boltot nyitja.",
                    npcName, shopName
            ));
        } else {
            // No strict enumeration exists on ShopManager (a shop may just be currently
            // disabled/gated, e.g. the caravan while it's not in town) — bind anyway and warn.
            sender.sendMessage(messageManager.get(
                    "npcbind-shop-warning",
                    "&eKötve: &e%s &7-> &e%s&7, de ez a bolt jelenleg nem található/aktív (elgépelés vagy kikapcsolt bolt?). A kötés mentve.",
                    npcName, shopName
            ));
        }
    }

    /**
     * Binds the NPC to a command line the INTERACTING PLAYER runs on right-click
     * (performCommand — the player's own permissions apply, so this can never grant
     * anything the player couldn't type themselves). Leading slash is stripped.
     */
    private void handleCommand(final CommandSender sender, final String npcName, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("npcbind-command-usage",
                    "&cHasználat: /npcbind <npc> command <parancs...> &7(a kattintó játékos nevében fut)"));
            return;
        }
        String commandLine = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
        if (commandLine.startsWith("/")) {
            commandLine = commandLine.substring(1);
        }
        if (commandLine.isEmpty()) {
            sender.sendMessage(messageManager.get("npcbind-command-usage",
                    "&cHasználat: /npcbind <npc> command <parancs...> &7(a kattintó játékos nevében fut)"));
            return;
        }
        npcBindingManager.bind(npcName, BindingType.COMMAND, commandLine);
        sender.sendMessage(messageManager.get(
                "npcbind-command-success",
                "&aKötve: &e%s &7NPC-re kattintva a játékos ezt futtatja: &e/%s",
                npcName, commandLine
        ));
    }

    private void handleFaction(final CommandSender sender, final String npcName) {
        npcBindingManager.bind(npcName, BindingType.FACTION, "");
        sender.sendMessage(messageManager.get(
                "npcbind-faction-success",
                "&aKötve: &e%s &7NPC most királyság-választó hírnökként a frakció-menüt nyitja.",
                npcName
        ));
    }

    private void handleBankOrExchange(final CommandSender sender, final String npcName, final BindingType type) {
        npcBindingManager.bind(npcName, type, "");
        final String kind = type == BindingType.BANK ? "bankár" : "valutaváltó";
        sender.sendMessage(messageManager.get(
                "npcbind-bank-success",
                "&aKötve: &e%s &7NPC most &e%s&7-ként a bank menüt nyitja.",
                npcName, kind
        ));
    }

    private void handleClear(final CommandSender sender, final String npcName) {
        if (npcBindingManager.unbind(npcName)) {
            sender.sendMessage(messageManager.get("npcbind-clear-success", "&aKötés törölve: &e%s", npcName));
        } else {
            sender.sendMessage(messageManager.get("npcbind-clear-none", "&cEnnek az NPC-nek nincs kötése: &f%s", npcName));
        }
    }

    private void handleList(final CommandSender sender) {
        final Map<String, Binding> bindings = npcBindingManager.describeAll();
        if (bindings.isEmpty()) {
            sender.sendMessage(messageManager.get("npcbind-list-empty", "&7Nincs egyetlen NPC-kötés sem."));
            return;
        }

        sender.sendMessage(messageManager.get("npcbind-list-header", "&6NPC-kötések (%s):", bindings.size()));
        for (final Map.Entry<String, Binding> entry : bindings.entrySet()) {
            final Binding binding = entry.getValue();
            final String detail = binding.value() == null || binding.value().isBlank()
                    ? ""
                    : " (" + binding.value() + ")";
            sender.sendMessage(messageManager.get(
                    "npcbind-list-line",
                    "&e%s &7- &f%s%s",
                    entry.getKey(), binding.type().name(), detail
            ));
        }
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("npcbind-help-header", "&6/npcbind &7- NPC-kötések (Admin):"));
        sender.sendMessage(messageManager.get("npcbind-help-quest", "&e/npcbind <npc> quest <id> &7- Az NPC ezt a küldetést adja."));
        sender.sendMessage(messageManager.get("npcbind-help-shop", "&e/npcbind <npc> shop <bolt> &7- Az NPC ezt a boltot nyitja."));
        sender.sendMessage(messageManager.get("npcbind-help-bank", "&e/npcbind <npc> bank &7- Az NPC bankárként a bank menüt nyitja."));
        sender.sendMessage(messageManager.get("npcbind-help-exchange", "&e/npcbind <npc> exchange &7- Az NPC valutaváltóként a bank menüt nyitja."));
        sender.sendMessage(messageManager.get("npcbind-help-faction", "&e/npcbind <npc> faction &7- Az NPC a frakció-menüt nyitja (királyság-választó hírnök)."));
        sender.sendMessage(messageManager.get("npcbind-help-command", "&e/npcbind <npc> command <parancs...> &7- A kattintó játékos futtatja a parancsot (saját jogaival)."));
        sender.sendMessage(messageManager.get("npcbind-help-clear", "&e/npcbind <npc> clear &7- Kötés törlése."));
        sender.sendMessage(messageManager.get("npcbind-help-list", "&e/npcbind list &7- Minden kötés kiírása."));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }

        // Position 0 (npc name) is free text — the only known literal to offer is "list".
        if (args.length == 0 || (args.length == 1 && !"list".equalsIgnoreCase(args[0]))) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return "list".startsWith(prefix) ? List.of("list") : List.of();
        }
        if ("list".equalsIgnoreCase(args[0])) {
            return List.of();
        }

        // A Paper a lezáró szóköz utáni ÜRES szót nem adja át (args rövidebb), ezért minden
        // szintet két hosszal kezelünk: N+1 = szó közben (prefix az utolsó arg), N = szóköz
        // után (üres prefix) — utóbbit a megelőző arg pontos egyezése különbözteti meg.
        if (args.length == 1 || (args.length == 2 && !ACTIONS.contains(args[1].toLowerCase(Locale.ROOT)))) {
            final String prefix = prefixAt(args, 1);
            return ACTIONS.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if ("quest".equals(args[1].toLowerCase(Locale.ROOT)) && args.length <= 3) {
            final String prefix = prefixAt(args, 2);
            return questManager.getQuestIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }

        return List.of();
    }

    /** Az adott pozíción gépelés alatt álló szó (kisbetűsítve), vagy üres, ha még el sem kezdték. */
    private static String prefixAt(final String[] args, final int index) {
        return args.length > index ? args[index].toLowerCase(Locale.ROOT) : "";
    }
}
