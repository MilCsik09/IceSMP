package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Admin command for defining faction territory zones. Two shapes:
 * <ul>
 *   <li>CIRCLE — {@code /territory circle <típus> <frakció> <id> <sugár> [név...]}
 *       and the shortcut {@code /territory setcapital <frakció> <sugár> [név...]};</li>
 *   <li>POLYGON — walk the boundary placing points with {@code /territory pos}
 *       (undo / clear / points to manage the buffer), then seal it with
 *       {@code /territory create <típus> <frakció> <id> [név...]}.</li>
 * </ul>
 * Plus {@code remove}, {@code list}, {@code info} and {@code show} (particle preview).
 */
public final class TerritoryCommand implements BasicCommand {

    private static final String PERMISSION = "icesmp.admin.territory";
    private static final int MAX_RADIUS = 512;
    private static final int MAX_POINTS = 64;

    private static final List<String> SUBCOMMANDS = List.of(
            "pos", "wand", "undo", "clearpoints", "points", "create", "circle",
            "setcapital", "setspawn", "rename", "resize", "settype", "sety", "remove", "list", "info", "show", "tp",
            "dungeonchest", "dungeonboss");
    private static final List<String> TYPE_NAMES = List.of(
            "faction", "protected-faction", "protected-city", "capital", "doom-gate", "dungeon");

    private final JavaPlugin plugin;
    private final TerritoryManager territoryManager;
    private final MessageManager messageManager;

    public TerritoryCommand(final JavaPlugin plugin, final TerritoryManager territoryManager,
                            final MessageManager messageManager) {
        this.plugin = plugin;
        this.territoryManager = territoryManager;
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

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pos", "point" -> handleAddPoint(sender);
            case "wand", "palca" -> handleWand(sender);
            case "undo" -> handleUndo(sender);
            case "clearpoints", "clear" -> handleClearPoints(sender);
            case "points" -> handlePoints(sender);
            case "create" -> handleCreatePolygon(sender, args);
            case "circle" -> handleCircle(sender, args);
            case "setcapital" -> handleSetCapital(sender, args);
            case "setspawn" -> handleSetSpawn(sender, args);
            case "rename" -> handleRename(sender, args);
            case "resize" -> handleResize(sender, args);
            case "settype" -> handleSetType(sender, args);
            case "sety" -> handleSetY(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender);
            case "show" -> handleShow(sender, args);
            case "tp", "teleport" -> handleTeleport(sender, args);
            case "dungeonchest" -> handleDungeonChest(sender, args);
            case "dungeonboss" -> handleDungeonBoss(sender, args);
            default -> {
                sender.sendMessage(messageManager.get("territory-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
                sendHelp(sender);
            }
        }
    }

    // ==================== polygon boundary buffer ====================

    private void handleWand(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        final var wand = hu.taliann.icesmp.listeners.SelectionWandListener.createWand("territory");
        for (final var overflow : player.getInventory().addItem(wand).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
        sender.sendMessage(messageManager.get("territory-wand-given",
                "&a⚑ Határkijelölő pálca a kezedben: bal katt = pont, jobb = visszavon, SNEAK+jobb = előnézet."));
    }

    private void handleAddPoint(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        final int count = territoryManager.addPoint(player);
        sender.sendMessage(messageManager.get("territory-point-added",
                "&aHatárpont hozzáadva (&f%s.&a) itt: &f%s, %s &7— &f/territory create&7 a lezáráshoz.",
                count, player.getLocation().getBlockX(), player.getLocation().getBlockZ()));
    }

    private void handleUndo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        final int count = territoryManager.undoPoint(player.getUniqueId());
        if (count < 0) {
            sender.sendMessage(messageManager.get("territory-point-none", "&eNincs visszavonható határpont."));
            return;
        }
        sender.sendMessage(messageManager.get("territory-point-undone", "&aUtolsó határpont törölve (&f%s&a maradt).", count));
    }

    private void handleClearPoints(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        territoryManager.clearPoints(player.getUniqueId());
        sender.sendMessage(messageManager.get("territory-points-cleared", "&aHatárpont-puffer törölve."));
    }

    private void handlePoints(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        final List<int[]> points = territoryManager.getPoints(player.getUniqueId());
        if (points.isEmpty()) {
            sender.sendMessage(messageManager.get("territory-points-empty",
                    "&eNincs kijelölt határpont. &7Használd a &f/territory pos&7 parancsot."));
            return;
        }
        sender.sendMessage(messageManager.get("territory-points-header",
                "&6Határpontok (&f%s&6):", points.size()));
        int index = 1;
        for (final int[] point : points) {
            sender.sendMessage(messageManager.get("territory-points-line", "&7 %s. &f%s, %s", index++, point[0], point[1]));
        }
    }

    // ==================== zone creation ====================

    private void handleCreatePolygon(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("territory-create-usage",
                    "&cHasználat: /territory create <típus> <frakció> <azonosító> [név...] &7(doom-gate: a frakció elhagyható)"));
            return;
        }

