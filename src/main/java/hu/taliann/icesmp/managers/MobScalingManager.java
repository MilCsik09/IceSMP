package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Manager for distance-based mob leveling.
 * Mobs spawning farther from the world spawn receive higher levels,
 * scaling their max health and attack damage, with an optional level name tag.
 */
public final class MobScalingManager {

    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final BloodMoonManager bloodMoonManager;
    private final TerritoryManager territoryManager;
    private final NamespacedKey mobLevelKey;
    private final Set<SpawnReason> ignoredSpawnReasons = EnumSet.noneOf(SpawnReason.class);

    private boolean enabled;
    private int blocksPerLevel;
    private int maxLevel;
    private double healthPerLevel;
    private double damagePerLevel;
    private boolean hostileOnly;
    private boolean nameEnabled;
    private boolean nameVisible;
    private String namePrefix;
    private NamedTextColor nameColor;

    public MobScalingManager(final JavaPlugin plugin, final ConfigManager configManager,
                             final BloodMoonManager bloodMoonManager, final TerritoryManager territoryManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bloodMoonManager = bloodMoonManager;
        this.territoryManager = territoryManager;
        this.mobLevelKey = new NamespacedKey(plugin, "mob_level");
    }

    public void load() {
        enabled = configManager.getBoolean("mob-scaling.enabled", true);
        blocksPerLevel = Math.max(1, configManager.getInt("mob-scaling.blocks-per-level", 1000));
        maxLevel = Math.max(1, configManager.getInt("mob-scaling.max-level", 10));
        healthPerLevel = Math.max(0.0D, configManager.getDouble("mob-scaling.health-per-level", 2.0D));
        damagePerLevel = Math.max(0.0D, configManager.getDouble("mob-scaling.damage-per-level", 1.0D));
        hostileOnly = configManager.getBoolean("mob-scaling.hostile-only", true);
        nameEnabled = configManager.getBoolean("mob-scaling.name.enabled", true);
        // Egyértelmű név: always-visible (true = falakon át/messziről is látszik; false =
        // csak ránézésre). A régi 'visible' kulcs legacy-fallbackként él tovább.
        nameVisible = configManager.getConfiguration() != null
                && configManager.getConfiguration().isSet("mob-scaling.name.always-visible")
                ? configManager.getBoolean("mob-scaling.name.always-visible", true)
                : configManager.getBoolean("mob-scaling.name.visible", true);
        namePrefix = configManager.getString("mob-scaling.name.prefix", "&7[Lvl %level%] ");
        nameColor = resolveColor(configManager.getString("mob-scaling.name.color", "WHITE"));

        ignoredSpawnReasons.clear();
        final List<String> rawReasons = configManager.getStringList("mob-scaling.ignored-spawn-reasons");
        for (final String rawReason : rawReasons) {
            try {
                ignoredSpawnReasons.add(SpawnReason.valueOf(rawReason.trim().toUpperCase(Locale.ROOT)));
            } catch (final IllegalArgumentException exception) {
                plugin.getLogger().warning("Unknown spawn reason in 'mob-scaling.ignored-spawn-reasons': " + rawReason);
            }
        }

        if (enabled) {
            plugin.getLogger().info("Mob scaling enabled: 1 level per " + blocksPerLevel + " blocks, max level " + maxLevel + ".");
        } else {
            plugin.getLogger().info("Mob scaling is disabled in config.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Applies distance-based scaling to a freshly spawned mob.
     * Safe to call from the spawn event handler; the entity is owned by the current region thread.
     *
     * @param entity the spawned living entity
     * @param spawnReason the spawn reason reported by the event
     */
    public void applyScaling(final LivingEntity entity, final SpawnReason spawnReason) {
        if (!enabled || entity == null || ignoredSpawnReasons.contains(spawnReason)) {
            return;
        }

        if (hostileOnly && !(entity instanceof Monster)) {
            return;
        }

        if (entity.getPersistentDataContainer().has(mobLevelKey, PersistentDataType.INTEGER)) {
            return;
        }

        // Blood moon nights spawn every mob with bonus levels (may exceed max-level), and a
        // territórium mob-szabálya (territory.mob-rules — pl. Kárhozat-zóna, DARK-földek)
        // adds its own danger bonus on top.
        // Egyetlen zóna-lookup spawnonként — a szelektor-listát mindkét fogyasztó megkapja.
        final java.util.List<String> zoneSelectors = zoneRuleSelectors(entity.getLocation());
        final int level = resolveLevel(entity.getLocation()) + bloodMoonManager.getBonusMobLevels()
                + zoneBonusLevels(zoneSelectors);
        applyZoneHardening(entity, zoneSelectors);
        if (level < 1) {
            return;
        }

        applyLevel(entity, level);
    }

    /**
     * Applies a specific level to an entity (attributes + name tag + PDC marker),
     * bypassing the distance/spawn-reason checks. Used by event spawners (e.g.
     * invasions) so their mobs grant scaled XP and soulstone drops.
     *
     * @param entity the living entity
     * @param level the level to apply (≥ 1)
     */
    public void forceLevel(final LivingEntity entity, final int level) {
        if (entity == null || level < 1 || entity.getPersistentDataContainer().has(mobLevelKey, PersistentDataType.INTEGER)) {
            return;
        }
        applyLevel(entity, level);
    }

    private void applyLevel(final LivingEntity entity, final int level) {
        entity.getPersistentDataContainer().set(mobLevelKey, PersistentDataType.INTEGER, level);

        final AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() + (level * healthPerLevel));
            entity.setHealth(maxHealth.getValue());
        }

        final AttributeInstance attackDamage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.setBaseValue(attackDamage.getBaseValue() + (level * damagePerLevel));
        }

        if (nameEnabled) {
            applyLevelName(entity, level);
        }
    }

