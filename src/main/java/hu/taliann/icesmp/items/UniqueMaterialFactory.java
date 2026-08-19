package hu.taliann.icesmp.items;

import hu.taliann.icesmp.gui.MaterialSourceHints;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.ConfigMaterialResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds and identifies unique profession materials — PDC-tagged intermediate items a profession
 * crafts and higher recipes consume (WoW "Spirit Dust" / Terraria intermediate bars). Definitions
 * live in {@code profession-materials.yml}; the tag lets the recipe engine match them by id rather
 * than by vanilla {@link Material}, so they never mix with ordinary items.
 */
public final class UniqueMaterialFactory {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey idKey;

    public UniqueMaterialFactory(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.idKey = new NamespacedKey(plugin, "unique_material");
    }

    /** The plain display name of a unique material (for GUI labels), or the raw id if undefined. */
    public String displayName(final String uniqueId) {
        final ConfigurationSection section = configOf(uniqueId);
        if (section == null) {
            return uniqueId;
        }
        // Strip legacy '&x' colour codes for a plain label.
        return section.getString("display-name", uniqueId).replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    /** Creates {@code amount} of the unique material, or null if the id is undefined / has a bad icon. */
    public ItemStack create(final String uniqueId, final int amount) {
        final ConfigurationSection section = configOf(uniqueId);
        if (section == null) {
            return null;
        }
        final Material icon = ConfigMaterialResolver.match(section.getString("material", ""));
        if (icon == null) {
            return null;
        }
        final ItemStack item = new ItemStack(icon, Math.max(1, amount));
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(legacy(section.getString("display-name", uniqueId))
                .colorIfAbsent(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        final List<Component> lore = new ArrayList<>();
        for (final String line : section.getStringList("lore")) {
            lore.add(legacy(line).colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        for (final String hint : MaterialSourceHints.forManagedMaterial(uniqueId)) {
            lore.add(Component.text(hint, NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, uniqueId.toLowerCase(Locale.ROOT));
        item.setItemMeta(meta);
        if (!applyPresentation(item, uniqueId)) {
            return null;
        }
        return item;
    }

    /**
     * Reapplies a unique item's data-component presentation after a caller performed ItemMeta
     * round-trips (for example the profession crafted-by/affix pipeline).
     */
    public boolean applyPresentation(final ItemStack item, final String uniqueId) {
        final ConfigurationSection section = configOf(uniqueId);
        if (section == null || item == null) {
            return false;
        }
        final String itemModel = section.getString("item-model", null);
        final String equipmentAsset = section.getString("equipment-asset", null);
        final WearablePresentation.Result presentation = WearablePresentation.applyWearablePresentation(
                item, itemModel, equipmentAsset);
        if (equipmentAsset != null && !equipmentAsset.isBlank() && !presentation.equipmentApplied()) {
            plugin.getLogger().warning("profession-materials." + uniqueId + ".equipment-asset: '"
                    + equipmentAsset + "' cannot be applied (" + presentation.equipmentStatus() + ")");
            return false;
        }
        return true;
    }

    /** The unique-material id of an item, or null if it is not a unique material. */
    public String idOf(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public boolean isDefined(final String uniqueId) {
        return configOf(uniqueId) != null;
    }

    /** Minden definiált unique-material id (admin item-adó parancs tab-complete-je). */
    public List<String> allIds() {
        if (configManager.getConfiguration() == null) {
            return List.of();
        }
        final ConfigurationSection root = configManager.getConfiguration()
                .getConfigurationSection("profession-materials");
        return root == null ? List.of() : List.copyOf(root.getKeys(false));
    }

    private static Component legacy(final String text) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private ConfigurationSection configOf(final String uniqueId) {
        if (uniqueId == null || configManager.getConfiguration() == null) {
            return null;
        }
        return configManager.getConfiguration()
                .getConfigurationSection("profession-materials." + uniqueId.toLowerCase(Locale.ROOT));
    }
}
