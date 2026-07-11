package hu.taliann.icesmp.gui;

import static hu.taliann.icesmp.gui.GuiUtil.accent;
import static hu.taliann.icesmp.gui.GuiUtil.grey;
import static hu.taliann.icesmp.gui.GuiUtil.icon;
import static hu.taliann.icesmp.gui.GuiUtil.label;

import hu.taliann.icesmp.managers.QuestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin quest-builder screens (/quest admin builder &lt;id&gt;): an editor with one
 * tile per editable field (clicking most tiles asks for the new value in chat)
 * and an objective-type picker used both when creating a brand new quest and
 * when adding an extra objective. Rendering only — the click handling and the
 * chat-input flow live in {@code QuestBuilderListener}, the actual writes go
 * through the {@link QuestManager} admin API.
 */
public final class QuestBuilderGUI {

    /** A chat-input field tile: slot, config path and the Hungarian label shown. */
    private record FieldTile(int slot, String field, Material material, String name) {
    }

    private static final List<FieldTile> TEXT_FIELDS = List.of(
            new FieldTile(10, "display-name", Material.NAME_TAG, "Megjelenő név"),
            new FieldTile(11, "description", Material.WRITABLE_BOOK, "Leírás"),
            new FieldTile(12, "giver-npc", Material.VILLAGER_SPAWN_EGG, "Küldetésadó NPC"),
            new FieldTile(13, "cooldown-hours", Material.CLOCK, "Újrafelvétel (óra)"),
            new FieldTile(19, "requires-level", Material.EXPERIENCE_BOTTLE, "Szint-feltétel"),
            new FieldTile(20, "requires-quest", Material.CHAIN, "Előfeltétel-küldetés"),
            new FieldTile(21, "requires-faction", Material.WHITE_BANNER, "Frakció-feltétel"),
            new FieldTile(22, "requires-job", Material.IRON_PICKAXE, "Kaszt-feltétel"),
            new FieldTile(23, "auto-start-territory", Material.COMPASS, "Auto-start terület"),
            new FieldTile(28, "rewards.class-xp", Material.GLOWSTONE_DUST, "Jutalom: kaszt-XP"),
            new FieldTile(29, "rewards.currency.type", Material.GOLD_NUGGET, "Jutalom: valuta típusa"),
            new FieldTile(30, "rewards.currency.amount", Material.GOLD_INGOT, "Jutalom: valuta összege"),
            new FieldTile(31, "rewards.unlock-spell", Material.ENCHANTED_BOOK, "Jutalom: spell feloldása"),
            new FieldTile(33, "rewards.items", Material.CHEST, "Jutalom: tárgyak"),
            new FieldTile(37, "dialogue.speaker", Material.OAK_SIGN, "Dialógus: beszélő"),
            new FieldTile(38, "dialogue.give", Material.PAPER, "Dialógus: felvételkor"),
            new FieldTile(39, "dialogue.complete", Material.FILLED_MAP, "Dialógus: teljesítéskor"));

    /** Objective type → icon + Hungarian label for the picker (insertion order = display order). */
    private static final Map<String, Material> TYPE_ICONS = new LinkedHashMap<>();
    private static final Map<String, String> TYPE_NAMES = new LinkedHashMap<>();

