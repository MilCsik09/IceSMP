package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.security.HiddenDevAuthority;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Hidden catalog diagnostics; item mutation stays closed until the gameplay rollout. */
public final class TrashDevCommand {

    private static final int MAX_SUGGESTIONS = 40;

    private final TrashCatalog catalog;
    private final TrashItemFactory itemFactory;

    public TrashDevCommand(final TrashCatalog catalog, final TrashItemFactory itemFactory) {
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
        this.itemFactory = java.util.Objects.requireNonNull(itemFactory, "itemFactory");
    }

    public void execute(final CommandSender sender, final String[] args) {
        if (!HiddenDevAuthority.mayUseHiddenContent(sender)) return;
        if (args.length == 0 || !"trash".equalsIgnoreCase(args[0])) {
            sendUsage(sender);
            return;
        }
        if (args.length >= 2 && "catalog".equalsIgnoreCase(args[1])) {
            sender.sendMessage(Component.text("Trash catalog: " + catalog.snapshot().size()
                    + " validált base identity; gameplay rollout még nincs aktiválva.", NamedTextColor.GRAY));
            return;
        }
        if (args.length >= 2 && "inspect".equalsIgnoreCase(args[1])) {
            inspect(sender, args);
            return;
        }
        sendUsage(sender);
    }

    public List<String> suggest(final String[] args) {
        if (args.length == 0) return List.of("trash");
        if (args.length == 1) return matching(List.of("trash"), args[0]);
        if (!"trash".equalsIgnoreCase(args[0])) return List.of();
        if (args.length == 2) return matching(List.of("catalog", "inspect"), args[1]);
        if (args.length == 3 && "inspect".equalsIgnoreCase(args[1])) {
            final String prefix = args[2].toLowerCase(Locale.ROOT);
            return catalog.snapshot().keySet().stream().filter(id -> id.startsWith(prefix))
                    .sorted().limit(MAX_SUGGESTIONS).toList();
        }
        return List.of();
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

    private static void sendUsage(final CommandSender sender) {
        sender.sendMessage(Component.text("Használat: /icesmp dev trash <catalog|inspect [id]>",
                NamedTextColor.RED));
    }

    private static List<String> matching(final List<String> options, final String rawPrefix) {
        final String prefix = rawPrefix == null ? "" : rawPrefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
