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
 * Rolls unique random-attribute gear with a WoW/Terraria-style rarity ladder. Every affixable item
 * (profession crafts and mob loot) rolls a rarity from a shared ladder — Ócska (Trash) → Ereklye
 * (Artifact) — that determines the colour, the name prefix/adjective, how many affixes roll, how
 * strong they are (value-multiplier) and the chance an affix rolls negative. Trash is all-negative;
 * higher rarities roll more and stronger bonuses, so the prefix matches the affixes. Which rarities
 * a source can roll (and their weights) comes from the loot tier. Rolled items carry a PDC tag so
 * they are never re-rolled.
 */
public final class MasterworkAffixService {

    private enum Family { ARMOR, WEAPON, TOOL, OTHER }

    private record Affix(String id, Attribute attribute, EquipmentSlotGroup slot, boolean forArmor, boolean forHand) {
    }

    /** One rung of the shared rarity ladder. */
    private record Rarity(String id, String name, String color, int affixCount, double negativeChance,
                         double valueMultiplier, List<String> adjectives) {
    }

    /** Source tiers (config: professions.masterwork.tiers.&lt;id&gt;.weights). */
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

    /** Whether the item already carries a rolled rarity (so it is not re-rolled). */
    public boolean isRolled(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(qualityKey, PersistentDataType.STRING);
    }

    public ItemStack roll(final ItemStack base, final String tierId) {
        return roll(base, tierId, false);
    }

    /**
     * Rolls a rarity + affixes for the given loot tier. With {@code randomName} the item gets a
     * rarity-matched generated name (mob loot); otherwise it keeps its designed name (crafts).
     */
    public ItemStack roll(final ItemStack base, final String tierId, final boolean randomName) {
        if (base == null || !isEnabled() || isRolled(base)) {
            return base;
        }
        final Family family = familyOf(base.getType());
        if (family == Family.OTHER) {
            return base;
        }

        final Map<String, Rarity> ladder = loadRarities();
        final Rarity rarity = pickRarity(tierId, ladder);
        if (rarity == null) {
            return base;
        }

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
        extraLore.add(Component.text("✦ " + rarity.name() + (randomName ? "" : " mestermű"), colorOf(rarity.color()))
                .decoration(TextDecoration.ITALIC, false));

        final int count = Math.min(rarity.affixCount(), eligible.size());
        for (int i = 0; i < count; i++) {
            final Affix affix = eligible.remove(ThreadLocalRandom.current().nextInt(eligible.size()));
            final ConfigurationSection cfg = affixConfig(affix.id());
            final double min = cfg == null ? 0.0D : cfg.getDouble("min", 0.0D);
            final double max = cfg == null ? 0.0D : cfg.getDouble("max", 0.0D);
            if (max <= 0.0D) {
                continue;
            }
            final int decimals = cfg.getInt("decimals", 0);
            double raw = (min + ThreadLocalRandom.current().nextDouble() * Math.max(0.0D, max - min)) * rarity.valueMultiplier();
            final boolean negative = rarity.negativeChance() > 0.0D
                    && ThreadLocalRandom.current().nextDouble() < rarity.negativeChance();
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
        // Rarity-matched name: a fitting adjective (mob loot) or the designed name (crafts), prefixed
        // with the rarity so the prefix reads its power — WoW/Terraria style.
        final String adjective = randomName && !rarity.adjectives().isEmpty()
                ? rarity.adjectives().get(ThreadLocalRandom.current().nextInt(rarity.adjectives().size())) + " " : "";
        final Component baseName = randomName ? Component.text(adjective + nounFor(family, rolled.getType()))
                : (meta.hasDisplayName() ? meta.displayName() : Component.text(prettyName(rolled.getType())));
        meta.displayName(Component.text("[" + rarity.name() + "] ", colorOf(rarity.color()))
                .decoration(TextDecoration.ITALIC, false).append(baseName));
        meta.getPersistentDataContainer().set(qualityKey, PersistentDataType.STRING, rarity.id());
        rolled.setItemMeta(meta);
        return rolled;
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

    /** Loads the shared rarity ladder (professions.masterwork.rarities). */
    private Map<String, Rarity> loadRarities() {
        final Map<String, Rarity> ladder = new LinkedHashMap<>();
        if (configManager.getConfiguration() == null) {
            return ladder;
        }
        final ConfigurationSection section = configManager.getConfiguration()
                .getConfigurationSection("professions.masterwork.rarities");
        if (section == null) {
            return ladder;
        }
        for (final String id : section.getKeys(false)) {
            final ConfigurationSection r = section.getConfigurationSection(id);
            if (r == null) {
                continue;
            }
            ladder.put(id.toLowerCase(Locale.ROOT), new Rarity(
                    id.toLowerCase(Locale.ROOT),
                    r.getString("name", id),
                    r.getString("color", "&f"),
                    Math.max(1, r.getInt("affixes", 1)),
                    Math.max(0.0D, Math.min(1.0D, r.getDouble("negative-chance", 0.0D))),
                    Math.max(0.0D, r.getDouble("value-multiplier", 1.0D)),
                    r.getStringList("adjectives")));
        }
        return ladder;
    }

    /** Picks a rarity for a loot tier by its weighted rarity distribution (tiers.&lt;id&gt;.weights). */
    private Rarity pickRarity(final String tierId, final Map<String, Rarity> ladder) {
        if (configManager.getConfiguration() == null || tierId == null || ladder.isEmpty()) {
            return null;
        }
        final ConfigurationSection weights = configManager.getConfiguration()
                .getConfigurationSection("professions.masterwork.tiers." + tierId + ".weights");
        if (weights == null) {
            return null;
        }
        int total = 0;
        final Map<String, Integer> valid = new LinkedHashMap<>();
        for (final String rarityId : weights.getKeys(false)) {
            final int weight = Math.max(0, weights.getInt(rarityId));
            if (weight > 0 && ladder.containsKey(rarityId.toLowerCase(Locale.ROOT))) {
                valid.put(rarityId.toLowerCase(Locale.ROOT), weight);
                total += weight;
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (final Map.Entry<String, Integer> entry : valid.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return ladder.get(entry.getKey());
            }
        }
        return ladder.get(valid.keySet().iterator().next());
    }

    private ConfigurationSection affixConfig(final String affixId) {
        if (configManager.getConfiguration() == null) {
            return null;
        }
        return configManager.getConfiguration().getConfigurationSection("professions.masterwork.affixes." + affixId);
    }

    private String nounFor(final Family family, final Material material) {
        final String key = switch (family) {
            case ARMOR -> "armor";
            case WEAPON -> "weapon";
            case TOOL -> "tool";
            default -> "weapon";
        };
        final List<String> nouns = configManager.getStringList("professions.masterwork.nouns." + key);
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
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