    /** @return the mob's stored level, or 0 if the entity is not scaled */
    public int getLevel(final LivingEntity entity) {
        if (entity == null) {
            return 0;
        }

        return entity.getPersistentDataContainer().getOrDefault(mobLevelKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * A territórium mob-szabályainak (territory.mob-rules.<szelektor>) kulcsai a spawn
     * helyén. Két szelektor illeszkedhet egyszerre: a zóna TÍPUSA (pl. doom-gate,
     * protected-city) és a zóna tulajdonos-FRAKCIÓJA (dark/red/blue/neutral) — így a
     * „minden DARK-föld" és a „minden Kárhozat-zóna" is külön szabályozható. Lock-mentes
     * zóna-lookup, a spawnoló régió-szálán biztonságos. Üres lista = nincs zóna.
     */
    private java.util.List<String> zoneRuleSelectors(final Location location) {
        final hu.taliann.icesmp.data.Territory zone = territoryManager.getTerritoryAt(location);
        if (zone == null) {
            return java.util.List.of();
        }
        return java.util.List.of(
                zone.type().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                zone.faction().name().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Bonus mob levels a spawn helyének territórium-szabályaiból (a legnagyobb illeszkedő
     * érték számít, nem összeadás — kiszámítható marad a végszint). Doom-gate default: 3.
     */
    private int zoneBonusLevels(final java.util.List<String> selectors) {
        int bonus = 0;
        for (final String selector : selectors) {
            final int fallback = "doom-gate".equals(selector) ? 3 : 0;
            bonus = Math.max(bonus, Math.max(0,
                    configManager.getInt("territory.mob-rules." + selector + ".bonus-levels", fallback)));
        }
        return bonus;
    }

    /**
     * Zóna-alapú mob-keményítés a spawn pillanatában: no-daylight-burn = a zombi/csontváz/
     * phantom típus nappal sem gyullad meg (pl. DARK-földek örök élőhalottjai), no-zombification
     * = piglin/hoglin nem alakul át az overworldben. Doom-gate default: mindkettő igaz.
     */
    private void applyZoneHardening(final LivingEntity entity, final java.util.List<String> selectors) {
        boolean noBurn = false;
        boolean noZombification = false;
        for (final String selector : selectors) {
            final boolean fallback = "doom-gate".equals(selector);
            noBurn |= configManager.getBoolean("territory.mob-rules." + selector + ".no-daylight-burn", fallback);
            noZombification |= configManager.getBoolean("territory.mob-rules." + selector + ".no-zombification", fallback);
        }
        if (noBurn) {
            if (entity instanceof org.bukkit.entity.AbstractSkeleton skeleton) {
                skeleton.setShouldBurnInDay(false);
            } else if (entity instanceof org.bukkit.entity.Zombie zombie) {
                zombie.setShouldBurnInDay(false);
            } else if (entity instanceof org.bukkit.entity.Phantom phantom) {
                phantom.setShouldBurnInDay(false);
            }
        }
        if (noZombification) {
            if (entity instanceof org.bukkit.entity.PiglinAbstract piglin) {
                piglin.setImmuneToZombification(true);
            } else if (entity instanceof org.bukkit.entity.Hoglin hoglin) {
                hoglin.setImmuneToZombification(true);
            }
        }
    }

    /**
     * Resolves the mob level for a location based on horizontal distance from the world spawn.
     *
     * @param location the spawn location
     * @return the level (0 means no scaling)
     */
    public int resolveLevel(final Location location) {
        if (location == null || location.getWorld() == null) {
            return 0;
        }

        final Location spawn = location.getWorld().getSpawnLocation();
        final double deltaX = location.getX() - spawn.getX();
        final double deltaZ = location.getZ() - spawn.getZ();
        final double distance = Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ));
        return (int) Math.min(maxLevel, distance / blocksPerLevel);
    }

    private void applyLevelName(final LivingEntity entity, final int level) {
        final String prefixText = namePrefix == null ? "" : namePrefix.replace("%level%", String.valueOf(level));
        final Component name = SECTION_SERIALIZER.deserialize(TextUtil.color(prefixText))
                .append(Component.translatable(entity.getType()).color(nameColor));
        entity.customName(name);
        entity.setCustomNameVisible(nameVisible);
    }

    private NamedTextColor resolveColor(final String rawColor) {
        if (rawColor == null || rawColor.isBlank()) {
            return NamedTextColor.WHITE;
        }

        final NamedTextColor color = NamedTextColor.NAMES.value(rawColor.trim().toLowerCase(Locale.ROOT));
        return color == null ? NamedTextColor.WHITE : color;
    }
}
