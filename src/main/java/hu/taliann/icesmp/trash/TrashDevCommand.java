package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.security.HiddenDevAuthority;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;

/** Hidden catalog diagnostics; item mutation stays closed until the gameplay rollout. */
public final class TrashDevCommand {

    private static final int MAX_SUGGESTIONS = 40;

    private final JavaPlugin plugin;
    private final TrashCatalog catalog;
    private final TrashItemFactory itemFactory;
    private final TrashHistoryService historyService;
    private final TrashRecyclePool recyclePool;
    private final TrashLootService lootService;
    private final TrashArchaeologyService archaeology;
    private final TrashArchaeologyListener archaeologyListener;

    public TrashDevCommand(final JavaPlugin plugin, final TrashCatalog catalog,
                           final TrashItemFactory itemFactory,
                           final TrashHistoryService historyService,
                           final TrashRecyclePool recyclePool, final TrashLootService lootService,
                           final TrashArchaeologyService archaeology,
                           final TrashArchaeologyListener archaeologyListener) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
        this.itemFactory = java.util.Objects.requireNonNull(itemFactory, "itemFactory");
        this.historyService = java.util.Objects.requireNonNull(historyService, "historyService");
        this.recyclePool = java.util.Objects.requireNonNull(recyclePool, "recyclePool");
        this.lootService = java.util.Objects.requireNonNull(lootService, "lootService");
        this.archaeology = java.util.Objects.requireNonNull(archaeology, "archaeology");
        this.archaeologyListener = java.util.Objects.requireNonNull(
                archaeologyListener, "archaeologyListener");
    }

    public void execute(final CommandSender sender, final String[] args) {
        if (!HiddenDevAuthority.mayUseHiddenContent(sender)) return;
        if (args.length == 0) {
            sendUsage(sender);
            return;
        }
        if ("archaeology".equalsIgnoreCase(args[0])) {
            archaeology(sender, args);
            return;
        }
        if (!"trash".equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return;
        }
        if (args.length >= 2 && "catalog".equalsIgnoreCase(args[1])) {
            final TrashLootService.Telemetry telemetry = lootService.telemetry();
            sender.sendMessage(Component.text("Trash catalog: " + catalog.snapshot().size()
                    + " validált base identity; loot ecology aktív. generated="
                    + telemetry.generated() + ", recycled=" + telemetry.recycled()
                    + ", pool=" + telemetry.recyclePoolSize(), NamedTextColor.GRAY));
            return;
        }
        if (args.length >= 2 && "inspect".equalsIgnoreCase(args[1])) {
            inspect(sender, args);
            return;
        }
        if (args.length >= 2 && "give".equalsIgnoreCase(args[1])) {
            give(sender, args);
            return;
        }
        if (args.length >= 2 && "pool".equalsIgnoreCase(args[1])) {
            sender.sendMessage(Component.text("Trash recycle pool: " + recyclePool.pooledCount()
                    + " exact instance.", NamedTextColor.GRAY));
            return;
        }
        if (args.length >= 2 && "history".equalsIgnoreCase(args[1])) {
            history(sender);
            return;
        }
        if (args.length >= 2 && "state".equalsIgnoreCase(args[1])) {
            state(sender, args);
            return;
        }
        sendUsage(sender);
    }

    public List<String> suggest(final String[] args) {
        if (args.length == 0) return List.of("trash", "archaeology");
        if (args.length == 1) return matching(List.of("trash", "archaeology"), args[0]);
        if ("archaeology".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return matching(List.of("unlock", "setlevel", "addinsight", "reset",
                        "inspect", "force"), args[1]);
            }
            if (args.length == 3) {
                final String prefix = args[2].toLowerCase(Locale.ROOT);
                return org.bukkit.Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted().limit(MAX_SUGGESTIONS).toList();
            }
            if (args.length == 4 && "setlevel".equalsIgnoreCase(args[1])) {
                return matching(List.of("0", "1", "10", "25", "50"), args[3]);
            }
            return List.of();
        }
        if (!"trash".equalsIgnoreCase(args[0])) return List.of();
        if (args.length == 2) {
            return matching(List.of("catalog", "inspect", "give", "pool", "history", "state"), args[1]);
        }
        if (args.length == 3 && ("inspect".equalsIgnoreCase(args[1])
                || "give".equalsIgnoreCase(args[1]))) {
            final String prefix = args[2].toLowerCase(Locale.ROOT);
            return catalog.snapshot().keySet().stream().filter(id -> id.startsWith(prefix))
                    .sorted().limit(MAX_SUGGESTIONS).toList();
        }
        if (args.length == 3 && "state".equalsIgnoreCase(args[1])) {
            return matching(List.of("transform"), args[2]);
        }
        return List.of();
    }

    private void archaeology(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sendArchaeologyUsage(sender);
            return;
        }
        final String action = args[1].toLowerCase(Locale.ROOT);
        final Player target = org.bukkit.Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("A célpontnak online kell lennie.",
                    NamedTextColor.RED));
            return;
        }
        final String targetName = target.getName();
        final java.util.UUID targetId = target.getUniqueId();
        if ("inspect".equals(action)) {
            inspectArchaeology(sender, targetId, targetName);
            return;
        }
        if ("force".equals(action)) {
            archaeologyListener.forceInspection(target);
            sender.sendMessage(Component.text("Archaeology force inspection ütemezve: "
                    + targetName, NamedTextColor.GRAY));
            return;
        }

        final CompletionStage<TrashArchaeologyProfileStore.Profile> mutation;
        try {
            mutation = switch (action) {
                case "unlock" -> archaeology.unlock(targetId);
                case "reset" -> archaeology.reset(targetId);
                case "setlevel" -> archaeology.setLevel(targetId,
                        integerArgument(args, 3, 0, 50));
                case "addinsight" -> archaeology.addInsight(targetId,
                        longArgument(args, 3));
                default -> null;
            };
        } catch (final IllegalArgumentException invalid) {
            sendArchaeologyUsage(sender);
            return;
        }
        if (mutation == null) {
            sendArchaeologyUsage(sender);
            return;
        }
        mutation.whenComplete((profile, failure) -> reply(sender, () -> {
            if (failure != null || profile == null) {
                sender.sendMessage(Component.text("Archaeology profilmutáció sikertelen.",
                        NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text("Archaeology profil frissítve: "
                    + targetName + " | unlocked=" + profile.unlocked()
                    + " | level=" + profile.level() + " | insight=" + profile.insight(),
                    NamedTextColor.GRAY));
        }));
    }

    private void inspectArchaeology(final CommandSender sender, final java.util.UUID targetId,
                                    final String targetName) {
        final TrashArchaeologyProfileStore.Profile profile;
        try {
            profile = archaeology.profile(targetId);
        } catch (final RuntimeException unavailable) {
            sender.sendMessage(Component.text("A canonical Archaeology profil nem olvasható.",
                    NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("=== Rejtett Archaeology profile ===",
                NamedTextColor.GOLD));
        sender.sendMessage(Component.text("player=" + targetName + " | unlocked="
                + profile.unlocked() + " | level=" + profile.level() + " | insight="
                + profile.insight() + " | familiarity=" + profile.familiarity()
                + " | historical=" + profile.historicalInspections(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("families=" + profile.families() + " | domains="
                + profile.domains() + " | knowledge=" + profile.knowledge().size(),
                NamedTextColor.DARK_GRAY));
        profile.knowledge().stream().sorted().limit(MAX_SUGGESTIONS).forEach(signature ->
                sender.sendMessage(Component.text(signature, NamedTextColor.DARK_GRAY)));
        if (profile.knowledge().size() > MAX_SUGGESTIONS) {
            sender.sendMessage(Component.text("… további "
                    + (profile.knowledge().size() - MAX_SUGGESTIONS) + " signature",
                    NamedTextColor.DARK_GRAY));
        }
    }

    private static int integerArgument(final String[] args, final int index,
                                       final int min, final int max) {
        if (args.length <= index) throw new IllegalArgumentException("missing integer");
        final int value = Integer.parseInt(args[index]);
        if (value < min || value > max) throw new IllegalArgumentException("integer range");
        return value;
    }

    private static long longArgument(final String[] args, final int index) {
        if (args.length <= index) throw new IllegalArgumentException("missing long");
        final long value = Long.parseLong(args[index]);
        if (value < 0L) throw new IllegalArgumentException("negative long");
        return value;
    }

    private void reply(final CommandSender sender, final Runnable response) {
        if (sender instanceof Player player) {
            player.getScheduler().run(plugin, ignored -> response.run(), null);
        } else {
            org.bukkit.Bukkit.getGlobalRegionScheduler().run(
                    plugin, ignored -> response.run());
        }
    }

    private void give(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("A give route játékos feladót igényel.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3 || catalog.find(args[2]).isEmpty()) {
            sender.sendMessage(Component.text("Használat: /icesmp dev trash give <id> [1..64]",
                    NamedTextColor.RED));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (final NumberFormatException invalid) {
                amount = 0;
            }
        }
        if (amount < 1 || amount > 64) {
            sender.sendMessage(Component.text("A darabszám 1..64 lehet.", NamedTextColor.RED));
            return;
        }
        final org.bukkit.inventory.ItemStack item = itemFactory.create(args[2], amount);
        player.getInventory().addItem(item).values().forEach(overflow ->
                player.getWorld().dropItemNaturally(player.getLocation(), overflow));
        sender.sendMessage(Component.text("Trash identity kiadva: " + args[2] + " ×" + amount,
                NamedTextColor.GRAY));
    }

    private void inspect(final CommandSender sender, final String[] args) {
        final String id;
        if (args.length >= 3 && !args[2].isBlank()) {
            id = args[2];
        } else if (sender instanceof Player player) {
            id = itemFactory.idOf(player.getInventory().getItemInMainHand()).orElse("");
            if (id.isBlank()) {
                sender.sendMessage(Component.text("A főkézben nincs ismert Trash identity.", NamedTextColor.RED));
                return;
            }
        } else {
            sender.sendMessage(Component.text("Használat: /icesmp dev trash inspect <id>", NamedTextColor.RED));
            return;
        }
        final TrashDefinition definition = catalog.find(id).orElse(null);
        if (definition == null) {
            sender.sendMessage(Component.text("Ismeretlen Trash identity: " + id, NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("=== Rejtett Trash inspect ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("ID: " + definition.id() + " | név: "
                + definition.displayName(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("kind: " + definition.internalKind() + " | behavior: "
                + definition.behavior(), NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("material: " + definition.material() + " | model: "
                + definition.itemModel(), NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("source-bias: " + definition.sourceBias() + " | vendor: "
                + definition.vendorValue(), NamedTextColor.DARK_GRAY));
    }

    private void history(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("A history route játékos feladót igényel.", NamedTextColor.RED));
            return;
        }
        final org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();
        final TrashHistoryStore.Snapshot history;
        try {
            history = historyService.historyOf(held).orElse(null);
        } catch (final RuntimeException rejected) {
            sender.sendMessage(Component.text("A főkézben lévő Trash history authority érvénytelen.",
                    NamedTextColor.RED));
            return;
        }
        if (history == null) {
            sender.sendMessage(Component.text("A főkézben nincs individualizált Trash history.",
                    NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("=== Rejtett Trash history ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("instance=" + history.instanceId() + " | base="
                + history.baseId() + " | phase=" + history.phase() + " | revision="
                + history.revision() + " | owners=" + history.owners().size(), NamedTextColor.GRAY));
        for (final TrashHistoryStore.HistoryEntry event : history.events()) {
            final String actor = event.actor() == null ? "-" : event.actor().toString();
            final String detail = event.detail().isBlank() ? "" : " | " + event.detail();
            sender.sendMessage(Component.text("#" + event.revision() + " " + event.type()
                    + " | actor=" + actor + detail, NamedTextColor.DARK_GRAY));
        }
    }

    private void state(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("A state route játékos feladót igényel.", NamedTextColor.RED));
            return;
        }
        final org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();
        if (args.length >= 3 && "transform".equalsIgnoreCase(args[2])) {
            try {
                if (!historyService.transformMainHandOnSuccess(player)) {
                    sender.sendMessage(Component.text(
                            "Nincs authored transition, vagy nincs hely a singleton splithez.",
                            NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text("Trash lifecycle transition végrehajtva.",
                        NamedTextColor.GRAY));
            } catch (final RuntimeException rejected) {
                sender.sendMessage(Component.text("A Trash lifecycle transition elutasítva.",
                        NamedTextColor.RED));
            }
            return;
        }
        final String id = itemFactory.idOf(held).orElse("");
        final String phase = itemFactory.phaseOf(held).orElse("");
        if (id.isBlank() || phase.isBlank()) {
            sender.sendMessage(Component.text("A főkézben nincs ismert Trash state.", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("Trash state: base=" + id + " | phase=" + phase
                + " | tracked=" + historyService.isValidTracked(held)
                + " | success=" + itemFactory.successPhaseOf(held).orElse("-"),
                NamedTextColor.DARK_GRAY));
    }

    private static void sendUsage(final CommandSender sender) {
        sender.sendMessage(Component.text("Használat: /icesmp dev trash <catalog|inspect [id]|give <id> [amount]|pool|history|state [transform]>",
                NamedTextColor.RED));
        sendArchaeologyUsage(sender);
    }

    private static void sendArchaeologyUsage(final CommandSender sender) {
        sender.sendMessage(Component.text("Használat: /icesmp dev archaeology <unlock|setlevel|addinsight|reset|inspect|force> <player> [érték]",
                NamedTextColor.RED));
    }

    private static List<String> matching(final List<String> options, final String rawPrefix) {
        final String prefix = rawPrefix == null ? "" : rawPrefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
