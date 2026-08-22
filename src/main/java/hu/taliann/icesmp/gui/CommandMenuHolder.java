package hu.taliann.icesmp.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory holder for the command-menu hub and its sub-menus (/menu). Each menu
 * is identified by its {@link Menu} type, scoped to an owner, and carries a
 * slot → action map so the listener can stay generic: an action is either
 * "MENU:&lt;type&gt;" (open a sub-menu), "RUN:&lt;command&gt;" (run a command then
 * refresh), "OPEN:&lt;command&gt;" (run a command that opens its own GUI) or "CLOSE".
 */
public final class CommandMenuHolder implements InventoryHolder {

    public enum Menu { MAIN, FACTION, FACTION_SWITCH, BANK, EXCHANGE, EVENTS, RELIC, SOULS, LEADERBOARD, ACHIEVEMENTS, PARTY, CLAIM, BOUNTY, ADMIN }

    private final Menu menu;
    private final UUID ownerUuid;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;
    // A valutaváltó almenü kiválasztott iránya — a RUN utáni frissítés így őrzi meg a választást.
    private String exchangeFrom;
    private String exchangeTo;

    public CommandMenuHolder(final Menu menu, final UUID ownerUuid) {
        this.menu = menu;
        this.ownerUuid = ownerUuid;
    }

    public Menu getMenu() {
        return menu;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Map<Integer, String> getActions() {
        return actions;
    }

    public void bind(final int slot, final String action) {
        actions.put(slot, action);
    }

    public String getExchangeFrom() {
        return exchangeFrom;
    }

    public String getExchangeTo() {
        return exchangeTo;
    }

    public void setExchangeSelection(final String from, final String to) {
        this.exchangeFrom = from;
        this.exchangeTo = to;
    }

    @Override
    public @NonNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("CommandMenuHolder inventory has not been set yet");
        }
        return inventory;
    }

    public void setInventory(final Inventory inventory) {
        this.inventory = inventory;
        if (menu == Menu.MAIN && inventory != null && inventory.getSize() > 33) {
            final ItemStack forge = new ItemStack(Material.SMITHING_TABLE);
            final ItemMeta meta = forge.getItemMeta();
            meta.displayName(Component.text("Szakma-műhely", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Mestermű, rúna, újrakovácsolás és Felemelkedés",
                                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("a canonical Item Forge felületén.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("» Kattints", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)));
            forge.setItemMeta(meta);
            inventory.setItem(33, forge);
            actions.put(33, "OPEN:profession forge");
        }
    }
}
