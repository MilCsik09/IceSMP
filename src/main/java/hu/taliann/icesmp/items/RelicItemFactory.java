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
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.inventory.meta.components.ToolComponent.ToolRule;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("UnstableApiUsage")
public final class RelicItemFactory {

    private static final String METELYTEPO_ID = "metelytepo";
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

        final String rawOwner = itemStack.getItemMeta().getPersistentDataContainer().get(relicOwnerKey, PersistentDataType.STRING);
        if (rawOwner == null || rawOwner.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(rawOwner);
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    public void refresh(final ItemStack itemStack, final RelicDefinition definition) {
        if (itemStack == null || definition == null || !itemStack.hasItemMeta()) {
            return;
        }

        final ItemMeta meta = itemStack.getItemMeta();
        applyVisuals(meta, definition);
        itemStack.setItemMeta(meta);
    }

    private void applyVisuals(final ItemMeta meta, final RelicDefinition definition) {
        final Component displayName = serializer.deserialize(TextUtil.color(definition.displayColor() + definition.displayName()))
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(displayName);

        final List<String> loreLines = definition.lore() == null ? List.of() : definition.lore();
        final List<Component> lore = loreLines.stream()
                .<Component>map(line -> serializer.deserialize(TextUtil.color(line)).decoration(TextDecoration.ITALIC, false))
                .toList();
        meta.lore(lore.isEmpty() ? null : lore);

        applyCustomModelData(meta, definition.customModelData());

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

        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);

        final double defaultDamage = switch (definition.material()) {
            case NETHERITE_AXE -> 10.0D;
            case GOLDEN_AXE -> 7.0D;
            default -> 0.0D;
        };

        final double targetDamage = 12.0D;
        final double damageBonus = Math.max(0.0D, targetDamage - defaultDamage);
        meta.addAttributeModifier(
                Attribute.ATTACK_DAMAGE,
                new AttributeModifier(
                        new NamespacedKey("icesmp", "metelytepo_damage_bonus"),
                        damageBonus,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                )
        );

        final double defaultSpeed = switch (definition.material()) {
            case NETHERITE_AXE, GOLDEN_AXE -> 1.0D;
            default -> 4.0D;
        };
        final double targetSpeed = 1.0D;
        final double speedDelta = targetSpeed - defaultSpeed;

        meta.addAttributeModifier(
                Attribute.ATTACK_SPEED,
                new AttributeModifier(
                        new NamespacedKey("icesmp", "metelytepo_speed_baseline"),
                        speedDelta,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                )
        );
    }

    private void applyCustomModelData(final ItemMeta meta, final int customModelData) {
        final CustomModelDataComponent component = meta.getCustomModelDataComponent();
        if (customModelData > 0) {
            component.setFloats(List.of((float) customModelData));
        } else {
            component.setFloats(List.of());
        }
        meta.setCustomModelDataComponent(component);
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
            // Prefer direct API shape: addRule(Tag, speed, correctForDrops)
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

            // Fallback single-rule mutator with ToolRule parameter.
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

            // Fallback: bulk setter style APIs.
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
            LOGGER.log(Level.WARNING, "Failed to apply pickaxe tool rule via reflection; Metelytepo mining speed may not work.", exception);
        }
    }
}


