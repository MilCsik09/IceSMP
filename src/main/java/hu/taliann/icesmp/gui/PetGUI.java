package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.MinionManager;
import hu.taliann.icesmp.managers.PetManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Companion control GUI (/pet): status tile, summon/dismiss/rename, WoW-style
 * stance buttons and the Társvért status. Every click is pure delegation to the
 * existing /pet subcommands — PetManager stays the single owner of pet state.
 */
public final class PetGUI {

    private static final int SIZE = 27;
    private static final int INFO_SLOT = 4;
    private static final int SUMMON_SLOT = 10;
    private static final int DISMISS_SLOT = 11;
    private static final int NAME_SLOT = 12;
    private static final int STANCE_ACTIVE_SLOT = 14;
    private static final int STANCE_PASSIVE_SLOT = 15;
    private static final int STANCE_STAY_SLOT = 16;
    private static final int ARMOR_SLOT = 22;
    private static final int CLOSE_SLOT = 26;

    private PetGUI() {
    }

    public static void open(final Player viewer, final PetManager petManager, final MessageManager messageManager) {
        final Component title = messageManager.getComponent("messages.pet-gui-title", "&2» Társ «");
        final PetGUIHolder holder = new PetGUIHolder(viewer.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);
        GuiUtil.fill(inventory);

        inventory.setItem(INFO_SLOT, infoTile(viewer, petManager));

        set(inventory, holder, SUMMON_SLOT, GuiUtil.icon(Material.SOUL_LANTERN,
                name("Idézés", NamedTextColor.GREEN),
                List.of(GuiUtil.grey("A társad megjelenik melletted."))), "RUN:pet summon");
        set(inventory, holder, DISMISS_SLOT, GuiUtil.icon(Material.LEAD,
                name("Elbocsátás", NamedTextColor.YELLOW),
                List.of(GuiUtil.grey("A társad eltűnik (később újraidézhető)."))), "RUN:pet dismiss");
        set(inventory, holder, NAME_SLOT, GuiUtil.icon(Material.NAME_TAG,
                name("Átnevezés", NamedTextColor.AQUA),
                List.of(GuiUtil.grey("Chatből: /pet name <új név>"))), "HINT:name");

        final MinionManager.Stance stance = petManager.getStance(viewer);
        set(inventory, holder, STANCE_ACTIVE_SLOT, GuiUtil.icon(Material.IRON_SWORD,
                name("Támadás", NamedTextColor.RED),
                stanceLore("Harcol: segít a célpontod ellen, és megvéd.", stance == MinionManager.Stance.ACTIVE),
                stance == MinionManager.Stance.ACTIVE), "RUN:pet stance aktiv");
        set(inventory, holder, STANCE_PASSIVE_SLOT, GuiUtil.icon(Material.SHIELD,
                name("Passzív", NamedTextColor.GOLD),
                stanceLore("Csak követ — sosem harcol.", stance == MinionManager.Stance.PASSIVE),
                stance == MinionManager.Stance.PASSIVE), "RUN:pet stance passziv");
        set(inventory, holder, STANCE_STAY_SLOT, GuiUtil.icon(Material.ARMOR_STAND,
                name("Maradj", NamedTextColor.GRAY),
                stanceLore("Helyben vár, amíg vissza nem hívod.", stance == MinionManager.Stance.STAY),
                stance == MinionManager.Stance.STAY), "RUN:pet stance marad");

        inventory.setItem(ARMOR_SLOT, armorTile(viewer, petManager));

        set(inventory, holder, CLOSE_SLOT, GuiUtil.icon(Material.BARRIER,
                name("Bezárás", NamedTextColor.RED), List.of()), "CLOSE");

        viewer.openInventory(inventory);
    }

    private static ItemStack infoTile(final Player viewer, final PetManager petManager) {
        final List<Component> lore = new ArrayList<>();
        lore.add(GuiUtil.grey("Szint: " + petManager.getLevel(viewer)
                + "  |  XP: " + petManager.getXp(viewer) + " / " + petManager.nextLevelCost(viewer)));
        lore.add(GuiUtil.grey(petManager.hasActivePet(viewer)
                ? "Állapot: kint van melletted."
                : "Állapot: nincs kint (Idézés gomb)."));
        final long respawn = petManager.respawnRemainingSeconds(viewer);
        if (respawn > 0L) {
            lore.add(GuiUtil.grey("Újraidézhető: " + respawn + " mp múlva (elesett)."));
        }
        return GuiUtil.icon(Material.BONE,
                name(petManager.getName(viewer), NamedTextColor.GREEN), lore);
    }

    private static ItemStack armorTile(final Player viewer, final PetManager petManager) {
        final boolean equipped = petManager.hasPetArmor(viewer);
        return GuiUtil.icon(Material.LEATHER_HORSE_ARMOR,
                name("Társvért", equipped ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                List.of(GuiUtil.grey(equipped
                                ? "Felszerelve: a társad páncélt és életerőt kap."
                                : "Nincs felszerelve — ritka zsákmány szörnyekből;"),
                        GuiUtil.grey(equipped
                                ? "Újraidézéskor is megmarad."
                                : "jobb katt vele a társadon a felszereléshez.")),
                equipped);
    }

    private static List<Component> stanceLore(final String description, final boolean current) {
        return current
                ? List.of(GuiUtil.grey(description), GuiUtil.accent("✔ Jelenlegi állásmód"))
                : List.of(GuiUtil.grey(description));
    }

    private static Component name(final String text, final NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static void set(final Inventory inventory, final PetGUIHolder holder, final int slot,
                            final ItemStack item, final String action) {
        inventory.setItem(slot, item);
        holder.mapAction(slot, action);
    }
}
