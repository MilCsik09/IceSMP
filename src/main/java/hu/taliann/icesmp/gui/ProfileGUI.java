package hu.taliann.icesmp.gui;

import static hu.taliann.icesmp.gui.GuiUtil.accent;
import static hu.taliann.icesmp.gui.GuiUtil.grey;
import static hu.taliann.icesmp.gui.GuiUtil.label;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.ProfessionCategory;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.managers.JobManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Character hub (/profile). Shows a live summary of the player (faction, classes,
 * specializations, professions, talents, balances, status) and routes to the
 * interactive sub-menus: kaszt, specializáció, szakma, talentek, képesség-fa.
 */
@SuppressWarnings("deprecation")
public final class ProfileGUI {

    private static final int SIZE = 36;
    public static final int HEAD_SLOT = 4;
    public static final int JOB_SLOT = 11;
    public static final int SPEC_SLOT = 13;
    public static final int PROFESSION_SLOT = 15;
    public static final int TALENT_SLOT = 20;
    public static final int SKILLTREE_SLOT = 22;
    public static final int ECONOMY_SLOT = 24;
    public static final int MENU_SLOT = 27;
    public static final int CLOSE_SLOT = 31;

    private ProfileGUI() {
    }

    public static void open(final Player viewer, final CharacterMenuContext ctx) {
        if (viewer == null || ctx == null) {
            return;
        }

        final Component title = ctx.messageManager().getMessage("system.profile.gui.title", "<dark_aqua>» Karakterlap «</dark_aqua>");
        final ProfileHolder holder = new ProfileHolder(viewer.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, SIZE, title);
        holder.setInventory(inventory);

        GuiUtil.fill(inventory);
        inventory.setItem(HEAD_SLOT, createHead(viewer, ctx));
        inventory.setItem(JOB_SLOT, GuiUtil.icon(Material.ENCHANTED_BOOK,
                accent("Kasztok"),
                List.of(grey("A kasztod, szintezés"), grey("és Lélekkapocs."), Component.empty(), click("Megnyitás"))));
        inventory.setItem(SPEC_SLOT, GuiUtil.icon(Material.NETHER_STAR,
                accent("Specializációk"),
                List.of(grey("Kaszt- és szakma-specializáció"), grey("kiválasztása és visszaváltása."), Component.empty(), click("Megnyitás"))));
        inventory.setItem(PROFESSION_SLOT, GuiUtil.icon(Material.IRON_PICKAXE,
                accent("Szakmák"),
                List.of(grey("Gyűjtögető és készítő szakma"), grey("választása, szintek."), Component.empty(), click("Megnyitás"))));
        inventory.setItem(TALENT_SLOT, GuiUtil.icon(Material.EXPERIENCE_BOTTLE,
                accent("Talentek"),
                List.of(grey("Talentpontok elköltése a kaszt-"), grey("és szakma-fádon."), Component.empty(), click("Megnyitás"))));
        inventory.setItem(SKILLTREE_SLOT, GuiUtil.icon(Material.KNOWLEDGE_BOOK,
                accent("Képesség-fa"),
                List.of(grey("A kasztod és specializációd"), grey("képességei feloldási szint szerint."), Component.empty(), click("Megnyitás"))));
        inventory.setItem(ECONOMY_SLOT, createEconomy(viewer, ctx));
        inventory.setItem(MENU_SLOT, GuiUtil.icon(Material.ARROW,
                Component.text("Főmenü", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                List.of(grey("Vissza a /menu főmenübe."))));
        inventory.setItem(CLOSE_SLOT, GuiUtil.icon(Material.BARRIER,
                Component.text("Bezárás", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()));

        viewer.openInventory(inventory);
    }

    public static void closeAll() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (isProfileInventory(player.getOpenInventory().getTopInventory())) {
                player.closeInventory();
            }
        }
    }

    public static boolean isProfileInventory(final Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof ProfileHolder;
    }

