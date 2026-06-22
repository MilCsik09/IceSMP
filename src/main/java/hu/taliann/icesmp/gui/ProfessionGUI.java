package hu.taliann.icesmp.gui;

import static hu.taliann.icesmp.gui.GuiUtil.label;

import hu.taliann.icesmp.data.ProfessionCategory;
import hu.taliann.icesmp.data.ProfessionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Profession menu: learn one gathering and one crafting primary profession
 * (secondary professions are known by everyone). Shows levels for active
 * professions.
 */
public final class ProfessionGUI {

    private static final int SIZE = 45;
    private static final int HEADER_SLOT = 4;
    private static final int BACK_SLOT = 40;

    // Fixed slot ↔ profession mapping so render and click stay in sync.
    private static final int[] GATHERING_SLOTS = {19, 20, 21};
    private static final ProfessionType[] GATHERING = {ProfessionType.MINER, ProfessionType.HERBALIST, ProfessionType.LUMBERJACK};
    private static final int[] CRAFTING_SLOTS = {23, 24, 25};
    private static final ProfessionType[] CRAFTING = {ProfessionType.ARMORER, ProfessionType.ALCHEMIST, ProfessionType.ENCHANTER};
    private static final int[] SECONDARY_SLOTS = {30, 32};
    private static final ProfessionType[] SECONDARY = {ProfessionType.FISHERMAN, ProfessionType.COOK};

    private ProfessionGUI() {
    }

    public static int getBackSlot() {
        return BACK_SLOT;
    }

    public static void open(final Player viewer, final CharacterMenuContext ctx) {
        if (viewer == null || ctx == null) {
            return;
        }

        final ProfessionHolder holder = new ProfessionHolder(viewer.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE,
                ctx.messageManager().getMessage("profession-gui-title", "<dark_aqua>» Szakmák «</dark_aqua>"));
        holder.setInventory(inventory);

        GuiUtil.fill(inventory);
        inventory.setItem(HEADER_SLOT, GuiUtil.icon(Material.IRON_PICKAXE, accent("Szakmáid"),
                List.of(
                        grey("Egy gyűjtögető és egy készítő"),
                        grey("főszakmát tanulhatsz."),
                        grey("A másodlagos szakmákat mindenki ismeri."),
                        Component.empty(),
                        grey("A szakmaváltáskor a szintek megmaradnak.")
                )));

        for (int i = 0; i < GATHERING.length; i++) {
            inventory.setItem(GATHERING_SLOTS[i], createPrimaryItem(viewer, ctx, GATHERING[i]));
        }
        for (int i = 0; i < CRAFTING.length; i++) {
            inventory.setItem(CRAFTING_SLOTS[i], createPrimaryItem(viewer, ctx, CRAFTING[i]));
        }
        for (int i = 0; i < SECONDARY.length; i++) {
            inventory.setItem(SECONDARY_SLOTS[i], createSecondaryItem(viewer, ctx, SECONDARY[i]));
        }

        inventory.setItem(BACK_SLOT, GuiUtil.icon(Material.ARROW,
                Component.text("Vissza", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()));

        viewer.openInventory(inventory);
    }

    /** Maps a clicked slot to its gathering/crafting profession (null for others). */
    public static ProfessionType resolvePrimary(final int rawSlot) {
        for (int i = 0; i < GATHERING_SLOTS.length; i++) {
            if (GATHERING_SLOTS[i] == rawSlot) {
                return GATHERING[i];
            }
        }
        for (int i = 0; i < CRAFTING_SLOTS.length; i++) {
            if (CRAFTING_SLOTS[i] == rawSlot) {
                return CRAFTING[i];
            }
        }
        return null;
    }

    private static ItemStack createPrimaryItem(final Player player, final CharacterMenuContext ctx, final ProfessionType profession) {
        final boolean active = ctx.professionManager().hasProfession(player, profession);
        final ProfessionType slotHolder = ctx.professionManager().getProfession(player, profession.getCategory());
        final boolean slotTaken = slotHolder != null && slotHolder != profession;

        final List<Component> lore = new java.util.ArrayList<>();
        lore.add(grey("Típus: " + profession.getCategory().getDisplayName()));
        if (active) {
            lore.add(Component.text("✔ Aktív szakma", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(label("Szint", Component.text(String.valueOf(ctx.professionManager().getLevel(player, profession)), NamedTextColor.WHITE)));
            lore.add(label("XP", Component.text(String.valueOf(ctx.professionManager().getXp(player, profession)), NamedTextColor.WHITE)));
        } else if (slotTaken) {
            lore.add(Component.text("A(z) " + profession.getCategory().getDisplayName() + " helyed foglalt", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            lore.add(grey("(megtartott szint: " + ctx.professionManager().getLevel(player, profession) + ")"));
        } else {
            lore.add(click("Kattints a tanuláshoz"));
        }

        return GuiUtil.icon(materialFor(profession), name(profession.getDisplayName()), lore, active);
    }

    private static ItemStack createSecondaryItem(final Player player, final CharacterMenuContext ctx, final ProfessionType profession) {
        final List<Component> lore = List.of(
                grey("Típus: másodlagos (mindenki ismeri)"),
                label("Szint", Component.text(String.valueOf(ctx.professionManager().getLevel(player, profession)), NamedTextColor.WHITE)),
                label("XP", Component.text(String.valueOf(ctx.professionManager().getXp(player, profession)), NamedTextColor.WHITE))
        );
        return GuiUtil.icon(materialFor(profession), name(profession.getDisplayName()), lore, true);
    }

    private static Material materialFor(final ProfessionType profession) {
        return switch (profession) {
            case MINER -> Material.IRON_ORE;
            case HERBALIST -> Material.FERN;
            case LUMBERJACK -> Material.OAK_LOG;
            case ARMORER -> Material.ANVIL;
            case ALCHEMIST -> Material.BREWING_STAND;
            case ENCHANTER -> Material.ENCHANTING_TABLE;
            case FISHERMAN -> Material.FISHING_ROD;
            case COOK -> Material.COOKED_BEEF;
        };
    }

    private static Component name(final Component display) {
        return display.decoration(TextDecoration.ITALIC, false);
    }

    private static Component accent(final String text) {
        return Component.text(text, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false);
    }

    private static Component grey(final String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static Component click(final String text) {
        return Component.text("» " + text, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
    }
}
