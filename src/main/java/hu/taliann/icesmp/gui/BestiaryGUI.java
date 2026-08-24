package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.BestiaryManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.managers.StatsManager;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * B21 — a bestiárium lapozható nézetei. A főoldal 4 kattintható kategória-csempét ad
 * teljesítmény-%-kal; a kategória-nézet 45 bejegyzés/oldal: az ismert bejegyzés ikonnal
 * és tudás-fokozat szerinti lore-ral, az ismeretlen „???" sziluettként jelenik meg —
 * a nevezőt a fajta-, recept-, territórium- és boss-lajstrom adja. Minden nézet
 * csak olvasható; a kattintás-útvonalakat a BestiaryListener értelmezi.
 */
public final class BestiaryGUI {

    /** Kategória-csempék a főoldalon (slot → kategória) — a listener ugyanezt olvassa. */
    public static final Map<Integer, BestiaryManager.Category> MAIN_SLOTS = Map.of(
            10, BestiaryManager.Category.MOBS,
            12, BestiaryManager.Category.RECIPES,
            14, BestiaryManager.Category.TERRITORIES,
            16, BestiaryManager.Category.BOSSES);
    public static final int SLOT_BACK = 45;
    public static final int SLOT_PREV = 48;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 50;
    public static final int PAGE_SIZE = 45;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final DateTimeFormatter FIRST_KILL_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private BestiaryGUI() {
    }

    /** Egy bejegyzés megjelenítési sora: kánon-id + ikon + név + gyűjtött-e. */
    public record Entry(String id, Material icon, Component name, boolean collected) {
    }