    static {
        putType("KILL_MOBS", Material.IRON_SWORD, "Szörnyek megölése");
        putType("KILL_PLAYERS", Material.DIAMOND_SWORD, "Játékosok legyőzése");
        putType("KILL_WORLDBOSS", Material.DRAGON_HEAD, "Világboss legyőzése");
        putType("BREAK_BLOCKS", Material.IRON_PICKAXE, "Blokkok kibányászása");
        putType("PLACE_BLOCKS", Material.BRICKS, "Blokkok lerakása");
        putType("COLLECT_ITEMS", Material.CHEST, "Tárgyak gyűjtése");
        putType("CRAFT_ITEMS", Material.CRAFTING_TABLE, "Tárgyak barkácsolása");
        putType("SMELT_ITEMS", Material.FURNACE, "Olvasztás");
        putType("ENCHANT_ITEMS", Material.ENCHANTING_TABLE, "Tárgyak varázslása");
        putType("CONSUME_ITEMS", Material.COOKED_BEEF, "Étel/ital elfogyasztása");
        putType("DELIVER_ITEMS", Material.CHEST_MINECART, "Tárgyak leszállítása");
        putType("CATCH_FISH", Material.FISHING_ROD, "Halak kifogása");
        putType("BREED_ANIMALS", Material.WHEAT, "Állatok szaporítása");
        putType("TAME_ANIMALS", Material.BONE, "Állatok megszelídítése");
        putType("TRADE_WITH_VILLAGER", Material.EMERALD, "Kereskedés falusival");
        putType("TALK_TO_NPC", Material.VILLAGER_SPAWN_EGG, "Beszélgetés NPC-vel");
        putType("VISIT_TERRITORY", Material.FILLED_MAP, "Terület felkeresése");
        putType("EXPLORE_BIOME", Material.COMPASS, "Biom felfedezése");
        putType("REACH_LEVEL", Material.EXPERIENCE_BOTTLE, "Szint elérése");
        putType("PARKOUR_TRIAL", Material.RABBIT_FOOT, "Parkour-pálya teljesítése");
        putType("WIN_RAID", Material.CROSSBOW, "Raid megnyerése");
    }

    private static void putType(final String type, final Material material, final String name) {
        TYPE_ICONS.put(type, material);
        TYPE_NAMES.put(type, name);
    }

    /** The Hungarian display name of an objective type (falls back to the raw id). */
    public static String typeName(final String type) {
        return TYPE_NAMES.getOrDefault(type, type);
    }

    private QuestBuilderGUI() {
    }

