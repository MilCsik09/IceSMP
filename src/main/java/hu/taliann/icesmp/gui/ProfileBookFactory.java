package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.List;

public final class ProfileBookFactory {

    private final FactionManager factionManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;

    public ProfileBookFactory(final FactionManager factionManager, final CurrencyManager currencyManager,
                              final MessageManager messageManager) {
        this.factionManager = factionManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    public Book create(final Player player, final String titleText) {
        final Component title = LegacyComponentSerializer.legacySection().deserialize(TextUtil.color(titleText));
        final Component author = messageManager.getMessage("system.profile.book.author", "<dark_purple>IceSMP</dark_purple>");
        return Book.book(title, author, List.of(
                createCharacterSheetPage(player),
                createBankActionsPage(),
                createQuickStatsPage(player)
        ));
    }

    private Component createCharacterSheetPage(final Player player) {
        final FactionType factionType = factionManager.getFaction(player.getUniqueId());
        final Component factionComponent = factionType == null
                ? messageManager.getMessage("system.profile.book.faction.none", "<gray>Nincs</gray>")
                : switch (factionType) {
                    case RED -> messageManager.getMessage("system.profile.book.faction.red", "<red>Piros</red>");
                    case BLUE -> messageManager.getMessage("system.profile.book.faction.blue", "<blue>Kek</blue>");
                    case NEUTRAL -> messageManager.getMessage("system.profile.book.faction.neutral", "<dark_purple>Semleges</dark_purple>");
                    case DARK -> messageManager.getMessage("system.profile.book.faction.dark", "<dark_gray>Sötét</dark_gray>");
                };

        return Component.text()
                .append(messageManager.getMessage("system.profile.book.character.title", "<gold><b>Profil kartya</b></gold>"))
                .append(Component.newline())
                .append(messageManager.getMessage("system.profile.book.character.player", "<gray>Nev: </gray><white>{player}</white>", java.util.Map.of("player", player.getName())))
                .append(Component.newline())
                .append(messageManager.getMessage("system.profile.book.character.faction", "<gray>Frakcio: </gray>").append(factionComponent))
                .append(Component.newline())
                .append(messageManager.getMessage("system.profile.book.character.balances", "<gray>Egyenlegek:</gray>"))
                .append(Component.newline())
                .append(balanceLine("Piros", NamedTextColor.RED, currencyManager.getBalance(player, FactionType.RED)))
                .append(Component.newline())
                .append(balanceLine("Kék", NamedTextColor.BLUE, currencyManager.getBalance(player, FactionType.BLUE)))
                .append(Component.newline())
                .append(balanceLine("Semleges", NamedTextColor.DARK_PURPLE, currencyManager.getBalance(player, FactionType.NEUTRAL)))
                .append(Component.newline())
                .append(balanceLine("Sötét", NamedTextColor.DARK_GRAY, currencyManager.getBalance(player, FactionType.DARK)))
                .build();
    }

    private Component createBankActionsPage() {
        return Component.text()
                .append(messageManager.getMessage("system.profile.book.bank.title", "<gold><b>Bank muveletek</b></gold>"))
                .append(Component.newline())
                .append(Component.newline())
                .append(actionButton(messageManager.get("system.profile.book.bank.deposit.label", "DEPOSIT ALL"), NamedTextColor.GREEN, "/bank deposit",
                        messageManager.get("system.profile.book.bank.deposit.hover", "Kattints a teljes befizeteshez.")))
                .append(Component.newline())
                .append(Component.newline())
                .append(actionButton(messageManager.get("system.profile.book.bank.withdraw-red.label", "WITHDRAW RED"), NamedTextColor.RED, "/bank withdraw red ",
                        messageManager.get("system.profile.book.bank.withdraw-red.hover", "Javasolja a piros valuta kivetelt.")))
                .append(Component.newline())
                .append(actionButton(messageManager.get("system.profile.book.bank.withdraw-blue.label", "WITHDRAW BLUE"), NamedTextColor.BLUE, "/bank withdraw blue ",
                        messageManager.get("system.profile.book.bank.withdraw-blue.hover", "Javasolja a kek valuta kivetelt.")))
                .append(Component.newline())
                .append(actionButton(messageManager.get("system.profile.book.bank.withdraw-neutral.label", "WITHDRAW NEUTRAL"), NamedTextColor.DARK_PURPLE, "/bank withdraw neutral ",
                        messageManager.get("system.profile.book.bank.withdraw-neutral.hover", "Javasolja a semleges valuta kivetelt.")))
                .append(Component.newline())
                .append(actionButton(messageManager.get("system.profile.book.bank.withdraw-dark.label", "WITHDRAW DARK"), NamedTextColor.DARK_GRAY, "/bank withdraw dark ",
                        messageManager.get("system.profile.book.bank.withdraw-dark.hover", "Javasolja a sotet valuta kivetelt.")))
                .append(Component.newline())
                .append(Component.newline())
                .append(messageManager.getMessage("system.profile.book.bank.tip", "<dark_gray>Tipp: ezek elo muveletek, a /profile ujranyitasa frissiti az egyenleget.</dark_gray>"))
                .build();
    }

    private Component createQuickStatsPage(final Player player) {
        return Component.text()
                .append(messageManager.getMessage("system.profile.book.stats.title", "<gold><b>Gyors statisztikak</b></gold>"))
                .append(Component.newline())
                .append(Component.newline())
                .append(messageManager.getMessage("system.profile.book.stats.level", "<gray>Szint: </gray><white>Nincs beallitva</white>"))
                .append(Component.newline())
                .append(messageManager.getMessage("system.profile.book.stats.rank", "<gray>Rang: </gray><white>Nincs beallitva</white>"))
                .append(Component.newline())
                .append(messageManager.getMessage("system.profile.book.stats.profession", "<gray>Foglalkozas: </gray><white>Nincs beallitva</white>"))
                .append(Component.newline())
                .append(Component.newline())
                .append(messageManager.getMessage("system.profile.book.stats.world", "<gray>Vilag: </gray><white>{world}</white>", java.util.Map.of("world", player.getWorld().getName())))
                .append(Component.newline())
                .append(messageManager.getMessage("system.profile.book.stats.faction", "<gray>Frakcio: </gray><white>{faction}</white>", java.util.Map.of(
                        "faction",
                        factionManager.getFaction(player.getUniqueId()) == null
                                ? messageManager.get("system.profile.book.faction.none-text", "Nincs")
                                : factionManager.getFaction(player.getUniqueId()).getDisplayName()
                )))
                .build();
    }

    private Component balanceLine(final String label, final NamedTextColor color, final double value) {
        return Component.text(label + ": ", color)
                .append(Component.text(currencyManager.formatBalance(value), NamedTextColor.WHITE));
    }

    private Component actionButton(final String label, final NamedTextColor color, final String command, final String hoverText) {
        return Component.text("[" + label + "]", color, TextDecoration.BOLD)
                .clickEvent(label.contains("DEPOSIT")
                        ? ClickEvent.runCommand(command)
                        : ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hoverText, NamedTextColor.YELLOW)));
    }
}


