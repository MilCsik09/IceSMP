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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls unique random-attribute "affixes" onto crafted profession masterworks, so every
 * masterwork comes out with a random quality (Közönséges → Legendás) and 1-4 rolled
 * attribute bonuses. The affix pool, quality weights and value ranges are config-driven
 * ({@code professions.masterwork}); an item is tagged in its PDC once rolled so it is never
 * re-rolled. Weapons/tools draw hand-slot affixes (attack), armour draws armour-slot affixes.
 */
public final class MasterworkAffixService {

    /** Which item family a masterwork belongs to (decides the affix pool + slot group). */
    private enum Family { ARMOR, WEAPON, TOOL, OTHER }

    /** A rollable affix: its attribute, the slot it applies in, and which families can roll it. */
    private record Affix(String id, Attribute attribute, EquipmentSlotGroup slot, boolean forArmor, boolean forHand) {
    }

    /** A rolled quality tier. */
    private record Quality(String id, String name, String color, int weight, int affixCount) {
    }

    /** A loot source tier: its quality profile, affix-value multiplier and negative-roll chance. */
    private record LootTier(List<Quality> qualities, double valueMultiplier, double negativeChance) {
    }

    /** Source tiers (config: professions.masterwork.tiers.&lt;id&gt;). */
    public static final String TIER_DROP = "drop";
    public static final String TIER_CRAFTED = "crafted";
    public static final String TIER_BOSS = "boss";

    private static final List<Affix> AFFIXES = List.of(
            new Affix("max_health", Attribute.MAX_HEALTH, EquipmentSlotGroup.ARMOR, true, false),
            new Affix("armor", Attribute.ARMOR, EquipmentSlotGroup.ARMOR, true, false),
            new Affix("armor_toughness", Attribute.ARMOR_TOUGHNESS, EquipmentSlotGroup.ARMOR, true, false),
            new Affix("movement_speed", Attribute.MOVEMENT_SPEED, EquipmentSlotGroup.ARMOR, true, false),
            new Affix("attack_damage", Attribute.ATTACK_DAMAGE, EquipmentSlotGroup.MAINHAND, false, true),
            new Affix("attack_speed", Attribute.ATTACK_SPEED, EquipmentSlotGroup.MAINHAND, false, true));

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey qualityKey;

    public MasterworkAffixService(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.qualityKey = new NamespacedKey(plugin, "masterwork_quality");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("professions.masterwork.enabled", true);
    }

    /** Whether the item already carries a rolled masterwork quality (so it is not re-rolled). */
    public boolean isRolled(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(qualityKey, PersistentDataType.STRING);
    }

    /**
     * Returns a copy of {@code base} with a rolled quality and random attribute affixes applied
     * for the given loot tier ({@link #TIER_DROP}/{@link #TIER_CRAFTED}/{@link #TIER_BOSS}), or the
     * original item unchanged when the feature is off, the item is not affixable (e.g. an enchanted
     * book), or it was already rolled. The tier picks the quality profile and an affix-value
     * multiplier — drops roll weaker than crafts, boss/hard-event loot rolls on par with crafts.
     */
    public ItemStack roll(final ItemStack base, final String tierId) {
        return roll(base, tierId, false);
    }

