package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transactional view over the first-wave operational catalog. The moderation category is hidden
 * because the rebased canonical ConfigMenuGUI now owns those scalar paths.
 */
public final class TransactionalOperationalConfigMenuGUI {

    private static final String HIDDEN_CANONICAL_CATEGORY = "moderation";
    private static final Map<String, OperationalConfigMenuGUI.Category> CATEGORIES = visibleCatalog();

    private TransactionalOperationalConfigMenuGUI() { }

    @SuppressWarnings("unchecked")
    private static Map<String, OperationalConfigMenuGUI.Category> visibleCatalog() {
        try {
            final Field field = OperationalConfigMenuGUI.class.getDeclaredField("CATEGORIES");
            field.setAccessible(true);
            final Map<String, OperationalConfigMenuGUI.Category> source =
                    (Map<String, OperationalConfigMenuGUI.Category>) field.get(null);
            final Map<String, OperationalConfigMenuGUI.Category> visible = new LinkedHashMap<>();
            source.forEach((id, category) -> {
                if (!HIDDEN_CANONICAL_CATEGORY.equals(id)) {
                    visible.put(id, category);
                }
            });
            return java.util.Collections.unmodifiableMap(visible);
        } catch (final ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

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

    public static void openRoot(final Player player, final ConfigEditSession session) {
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(),
                OperationalConfigMenuGUI.ROOT_CATEGORY_ID);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ Üzemeltetés és finomhangolás", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        final int[] slots = {10, 12, 14, 16};
        int index = 0;
        for (final OperationalConfigMenuGUI.Category category : CATEGORIES.values()) {
            final int slot = slots[index++];
            inventory.setItem(slot, GuiUtil.item(category.icon(), "&b" + category.title(),
                    List.of("&7" + category.entries().size() + " staged kulcs",
                            "&eKattints a megnyitáshoz")));
            holder.bind(slot, OperationalConfigMenuGUI.CATEGORY_ACTION_PREFIX + category.id());
        }
        ConfigMenuControls.add(inventory, holder, session, true);
        player.openInventory(inventory);
    }

    public static void openCategory(final Player player, final String categoryId,
                                    final ConfigManager configManager,
                                    final ConfigEditSession session) {
        final OperationalConfigMenuGUI.Category category = CATEGORIES.get(categoryId);
        if (category == null) {
            openRoot(player, session);
            return;
        }
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), "ops:" + categoryId);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ " + category.title(), NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        int slot = 0;
        for (final ConfigMenuGUI.Entry entry : category.entries()) {
            inventory.setItem(slot, ConfigMenuEntryRenderer.render(entry, configManager, session));
            holder.bind(slot, switch (entry.type()) {
                case TOGGLE -> "TOGGLE:" + entry.key();
                case CYCLE -> "CYCLE:" + entry.key();
                default -> "NUM:" + entry.key();
            });
            slot++;
        }
        ConfigMenuControls.add(inventory, holder, session, true);
        player.openInventory(inventory);
    }
}
