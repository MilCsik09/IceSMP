package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.managers.ModerationManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import java.util.UUID;
import java.util.stream.Stream;

/**
 * {@code /mute <név> [perc] [ok...]} (0 perc = végtelen; perc kihagyva = eszkalációs
 * lépcső a némítás-történet alapján, ld. {@link ModerationManager#escalationMinutes}) +
 * {@code /mute list} — aktív némítások. A feloldás külön {@link UnmuteCommand}-ban van, mert egy
 * {@link BasicCommand} egy parancs.
 */
public final class MuteCommand implements BasicCommand {

    private static final String PERMISSION = hu.taliann.icesmp.core.Permissions.MODERATION;
    private static final String LIST = "list";

    private final JavaPlugin plugin;
    private final ModerationManager moderationManager;
    private final MessageManager messageManager;

    public MuteCommand(final JavaPlugin plugin, final ModerationManager moderationManager,
                        final MessageManager messageManager) {
        this.plugin = plugin;
        this.moderationManager = moderationManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(messageManager.get("moderation.permission-denied", "&cNincs jogod ehhez a parancshoz."));
            return;
        }

        if (args.length >= 1 && LIST.equalsIgnoreCase(args[0])) {
            listMutes(sender);
            return;
        }

        if (args.length < 1) {
            sender.sendMessage(messageManager.get("moderation.mute-usage",
                    "&cHasználat: /mute <név> [perc] [ok...] &7(perc kihagyva = automatikus eszkaláció, 0 perc = végtelen)&c, vagy /mute list"));
            return;
        }

        // Never Bukkit.getOfflinePlayer(name): unknown-name lookup fires a blocking Mojang call.
        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
        }
        if (target == null) {
            sender.sendMessage(messageManager.get("moderation.player-unknown",
                    "&cIsmeretlen játékos (még sosem járt a szerveren): &f%s", args[0]));
            return;
        }

        // A <perc> argumentum opcionális — ha hiányzik, vagy args[1] nem szám (tehát a
        // reason része), az eszkalációs lépcső adja az időt a némítás-történet alapján.
        final int minutes;
        final String reason;
        final boolean escalated;
        if (args.length >= 2 && isNumeric(args[1])) {
            final int parsed;
            try {
                parsed = Integer.parseInt(args[1]);
            } catch (final NumberFormatException invalidNumber) {
                sender.sendMessage(messageManager.get("moderation.mute-invalid-minutes",
                        "&cÉrvénytelen perc-érték: &f%s", args[1]));
                return;
            }
            if (parsed < 0) {
                sender.sendMessage(messageManager.get("moderation.mute-invalid-minutes",
                        "&cÉrvénytelen perc-érték: &f%s", args[1]));
                return;
            }
            minutes = parsed;
            reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
            escalated = false;
        } else {
            minutes = (int) Math.min(Integer.MAX_VALUE, moderationManager.escalationMinutes(target.getUniqueId()));
            reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
            escalated = true;
        }

        final int occurrence = moderationManager.muteHistoryCount(target.getUniqueId()) + 1;
        final String reasonDisplay = reason.isBlank() ? "nincs megadva" : reason;
        final String durationText = minutes == 0 ? "véglegesen" : minutes + " percre";

        moderationManager.mute(target.getUniqueId(), minutes, reason, sender.getName());

        final String escalationNote = escalated
                ? messageManager.get("moderation.mute-escalation-note",
                        " &7(eszkaláció: %d. alkalom → %d perc)", occurrence, minutes)
                : "";
        sender.sendMessage(messageManager.get("moderation.mute-success",
                "&aNémítva: &f%s &7(%s)&a. Ok: &f%s", target.getName(), durationText, reasonDisplay)
                + escalationNote);

        // Folia: a cél PDC-jét/inventory-ját itt nem érintjük, csak üzenetet küldünk — a saját
        // régió-szálán, sosem a parancs kiadójáén (target.getScheduler().run(...)).
        if (target.getPlayer() instanceof Player onlineTarget) {
            onlineTarget.getScheduler().run(plugin, task -> onlineTarget.sendMessage(messageManager.get(
                    "moderation.mute-notify", "&cNémítva lettél. &7Időtartam: &f%s&7, ok: &f%s",
                    durationText, reasonDisplay)), null);
        }
    }

    /** True if {@code value} parses as a (possibly signed) integer — used to tell "perc" from "ok...". */
    private static boolean isNumeric(final String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (final NumberFormatException notANumber) {
            return false;
        }
    }

    private void listMutes(final CommandSender sender) {
        final Map<UUID, ModerationManager.MuteEntry> mutes = moderationManager.listMutes();
        if (mutes.isEmpty()) {
            sender.sendMessage(messageManager.get("moderation.mute-list-empty", "&7Nincs aktív némítás."));
            return;
        }

        sender.sendMessage(messageManager.get("moderation.mute-list-header", "&b[Némítások]"));
        for (final Map.Entry<UUID, ModerationManager.MuteEntry> entry : mutes.entrySet()) {
            final ModerationManager.MuteEntry mute = entry.getValue();
            final OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getKey());
            final String name = offline.getName() != null ? offline.getName() : entry.getKey().toString();
            final String duration = mute.isPermanent() ? "végleges" : ModerationManager.formatRemaining(mute.untilMillis());

            sender.sendMessage(messageManager.get("moderation.mute-list-entry",
                    "&7- &f%s&7: &f%s &8[%s, admin: %s]",
                    name, mute.reason().isBlank() ? "nincs ok" : mute.reason(), duration,
                    mute.byName().isBlank() ? "?" : mute.byName()));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = prefixAt(args, 0);
            final List<String> options = new ArrayList<>();
            options.add(LIST);
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }

        if (args.length == 2 && !LIST.equalsIgnoreCase(args[0])) {
            final String prefix = prefixAt(args, 1);
            return Stream.of("5", "30", "60", "0").filter(option -> option.startsWith(prefix)).toList();
        }

        return List.of();
    }

}
