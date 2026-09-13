package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.pve.EliteAffix;
import hu.taliann.icesmp.pve.MobProgressionPolicy;
import hu.taliann.icesmp.pve.MobRank;
import hu.taliann.icesmp.pve.MobTemplate;
import hu.taliann.icesmp.pve.MobTemplateRegistry;
import hu.taliann.icesmp.pve.CreatureSpeciesPolicy;
import hu.taliann.icesmp.pve.CreatureSpeciesRegistry;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
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
    private static final double HARD_MAX_HEALTH = 4096.0D;
    private static final double HARD_MAX_DAMAGE = 80.0D;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final BloodMoonManager bloodMoonManager;
    private final TerritoryManager territoryManager;
    private final MobTemplateRegistry mobTemplates;
    private final CreatureSpeciesRegistry creatureSpecies;
    private final NamespacedKey mobLevelKey;
    private final NamespacedKey mobTemplateKey;
    private final NamespacedKey mobRankKey;
    private final NamespacedKey mobArchetypeKey;
    private final NamespacedKey mobAffixesKey;
    private final NamespacedKey encounterModifierKey;
    private final NamespacedKey territoryBurnManagedKey;
    private final NamespacedKey territoryBurnBaselineKey;
    private final NamespacedKey authoredBurnKey;
    private final NamespacedKey territoryZombificationManagedKey;
    private final NamespacedKey territoryZombificationBaselineKey;
    private final NamespacedKey eventBurnKey;
    private final NamespacedKey eventZombificationKey;

    private boolean enabled;
    private double blocksPerLevel;
    private MobProgressionPolicy.Tuning progressionTuning = MobProgressionPolicy.Tuning.defaults();
    private boolean nameEnabled;
    private boolean nameVisible;
    private String namePrefix;
    private NamedTextColor nameColor;

    public MobScalingManager(final JavaPlugin plugin, final ConfigManager configManager,
                             final BloodMoonManager bloodMoonManager,
                             final TerritoryManager territoryManager,
                             final MobTemplateRegistry mobTemplates,
                             final CreatureSpeciesRegistry creatureSpecies) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bloodMoonManager = bloodMoonManager;
        this.territoryManager = territoryManager;
        this.mobTemplates = mobTemplates;
        this.creatureSpecies = creatureSpecies;
        this.mobLevelKey = new NamespacedKey(plugin, "mob_level");
        this.mobTemplateKey = new NamespacedKey(plugin, "mob_template");
        this.mobRankKey = new NamespacedKey(plugin, "mob_rank");
        this.mobArchetypeKey = new NamespacedKey(plugin, "mob_archetype");
        this.mobAffixesKey = new NamespacedKey(plugin, "mob_affixes");
        this.encounterModifierKey = new NamespacedKey(plugin, "encounter_stat_modifier");
        this.territoryBurnManagedKey = new NamespacedKey(plugin, "territory_no_daylight_burn");
        this.territoryBurnBaselineKey = new NamespacedKey(plugin,
                EventSpawnGuard.DAYLIGHT_BURN_BASELINE_KEY);
        this.authoredBurnKey = new NamespacedKey(plugin, "authored_no_daylight_burn");
        this.territoryZombificationManagedKey = new NamespacedKey(plugin, "territory_no_zombification");
        this.territoryZombificationBaselineKey = new NamespacedKey(plugin, "territory_no_zombification_baseline");
        this.eventBurnKey = new NamespacedKey(plugin, EventSpawnGuard.EVENT_NO_BURN_KEY);
        this.eventZombificationKey = new NamespacedKey(plugin, EventSpawnGuard.EVENT_NO_ZOMBIFICATION_KEY);
    }

    public void load() {
        enabled = configManager.getBoolean("mob-scaling.enabled", true);
        blocksPerLevel = finitePositive(
                configManager.getDouble("mob-scaling.blocks-per-level", 500.0D), 500.0D);
        progressionTuning = new MobProgressionPolicy.Tuning(
                Math.max(1, configManager.getInt("mob-scaling.normal-max-level", 50)),
                Math.max(1, configManager.getInt("mob-scaling.hard-cap-level", 70)),
                Math.max(1, configManager.getInt("mob-scaling.authored-boss-cap-level", 200)),
                configManager.getDouble("mob-scaling.curves.health-per-level", 0.08D),
                configManager.getDouble("mob-scaling.curves.damage-per-level", 0.025D),
                configManager.getDouble("mob-scaling.curves.maximum-health-multiplier", 8.0D),
                configManager.getDouble("mob-scaling.curves.maximum-damage-multiplier", 3.0D));
        nameEnabled = configManager.getBoolean("mob-scaling.name.enabled", true);
        // Egyértelmű név: always-visible (true = falakon át/messziről is látszik; false =
        // csak ránézésre). A régi 'visible' kulcs legacy-fallbackként él tovább.
        nameVisible = configManager.getConfiguration() != null
                && configManager.getConfiguration().isSet("mob-scaling.name.always-visible")
                ? configManager.getBoolean("mob-scaling.name.always-visible", true)
                : configManager.getBoolean("mob-scaling.name.visible", true);
        namePrefix = configManager.getString("mob-scaling.name.prefix", "&7[Lvl %level%] ");
        nameColor = resolveColor(configManager.getString("mob-scaling.name.color", "WHITE"));

        if (enabled) {
            hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager,
                    "Mob 2.0 scaling enabled: normal 1-" + progressionTuning.normalMaximum()
                            + ", survival cap " + progressionTuning.hardCap() + ".");
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
        if (!enabled || entity == null
                || spawnReason == SpawnReason.CUSTOM || spawnReason == SpawnReason.COMMAND) {
            return;
        }

        final CreatureSpeciesPolicy species = creatureSpecies.profile(entity.getType());
        if (!species.levelEnabled()) return;

        if (entity.getPersistentDataContainer().has(mobLevelKey, PersistentDataType.INTEGER)) {
            return;
        }

        final Location location = entity.getLocation();
        final List<String> zoneSelectors = zoneRuleSelectors(location);
        final int contextualLevel = resolveLevel(location);
        final MobTemplate template = mobTemplates.naturalTemplate(entity.getType(),
                location.getBlock().getBiome().getKey(), naturalContext(location, zoneSelectors),
                entity.getUniqueId(), contextualLevel).orElse(null);
        hu.taliann.icesmp.pve.CombatTelemetry.record("natural_template_selection",
                template == null ? "vanilla_" + entity.getType().name() : template.mobId());
        final Integer templateLevel = template == null ? null
                : template.levelForBaseline(wildernessBaseLevel(location));
        MobRank rank = template == null || !species.rankEnabled() ? MobRank.NORMAL : template.rank();
        if (species.rankEnabled() && rank == MobRank.NORMAL) {
            rank = promotedRank(spawnReason, zoneSelectors, location);
        }
        final MobProgressionPolicy.Resolution resolution = MobProgressionPolicy.resolve(
                new MobProgressionPolicy.Context(null, null, templateLevel,
                        wildernessBaseLevel(location), zoneBonusLevels(zoneSelectors),
                        biomeBonusLevels(location), depthBonusLevels(location),
                        bloodMoonManager.getBonusMobLevels(), false), progressionTuning);
        final List<EliteAffix> affixes = rank == MobRank.ELITE && species.authoredRewardEligible()
                ? rollAffixes(template) : List.of();

        // Ritka variáns sorsolása CSAK ténylegesen szintezett mobra (a szint-kapu
        // után, hogy a jelöletlen mob ne kapjon variáns-tageket).
        if (species.authoredRewardEligible()) maybeMakeRareVariant(entity);
        applyLevel(entity, resolution.level(), rank, template, affixes);
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
        forceRankedLevel(entity, level, MobRank.NORMAL, null, null);
    }

    /**
     * Applies an event-owned rank at spawn time, before any attribute mutation has been
     * published. Rank multipliers, optional authored abilities and the bounded elite-affix
     * roll therefore share the same canonical application step.
     */
    public void forceRankedLevel(final LivingEntity entity, final int level,
                                 final MobRank rank, final String templateId,
                                 final String archetypeId) {
        forceRankedLevel(entity, level, rank, templateId, archetypeId, true);
    }

    public void forceRankedLevel(final LivingEntity entity, final int level,
                                 final MobRank rank, final String templateId,
                                 final String archetypeId, final boolean authoredRewardEligible) {
        if (entity == null || level < 1 || rank == null || entity.getPersistentDataContainer()
                .has(mobLevelKey, PersistentDataType.INTEGER)) return;
        final MobTemplate template = templateId == null || templateId.isBlank()
                ? null : mobTemplates.require(templateId);
        if (template != null && !template.entityType().equals(entity.getType().name())) {
            throw new IllegalArgumentException("MobTemplate entity mismatch: "
                    + template.mobId() + '/' + entity.getType());
        }
        final int boundedLevel = Math.min(rank.bossLike()
                ? progressionTuning.authoredBossCap() : progressionTuning.hardCap(), level);
        final List<EliteAffix> affixes = rank == MobRank.ELITE
                ? rollAffixes(template) : List.of();
        applyLevel(entity, boundedLevel, rank, template, affixes);
        if (authoredRewardEligible) {
            hu.taliann.icesmp.pve.CreatureProfileService.markExplicitAuthoredReward(entity);
        }
        if (template == null && archetypeId != null && !archetypeId.isBlank()) {
            entity.getPersistentDataContainer().set(mobArchetypeKey,
                    PersistentDataType.STRING, archetypeId.trim().toUpperCase(Locale.ROOT));
        }
    }

    /** Applies one explicit authored template; boss overrides may display above level 70. */
    public void forceTemplate(final LivingEntity entity, final String templateId,
                              final Integer explicitLevel) {
        forceTemplate(entity, templateId, explicitLevel, true);
    }

    public void forceTemplate(final LivingEntity entity, final String templateId,
                              final Integer explicitLevel, final boolean authoredRewardEligible) {
        if (entity == null || entity.getPersistentDataContainer()
                .has(mobLevelKey, PersistentDataType.INTEGER)) return;
        final MobTemplate template = mobTemplates.require(templateId);
        final int requested = explicitLevel == null
                ? template.levelAt(0.5D) : explicitLevel;
        final int level = MobProgressionPolicy.resolve(new MobProgressionPolicy.Context(
                requested, null, null, 1, 0, 0, 0, 0,
                template.rank().bossLike()), progressionTuning).level();
        applyLevel(entity, level, template.rank(), template, List.of());
        if (authoredRewardEligible) {
            hu.taliann.icesmp.pve.CreatureProfileService.markExplicitAuthoredReward(entity);
        }
    }

    /**
     * Applies the single encounter-context layer after canonical template/level/rank projection.
     * The PDC guard makes participant scaling idempotent and exposes exact stat provenance.
     */
    public void applyEncounterModifier(final LivingEntity entity, final double healthMultiplier,
                                       final double damageMultiplier, final String provenance) {
        if (entity == null || !Double.isFinite(healthMultiplier) || !Double.isFinite(damageMultiplier)
                || healthMultiplier < 1.0D || healthMultiplier > 16.0D
                || damageMultiplier < 0.5D || damageMultiplier > 2.0D) {
            throw new IllegalArgumentException("invalid encounter stat modifier");
        }
        final var pdc = entity.getPersistentDataContainer();
        if (pdc.has(encounterModifierKey, PersistentDataType.STRING)) {
            throw new IllegalStateException("encounter stat modifier already applied");
        }
        final AttributeInstance maximumHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maximumHealth != null) {
            final double value = Math.min(HARD_MAX_HEALTH,
                    maximumHealth.getBaseValue() * healthMultiplier);
            maximumHealth.setBaseValue(value);
            entity.setHealth(Math.min(value, maximumHealth.getValue()));
        }
        final AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(Math.min(HARD_MAX_DAMAGE,
                    damage.getBaseValue() * damageMultiplier));
        }
        final String source = provenance == null || provenance.isBlank()
                ? "encounter" : provenance.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]", "_");
        pdc.set(encounterModifierKey, PersistentDataType.STRING,
                source + ";health=" + healthMultiplier + ";damage=" + damageMultiplier);
    }

    public String encounterStatProvenance(final LivingEntity entity) {
        return entity == null ? null : entity.getPersistentDataContainer()
                .get(encounterModifierKey, PersistentDataType.STRING);
    }

    /** Metadata-only seam for encounter engines that own their own dynamic attribute snapshot. */
    public void markEncounterMetadata(final LivingEntity entity, final int level,
                                      final MobRank rank, final String templateId,
                                      final String archetypeId) {
        if (entity == null || level < 1 || rank == null) return;
        final var pdc = entity.getPersistentDataContainer();
        pdc.set(mobLevelKey, PersistentDataType.INTEGER, level);
        pdc.set(mobRankKey, PersistentDataType.STRING, rank.name());
        hu.taliann.icesmp.pve.CreatureProfileService.markExplicitAuthoredReward(entity);
        if (templateId != null && !templateId.isBlank()) {
            final MobTemplate template = mobTemplates.require(templateId);
            pdc.set(mobTemplateKey, PersistentDataType.STRING, template.mobId());
            pdc.set(mobArchetypeKey, PersistentDataType.STRING, template.archetype().name());
        } else if (archetypeId != null && !archetypeId.isBlank()) {
            pdc.set(mobArchetypeKey, PersistentDataType.STRING,
                    archetypeId.trim().toUpperCase(Locale.ROOT));
        }
    }

    private void applyLevel(final LivingEntity entity, final int level, final MobRank rank,
                            final MobTemplate template, final List<EliteAffix> affixes) {
        hu.taliann.icesmp.pve.CombatTelemetry.record("rank_distribution", rank.name());
        if (template != null) {
            hu.taliann.icesmp.pve.CombatTelemetry.record("template_spawn", template.mobId());
        }
        entity.getPersistentDataContainer().set(mobLevelKey, PersistentDataType.INTEGER, level);
        entity.getPersistentDataContainer().set(mobRankKey, PersistentDataType.STRING, rank.name());
        if (template != null) {
            entity.getPersistentDataContainer().set(mobTemplateKey, PersistentDataType.STRING,
                    template.mobId());
            entity.getPersistentDataContainer().set(mobArchetypeKey, PersistentDataType.STRING,
                    template.archetype().name());
            if (template.naturalContext().noDaylightBurn()) {
                entity.getPersistentDataContainer().set(authoredBurnKey,
                        PersistentDataType.BYTE, (byte) 1);
            } else {
                entity.getPersistentDataContainer().remove(authoredBurnKey);
            }
        }
        if (!affixes.isEmpty()) {
            entity.getPersistentDataContainer().set(mobAffixesKey, PersistentDataType.STRING,
                    String.join(",", affixes.stream().map(Enum::name).toList()));
        }

        final double templateHealth = template == null ? 1.0D : template.stats().healthMultiplier();
        final double templateDamage = template == null ? 1.0D : template.stats().damageMultiplier();
        final double rankHealth = rankMultiplier(rank, "health-multiplier", rankHealthFallback(rank));
        final double rankDamage = rankMultiplier(rank, "damage-multiplier", rankDamageFallback(rank));
        final double rankArmor = rankMultiplier(rank, "armor-bonus", rankArmorFallback(rank));
        final double rankMovement = rankMultiplier(rank, "movement-multiplier", rankMovementFallback(rank));

        final AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            final MobProgressionPolicy.ScaledStats scaled = MobProgressionPolicy.scale(
                    maxHealth.getBaseValue() * templateHealth, 0.0D, level,
                    rankHealth, rankDamage * templateDamage, progressionTuning);
            final double health = Math.min(absoluteCap(
                    "mob-scaling.maximum-absolute-health", HARD_MAX_HEALTH, HARD_MAX_HEALTH),
                    scaled.maximumHealth());
            maxHealth.setBaseValue(health);
            entity.setHealth(Math.min(health, maxHealth.getValue()));
        }

        final AttributeInstance attackDamage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) {
            final MobProgressionPolicy.ScaledStats scaled = MobProgressionPolicy.scale(
                    1.0D, attackDamage.getBaseValue() * templateDamage, level,
                    rankHealth, rankDamage, progressionTuning);
            attackDamage.setBaseValue(Math.min(absoluteCap(
                    "mob-scaling.maximum-absolute-damage", HARD_MAX_DAMAGE, HARD_MAX_DAMAGE),
                    scaled.attackDamage()));
        }
        final AttributeInstance armor = entity.getAttribute(Attribute.ARMOR);
        if (armor != null) armor.setBaseValue(Math.min(30.0D,
                Math.max(0.0D, armor.getBaseValue() + rankArmor)));
        final AttributeInstance movement = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movement != null) movement.setBaseValue(Math.min(1.0D,
                movement.getBaseValue() * rankMovement
                        * (template == null ? 1.0D : template.stats().movementMultiplier())));

        if (nameEnabled) {
            applyLevelName(entity, level, rank, template, affixes);
        }
        reconcileBurnSources(entity);
    }

    /** @return the mob's stored level, or 0 if the entity is not scaled */
    public int getLevel(final LivingEntity entity) {
        if (entity == null) {
            return 0;
        }

        return entity.getPersistentDataContainer().getOrDefault(mobLevelKey, PersistentDataType.INTEGER, 0);
    }

    public MobRank getRank(final LivingEntity entity) {
        if (entity == null) return MobRank.NORMAL;
        try {
            return MobRank.parse(entity.getPersistentDataContainer().getOrDefault(
                    mobRankKey, PersistentDataType.STRING, MobRank.NORMAL.name()));
        } catch (final IllegalArgumentException ignored) {
            return MobRank.NORMAL;
        }
    }

    public String getTemplateId(final LivingEntity entity) {
        return entity == null ? null : entity.getPersistentDataContainer()
                .get(mobTemplateKey, PersistentDataType.STRING);
    }

    public String getArchetypeId(final LivingEntity entity) {
        return entity == null ? null : entity.getPersistentDataContainer()
                .get(mobArchetypeKey, PersistentDataType.STRING);
    }

    public List<EliteAffix> getAffixes(final LivingEntity entity) {
        if (entity == null) return List.of();
        final String encoded = entity.getPersistentDataContainer()
                .get(mobAffixesKey, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) return List.of();
        try {
            return EliteAffix.validate(java.util.Arrays.stream(encoded.split(","))
                    .map(EliteAffix::parse).toList());
        } catch (final IllegalArgumentException malformed) {
            return List.of();
        }
    }

    public static String templateIdOf(final org.bukkit.entity.Entity entity) {
        if (entity == null) return null;
        return entity.getPersistentDataContainer().get(
                NamespacedKey.fromString("icesmp:mob_template"), PersistentDataType.STRING);
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

    /** All active daylight sources compose; combustion admission must observe the same OR rule. */
    public boolean hasDaylightProtection(final LivingEntity entity) {
        if (entity == null) return false;
        final var pdc = entity.getPersistentDataContainer();
        return hu.taliann.icesmp.pve.DaylightProtectionPolicy.protectedNow(
                pdc.has(authoredBurnKey, PersistentDataType.BYTE),
                pdc.has(territoryBurnManagedKey, PersistentDataType.BYTE),
                pdc.has(eventBurnKey, PersistentDataType.BYTE));
    }

    /** Read-only runtime/evidence seam; authored protection remains template-owned. */
    public boolean hasAuthoredDaylightProtection(final LivingEntity entity) {
        return entity != null && entity.getPersistentDataContainer()
                .has(authoredBurnKey, PersistentDataType.BYTE);
    }

    private void reconcileBurn(final LivingEntity entity, final boolean requested) {
        final Boolean current = shouldBurnInDay(entity);
        if (current == null) {
            return;
        }
        final var pdc = entity.getPersistentDataContainer();
        if (requested) {
            pdc.set(territoryBurnManagedKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(territoryBurnManagedKey);
        }
        reconcileBurnSources(entity);
    }

    /** Authored, territory and event protection compose; removing one source cannot cancel another. */
    private void reconcileBurnSources(final LivingEntity entity) {
        final Boolean current = shouldBurnInDay(entity);
        if (current == null) return;
        final var pdc = entity.getPersistentDataContainer();
        final boolean authored = pdc.has(authoredBurnKey, PersistentDataType.BYTE);
        final boolean territory = pdc.has(territoryBurnManagedKey, PersistentDataType.BYTE);
        final boolean event = pdc.has(eventBurnKey, PersistentDataType.BYTE);
        final boolean protectedNow = hu.taliann.icesmp.pve.DaylightProtectionPolicy
                .protectedNow(authored, territory, event);
        if (protectedNow) {
            if (!pdc.has(territoryBurnBaselineKey, PersistentDataType.BYTE)) {
                // EventSpawnGuard may already have disabled burning before this reconciliation.
                pdc.set(territoryBurnBaselineKey, PersistentDataType.BYTE,
                        (byte) ((event || current) ? 1 : 0));
            }
            setShouldBurnInDay(entity, false);
            if (entity.getFireTicks() > 0 && locationHasOpenDaylight(entity.getLocation())) {
                entity.setFireTicks(0);
            }
            return;
        }
        if (pdc.has(territoryBurnBaselineKey, PersistentDataType.BYTE)) {
            final boolean baseline = pdc.getOrDefault(territoryBurnBaselineKey,
                    PersistentDataType.BYTE, (byte) 1) != 0;
            setShouldBurnInDay(entity, hu.taliann.icesmp.pve.DaylightProtectionPolicy
                    .shouldBurn(baseline, authored, territory, event));
            pdc.remove(territoryBurnBaselineKey);
        }
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

        final List<String> selectors = zoneRuleSelectors(location);
        return MobProgressionPolicy.resolve(new MobProgressionPolicy.Context(
                null, null, null, wildernessBaseLevel(location),
                zoneBonusLevels(selectors), biomeBonusLevels(location),
                depthBonusLevels(location), bloodMoonManager.getBonusMobLevels(),
                false), progressionTuning).level();
    }

    private int wildernessBaseLevel(final Location location) {
        if (location == null || location.getWorld() == null) return 1;

        final Location spawn = location.getWorld().getSpawnLocation();
        final double deltaX = location.getX() - spawn.getX();
        final double deltaZ = location.getZ() - spawn.getZ();
        final double distance = Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ));
        final int normalLevel = MobProgressionPolicy.wildernessLevel(distance,
                blocksPerLevel, progressionTuning.normalMaximum());

        // Zóna-rámpa: a biztonságos territórium-zónák (városok) pereme
        // körül a szint a zónától KIFELÉ nő egyenletesen, amíg el nem éri a táv-alapú
        // "normál" szintet — így a 11-14k-ra épült fővárosok környéke sem Lvl 10-es
        // azonnal. A zóna belsejében 0. Élő kulcsok; a doom-gate/dungeon nem számít
        // biztonságos zónának (ott a mob-rules bónusz él).
        if (configManager.getBoolean("mob-scaling.zone-ramp.enabled", true)) {
            final double edgeDistance = territoryManager.distanceFromNearestSafeZoneEdge(location);
            if (edgeDistance >= 0.0D) {
                final double rampBlocks = finitePositive(configManager.getDouble(
                        "mob-scaling.zone-ramp.blocks-per-level", 250.0D), 250.0D);
                return Math.min(normalLevel, 1 + (int) (edgeDistance / rampBlocks));
            }
        }
        return normalLevel;
    }

    private void applyLevelName(final LivingEntity entity, final int level, final MobRank rank,
                                final MobTemplate template, final List<EliteAffix> affixes) {
        final String prefixText = namePrefix == null ? "" : namePrefix.replace("%level%", String.valueOf(level));
        final Component name = SECTION_SERIALIZER.deserialize(TextUtil.color(prefixText))
                .append(Component.text(template == null ? "" : template.displayName())
                        .color(nameColor))
                .append(template == null ? Component.translatable(entity.getType()).color(nameColor)
                        : Component.empty())
                .append(rank == MobRank.NORMAL ? Component.empty()
                        : Component.text(" [" + rankLabel(rank) + "]", rankColor(rank)))
                .append(affixes.isEmpty() ? Component.empty()
                        : Component.text(" • " + String.join("/", affixes.stream()
                        .map(MobScalingManager::affixLabel).toList()), NamedTextColor.LIGHT_PURPLE));
        entity.customName(name);
        entity.setCustomNameVisible(nameVisible);
    }

    private int biomeBonusLevels(final Location location) {
        if (location == null || location.getWorld() == null) return 0;
        final String biome = location.getBlock().getBiome().getKey().getKey();
        final int biomeBonus = Math.max(0, configManager.getInt(
                "mob-scaling.biome-bonuses." + biome, "deep_dark".equals(biome) ? 8 : 0));
        final String dimension = location.getWorld().getEnvironment().name()
                .toLowerCase(Locale.ROOT);
        final int dimensionBonus = Math.max(0, configManager.getInt(
                "mob-scaling.dimension-bonuses." + dimension, 0));
        return Math.max(biomeBonus, dimensionBonus);
    }

    private int depthBonusLevels(final Location location) {
        if (location == null || location.getWorld() == null
                || location.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) {
            return 0;
        }
        return MobProgressionPolicy.depthBonus(location.getBlockY(),
                configManager.getInt("mob-scaling.depth.start-y", 32),
                Math.max(1, configManager.getInt("mob-scaling.depth.blocks-per-level", 16)),
                Math.max(0, configManager.getInt("mob-scaling.depth.maximum-bonus", 8)));
    }

    private MobRank promotedRank(final SpawnReason spawnReason, final List<String> selectors,
                                 final Location location) {
        if (spawnReason != SpawnReason.NATURAL) {
            return MobRank.NORMAL;
        }
        if (selectors.stream().anyMatch(selector -> selector.equals("protected-city"))) {
            return MobRank.NORMAL;
        }
        final boolean deep = location.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL
                && location.getBlockY() <= configManager.getInt(
                "mob-scaling.promotion.deep-threshold-y", 0);
        final boolean dangerousDimension = location.getWorld().getEnvironment()
                != org.bukkit.World.Environment.NORMAL;
        final double contextElite = (deep ? configManager.getDouble(
                "mob-scaling.promotion.deep-elite-bonus-percent", 0.75D) : 0.0D)
                + (dangerousDimension ? configManager.getDouble(
                "mob-scaling.promotion.dimension-elite-bonus-percent", 0.75D) : 0.0D)
                + (bloodMoonManager.isActive() ? configManager.getDouble(
                "mob-scaling.promotion.blood-moon-elite-bonus-percent", 1.0D) : 0.0D);
        final double eliteChance = clampChance(configManager.getDouble(
                "mob-scaling.promotion.elite-percent", 1.5D) + contextElite);
        final double veteranChance = clampChance(configManager.getDouble(
                "mob-scaling.promotion.veteran-percent", 6.0D)
                + (deep || dangerousDimension ? configManager.getDouble(
                "mob-scaling.promotion.danger-veteran-bonus-percent", 2.0D) : 0.0D));
        final double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble(100.0D);
        if (roll < eliteChance) return MobRank.ELITE;
        return roll < eliteChance + veteranChance ? MobRank.VETERAN : MobRank.NORMAL;
    }

    private Set<String> naturalContext(final Location location,
                                       final List<String> zoneSelectors) {
        final java.util.LinkedHashSet<String> tags = new java.util.LinkedHashSet<>();
        final org.bukkit.World world = location.getWorld();
        tags.add("dimension:" + world.getEnvironment().name().toLowerCase(Locale.ROOT));
        if (world.getEnvironment() == org.bukkit.World.Environment.NORMAL && location.getBlockY() <= 0) {
            tags.add("depth:deep");
        }
        if (!world.isDayTime()) tags.add("time:night");
        else tags.add("time:day");
        if (world.hasStorm()) tags.add("weather:storm");
        if (world.isThundering()) tags.add("weather:thunder");
        if (bloodMoonManager.isActive()) tags.add("event:blood_moon");
        zoneSelectors.forEach(selector -> tags.add("territory:" + selector.replace('-', '_')));
        return Set.copyOf(tags);
    }

    private List<EliteAffix> rollAffixes(final MobTemplate template) {
        final ArrayList<EliteAffix> pool = new ArrayList<>(template == null
                ? List.of(EliteAffix.VOLATILE, EliteAffix.VAMPIRIC, EliteAffix.SHIELDED,
                EliteAffix.FRENZIED, EliteAffix.FROSTBOUND)
                : template.affixPool());
        if (pool.isEmpty()) return List.of();
        Collections.shuffle(pool, java.util.concurrent.ThreadLocalRandom.current());
        final int requested = pool.size() > 1 && java.util.concurrent.ThreadLocalRandom.current()
                .nextDouble(100.0D) < clampChance(configManager.getDouble(
                "mob-scaling.promotion.second-affix-percent", 20.0D)) ? 2 : 1;
        if (requested == 1) return List.of(pool.getFirst());
        for (int index = 1; index < pool.size(); index++) {
            try {
                return EliteAffix.validate(List.of(pool.getFirst(), pool.get(index)));
            } catch (final IllegalArgumentException ignored) {
                // Try another bounded candidate; unsafe combinations never reach runtime.
            }
        }
        return List.of(pool.getFirst());
    }

    private double rankMultiplier(final MobRank rank, final String field, final double fallback) {
        final double configured = configManager.getDouble("mob-scaling.ranks."
                + rank.name().toLowerCase(Locale.ROOT) + '.' + field, fallback);
        if (!Double.isFinite(configured)) return fallback;
        final double maximum = field.startsWith("health") ? 50.0D
                : field.startsWith("movement") ? 2.0D : 20.0D;
        final double minimum = field.startsWith("health") || field.startsWith("movement") ? 0.1D : 0.0D;
        return Math.max(minimum, Math.min(maximum, configured));
    }

    private double absoluteCap(final String path, final double fallback, final double hardMaximum) {
        final double configured = configManager.getDouble(path, fallback);
        if (!Double.isFinite(configured) || configured <= 0.0D) return fallback;
        return Math.min(hardMaximum, configured);
    }

    private static double finitePositive(final double configured, final double fallback) {
        return Double.isFinite(configured) && configured > 0.0D ? configured : fallback;
    }

    private static double rankHealthFallback(final MobRank rank) {
        return switch (rank) {
            case NORMAL -> 1.0D;
            case VETERAN -> 1.35D;
            case ELITE -> 1.85D;
            case CHAMPION -> 2.4D;
            case MINIBOSS -> 3.2D;
            case BOSS -> 4.0D;
            case WORLD_BOSS -> 5.0D;
        };
    }

    private static double rankDamageFallback(final MobRank rank) {
        return switch (rank) {
            case NORMAL -> 1.0D;
            case VETERAN -> 1.08D;
            case ELITE -> 1.15D;
            case CHAMPION -> 1.22D;
            case MINIBOSS -> 1.28D;
            case BOSS, WORLD_BOSS -> 1.35D;
        };
    }

    private static double rankArmorFallback(final MobRank rank) {
        return switch (rank) {
            case NORMAL -> 0.0D;
            case VETERAN -> 1.0D;
            case ELITE -> 2.0D;
            case CHAMPION -> 3.0D;
            case MINIBOSS -> 4.0D;
            case BOSS -> 5.0D;
            case WORLD_BOSS -> 6.0D;
        };
    }

    private static double rankMovementFallback(final MobRank rank) {
        return switch (rank) {
            case NORMAL -> 1.0D;
            case VETERAN -> 1.02D;
            case ELITE -> 1.04D;
            case CHAMPION -> 1.05D;
            case MINIBOSS -> 1.04D;
            case BOSS -> 1.02D;
            case WORLD_BOSS -> 1.0D;
        };
    }

    private static NamedTextColor rankColor(final MobRank rank) {
        return rank.bossLike() ? NamedTextColor.DARK_PURPLE
                : rank == MobRank.ELITE ? NamedTextColor.GOLD
                : NamedTextColor.YELLOW;
    }

    private static String rankLabel(final MobRank rank) {
        return switch (rank) {
            case NORMAL -> "Normál";
            case VETERAN -> "Veterán";
            case ELITE -> "Elit";
            case CHAMPION -> "Bajnok";
            case MINIBOSS -> "Miniboss";
            case BOSS -> "Boss";
            case WORLD_BOSS -> "Világboss";
        };
    }

    private static String affixLabel(final EliteAffix affix) {
        return switch (affix) {
            case VOLATILE -> "Kitörő";
            case VAMPIRIC -> "Vérszívó";
            case SHIELDED -> "Pajzsos";
            case FRENZIED -> "Őrjöngő";
            case FROSTBOUND -> "Fagybilincs";
            case ARCANE -> "Rúnás";
            case SUMMONER -> "Idéző";
        };
    }

    private static double clampChance(final double chance) {
        if (!Double.isFinite(chance)) return 0.0D;
        return Math.max(0.0D, Math.min(100.0D, chance));
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
        final double chance = clampChance(configManager.getDouble("rare-variant.chance-percent", 1.5D));
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
