package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.managers.ParkourManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /parkour — run timed trial courses; admins set them up.
 */
public final class ParkourCommand implements BasicCommand {

    private static final String ADMIN_PERMISSION = "icesmp.admin.parkour";

    private final ParkourManager parkourManager;
    private final MessageManager messageManager;

    public ParkourCommand(final ParkourManager parkourManager, final MessageManager messageManager) {
        this.parkourManager = parkourManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> {
                if (args.length < 2) {
                    player.sendMessage(messageManager.get("parkour-start-usage", "&cHasználat: /parkour start <pálya>"));
                    return;
                }
                final String error = parkourManager.start(player, args[1]);
                player.sendMessage(error != null
                        ? messageManager.get(error, "&cEz a pálya nincs kész (hiányzik a start vagy a cél).")
                        : messageManager.get("parkour-started", "&aFutás indul! Érj el a célba."));
            }
            case "setstart" -> {
                if (notAdmin(player) || requireId(player, args)) {
                    return;
                }
                parkourManager.setStart(args[1], player, args.length >= 3 ? args[2] : null);
                player.sendMessage(messageManager.get("parkour-setstart", "&aStart beállítva: &f%s", args[1]));
            }
            case "setfinish" -> {
                if (notAdmin(player) || requireId(player, args)) {
                    return;
                }
                // Clamp: a negative/zero radius would make the finish untouchable, a negative reward would deduct.
                final double radius = Math.max(0.5D, args.length >= 3 ? parseDouble(args[2], 2.5D) : 2.5D);
                final long reward = Math.max(0L, args.length >= 4 ? parseLong(args[3], 100L) : 100L);
                parkourManager.setFinish(args[1], player, radius, reward);
                player.sendMessage(messageManager.get("parkour-setfinish", "&aCél beállítva: &f%s &7(sugár %s, jutalom %s)", args[1], radius, reward));
            }
            case "remove" -> {
                if (notAdmin(player) || requireId(player, args)) {
                    return;
                }
                player.sendMessage(parkourManager.remove(args[1])
                        ? messageManager.get("parkour-removed", "&aPálya törölve: &f%s", args[1])
                        : messageManager.get("parkour-unknown", "&cIsmeretlen pálya: &f%s", args[1]));
            }
            default -> {
                player.sendMessage(messageManager.get("parkour-list-header", "&6Parkour-pályák:"));
                if (parkourManager.getCourseIds().isEmpty()) {
                    player.sendMessage(messageManager.get("parkour-list-empty", "&7Még nincs pálya."));
                }
                for (final String id : parkourManager.getCourseIds()) {
                    player.sendMessage(messageManager.get("parkour-list-line", "&e- %s %s", id,
                            parkourManager.isReady(id) ? "&7(kész)" : "&8(beállítás alatt)"));
                }
            }
        }
    }

    private boolean notAdmin(final Player player) {
        if (player.hasPermission(ADMIN_PERMISSION)) {
            return false;
        }
        player.sendMessage(messageManager.get("messages.permission-denied", "&cNincs jogosultságod erre a parancsra."));
        return true;
    }

    private boolean requireId(final Player player, final String[] args) {
        if (args.length >= 2) {
            return false;
        }
        player.sendMessage(messageManager.get("parkour-id-usage", "&cAdj meg egy pálya-azonosítót."));
        return true;
    }

    private double parseDouble(final String raw, final double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (final NumberFormatException exception) {
            return fallback;
        }
    }

    private long parseLong(final String raw, final long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (final NumberFormatException exception) {
            return fallback;
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        final List<String> options = new ArrayList<>(List.of("list", "start"));
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            options.addAll(List.of("setstart", "setfinish", "remove"));
        }
        final String first = prefixAt(args, 0);
        final boolean firstComplete = options.contains(first);

        // Két hosszal: 0 = "/parkour " (üres prefix), 1 = gépelés közben — kivéve, ha az args[0]
        // már pontos egyezés, akkor a P=1 (pálya-azonosító) pozíció javaslatai jönnek.
        if (args.length == 0 || (args.length == 1 && !firstComplete)) {
            return options.stream().filter(o -> o.startsWith(first)).toList();
        }
        if (args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return parkourManager.getCourseIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }
        return List.of();
    }

    /** Az adott pozíción gépelés alatt álló szó (kisbetűsítve), vagy üres, ha még el sem kezdték. */
}
