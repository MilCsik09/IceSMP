package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.managers.HudManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * /hud — a saját HUD-oldalsáv szekcióinak be/kikapcsolása (perzisztens, PDC-alapú).
 * Nem admin parancs: minden játékos a saját HUD-ját állítja.
 */
public final class HudCommand implements BasicCommand {

    private static final String TOGGLE = "toggle";

    private final HudManager hudManager;
    private final MessageManager messageManager;

    public HudCommand(final HudManager hudManager, final MessageManager messageManager) {
        this.hudManager = hudManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length == 0) {
            sendStatus(player);
            return;
        }

        if (TOGGLE.equalsIgnoreCase(args[0])) {
            handleToggle(player, args);
            return;
        }

        sendStatus(player);
    }

    private void handleToggle(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("hud-usage-toggle", "&cHasználat: /hud toggle <szekció|mind>"));
            return;
        }

        final String section = args[1].toLowerCase(Locale.ROOT);
        if (!HudManager.SECTION_ALL.equals(section) && !HudManager.SECTIONS.contains(section)) {
            player.sendMessage(messageManager.get("hud-unknown-section",
                    "&cIsmeretlen HUD-szekció: &f%s&c. Lehetséges értékek: &f%s&c, mind",
                    section, String.join(", ", HudManager.SECTIONS)));
            return;
        }

        final boolean nowHidden = hudManager.toggleSection(player, section);
        // A parancs a saját szálán fut (a játékos a saját PDC-jét írja), utána azonnal
        // frissítjük a láthatóságot, hogy a következő HUD-tick előtt is lássa a hatást.
        hudManager.update(player);

        if (HudManager.SECTION_ALL.equals(section)) {
            player.sendMessage(nowHidden
                    ? messageManager.get("hud-toggled-all-off", "&b[HUD] &7A teljes HUD-oldalsáv &ckikapcsolva&7.")
                    : messageManager.get("hud-toggled-all-on", "&b[HUD] &7A teljes HUD-oldalsáv &abekapcsolva&7."));
            return;
        }

        player.sendMessage(nowHidden
                ? messageManager.get("hud-toggled-off", "&b[HUD] &7%s szekció &ckikapcsolva&7.", displayName(section))
                : messageManager.get("hud-toggled-on", "&b[HUD] &7%s szekció &abekapcsolva&7.", displayName(section)));
    }

    private void sendStatus(final Player player) {
        final Set<String> hidden = hudManager.hiddenSections(player);
        player.sendMessage(messageManager.get("hud-list-header", "&b[HUD] &7Szekciók (/hud toggle <szekció>):"));
        for (final String section : HudManager.SECTIONS) {
            final boolean on = !hidden.contains(section);
            player.sendMessage(messageManager.get("hud-list-entry", "&7- &f%s &8(%s)&7: %s",
                    displayName(section), section, on ? "&abekapcsolva" : "&ckikapcsolva"));
        }
        final boolean allOn = !hidden.contains(HudManager.SECTION_ALL);
        player.sendMessage(messageManager.get("hud-list-all", "&7- &fTeljes HUD &8(mind)&7: %s",
                allOn ? "&abekapcsolva" : "&ckikapcsolva"));
    }

    private static String displayName(final String section) {
        return switch (section) {
            case HudManager.SECTION_FACTION -> "Frakció";
            case HudManager.SECTION_CURRENCY -> "Valuta";
            case HudManager.SECTION_CLASS -> "Kaszt";
            case HudManager.SECTION_RESOURCE -> "Erőforrás-csík";
            case HudManager.SECTION_EVENT -> "Esemény sor";
            case HudManager.SECTION_PARTY -> "Csapat (party) keret";
            case HudManager.SECTION_ALL -> "Teljes HUD";
            default -> section;
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = prefixAt(args, 0);
            return TOGGLE.startsWith(prefix) ? List.of(TOGGLE) : List.of();
        }

        if (args.length == 2 && TOGGLE.equalsIgnoreCase(args[0])) {
            final String prefix = prefixAt(args, 1);
            return List.of(
                    HudManager.SECTION_FACTION, HudManager.SECTION_CURRENCY, HudManager.SECTION_CLASS,
                    HudManager.SECTION_RESOURCE, HudManager.SECTION_EVENT, HudManager.SECTION_PARTY,
                    HudManager.SECTION_ALL
            ).stream().filter(option -> option.startsWith(prefix)).toList();
        }

        return List.of();
    }

}
