package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.managers.GuildManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * B35 — /ceh (alias /guild): frakción belüli kisközösségek kezelése.
 * Alparancsok: letrehoz, meghiv, elfogad, elhagy, kirug, befizet, info, lista.
 * A gameplay-logika a GuildManagerben él; a parancs vékony héj + üzenetek.
 */
public final class GuildCommand implements BasicCommand {

    private static final List<String> SUBS = List.of(
            "letrehoz", "meghiv", "elfogad", "elhagy", "kirug", "befizet", "info", "lista");

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final GuildManager guildManager;
    private final MessageManager messageManager;

    public GuildCommand(final org.bukkit.plugin.java.JavaPlugin plugin,
                        final GuildManager guildManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (!(commandSourceStack.getSender() instanceof Player player)) {
            commandSourceStack.getSender().sendMessage(messageManager.get(
                    "guild-players-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }
        if (args.length == 0) {
            sendHelp(player);
            return;
        }
        final String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "letrehoz", "create" -> {
                if (args.length < 2) {
                    player.sendMessage(messageManager.get("guild-usage-create",
                            "&cHasználat: /ceh letrehoz <név> &7(a név lehet többszavas)"));
                    return;
                }
                final String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                reply(player, guildManager.create(player, name), "guild-created",
                        "<gold>⚜ Céh alapítva: <white>{name}</white> — hívd meg a társaidat: /ceh meghiv <játékos></gold>",
                        Map.of("name", name.trim()));
            }
            case "meghiv", "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(messageManager.get("guild-usage-invite", "&cHasználat: /ceh meghiv <játékos>"));
                    return;
                }
                final Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(messageManager.get("guild-no-player", "&cNincs ilyen online játékos."));
                    return;
                }
                final String error = guildManager.invite(player, target);
                if (error == null) {
                    final GuildManager.Guild guild = guildManager.getGuild(player.getUniqueId());
                    player.sendMessage(messageManager.get("guild-invited", "&6⚜ Meghívtad: &f%s", target.getName()));
                    // Folia: a cél értesítése a CÉL saját régió-szálán.
                    final String guildName = guild == null ? "?" : guild.name;
                    target.getScheduler().run(plugin,
                            task -> target.sendMessage(messageManager.getMessage("guild-invite-received",
                                    "<gold>⚜ Meghívtak a(z) <white>{guild}</white> céhbe! Elfogadás: <white>/ceh elfogad</white></gold>",
                                    Map.of("guild", guildName))), null);
                } else {
                    fail(player, error);
                }
            }
            case "elfogad", "accept" -> reply(player, guildManager.accept(player), "guild-joined",
                    "<gold>⚜ Beléptél a céhbe — jó munkát a közös célokhoz!</gold>", Map.of());
            case "elhagy", "leave" -> reply(player, guildManager.leave(player), "guild-left",
                    "<gray>⚜ Elhagytad a céhet.</gray>", Map.of());
            case "kirug", "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(messageManager.get("guild-usage-kick", "&cHasználat: /ceh kirug <játékos>"));
                    return;
                }
                // Soha Bukkit.getOfflinePlayer(név): ismeretlen névre blokkoló Mojang-hívást
                // indítana a régió-szálon. Online pontos név, különben csak a helyi cache.
                final Player online = Bukkit.getPlayerExact(args[1]);
                final org.bukkit.OfflinePlayer target = online != null ? online
                        : Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    player.sendMessage(messageManager.get("guild-kick-unknown",
                            "&cNincs ilyen nevű (ismert) játékos."));
                    return;
                }
                reply(player, guildManager.kick(player, target.getUniqueId()), "guild-kicked",
                        "<gray>⚜ Kirúgva a céhből: <white>{name}</white>.</gray>", Map.of("name", args[1]));
            }
            case "befizet", "deposit" -> {
                if (args.length < 2) {
                    player.sendMessage(messageManager.get("guild-usage-deposit", "&cHasználat: /ceh befizet <összeg>"));
                    return;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[1]);
                } catch (final NumberFormatException exception) {
                    player.sendMessage(messageManager.get("guild-bad-amount-msg", "&cÉrvénytelen összeg."));
                    return;
                }
                reply(player, guildManager.deposit(player, amount), "guild-deposited",
                        "<gold>⚜ Befizetve a céh-kasszába: <white>{amount}</white>.</gold>",
                        Map.of("amount", String.valueOf(amount)));
            }
            case "info" -> {
                final GuildManager.Guild guild = guildManager.getGuild(player.getUniqueId());
                if (guild == null) {
                    fail(player, "guild-not-member");
                    return;
                }
                player.sendMessage(messageManager.getMessage("guild-info",
                        "<gold>⚜ <white>{name}</white> — {level}. szint <gray>({xp} XP)</gray> • Tagok: <white>{members}/{max}</white> • Kassza: <white>{bank}</white></gold>",
                        Map.of("name", guild.name, "level", String.valueOf(guildManager.levelOf(guild)),
                                "xp", String.valueOf(guild.xp),
                                "members", String.valueOf(guild.members.size()),
                                "max", String.valueOf(guildManager.maxMembers(guild)),
                                "bank", String.valueOf(guild.bank))));
                final StringBuilder names = new StringBuilder();
                for (final java.util.UUID memberId : guild.members) {
                    final org.bukkit.OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
                    if (!names.isEmpty()) {
                        names.append(", ");
                    }
                    names.append(member.getName() == null ? "?" : member.getName());
                    if (memberId.equals(guild.leader)) {
                        names.append(" ⭐");
                    }
                }
                player.sendMessage(messageManager.get("guild-info-members", "&7Tagok: &f%s", names.toString()));
            }
            case "lista", "list" -> {
                final List<GuildManager.Guild> all = guildManager.allGuilds();
                if (all.isEmpty()) {
                    player.sendMessage(messageManager.get("guild-list-empty",
                            "&7Még nincs céh a szerveren — alapíts egyet: /ceh letrehoz <név>"));
                    return;
                }
                player.sendMessage(messageManager.get("guild-list-header", "&6⚜ Céhek:"));
                all.stream()
                        .sorted((a, b) -> Long.compare(b.xp, a.xp))
                        .limit(10)
                        .forEach(guild -> player.sendMessage(messageManager.get("guild-list-row",
                                "&7- &f%s &7(%s. szint, %s tag)",
                                guild.name, guildManager.levelOf(guild), guild.members.size())));
            }
            default -> sendHelp(player);
        }
    }

    private void reply(final Player player, final String error, final String okKey,
                       final String okDefault, final Map<String, String> placeholders) {
        if (error == null) {
            player.sendMessage(messageManager.getMessage(okKey, okDefault, placeholders));
        } else {
            fail(player, error);
        }
    }

    private void fail(final Player player, final String error) {
        final String text = switch (error) {
            case "guild-disabled" -> "&cA céh-rendszer jelenleg ki van kapcsolva.";
            case "guild-already-member" -> "&cMár tagja vagy egy céhnek.";
            case "guild-needs-faction" -> "&cElőbb csatlakozz egy frakcióhoz.";
            case "guild-bad-name" -> "&cA céh-név 3-24 karakter legyen.";
            case "guild-name-taken" -> "&cEz a céh-név foglalt vagy érvénytelen.";
            case "guild-cant-afford" -> "&cNincs elég fedezet a számládon.";
            case "guild-not-leader" -> "&cEhhez céh-vezetőnek kell lenned.";
            case "guild-target-in-guild" -> "&cA játékos már céh-tag.";
            case "guild-wrong-faction" -> "&cCsak a saját frakciódból hívhatsz tagot.";
            case "guild-full" -> "&cA céh megtelt (szintlépéssel nő a plafon).";
            case "guild-no-invite" -> "&cNincs függő céh-meghívásod.";
            case "guild-not-member" -> "&cNem vagy céh-tag.";
            case "guild-target-not-member" -> "&cA játékos nem (rúgható) tagja a céhnek.";
            case "guild-bad-amount" -> "&cÉrvénytelen összeg.";
            default -> "&cA művelet nem hajtható végre.";
        };
        player.sendMessage(messageManager.get("guild-error." + error, text));
    }

    private void sendHelp(final Player player) {
        player.sendMessage(messageManager.get("guild-help",
                "&6⚜ /ceh &7- letrehoz <név> | meghiv <játékos> | elfogad | elhagy | kirug <játékos> | befizet <összeg> | info | lista"));
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack,
                                               final @NonNull String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("meghiv") || args[0].equalsIgnoreCase("kirug"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
