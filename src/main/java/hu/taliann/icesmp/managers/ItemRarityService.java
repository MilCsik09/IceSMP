package hu.taliann.icesmp.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Legacy/random-affix rarity authority. Canonical Itemization identity is a hard boundary: managed
 * gear never receives or contributes legacy affixes; BASIC/non-managed gear keeps this system.
 */
public final class ItemRarityService {

    private enum Family { ARMOR, WEAPON, TOOL, OTHER }

    private record Affix(String id, Attribute attribute, EquipmentSlotGroup slot, boolean forArmor, boolean forHand) { }

    private record Rarity(String id, String name, String color, int affixCount, double negativeChance,
                         double valueMultiplier, List<String> adjectives, List<String> flavor) { }

    public static final String TIER_DROP = "drop";
    public static final String TIER_CRAFTED = "crafted";
    public static final String TIER_BOSS = "boss";

    private static final List<Affix> AFFIXES = List.of(
            new Affix("max_health", Attribute.MAX_HEALTH, EquipmentSlotGroup.ARMOR, true, false),
            new Affix("armor", Attribute.ARMOR, EquipmentSlotGroup.ARMOR, true, false),
            new Affix("armor_toughness", Attribute.ARMOR_TOUGHNESS, EquipmentSlotGroup.ARMOR, true, false),
            new Affix("movement_speed", Attribute.MOVEMENT_SPEED, EquipmentSlotGroup.ARMOR, true, false),
            new Affix("attack_damage", Attribute.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND, false, true),
            new Affix("attack_speed", Attribute.ATTACK_SPEED, EquipmentSlotGroup.MAINHAND, false, true),
            new Affix("spell_power", null, EquipmentSlotGroup.ANY, true, true));

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey qualityKey;
    private final NamespacedKey spellPowerKey;
    private final NamespacedKey managedSchemaKey;

    public ItemRarityService(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.qualityKey = new NamespacedKey(plugin, "masterwork_quality");
        this.spellPowerKey = new NamespacedKey(plugin, "spell_power");
        this.managedSchemaKey = new NamespacedKey(plugin, "item_schema");
    }

    /** Legacy Varázserő is inert on every identity-managed canonical item, valid or invalid. */
    public double spellPowerOf(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta() || isCanonicalManaged(item)) {
            return 0.0D;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(spellPowerKey, PersistentDataType.DOUBLE, 0.0D);
    }

    private boolean isCanonicalManaged(final ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(managedSchemaKey);
    }

    public boolean isEnabled() {
        return configManager.getBoolean("item-rarity.enabled", true);
    }

    public String rarityIdOf(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(qualityKey, PersistentDataType.STRING);
    }

    public boolean isRolled(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(qualityKey, PersistentDataType.STRING);
    }

    public ItemStack roll(final ItemStack base, final String tierId) {
        return roll(base, tierId, false);
    }

