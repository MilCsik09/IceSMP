package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.projection.*;
import hu.taliann.icesmp.dev.weaver.subject.EntityRef;
import hu.taliann.icesmp.pve.*;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Provider-owned effective combat projection. All definitions still come from canonical registries. */
public final class PvEMobProjectionSource implements MobRuntimeProjectionSource {
    public static final String CONSUMER = "pve.combat";
    public static final String ADD = "pve.ability_add", REMOVE = "pve.ability_remove", RANK = "pve.rank_override",
            ARCHETYPE = "pve.archetype_override", TEMPLATE = "pve.template_override", IMPRINT = "pve.combat_imprint";
    private final WeaverProjectionSource projections;
    private final Supplier<Map<String, MobAbilityDefinition>> abilities;
    private final Supplier<Map<String, MobTemplate>> templates;
    private final LongSupplier clock;
    public PvEMobProjectionSource(final WeaverProjectionSource projections, final Supplier<Map<String, MobAbilityDefinition>> abilities,
            final Supplier<Map<String, MobTemplate>> templates, final LongSupplier clock) {
        this.projections = Objects.requireNonNull(projections); this.abilities = Objects.requireNonNull(abilities);
        this.templates = Objects.requireNonNull(templates); this.clock = Objects.requireNonNull(clock);
    }
    public List<WeaverProjection> active(final UUID entityId) { return projections.active(CONSUMER, new EntityRef(entityId), clock.getAsLong()); }
    @Override public EffectiveMobProjection resolve(final UUID entityId, final CanonicalMobProfile canonical) { return resolve(canonical, active(entityId)); }
    public EffectiveMobProjection resolve(final CanonicalMobProfile canonical, final List<WeaverProjection> active) {
        if (active.size() > 32) throw new WeaverDomainRejection("PROJECTION_CAPACITY");
        final List<WeaverProjection> ordered = active.stream().sorted(Comparator.comparingLong(WeaverProjection::sequence)).toList();
        String templateId = canonical.templateId(); MobRank rank = canonical.rank(); Optional<MobArchetype> archetype = canonical.archetype();
        boolean templateOverride = false, archetypeOverride = false;
        for (final var projection : ordered) {
            if (projection.values().containsKey(IMPRINT)) {
                final Map<String, Object> recipe = projection.values().get(IMPRINT).payload();
                rank = MobRank.valueOf(((String) recipe.get("rank")).toUpperCase(Locale.ROOT));
                final String raw = (String) recipe.get("archetype");
                archetype = raw.isEmpty() ? Optional.empty() : Optional.of(MobArchetype.valueOf(raw.toUpperCase(Locale.ROOT)));
                archetypeOverride = true;
                final String capturedTemplate = (String) recipe.get("template");
                templateOverride = !capturedTemplate.isEmpty(); templateId = templateOverride ? capturedTemplate : canonical.templateId();
            }
            if (projection.values().containsKey(TEMPLATE)) { templateId = id(projection.values().get(TEMPLATE)); templateOverride = true; }
            if (projection.values().containsKey(RANK)) rank = MobRank.valueOf(id(projection.values().get(RANK)).toUpperCase(Locale.ROOT));
            if (projection.values().containsKey(ARCHETYPE)) { archetype = Optional.of(MobArchetype.valueOf(id(projection.values().get(ARCHETYPE)).toUpperCase(Locale.ROOT))); archetypeOverride = true; }
        }
        final MobTemplate template = templateOverride ? templates.get().get(templateId) : null;
        if (templateOverride && template == null) throw new WeaverDomainRejection("PROJECTION_CONTENT_UNAVAILABLE");
        if (template != null && !archetypeOverride) archetype = Optional.of(template.archetype());
        final LinkedHashMap<String, Boolean> members = new LinkedHashMap<>();
        List<String> imprintKit = null;
        for (final var projection : ordered) {
            if (projection.values().containsKey(IMPRINT)) {
                imprintKit = ((List<?>) projection.values().get(IMPRINT).payload().get("abilities")).stream().map(String.class::cast).toList();
                members.clear();
            }
            for (final String field : List.of(ADD, REMOVE)) if (projection.values().containsKey(field)) {
                final String ability = id(projection.values().get(field)); members.remove(ability); members.put(ability, field.equals(ADD));
            }
        }
        final List<String> additions = new ArrayList<>(members.keySet()); Collections.reverse(additions);
        final LinkedHashSet<String> kit = new LinkedHashSet<>();
        for (final String ability : additions) if (members.get(ability)) kit.add(ability);
        kit.addAll(imprintKit != null ? imprintKit : template == null ? canonical.rankKits().get(rank) : template.abilityIdsFor(rank));
        members.forEach((ability, present) -> { if (!present) kit.remove(ability); });
        if (kit.size() > 128) throw new WeaverDomainRejection("PROJECTION_KIT_CAPACITY");
        final Map<String, MobAbilityDefinition> definitions = abilities.get();
        for (final String ability : kit) if (!definitions.containsKey(ability)) throw new WeaverDomainRejection("PROJECTION_CONTENT_UNAVAILABLE");
        return new EffectiveMobProjection(templateId, rank, archetype, List.copyOf(kit), template == null ? canonical.behavior() : template.behavior(),
                Set.copyOf(ordered.stream().map(WeaverProjection::projectionId).toList()));
    }
    Map<String, Object> imprintPayload(final EffectiveMobProjection effective) {
        final Map<String, MobAbilityDefinition> definitions = abilities.get();
        final List<String> kit = MobAbilityRuntime.effectiveDefinitions(effective, definitions::get).stream().map(MobAbilityDefinition::abilityId).toList();
        return Map.of("rank", effective.rank().name().toLowerCase(Locale.ROOT), "archetype", effective.archetype().map(value -> value.name().toLowerCase(Locale.ROOT)).orElse(""),
                "template", templates.get().containsKey(effective.templateId()) ? effective.templateId() : "", "abilities", kit);
    }
    public String canonicalRevision(final CanonicalMobProfile canonical) {
        final Map<String, Object> kits = new TreeMap<>(); canonical.rankKits().forEach((rank, kit) -> kits.put(rank.name(), kit));
        final Map<String, Object> definitions = new TreeMap<>(); abilities.get().forEach((id, value) -> definitions.put(id, abilityRevision(value)));
        final Map<String, Object> templateDefinitions = new TreeMap<>(); templates.get().forEach((id, value) -> templateDefinitions.put(id, templateRevision(value)));
        return digest(Map.of("template", canonical.templateId(), "rank", canonical.rank().name(), "archetype", canonical.archetype().map(Enum::name).orElse(""),
                "level", canonical.level(), "kits", kits, "affixes", canonical.affixes().stream().map(Enum::name).sorted().toList(),
                "behavior", canonical.behavior().toString(), "abilities", definitions, "templates", templateDefinitions));
    }
    private static String abilityRevision(final MobAbilityDefinition value) {
        final Map<String, Object> fields = new TreeMap<>();
        fields.put("id", value.abilityId()); fields.put("kind", value.kind().name()); fields.put("cooldown", value.cooldownTicks());
        fields.put("telegraph", value.telegraphTicks()); fields.put("recovery", value.recoveryTicks()); fields.put("radius", value.radius());
        fields.put("power", value.power()); fields.put("summons", value.maxSummons()); fields.put("target", value.targetRule().name()); fields.put("interruptible", value.interruptible());
        fields.put("ranks", value.eligibleRanks().stream().map(Enum::name).sorted().toList()); fields.put("archetypes", value.eligibleArchetypes().stream().map(Enum::name).sorted().toList());
        fields.put("tuning", value.tuning()); fields.put("triggers", value.triggers().stream().map(Enum::name).sorted().toList());
        fields.put("conditions", value.conditions().stream().map(condition -> Map.of("type", condition.type().name(), "value", condition.value())).toList());
        fields.put("actions", value.actions().stream().map(action -> Map.of("type", action.type().name(), "target", action.target().name(), "parameters", action.parameters(), "reference", action.reference())).toList());
        fields.put("presentation", value.presentation().toString()); return digest(fields);
    }
    private static String templateRevision(final MobTemplate value) {
        final Map<String, Object> kits = new TreeMap<>(); value.rankAbilities().forEach((rank, kit) -> kits.put(rank.name(), kit));
        return digest(Map.of("id", value.mobId(), "schema", value.schemaVersion(), "entity", value.entityType(), "rank", value.rank().name(),
                "archetype", value.archetype().name(), "abilities", value.abilityIds(), "rank_abilities", kits, "behavior", value.behavior().toString(), "stats", value.stats().toString()));
    }
    private static String digest(final Map<String, Object> values) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(CanonicalValueBytes.encode(values))); }
        catch (final java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static String id(final WeaverValue value) {
        final Object id = value.payload().get("id");
        if (!(id instanceof String text)) throw new WeaverDomainRejection("PROJECTION_VALUE_INVALID"); return text;
    }
}
