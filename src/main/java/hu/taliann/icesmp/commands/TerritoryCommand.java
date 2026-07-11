package hu.taliann.icesmp.commands;

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
            "pos", "undo", "clearpoints", "points", "create", "circle",
            "setcapital", "remove", "list", "info", "show");
    private static final List<String> TYPE_NAMES = List.of(
            "faction", "protected-faction", "protected-city", "capital");

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
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pos", "point" -> handleAddPoint(sender);
            case "undo" -> handleUndo(sender);
            case "clearpoints", "clear" -> handleClearPoints(sender);
            case "points" -> handlePoints(sender);
            case "create" -> handleCreatePolygon(sender, args);
            case "circle" -> handleCircle(sender, args);
            case "setcapital" -> handleSetCapital(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender);
            case "show" -> handleShow(sender);
            default -> {
                sender.sendMessage(messageManager.get("territory-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
                sendHelp(sender);
            }
        }
    }

    // ==================== polygon boundary buffer ====================

    private void handleAddPoint(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        final int count = territoryManager.addPoint(player);
        sender.sendMessage(messageManager.get("territory-point-added",
                "&aHatárpont hozzáadva (&f%s.&a) itt: &f%s, %s &7— &f/territory create&7 a lezáráshoz.",
                count, player.getLocation().getBlockX(), player.getLocation().getBlockZ()));
    }

    private void handleUndo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
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
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        territoryManager.clearPoints(player.getUniqueId());
        sender.sendMessage(messageManager.get("territory-points-cleared", "&aHatárpont-puffer törölve."));
    }

    private void handlePoints(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
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
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(messageManager.get("territory-create-usage",
                    "&cHasználat: /territory create <típus> <frakció> <azonosító> [név...]"));
            return;
        }

        final TerritoryType type = parseType(sender, args[1]);
        if (type == null) {
            return;
        }
        final FactionType faction = parseFaction(sender, args[2]);
        if (faction == null) {
            return;
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

        final String name = args.length > 4
                ? String.join(" ", Arrays.copyOfRange(args, 4, args.length))
                : type.getDisplayName();
        final Territory territory = territoryManager.definePolygon(args[3], faction, name, type, world, points);
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
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(messageManager.get("territory-circle-usage",
                    "&cHasználat: /territory circle <típus> <frakció> <azonosító> <sugár> [név...]"));
            return;
        }

        final TerritoryType type = parseType(sender, args[1]);
        if (type == null) {
            return;
        }
        final FactionType faction = parseFaction(sender, args[2]);
        if (faction == null) {
            return;
        }
        final Integer radius = parseRadius(sender, args[4]);
        if (radius == null) {
            return;
        }

        final String name = args.length > 5
                ? String.join(" ", Arrays.copyOfRange(args, 5, args.length))
                : args[3];
        final Territory territory = territoryManager.define(args[3], faction, name, type, player.getLocation(), radius);
        sender.sendMessage(messageManager.get("territory-circle-success",
                "&aKör-terület kijelölve: &f%s &7(%s, %s, sugár: %s, középpont: %s, %s)",
                territory.name(), type.getDisplayName(), faction.getDisplayName(),
                territory.radius(), territory.x(), territory.z()));
    }

    private void handleSetCapital(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
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
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final Territory territory = territoryManager.getTerritoryAt(player.getLocation());
        if (territory == null) {
            sender.sendMessage(messageManager.get("territory-info-wilderness", "&7Itt nincs kijelölt terület (vadon)."));
            return;
        }
        sender.sendMessage(messageManager.get("territory-info-line",
                "&6Terület: &f%s &7| Típus: &f%s &7| Frakció: &f%s &7| Azonosító: &f%s",
                territory.name(), territory.type().getDisplayName(),
                territory.faction().getDisplayName(), territory.id()));
    }

    // ==================== particle preview ====================

    private void handleShow(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final List<int[]> buffer = territoryManager.getPoints(player.getUniqueId());
        final Territory here = territoryManager.getTerritoryAt(player.getLocation());
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

    /** Draws particle segments between consecutive vertices (closing the ring). */
    private void drawRing(final Player player, final World world, final List<int[]> ring,
                          final double y, final Particle particle, final boolean marks) {
        final int count = ring.size();
        for (int i = 0; i < count; i++) {
            final int[] a = ring.get(i);
            final int[] b = ring.get((i + 1) % count);
            if (count < 2) {
                player.spawnParticle(particle, new Location(world, a[0] + 0.5D, y, a[1] + 0.5D), 1, 0, 0, 0, 0);
                continue;
            }
            final double dist = Math.hypot(b[0] - a[0], b[1] - a[1]);
            final int steps = Math.max(1, (int) (dist / 2.0D));
            for (int s = 0; s <= steps; s++) {
                final double t = (double) s / steps;
                final double px = a[0] + (b[0] - a[0]) * t + 0.5D;
                final double pz = a[1] + (b[1] - a[1]) * t + 0.5D;
                player.spawnParticle(particle, new Location(world, px, y, pz), 1, 0, 0, 0, 0);
            }
        }
        if (marks) {
            for (final int[] point : ring) {
                player.spawnParticle(Particle.END_ROD, new Location(world, point[0] + 0.5D, y + 0.5D, point[1] + 0.5D), 1, 0, 0, 0, 0);
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
            player.spawnParticle(particle, new Location(world, px, y, pz), 1, 0, 0, 0, 0);
        }
    }

    // ==================== parsing helpers ====================

    private TerritoryType parseType(final CommandSender sender, final String raw) {
        final TerritoryType type = TerritoryType.fromInput(raw);
        if (type == null) {
            sender.sendMessage(messageManager.get("territory-type-unknown",
                    "&cIsmeretlen típus: &f%s &7(faction, protected-faction, protected-city, capital)", raw));
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
        sender.sendMessage(messageManager.get("territory-help-remove", "&e/territory remove <id> &7- Terület törlése."));
        sender.sendMessage(messageManager.get("territory-help-list", "&e/territory list &7- Területek listája."));
        sender.sendMessage(messageManager.get("territory-help-info", "&e/territory info &7- Az aktuális pozíció területe."));
        sender.sendMessage(messageManager.get("territory-help-show", "&e/territory show &7- Határrajz (puffer + aktuális terület)."));
        sender.sendMessage(messageManager.get("territory-help-types",
                "&7Típusok: &ffaction &7(csak tagok), &fprotected-faction&7/&fprotected-city &7(senki), &fcapital &7(főváros)."));
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
        if ("setcapital".equals(subcommand) && args.length == 2) {
            return factionSuggestions(prefixAt(args, 1));
        }
        if ("remove".equals(subcommand) && args.length == 2) {
            final String prefix = prefixAt(args, 1);
            return territoryManager.all().stream()
                    .map(Territory::id)
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private static List<String> factionSuggestions(final String prefix) {
        return Arrays.stream(FactionType.values())
                .map(faction -> faction.name().toLowerCase(Locale.ROOT))
                .filter(name -> name.startsWith(prefix))
                .toList();
    }

    private static String prefixAt(final String[] args, final int index) {
        return args.length > index ? args[index].toLowerCase(Locale.ROOT) : "";
    }
}
