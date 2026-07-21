package hu.taliann.icesmp.commands.faction;

import hu.taliann.icesmp.managers.WarWindowManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * /faction war — a hadi-ablak státusza mindenkinek; admin (icesmp.admin.war):
 * "start [perc]" soron kívüli nyitás, "stop" a kényszerített ablak zárása.
 * A gameplay-logika a WarWindowManagerben él, a parancs csak delegál.
 */
public final class FactionWarSubcommand implements FactionSubcommand {

    private final WarWindowManager warWindowManager;
    private final MessageManager messageManager;

    public FactionWarSubcommand(final WarWindowManager warWindowManager, final MessageManager messageManager) {
        this.warWindowManager = warWindowManager;
        this.messageManager = messageManager;
    }

    @Override
    public String name() {
        return "war";
    }

    @Override
    public String description() {
        return messageManager.get("messages.faction-desc-war",
                "A hadi-ablak állása (a RED↔BLUE ölés az ablak alatt nem bűn).");
    }

    @Override
    public String usage() {
        return messageManager.get("messages.faction-usage-war", "/faction war [start [perc]|stop]");
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args) {
        if (args.length >= 1) {
            if (!sender.hasPermission("icesmp.admin.war")) {
                sender.sendMessage(messageManager.get("messages.no-permission", "&cNincs jogosultságod ehhez."));
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "start" -> {
                    long minutes = 60L;
                    if (args.length >= 2) {
                        try {
                            minutes = Math.max(1L, Long.parseLong(args[1]));
                        } catch (final NumberFormatException exception) {
                            sender.sendMessage(messageManager.get("messages.faction-war-bad-minutes",
                                    "&cÉrvénytelen perc-érték: &f%s", args[1]));
                            return true;
                        }
                    }
                    sender.sendMessage(warWindowManager.forceStart(minutes)
                            ? messageManager.get("messages.faction-war-started",
                                    "&cHadi-ablak nyitva &f%s&c percre.", String.valueOf(minutes))
                            : messageManager.get("messages.faction-war-already",
                                    "&7A hadi-ablak már nyitva van."));
                    return true;
                }
                case "stop" -> {
                    sender.sendMessage(warWindowManager.forceEnd()
                            ? messageManager.get("messages.faction-war-stopped", "&7A kényszerített hadi-ablak lezárva.")
                            : messageManager.get("messages.faction-war-not-forced",
                                    "&7Nincs admin-nyitott ablak (a menetrendes ablakot a menetrend zárja)."));
                    return true;
                }
                default -> {
                    sender.sendMessage(messageManager.get("messages.faction-war-usage", "&cHasználat: %s", usage()));
                    return true;
                }
            }
        }

        if (warWindowManager.isActive()) {
            sender.sendMessage(messageManager.get("messages.faction-war-status-open",
                    "&c⚔ A hadi-ablak NYITVA — a Láng és a Fagy közt az ölés most nem bűn, és liga-pontot ér."));
        } else {
            final long minutes = warWindowManager.minutesUntilNextWindow();
            sender.sendMessage(minutes >= 0
                    ? messageManager.get("messages.faction-war-status-next",
                            "&7🕊 A hadi-ablak zárva. Következő nyitás kb. &f%s&7 perc múlva.", String.valueOf(minutes))
                    : messageManager.get("messages.faction-war-status-closed",
                            "&7🕊 A hadi-ablak zárva, és nincs menetrendbe írt nyitás."));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String[] args) {
        if (args.length <= 1 && sender.hasPermission("icesmp.admin.war")) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("start", "stop").stream().filter(option -> option.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