    public static void openMain(final Player player, final BestiaryManager bestiaryManager,
                                final ProfessionRecipeCatalog recipeCatalog,
                                final TerritoryManager territoryManager) {
        final BestiaryHolder holder = new BestiaryHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("📜 Bestiárium — a lajstromod"));
        holder.setInventory(inventory);
        inventory.setItem(10, categoryIcon(Material.IRON_SWORD, BestiaryManager.Category.MOBS,
                bestiaryManager.count(player, BestiaryManager.Category.MOBS),
                bestiaryManager.knownMobEntryCount(),
                "Megölt szörny-FAJOK — minden faj első", "elejtése új bejegyzés."));
        inventory.setItem(12, categoryIcon(Material.CRAFTING_TABLE, BestiaryManager.Category.RECIPES,
                bestiaryManager.count(player, BestiaryManager.Category.RECIPES),
                recipeCatalog.allIds().size(),
                "Első craftok a recept-katalógusból.", "A céh számon tartja a munkád."));
        inventory.setItem(14, categoryIcon(Material.FILLED_MAP, BestiaryManager.Category.TERRITORIES,
                bestiaryManager.count(player, BestiaryManager.Category.TERRITORIES),
                territoryManager.all().size(),
                "Bejárt territóriumok — az első", "határátlépés írja a lajstromot."));
        inventory.setItem(16, categoryIcon(Material.WITHER_SKELETON_SKULL, BestiaryManager.Category.BOSSES,
                bestiaryManager.count(player, BestiaryManager.Category.BOSSES),
                WorldBossManager.archetypeDisplayNames().size(),
                "Legyőzött boss-archetípusok.", "A krónikások kedvence."));
        player.openInventory(inventory);
    }

    public static void openCategory(final Player player, final BestiaryManager.Category category,
                                    final int requestedPage, final BestiaryManager bestiaryManager,
                                    final StatsManager statsManager,
                                    final ProfessionRecipeCatalog recipeCatalog,
                                    final TerritoryManager territoryManager) {
        final List<Entry> entries = entries(player, category, bestiaryManager, recipeCatalog,
                territoryManager);
        final int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int page = Math.min(Math.max(0, requestedPage), pages - 1);
        final BestiaryHolder holder = new BestiaryHolder(player.getUniqueId(), category, page);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("📜 Bestiárium — " + BestiaryManager.categoryName(category)));
        holder.setInventory(inventory);

        final int from = page * PAGE_SIZE;
        final int to = Math.min(entries.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            inventory.setItem(index - from, entryItem(player, category, entries.get(index),
                    bestiaryManager, statsManager));
        }

        inventory.setItem(SLOT_BACK, GuiUtil.icon(Material.ARROW,
                GuiUtil.accent("Vissza az áttekintőhöz"), List.of()));
        if (page > 0) {
            inventory.setItem(SLOT_PREV, GuiUtil.icon(Material.SPECTRAL_ARROW,
                    GuiUtil.accent("Előző oldal"), List.of()));
        }
        final long collected = entries.stream().filter(Entry::collected).count();
        inventory.setItem(SLOT_INFO, GuiUtil.icon(Material.BOOK,
                GuiUtil.accent("Oldal " + (page + 1) + " / " + pages),
                List.of(GuiUtil.grey("Bejegyzések: " + collected + " / " + entries.size()))));
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, GuiUtil.icon(Material.SPECTRAL_ARROW,
                    GuiUtil.accent("Következő oldal"), List.of()));
        }
        player.openInventory(inventory);
    }

    /** A kategória teljes lajstroma: ismert nevező + a nevezőn kívüli gyűjtött extrák. */
    private static List<Entry> entries(final Player player, final BestiaryManager.Category category,
                                       final BestiaryManager bestiaryManager,
                                       final ProfessionRecipeCatalog recipeCatalog,
                                       final TerritoryManager territoryManager) {
        final Set<String> collected = bestiaryManager.entries(player, category);
        final LinkedHashMap<String, Entry> rows = new LinkedHashMap<>();
        switch (category) {
            case MOBS -> {
                for (final EntityType type : BestiaryManager.knownMonsterTypes()) {
                    final String id = type.name().toLowerCase(Locale.ROOT);
                    rows.put(id, new Entry(id, spawnEgg(type),
                            Component.translatable(type.translationKey()), collected.contains(id)));
                }
                bestiaryManager.mobTemplates().forEach((id, template) -> {
                    final EntityType type;
                    try {
                        type = EntityType.valueOf(template.entityType());
                    } catch (final IllegalArgumentException invalid) {
                        return;
                    }
                    rows.put(id, new Entry(id, spawnEgg(type),
                            Component.text(template.displayName()), collected.contains(id)));
                });
                // A ritka variánsok gyűjtött extrák: nem részei a nevezőnek, nincs ???-soruk.
                for (final String id : collected) {
                    if (!rows.containsKey(id)) {
                        rows.put(id, new Entry(id, Material.NETHER_STAR,
                                Component.text(variantDisplay(id)), true));
                    }
                }
            }
            case RECIPES -> {
                for (final String id : recipeCatalog.allIds()) {
                    final ProfessionRecipeCatalog.Recipe recipe = recipeCatalog.get(id);
                    final Material icon = recipe == null ? Material.CRAFTING_TABLE : recipe.result();
                    final Component name = recipe == null
                            ? Component.text(id)
                            : LEGACY.deserialize(recipe.displayName());
                    rows.put(id, new Entry(id, icon, name, collected.contains(id.toLowerCase(Locale.ROOT))));
                }
            }
            case TERRITORIES -> {
                for (final var territory : territoryManager.all()) {
                    final String id = territory.id().toLowerCase(Locale.ROOT);
                    rows.put(id, new Entry(id, Material.FILLED_MAP,
                            Component.text(territory.name()), collected.contains(id)));
                }
            }
            case BOSSES -> {
                WorldBossManager.archetypeDisplayNames().forEach((id, rawName) ->
                        rows.put(id, new Entry(id, Material.WITHER_SKELETON_SKULL,
                                LEGACY.deserialize(rawName), collected.contains(id))));
                for (final String id : collected) {
                    rows.putIfAbsent(id, new Entry(id, Material.SKELETON_SKULL,
                            Component.text(id), true));
                }
            }
        }
        return new ArrayList<>(rows.values());
    }

    private static ItemStack entryItem(final Player player, final BestiaryManager.Category category,
                                       final Entry entry, final BestiaryManager bestiaryManager,
                                       final StatsManager statsManager) {
        if (!entry.collected()) {
            return GuiUtil.icon(Material.GRAY_STAINED_GLASS_PANE,
                    Component.text("???", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(GuiUtil.grey("Még ismeretlen bejegyzés."),
                            GuiUtil.grey("A krónikás vár a felfedezésre.")));
        }
        final List<Component> lore = new ArrayList<>();
        if (category == BestiaryManager.Category.MOBS) {
            final long kills = statsManager.getSpeciesKills(player.getUniqueId(), entry.id());
            final long firstAt = statsManager.getSpeciesFirstKillAt(player.getUniqueId(), entry.id());
            final int tier = bestiaryManager.knowledgeTier(kills);
            final List<Integer> tiers = bestiaryManager.knowledgeTiers();
            lore.add(GuiUtil.grey("Elejtve: " + kills + " alkalommal"));
            if (firstAt > 0) {
                lore.add(GuiUtil.grey("Első elejtés: "
                        + FIRST_KILL_FORMAT.format(Instant.ofEpochMilli(firstAt))));
            }
            lore.add(Component.text("Tudás-fokozat: " + "◆".repeat(Math.max(1, tier))
                            + "◇".repeat(Math.max(0, tiers.size() - tier)),
                    NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            final String note = bestiaryManager.codexNote(entry.id());
            if (!note.isBlank()) {
                lore.add(Component.text("„" + note + "”", NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.ITALIC, true));
            }
            final hu.taliann.icesmp.pve.MobTemplate template =
                    bestiaryManager.mobTemplate(entry.id());
            if (template != null && tier >= 1) {
                lore.add(GuiUtil.grey("Rang: " + template.rank().name()
                        + " • Archetípus: " + template.archetype().name()));
                lore.add(GuiUtil.grey(template.bestiarySummary()));
            }
            if (template != null && tier >= 2) {
                lore.add(Component.text("Taktikai jegyzet: " + template.counterplayHint(),
                        NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                if (!template.resistances().isEmpty()) {
                    lore.add(GuiUtil.grey("Ellenállás: " + String.join(", ", template.resistances())));
                }
                if (!template.weaknesses().isEmpty()) {
                    lore.add(GuiUtil.grey("Gyengeség: " + String.join(", ", template.weaknesses())));
                }
            }
            if (template != null && tier >= 3) {
                lore.add(GuiUtil.grey("Forrásprofil: " + template.lootProfile()));
            }
            if (tier >= 2) {
                lore.add(GuiUtil.grey("Zsákmány-jegyzet: a Káoszkor erős példányai"));
                lore.add(GuiUtil.grey("lélekkövet hagynak a Csontszámvevőnek."));
            }
            if (tier >= 3) {
                lore.add(Component.text("Mestervadász — a faj minden titkát ismered.",
                        NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            } else if (tier < tiers.size()) {
                lore.add(GuiUtil.grey("Következő fokozat: " + tiers.get(tier) + " elejtés."));
            }
        } else if (category == BestiaryManager.Category.BOSSES) {
            final String note = bestiaryManager.codexNote(entry.id());
            if (!note.isBlank()) {
                lore.add(Component.text("„" + note + "”", NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.ITALIC, true));
            }
            lore.add(GuiUtil.grey("Archetípus legyőzve — a krónikák jegyzik."));
        } else {
            lore.add(GuiUtil.grey("Bejegyezve a lajstromba."));
        }
        return GuiUtil.icon(entry.icon(),
                entry.name().color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                lore);
    }

    private static ItemStack categoryIcon(final Material material,
                                          final BestiaryManager.Category category,
                                          final int count, final int total,
                                          final String... loreLines) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("📜 " + BestiaryManager.categoryName(category),
                NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        final List<Component> lore = new ArrayList<>();
        final int percent = total > 0 ? (int) Math.floor(count * 100.0D / total) : 0;
        lore.add(Component.text("Bejegyzések: " + count + (total > 0 ? " / " + total : ""),
                NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        if (total > 0) {
            lore.add(Component.text("Teljesítve: " + percent + "%", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        }
        for (final String line : loreLines) {
            lore.add(GuiUtil.grey(line));
        }
        lore.add(Component.text("Kattints a lapozáshoz!", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Material spawnEgg(final EntityType type) {
        final Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return egg == null ? Material.SKELETON_SKULL : egg;
    }

    /** Ritka variáns megjelenítési neve: `albino_zombie` → „Albínó Zombie". */
    private static String variantDisplay(final String id) {
        if (id.startsWith("albino_")) {
            return "Albínó " + typeName(id.substring("albino_".length()));
        }
        if (id.startsWith("arnyek_")) {
            return "Árnyék-" + typeName(id.substring("arnyek_".length()));
        }
        return typeName(id);
    }

    private static String typeName(final String rawType) {
        final String pretty = rawType.replace('_', ' ');
        return pretty.isEmpty() ? rawType
                : Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1);
    }
}
