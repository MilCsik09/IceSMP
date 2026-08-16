package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.classspec.application.CompanionProgressView;
import hu.taliann.icesmp.managers.MinionManager;
import hu.taliann.icesmp.managers.PetManager;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.data.FactionType;
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

/** Custom Profile v2 companion workshop: roster, progression, commands and safe release. */
public final class PetGUI {

    private static final int SIZE = 54;
    private static final int[] ROSTER_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};

    private PetGUI() { }

    public static void open(final Player viewer, final PetManager pets,
                            final MessageManager messages) {
        final PetGUIHolder holder = new PetGUIHolder(viewer.getUniqueId());
        final Inventory inventory = createInventory(holder, viewer, pets, messages,
                "messages.pet-gui-title", "Társműhely");
        fillThemed(inventory, pets.darkCompanionTheme(viewer));

        final List<CompanionProgressView> roster = pets.companionProgression(viewer);
        for (int index = 0; index < Math.min(ROSTER_SLOTS.length, roster.size()); index++) {
            final CompanionProgressView companion = roster.get(index);
            set(inventory, holder, ROSTER_SLOTS[index], companionTile(companion, index + 1,
                    pets.respawnRemainingSeconds(companion.companion())),
                    "SELECT:" + companion.companion().companionId());
        }

        final CompanionProgressView selected = roster.stream()
                .filter(CompanionProgressView::selected).findFirst().orElse(null);
        inventory.setItem(4, overviewTile(roster, selected));
        inventory.setItem(22, selected == null ? emptySelectionTile() : progressTile(selected));

        set(inventory, holder, 28, GuiUtil.icon(Material.SOUL_LANTERN,
                name("Idézés", NamedTextColor.GREEN),
                List.of(GuiUtil.grey("A kiválasztott társ megjelenik melletted."),
                        click("Idézés"))), "SUMMON");
        set(inventory, holder, 29, GuiUtil.icon(Material.LEAD,
                name("Elbocsátás", NamedTextColor.YELLOW),
                List.of(GuiUtil.grey("A társ eltűnik, de később újraidézhető."),
                        click("Elbocsátás"))), "DISMISS");
        set(inventory, holder, 30, GuiUtil.icon(Material.NAME_TAG,
                name("Átnevezés", NamedTextColor.AQUA),
                List.of(GuiUtil.grey("Chatből: /pet name <új név>"))), "HINT:name");
        if (selected != null) {
            set(inventory, holder, 31, GuiUtil.icon(Material.FLINT_AND_STEEL,
                    name("Végleges elengedés", NamedTextColor.DARK_RED),
                    List.of(GuiUtil.grey("Külön megerősítést kér."),
                            error("A társprofil végleg törlődik."))),
                    "REQUEST_RELEASE:" + selected.companion().companionId());
        }

        final MinionManager.Stance stance = pets.getStance(viewer);
        set(inventory, holder, 33, stanceTile(Material.IRON_SWORD, "Támadás",
                "Segít a célpontod ellen, és megvéd.", MinionManager.Stance.ACTIVE, stance),
                "STANCE:ACTIVE");
        set(inventory, holder, 34, stanceTile(Material.SHIELD, "Passzív",
                "Követ, de nem harcol.", MinionManager.Stance.PASSIVE, stance),
                "STANCE:PASSIVE");
        set(inventory, holder, 35, stanceTile(Material.ARMOR_STAND, "Maradj",
                "Helyben vár, amíg vissza nem hívod.", MinionManager.Stance.STAY, stance),
                "STANCE:STAY");

        inventory.setItem(40, upgradeGuide(selected));
        inventory.setItem(42, armorTile(viewer, pets));
        set(inventory, holder, 49, GuiUtil.icon(Material.BARRIER,
                name("Bezárás", NamedTextColor.RED), List.of()), "CLOSE");
        viewer.openInventory(inventory);
        GuiUtil.sound(viewer, GuiUtil.GuiSound.OPEN);
    }

    public static void openReleaseConfirmation(final Player viewer, final PetManager pets,
                                               final MessageManager messages,
                                               final UUID companionId) {
        final CompanionProgressView companion = pets.companionProgression(viewer, companionId)
                .orElse(null);
        if (companion == null) {
            open(viewer, pets, messages);
            return;
        }
        final PetGUIHolder holder = new PetGUIHolder(viewer.getUniqueId(),
                PetGUIHolder.Mode.RELEASE_CONFIRM, companionId);
        final Inventory inventory = createInventory(holder, viewer, pets, messages,
                "messages.pet-release-confirm-title", "Társ elengedése");
        fillThemed(inventory, pets.darkCompanionTheme(viewer));
        inventory.setItem(13, companionTile(companion, 1,
                pets.respawnRemainingSeconds(companion.companion())));
        set(inventory, holder, 20, GuiUtil.icon(Material.ARROW,
                name("Mégse", NamedTextColor.GREEN),
                List.of(GuiUtil.grey("A társad megmarad."))), "BACK");
        set(inventory, holder, 24, GuiUtil.icon(Material.LAVA_BUCKET,
                name("Elengedés megerősítése", NamedTextColor.DARK_RED),
                List.of(error("Ez nem vonható vissza."),
                        GuiUtil.grey("A szint, XP, felszerelés és mutáció elvész."),
                        click("Végleges elengedés"))), "CONFIRM_RELEASE:" + companionId);
        viewer.openInventory(inventory);
        GuiUtil.sound(viewer, GuiUtil.GuiSound.PAGE);
    }

    private static ItemStack companionTile(final CompanionProgressView view, final int index,
                                           final long respawnSeconds) {
        final List<Component> lore = new ArrayList<>();
        lore.add(GuiUtil.label("Szerep", name(view.roleName(), NamedTextColor.GRAY)));
        lore.add(GuiUtil.label("Forma", name(view.formName(), NamedTextColor.WHITE)));
        lore.add(GuiUtil.label("Szint", name(Integer.toString(view.level()), NamedTextColor.GREEN)));
        lore.add(GuiUtil.label("XP", name(view.maxLevelReached() ? "MAX"
                : view.experience() + " / " + view.nextLevelCost(), NamedTextColor.AQUA)));
        if (view.mutationMaximum() > 0) {
            lore.add(GuiUtil.label("Mutáció", name(view.mutationStage() + " / "
                    + view.mutationMaximum(), NamedTextColor.DARK_GREEN)));
        }
        if (view.live()) lore.add(ok("Kint van melletted"));
        else if (view.selected()) lore.add(ok("Kiválasztott társ"));
        else lore.add(click("Kiválasztás és idézés"));
        lore.add(GuiUtil.grey("Jobb katt: elengedés áttekintése."));
        if (respawnSeconds > 0L) lore.add(error("Újraidézhető " + respawnSeconds + " mp múlva."));
        return GuiUtil.icon(Material.BONE, name(index + ". " + view.displayName(),
                view.selected() ? NamedTextColor.GREEN : NamedTextColor.WHITE), lore, view.selected());
    }

    private static ItemStack overviewTile(final List<CompanionProgressView> roster,
                                          final CompanionProgressView selected) {
        final List<Component> lore = new ArrayList<>();
        lore.add(GuiUtil.label("Társlista", name(Integer.toString(roster.size()), NamedTextColor.WHITE)));
        lore.add(GuiUtil.label("Kiválasztva", name(selected == null ? "nincs"
                : selected.displayName(), selected == null ? NamedTextColor.GRAY : NamedTextColor.GREEN)));
        lore.add(GuiUtil.grey("Minden fejlődés a Profile v2 aktív loadoutjában él."));
        return GuiUtil.icon(Material.WRITABLE_BOOK,
                name("Társműhely", NamedTextColor.DARK_GREEN), lore);
    }

    private static ItemStack progressTile(final CompanionProgressView view) {
        final List<Component> lore = new ArrayList<>();
        lore.add(GuiUtil.label("Alapszint", name(Integer.toString(view.level()), NamedTextColor.GREEN)));
        lore.add(GuiUtil.label("Harci erőszint", name(Integer.toString(view.powerLevel()), NamedTextColor.GOLD)));
        if (view.maxLevelReached()) lore.add(ok("Elérte a maximális társszintet."));
        else lore.add(GuiUtil.grey("Következő szintig: " + view.experienceRemaining() + " XP"));
        if (!view.nextFormName().isBlank()) {
            lore.add(GuiUtil.grey("Következő forma: " + view.nextFormName()
                    + " (társszint " + view.nextFormLevel().orElseThrow() + ")"));
        } else if (CompanionProgressView.UNHOLY_GHOUL.equals(view.companion().namespace())
                || CompanionProgressView.DEMON_ROSTER.equals(view.companion().namespace())) {
            lore.add(ok("Végső forma elérve."));
        }
        return GuiUtil.icon(Material.EXPERIENCE_BOTTLE,
                name("Fejlődési lap", NamedTextColor.AQUA), lore);
    }

    private static ItemStack emptySelectionTile() {
        return GuiUtil.icon(Material.GLASS_BOTTLE, name("Nincs kiválasztott társ", NamedTextColor.GRAY),
                List.of(GuiUtil.grey("Válassz a felső sorból.")));
    }

    private static ItemStack upgradeGuide(final CompanionProgressView selected) {
        final List<Component> lore = new ArrayList<>();
        lore.add(GuiUtil.grey("A kint lévő társ által segített ölések társ-XP-t adnak."));
        lore.add(GuiUtil.grey("A szint automatikusan növeli az életerőt és a sebzést."));
        if (selected != null && CompanionProgressView.UNHOLY_GHOUL.equals(
                selected.companion().namespace())) {
            lore.add(GuiUtil.grey("A Dögvész kitörése tartós mutációs fokozatot ad."));
            lore.add(GuiUtil.grey("A mutáció erőszintet és korábbi formaváltást biztosít."));
        }
        return GuiUtil.icon(Material.NETHER_STAR, name("Hogyan fejleszthető?", NamedTextColor.GOLD), lore);
    }

    private static ItemStack stanceTile(final Material material, final String title,
                                        final String description, final MinionManager.Stance value,
                                        final MinionManager.Stance current) {
        return GuiUtil.icon(material, name(title, value == current ? NamedTextColor.GREEN
                        : NamedTextColor.GRAY), value == current
                        ? List.of(GuiUtil.grey(description), ok("Jelenlegi állásmód"))
                        : List.of(GuiUtil.grey(description)), value == current);
    }

    private static ItemStack armorTile(final Player viewer, final PetManager pets) {
        final boolean equipped = pets.hasPetArmor(viewer);
        return GuiUtil.icon(Material.LEATHER_HORSE_ARMOR,
                name("Társvért", equipped ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                List.of(GuiUtil.grey(equipped
                                ? "Felszerelve: páncélt és életerőt ad."
                                : "Ritka szörnyzsákmány; jobb katt a kint lévő társadon."),
                        GuiUtil.grey("A felszerelés újraidézés és formaváltás után is megmarad.")),
                equipped);
    }

    private static Inventory createInventory(final PetGUIHolder holder, final Player viewer,
                                             final PetManager pets, final MessageManager messages,
                                             final String messageKey, final String fallback) {
        final Component title = ClassUiAssets.title(ClassUiAssets.Surface.COMPANION,
                pets.darkCompanionTheme(viewer) ? FactionType.DARK : FactionType.NEUTRAL,
                messages.getComponent(messageKey, fallback));
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);
        return inventory;
    }

    private static void fillThemed(final Inventory inventory, final boolean dark) {
        final ItemStack filler = GuiUtil.icon(dark ? Material.PURPLE_STAINED_GLASS_PANE
                : Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler.clone());
    }

    private static Component name(final String text, final NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component click(final String text) { return name("» " + text, NamedTextColor.YELLOW); }
    private static Component ok(final String text) { return name("✔ " + text, NamedTextColor.GREEN); }
    private static Component error(final String text) { return name("✖ " + text, NamedTextColor.RED); }

    private static void set(final Inventory inventory, final PetGUIHolder holder, final int slot,
                            final ItemStack item, final String action) {
        inventory.setItem(slot, item);
        holder.mapAction(slot, action);
    }
}
