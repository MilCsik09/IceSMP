package hu.taliann.icesmp.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Talent menu: spend class and profession talent points on the config-driven
 * talent definitions. Only talents whose WoW-style requirements are met show up
 * (mirrors {@code /talent} list gating).
 */
public final class TalentGUI {

    private static final int SIZE = 54;
    private static final int HEADER_SLOT = 4;
    private static final int BACK_SLOT = 49;
    private static final int[] CLASS_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int[] PROF_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private TalentGUI() {
    }

    public static int getBackSlot() {
        return BACK_SLOT;
    }

    public static void open(final Player viewer, final CharacterMenuContext ctx) {
        if (viewer == null || ctx == null) {
            return;
        }

        final TalentHolder holder = new TalentHolder(viewer.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE,
                ctx.messageManager().getMessage("talent-gui-title", "<dark_aqua>» Talentek «</dark_aqua>"));
        holder.setInventory(inventory);

        GuiUtil.fill(inventory);
        inventory.setItem(HEADER_SLOT, GuiUtil.icon(Material.EXPERIENCE_BOTTLE, accent("Talentpontok"),
                List.of(
                        label("Kaszt-pont", Component.text(String.valueOf(ctx.talentManager().getAvailablePoints(viewer, true)), NamedTextColor.WHITE)),
                        label("Szakma-pont", Component.text(String.valueOf(ctx.talentManager().getAvailablePoints(viewer, false)), NamedTextColor.WHITE)),
                        Component.empty(),
                        grey("Felül a kaszt-, alul a szakma-talentek."),
                        grey("Pontot szintlépéskor kapsz.")
                )));

        final List<String> classTalents = availableTalentIds(viewer, ctx, true);
        for (int i = 0; i < classTalents.size() && i < CLASS_SLOTS.length; i++) {
            inventory.setItem(CLASS_SLOTS[i], createTalentItem(viewer, ctx, true, classTalents.get(i)));
        }
        final List<String> profTalents = availableTalentIds(viewer, ctx, false);
        for (int i = 0; i < profTalents.size() && i < PROF_SLOTS.length; i++) {
            inventory.setItem(PROF_SLOTS[i], createTalentItem(viewer, ctx, false, profTalents.get(i)));
        }

        inventory.setItem(BACK_SLOT, GuiUtil.icon(Material.ARROW,
                Component.text("Vissza", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()));

        viewer.openInventory(inventory);
    }

    public static List<String> availableTalentIds(final Player player, final CharacterMenuContext ctx, final boolean classPool) {
        final List<String> ids = new ArrayList<>();
        final ConfigurationSection definitions = ctx.talentManager().getDefinitions(classPool);
        if (definitions == null) {
            return ids;
        }
        for (final String talentId : definitions.getKeys(false)) {
            if (ctx.talentManager().isAvailable(player, classPool, talentId)) {
                ids.add(talentId);
            }
        }
        return ids;
    }

    public static String resolveTalent(final Player player, final CharacterMenuContext ctx, final boolean classPool, final int rawSlot) {
        final int[] slots = classPool ? CLASS_SLOTS : PROF_SLOTS;
        final int index = indexOf(slots, rawSlot);
        if (index < 0) {
            return null;
        }
        final List<String> ids = availableTalentIds(player, ctx, classPool);
        return index < ids.size() ? ids.get(index) : null;
    }

    public static boolean isClassSlot(final int rawSlot) {
        return indexOf(CLASS_SLOTS, rawSlot) >= 0;
    }

    public static boolean isProfSlot(final int rawSlot) {
        return indexOf(PROF_SLOTS, rawSlot) >= 0;
    }

    private static ItemStack createTalentItem(final Player player, final CharacterMenuContext ctx, final boolean classPool, final String talentId) {
        final ConfigurationSection definitions = ctx.talentManager().getDefinitions(classPool);
        final ConfigurationSection talent = definitions == null ? null : definitions.getConfigurationSection(talentId);
        if (talent == null) {
            return GuiUtil.filler();
        }

        final int rank = ctx.talentManager().getRank(player, classPool, talentId);
        final int maxRank = talent.getInt("max-rank", 1);
        final int points = ctx.talentManager().getAvailablePoints(player, classPool);
        final boolean maxed = rank >= maxRank;

        final List<Component> lore = new ArrayList<>();
        lore.add(label("Rang", Component.text(rank + "/" + maxRank, NamedTextColor.WHITE)));
        lore.add(label("Hatás", Component.text(talent.getString("effect", "?")
                + " +" + talent.getDouble("per-rank", 0.0D) + "/rang", NamedTextColor.WHITE)));
        lore.add(Component.empty());
        if (maxed) {
            lore.add(Component.text("✔ Maximális rang", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else if (points <= 0) {
            lore.add(Component.text("Nincs elkölthető talentpontod", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(click("Kattints a fejlesztéshez"));
        }

        final Material material = maxed ? Material.GLOWSTONE_DUST : Material.EXPERIENCE_BOTTLE;
        return GuiUtil.icon(material,
                Component.text(talent.getString("display-name", talentId), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                lore, rank > 0);
    }

    private static int indexOf(final int[] slots, final int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static Component label(final String key, final Component value) {
        return Component.text(key + ": ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).append(value);
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
