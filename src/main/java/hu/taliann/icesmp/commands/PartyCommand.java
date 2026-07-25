package hu.taliann.icesmp.commands;

import static hu.taliann.icesmp.utils.TabCompleteUtil.prefixAt;
import hu.taliann.icesmp.managers.PartyManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /party (alias /p) — WoW-stílusú csapat parancsok: meghívás, elfogadás/elutasítás,
 * kilépés, kirúgás, vezetés átadása, feloszlatás, tagok listája és csapat-chat
 * (bármely fel nem ismert alparancs csapat-chat üzenetként kerül elküldésre, hogy
 * a "/p <üzenet>" WoW-szerűen működjön).
 */
public final class PartyCommand implements BasicCommand {

    private final PartyManager partyManager;
    private final MessageManager messageManager;

    public PartyCommand(final PartyManager partyManager, final MessageManager messageManager) {
        this.partyManager = partyManager;
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
            if (partyManager.getParty(player.getUniqueId()) != null) {
                sendList(player);
            } else {
                sendHelp(player);
            }
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleResult(player, partyManager.accept(player));
            case "decline" -> handleResult(player, partyManager.decline(player));
            case "leave" -> handleResult(player, partyManager.leave(player));
            case "disband" -> handleResult(player, partyManager.disband(player));
            case "kick" -> handleKick(player, args);
            case "promote" -> handlePromote(player, args);
            case "list" -> sendList(player);
            case "chat", "c" -> handleChat(player, args, 1);
            case "help" -> sendHelp(player);
            default -> handleChat(player, args, 0);
        }
    }

    private void handleInvite(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("party-usage-invite", "&cHasználat: /party invite <név>"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(messageManager.get("target-player-offline", "&cA célpont játékos nem elérhető."));
            return;
        }

        final String errorKey = partyManager.invite(player, target);
        if (errorKey != null) {
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
            return;
        }

        player.sendMessage(messageManager.get("party-invite-sent", "&d[Party] &aMeghívó elküldve: &f%s", target.getName()));
    }

    private void handleKick(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("party-usage-kick", "&cHasználat: /party kick <név>"));
            return;
        }
        handleResult(player, partyManager.kick(player, args[1]));
    }

    private void handlePromote(final Player player, final String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageManager.get("party-usage-promote", "&cHasználat: /party promote <név>"));
            return;
        }
        handleResult(player, partyManager.promote(player, args[1]));
    }

    private void handleChat(final Player player, final String[] args, final int fromIndex) {
        final String message = args.length > fromIndex
                ? String.join(" ", Arrays.copyOfRange(args, fromIndex, args.length))
                : "";
        if (message.isBlank()) {
            player.sendMessage(messageManager.get("party-usage-chat", "&cHasználat: /party chat <üzenet>"));
            return;
        }
        handleResult(player, partyManager.chat(player, message));
    }

    /** Sends the mapped error message when a manager call reports failure via a message key. */
    private void handleResult(final Player player, final String errorKey) {
        if (errorKey != null) {
            player.sendMessage(messageManager.get(errorKey, defaultErrorFor(errorKey)));
        }
    }

    private void sendList(final Player player) {
        final PartyManager.Party party = partyManager.getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(messageManager.get("party-not-in-party", "&cNem vagy egyetlen csapatnak sem a tagja."));
            return;
        }

        player.sendMessage(messageManager.get("party-list-header", "&d[Party] &7Csapatod (&f%s&7/&f%s&7):",
                party.getMembers().size(), partyManager.getMaxSize()));
        for (final UUID memberId : party.getMembers()) {
            final Player member = Bukkit.getPlayer(memberId);
            final String name = member == null ? "?" : member.getName();
            final boolean isLeader = memberId.equals(party.getLeader());
            player.sendMessage(messageManager.get("party-list-entry", "&7- %s&f%s", isLeader ? "👑 " : "", name));
        }
    }

    private void sendHelp(final Player player) {
        player.sendMessage(messageManager.get("party-help-header", "&d/party &7- Csapat (party) parancsok:"));
        player.sendMessage(messageManager.get("party-help-invite",
                "&e/party invite <név> &7- Játékos meghívása (max 5 fő, frakciótól függetlenül)."));
        player.sendMessage(messageManager.get("party-help-accept",
                "&e/party accept &7- Meghívó elfogadása. &8(/party decline - elutasítás)"));
        player.sendMessage(messageManager.get("party-help-leave", "&e/party leave &7- Kilépés a csapatból."));
        player.sendMessage(messageManager.get("party-help-kick", "&e/party kick <név> &7- Tag kirúgása (csak vezető)."));
        player.sendMessage(messageManager.get("party-help-promote", "&e/party promote <név> &7- Vezetés átadása (csak vezető)."));
        player.sendMessage(messageManager.get("party-help-disband", "&e/party disband &7- Csapat feloszlatása (csak vezető)."));
        player.sendMessage(messageManager.get("party-help-list", "&e/party list &7- Tagok listája."));
        player.sendMessage(messageManager.get("party-help-chat", "&e/p <üzenet> &7- Csapat-chat (a párttagok látják)."));
    }

    private String defaultErrorFor(final String errorKey) {
        return switch (errorKey) {
            case "party-disabled" -> "&cA party-rendszer ki van kapcsolva.";
            case "party-invite-self" -> "&cMagadat nem hívhatod meg.";
            case "party-target-in-party" -> "&cA játékos már egy csapat tagja.";
            case "party-not-leader" -> "&cEhhez csapatvezetőnek kell lenned.";
            case "party-full" -> "&cA csapat megtelt (max 5 fő).";
            case "party-no-invite" -> "&cNincs érvényes meghívód (lejárt vagy nem is volt).";
            case "party-already-in-party" -> "&cMár egy csapat tagja vagy.";
            case "party-not-in-party" -> "&cNem vagy egyetlen csapatnak sem a tagja.";
            case "party-member-not-found" -> "&cNincs ilyen nevű tag a csapatodban.";
            case "party-kick-self" -> "&cMagadat nem rúghatod ki — használd a /party leave-et.";
            case "party-promote-self" -> "&cMár te vagy a csapatvezető.";
            default -> "&cA művelet nem sikerült.";
        };
    }

    @Override
    public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        final CommandSender sender = commandSourceStack.getSender();

        final List<String> options = List.of("invite", "accept", "decline", "leave", "kick", "promote", "disband", "list", "chat", "help");
        final String first = prefixAt(args, 0);
        final boolean firstComplete = options.contains(first);

        // Két hosszal: 0 = "/party " (üres prefix), 1 = gépelés közben — kivéve, ha az args[0]
        // már pontos egyezés, akkor a P=1 pozíció (célnév) javaslatai jönnek.
        if (args.length == 0 || (args.length == 1 && !firstComplete)) {
            return options.stream().filter(option -> option.startsWith(first)).toList();
        }

        if ("invite".equals(first) && args.length <= 2) {
            final String prefix = prefixAt(args, 1);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        if (("kick".equals(first) || "promote".equals(first)) && args.length <= 2) {
            if (!(sender instanceof Player player)) {
                return List.of();
            }
            final PartyManager.Party party = partyManager.getParty(player.getUniqueId());
            if (party == null) {
                return List.of();
            }
            final String prefix = prefixAt(args, 1);
            final List<String> names = new ArrayList<>();
            for (final UUID memberId : party.getMembers()) {
                final Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    names.add(member.getName());
                }
            }
            return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }

        return List.of();
    }

    /** Az adott pozíción gépelés alatt álló szó (kisbetűsítve), vagy üres, ha még el sem kezdték. */
}
