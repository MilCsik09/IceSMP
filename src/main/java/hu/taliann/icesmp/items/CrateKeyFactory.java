package hu.taliann.icesmp.items;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Factory for crate-key items (native crate system, replaces CrazyCrates).
 * A key is a config-themed item (material/name/item-model
 * come from {@code config/crates.yml}) tagged with the {@code crate_key} PDC
 * key so {@link hu.taliann.icesmp.listeners.CrateListener} and
 * {@link hu.taliann.icesmp.managers.CrateManager} can identify which crate it
 * opens without parsing the display name.
 */
public final class CrateKeyFactory {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private final ConfigManager configManager;
    private final NamespacedKey crateKeyIdKey;
    private volatile hu.taliann.icesmp.managers.CrateManager crateManager;

    public CrateKeyFactory(final JavaPlugin plugin, final ConfigManager configManager) {
        this.configManager = configManager;
        this.crateKeyIdKey = new NamespacedKey(plugin, "crate_key");
    }

    /** Binds the validated crate snapshot after the manager is constructed. */
    public void bind(final hu.taliann.icesmp.managers.CrateManager crateManager) {
        this.crateManager = crateManager;
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
        final hu.taliann.icesmp.managers.CrateManager manager = crateManager;
        final Material material = manager == null
                ? Material.matchMaterial(configManager.getString(basePath + ".key-material", "TRIPWIRE_HOOK"))
                : manager.keyMaterial(crateId);
        if (material == null || material.isAir()) {
            return new ItemStack(Material.AIR);
        }

        final ItemStack itemStack = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }

        final String keyName = manager == null
                ? configManager.getString(basePath + ".key-name", "&fLáda Kulcs")
                : manager.keyName(crateId);
        meta.displayName(SERIALIZER.deserialize(TextUtil.color(keyName)).decoration(TextDecoration.ITALIC, false));
        // A kulcs lore-ja a manager teljesen validált immutable snapshotjából készül.
        // A már kiosztott kulcs lore-ja reload után kozmetikailag elavulhat, a PDC azonosítója viszont stabil.
        final java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        final String crateName = manager == null
                ? configManager.getString(basePath + ".display-name", "&8Láda")
                : manager.displayName(crateId);
        lore.add(SERIALIZER.deserialize(TextUtil.color(crateName + " &8— kulcs"))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(SERIALIZER.deserialize(TextUtil.color("&7Jobb katt a ládán: &fkinyitás")).decoration(TextDecoration.ITALIC, false));
        lore.addAll(topRewardLore(crateId, basePath));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(crateKeyIdKey, PersistentDataType.STRING, crateId);
        itemStack.setItemMeta(meta);
        final String keyModel = manager == null
                ? configManager.getString(basePath + ".key-item-model", null)
                : manager.keyItemModel(crateId);
        if (keyModel != null && !keyModel.isBlank()) {
            hu.taliann.icesmp.items.ItemDataFactory.applyItemModel(itemStack, keyModel);
        }
        return itemStack;
    }

    /** A top-3 jutalom lore-sorai a manager validált snapshotjából. */
    private List<net.kyori.adventure.text.Component> topRewardLore(final String crateId, final String basePath) {
        final hu.taliann.icesmp.managers.CrateManager manager = crateManager;
        if (manager != null) {
            final java.util.List<hu.taliann.icesmp.managers.CrateManager.RewardOdds> odds =
                    new java.util.ArrayList<>(manager.rewardOdds(crateId));
            odds.sort((left, right) -> Double.compare(right.percent(), left.percent()));
            if (odds.isEmpty()) {
                return List.of();
            }
            final java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
            final int shown = Math.min(3, odds.size());
            for (int i = 0; i < shown; i++) {
                final var reward = odds.get(i);
                final String percent = new java.text.DecimalFormat("0.#").format(reward.percent());
                lore.add(SERIALIZER.deserialize(TextUtil.color(
                                "&8◆ &7" + reward.description() + " &8— &e" + percent + "%"))
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (odds.size() > shown) {
                lore.add(SERIALIZER.deserialize(TextUtil.color("&8…és további " + (odds.size() - shown) + " jutalom"))
                        .decoration(TextDecoration.ITALIC, false));
            }
            return lore;
        }

        // Construction-time fallback; gameplay always binds the manager before a key is issued.
        final java.util.List<java.util.Map<?, ?>> rewards = configManager.getConfiguration() == null
                ? List.of() : configManager.getConfiguration().getMapList(basePath + ".rewards");
        return rewards.isEmpty() ? List.of() : List.of(
                SERIALIZER.deserialize(TextUtil.color("&8◆ &7Jutalmak: &f" + rewards.size()))
                        .decoration(TextDecoration.ITALIC, false));
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
