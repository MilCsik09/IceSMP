package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;
import hu.taliann.icesmp.managers.FactionManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Shared resource-pack contract for every first-party class/progression inventory. */
public final class ClassUiAssets {
    public enum Surface {
        WORKSHOP, PROFILE, CLASS_SELECT, SPELLBOOK, SKILL_TREE, TALENTS, DETAIL, COMPANION
    }

    private static final int BACKGROUND_BASE = 0xE390;
    private static final int BADGE_BASE = 0xE400;
    private static final int CLASS_BADGE_BASE = 0xE430;
    private static final Map<String, Integer> CLASS_BADGES = Map.ofEntries(
            Map.entry("warrior", 0), Map.entry("evoker", 1), Map.entry("archer", 2),
            Map.entry("shaman", 3), Map.entry("monk", 4), Map.entry("paladin", 5),
            Map.entry("demon_hunter", 6), Map.entry("druid", 7), Map.entry("priest", 8),
            Map.entry("death_knight", 9), Map.entry("assassin", 10), Map.entry("warlock", 11),
            Map.entry("wizard", 12)
    );
    private static final Map<String, Integer> BADGES = Map.ofEntries(
            Map.entry("berserker", 0), Map.entry("retribution", 1), Map.entry("necromancer", 2),
            Map.entry("beast_master", 3), Map.entry("phantom", 4), Map.entry("holy", 5),
            Map.entry("elemental", 6), Map.entry("elementalist", 7), Map.entry("demonologist", 8),
            Map.entry("windwalker", 9), Map.entry("ironbark", 10), Map.entry("havoc", 11),
            Map.entry("devastation", 12), Map.entry("blood", 13), Map.entry("frost", 14),
            Map.entry("plaguebringer", 15), Map.entry("discipline", 16), Map.entry("protection", 17),
            Map.entry("guardian", 18), Map.entry("feral", 19), Map.entry("sharpshooter", 20),
            Map.entry("shadow", 21), Map.entry("tidal", 22), Map.entry("enhancement", 23),
            Map.entry("restoration", 24), Map.entry("lunar", 25), Map.entry("affliction", 26),
            Map.entry("bone_priest", 27), Map.entry("destruction", 28), Map.entry("brewmaster", 29),
            Map.entry("vengeance", 30), Map.entry("mistweaver", 31), Map.entry("preservation", 32),
            Map.entry("unholy", 33), Map.entry("poisoner", 34)
    );

    private ClassUiAssets() { }

    public static Component title(final Surface surface, final FactionType faction,
                                  final Component visibleTitle) {
        final int codepoint = BACKGROUND_BASE + surface.ordinal() * 4 + themeIndex(faction);
        return Component.text(String.valueOf((char) codepoint))
                .font(Key.key("icesmp_hud", "class_ui")).color(NamedTextColor.WHITE)
                .append(Component.text(String.valueOf((char) 0xE550))
                        .font(Key.key("icesmp_hud", "space")))
                .append(visibleTitle == null ? Component.empty() : visibleTitle);
    }

    public static Component title(final Surface surface, final Component visibleTitle) {
        return title(surface, FactionType.NEUTRAL, visibleTitle);
    }

    public static FactionType faction(final Player player, final CharacterMenuContext context) {
        if (player == null || context == null) return FactionType.NEUTRAL;
        return faction(player, context.factionManager());
    }

    public static FactionType faction(final Player player, final FactionManager factionManager) {
        if (player == null || factionManager == null) return FactionType.NEUTRAL;
        return factionManager.getChosenFaction(player.getUniqueId()).orElse(FactionType.NEUTRAL);
    }

    public static void fill(final Inventory inventory, final FactionType faction) {
        if (inventory == null) return;
        final Material material = switch (faction == null ? FactionType.NEUTRAL : faction) {
            case RED -> Material.RED_STAINED_GLASS_PANE;
            case BLUE -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case DARK -> Material.PURPLE_STAINED_GLASS_PANE;
            case NEUTRAL -> Material.GRAY_STAINED_GLASS_PANE;
        };
        final ItemStack filler = GuiUtil.icon(material, Component.empty(), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler.clone());
    }

    public static Component badge(final SpecializationType specialization) {
        if (specialization == null) return Component.empty();
        final Integer index = BADGES.get(specialization.getId());
        if (index == null) return Component.empty();
        return Component.text(String.valueOf((char) (BADGE_BASE + index)))
                .font(Key.key("icesmp_hud", "specialization_badge"))
                .color(NamedTextColor.WHITE);
    }

    public static Component badgeName(final SpecializationType specialization) {
        if (specialization == null) return Component.text("nincs", NamedTextColor.GRAY);
        return badge(specialization).append(Component.space()).append(specialization.getDisplayName());
    }

    public static Component classBadge(final JobType job) {
        if (job == null) return Component.empty();
        final Integer index = CLASS_BADGES.get(job.getId());
        if (index == null) return Component.empty();
        return Component.text(String.valueOf((char) (CLASS_BADGE_BASE + index)))
                .font(Key.key("icesmp_hud", "class_badge")).color(NamedTextColor.WHITE);
    }

    public static Component classBadgeName(final JobType job) {
        if (job == null) return Component.text("nincs", NamedTextColor.GRAY);
        return classBadge(job).append(Component.space()).append(job.getDisplayName());
    }

    private static int themeIndex(final FactionType faction) {
        return switch (faction == null ? FactionType.NEUTRAL : faction) {
            case RED -> 0;
            case BLUE -> 1;
            case NEUTRAL -> 2;
            case DARK -> 3;
        };
    }
}
