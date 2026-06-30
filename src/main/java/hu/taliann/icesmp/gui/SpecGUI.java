package hu.taliann.icesmp.gui;

import static hu.taliann.icesmp.gui.GuiUtil.accent;
import static hu.taliann.icesmp.gui.GuiUtil.grey;
import static hu.taliann.icesmp.gui.GuiUtil.label;

import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.data.JobType;
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
 * Specialization menu: pick (and respec) the class specialization for the primary
 * job and the single profession specialization across the player's professions.
 */
public final class SpecGUI {

    private static final int SIZE = 54;
    private static final int HEADER_SLOT = 4;
    private static final int[] CLASS_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] PROF_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int RESPEC_CLASS_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int RESPEC_PROF_SLOT = 53;

    private SpecGUI() {
    }

    public static int getBackSlot() {
        return BACK_SLOT;
    }

    public static int getRespecClassSlot() {
        return RESPEC_CLASS_SLOT;
    }

    public static int getRespecProfSlot() {
        return RESPEC_PROF_SLOT;
    }

    public static void open(final Player viewer, final CharacterMenuContext ctx) {
        if (viewer == null || ctx == null) {
            return;
        }

        final SpecHolder holder = new SpecHolder(viewer.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE,
                ctx.messageManager().getMessage("spec-gui-title", "<dark_aqua>» Specializációk «</dark_aqua>"));
        holder.setInventory(inventory);

        GuiUtil.fill(inventory);
        inventory.setItem(HEADER_SLOT, createHeader(viewer, ctx));

        final List<SpecializationType> classSpecs = classSpecOptions(viewer, ctx);
        for (int i = 0; i < classSpecs.size() && i < CLASS_SLOTS.length; i++) {
            inventory.setItem(CLASS_SLOTS[i], createClassSpecItem(viewer, ctx, classSpecs.get(i)));
        }

        final List<ProfessionSpecializationType> profSpecs = professionSpecOptions(viewer, ctx);
        for (int i = 0; i < profSpecs.size() && i < PROF_SLOTS.length; i++) {
            inventory.setItem(PROF_SLOTS[i], createProfSpecItem(viewer, ctx, profSpecs.get(i)));
        }

        inventory.setItem(RESPEC_CLASS_SLOT, createRespecButton(ctx, true));
        inventory.setItem(RESPEC_PROF_SLOT, createRespecButton(ctx, false));
        inventory.setItem(BACK_SLOT, GuiUtil.icon(Material.ARROW,
                Component.text("Vissza", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()));

        viewer.openInventory(inventory);
    }

    /** Class specs selectable from the player's primary job, in enum order. */
    public static List<SpecializationType> classSpecOptions(final Player player, final CharacterMenuContext ctx) {
        final List<SpecializationType> options = new ArrayList<>();
        final JobType primary = ctx.jobManager().getPrimaryJob(player);
        if (primary == null) {
            return options;
        }
        for (final SpecializationType spec : SpecializationType.values()) {
            if (spec.getParentJob() == primary) {
                options.add(spec);
            }
        }
        return options;
    }

    /** Profession specs whose parent profession the player actively practices. */
    public static List<ProfessionSpecializationType> professionSpecOptions(final Player player, final CharacterMenuContext ctx) {
        final List<ProfessionType> active = ctx.professionManager().getActiveProfessions(player);
        final List<ProfessionSpecializationType> options = new ArrayList<>();
        for (final ProfessionSpecializationType spec : ProfessionSpecializationType.values()) {
            if (active.contains(spec.getParentProfession())) {
                options.add(spec);
            }
        }
        return options;
    }

    public static SpecializationType resolveClassSpec(final Player player, final CharacterMenuContext ctx, final int rawSlot) {
        final int index = indexOf(CLASS_SLOTS, rawSlot);
        if (index < 0) {
            return null;
        }
        final List<SpecializationType> options = classSpecOptions(player, ctx);
        return index < options.size() ? options.get(index) : null;
    }

    public static ProfessionSpecializationType resolveProfSpec(final Player player, final CharacterMenuContext ctx, final int rawSlot) {
        final int index = indexOf(PROF_SLOTS, rawSlot);
        if (index < 0) {
            return null;
        }
        final List<ProfessionSpecializationType> options = professionSpecOptions(player, ctx);
        return index < options.size() ? options.get(index) : null;
    }

    private static ItemStack createHeader(final Player player, final CharacterMenuContext ctx) {
        final SpecializationType classSpec = ctx.specializationManager().getClassSpecialization(player);
        final ProfessionSpecializationType profSpec = ctx.specializationManager().getProfessionSpecialization(player);
        final List<Component> lore = List.of(
                label("Kaszt-spec", classSpec == null ? none() : classSpec.getDisplayName()),
                label("Szakma-spec", profSpec == null ? none() : profSpec.getDisplayName()),
                Component.empty(),
                grey("Kaszt-spec szint " + ctx.specializationManager().getRequiredClassLevel() + "-tól,"),
                grey("szakma-spec szint " + ctx.specializationManager().getRequiredProfessionLevel() + "-tól érhető el."),
                grey("Egy specializáció felülírásához respec kell.")
        );
        return GuiUtil.icon(Material.NETHER_STAR, accent("Specializációid"), lore);
    }

    private static ItemStack createClassSpecItem(final Player player, final CharacterMenuContext ctx, final SpecializationType spec) {
        final SpecializationType current = ctx.specializationManager().getClassSpecialization(player);
        final boolean selected = current == spec;
        final boolean available = current == null && ctx.specializationManager().canSelectClassSpecialization(player, spec);

        final List<Component> lore = new ArrayList<>();
        if (selected) {
            lore.add(Component.text("✔ Aktív specializáció", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else if (available) {
            lore.add(click("Kattints a kiválasztáshoz"));
        } else if (current != null) {
            lore.add(Component.text("Másik spec aktív — respec szükséges", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Nem elérhető", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            lore.add(grey("Szükséges szint: " + ctx.specializationManager().getRequiredClassLevel()));
            if (spec.getRequiredFaction() != null) {
                lore.add(grey("Frakció: " + spec.getRequiredFaction().getDisplayName()));
            }
            if (spec.requiresSinner()) {
                lore.add(grey("Bűnös állapot szükséges"));
            }
        }

        final Material material = selected ? Material.ENCHANTED_BOOK : (available ? Material.WRITABLE_BOOK : Material.BOOK);
        return GuiUtil.icon(material, name(spec.getDisplayName()), lore, selected);
    }

    private static ItemStack createProfSpecItem(final Player player, final CharacterMenuContext ctx, final ProfessionSpecializationType spec) {
        final ProfessionSpecializationType current = ctx.specializationManager().getProfessionSpecialization(player);
        final boolean selected = current == spec;
        final boolean available = current == null && ctx.specializationManager().canSelectProfessionSpecialization(player, spec);

        final List<Component> lore = new ArrayList<>();
        lore.add(grey("Szakma: ").append(spec.getParentProfession().getDisplayName()));
        if (selected) {
            lore.add(Component.text("✔ Aktív specializáció", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else if (available) {
            lore.add(click("Kattints a kiválasztáshoz"));
        } else if (current != null) {
            lore.add(Component.text("Másik spec aktív — respec szükséges", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Nem elérhető", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            lore.add(grey("Szükséges szint: " + ctx.specializationManager().getRequiredProfessionLevel()));
        }

        final Material material = selected ? Material.ENCHANTED_BOOK : (available ? Material.WRITABLE_BOOK : Material.BOOK);
        return GuiUtil.icon(material, name(spec.getDisplayName()), lore, selected);
    }

    private static ItemStack createRespecButton(final CharacterMenuContext ctx, final boolean classPool) {
        final String which = classPool ? "kaszt-specializáció" : "szakma-specializáció";
        return GuiUtil.icon(Material.GRINDSTONE,
                Component.text("Respec (" + which + ")", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                List.of(
                        grey("Visszaváltja a jelenlegi " + which + "t,"),
                        grey("hogy újat választhass."),
                        grey("Ár: " + ctx.currencyManager().formatBalance(ctx.specializationManager().getRespecCost())
                                + " a frakcióvalutádból."),
                        Component.empty(),
                        click("Kattints a respec-hez")
                ));
    }

    private static int indexOf(final int[] slots, final int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static Component name(final Component display) {
        return display.decoration(TextDecoration.ITALIC, false);
    }

    private static Component none() {
        return Component.text("nincs", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static Component click(final String text) {
        return Component.text("» " + text, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
    }
}
