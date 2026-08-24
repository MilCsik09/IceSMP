package hu.taliann.icesmp.pve;

import org.bukkit.NamespacedKey;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Startup fail-closed validation for every authored event roster/template reference. */
public final class AuthoredPveContentValidator {
    public static final List<String> WORLD_BOSSES = List.of(
            "ring_warden", "magma_behemoth", "frost_king", "bone_king", "deep_horror",
            "venom_broodmother", "storm_herald", "plague_titan", "golem_sentinel",
            "piglin_warlord");
    public static final List<String> INVASION_CHAMPIONS = List.of(
            "invasion_undead_champion", "invasion_bone_champion",
            "invasion_spider_champion", "invasion_chaos_champion",
            "invasion_nether_champion", "invasion_illager_champion",
            "invasion_witch_champion", "invasion_blazing_champion");
    public static final List<String> PROLOGUE = List.of(
            "prologue_breach_piglin", "prologue_breach_brute", "prologue_breach_hoglin",
            "prologue_breach_blaze", "prologue_breach_skeleton", "prologue_breach_elite",
            "prologue_finale_boss", "prologue_flame_add", "prologue_brute_add",
            "prologue_bone_add");
    public static final List<String> EVENT_ROSTER = List.of(
            "cultist_ritualist", "cultist_blade", "cultist_courier",
            "corruption_scabwalker", "corruption_hollowbow", "corruption_gloomvotary",
            "corruption_blackwing", "road_raider", "tollblade", "coven_wisp",
            "hunt_ancient_ravager", "hunt_bone_hunter", "hunt_elder_mage",
            "hunt_infernal_brute", "dungeon_depth_jailer", "dungeon_ossuary_lord");
    private static final Set<String> DAYLIGHT_SENSITIVE = Set.of(
            "ZOMBIE", "ZOMBIE_VILLAGER", "DROWNED", "SKELETON", "STRAY", "BOGGED", "PHANTOM");

    private AuthoredPveContentValidator() { }