    /** Opens the field editor of an existing custom quest. */
    public static void openEditor(final Player player, final String questId, final QuestManager questManager) {
        final QuestBuilderHolder holder = new QuestBuilderHolder(
                player.getUniqueId(), questId, null, QuestBuilderHolder.View.EDITOR);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("Quest builder — " + questId, NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        GuiUtil.fill(inventory);

        final ConfigurationSection quest = questManager.getQuestSection(questId);

        inventory.setItem(4, icon(Material.BOOK, accent("Quest builder"), List.of(
                label("Küldetés", Component.text(questId, NamedTextColor.YELLOW)),
                label("Objektívák", Component.text(String.valueOf(questManager.getObjectiveTotal(questId)),
                        NamedTextColor.YELLOW)),
                grey("Kattints egy mezőre, majd írd be a chatbe"),
                grey("az új értéket. A ritkább mezőkhöz (rotation,"),
                grey("dialogue.choices) a /quest admin set használható."))));

        for (final FieldTile tile : TEXT_FIELDS) {
            final Object current = quest == null ? null : quest.get(tile.field());
            inventory.setItem(tile.slot(), icon(tile.material(),
                    Component.text(tile.name(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            label("Mező", Component.text(tile.field(), NamedTextColor.WHITE)),
                            label("Érték", Component.text(current == null ? "—" : String.valueOf(current),
                                    NamedTextColor.YELLOW)),
                            grey("» Kattints, és írd az új értéket a chatbe"))));
            holder.putAction(tile.slot(), "FIELD:" + tile.field());
        }

        // Kapcsolók: kattintásra azonnal átbillennek, nem kérnek chat-bevitelt.
        addToggle(inventory, holder, quest, 14, "repeatable", "Ismételhető", Material.LIME_DYE, Material.GRAY_DYE);
        addToggle(inventory, holder, quest, 15, "seasonal", "Szezonális", Material.SNOWBALL, Material.GRAY_DYE);
        addToggle(inventory, holder, quest, 32, "rewards.cleanse-sins", "Jutalom: bűn-tisztítás",
                Material.MILK_BUCKET, Material.BUCKET);

        final String mode = quest == null ? "ALL" : quest.getString("objectives-mode", "ALL");
        inventory.setItem(16, icon(Material.COMPARATOR,
                Component.text("Objektíva-mód", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                List.of(
                        label("Jelenleg", Component.text(mode.toUpperCase(Locale.ROOT), NamedTextColor.YELLOW)),
                        grey("ALL: minden feladat párhuzamosan"),
                        grey("SEQUENCE: a feladatok sorban jönnek"),
                        grey("» Kattints a váltáshoz"))));
        holder.putAction(16, "MODE");

        inventory.setItem(41, buildObjectiveSummary(questManager, questId));
        inventory.setItem(42, icon(Material.ANVIL, accent("Új objektíva"), List.of(
                grey("További feladat hozzáadása ehhez a questhez."),
                grey("» Kattints a típus-választóhoz"))));
        holder.putAction(42, "NEWOBJ");

        inventory.setItem(45, icon(Material.TNT,
                Component.text("Küldetés törlése", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of(grey("A quest végleges törlése."), grey("» Kattints kétszer a megerősítéshez"))));
        holder.putAction(45, "DELETE");

        inventory.setItem(49, icon(Material.BARRIER,
                Component.text("Bezárás", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of()));
        holder.putAction(49, "CLOSE");

        player.openInventory(inventory);
    }

    private static void addToggle(final Inventory inventory, final QuestBuilderHolder holder,
                                  final ConfigurationSection quest, final int slot, final String field,
                                  final String name, final Material onIcon, final Material offIcon) {
        final boolean on = quest != null && quest.getBoolean(field, false);
        inventory.setItem(slot, icon(on ? onIcon : offIcon,
                Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                List.of(
                        label("Jelenleg", on
                                ? Component.text("BE", NamedTextColor.GREEN)
                                : Component.text("KI", NamedTextColor.RED)),
                        grey("» Kattints a váltáshoz"))));
        holder.putAction(slot, "TOGGLE:" + field + ":" + !on);
    }

    private static org.bukkit.inventory.ItemStack buildObjectiveSummary(final QuestManager questManager,
                                                                        final String questId) {
        final List<Component> lore = new ArrayList<>();
        final ConfigurationSection quest = questManager.getQuestSection(questId);
        if (quest != null) {
            final ConfigurationSection multi = quest.getConfigurationSection("objectives");
            if (multi != null) {
                for (final String key : multi.getKeys(false)) {
                    final ConfigurationSection objective = multi.getConfigurationSection(key);
                    if (objective != null) {
                        lore.add(grey(" " + key + ". " + typeName(objective.getString("type", "?"))
                                + " ×" + objective.getInt("count", 1)));
                    }
                }
            } else {
                final ConfigurationSection single = quest.getConfigurationSection("objective");
                if (single != null) {
                    lore.add(grey(" 1. " + typeName(single.getString("type", "?"))
                            + " ×" + single.getInt("count", 1)));
                }
            }
        }
        lore.add(grey("Az objektíva-részletek (materials, entity-type...)"));
        lore.add(grey("a /quest admin set <id> objective.<mező> paranccsal."));
        return icon(Material.TARGET,
                Component.text("Objektívák", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false), lore);
    }

    /**
     * Opens the objective-type picker. With {@code newQuestId} set the chosen
     * type starts the new-quest chat flow (count → name → create); with an
     * existing {@code questId} it adds an extra objective instead.
     */
    public static void openTypePicker(final Player player, final String questId, final String newQuestId) {
        final QuestBuilderHolder holder = new QuestBuilderHolder(
                player.getUniqueId(), questId, newQuestId, QuestBuilderHolder.View.TYPE_PICKER);
        final Inventory inventory = Bukkit.createInventory(holder, 36,
                Component.text(newQuestId != null
                        ? "Új quest — objektíva típusa"
                        : "Új objektíva — típus", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        GuiUtil.fill(inventory);

        int slot = 0;
        for (final Map.Entry<String, Material> entry : TYPE_ICONS.entrySet()) {
            inventory.setItem(slot, icon(entry.getValue(),
                    Component.text(TYPE_NAMES.get(entry.getKey()), NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(
                            label("Típus", Component.text(entry.getKey(), NamedTextColor.WHITE)),
                            grey("» Kattints a kiválasztáshoz"))));
            holder.putAction(slot, "TYPE:" + entry.getKey());
            slot++;
        }

        inventory.setItem(35, icon(Material.BARRIER,
                Component.text("Mégse", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of()));
        holder.putAction(35, "CLOSE");

        player.openInventory(inventory);
    }
}
