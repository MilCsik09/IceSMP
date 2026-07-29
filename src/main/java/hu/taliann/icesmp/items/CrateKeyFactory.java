package hu.taliann.icesmp.items;

import hu.taliann.icesmp.crates.CrateFormatting;
import hu.taliann.icesmp.crates.CrateRecoveryLedger;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CrateManager;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** PDC-identified crate keys built from one immutable crate generation. */
public final class CrateKeyFactory {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private final ConfigManager configManager;
    private final NamespacedKey crateKeyIdKey;
    private volatile CrateManager crateManager;

    public CrateKeyFactory(final JavaPlugin plugin, final ConfigManager configManager) {
        this.configManager = configManager;
        this.crateKeyIdKey = new NamespacedKey(plugin, "crate_key");
    }

    public void bind(final CrateManager crateManager) {
        this.crateManager = crateManager;
    }

    /** Compatibility entry point; transaction paths should pass their captured definition instead. */
    public ItemStack createKey(final String crateId, final int amount) {
        final CrateManager manager = crateManager;
        final CrateManager.CrateDefinition definition = manager == null ? null : manager.definition(crateId);
        if (definition != null) {
            return createKey(definition, amount);
        }
        if (crateId == null || crateId.isBlank()) {
            return new ItemStack(Material.AIR);
        }
        final String basePath = "crates." + crateId;
        final Material material = Material.matchMaterial(configManager.getString(basePath + ".key-material",
                "TRIPWIRE_HOOK"));
        if (material == null || material.isAir()) {
            return new ItemStack(Material.AIR);
        }
        return build(crateId, material, configManager.getString(basePath + ".key-name", "&fLáda Kulcs"),
                configManager.getString(basePath + ".display-name", "&8Láda"),
                configManager.getString(basePath + ".key-item-model", null), amount, List.of());
    }

    /** Exact-generation key creation used by purchase/opening transactions. */
    public ItemStack createKey(final CrateManager.CrateDefinition definition, final int amount) {
        if (definition == null) {
            return new ItemStack(Material.AIR);
        }
        return build(definition.id(), definition.keyMaterial(), definition.keyName(),
                definition.displayName(), definition.keyItemModel(), amount, odds(definition));
    }

    /** Builds a compensation key without consulting the current config generation. */
    public ItemStack createRecoveryKey(final String crateId, final CrateRecoveryLedger.KeySpec spec,
                                       final int amount) {
        final Material material = Material.matchMaterial(spec.material());
        if (material == null || material.isAir()) {
            return new ItemStack(Material.AIR);
        }
        return build(crateId, material, spec.displayName(), "&8Visszaadott ládakulcs",
                spec.itemModel(), amount, List.of());
    }

    public CrateRecoveryLedger.KeySpec recoverySpec(final CrateManager.CrateDefinition definition) {
        return new CrateRecoveryLedger.KeySpec(definition.keyMaterial().name(),
                definition.keyName(), definition.keyItemModel());
    }

    private ItemStack build(final String crateId, final Material material, final String keyName,
                            final String crateName, final String itemModel, final int amount,
                            final List<CrateManager.RewardOdds> odds) {
        if (crateId == null || crateId.isBlank() || material == null || material.isAir()) {
            return new ItemStack(Material.AIR);
        }
        final ItemStack itemStack = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }
        meta.displayName(SERIALIZER.deserialize(TextUtil.color(keyName)).decoration(TextDecoration.ITALIC, false));
        final List<Component> lore = new ArrayList<>();
        lore.add(SERIALIZER.deserialize(TextUtil.color(crateName + " &8— kulcs"))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(SERIALIZER.deserialize(TextUtil.color("&7Jobb katt a ládán: &fkinyitás"))
                .decoration(TextDecoration.ITALIC, false));
        final int shown = Math.min(3, odds.size());
        for (int index = 0; index < shown; index++) {
            final CrateManager.RewardOdds reward = odds.get(index);
            lore.add(SERIALIZER.deserialize(TextUtil.color("&8◆ &7" + reward.description()
                            + " &8— &e" + CrateFormatting.decimal(reward.percent()) + "%"))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (odds.size() > shown) {
            lore.add(SERIALIZER.deserialize(TextUtil.color("&8…és további " + (odds.size() - shown) + " jutalom"))
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(crateKeyIdKey, PersistentDataType.STRING, crateId);
        itemStack.setItemMeta(meta);
        if (itemModel != null && !itemModel.isBlank()) {
            ItemDataFactory.applyItemModel(itemStack, itemModel);
        }
        return itemStack;
    }

    private static List<CrateManager.RewardOdds> odds(final CrateManager.CrateDefinition definition) {
        final double total = definition.rewards().stream().mapToDouble(CrateManager.RewardEntry::weight).sum();
        return definition.rewards().stream()
                .map(reward -> new CrateManager.RewardOdds(reward, CrateManager.describeReward(reward),
                        reward.weight() / total * 100.0D))
                .sorted(Comparator.comparingDouble(CrateManager.RewardOdds::percent).reversed())
                .toList();
    }

    public boolean isKey(final ItemStack itemStack) {
        return keyCrateId(itemStack) != null;
    }

    public String keyCrateId(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().get(crateKeyIdKey, PersistentDataType.STRING);
    }
}