    /**
     * Rolls quality + affixes for the given loot tier. With {@code randomName} the item gets a
     * generated random name (mob loot) instead of keeping its designed name (profession crafts);
     * a tier's {@code negative-affix-chance} lets individual affixes roll negative (a curse) — used
     * for mob drops, so scavenged gear can be worse than crafted gear.
     */
    public ItemStack roll(final ItemStack base, final String tierId, final boolean randomName) {
        if (base == null || !isEnabled() || isRolled(base)) {
            return base;
        }
        final Family family = familyOf(base.getType());
        if (family == Family.OTHER) {
            return base;
        }

        final LootTier tier = loadTier(tierId);
        if (tier == null || tier.qualities().isEmpty()) {
            return base;
        }
        final Quality quality = pickQuality(tier.qualities());
        final double valueMultiplier = tier.valueMultiplier();

        final List<Affix> eligible = new ArrayList<>();
        for (final Affix affix : AFFIXES) {
            if (family == Family.ARMOR ? affix.forArmor() : affix.forHand()) {
                eligible.add(affix);
            }
        }
        if (eligible.isEmpty()) {
            return base;
        }

        final ItemStack rolled = base.clone();
        final ItemMeta meta = rolled.getItemMeta();
        final List<Component> extraLore = new ArrayList<>();
        extraLore.add(Component.text("✦ " + quality.name() + (randomName ? "" : " mestermű"), colorOf(quality.color()))
                .decoration(TextDecoration.ITALIC, false));

        final int count = Math.min(quality.affixCount(), eligible.size());
        for (int i = 0; i < count; i++) {
            final Affix affix = eligible.remove(ThreadLocalRandom.current().nextInt(eligible.size()));
            final ConfigurationSection cfg = affixConfig(affix.id());
            final double min = cfg == null ? 0.0D : cfg.getDouble("min", 0.0D);
            final double max = cfg == null ? 0.0D : cfg.getDouble("max", 0.0D);
            if (max <= 0.0D) {
                continue;
            }
            final int decimals = cfg.getInt("decimals", 0);
            double raw = (min + ThreadLocalRandom.current().nextDouble() * Math.max(0.0D, max - min)) * valueMultiplier;
            final boolean negative = tier.negativeChance() > 0.0D
                    && ThreadLocalRandom.current().nextDouble() < tier.negativeChance();
            if (negative) {
                raw = -raw;
            }
            final double amount = round(raw, decimals);
            if (amount == 0.0D) {
                continue;
            }
            final String affixName = cfg.getString("name", affix.id());
            meta.addAttributeModifier(affix.attribute(), new AttributeModifier(
                    new NamespacedKey(plugin, "mw_" + affix.id() + "_" + i),
                    amount, AttributeModifier.Operation.ADD_NUMBER, affix.slot()));
            final String sign = amount > 0.0D ? "+ " : "- ";
            extraLore.add(Component.text("  " + sign + format(Math.abs(amount), decimals) + " " + affixName,
                    amount > 0.0D ? NamedTextColor.GRAY : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }

        final List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.addAll(extraLore);
        meta.lore(lore);
        // Name: keep the designed name for crafts; generate a random name for mob loot.
        final Component baseName = randomName ? Component.text(randomName(rolled.getType()))
                : (meta.hasDisplayName() ? meta.displayName() : Component.text(prettyName(rolled.getType())));
        meta.displayName(Component.text("[" + quality.name() + "] ", colorOf(quality.color()))
                .decoration(TextDecoration.ITALIC, false).append(baseName));
        meta.getPersistentDataContainer().set(qualityKey, PersistentDataType.STRING, quality.id());
        rolled.setItemMeta(meta);
        return rolled;
    }

    /** A generated "&lt;adjective&gt; &lt;noun&gt;" name for mob loot, from the config word pools. */
    private String randomName(final Material material) {
        final List<String> adjectives = configManager.getStringList("professions.masterwork.random-names.adjectives");
        final List<String> nouns = nounPoolFor(familyOf(material));
        final String adjective = adjectives.isEmpty() ? "" : adjectives.get(ThreadLocalRandom.current().nextInt(adjectives.size())) + " ";
        final String noun = nouns.isEmpty() ? prettyName(material) : nouns.get(ThreadLocalRandom.current().nextInt(nouns.size()));
        return adjective + noun;
    }

    private List<String> nounPoolFor(final Family family) {
        final String key = switch (family) {
            case ARMOR -> "armor";
            case WEAPON -> "weapon";
            case TOOL -> "tool";
            default -> "weapon";
        };
        return configManager.getStringList("professions.masterwork.random-names.nouns." + key);
    }

    private Family familyOf(final Material material) {
        final String name = material.name().toLowerCase(Locale.ROOT);
        if (name.endsWith("_helmet") || name.endsWith("_chestplate") || name.endsWith("_leggings")
                || name.endsWith("_boots") || material == Material.SHIELD || material == Material.ELYTRA) {
            return Family.ARMOR;
        }
        if (name.endsWith("_sword") || material == Material.TRIDENT || material == Material.BOW
                || material == Material.CROSSBOW) {
            return Family.WEAPON;
        }
        if (name.endsWith("_pickaxe") || name.endsWith("_axe") || name.endsWith("_shovel")
                || name.endsWith("_hoe")) {
            return Family.TOOL;
        }
        return Family.OTHER;
    }

    private LootTier loadTier(final String tierId) {
        if (configManager.getConfiguration() == null || tierId == null) {
            return null;
        }
        final ConfigurationSection tierSection = configManager.getConfiguration()
                .getConfigurationSection("professions.masterwork.tiers." + tierId);
        if (tierSection == null) {
            return null;
        }
        final List<Quality> qualities = new ArrayList<>();
        for (final Object entry : tierSection.getList("qualities", List.of())) {
            if (!(entry instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            final String id = str(map.get("id"), "kozonseges");
            final String name = str(map.get("name"), id);
            final String color = str(map.get("color"), "&f");
            final int weight = toInt(map.get("weight"), 1);
            final int affixes = toInt(map.get("affixes"), 1);
            if (weight > 0) {
                qualities.add(new Quality(id, name, color, weight, Math.max(1, affixes)));
            }
        }
        final double valueMultiplier = Math.max(0.0D, tierSection.getDouble("value-multiplier", 1.0D));
        final double negativeChance = Math.max(0.0D, Math.min(1.0D, tierSection.getDouble("negative-affix-chance", 0.0D)));
        return new LootTier(qualities, valueMultiplier, negativeChance);
    }

    private static String str(final Object value, final String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private Quality pickQuality(final List<Quality> qualities) {
        int total = 0;
        for (final Quality q : qualities) {
            total += q.weight();
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (final Quality q : qualities) {
            roll -= q.weight();
            if (roll < 0) {
                return q;
            }
        }
        return qualities.get(qualities.size() - 1);
    }

    private ConfigurationSection affixConfig(final String affixId) {
        if (configManager.getConfiguration() == null) {
            return null;
        }
        return configManager.getConfiguration().getConfigurationSection("professions.masterwork.affixes." + affixId);
    }

    private static int toInt(final Object value, final int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (final NumberFormatException exception) {
            return fallback;
        }
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
            case "9" -> NamedTextColor.BLUE;
            case "5" -> NamedTextColor.DARK_PURPLE;
            case "6" -> NamedTextColor.GOLD;
            case "a" -> NamedTextColor.GREEN;
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
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
