package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.classspec.domain.CompanionProfile;
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
import java.util.UUID;

/**
 * Companion control GUI (/pet): durable roster selection, summon/dismiss/release,
 * WoW-style stance buttons and the Társvért status.
 */
public final class PetGUI {

    private static final int SIZE = 27;
    private static final int INFO_SLOT = 18;
    private static final int SUMMON_SLOT = 10;
    private static final int DISMISS_SLOT = 11;
    private static final int NAME_SLOT = 12;
    private static final int RELEASE_SLOT = 13;
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

        final List<CompanionProfile> roster = petManager.companionRoster(viewer);
        final UUID selectedId = petManager.selectedCompanionId(viewer).orElse(null);
        for (int index = 0; index < Math.min(9, roster.size()); index++) {
            final CompanionProfile companion = roster.get(index);
            final boolean selected = companion.companionId().equals(selectedId);
            set(inventory, holder, index, companionTile(companion, index + 1, selected,
                    selected && petManager.hasActivePet(viewer), petManager.respawnRemainingSeconds(companion)),
                    "SELECT:" + companion.companionId());
        }

        inventory.setItem(INFO_SLOT, infoTile(viewer, petManager, roster, selectedId));

        set(inventory, holder, SUMMON_SLOT, GuiUtil.icon(Material.SOUL_LANTERN,
                name("Idézés", NamedTextColor.GREEN),
                List.of(GuiUtil.grey("A kiválasztott társad biztonságos helyen megjelenik."))), "SUMMON");
        set(inventory, holder, DISMISS_SLOT, GuiUtil.icon(Material.LEAD,
                name("Elbocsátás", NamedTextColor.YELLOW),
                List.of(GuiUtil.grey("A társad eltűnik (később újraidézhető)."))), "DISMISS");
        set(inventory, holder, NAME_SLOT, GuiUtil.icon(Material.NAME_TAG,
                name("Átnevezés", NamedTextColor.AQUA),
                List.of(GuiUtil.grey("Chatből: /pet name <új név>"))), "HINT:name");
        if (selectedId != null) {
            set(inventory, holder, RELEASE_SLOT, GuiUtil.icon(Material.FLINT_AND_STEEL,
                    name("Végleges elengedés", NamedTextColor.DARK_RED),
                    List.of(GuiUtil.grey("A kiválasztott társ végleg kikerül a társlistából."),
                            GuiUtil.grey("Ez a művelet nem vonható vissza."))), "RELEASE:" + selectedId);
        }

        final MinionManager.Stance stance = petManager.getStance(viewer);
        set(inventory, holder, STANCE_ACTIVE_SLOT, GuiUtil.icon(Material.IRON_SWORD,
                name("Támadás", NamedTextColor.RED),
                stanceLore("Harcol: segít a célpontod ellen, és megvéd.", stance == MinionManager.Stance.ACTIVE),
                stance == MinionManager.Stance.ACTIVE), "STANCE:ACTIVE");
        set(inventory, holder, STANCE_PASSIVE_SLOT, GuiUtil.icon(Material.SHIELD,
                name("Passzív", NamedTextColor.GOLD),
                stanceLore("Csak követ — sosem harcol.", stance == MinionManager.Stance.PASSIVE),
                stance == MinionManager.Stance.PASSIVE), "STANCE:PASSIVE");
        set(inventory, holder, STANCE_STAY_SLOT, GuiUtil.icon(Material.ARMOR_STAND,
                name("Maradj", NamedTextColor.GRAY),
                stanceLore("Helyben vár, amíg vissza nem hívod.", stance == MinionManager.Stance.STAY),
                stance == MinionManager.Stance.STAY), "STANCE:STAY");

        inventory.setItem(ARMOR_SLOT, armorTile(viewer, petManager));

        set(inventory, holder, CLOSE_SLOT, GuiUtil.icon(Material.BARRIER,
                name("Bezárás", NamedTextColor.RED), List.of()), "CLOSE");

        viewer.openInventory(inventory);
    }

    private static ItemStack companionTile(final CompanionProfile companion, final int index,
                                           final boolean selected, final boolean live,
                                           final long respawnSeconds) {
        final List<Component> lore = new ArrayList<>();
        lore.add(GuiUtil.grey("Típus: " + companion.typeId()));
        lore.add(GuiUtil.grey("Szint: " + companion.level() + "  |  XP: " + companion.experience()));
        if (live) {
            lore.add(GuiUtil.accent("✔ Kint van melletted"));
        } else if (selected) {
            lore.add(GuiUtil.accent("✔ Kiválasztott társ"));
        } else {
            lore.add(GuiUtil.grey("Bal katt: kiválasztás és idézés."));
        }
        lore.add(GuiUtil.grey("Jobb katt: végleges elengedés."));
        if (respawnSeconds > 0L) {
            lore.add(GuiUtil.grey("Újraidézhető: " + respawnSeconds + " mp múlva."));
        }
        return GuiUtil.icon(Material.BONE,
                name(index + ". " + (companion.name().isBlank() ? "Társ" : companion.name()),
                        selected ? NamedTextColor.GREEN : NamedTextColor.WHITE), lore, selected);
    }

    private static ItemStack infoTile(final Player viewer, final PetManager petManager,
                                      final List<CompanionProfile> roster, final UUID selectedId) {
        final List<Component> lore = new ArrayList<>();
        lore.add(GuiUtil.grey("Társlista: " + roster.size()));
        final CompanionProfile selected = roster.stream()
                .filter(companion -> companion.companionId().equals(selectedId))
                .findFirst().orElse(null);
        if (selected == null) {
            lore.add(GuiUtil.grey(roster.isEmpty()
                    ? "Szerezz vagy idézz egy társat."
                    : "Válassz társat a felső sorból."));
            return GuiUtil.icon(Material.BONE,
                    name(roster.isEmpty() ? "Nincs társad" : "Nincs kiválasztott társ",
                            NamedTextColor.GREEN), lore);
        }
        lore.add(GuiUtil.grey("Szint: " + selected.level()
                + "  |  XP: " + selected.experience() + " / " + petManager.nextLevelCost(viewer)));
        lore.add(GuiUtil.grey(petManager.hasActivePet(viewer)
                ? "Állapot: kint van melletted."
                : "Állapot: nincs kint (Idézés gomb)."));
        final long respawn = petManager.respawnRemainingSeconds(selected);
        if (respawn > 0L) {
            lore.add(GuiUtil.grey("Újraidézhető: " + respawn + " mp múlva (elesett)."));
        }
        return GuiUtil.icon(Material.BONE,
                name(selected.name().isBlank() ? "Társ" : selected.name(), NamedTextColor.GREEN), lore);
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
