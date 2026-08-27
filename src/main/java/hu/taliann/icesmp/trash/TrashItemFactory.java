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
        return createPresented(definition.id(), BASE_PHASE, definition.displayName(),
                definition.playerRarity(), definition.material(), definition.itemModel(),
                definition.lore(), amount);
    }

    public ItemStack createPhase(final String rawBaseId, final String rawPhase, final int amount) {
        if (amount < 1 || amount > BASE_STACK_SIZE) {
            throw new IllegalArgumentException("Trash amount must be between 1 and " + BASE_STACK_SIZE);
        }
        final TrashDefinition base = catalog.require(rawBaseId);
        final String phase = normalize(rawPhase);
        if (!catalog.isKnownPhase(base.id(), phase) || BASE_PHASE.equals(phase)) {
            throw new IllegalArgumentException("a lifecycle phase nem tartozik a base identityhez: "
                    + base.id() + "/" + rawPhase);
        }
        final TrashLifecyclePhase definition = catalog.requirePhase(phase);
        return createPresented(base.id(), definition.id(), definition.displayName(),
                definition.playerRarity(), definition.material(), definition.itemModel(),
                definition.lore(), amount);
    }

    private ItemStack createPresented(final String baseId, final String phase,
                                      final String displayName, final String playerRarity,
                                      final org.bukkit.Material material, final String itemModel,
                                      final List<String> authoredLore, final int amount) {
        final RarityPresentationService.Presentation rarity = rarityPresentations.require(
                playerRarity);
        if (!catalog.rarityLabel().equals(rarity.label())) {
            throw new IllegalStateException("Trash rarity presentation drifted from the catalog authority");
        }

        final ItemStack item = new ItemStack(material, amount);
        final ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(BASE_STACK_SIZE);
        meta.displayName(colored(rarity.legacyColor(), displayName));
        final List<Component> lore = new ArrayList<>();
        lore.add(colored(rarity.legacyColor(), rarity.label()));
        if (!authoredLore.isEmpty()) {
            lore.add(Component.empty());
            authoredLore.forEach(line -> lore.add(Component.text(line, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        }
        meta.lore(lore);
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(trashIdKey, PersistentDataType.STRING, baseId);
        pdc.set(phaseKey, PersistentDataType.STRING, phase);
        item.setItemMeta(meta);

        // Data components must remain last; a subsequent ItemMeta round-trip would erase them.
        ItemDataFactory.applyItemModel(item, itemModel);
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
        return BASE_PHASE.equals(phaseOf(item).orElse(""));
    }

    public boolean isKnownItem(final ItemStack item) {
        final String id = idOf(item).orElse(null);
        final String phase = phaseOf(item).orElse(null);
        return id != null && phase != null && catalog.isKnownPhase(id, phase);
    }

    public Optional<String> phaseOf(final ItemStack item) {
        final String id = idOf(item).orElse(null);
        if (id == null) return Optional.empty();
        final String phase = item.getItemMeta().getPersistentDataContainer().get(phaseKey,
                PersistentDataType.STRING);
        if (phase == null || !catalog.isKnownPhase(id, phase)) return Optional.empty();
        return Optional.of(phase);
    }

    public Optional<String> successPhaseOf(final ItemStack item) {
        final String id = idOf(item).orElse(null);
        if (id == null || !BASE_PHASE.equals(phaseOf(item).orElse(""))) return Optional.empty();
        final String successPhase = catalog.require(id).successPhase();
        return successPhase.isBlank() ? Optional.empty() : Optional.of(successPhase);
    }

    public String displayNameOf(final ItemStack item) {
        final String id = idOf(item).orElseThrow(() -> new IllegalArgumentException("nem Trash item"));
        final String phase = phaseOf(item).orElseThrow(() ->
                new IllegalArgumentException("ismeretlen Trash lifecycle phase"));
        return BASE_PHASE.equals(phase) ? catalog.require(id).displayName()
                : catalog.requirePhase(phase).displayName();
    }

    public int vendorValueOf(final ItemStack item) {
        final String id = idOf(item).orElseThrow(() -> new IllegalArgumentException("nem Trash item"));
        final String phase = phaseOf(item).orElseThrow(() ->
                new IllegalArgumentException("ismeretlen Trash lifecycle phase"));
        return BASE_PHASE.equals(phase) ? catalog.require(id).vendorValue()
                : catalog.requirePhase(phase).vendorValue();
    }

    public void applyPhase(final ItemStack item, final String rawPhase) {
        final String id = idOf(item).orElseThrow(() -> new IllegalArgumentException("nem Trash item"));
        final String phase = normalize(rawPhase);
        if (!catalog.isKnownPhase(id, phase) || BASE_PHASE.equals(phase)) {
            throw new IllegalArgumentException("a lifecycle phase nem tartozik a base identityhez: "
                    + id + "/" + rawPhase);
        }
        final TrashLifecyclePhase definition = catalog.requirePhase(phase);
        final RarityPresentationService.Presentation rarity = rarityPresentations.require(
                definition.playerRarity());
        final ItemMeta previousMeta = item.getItemMeta();
        item.setType(definition.material());
        final ItemMeta meta = item.getItemMeta();
        previousMeta.getPersistentDataContainer().copyTo(meta.getPersistentDataContainer(), true);
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
        meta.getPersistentDataContainer().set(trashIdKey, PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(phaseKey, PersistentDataType.STRING, phase);
        item.setItemMeta(meta);
        ItemDataFactory.applyItemModel(item, definition.itemModel());
        ItemDataFactory.applyRarity(item, rarity.vanillaRarity());
    }

    /** Restores data-component presentation after a required ItemMeta/PDC write. */
    public void refreshPresentation(final ItemStack item) {
        final String id = idOf(item).orElseThrow(() -> new IllegalArgumentException("nem Trash item"));
        final String phase = phaseOf(item).orElseThrow(() ->
                new IllegalArgumentException("ismeretlen Trash lifecycle phase"));
        final String itemModel;
        final String playerRarity;
        if (BASE_PHASE.equals(phase)) {
            final TrashDefinition definition = catalog.require(id);
            itemModel = definition.itemModel();
            playerRarity = definition.playerRarity();
        } else {
            final TrashLifecyclePhase definition = catalog.requirePhase(phase);
            itemModel = definition.itemModel();
            playerRarity = definition.playerRarity();
        }
        final RarityPresentationService.Presentation rarity = rarityPresentations.require(playerRarity);
        ItemDataFactory.applyItemModel(item, itemModel);
        ItemDataFactory.applyRarity(item, rarity.vanillaRarity());
    }

    private static String normalize(final String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static TextComponent colored(final String legacyColor, final String text) {
        return LEGACY.deserialize(TextUtil.color(legacyColor + text))
                .decoration(TextDecoration.ITALIC, false);
    }
}
