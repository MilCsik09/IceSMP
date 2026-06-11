package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ProfessionCommand implements BasicCommand {

    private static final String ADMIN_PERMISSION = "icesmp.admin.profession";

    private final ProfessionManager professionManager;
    private final MessageManager messageManager;

    public ProfessionCommand(final ProfessionManager professionManager, final MessageManager messageManager) {
        this.professionManager = professionManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> handleJoin(sender, args);
            case "info" -> handleInfo(sender);
            case "list" -> handleList(sender);
            case "set" -> handleSet(sender, args);
            case "addxp" -> handleAddXp(sender, args);
            default -> {
                sender.sendMessage(messageManager.get("profession-unknown-subcommand", "&cIsmeretlen alparancs: &f%s", args[0]));
                sendHelp(sender);
            }
        }
    }

    private void handleJoin(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("profession-join-usage", "&cHasználat: /profession join <szakma>"));
            return;
        }

        final ProfessionType profession = ProfessionType.fromId(args[1]);
        if (profession == null) {
            sender.sendMessage(messageManager.get("profession-unknown", "&cIsmeretlen szakma: &f%s", args[1]));
            return;
        }

        if (!professionManager.selectProfession(player, profession)) {
            sender.sendMessage(messageManager.get("profession-already-set", "&cMár van szakmád. Szakmaváltást csak admin végezhet."));
            return;
        }

        player.sendMessage(messageManager.getMessage("profession-join-success", "&aSzakma kiválasztva:")
                .append(Component.space())
                .append(profession.getDisplayName()));
    }

    private void handleInfo(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        final ProfessionType profession = professionManager.getProfession(player);
        if (profession == null) {
            sender.sendMessage(messageManager.get("profession-none", "&eNincs szakmád. Válassz: &f/profession join <szakma>"));
            return;
        }

        player.sendMessage(messageManager.getMessage(
                        "profession-info",
                        "&6Szakmád: &f{profession} &7| Szint: &f{level}&7/&f{max} &7| XP: &f{xp}",
                        Map.of(
                                "profession", profession.getId(),
                                "level", String.valueOf(professionManager.getLevel(player)),
                                "max", String.valueOf(ProfessionManager.MAX_PROFESSION_LEVEL),
                                "xp", String.valueOf(professionManager.getXp(player))
                        )
                ));
    }

    private void handleList(final CommandSender sender) {
        final StringBuilder ids = new StringBuilder();
        for (final ProfessionType profession : ProfessionType.values()) {
            if (!ids.isEmpty()) {
                ids.append(", ");
            }
            ids.append(profession.getId());
        }
        sender.sendMessage(messageManager.get("profession-list", "&6Elérhető szakmák: &e%s", ids.toString()));
    }

    private void handleSet(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.get("profession-set-usage", "&cHasználat: /profession set <játékos> <szakma|none>"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("target-player-offline", "&cA célpont játékos nem elérhető."));
            return;
        }

        if ("none".equalsIgnoreCase(args[2])) {
            professionManager.setProfession(target, null);
            sender.sendMessage(messageManager.get("profession-set-cleared", "&aSzakma törölve: &f%s", target.getName()));
            return;
        }

        final ProfessionType profession = ProfessionType.fromId(args[2]);
        if (profession == null) {
            sender.sendMessage(messageManager.get("profession-unknown", "&cIsmeretlen szakma: &f%s", args[2]));
            return;
        }

        professionManager.setProfession(target, profession);
        sender.sendMessage(messageManager.get("profession-set-success", "&aSzakma beállítva: &f%s &7-> &e%s", target.getName(), profession.getId()));
    }

    private void handleAddXp(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("system.permission-denied", "&cNincs jogosultságod erre a parancsra."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.get("profession-addxp-usage", "&cHasználat: /profession addxp <játékos> <mennyiség>"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(messageManager.get("target-player-offline", "&cA célpont játékos nem elérhető."));
            return;
        }

        final int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(messageManager.get("invalid-amount", "&cÉrvénytelen összeg."));
            return;
        }

        if (amount <= 0 || !professionManager.addXp(target, amount)) {
            sender.sendMessage(messageManager.get("profession-addxp-failed", "&cNem sikerült XP-t adni (nincs szakma vagy hibás összeg)."));
            return;
        }

        sender.sendMessage(messageManager.get(
                "profession-addxp-success",
                "&aXP hozzáadva: &f%s &7| +&f%s XP &7| Szint: &f%s",
                target.getName(),
                amount,
                professionManager.getLevel(target)
        ));
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(messageManager.get("profession-help-header", "&6/profession &7- Elérhető parancsok:"));
        sender.sendMessage(messageManager.get("profession-help-join", "&e/profession join <szakma> &7- Szakma kiválasztása."));
        sender.sendMessage(messageManager.get("profession-help-info", "&e/profession info &7- Szakma állapot megtekintése."));
        sender.sendMessage(messageManager.get("profession-help-list", "&e/profession list &7- Elérhető szakmák listája."));
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messageManager.get("profession-help-set", "&e/profession set <játékos> <szakma|none> &7- Szakma beállítása (Admin)."));
            sender.sendMessage(messageManager.get("profession-help-addxp", "&e/profession addxp <játékos> <mennyiség> &7- XP hozzáadása (Admin)."));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();
        final List<String> subcommands = sender.hasPermission(ADMIN_PERMISSION)
                ? List.of("join", "info", "list", "set", "addxp")
                : List.of("join", "info", "list");

        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return subcommands.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && "join".equals(subcommand)) {
            return professionIds(args[1]);
        }

        if (args.length == 2 && ("set".equals(subcommand) || "addxp".equals(subcommand))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        if (args.length == 3 && "set".equals(subcommand)) {
            final List<String> options = new java.util.ArrayList<>(professionIds(args[2]));
            if ("none".startsWith(args[2].toLowerCase(Locale.ROOT))) {
                options.add("none");
            }
            return options;
        }

        return List.of();
    }

    private List<String> professionIds(final String rawPrefix) {
        final String prefix = rawPrefix == null ? "" : rawPrefix.toLowerCase(Locale.ROOT);
        return Arrays.stream(ProfessionType.values())
                .map(ProfessionType::getId)
                .filter(id -> id.startsWith(prefix))
                .toList();
    }
}
