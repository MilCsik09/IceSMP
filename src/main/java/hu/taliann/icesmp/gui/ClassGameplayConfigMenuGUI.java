package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClassGameplayConfigMenuGUI {

    public static final String ROOT_ACTION = "CLASS_GAMEPLAY";
    public static final String CATEGORY_ACTION_PREFIX = "CLASS_GAMEPLAY_CAT:";
    public static final String ROOT_CATEGORY_ID = "class-gameplay-root";
    private static final String HOLDER_PREFIX = "class-gameplay:";
    private static final int PAGE_SIZE = 45;

    private record ClassCategory(String id, String title, Material icon,
                                 List<ConfigMenuGUI.Entry> entries) { }

    private static final Map<String, ClassCategory> CATEGORIES = loadCatalog();

    private ClassGameplayConfigMenuGUI() { }

    public static int categoryCount() { return CATEGORIES.size(); }

    public static int entryCount() {
        return CATEGORIES.values().stream().mapToInt(category -> category.entries().size()).sum();
    }

    public static List<ConfigMenuGUI.Entry> entries() {
        return CATEGORIES.values().stream().flatMap(category -> category.entries().stream()).toList();
    }

    public static ConfigMenuGUI.Entry findEntry(final String key) {
        return entries().stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
    }

    public static boolean isCategory(final String holderCategory) {
        return holderCategory != null && holderCategory.startsWith(HOLDER_PREFIX);
    }

    public static void openRoot(final Player player, final ConfigEditSession session) {
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), ROOT_CATEGORY_ID);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚔ Kaszt-játékmenet", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        int slot = 0;
        for (final ClassCategory category : CATEGORIES.values()) {
            inventory.setItem(slot, GuiUtil.item(category.icon(), "&b" + category.title(),
                    List.of("&7" + category.entries().size() + " live balance-kulcs",
                            "&eKattints a megnyitáshoz")));
            holder.bind(slot, CATEGORY_ACTION_PREFIX + category.id() + ":0");
            slot++;
        }
        ConfigMenuControls.add(inventory, holder, session, true);
        player.openInventory(inventory);
    }

    public static void openCategory(final Player player, final String request,
                                    final ConfigManager configManager,
                                    final ConfigEditSession session) {
        final int separator = request.lastIndexOf(':');
        if (separator < 1) {
            openRoot(player, session);
            return;
        }
        final String categoryId = request.substring(0, separator);
        final ClassCategory category = CATEGORIES.get(categoryId);
        final int requestedPage;
        try {
            requestedPage = Integer.parseInt(request.substring(separator + 1));
        } catch (final NumberFormatException ignored) {
            openRoot(player, session);
            return;
        }
        if (category == null) {
            openRoot(player, session);
            return;
        }
        final int pageCount = Math.max(1, (category.entries().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        final int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(),
                HOLDER_PREFIX + categoryId + ":" + page);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚔ " + category.title() + " " + (page + 1) + "/" + pageCount,
                        NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        final int from = page * PAGE_SIZE;
        final int to = Math.min(category.entries().size(), from + PAGE_SIZE);
        int slot = 0;
        for (final ConfigMenuGUI.Entry entry : category.entries().subList(from, to)) {
            inventory.setItem(slot, ConfigMenuEntryRenderer.render(entry, configManager, session));
            holder.bind(slot, switch (entry.type()) {
                case TOGGLE -> "TOGGLE:" + entry.key();
                case CYCLE -> "CYCLE:" + entry.key();
                default -> "NUM:" + entry.key();
            });
            slot++;
        }
        if (page > 0) {
            inventory.setItem(46, GuiUtil.item(Material.ARROW, "&7Előző oldal", List.of()));
            holder.bind(46, CATEGORY_ACTION_PREFIX + categoryId + ":" + (page - 1));
        }
        if (page + 1 < pageCount) {
            inventory.setItem(52, GuiUtil.item(Material.ARROW, "&7Következő oldal", List.of()));
            holder.bind(52, CATEGORY_ACTION_PREFIX + categoryId + ":" + (page + 1));
        }
        ConfigMenuControls.add(inventory, holder, session, true);
        player.openInventory(inventory);
    }

    public static void reopen(final Player player, final String holderCategory,
                              final ConfigManager configManager,
                              final ConfigEditSession session) {
        openCategory(player, holderCategory.substring(HOLDER_PREFIX.length()), configManager, session);
    }

    private static Map<String, ClassCategory> loadCatalog() {
        final YamlConfiguration yaml;
        try (InputStream input = ClassGameplayConfigMenuGUI.class.getClassLoader()
                .getResourceAsStream("config/class-gameplay.yml")) {
            if (input == null) throw new IllegalStateException("Hiányzó csomagolt config: class-gameplay.yml");
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (final java.io.IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }

        final Map<String, ClassCategory> categories = new LinkedHashMap<>();
        categories.put("specialization", category(yaml, "specialization", "Specializáció-váltás",
                Material.NETHER_STAR, "classes.specialization"));
        categories.put("capstones", category(yaml, "capstones", "Záróképesség-szintek",
                Material.EXPERIENCE_BOTTLE, "specializations"));
        addClass(categories, yaml, "warrior", "Harcos", Material.IRON_SWORD);
        addClass(categories, yaml, "evoker", "Sárkányidéző", Material.DRAGON_BREATH);
        addClass(categories, yaml, "archer", "Íjász", Material.BOW);
        addClass(categories, yaml, "shaman", "Sámán", Material.LIGHTNING_ROD);
        addClass(categories, yaml, "monk", "Szerzetes", Material.BAMBOO);
        addClass(categories, yaml, "paladin", "Lovag", Material.SHIELD);
        addClass(categories, yaml, "demon_hunter", "Démonvadász", Material.CROSSBOW);
        addClass(categories, yaml, "druid", "Druida", Material.OAK_SAPLING);
        addClass(categories, yaml, "priest", "Pap", Material.TOTEM_OF_UNDYING);
        addClass(categories, yaml, "death_knight", "Halállovag", Material.WITHER_SKELETON_SKULL);
        addClass(categories, yaml, "assassin", "Orgyilkos", Material.IRON_SWORD);
        addClass(categories, yaml, "warlock", "Boszorkánymester", Material.SOUL_LANTERN);
        addClass(categories, yaml, "wizard", "Varázsló", Material.ENCHANTED_BOOK);
        return Collections.unmodifiableMap(new LinkedHashMap<>(categories));
    }

    private static void addClass(final Map<String, ClassCategory> categories,
                                 final YamlConfiguration yaml, final String id,
                                 final String title, final Material icon) {
        categories.put(id, category(yaml, id, title, icon, "classes." + id));
    }

    private static ClassCategory category(final YamlConfiguration yaml, final String id,
                                          final String title, final Material icon,
                                          final String prefix) {
        final List<ConfigMenuGUI.Entry> entries = new ArrayList<>();
        for (final String key : yaml.getKeys(true)) {
            if (!key.startsWith(prefix + ".") || yaml.isConfigurationSection(key)) continue;
            final Object value = yaml.get(key);
            final String label = key.substring(prefix.length() + 1).replace(".", " › ");
            if (value instanceof Boolean) {
                entries.add(ConfigMenuGUI.Entry.toggle(key, label));
            } else if (value instanceof Integer) {
                entries.add(ConfigMenuGUI.Entry.integer(key, label, integerStep(key), 0, Integer.MAX_VALUE));
            } else if (value instanceof Number) {
                entries.add(ConfigMenuGUI.Entry.number(key, label, decimalStep(key), 0, Double.MAX_VALUE));
            }
        }
        if (entries.isEmpty()) throw new IllegalStateException("Nincs balance-kulcs: " + prefix);
        return new ClassCategory(id, title, icon, List.copyOf(entries));
    }

    private static int integerStep(final String key) {
        if (key.endsWith("-millis")) return 100;
        if (key.endsWith("-ticks")) return 5;
        return 1;
    }

    private static double decimalStep(final String key) {
        if (key.endsWith("-percent")) return 1.0D;
        if (key.endsWith("-radius") || key.endsWith("-distance")) return 0.5D;
        return 0.05D;
    }
}
