package hu.taliann.icesmp.gui;

import static hu.taliann.icesmp.gui.GuiUtil.label;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Talent TREE menu. Talents are laid out top-to-bottom by their {@code tier}
 * (row = tier), and a child talent stays locked until its {@code requires-talent}
 * parent has at least one rank — so the panel reads as a branching tree rather
 * than a flat list. The class tree fills the upper rows, the profession tree the
 * lower rows.
 */
public final class TalentGUI {

    private static final int SIZE = 54;
    private static final int HEADER_SLOT = 4;
    private static final int CLASS_LABEL_SLOT = 0;
    private static final int PROF_LABEL_SLOT = 45;
    private static final int BACK_SLOT = 53;
    private static final int[] CLASS_ROWS = {1, 2, 3};
    private static final int[] PROF_ROWS = {4, 5};
    private static final int COLUMNS = 7;

    /** A placed talent node: which pool it belongs to and its id. */
    public record Node(boolean classPool, String id) {
    }

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
                ctx.messageManager().getMessage("talent-gui-title", "<dark_aqua>» Talent-fa «</dark_aqua>"));
        holder.setInventory(inventory);

        GuiUtil.fill(inventory);
        inventory.setItem(HEADER_SLOT, GuiUtil.icon(Material.EXPERIENCE_BOTTLE, accent("Talent-fa"),
                List.of(
                        grey("Felül a kaszt-, alul a szakma-fa."),
                        grey("A fa felülről lefelé épül: egy talent csak akkor"),
                        grey("oldódik fel, ha a fölötte lévő szülőjébe már tettél pontot."),
                        Component.empty(),
                        grey("Pontot szintlépéskor kapsz.")
                )));
        inventory.setItem(CLASS_LABEL_SLOT, GuiUtil.icon(Material.IRON_SWORD,
                accent("Kaszt-talentek"),
                List.of(label("Pont", Component.text(String.valueOf(ctx.talentManager().getAvailablePoints(viewer, true)), NamedTextColor.WHITE)))));
        inventory.setItem(PROF_LABEL_SLOT, GuiUtil.icon(Material.IRON_PICKAXE,
                accent("Szakma-talentek"),
                List.of(label("Pont", Component.text(String.valueOf(ctx.talentManager().getAvailablePoints(viewer, false)), NamedTextColor.WHITE)))));

        for (final Map.Entry<Integer, Node> entry : layout(viewer, ctx).entrySet()) {
            inventory.setItem(entry.getKey(), createTalentItem(viewer, ctx, entry.getValue()));
        }

        inventory.setItem(BACK_SLOT, GuiUtil.icon(Material.ARROW,
                Component.text("Vissza", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()));

        viewer.openInventory(inventory);
    }

    public static Node resolveTalent(final Player player, final CharacterMenuContext ctx, final int rawSlot) {
        return layout(player, ctx).get(rawSlot);
    }

    /** Deterministic slot→talent layout shared by render and click handling. */
    private static Map<Integer, Node> layout(final Player player, final CharacterMenuContext ctx) {
        final Map<Integer, Node> map = new LinkedHashMap<>();
        placePool(map, player, ctx, true, CLASS_ROWS);
        placePool(map, player, ctx, false, PROF_ROWS);
        return map;
    }

    private static void placePool(final Map<Integer, Node> map, final Player player, final CharacterMenuContext ctx,
                                  final boolean classPool, final int[] rows) {
        final ConfigurationSection definitions = ctx.talentManager().getDefinitions(classPool);
        if (definitions == null) {
            return;
        }

        // Group the visible talents by tier (ascending), preserving config order within a tier.
        final TreeMap<Integer, List<String>> byTier = new TreeMap<>();
        for (final String id : definitions.getKeys(false)) {
            if (!ctx.talentManager().isAvailable(player, classPool, id)) {
                continue;
            }
            final ConfigurationSection section = definitions.getConfigurationSection(id);
            final int tier = section == null ? 1 : Math.max(1, section.getInt("tier", 1));
            byTier.computeIfAbsent(tier, key -> new ArrayList<>()).add(id);
        }

        int rowIndex = 0;
        for (final List<String> tierTalents : byTier.values()) {
            if (rowIndex >= rows.length) {
                break;
            }
            placeRow(map, tierTalents, rows[rowIndex++], classPool);
        }
    }

    private static void placeRow(final Map<Integer, Node> map, final List<String> ids, final int row, final boolean classPool) {
        final int count = Math.min(ids.size(), COLUMNS);
        final int base = row * 9;
        final int start = 1 + (COLUMNS - count) / 2;
        for (int i = 0; i < count; i++) {
            map.put(base + start + i, new Node(classPool, ids.get(i)));
        }
    }

    private static ItemStack createTalentItem(final Player player, final CharacterMenuContext ctx, final Node node) {
        final ConfigurationSection definitions = ctx.talentManager().getDefinitions(node.classPool());
        final ConfigurationSection talent = definitions == null ? null : definitions.getConfigurationSection(node.id());
        if (talent == null) {
            return GuiUtil.filler();
        }

        final int rank = ctx.talentManager().getRank(player, node.classPool(), node.id());
        final int maxRank = Math.max(1, talent.getInt("max-rank", 1));
        final int points = ctx.talentManager().getAvailablePoints(player, node.classPool());
        final boolean unlocked = ctx.talentManager().isUnlocked(player, node.classPool(), node.id());
        final boolean maxed = rank >= maxRank;

        final List<Component> lore = new ArrayList<>();
        lore.add(label("Szint (tier)", Component.text(String.valueOf(Math.max(1, talent.getInt("tier", 1))), NamedTextColor.WHITE)));
        lore.add(label("Rang", Component.text(rank + "/" + maxRank, NamedTextColor.WHITE)));
        lore.add(label("Hatás", Component.text(effectLabel(talent.getString("effect", "?"))
                + " +" + talent.getDouble("per-rank", 0.0D) + "/rang", NamedTextColor.WHITE)));
        final boolean active = talent.getString("grants-spell") != null && !talent.getString("grants-spell").isBlank();
        if (active) {
            lore.add(Component.text("★ Aktív talent — képességet old fel", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        }
        final List<String> excludes = talent.getStringList("excludes");
        if (!excludes.isEmpty()) {
            lore.add(grey("Kizárja: " + excludeNames(definitions, excludes)));
        }
        lore.add(Component.empty());

        final Material material;
        if (!unlocked) {
            material = Material.GRAY_DYE;
            lore.add(Component.text("🔒 Zárolva", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            final String parentId = talent.getString("requires-talent");
            if (parentId != null && !parentId.isBlank() && definitions.getConfigurationSection(parentId) != null) {
                lore.add(grey("Előbb fejleszd: " + definitions.getConfigurationSection(parentId).getString("display-name", parentId)));
            }
            for (final String excluded : excludes) {
                if (ctx.talentManager().getRank(player, node.classPool(), excluded) > 0) {
                    lore.add(grey("A választott ággal kizárt: " + nameOf(definitions, excluded)));
                }
            }
            final int requiresSpent = Math.max(0, talent.getInt("requires-spent", 0));
            if (requiresSpent > 0) {
                lore.add(grey("Szükséges elköltött pont: " + requiresSpent
                        + " (jelenleg " + ctx.talentManager().getSpentPoints(player, node.classPool()) + ")"));
            }
        } else if (maxed) {
            material = Material.GLOWSTONE_DUST;
            lore.add(Component.text("✔ Maximális rang", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else if (points <= 0) {
            material = Material.EXPERIENCE_BOTTLE;
            lore.add(Component.text("Nincs elkölthető talentpontod", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            material = Material.EXPERIENCE_BOTTLE;
            lore.add(click("Kattints a fejlesztéshez"));
        }

        final NamedTextColor nameColor = unlocked ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY;
        return GuiUtil.icon(material,
                Component.text(talent.getString("display-name", node.id()), nameColor).decoration(TextDecoration.ITALIC, false),
                lore, rank > 0);
    }

    private static String effectLabel(final String effect) {
        return switch (effect) {
            case "max-health" -> "Max élet";
            case "movement-speed" -> "Sebesség";
            case "attack-damage" -> "Sebzés";
            case "class-xp-bonus" -> "Kaszt XP";
            case "profession-xp-bonus" -> "Szakma XP";
            default -> effect;
        };
    }

    private static String excludeNames(final ConfigurationSection definitions, final List<String> ids) {
        final StringBuilder builder = new StringBuilder();
        for (final String id : ids) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(nameOf(definitions, id));
        }
        return builder.toString();
    }

    private static String nameOf(final ConfigurationSection definitions, final String id) {
        final ConfigurationSection section = definitions == null ? null : definitions.getConfigurationSection(id);
        return section == null ? id : section.getString("display-name", id);
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