    public static Report validate(final MobTemplateRegistry templates,
                                  final MobAbilityRegistry abilities) {
        final LinkedHashMap<String, String> owners = new LinkedHashMap<>();
        WORLD_BOSSES.forEach(id -> validateTemplate(templates.require(id), MobRank.WORLD_BOSS,
                "boss", "world_boss", owners, "world_boss"));
        INVASION_CHAMPIONS.forEach(id -> validateTemplate(templates.require(id), MobRank.CHAMPION,
                "event", "champion", owners, "invasion"));
        for (final String id : PROLOGUE) {
            final MobTemplate template = templates.require(id);
            if (id.equals("prologue_finale_boss") && !template.rank().bossLike()) {
                throw new IllegalStateException("Prologue finale template is not boss-ranked");
            }
            if (!Set.of("event", "boss").contains(template.spawnPolicy())) {
                throw new IllegalStateException("Prologue template has invalid spawn policy: " + id);
            }
            owners.put(id, "prologue");
        }
        EVENT_ROSTER.forEach(id -> {
            final MobTemplate template = templates.require(id);
            if (!Set.of("event", "boss").contains(template.spawnPolicy())) {
                throw new IllegalStateException("event roster template has invalid spawn policy: " + id);
            }
            owners.put(id, "event_roster");
        });
        int summonReferences = 0;
        int thresholdAbilities = 0;
        final java.util.Set<MobTechniqueAction.Type> usedActions = new java.util.LinkedHashSet<>();
        for (final MobAbilityDefinition definition : abilities.all().values()) {
            usedActions.addAll(definition.actions().stream().map(MobTechniqueAction::type).toList());
            if (definition.triggers().contains(MobAbilityDefinition.Trigger.HEALTH_THRESHOLD)) {
                thresholdAbilities++;
                if (definition.conditions().stream().noneMatch(condition ->
                        condition.type() == MobTechniqueCondition.Type.HEALTH_BELOW)) {
                    throw new IllegalStateException("threshold ability lacks HEALTH_BELOW: "
                            + definition.abilityId());
                }
            }
            for (final MobTechniqueAction action : definition.actions()) {
                if (action.type() == MobTechniqueAction.Type.SUMMON_TEMPLATE) {
                    final MobTemplate add = templates.require(action.reference());
                    if (!Set.of("event", "authored").contains(add.spawnPolicy())) {
                        throw new IllegalStateException("summoned add is not event/authored: " + add.mobId());
                    }
                    summonReferences++;
                } else if (action.type() == MobTechniqueAction.Type.APPLY_EFFECT
                        && org.bukkit.Registry.EFFECT.get(NamespacedKey.minecraft(action.reference())) == null) {
                    throw new IllegalStateException("unknown authored effect: " + action.reference());
                }
            }
        }
        if (!usedActions.containsAll(Set.of(MobTechniqueAction.Type.APPLY_EFFECT,
                MobTechniqueAction.Type.SUMMON_TEMPLATE))) {
            throw new IllegalStateException("authored PvE primitive was introduced without a use case");
        }
        final Set<List<String>> bossKits = new LinkedHashSet<>();
        for (final String id : WORLD_BOSSES) {
            final MobTemplate boss = templates.require(id);
            if (!bossKits.add(boss.abilityIds())) {
                throw new IllegalStateException("duplicate world-boss kit: " + id);
            }
            if (boss.abilityIds().stream().map(abilities::require).noneMatch(definition ->
                    definition.triggers().contains(MobAbilityDefinition.Trigger.HEALTH_THRESHOLD))) {
                throw new IllegalStateException("world boss lacks threshold phase: " + id);
            }
        }
        final LinkedHashMap<String, String> identityOwners = new LinkedHashMap<>();
        int naturalTemplates = 0;
        int daylightProtected = 0;
        final Map<String, Integer> carrierVariants = new java.util.HashMap<>();
        for (final MobTemplate template : templates.all().values()) {
            final String identity = template.entityType() + '|' + template.archetype() + '|'
                    + template.stats() + '|' + template.abilityIdsFor(template.rank()) + '|'
                    + template.behavior() + '|' + template.naturalContext() + '|'
                    + template.sourceTags();
            final String previous = identityOwners.putIfAbsent(identity, template.mobId());
            if (previous != null) {
                throw new IllegalStateException("duplicate authored combat identity: "
                        + previous + '/' + template.mobId());
            }
            if (!Set.of("natural", "natural_or_authored").contains(template.spawnPolicy())) continue;
            naturalTemplates++;
            carrierVariants.merge(template.entityType(), 1, Integer::sum);
            final Set<String> required = template.naturalContext().requiredTags();
            final boolean surfaceDayEligible = !required.contains("time:night")
                    && !required.contains("depth:deep")
                    && !required.contains("dimension:nether")
                    && !required.contains("dimension:the_end");
            if (surfaceDayEligible && DAYLIGHT_SENSITIVE.contains(template.entityType())) {
                if (!template.naturalContext().noDaylightBurn()) {
                    throw new IllegalStateException("daylight-eligible undead lacks protection: "
                            + template.mobId());
                }
                daylightProtected++;
            }
        }
        for (final String carrier : List.of("ZOMBIE", "SKELETON")) {
            if (carrierVariants.getOrDefault(carrier, 0) < 3) {
                throw new IllegalStateException("representative carrier lacks three variants: " + carrier);
            }
        }
        if (carrierVariants.getOrDefault("SPIDER", 0) < 2) {
            throw new IllegalStateException("Spider carrier lacks multiple natural variants");
        }
        return new Report(Map.copyOf(owners), WORLD_BOSSES.size(), INVASION_CHAMPIONS.size(),
                PROLOGUE.size(), EVENT_ROSTER.size(), naturalTemplates, daylightProtected,
                summonReferences, thresholdAbilities, Set.copyOf(usedActions),
                Map.copyOf(carrierVariants));
    }

    private static void validateTemplate(final MobTemplate template, final MobRank rank,
                                         final String spawnPolicy, final String rewardProfile,
                                         final Map<String, String> owners, final String owner) {
        if (template.rank() != rank || !template.spawnPolicy().equals(spawnPolicy)
                || !template.lootProfile().equals(rewardProfile)) {
            throw new IllegalStateException("authored roster policy mismatch: " + template.mobId());
        }
        owners.put(template.mobId(), owner);
    }

    public record Report(Map<String, String> templateOwners, int worldBosses,
                         int invasionChampions, int prologueTemplates,
                         int eventRosterTemplates, int naturalTemplates,
                         int daylightProtectedTemplates,
                         int summonReferences, int thresholdAbilities,
                         Set<MobTechniqueAction.Type> usedActions,
                         Map<String, Integer> carrierVariants) { }
}