        final TerritoryType type = parseType(sender, args[1]);
        if (type == null) {
            return;
        }
        // Kárhozat-zóna: frakció-semleges senkiföldje — a <frakció> argumentum elhagyható
        // (/territory create doom-gate <id> [név...]); belső tulajdonosként NEUTRAL-t jegyzünk.
        final FactionType faction;
        final int idIndex;
        if (type == TerritoryType.DOOM_GATE && FactionType.fromInput(args[2]) == null) {
            faction = FactionType.NEUTRAL;
            idIndex = 2;
        } else {
            if (args.length < 4) {
                sender.sendMessage(messageManager.get("territory-create-usage",
                        "&cHasználat: /territory create <típus> <frakció> <azonosító> [név...] &7(doom-gate: a frakció elhagyható)"));
                return;
            }
            faction = parseFaction(sender, args[2]);
            if (faction == null) {
                return;
            }
            idIndex = 3;
        }

        final List<int[]> points = territoryManager.getPoints(player.getUniqueId());
        if (points.size() < 3) {
            sender.sendMessage(messageManager.get("territory-create-too-few",
                    "&cLegalább 3 határpont kell (&f/territory pos&c). Jelenleg: &f%s", points.size()));
            return;
        }
        if (points.size() > MAX_POINTS) {
            sender.sendMessage(messageManager.get("territory-create-too-many",
                    "&cTúl sok határpont (max %s).", MAX_POINTS));
            return;
        }
        final String world = territoryManager.getPointsWorld(player.getUniqueId());
        if (world == null || !world.equals(player.getWorld().getName())) {
            sender.sendMessage(messageManager.get("territory-create-cross-world",
                    "&cA határpontok másik világban vannak — állj vissza abba a világba, vagy töröld őket."));
            return;
        }
        if (isSelfIntersecting(points)) {
            sender.sendMessage(messageManager.get("territory-create-self-intersect",
                    "&cA határvonal önmagát keresztezi — igazítsd a pontokat, hogy a fal ne messe át saját magát."));
            return;
        }

