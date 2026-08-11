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

    /** H14 — ritka variáns PDC-kulcsa (albino|arnyek); a kill-oldali bónuszok erre szűrnek. */
    public static final org.bukkit.NamespacedKey RARE_VARIANT_KEY =
            org.bukkit.NamespacedKey.fromString("icesmp:rare_variant");

    /** A mob ritka variáns-címkéje (null, ha sima). */
    public static String rareVariantOf(final org.bukkit.entity.Entity entity) {
        return entity.getPersistentDataContainer().get(RARE_VARIANT_KEY,
                org.bukkit.persistence.PersistentDataType.STRING);
    }

    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final BloodMoonManager bloodMoonManager;
    private final TerritoryManager territoryManager;
    private final NamespacedKey mobLevelKey;
    private final NamespacedKey territoryBurnManagedKey;
    private final NamespacedKey territoryBurnBaselineKey;
    private final NamespacedKey territoryZombificationManagedKey;
    private final NamespacedKey territoryZombificationBaselineKey;
    private final NamespacedKey eventBurnKey;
    private final NamespacedKey eventZombificationKey;
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
        this.territoryBurnManagedKey = new NamespacedKey(plugin, "territory_no_daylight_burn");
        this.territoryBurnBaselineKey = new NamespacedKey(plugin, "territory_no_daylight_burn_baseline");
        this.territoryZombificationManagedKey = new NamespacedKey(plugin, "territory_no_zombification");
        this.territoryZombificationBaselineKey = new NamespacedKey(plugin, "territory_no_zombification_baseline");
        this.eventBurnKey = new NamespacedKey(plugin, EventSpawnGuard.EVENT_NO_BURN_KEY);
        this.eventZombificationKey = new NamespacedKey(plugin, EventSpawnGuard.EVENT_NO_ZOMBIFICATION_KEY);
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
            hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager, "Mob scaling enabled: 1 level per " + blocksPerLevel + " blocks, max level " + maxLevel + ".");
        } else {
            hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager, "Mob scaling is disabled in config.");
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
        reconcileTerritoryProtection(entity);
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
        // A vérhold/zóna-bónusz átlépheti a max-level-t, de az abszolút plafon fogja:
        // a "max 10" invariánst a bónuszok legfeljebb hard-cap-level-ig (15) tolhatják.
        final int level = Math.min(
                Math.max(1, configManager.getInt("mob-scaling.hard-cap-level", 15)),
                resolveLevel(entity.getLocation()) + bloodMoonManager.getBonusMobLevels()
                        + zoneBonusLevels(zoneSelectors));
        if (level < 1) {
            return;
        }

        // Ritka variáns sorsolása CSAK ténylegesen szintezett mobra (a szint-kapu
        // után, hogy a jelöletlen mob ne kapjon variáns-tageket).
        maybeMakeRareVariant(entity);
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

    /** Reconciles reversible DARK/doom daylight and zombification protection. */
    public void reconcileTerritoryProtection(final LivingEntity entity) {
        if (entity != null) {
            reconcileTerritoryProtection(entity, entity.getLocation());
        }
    }

    /** Location-explicit overload used by teleport/move events before the entity location mutates. */
    public void reconcileTerritoryProtection(final LivingEntity entity, final Location location) {
        if (entity == null || location == null || location.getWorld() == null) {
            return;
        }
        boolean noBurn = false;
        boolean noZombification = false;
        for (final String selector : zoneRuleSelectors(location)) {
            final boolean fallback = "doom-gate".equals(selector);
            noBurn |= configManager.getBoolean("territory.mob-rules." + selector + ".no-daylight-burn", fallback);
            noZombification |= configManager.getBoolean("territory.mob-rules." + selector + ".no-zombification", fallback);
        }
        reconcileBurn(entity, noBurn);
        reconcileZombification(entity, noZombification);
    }

    public boolean hasTerritoryDaylightProtection(final LivingEntity entity) {
        return entity != null && entity.getPersistentDataContainer()
                .has(territoryBurnManagedKey, PersistentDataType.BYTE);
    }

    private void reconcileBurn(final LivingEntity entity, final boolean requested) {
        final Boolean current = shouldBurnInDay(entity);
        if (current == null) {
            return;
        }
        final var pdc = entity.getPersistentDataContainer();
        final boolean managed = pdc.has(territoryBurnManagedKey, PersistentDataType.BYTE);
        if (requested) {
            if (!managed) {
                pdc.set(territoryBurnBaselineKey, PersistentDataType.BYTE, (byte) (current ? 1 : 0));
                pdc.set(territoryBurnManagedKey, PersistentDataType.BYTE, (byte) 1);
            }
            setShouldBurnInDay(entity, false);
            if (entity.getFireTicks() > 0 && locationHasOpenDaylight(entity.getLocation())) {
                entity.setFireTicks(0);
            }
            return;
        }
        if (!managed) {
            return;
        }
        final boolean eventProtected = pdc.has(eventBurnKey, PersistentDataType.BYTE);
        final byte baseline = pdc.getOrDefault(territoryBurnBaselineKey, PersistentDataType.BYTE, (byte) 1);
        setShouldBurnInDay(entity, eventProtected ? false : baseline != 0);
        pdc.remove(territoryBurnManagedKey);
        pdc.remove(territoryBurnBaselineKey);
    }

    private void reconcileZombification(final LivingEntity entity, final boolean requested) {
        final Boolean current = immuneToZombification(entity);
        if (current == null) {
            return;
        }
        final var pdc = entity.getPersistentDataContainer();
        final boolean managed = pdc.has(territoryZombificationManagedKey, PersistentDataType.BYTE);
        if (requested) {
            if (!managed) {
                pdc.set(territoryZombificationBaselineKey, PersistentDataType.BYTE, (byte) (current ? 1 : 0));
                pdc.set(territoryZombificationManagedKey, PersistentDataType.BYTE, (byte) 1);
            }
            setImmuneToZombification(entity, true);
            return;
        }
        if (!managed) {
            return;
        }
        final boolean eventProtected = pdc.has(eventZombificationKey, PersistentDataType.BYTE);
        final byte baseline = pdc.getOrDefault(territoryZombificationBaselineKey,
                PersistentDataType.BYTE, (byte) 0);
        setImmuneToZombification(entity, eventProtected || baseline != 0);
        pdc.remove(territoryZombificationManagedKey);
        pdc.remove(territoryZombificationBaselineKey);
    }

    private static Boolean shouldBurnInDay(final LivingEntity entity) {
        if (entity instanceof org.bukkit.entity.AbstractSkeleton value) return value.shouldBurnInDay();
        if (entity instanceof org.bukkit.entity.Zombie value) return value.shouldBurnInDay();
        if (entity instanceof org.bukkit.entity.Phantom value) return value.shouldBurnInDay();
        return null;
    }

    private static void setShouldBurnInDay(final LivingEntity entity, final boolean value) {
        if (entity instanceof org.bukkit.entity.AbstractSkeleton skeleton) skeleton.setShouldBurnInDay(value);
        else if (entity instanceof org.bukkit.entity.Zombie zombie) zombie.setShouldBurnInDay(value);
        else if (entity instanceof org.bukkit.entity.Phantom phantom) phantom.setShouldBurnInDay(value);
    }

    private static Boolean immuneToZombification(final LivingEntity entity) {
        if (entity instanceof org.bukkit.entity.PiglinAbstract value) return value.isImmuneToZombification();
        if (entity instanceof org.bukkit.entity.Hoglin value) return value.isImmuneToZombification();
        return null;
    }

    private static void setImmuneToZombification(final LivingEntity entity, final boolean value) {
        if (entity instanceof org.bukkit.entity.PiglinAbstract piglin) piglin.setImmuneToZombification(value);
        else if (entity instanceof org.bukkit.entity.Hoglin hoglin) hoglin.setImmuneToZombification(value);
    }

    private static boolean locationHasOpenDaylight(final Location location) {
        return location.getWorld() != null && location.getWorld().isDayTime()
                && location.getBlock().getLightFromSky() >= 14;
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
        final int normalLevel = (int) Math.min(maxLevel, distance / blocksPerLevel);

        // Zóna-rámpa: a biztonságos territórium-zónák (városok) pereme
        // körül a szint a zónától KIFELÉ nő egyenletesen, amíg el nem éri a táv-alapú
        // "normál" szintet — így a 11-14k-ra épült fővárosok környéke sem Lvl 10-es
        // azonnal. A zóna belsejében 0. Élő kulcsok; a doom-gate/dungeon nem számít
        // biztonságos zónának (ott a mob-rules bónusz él).
        if (normalLevel > 0 && configManager.getBoolean("mob-scaling.zone-ramp.enabled", true)) {
            final double edgeDistance = territoryManager.distanceFromNearestSafeZoneEdge(location);
            if (edgeDistance >= 0.0D) {
                final double rampBlocks = Math.max(1.0D,
                        configManager.getDouble("mob-scaling.zone-ramp.blocks-per-level", 250.0D));
                return Math.min(normalLevel, (int) (edgeDistance / rampBlocks));
            }
        }
        return normalLevel;
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

    /**
     * H14 — ritka spawn-variáns: kis eséllyel a mob „Albínó” (fehér, derengő) vagy
     * „Árnyék” (sötét, fürge) változat lesz — PDC-tag + névtábla; a kill-oldal
     * (kaszt-XP dupla, lélekkő-esély emelt, önálló bestiárium-bejegyzés) erre szűr.
     * A spawn-event a mob régió-szálán fut — az effekt-adás biztonságos.
     */
    private void maybeMakeRareVariant(final LivingEntity entity) {
        final double chance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("rare-variant.chance-percent", 1.5D)));
        if (chance <= 0.0D
                || java.util.concurrent.ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return;
        }
        final boolean albino = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
        entity.getPersistentDataContainer().set(RARE_VARIANT_KEY,
                org.bukkit.persistence.PersistentDataType.STRING, albino ? "albino" : "arnyek");
        // A fej fölött ÁLLANDÓAN látszó név: a nyers EntityType enum-név belső azonosító, ezért
        // fordítható komponenst adunk — a kliens a SAJÁT nyelvén írja ki a mob nevét.
        final net.kyori.adventure.text.format.NamedTextColor variantColor =
                albino ? net.kyori.adventure.text.format.NamedTextColor.WHITE
                        : net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE;
        entity.customName(net.kyori.adventure.text.Component
                .text(albino ? "✦ Albínó " : "☽ Árnyék-", variantColor)
                .append(net.kyori.adventure.text.Component.translatable(entity.getType()).color(variantColor)));
        entity.setCustomNameVisible(true);
        if (albino) {
            entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
        } else {
            entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }
    }
}