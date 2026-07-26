package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.listeners.QuestBuilderListener;
import hu.taliann.icesmp.managers.QuestManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /quest — küldetések: list (elérhető), info (aktív + haladás), accept,
 * abandon; admin: complete <player> <quest>.
 */
public final class QuestCommand implements BasicCommand {

    private static final String ADMIN_PERMISSION = "icesmp.admin.quest";

    private final JavaPlugin plugin;
    private final QuestManager questManager;
    private final MessageManager messageManager;
    private final QuestBuilderListener questBuilderListener;

    private final hu.taliann.icesmp.managers.ConfigManager configManager;

    public QuestCommand(final JavaPlugin plugin, final QuestManager questManager,
                        final hu.taliann.icesmp.managers.ConfigManager configManager,
                        final MessageManager messageManager, final QuestBuilderListener questBuilderListener) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.questBuilderListener = questBuilderListener;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "log", "gui", "naplo", "napló" -> handleLog(sender);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender);
            case "accept" -> handleAccept(sender, args);
            case "talk" -> handleTalk(sender, args);
            case "abandon" -> handleAbandon(sender, args);
            case "complete" -> handleComplete(sender, args);
            case "admin" -> handleAdmin(sender, args);
            default -> {
                sender.sendMessage(messageManager.get("quest-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
                sendHelp(sender);
            }
        }
    }

    // ===== Admin quest-szerkesztő (kód és fájl-szerkesztés nélkül) =====

    private void handleAdmin(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        if (args.length < 2) {
            sendAdminHelp(sender);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> handleAdminCreate(sender, args);
            case "addobjective" -> handleAdminAddObjective(sender, args);
            case "set" -> handleAdminSet(sender, args);
            case "delete" -> handleAdminDelete(sender, args);
            case "info" -> handleAdminInfo(sender, args);
            case "list" -> handleAdminList(sender);
            case "builder" -> handleAdminBuilder(sender, args);
            default -> sendAdminHelp(sender);
        }
    }

    private void handleAdminCreate(final CommandSender sender, final String[] args) {
        if (args.length < 5) {
            sender.sendMessage(messageManager.get("quest-admin-create-usage",
                    "&cHasználat: /quest admin create <id> <feladat-típus> <darab> <megjelenő név...>"));
            return;
        }

        final int count;
        try {
            count = Integer.parseInt(args[4]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("quest-admin-bad-count", "&cA darabszámnak pozitív egész számnak kell lennie."));
            return;
        }

        final String displayName = String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length));
        final String errorKey = questManager.createCustomQuest(args[2], args[3], count, displayName);
        if (errorKey != null) {
            sender.sendMessage(messageManager.get(errorKey, defaultAdminErrorFor(errorKey)));
            return;
        }

        sender.sendMessage(messageManager.get(
                "quest-admin-create-success",
                "&aKüldetés létrehozva: &e%s &7(%s x%s). Mezők beállítása: &f/quest admin set %s <mező> <érték>",
                args[2].toLowerCase(Locale.ROOT), args[3].toUpperCase(Locale.ROOT), count, args[2].toLowerCase(Locale.ROOT)
        ));
    }

    private void handleAdminAddObjective(final CommandSender sender, final String[] args) {
        if (args.length < 5) {
            sender.sendMessage(messageManager.get("quest-admin-addobjective-usage",
                    "&cHasználat: /quest admin addobjective <id> <feladat-típus> <darab> [leírás...]"));
            return;
        }

        final int count;
        try {
            count = Integer.parseInt(args[4]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("quest-admin-bad-count", "&cA darabszámnak pozitív egész számnak kell lennie."));
            return;
        }

        final String description = args.length > 5 ? String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length)) : "";
        final int[] index = new int[1];
        final String errorKey = questManager.addObjective(args[2], args[3], count, description, index);
        if (errorKey != null) {
            sender.sendMessage(messageManager.get(errorKey, defaultAdminErrorFor(errorKey)));
            return;
        }

        sender.sendMessage(messageManager.get(
                "quest-admin-addobjective-success",
                "&aObjektíva hozzáadva a(z) &e%s &aküldetéshez: &f#%s %s x%s&7. Több feladat = objectives-mode ALL (párhuzamos) vagy SEQUENCE (sorban).",
                args[2].toLowerCase(Locale.ROOT), index[0], args[3].toUpperCase(Locale.ROOT), count
        ));
    }

    private void handleAdminSet(final CommandSender sender, final String[] args) {
        if (args.length < 5) {
            sender.sendMessage(messageManager.get("quest-admin-set-usage",
                    "&cHasználat: /quest admin set <id> <mező> <érték...>"));
            return;
        }

        final String value = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));
        final String errorKey = questManager.setCustomQuestField(args[2], args[3], value);
        if (errorKey != null) {
            sender.sendMessage(messageManager.get(errorKey, defaultAdminErrorFor(errorKey)));
            return;
        }

        sender.sendMessage(messageManager.get(
                "quest-admin-set-success",
                "&aBeállítva: &e%s&7.&f%s &7= &f%s",
                args[2].toLowerCase(Locale.ROOT), args[3].toLowerCase(Locale.ROOT), value
        ));
    }

    private void handleAdminDelete(final CommandSender sender, final String[] args) {
        if (args.length < 3 || !questManager.deleteCustomQuest(args[2])) {
            sender.sendMessage(messageManager.get("quest-admin-not-custom",
                    "&cNincs ilyen admin-készítette küldetés (a configbeliek innen nem törölhetők)."));
            return;
        }

        sender.sendMessage(messageManager.get("quest-admin-delete-success", "&aKüldetés törölve: &e%s", args[2].toLowerCase(Locale.ROOT)));
    }

    private void handleAdminInfo(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("quest-admin-info-usage", "&cHasználat: /quest admin info <id>"));
            return;
        }

        final ConfigurationSection quest = questManager.getQuestSection(args[2]);
        if (quest == null) {
            sender.sendMessage(messageManager.get("quest-unknown", "&cIsmeretlen küldetés: &f%s", args[2]));
            return;
        }

        sender.sendMessage(messageManager.get(
                "quest-admin-info-header",
                "&6Küldetés: &e%s &7(%s)",
                args[2].toLowerCase(Locale.ROOT),
                questManager.isCustomQuest(args[2]) ? "admin-készítette" : "config"
        ));
        for (final Map.Entry<String, Object> entry : quest.getValues(true).entrySet()) {
            if (entry.getValue() instanceof ConfigurationSection) {
                continue;
            }
            sender.sendMessage(messageManager.get("quest-admin-info-line", "&7 - &f%s &7= &f%s",
                    entry.getKey(), String.valueOf(entry.getValue())));
        }
    }

    private void handleAdminBuilder(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("quest-builder-usage",
                    "&cHasználat: /quest admin builder <id> &7(új id = új küldetés varázsló)"));
            return;
        }
        questBuilderListener.openBuilder(player, args[2]);
    }

    private void handleAdminList(final CommandSender sender) {
        final var customIds = questManager.getCustomQuestIds();
        if (customIds.isEmpty()) {
            sender.sendMessage(messageManager.get("quest-admin-list-empty", "&7Nincs admin-készítette küldetés."));
            return;
        }

        sender.sendMessage(messageManager.get("quest-admin-list-header", "&6Admin-készítette küldetések (%s):", customIds.size()));
        for (final String id : customIds) {
            sender.sendMessage(messageManager.get("quest-admin-list-line", "&e%s &7- %s", id, questManager.getDisplayName(id)));
        }
    }

    private String defaultAdminErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "quest-admin-bad-id" -> "&cAz azonosító csak kisbetűt, számot és aláhúzást tartalmazhat.";
            case "quest-admin-exists" -> "&cMár létezik küldetés ezzel az azonosítóval.";
            case "quest-admin-bad-objective" -> "&cIsmeretlen feladat-típus. Elérhetők: "
                    + String.join(", ", QuestManager.OBJECTIVE_TYPES);
            case "quest-admin-bad-count" -> "&cA darabszámnak pozitív egész számnak kell lennie.";
            case "quest-admin-not-custom" -> "&cNincs ilyen admin-készítette küldetés (a configbeliek innen nem szerkeszthetők).";
            case "quest-admin-bad-field" -> "&cIsmeretlen mező. Elérhetők: " + String.join(", ", QuestManager.EDITABLE_FIELDS);
            case "quest-admin-bad-value" -> "&cÉrvénytelen érték ehhez a mezőhöz.";
            default -> "&cA művelet nem sikerült.";
        };
    }

    private void sendAdminHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("quest-admin-help-header", "&6/quest admin &7- Küldetés-szerkesztő (Admin):"));
        sender.sendMessage(messageManager.get("quest-admin-help-create",
                "&e/quest admin create <id> <feladat> <darab> <név...> &7- Új küldetés."));
        sender.sendMessage(messageManager.get("quest-admin-help-addobjective",
                "&e/quest admin addobjective <id> <feladat> <darab> [leírás...] &7- További feladat (több-feladatos küldetés)."));
        sender.sendMessage(messageManager.get("quest-admin-help-set",
                "&e/quest admin set <id> <mező> <érték...> &7- Mező beállítása (feltételek, jutalmak, NPC, objectives-mode, dialogue.choices.N.text|quest...)."));
        sender.sendMessage(messageManager.get("quest-admin-help-delete", "&e/quest admin delete <id> &7- Küldetés törlése."));
        sender.sendMessage(messageManager.get("quest-admin-help-info", "&e/quest admin info <id> &7- Definíció megtekintése."));
        sender.sendMessage(messageManager.get("quest-admin-help-list", "&e/quest admin list &7- Admin-készítette küldetések."));
        sender.sendMessage(messageManager.get("quest-admin-help-builder",
                "&e/quest admin builder <id> &7- Kattintós küldetés-szerkesztő (új id-vel varázsló)."));
    }

    private void handleLog(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        hu.taliann.icesmp.gui.QuestLogGUI.open(player, questManager, messageManager);
    }

    private void handleList(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        sender.sendMessage(messageManager.get("quest-list-header", "&6Elérhető küldetések:"));
        boolean any = false;
        for (final String questId : questManager.getQuestIds()) {
            if (questManager.getAcceptBlocker(player, questId) != null) {
                continue;
            }

            any = true;
            final ConfigurationSection quest = questManager.getQuestSection(questId);
            sender.sendMessage(messageManager.get(
                    "quest-list-line",
                    "&e%s &7(%s) - %s",
                    questManager.getDisplayName(questId),
                    questId,
                    quest == null ? "" : quest.getString("description", "")
            ));
        }

        if (!any) {
            sender.sendMessage(messageManager.get("quest-list-empty", "&7Jelenleg nincs felvehető küldetésed."));
        }
    }

    private void handleInfo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final List<String> active = questManager.getActiveQuests(player);
        sender.sendMessage(messageManager.get("quest-info-header", "&6Aktív küldetéseid (%s):", active.size()));
        for (final String questId : active) {
            sender.sendMessage(messageManager.get(
                    "quest-info-line",
                    "&e%s &7- haladás: &f%s",
                    questManager.getDisplayName(questId),
                    questManager.describeProgress(player, questId)
            ));
        }

        sender.sendMessage(messageManager.get(
                "quest-info-completed",
                "&7Teljesített küldetések: &f%s",
                questManager.getCompletedQuests(player).size()
        ));
    }

    private void handleAccept(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("quest-accept-usage", "&cHasználat: /quest accept <küldetés>"));
            return;
        }

        final String blocker = questManager.getAcceptBlocker(player, args[1]);
        if (blocker != null) {
            sender.sendMessage(messageManager.get(blocker, defaultBlockerMessage(blocker)));
            return;
        }

        questManager.accept(player, args[1]);
        sender.sendMessage(messageManager.get(
                "quest-accept-success",
                "&aKüldetés felvéve: &e%s",
                questManager.getDisplayName(args[1])
        ));
        // A dialógus a parancsos felvételkor is jár — az elágazó story-döntések
        // (pl. merchant_choice) szövege enélkül sosem látszana NPC nélkül.
        questManager.playGiveDialogue(player, args[1]);
    }

    /**
     * NPC-tartalék-út: a TALK_TO_NPC/DELIVER_ITEMS objektívák és a giver-npc questek
     * a FancyNpcs-híd nélkül is teljesíthetők/felvehetők — a sztori-gerinc nem lóghat
     * némán egy külső pluginon. Alapból csak akkor él, ha a híd NEM aktív.
     */
    private void handleTalk(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (questManager.isNpcBridgeActive()
                && !configManager.getBoolean("quest-npc-fallback.always", false)) {
            sender.sendMessage(messageManager.get("quest-talk-npc-active",
                    "&7A mesélők a helyükön állnak — keresd fel őket személyesen (kattints az NPC-re)."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("quest-talk-usage", "&cHasználat: /quest talk <npc-név>"));
            return;
        }
        final String npcName = args[1];
        questManager.handleNpcInteract(player, npcName);
        final String accepted = questManager.acceptFromNpc(player, npcName);
        if (accepted == null) {
            sender.sendMessage(messageManager.get("quest-talk-done",
                    "&7Szót váltottatok. (Ha küldetés-célpont volt, teljesült.)"));
        }
    }

    private void handleAbandon(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length < 2 || !questManager.abandon(player, args[1])) {
            sender.sendMessage(messageManager.get("quest-abandon-failed", "&cNincs ilyen aktív küldetésed."));
            return;
        }

        sender.sendMessage(messageManager.get("quest-abandon-success", "&eKüldetés eldobva: &f%s", questManager.getDisplayName(args[1])));
    }

    private void handleComplete(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.get("quest-complete-usage", "&cHasználat: /quest complete <játékos> <küldetés>"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("target-player-offline", "&cA célpont játékos nem elérhető."));
            return;
        }

        if (questManager.getQuestSection(args[2]) == null) {
            sender.sendMessage(messageManager.get("quest-unknown", "&cIsmeretlen küldetés: &f%s", args[2]));
            return;
        }

        final String questId = args[2];
        target.getScheduler().run(plugin, task -> {
            questManager.complete(target, questId);
            sender.sendMessage(messageManager.get("quest-complete-success", "&aKüldetés lezárva: &f%s &7-> &e%s", target.getName(), questId));
        }, null);
    }

    private String defaultBlockerMessage(final String blocker) {
        return switch (blocker) {
            case "quest-unknown" -> "&cIsmeretlen küldetés.";
            case "quest-already-active" -> "&cEz a küldetés már aktív nálad.";
            case "quest-already-completed" -> "&cEzt a küldetést már teljesítetted.";
            case "quest-requires-job" -> "&cEhhez a küldetéshez másik kaszt szükséges.";
            case "quest-requires-faction" -> "&cEhhez a küldetéshez másik frakció tagjának kell lenned.";
            case "quest-requires-level" -> "&cMég nem vagy elég magas szintű ehhez a küldetéshez.";
            case "quest-requires-quest" -> "&cElőbb az előfeltétel-küldetést kell teljesítened.";
            case "quest-on-cooldown" -> "&cEz a küldetés még pihen — nézz vissza később.";
            case "quest-not-offered-today" -> "&cEz a küldetés ma nem elérhető — nézz vissza egy másik napon.";
            case "quest-season-locked" -> "&cEzt a szezont már teljesítetted — az új szezonban újra elérhető lesz.";
            case "quest-chapter-closed" -> "&cEz a fejezet már lezárult — a krónika továbblapozott.";
            case "quest-chapter-future" -> "&cEz a fejezet még nem nyílt meg — várd ki a krónika következő lapját.";
            case "quest-season-window-future" -> "&cEnnek a történetnek még nem jött el az ideje — a szezon későbbi napjain nyílik.";
            case "quest-season-window-closed" -> "&cEz a történet-ablak bezárult ebben a szezonban — az idő továbbhaladt.";
            default -> "&cA küldetés nem vehető fel.";
        };
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("quest-help-header", "&6/quest &7- Elérhető parancsok:"));
        sender.sendMessage(messageManager.get("quest-help-log", "&e/quest log &7- Küldetésnapló (grafikus felület)."));
        sender.sendMessage(messageManager.get("quest-help-list", "&e/quest list &7- Felvehető küldetések."));
        sender.sendMessage(messageManager.get("quest-help-info", "&e/quest info &7- Aktív küldetéseid és haladásod."));
        sender.sendMessage(messageManager.get("quest-help-accept", "&e/quest accept <küldetés> &7- Küldetés felvétele."));
        sender.sendMessage(messageManager.get("quest-help-abandon", "&e/quest abandon <küldetés> &7- Küldetés eldobása."));
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("quest-help-complete", "&e/quest complete <játékos> <küldetés> &7- Lezárás (Admin)."));
            sender.sendMessage(messageManager.get("quest-help-admin", "&e/quest admin &7- Küldetés-szerkesztő (Admin)."));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        final List<String> subcommands = sender.hasPermission(ADMIN_PERMISSION)
                ? List.of("log", "list", "info", "accept", "abandon", "talk", "complete", "admin")
                : List.of("log", "list", "info", "accept", "abandon", "talk");

        // A Paper a lezáró szóköz utáni ÜRES szót nem adja át (args rövidebb), ezért minden
        // szintet két hosszal kezelünk: N+1 = szó közben (prefix az utolsó arg), N = szóköz
        // után (üres prefix) — utóbbit a megelőző arg pontos egyezése különbözteti meg.
        if (args.length == 0 || (args.length == 1 && !subcommands.contains(args[0].toLowerCase(Locale.ROOT)))) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return subcommands.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ("admin".equals(subcommand)) {
            return suggestAdmin(sender, args);
        }
        if (args.length <= 2 && "talk".equals(subcommand)) {
            final String prefix = prefixAt(args, 1);
            return questManager.getQuestNpcNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length <= 2 && ("accept".equals(subcommand) || "abandon".equals(subcommand))) {
            final String prefix = prefixAt(args, 1);
            if ("abandon".equals(subcommand) && sender instanceof Player player) {
                return questManager.getActiveQuests(player).stream().filter(id -> id.startsWith(prefix)).toList();
            }
            return questManager.getQuestIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }

        if ("complete".equals(subcommand)) {
            final boolean nameComplete = args.length >= 2 && Bukkit.getPlayerExact(args[1]) != null;
            if (args.length <= 2 && !nameComplete) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefixAt(args, 1)))
                        .toList();
            }
            if (args.length <= 3) {
                final String prefix = prefixAt(args, 2);
                return questManager.getQuestIds().stream().filter(id -> id.startsWith(prefix)).toList();
            }
        }

        return List.of();
    }


    private Collection<String> suggestAdmin(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }

        final List<String> actions = List.of("create", "addobjective", "set", "delete", "info", "list", "builder");
        final String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1 || (args.length == 2 && !actions.contains(action))) {
            final String prefix = prefixAt(args, 1);
            return actions.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        // A quest-id pozíció (P=2): akkor lépünk tovább a P=3 javaslatokra, ha az id már
        // egy létező küldetésre pontosan illeszkedik.
        final boolean idComplete = args.length >= 3
                && questManager.getQuestIds().contains(args[2].toLowerCase(Locale.ROOT));
        if (("set".equals(action) || "delete".equals(action) || "addobjective".equals(action)
                || "builder".equals(action))
                && (args.length == 2 || (args.length == 3 && !idComplete))) {
            final String prefix = prefixAt(args, 2);
            return questManager.getCustomQuestIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }
        if ("info".equals(action) && (args.length == 2 || (args.length == 3 && !idComplete))) {
            final String prefix = prefixAt(args, 2);
            return questManager.getQuestIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }

        if (("create".equals(action) || "addobjective".equals(action)) && args.length <= 4) {
            // create-nél az id szabad szöveg — a típus-javaslat szóköz után (üres prefixszel) is jön.
            final String prefix = prefixAt(args, 3).toUpperCase(Locale.ROOT);
            return new ArrayList<>(QuestManager.OBJECTIVE_TYPES).stream()
                    .filter(type -> type.startsWith(prefix)).toList();
        }

        if ("set".equals(action) && args.length <= 4) {
            final String prefix = prefixAt(args, 3);
            return QuestManager.EDITABLE_FIELDS.stream().filter(field -> field.startsWith(prefix)).toList();
        }

        return List.of();
    }

}