    /** Canonical identity never enters the legacy rarity/affix path. */
    public ItemStack roll(final ItemStack base, final String tierId, final boolean randomName) {
        if (base == null || !isEnabled() || isRolled(base) || isCanonicalManaged(base)) return base;
        final Family family = familyOf(base.getType());
        if (family == Family.OTHER) return base;

        final Map<String, Rarity> ladder = loadRarities();
        final Rarity rarity = pickRarity(tierId, ladder);
        if (rarity == null) return base;

        final List<Affix> eligible = new ArrayList<>();
        for (final Affix affix : AFFIXES) {
            if (affix.attribute() == null && !configManager.getBoolean("health.enabled", true)) continue;
            if (family == Family.ARMOR ? affix.forArmor() : affix.forHand()) eligible.add(affix);
        }
        if (eligible.isEmpty()) return base;

        final ItemStack rolled = base.clone();
        final ItemMeta meta = rolled.getItemMeta();
        final List<Component> extraLore = new ArrayList<>();
        extraLore.add(Component.text("✦ " + rarity.name() + (randomName ? "" : " mestermű"), colorOf(rarity.color()))
                .decoration(TextDecoration.ITALIC, false));
        if (!rarity.flavor().isEmpty()) {
            extraLore.add(Component.text("\"" + rarity.flavor().get(ThreadLocalRandom.current().nextInt(rarity.flavor().size())) + "\"",
                    colorOf(rarity.color())).decoration(TextDecoration.ITALIC, true));
        }

        final int count = Math.min(rarity.affixCount(), eligible.size());
        for (int i = 0; i < count; i++) {
            final Affix affix = eligible.remove(ThreadLocalRandom.current().nextInt(eligible.size()));
            final ConfigurationSection cfg = affixConfig(affix.id());
            final double min = cfg == null ? 0.0D : cfg.getDouble("min", 0.0D);
            final double max = cfg == null ? 0.0D : cfg.getDouble("max", 0.0D);
            if (max <= 0.0D) continue;
            final int decimals = cfg.getInt("decimals", 0);
            double raw = (min + ThreadLocalRandom.current().nextDouble() * Math.max(0.0D, max - min)) * rarity.valueMultiplier();
            if (rarity.negativeChance() > 0.0D && ThreadLocalRandom.current().nextDouble() < rarity.negativeChance()) raw = -raw;
            double amount = round(raw, decimals);
            if (amount == 0.0D && raw != 0.0D) amount = Math.copySign(Math.pow(10, -Math.max(0, decimals)), raw);
            if (amount == 0.0D) continue;
            final String affixName = cfg.getString("name", affix.id());
            if (affix.attribute() == null) {
                final double stored = meta.getPersistentDataContainer()
                        .getOrDefault(spellPowerKey, PersistentDataType.DOUBLE, 0.0D);
                meta.getPersistentDataContainer().set(spellPowerKey, PersistentDataType.DOUBLE, stored + amount);
            } else {
                hu.taliann.icesmp.items.ItemDataFactory.seedDefaultAttributeModifiers(rolled, meta);
                meta.addAttributeModifier(affix.attribute(), new AttributeModifier(
                        new NamespacedKey(plugin, "mw_" + rolled.getType().name().toLowerCase(Locale.ROOT)
                                + "_" + affix.id() + "_" + i),
                        amount, AttributeModifier.Operation.ADD_NUMBER, affix.slot()));
            }
            final String sign = amount > 0.0D ? "+ " : "- ";
            final String suffix = affix.attribute() == null ? "%" : "";
            extraLore.add(Component.text("  " + sign + format(Math.abs(amount), decimals) + suffix + " " + affixName,
                    amount > 0.0D ? NamedTextColor.GRAY : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }

        final List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.addAll(extraLore);
        meta.lore(lore);
        final String adjective = randomName && !rarity.adjectives().isEmpty()
                ? rarity.adjectives().get(ThreadLocalRandom.current().nextInt(rarity.adjectives().size())) + " " : "";
        final Component baseName = randomName ? Component.text(adjective + nounFor(family, rolled.getType()))
                : (meta.hasDisplayName() ? meta.displayName() : Component.translatable(rolled.getType()));
        meta.displayName(Component.text("[" + rarity.name() + "] ", colorOf(rarity.color()))
                .decoration(TextDecoration.ITALIC, false).append(baseName));
        meta.getPersistentDataContainer().set(qualityKey, PersistentDataType.STRING, rarity.id());
        rolled.setItemMeta(meta);
        hu.taliann.icesmp.items.ItemDataFactory.hideAttributeTooltip(rolled);
        hu.taliann.icesmp.items.ItemDataFactory.applyRarity(rolled,
                hu.taliann.icesmp.items.ItemDataFactory.vanillaRarityOf(rarity.id()));
        return rolled;
    }

    private Family familyOf(final Material material) {
        final String name = material.name().toLowerCase(Locale.ROOT);
        if (name.endsWith("_helmet") || name.endsWith("_chestplate") || name.endsWith("_leggings")
                || name.endsWith("_boots") || material == Material.SHIELD || material == Material.ELYTRA) {
            return Family.ARMOR;
        }
        if (name.endsWith("_sword") || material == Material.TRIDENT || material == Material.BOW
                || material == Material.CROSSBOW) return Family.WEAPON;
        if (name.endsWith("_pickaxe") || name.endsWith("_axe") || name.endsWith("_shovel")
                || name.endsWith("_hoe")) return Family.TOOL;
        return Family.OTHER;
    }

    private Map<String, Rarity> loadRarities() {
        final Map<String, Rarity> ladder = new LinkedHashMap<>();
        if (configManager.getConfiguration() == null) return ladder;
        final ConfigurationSection section = configManager.getConfiguration().getConfigurationSection("item-rarity.rarities");
        if (section == null) return ladder;
        for (final String id : section.getKeys(false)) {
            final ConfigurationSection r = section.getConfigurationSection(id);
            if (r == null) continue;
            ladder.put(id.toLowerCase(Locale.ROOT), new Rarity(
                    id.toLowerCase(Locale.ROOT), r.getString("name", id), r.getString("color", "&f"),
                    Math.max(1, r.getInt("affixes", 1)),
                    Math.max(0.0D, Math.min(1.0D, r.getDouble("negative-chance", 0.0D))),
                    Math.max(0.0D, r.getDouble("value-multiplier", 1.0D)),
                    r.getStringList("adjectives"), r.getStringList("flavor")));
        }
        return ladder;
    }

    private Rarity pickRarity(final String tierId, final Map<String, Rarity> ladder) {
        if (configManager.getConfiguration() == null || tierId == null || ladder.isEmpty()) return null;
        final ConfigurationSection weights = configManager.getConfiguration()
                .getConfigurationSection("item-rarity.tiers." + tierId + ".weights");
        if (weights == null) return null;
        int total = 0;
        final Map<String, Integer> valid = new LinkedHashMap<>();
        for (final String rarityId : weights.getKeys(false)) {
            final int weight = Math.max(0, weights.getInt(rarityId));
            if (weight > 0 && ladder.containsKey(rarityId.toLowerCase(Locale.ROOT))) {
                valid.put(rarityId.toLowerCase(Locale.ROOT), weight);
                total += weight;
            }
        }
        if (total <= 0) return null;
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (final Map.Entry<String, Integer> entry : valid.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) return ladder.get(entry.getKey());
        }
        return ladder.get(valid.keySet().iterator().next());
    }

    private ConfigurationSection affixConfig(final String affixId) {
        if (configManager.getConfiguration() == null) return null;
        return configManager.getConfiguration().getConfigurationSection("item-rarity.affixes." + affixId);
    }

    private String nounFor(final Family family, final Material material) {
        final String key = switch (family) {
            case ARMOR -> "armor";
            case WEAPON -> "weapon";
            case TOOL -> "tool";
            default -> "weapon";
        };
        final List<String> nouns = configManager.getStringList("item-rarity.nouns." + key);
        return nouns.isEmpty() ? prettyName(material) : nouns.get(ThreadLocalRandom.current().nextInt(nouns.size()));
    }

    private static double round(final double value, final int decimals) {
        final double factor = Math.pow(10, Math.max(0, decimals));
        return Math.round(value * factor) / factor;
    }

    private static String format(final double value, final int decimals) {
        return decimals <= 0 ? String.valueOf((long) value) : String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static NamedTextColor colorOf(final String legacyCode) {
        return switch (legacyCode == null ? "" : legacyCode.replace("&", "").toLowerCase(Locale.ROOT)) {
            case "8" -> NamedTextColor.DARK_GRAY;
            case "7" -> NamedTextColor.GRAY;
            case "9" -> NamedTextColor.BLUE;
            case "a" -> NamedTextColor.GREEN;
            case "5" -> NamedTextColor.DARK_PURPLE;
            case "6" -> NamedTextColor.GOLD;
            case "b" -> NamedTextColor.AQUA;
            case "c" -> NamedTextColor.RED;
            case "e" -> NamedTextColor.YELLOW;
            default -> NamedTextColor.WHITE;
        };
    }

    private static String prettyName(final Material material) {
        final String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder sb = new StringBuilder();
        for (final String part : parts) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
