package hu.taliann.icesmp.items;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Factory for crate-key items (native crate system, replaces CrazyCrates).
 * A key is a config-themed item (material/name/custom-model-data
 * come from {@code config/crates.yml}) tagged with the {@code crate_key} PDC
 * key so {@link hu.taliann.icesmp.listeners.CrateListener} and
 * {@link hu.taliann.icesmp.managers.CrateManager} can identify which crate it
 * opens without parsing the display name.
 */
@SuppressWarnings("deprecation")
public final class CrateKeyFactory {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private final ConfigManager configManager;
    private final NamespacedKey crateKeyIdKey;

    public CrateKeyFactory(final JavaPlugin plugin, final ConfigManager configManager) {
        this.configManager = configManager;
        this.crateKeyIdKey = new NamespacedKey(plugin, "crate_key");
    }

    /**
     * Creates a stack of keys for the given crate, themed from {@code config/crates.yml}.
     *
     * @param crateId the crate id (config/crates.yml crates.&lt;id&gt;)
     * @param amount the stack size (clamped to 1..64)
     * @return the key item, or an AIR stack if the crate id is unknown
     */
    public ItemStack createKey(final String crateId, final int amount) {
        if (crateId == null || crateId.isBlank()) {
            return new ItemStack(Material.AIR);
        }
        final String basePath = "crates." + crateId;
        final Material material = Material.matchMaterial(configManager.getString(basePath + ".key-material", "TRIPWIRE_HOOK"));
        if (material == null || material.isAir()) {
            return new ItemStack(Material.AIR);
        }

        final ItemStack itemStack = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }

        final String keyName = configManager.getString(basePath + ".key-name", "&fLáda Kulcs");
        meta.displayName(SERIALIZER.deserialize(TextUtil.color(keyName)).decoration(TextDecoration.ITALIC, false));
        // A kulcs lore-ja a láda nevét és a top-3 jutalmat mutatja esély-százalékkal —
        // a kulcs "önmagát adja el" a piacon/tradeben is. A sorsolás mindig a friss configból megy,
        // a lore csak tájékoztató (config-átírás után a régi kulcsok lore-ja elavulhat, ez kozmetikai).
        final java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        lore.add(SERIALIZER.deserialize(TextUtil.color(
                configManager.getString(basePath + ".display-name", "&8Láda") + " &8— kulcs")).decoration(TextDecoration.ITALIC, false));
        lore.add(SERIALIZER.deserialize(TextUtil.color("&7Jobb katt a ládán: &fkinyitás")).decoration(TextDecoration.ITALIC, false));
        lore.addAll(topRewardLore(basePath));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(crateKeyIdKey, PersistentDataType.STRING, crateId);
        itemStack.setItemMeta(meta);
        // ITEM_MODEL (CMD helyett) a setItemMeta UTÁN: key-item-model configból, különben base-item.
        final String keyModel = configManager.getString(basePath + ".key-item-model", null);
        if (keyModel != null && !keyModel.isBlank()) {
            hu.taliann.icesmp.items.ItemDataFactory.applyItemModel(itemStack, keyModel);
        }
        return itemStack;
    }

    /** A top-3 jutalom lore-sorai súly szerinti esély-százalékkal, plusz „…és további N". */
    private List<net.kyori.adventure.text.Component> topRewardLore(final String basePath) {
        final java.util.List<java.util.Map<?, ?>> rewards = configManager.getConfiguration() == null
                ? List.of() : configManager.getConfiguration().getMapList(basePath + ".rewards");
        if (rewards.isEmpty()) {
            return List.of();
        }
        int totalWeight = 0;
        for (final java.util.Map<?, ?> reward : rewards) {
            totalWeight += rewardWeight(reward);
        }
        if (totalWeight <= 0) {
            return List.of();
        }
        final java.util.List<java.util.Map<?, ?>> sorted = new java.util.ArrayList<>(rewards);
        sorted.sort((a, b) -> Integer.compare(rewardWeight(b), rewardWeight(a)));

        final java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        final int shown = Math.min(3, sorted.size());
        for (int i = 0; i < shown; i++) {
            final java.util.Map<?, ?> reward = sorted.get(i);
            final long percent = Math.round(rewardWeight(reward) * 100.0D / totalWeight);
            lore.add(SERIALIZER.deserialize(TextUtil.color(
                            "&8◆ &7" + rewardLabel(reward) + " &8— &e" + percent + "%"))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (sorted.size() > shown) {
            lore.add(SERIALIZER.deserialize(TextUtil.color("&8…és további " + (sorted.size() - shown) + " jutalom"))
                    .decoration(TextDecoration.ITALIC, false));
        }
        return lore;
    }

    private static int rewardWeight(final java.util.Map<?, ?> reward) {
        final Object weight = reward.get("weight");
        return weight instanceof Number number ? Math.max(0, number.intValue()) : 0;
    }

    private static String rewardLabel(final java.util.Map<?, ?> reward) {
        final Object description = reward.get("description");
        if (description instanceof String text && !text.isBlank()) {
            return TextUtil.color(text);
        }
        final Object material = reward.get("material");
        final Object amount = reward.get("amount");
        final String name = material instanceof String materialName
                ? materialName.toLowerCase(java.util.Locale.ROOT).replace('_', ' ') : "jutalom";
        return name + (amount instanceof Number number && number.intValue() > 1 ? " ×" + number.intValue() : "");
    }

    /** Whether the item is any crate key. */
    public boolean isKey(final ItemStack itemStack) {
        return keyCrateId(itemStack) != null;
    }

    /** The crate id this key opens, or null if the item is not a crate key. */
    public String keyCrateId(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().get(crateKeyIdKey, PersistentDataType.STRING);
    }
}
