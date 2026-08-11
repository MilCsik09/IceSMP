package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.managers.TalentManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TalentCommand implements BasicCommand {

    private final TalentManager talentManager;
    private final MessageManager messageManager;

    public TalentCommand(final TalentManager talentManager, final MessageManager messageManager) {
        this.talentManager = talentManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.get("messages.player-only", "&cEzt a parancsot csak játékosok használhatják."));
            return;
        }

        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            sendOverview(player);
            return;
        }

        if ("spend".equalsIgnoreCase(args[0])) {
            handleSpend(player, args);
            return;
        }

        sendHelp(player);
    }

    private void handleSpend(final Player player, final String[] args) {
        if (args.length < 3) {
            player.sendMessage(messageManager.get("talent-spend-usage", "&cHasználat: /talent spend <class|profession> <talent>"));
            return;
        }

        final boolean classPool;
        if ("class".equalsIgnoreCase(args[1])) {
            classPool = true;
        } else if ("profession".equalsIgnoreCase(args[1])) {
            classPool = false;
        } else {
            player.sendMessage(messageManager.get("talent-invalid-pool", "&cA tár csak 'class' vagy 'profession' lehet."));
            return;
        }

        talentManager.spendPoint(player, classPool, args[2]).whenComplete((spent, failure) ->
                talentManager.runOnOwnerThread(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure != null) {
                        player.sendMessage(messageManager.get(
                                "talent-storage-failed",
                                "&cA talent tartós mentése sikertelen; az állapot nem változott."));
                        return;
                    }
                    if (!Boolean.TRUE.equals(spent)) {
                        player.sendMessage(messageManager.get(
                                "talent-spend-failed",
                                "&cNem sikerült a pont elköltése (nincs pont, ismeretlen talent vagy max szint)."));
                        return;
                    }
                    player.sendMessage(messageManager.getMessage(
                            "talent-spend-success",
                            "&aTalent fejlesztve: &e{talent} &7(rang: &f{rank}&7) | Maradék pont: &f{points}",
                            Map.of(
                                    "talent", args[2].toLowerCase(Locale.ROOT),
                                    "rank", String.valueOf(talentManager.getRank(player, classPool, args[2])),
                                    "points", String.valueOf(talentManager.getAvailablePoints(player, classPool))
                            )));
                }));
    }

    private void sendOverview(final Player player) {
        sendPool(player, true, messageManager.get("talent-class-header", "&6Kaszt talentek &7(pont: &f%s&7):",
                talentManager.getAvailablePoints(player, true)));
        sendPool(player, false, messageManager.get("talent-profession-header", "&6Szakma talentek &7(pont: &f%s&7):",
                talentManager.getAvailablePoints(player, false)));
        player.sendMessage(messageManager.get("talent-spend-hint", "&7Pont elköltése: &f/talent spend <class|profession> <talent>"));
    }

    private void sendPool(final Player player, final boolean classPool, final String header) {
        player.sendMessage(header);

        final ConfigurationSection definitions = talentManager.getDefinitions(classPool);
        if (definitions == null || definitions.getKeys(false).isEmpty()) {
            player.sendMessage(messageManager.get("talent-pool-empty", "&7Nincsenek elérhető talentek."));
            return;
        }

        for (final String talentId : definitions.getKeys(false)) {
            final ConfigurationSection talentSection = definitions.getConfigurationSection(talentId);
            if (talentSection == null) {
                continue;
            }

            // WoW-style gating: only list talents whose class/spec/profession requirements are met.
            if (!talentManager.isAvailable(player, classPool, talentId)) {
                continue;
            }

            player.sendMessage(messageManager.getMessage(
                    "talent-line",
                    "&e{name} &7({id}) &8| &7Rang: &f{rank}&7/&f{max} &8| &7Hatás: &f{effect} +{per-rank}/rang",
                    Map.of(
                            "name", talentSection.getString("display-name", talentId),
                            "id", talentId,
                            "rank", String.valueOf(talentManager.getRank(player, classPool, talentId)),
                            "max", String.valueOf(talentSection.getInt("max-rank", 1)),
                            "effect", talentSection.getString("effect", "?"),
                            "per-rank", String.valueOf(talentSection.getDouble("per-rank", 0.0D))
                    )
            ));
        }
    }

    private void sendHelp(final Player player) {
        player.sendMessage(messageManager.get("talent-help-header", "&6/talent &7- Elérhető parancsok:"));
        player.sendMessage(messageManager.get("talent-help-list", "&e/talent &7- Talentek és pontok áttekintése."));
        player.sendMessage(messageManager.get("talent-help-spend", "&e/talent spend <class|profession> <talent> &7- Pont elköltése."));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final String first = prefixAt(args, 0);
        final boolean firstComplete = "list".equals(first) || "spend".equals(first);

        // Két hosszal: 0 = "/talent " (üres prefix), 1 = gépelés közben — kivéve, ha az args[0]
        // már pontos egyezés, akkor a P=1 (pool) pozíció javaslatai jönnek.
        if (args.length == 0 || (args.length == 1 && !firstComplete)) {
            return List.of("list", "spend").stream().filter(option -> option.startsWith(first)).toList();
        }

        if (!"spend".equals(first)) {
            return List.of();
        }

        final boolean poolComplete = args.length >= 2 && ("class".equalsIgnoreCase(args[1]) || "profession".equalsIgnoreCase(args[1]));
        if (args.length == 1 || (args.length == 2 && !poolComplete)) {
            final String prefix = prefixAt(args, 1);
            return List.of("class", "profession").stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if ((args.length == 2 && poolComplete) || args.length == 3) {
            final boolean classPool = "class".equalsIgnoreCase(args[1]);
            final ConfigurationSection definitions = talentManager.getDefinitions(classPool);
            if (definitions == null) {
                return List.of();
            }

            final String prefix = prefixAt(args, 2);
            return definitions.getKeys(false).stream().filter(id -> id.startsWith(prefix)).toList();
        }

        return List.of();
    }

}
