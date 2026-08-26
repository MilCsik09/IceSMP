package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.items.ItemDataFactory;
import hu.taliann.icesmp.items.RarityPresentationService;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Creates stack-equivalent base Trash items without invoking the rolled gear-rarity pipeline. */
public final class TrashItemFactory {

    private static final int BASE_STACK_SIZE = 64;
    private static final String BASE_PHASE = "base";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final TrashCatalog catalog;
    private final RarityPresentationService rarityPresentations;
    private final NamespacedKey trashIdKey;
    private final NamespacedKey phaseKey;

    public TrashItemFactory(final JavaPlugin plugin, final TrashCatalog catalog,
                            final RarityPresentationService rarityPresentations) {
        Objects.requireNonNull(plugin, "plugin");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.rarityPresentations = Objects.requireNonNull(rarityPresentations, "rarityPresentations");
        this.trashIdKey = new NamespacedKey(plugin, "trash_id");
        this.phaseKey = new NamespacedKey(plugin, "trash_phase");
    }

    public ItemStack create(final String rawId, final int amount) {
        if (amount < 1 || amount > BASE_STACK_SIZE) {
            throw new IllegalArgumentException("Trash amount must be between 1 and " + BASE_STACK_SIZE);
        }
        final TrashDefinition definition = catalog.require(rawId);
        final RarityPresentationService.Presentation rarity = rarityPresentations.require(
                definition.playerRarity());
        if (!catalog.rarityLabel().equals(rarity.label())) {
            throw new IllegalStateException("Trash rarity presentation drifted from the catalog authority");
        }

        final ItemStack item = new ItemStack(definition.material(), amount);
        final ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(BASE_STACK_SIZE);
        meta.displayName(colored(rarity.legacyColor(), definition.displayName()));
        final List<Component> lore = new ArrayList<>();
        lore.add(colored(rarity.legacyColor(), rarity.label()));
        if (!definition.lore().isEmpty()) {
            lore.add(Component.empty());
            definition.lore().forEach(line -> lore.add(Component.text(line, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        }
        meta.lore(lore);
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(trashIdKey, PersistentDataType.STRING, definition.id());
        pdc.set(phaseKey, PersistentDataType.STRING, BASE_PHASE);
        item.setItemMeta(meta);

        // Data components must remain last; a subsequent ItemMeta round-trip would erase them.
        ItemDataFactory.applyItemModel(item, definition.itemModel());
        ItemDataFactory.applyRarity(item, rarity.vanillaRarity());
        return item;
    }

    public Optional<String> idOf(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return Optional.empty();
        final String id = item.getItemMeta().getPersistentDataContainer().get(trashIdKey,
                PersistentDataType.STRING);
        if (id == null || id.isBlank()) return Optional.empty();
        return catalog.find(id).map(TrashDefinition::id);
    }

    public boolean isBaseIdentity(final ItemStack item) {
        if (idOf(item).isEmpty()) return false;
        final String phase = item.getItemMeta().getPersistentDataContainer().get(phaseKey,
                PersistentDataType.STRING);
        return BASE_PHASE.equals(phase);
    }

    private static TextComponent colored(final String legacyColor, final String text) {
        return LEGACY.deserialize(TextUtil.color(legacyColor + text))
                .decoration(TextDecoration.ITALIC, false);
    }
}