    private static ItemStack createHead(final Player target, final CharacterMenuContext ctx) {
        final ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = itemStack.getItemMeta();

        final List<Component> lore = buildHeadLore(target, ctx);
        final Component name = Component.text("» " + target.getName() + " «", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false);

        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(target);
            skullMeta.displayName(name);
            skullMeta.lore(lore);
            skullMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
            itemStack.setItemMeta(skullMeta);
            return itemStack;
        }

        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private static List<Component> buildHeadLore(final Player target, final CharacterMenuContext ctx) {
        final List<Component> lore = new ArrayList<>();

        final FactionType faction = ctx.factionManager().getChosenFaction(target.getUniqueId()).orElse(null);
        lore.add(label("Frakció", Component.text(faction == null ? "Menedék vendége"
                : faction.getDisplayName() + " (" + faction.getFullName() + ")", NamedTextColor.WHITE)));

        final JobType primary = ctx.jobManager().getPrimaryJob(target);
        if (primary == null) {
            lore.add(label("Kaszt", Component.text("nincs", NamedTextColor.GRAY)));
        } else {
            lore.add(label("Kaszt", primary.getDisplayName()
                    .append(Component.text(" (Lvl " + ctx.jobManager().getPrimaryLevel(target) + "/" + JobManager.MAX_JOB_LEVEL + ")", NamedTextColor.WHITE))));
        }
        final SpecializationType classSpec = ctx.specializationManager().getClassSpecialization(target);
        lore.add(label("Kaszt-spec", classSpec == null ? Component.text("nincs", NamedTextColor.GRAY) : classSpec.getDisplayName()));

        final ProfessionType gathering = ctx.professionManager().getProfession(target, ProfessionCategory.GATHERING);
        final ProfessionType crafting = ctx.professionManager().getProfession(target, ProfessionCategory.CRAFTING);
        lore.add(label("Gyűjtő szakma", professionValue(target, gathering, ctx)));
        lore.add(label("Készítő szakma", professionValue(target, crafting, ctx)));

        final ProfessionSpecializationType profSpec = ctx.specializationManager().getProfessionSpecialization(target);
        lore.add(label("Szakma-spec", profSpec == null ? Component.text("nincs", NamedTextColor.GRAY) : profSpec.getDisplayName()));

        final boolean sinner = ctx.sinManager() != null && ctx.sinManager().isSinner(target);
        lore.add(label("Állapot", sinner
                ? Component.text("Bűnös", NamedTextColor.RED)
                : Component.text("Tiszta", NamedTextColor.GREEN)));

        lore.add(label("Talentpont", Component.text(
                "kaszt " + ctx.talentManager().getAvailablePoints(target, true)
                        + " • szakma " + ctx.talentManager().getAvailablePoints(target, false), NamedTextColor.WHITE)));

        return lore;
    }

    private static Component professionValue(final Player target, final ProfessionType profession,
                                             final CharacterMenuContext ctx) {
        if (profession == null) {
            return Component.text("nincs", NamedTextColor.GRAY);
        }
        return profession.getDisplayName()
                .append(Component.text(" (Lvl " + ctx.professionManager().getLevel(target, profession) + ")", NamedTextColor.WHITE));
    }

    private static ItemStack createEconomy(final Player target, final CharacterMenuContext ctx) {
        final List<Component> lore = new ArrayList<>();
        for (final FactionType type : FactionType.values()) {
            lore.add(label(type.getDisplayName(), Component.text(
                    ctx.currencyManager().formatBalance(ctx.currencyManager().getBalance(target, type)), NamedTextColor.WHITE)));
        }
        lore.add(Component.empty());
        lore.add(grey("Részletek: /bank • /currency"));
        return GuiUtil.icon(Material.GOLD_INGOT, accent("Egyenlegek"), lore);
    }

    private static Component click(final String text) {
        return Component.text("» " + text, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
    }
}