        final String name = args.length > idIndex + 1
                ? String.join(" ", Arrays.copyOfRange(args, idIndex + 1, args.length))
                : type.getDisplayName();
        final Territory territory = territoryManager.definePolygon(args[idIndex], faction, name, type, world, points);
        if (territory == null) {
            sender.sendMessage(messageManager.get("territory-create-failed", "&cA terület létrehozása nem sikerült."));
            return;
        }
        territoryManager.clearPoints(player.getUniqueId());
        sender.sendMessage(messageManager.get("territory-create-success",
                "&aPoligon-terület kijelölve: &f%s &7(%s, %s, %s pont, középpont: %s, %s)",
                territory.name(), type.getDisplayName(), faction.getDisplayName(),
                points.size(), territory.x(), territory.z()));
    }

    private void handleCircle(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(messageManager.get("territory-circle-usage",
                    "&cHasználat: /territory circle <típus> <frakció> <azonosító> <sugár> [név...] &7(doom-gate: a frakció elhagyható)"));
            return;
        }

        final TerritoryType type = parseType(sender, args[1]);
        if (type == null) {
            return;
        }
        // Kárhozat-zóna: frakció-semleges — a <frakció> elhagyható
        // (/territory circle doom-gate <id> <sugár> [név...]).
        final FactionType faction;
        final int idIndex;
        if (type == TerritoryType.DOOM_GATE && FactionType.fromInput(args[2]) == null) {
            faction = FactionType.NEUTRAL;
            idIndex = 2;
        } else {
            if (args.length < 5) {
                sender.sendMessage(messageManager.get("territory-circle-usage",
                        "&cHasználat: /territory circle <típus> <frakció> <azonosító> <sugár> [név...] &7(doom-gate: a frakció elhagyható)"));
                return;
            }
            faction = parseFaction(sender, args[2]);
            if (faction == null) {
                return;
            }
            idIndex = 3;
        }
        if (args.length < idIndex + 2) {
            sender.sendMessage(messageManager.get("territory-circle-usage",
                    "&cHasználat: /territory circle <típus> <frakció> <azonosító> <sugár> [név...] &7(doom-gate: a frakció elhagyható)"));
            return;
        }
        final Integer radius = parseRadius(sender, args[idIndex + 1]);
        if (radius == null) {
            return;
        }

        final String name = args.length > idIndex + 2
                ? String.join(" ", Arrays.copyOfRange(args, idIndex + 2, args.length))
                : args[idIndex];
        final Territory territory = territoryManager.define(args[idIndex], faction, name, type, player.getLocation(), radius);
        sender.sendMessage(messageManager.get("territory-circle-success",
                "&aKör-terület kijelölve: &f%s &7(%s, %s, sugár: %s, középpont: %s, %s)",
                territory.name(), type.getDisplayName(), faction.getDisplayName(),
                territory.radius(), territory.x(), territory.z()));
    }

    private void handleSetCapital(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("territory-setcapital-usage",
                    "&cHasználat: /territory setcapital <frakció> <sugár> [név...]"));
            return;
        }

        final FactionType faction = parseFaction(sender, args[1]);
        if (faction == null) {
            return;
        }
        final Integer radius = parseRadius(sender, args[2]);
        if (radius == null) {
            return;
        }

        final String name = args.length > 3
                ? String.join(" ", Arrays.copyOfRange(args, 3, args.length))
                : faction.getDisplayName() + " főváros";
        final Territory territory = territoryManager.define(
                faction.name().toLowerCase(Locale.ROOT) + "-capital",
                faction, name, TerritoryType.CAPITAL, player.getLocation(), radius);
        sender.sendMessage(messageManager.get("territory-setcapital-success",
                "&aFőváros kijelölve: &f%s &7(%s, sugár: %s, középpont: %s, %s)",
                territory.name(), faction.getDisplayName(), territory.radius(), territory.x(), territory.z()));
    }

    /**
     * Sets a faction's kingdom spawn to the admin's EXACT standing position (full Y + view
     * direction) — no highest-block guessing. Used by first-join placement, the faction-join
     * teleport and the faction respawn.
     */
    private void handleSetSpawn(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("territory-setspawn-usage",
                    "&cHasználat: /territory setspawn <frakció> — az aktuális pozíciód lesz a spawn."));
            return;
        }

        final FactionType faction = parseFaction(sender, args[1]);
        if (faction == null) {
            return;
        }

        territoryManager.setFactionSpawn(faction, player.getLocation());
        sender.sendMessage(messageManager.get("territory-setspawn-success",
                "&aKirályság-spawn beállítva: &f%s &7(%s: %.1f, %.1f, %.1f)",
                faction.getDisplayName(), player.getWorld().getName(),
                player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ()));
    }

    // ==================== zone edits ====================

    private void handleRename(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("territory-rename-usage",
                    "&cHasználat: /territory rename <azonosító> <új név...>"));
            return;
        }
        final String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        final Territory territory = territoryManager.rename(args[1], name);
        if (territory == null) {
            sender.sendMessage(messageManager.get("territory-unknown", "&cIsmeretlen terület: &f%s", args[1]));
            return;
        }
        sender.sendMessage(messageManager.get("territory-rename-success",
                "&aTerület átnevezve: &f%s &7(%s)", territory.name(), territory.id()));
    }

    private void handleResize(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("territory-resize-usage",
                    "&cHasználat: /territory resize <azonosító> <sugár>"));
            return;
        }
        final Integer radius = parseRadius(sender, args[2]);
        if (radius == null) {
            return;
        }
        final Territory existing = territoryManager.getById(args[1]);
        if (existing == null) {
            sender.sendMessage(messageManager.get("territory-unknown", "&cIsmeretlen terület: &f%s", args[1]));
            return;
        }
        if (existing.isPolygon()) {
            sender.sendMessage(messageManager.get("territory-resize-polygon",
                    "&cPoligon-területet nem lehet sugárral átméretezni — használj /territory create-et új pontokkal."));
            return;
        }
        final Territory territory = territoryManager.resize(args[1], radius);
        sender.sendMessage(messageManager.get("territory-resize-success",
                "&aÚj sugár: &f%s &7(%s)", territory.radius(), territory.id()));
    }

    private void handleSetType(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("territory-settype-usage",
                    "&cHasználat: /territory settype <azonosító> <típus>"));
            return;
        }
        final TerritoryType type = parseType(sender, args[2]);
        if (type == null) {
            return;
        }
        final Territory territory = territoryManager.setType(args[1], type);
        if (territory == null) {
            sender.sendMessage(messageManager.get("territory-unknown", "&cIsmeretlen terület: &f%s", args[1]));
            return;
        }
        sender.sendMessage(messageManager.get("territory-settype-success",
                "&aÚj típus: &f%s &7(%s)", type.getDisplayName(), territory.id()));
    }

    private void handleSetY(final CommandSender sender, final String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messageManager.get("territory-sety-usage",
                    "&cHasználat: /territory sety <azonosító> <minY|~> <maxY|~> &7(a ~ = korlátlan)"));
            return;
        }
        if (territoryManager.getById(args[1]) == null) {
            sender.sendMessage(messageManager.get("territory-unknown", "&cIsmeretlen terület: &f%s", args[1]));
            return;
        }
        final Integer minY = parseYBound(sender, args[2], Territory.NO_MIN_Y);
        if (minY == null) {
            return;
        }
        final Integer maxY = parseYBound(sender, args[3], Territory.NO_MAX_Y);
        if (maxY == null) {
            return;
        }
        final Territory territory = territoryManager.setYBounds(args[1], minY, maxY);
        if (territory == null) {
            sender.sendMessage(messageManager.get("territory-unknown", "&cIsmeretlen terület: &f%s", args[1]));
            return;
        }
        final String range = (territory.minY() == Territory.NO_MIN_Y ? "−∞" : String.valueOf(territory.minY()))
                + " … " + (territory.maxY() == Territory.NO_MAX_Y ? "+∞" : String.valueOf(territory.maxY()));
        sender.sendMessage(messageManager.get("territory-sety-success",
                "&aMagassági sáv: &f%s &7(%s)", range, territory.id()));
    }

    /** Parses a Y bound; the literal {@code ~} means "unbounded" (returns the sentinel). */
    private Integer parseYBound(final CommandSender sender, final String raw, final int unbounded) {
        if ("~".equals(raw) || "*".equalsIgnoreCase(raw)) {
            return unbounded;
        }
        try {
            return Integer.parseInt(raw);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("invalid-amount", "&cÉrvénytelen szám (használd a ~ jelet a korlátlanhoz)."));
            return null;
        }
    }

    private void handleRemove(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("territory-remove-usage", "&cHasználat: /territory remove <azonosító>"));
            return;
        }
        if (!territoryManager.remove(args[1])) {
            sender.sendMessage(messageManager.get("territory-unknown", "&cIsmeretlen terület: &f%s", args[1]));
            return;
        }
        sender.sendMessage(messageManager.get("territory-remove-success", "&aTerület törölve: &f%s", args[1]));
    }

    private void handleList(final CommandSender sender) {
        final Collection<Territory> territories = territoryManager.all();
        if (territories.isEmpty()) {
            sender.sendMessage(messageManager.get("territory-list-empty", "&eNincsenek kijelölt területek."));
            return;
        }

        sender.sendMessage(messageManager.get("territory-list-header", "&6Kijelölt területek:"));
        for (final Territory territory : territories) {
            sender.sendMessage(messageManager.get("territory-list-line",
                    "&e%s &7(%s) | %s | %s | %s | középpont: %s, %s (%s)",
                    territory.id(),
                    territory.name(),
                    territory.type().getDisplayName(),
                    territory.faction().getDisplayName(),
                    territory.isPolygon() ? "poligon (" + territory.polygon().size() + " pont)" : "sugár: " + territory.radius(),
                    territory.x(),
                    territory.z(),
                    territory.world()));
        }
    }

    private void handleInfo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final Territory territory = territoryManager.getTerritoryAt(player.getLocation());
        if (territory == null) {
            sender.sendMessage(messageManager.get("territory-info-wilderness", "&7Itt nincs kijelölt terület (vadon)."));
            return;
        }
        sender.sendMessage(messageManager.get("territory-info-line",
                "&6Terület: &f%s &7| Típus: &f%s &7| Frakció: &f%s &7| Azonosító: &f%s &7| Magasság: &f%s",
                territory.name(), territory.type().getDisplayName(),
                territory.faction().getDisplayName(), territory.id(), describeY(territory)));
    }

    private static String describeY(final Territory territory) {
        if (!territory.hasYBounds()) {
            return "teljes";
        }
        return (territory.minY() == Territory.NO_MIN_Y ? "−∞" : String.valueOf(territory.minY()))
                + ".." + (territory.maxY() == Territory.NO_MAX_Y ? "+∞" : String.valueOf(territory.maxY()));
    }

    // ==================== particle preview ====================

    private void handleShow(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final List<int[]> buffer = territoryManager.getPoints(player.getUniqueId());
        final Territory here;
        if (args.length >= 2) {
            here = territoryManager.getById(args[1]);
            if (here == null) {
                sender.sendMessage(messageManager.get("territory-unknown", "&cIsmeretlen terület: &f%s", args[1]));
                return;
            }
            if (!here.world().equals(player.getWorld().getName())) {
                sender.sendMessage(messageManager.get("territory-show-cross-world",
                        "&cEz a zóna másik világban van (&f%s&c) — a rajz csak abban a világban látszik.", here.world()));
                return;
            }
        } else {
            here = territoryManager.getTerritoryAt(player.getLocation());
        }
        if (buffer.isEmpty() && here == null) {
            sender.sendMessage(messageManager.get("territory-show-nothing",
                    "&eNincs mit megjeleníteni (nincs határpont, és nem állsz területen)."));
            return;
        }

        final World world = player.getWorld();
        final int[] frames = {0};
        player.getScheduler().runAtFixedRate(plugin, task -> {
            if (frames[0]++ >= 8 || !player.isOnline()) {
                task.cancel();
                return;
            }
            final double y = player.getLocation().getY() + 1.2D;
            if (!buffer.isEmpty()) {
                drawRing(player, world, buffer, y, Particle.COMPOSTER, true);
            }
            if (here != null) {
                if (here.isPolygon()) {
                    drawRing(player, world, here.polygon(), y,
                            here.type().isProtectedZone() ? Particle.FLAME : Particle.HAPPY_VILLAGER, false);
                } else {
                    drawCircle(player, world, here.x(), here.z(), here.radius(), y,
                            here.type().isProtectedZone() ? Particle.FLAME : Particle.HAPPY_VILLAGER);
                }
            }
        }, null, 1L, 20L);
        sender.sendMessage(messageManager.get("territory-show-started", "&aHatárrajz megjelenítve (8 mp)."));
    }

    /**
     * Teleports the admin to a zone's centre. Folia-safe: the destination chunk is
     * loaded async, the highest-block probe runs on THAT chunk's region thread, and
     * {@code teleportAsync} is dispatched from there; the viewer's facing is captured
     * up front (we are on the player's own region thread in the command).
     */
    private volatile hu.taliann.icesmp.managers.DungeonLootService dungeonLootService;

    public void setDungeonLootService(final hu.taliann.icesmp.managers.DungeonLootService dungeonLootService) {
        this.dungeonLootService = dungeonLootService;
    }

    /** A nézett láda/hordó regisztrálása kazamata-kincsesládának (újra kiadva: törlés). */
    private void handleDungeonChest(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        final hu.taliann.icesmp.managers.DungeonLootService loot = this.dungeonLootService;
        if (loot == null) {
            return;
        }
        final org.bukkit.block.Block target = player.getTargetBlockExact(5);
        if (target == null || (target.getType() != org.bukkit.Material.CHEST
                && target.getType() != org.bukkit.Material.TRAPPED_CHEST
                && target.getType() != org.bukkit.Material.BARREL)) {
            sender.sendMessage(messageManager.get("territory-dungeonchest-no-target",
                    "&cNézz egy ládára vagy hordóra (max 5 blokk)."));
            return;
        }
        final String table = args.length >= 2 ? args[1] : "kazamata";
        final boolean added = loot.toggleChest(target.getLocation(), table);
        sender.sendMessage(added
                ? messageManager.get("territory-dungeonchest-added",
                        "&aKincsesláda regisztrálva (&f%s&a tábla) — fejenkénti, heti zsákmány.", table)
                : messageManager.get("territory-dungeonchest-removed", "&eKincsesláda-regisztráció törölve."));
    }

    /** Mini-boss spawn-pont kijelölése az admin pozícióján: /territory dungeonboss <zóna> [tábla] | clear <zóna>. */
    private void handleDungeonBoss(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        final hu.taliann.icesmp.managers.DungeonLootService loot = this.dungeonLootService;
        if (loot == null || args.length < 2) {
            sender.sendMessage(messageManager.get("territory-dungeonboss-usage",
                    "&cHasználat: /territory dungeonboss <zóna-id> [tábla] | clear <zóna-id>"));
            return;
        }
        if ("clear".equalsIgnoreCase(args[1])) {
            if (args.length < 3 || !loot.clearBossSpawn(args[2])) {
                sender.sendMessage(messageManager.get("territory-dungeonboss-none", "&cNincs ilyen boss-kijelölés."));
            } else {
                sender.sendMessage(messageManager.get("territory-dungeonboss-cleared", "&eBoss-kijelölés törölve."));
            }
            return;
        }
        final Territory zone = territoryManager.getById(args[1]);
        if (zone == null || zone.type() != hu.taliann.icesmp.data.TerritoryType.DUNGEON) {
            sender.sendMessage(messageManager.get("territory-dungeonboss-not-dungeon",
                    "&cIsmeretlen vagy nem DUNGEON típusú zóna: &f%s", args[1]));
            return;
        }
        final String table = args.length >= 3 ? args[2] : "kazamata-boss";
        loot.setBossSpawn(zone.id(), player.getLocation(), table);
        sender.sendMessage(messageManager.get("territory-dungeonboss-set",
                "&aMini-boss spawn kijelölve itt (&f%s&a zóna, &f%s&a tábla) — hangolás: dungeon.minibosses.%s.*",
                zone.id(), table, zone.id().toLowerCase(java.util.Locale.ROOT)));
    }

    private void handleTeleport(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("territory-tp-usage", "&cHasználat: /territory tp <azonosító>"));
            return;
        }
        final Territory zone = territoryManager.getById(args[1]);
        if (zone == null) {
            sender.sendMessage(messageManager.get("territory-unknown", "&cIsmeretlen terület: &f%s", args[1]));
            return;
        }
        final World world = org.bukkit.Bukkit.getWorld(zone.world());
        if (world == null) {
            sender.sendMessage(messageManager.get("territory-tp-world-missing",
                    "&cA zóna világa (&f%s&c) nincs betöltve.", zone.world()));
            return;
        }
        final float yaw = player.getLocation().getYaw();
        final float pitch = player.getLocation().getPitch();
        final int cx = zone.x() >> 4;
        final int cz = zone.z() >> 4;
        world.getChunkAtAsync(cx, cz).thenRun(() ->
                org.bukkit.Bukkit.getRegionScheduler().run(plugin, world, cx, cz, task -> {
                    final int baseY = zone.minY() != Territory.NO_MIN_Y
                            ? zone.minY() + 1
                            : world.getHighestBlockYAt(zone.x(), zone.z()) + 1;
                    player.teleportAsync(new Location(world, zone.x() + 0.5D, baseY, zone.z() + 0.5D, yaw, pitch));
                }));
        sender.sendMessage(messageManager.get("territory-tp-success",
                "&aTeleportálás a(z) &f%s &azónához (&f%s, %s&a)…", zone.name(), zone.x(), zone.z()));
    }

    /**
     * Draws particle segments between consecutive vertices (closing the ring).
     * TEREP-KÖVETŐ: minden pont a saját oszlopa legfelső blokkja fölé kerül
     * (dombon-völgyön át a talajt követi); a kapott {@code y} csak fallback, ha a pont chunkja
     * nem a hívó régiójáé (ParticleUtil.markerY Folia-guardja).
     */
    private void drawRing(final Player player, final World world, final List<int[]> ring,
                          final double y, final Particle particle, final boolean marks) {
        final int count = ring.size();
        for (int i = 0; i < count; i++) {
            final int[] a = ring.get(i);
            final int[] b = ring.get((i + 1) % count);
            if (count < 2) {
                drawGroundPoint(player, world, a[0] + 0.5D, a[1] + 0.5D, y, particle);
                continue;
            }
            final double dist = Math.hypot(b[0] - a[0], b[1] - a[1]);
            final int steps = Math.max(1, (int) (dist / 2.0D));
            for (int s = 0; s <= steps; s++) {
                final double t = (double) s / steps;
                final double px = a[0] + (b[0] - a[0]) * t + 0.5D;
                final double pz = a[1] + (b[1] - a[1]) * t + 0.5D;
                drawGroundPoint(player, world, px, pz, y, particle);
            }
        }
        if (marks) {
            for (final int[] point : ring) {
                final double markY = hu.taliann.icesmp.utils.ParticleUtil.markerY(
                        world, point[0], point[1], y) + 0.5D;
                player.spawnParticle(Particle.END_ROD,
                        new Location(world, point[0] + 0.5D, markY, point[1] + 0.5D), 1, 0, 0, 0, 0);
            }
        }
    }

    private void drawCircle(final Player player, final World world, final int cx, final int cz,
                            final int radius, final double y, final Particle particle) {
        final int segments = Math.min(120, Math.max(24, radius * 2));
        for (int i = 0; i < segments; i++) {
            final double angle = 2 * Math.PI * i / segments;
            final double px = cx + Math.cos(angle) * radius + 0.5D;
            final double pz = cz + Math.sin(angle) * radius + 0.5D;
            drawGroundPoint(player, world, px, pz, y, particle);
        }
    }

    /** Egy terep-követő határ-pont (a közös ParticleUtil.markerY magasság-forrással). */
    private void drawGroundPoint(final Player player, final World world, final double px, final double pz,
                                 final double fallbackY, final Particle particle) {
        final double gy = hu.taliann.icesmp.utils.ParticleUtil.markerY(
                world, (int) Math.floor(px), (int) Math.floor(pz), fallbackY);
        player.spawnParticle(particle, new Location(world, px, gy, pz), 1, 0, 0, 0, 0);
    }

    // ==================== parsing helpers ====================

    private TerritoryType parseType(final CommandSender sender, final String raw) {
        final TerritoryType type = TerritoryType.fromInput(raw);
        if (type == null) {
            sender.sendMessage(messageManager.get("territory-type-unknown",
                    "&cIsmeretlen típus: &f%s &7(faction, protected-faction, protected-city, capital, doom-gate, dungeon)", raw));
        }
        return type;
    }

    private FactionType parseFaction(final CommandSender sender, final String raw) {
        final FactionType faction = FactionType.fromInput(raw);
        if (faction == null) {
            sender.sendMessage(messageManager.get("faction-unknown", "&cIsmeretlen frakció: &f%s", raw));
        }
        return faction;
    }

    private Integer parseRadius(final CommandSender sender, final String rawRadius) {
        try {
            final int radius = Integer.parseInt(rawRadius);
            if (radius <= 0) {
                sender.sendMessage(messageManager.get("amount-must-be-positive", "&cAz összegnek pozitívnak kell lennie."));
                return null;
            }
            if (radius > MAX_RADIUS) {
                sender.sendMessage(messageManager.get("territory-radius-too-large",
                        "&cTúl nagy sugár (max " + MAX_RADIUS + ")."));
                return null;
            }
            return radius;
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("invalid-amount", "&cÉrvénytelen összeg."));
            return null;
        }
    }

    /**
     * Whether the closed vertex ring crosses itself (any two non-adjacent edges
     * intersect). Rejects tangled boundaries before they become a zone.
     */
    private static boolean isSelfIntersecting(final List<int[]> ring) {
        final int n = ring.size();
        for (int i = 0; i < n; i++) {
            final int[] a1 = ring.get(i);
            final int[] a2 = ring.get((i + 1) % n);
            for (int j = i + 1; j < n; j++) {
                // Skip edges that share a vertex (adjacent, or the wrap-around pair).
                if (j == i || (i + 1) % n == j || (j + 1) % n == i) {
                    continue;
                }
                final int[] b1 = ring.get(j);
                final int[] b2 = ring.get((j + 1) % n);
                if (segmentsIntersect(a1, a2, b1, b2)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean segmentsIntersect(final int[] p1, final int[] p2, final int[] p3, final int[] p4) {
        final long d1 = cross(p3, p4, p1);
        final long d2 = cross(p3, p4, p2);
        final long d3 = cross(p1, p2, p3);
        final long d4 = cross(p1, p2, p4);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    /** Cross product of (b-a)×(c-a) — sign gives the orientation of c about a→b. */
    private static long cross(final int[] a, final int[] b, final int[] c) {
        return (long) (b[0] - a[0]) * (c[1] - a[1]) - (long) (b[1] - a[1]) * (c[0] - a[0]);
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("territory-help-header", "&6/territory &7- Elérhető parancsok (Admin):"));
        sender.sendMessage(messageManager.get("territory-help-pos",
                "&e/territory pos &7- Határpont hozzáadása a pozíciódnál (poligon)."));
        sender.sendMessage(messageManager.get("territory-help-undo",
                "&e/territory undo &7| &eclearpoints &7| &epoints &7- Puffer kezelése."));
        sender.sendMessage(messageManager.get("territory-help-create",
                "&e/territory create <típus> <frakció> <id> [név...] &7- Poligon-terület lezárása."));
        sender.sendMessage(messageManager.get("territory-help-circle",
                "&e/territory circle <típus> <frakció> <id> <sugár> [név...] &7- Kör-terület."));
        sender.sendMessage(messageManager.get("territory-help-setcapital",
                "&e/territory setcapital <frakció> <sugár> [név...] &7- Főváros (kör)."));
        sender.sendMessage(messageManager.get("territory-help-setspawn",
                "&e/territory setspawn <frakció> &7- Királyság-spawn az aktuális pozíciódra."));
        sender.sendMessage(messageManager.get("territory-help-edit",
                "&e/territory rename|resize|settype|sety <id> ... &7- Meglévő zóna módosítása."));
        sender.sendMessage(messageManager.get("territory-help-remove", "&e/territory remove <id> &7- Terület törlése."));
        sender.sendMessage(messageManager.get("territory-help-list", "&e/territory list &7- Területek listája."));
        sender.sendMessage(messageManager.get("territory-help-info", "&e/territory info &7- Az aktuális pozíció területe."));
        sender.sendMessage(messageManager.get("territory-help-show", "&e/territory show [id] &7- Határrajz (puffer / aktuális / megadott zóna)."));
        sender.sendMessage(messageManager.get("territory-help-tp", "&e/territory tp <id> &7- Teleportálás a zóna középpontjához."));
        sender.sendMessage(messageManager.get("territory-help-types",
                "&7Típusok: &ffaction &7(csak tagok), &fprotected-faction&7/&fprotected-city &7(senki), &fcapital &7(főváros), &fdoom-gate &7(PvPvE senkiföldje), &fdungeon &7(kazamata, kulcs+lockout)."));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }

        final String subcommand = prefixAt(args, 0);
        final boolean subcommandComplete = SUBCOMMANDS.contains(subcommand);
        if (args.length == 0 || (args.length == 1 && !subcommandComplete)) {
            return SUBCOMMANDS.stream().filter(option -> option.startsWith(subcommand)).toList();
        }

        // create/circle: arg1 = type, arg2 = faction. setcapital: arg1 = faction.
        if (("create".equals(subcommand) || "circle".equals(subcommand)) && args.length == 2) {
            return TYPE_NAMES.stream().filter(name -> name.startsWith(prefixAt(args, 1))).toList();
        }
        if (("create".equals(subcommand) || "circle".equals(subcommand)) && args.length == 3) {
            return factionSuggestions(prefixAt(args, 2));
        }
        if (("setcapital".equals(subcommand) || "setspawn".equals(subcommand)) && args.length == 2) {
            return factionSuggestions(prefixAt(args, 1));
        }
        // Edit/remove/tp/show commands take an existing zone id at arg1.
        if (args.length == 2 && List.of("remove", "rename", "resize", "settype", "sety", "tp", "show").contains(subcommand)) {
            return idSuggestions(prefixAt(args, 1));
        }
        if ("settype".equals(subcommand) && args.length == 3) {
            return TYPE_NAMES.stream().filter(name -> name.startsWith(prefixAt(args, 2))).toList();
        }
        return List.of();
    }

    private List<String> idSuggestions(final String prefix) {
        return territoryManager.all().stream()
                .map(Territory::id)
                .filter(id -> id.startsWith(prefix))
                .toList();
    }

    private static List<String> factionSuggestions(final String prefix) {
        return Arrays.stream(FactionType.values())
                .map(faction -> faction.name().toLowerCase(Locale.ROOT))
                .filter(name -> name.startsWith(prefix))
                .toList();
    }

}
