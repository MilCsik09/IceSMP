package hu.taliann.icesmp.items;

import hu.taliann.icesmp.relics.RelicDefinition;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.inventory.meta.components.ToolComponent.ToolRule;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("UnstableApiUsage")
public final class RelicItemFactory {

    private static final String METELYTEPO_ID = "metelytepo";
    static final NamespacedKey METELYTEPO_DAMAGE_KEY =
            new NamespacedKey("icesmp", "metelytepo_damage_bonus");
    static final NamespacedKey METELYTEPO_SPEED_KEY =
            new NamespacedKey("icesmp", "metelytepo_speed_baseline");
    private static final Logger LOGGER = Logger.getLogger(RelicItemFactory.class.getName());

    private final NamespacedKey relicTypeKey;
    private final NamespacedKey relicOwnerKey;
    private final NamespacedKey relicCreatedAtKey;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();

    public RelicItemFactory(final JavaPlugin plugin) {
        this.relicTypeKey = new NamespacedKey(plugin, "relic_id");
        this.relicOwnerKey = new NamespacedKey(plugin, "relic_owner");
        this.relicCreatedAtKey = new NamespacedKey(plugin, "relic_created_at");
    }

    public ItemStack create(final RelicDefinition definition, final UUID owner) {
        final ItemStack itemStack = new ItemStack(definition.material());
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }
        applyVisuals(meta, definition);

        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(relicTypeKey, PersistentDataType.STRING, definition.id());
        if (owner != null) {
            pdc.set(relicOwnerKey, PersistentDataType.STRING, owner.toString());
        }
        pdc.set(relicCreatedAtKey, PersistentDataType.LONG, System.currentTimeMillis());

        itemStack.setItemMeta(meta);
        applyPresentation(itemStack, definition.id());
        return itemStack;
    }

    public boolean isRelicItem(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = itemStack.getItemMeta();
        return meta.getPersistentDataContainer().has(relicTypeKey, PersistentDataType.STRING);
    }

    public String getRelicType(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return null;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().get(relicTypeKey, PersistentDataType.STRING);
    }

    public UUID getOwner(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return null;
        }
        final String rawOwner = itemStack.getItemMeta().getPersistentDataContainer()
                .get(relicOwnerKey, PersistentDataType.STRING);
        if (rawOwner == null || rawOwner.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawOwner);
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    public void setOwner(final ItemStack itemStack, final UUID owner) {
        if (itemStack == null || !itemStack.hasItemMeta() || owner == null) {
            return;
        }
        final ItemMeta meta = itemStack.getItemMeta();
        meta.getPersistentDataContainer().set(relicOwnerKey, PersistentDataType.STRING, owner.toString());
        itemStack.setItemMeta(meta);
        final String relicType = getRelicType(itemStack);
        if (relicType != null) {
            applyPresentation(itemStack, relicType);
        }
    }

    public void refresh(final ItemStack itemStack, final RelicDefinition definition) {
        if (itemStack == null || definition == null || !itemStack.hasItemMeta()) {
            return;
        }
        final ItemMeta meta = itemStack.getItemMeta();
        applyVisuals(meta, definition);
        itemStack.setItemMeta(meta);
        applyPresentation(itemStack, definition.id());
    }

    /**
     * Relics share the same stable render identity for inventory and worn presentation.
     * Non-equippable relics receive only ITEM_MODEL; ELYTRA wing relics additionally resolve the
     * matching {@code assets/icesmp/equipment/relic_<id>.json} through the central wearable layer.
     */
    private static void applyPresentation(final ItemStack itemStack, final String relicId) {
        WearablePresentation.applyWearablePresentation(itemStack, "icesmp:relic_" + relicId, null);
    }

    private void applyVisuals(final ItemMeta meta, final RelicDefinition definition) {
        final Component displayName = serializer
                .deserialize(TextUtil.color(definition.displayColor() + definition.displayName()))
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(displayName);

        final List<String> loreLines = definition.lore() == null ? List.of() : definition.lore();
        final List<Component> lore = loreLines.stream()
                .<Component>map(line -> serializer.deserialize(TextUtil.color(line))
                        .decoration(TextDecoration.ITALIC, false))
                .toList();
        meta.lore(lore.isEmpty() ? null : lore);

        if (METELYTEPO_ID.equalsIgnoreCase(definition.id())) {
            applyMetelytepoMeta(meta, definition);
        }
    }

    private void applyMetelytepoMeta(final ItemMeta meta, final RelicDefinition definition) {
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

        final ToolComponent tool = meta.getTool();
        if (tool != null) {
            applyPickaxeToolRule(tool);
            meta.setTool(tool);
        }

        removeManagedAttributeModifiers(meta, Attribute.ATTACK_DAMAGE, METELYTEPO_DAMAGE_KEY);
        removeManagedAttributeModifiers(meta, Attribute.ATTACK_SPEED, METELYTEPO_SPEED_KEY);
        seedDefaultAttributeModifiers(definition.material().getDefaultAttributeModifiers(), meta);

        final double defaultDamage = switch (definition.material()) {
            case NETHERITE_AXE -> 10.0D;
            case GOLDEN_AXE -> 7.0D;
            default -> 0.0D;
        };
        final double damageBonus = Math.max(0.0D, 12.0D - defaultDamage);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, metelytepoDamageModifier(damageBonus));

        final double defaultSpeed = switch (definition.material()) {
            case NETHERITE_AXE, GOLDEN_AXE -> 1.0D;
            default -> 4.0D;
        };
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                metelytepoSpeedModifier(1.0D - defaultSpeed));
    }

    static AttributeModifier metelytepoDamageModifier(final double amount) {
        return new AttributeModifier(
                METELYTEPO_DAMAGE_KEY,
                amount,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
        );
    }

    static AttributeModifier metelytepoSpeedModifier(final double amount) {
        return new AttributeModifier(
                METELYTEPO_SPEED_KEY,
                amount,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND
        );
    }

    static List<AttributeModifier> managedModifiers(final Collection<AttributeModifier> modifiers,
                                                    final NamespacedKey managedKey) {
        if (modifiers == null || modifiers.isEmpty()) {
            return List.of();
        }
        return modifiers.stream()
                .filter(modifier -> managedKey.equals(modifier.getKey()))
                .toList();
    }

    static void seedDefaultAttributeModifiers(
            final com.google.common.collect.Multimap<Attribute, AttributeModifier> defaults,
            final ItemMeta meta) {
        if (defaults == null || defaults.isEmpty() || meta == null) {
            return;
        }
        for (final Map.Entry<Attribute, AttributeModifier> entry : defaults.entries()) {
            final Collection<AttributeModifier> current = meta.getAttributeModifiers(entry.getKey());
            final boolean alreadyPresent = current != null && current.stream()
                    .anyMatch(modifier -> modifier.getKey().equals(entry.getValue().getKey()));
            if (!alreadyPresent) {
                meta.addAttributeModifier(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void removeManagedAttributeModifiers(final ItemMeta meta,
                                                        final Attribute attribute,
                                                        final NamespacedKey managedKey) {
        for (final AttributeModifier modifier : managedModifiers(
                meta.getAttributeModifiers(attribute), managedKey)) {
            meta.removeAttributeModifier(attribute, modifier);
        }
    }

    private ToolRule createPickaxeToolRule() {
        try {
            for (final Method method : ToolRule.class.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (!ToolRule.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                final Class<?>[] params = method.getParameterTypes();
                if (params.length != 3) {
                    continue;
                }
                if (!Tag.class.isAssignableFrom(params[0])) {
                    continue;
                }
                if (!(params[1] == float.class || params[1] == Float.class)) {
                    continue;
                }
                if (!(params[2] == boolean.class || params[2] == Boolean.class)) {
                    continue;
                }
                return (ToolRule) method.invoke(null, Tag.MINEABLE_PICKAXE, 10.0F, true);
            }
        } catch (final ReflectiveOperationException exception) {
            return null;
        }
        return null;
    }

    private void applyPickaxeToolRule(final ToolComponent tool) {
        try {
            for (final Method method : tool.getClass().getMethods()) {
                if (!method.getName().equals("addRule")) {
                    continue;
                }
                final Class<?>[] params = method.getParameterTypes();
                if (params.length == 3
                        && Tag.class.isAssignableFrom(params[0])
                        && (params[1] == float.class || params[1] == Float.class)
                        && (params[2] == boolean.class || params[2] == Boolean.class)) {
                    method.invoke(tool, Tag.MINEABLE_PICKAXE, 10.0F, true);
                    return;
                }
            }

            final ToolRule rule = createPickaxeToolRule();
            if (rule == null) {
                return;
            }
            for (final Method method : tool.getClass().getMethods()) {
                if (!method.getName().equals("addRule")) {
                    continue;
                }
                final Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0].isAssignableFrom(rule.getClass())) {
                    method.invoke(tool, rule);
                    return;
                }
            }
            for (final Method method : tool.getClass().getMethods()) {
                if (!method.getName().equals("setRules")) {
                    continue;
                }
                final Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && java.util.List.class.isAssignableFrom(params[0])) {
                    method.invoke(tool, List.of(rule));
                    return;
                }
            }
        } catch (final ReflectiveOperationException exception) {
            LOGGER.log(Level.WARNING,
                    "Failed to apply pickaxe tool rule via reflection; Metelytepo mining speed may not work.",
                    exception);
        }
    }
}
