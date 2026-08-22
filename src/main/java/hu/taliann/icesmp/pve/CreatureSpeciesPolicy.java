package hu.taliann.icesmp.pve;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable species-level engagement, technique, social and reward authority. */
public record CreatureSpeciesPolicy(String speciesId, Category category, Disposition disposition,
                                    boolean levelEnabled, boolean rankEnabled,
                                    MobArchetype archetype,
                                    Set<Temperament> allowedTemperaments,
                                    TemperamentPolicy temperamentPolicy,
                                    ProvocationPolicy provocationPolicy,
                                    List<String> techniqueIds,
                                    Map<MobRank, List<String>> rankTechniqueIds,
                                    SocialPolicy socialPolicy,
                                    RewardProfile rewardProfile,
                                    BabyPolicy babyPolicy,
                                    TamePolicy tamePolicy) {
    public enum Category { HOSTILE, NEUTRAL, PASSIVE, CIVILIAN }
    public enum Disposition { PASSIVE, NEUTRAL, HOSTILE, NON_COMBAT }
    public enum Temperament {
        TIMID, CALM, DEFENSIVE, TERRITORIAL, HERD_DEFENSIVE, PACK_DEFENSIVE
    }
    public enum ProvocationPolicy { NONE, DIRECT_PLAYER, VANILLA }
    public enum Reaction { NONE, FLEE, WARN, FIGHT }
    public enum SocialRelation { NONE, SAME_SPECIES, VANILLA }
    public enum RewardProfile { VANILLA_ONLY, HOSTILE, EXPLICIT_AUTHORED }
    public enum BabyPolicy { IDENTITY_ONLY, NON_COMBAT, FULL }
    public enum TamePolicy { NOT_TAMEABLE, OWNER_SAFE, VANILLA }

    public record TemperamentPolicy(Map<Temperament, Integer> weights,
                                    Map<Temperament, Double> fightPercent,
                                    boolean warningBeforeFight,
                                    long combatTicks,
                                    String fleeTechniqueId) {
        public TemperamentPolicy {
            final EnumMap<Temperament, Integer> safeWeights = new EnumMap<>(Temperament.class);
            if (weights != null) safeWeights.putAll(weights);
            int total = 0;
            for (final var entry : safeWeights.entrySet()) {
                if (entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 10_000) {
                    throw new IllegalArgumentException("invalid creature temperament weight");
                }
                total += entry.getValue();
            }
            if (total > 10_000) throw new IllegalArgumentException("creature temperament weights exceed 10000");
            weights = Map.copyOf(safeWeights);

            final EnumMap<Temperament, Double> safeFight = new EnumMap<>(Temperament.class);
            if (fightPercent != null) safeFight.putAll(fightPercent);
            for (final var entry : safeFight.entrySet()) {
                final Double value = entry.getValue();
                if (value == null || !Double.isFinite(value) || value < 0.0D || value > 100.0D) {
                    throw new IllegalArgumentException("invalid creature fight percent");
                }
            }
            fightPercent = Map.copyOf(safeFight);
            if (combatTicks < 40L || combatTicks > 2_400L) {
                throw new IllegalArgumentException("creature combat duration out of bounds");
            }
            fleeTechniqueId = fleeTechniqueId == null || fleeTechniqueId.isBlank() ? ""
                    : MobAbilityDefinition.id(fleeTechniqueId, "flee technique");
        }

        public static TemperamentPolicy none() {
            return new TemperamentPolicy(Map.of(), Map.of(), false, 200L, "");
        }

        public Temperament select(final UUID entityId, final Set<Temperament> allowed) {
            if (entityId == null || allowed == null || allowed.isEmpty()) return null;
            int total = 0;
            for (final Temperament value : allowed) total += weights.getOrDefault(value, 0);
            if (total <= 0) return allowed.iterator().next();
            final int bucket = (int) Math.floor(stableUnit(entityId, 0x4f1bbcdcL) * total);
            int cursor = 0;
            for (final Temperament value : Temperament.values()) {
                if (!allowed.contains(value)) continue;
                cursor += weights.getOrDefault(value, 0);
                if (bucket < cursor) return value;
            }
            return allowed.iterator().next();
        }

        public Reaction reaction(final UUID entityId, final Temperament temperament) {
            if (entityId == null || temperament == null) return Reaction.NONE;
            final double percent = fightPercent.getOrDefault(temperament, 0.0D);
            if (stableUnit(entityId, 0x9e3779b9L) * 100.0D >= percent) return Reaction.FLEE;
            return warningBeforeFight ? Reaction.WARN : Reaction.FIGHT;
        }
    }

    public record SocialPolicy(SocialRelation relation, double radius, int maximumAssistants,
                               Set<Temperament> requiredTemperaments,
                               long cooldownTicks, int maximumCandidates) {
        public SocialPolicy {
            relation = Objects.requireNonNullElse(relation, SocialRelation.NONE);
            if (!Double.isFinite(radius) || radius < 0.0D || radius > 16.0D
                    || maximumAssistants < 0 || maximumAssistants > 6
                    || cooldownTicks < 20L || cooldownTicks > 2_400L
                    || maximumCandidates < 0 || maximumCandidates > 32) {
                throw new IllegalArgumentException("invalid creature social policy bounds");
            }
            requiredTemperaments = requiredTemperaments == null
                    ? Set.of() : Set.copyOf(requiredTemperaments);
            if (relation == SocialRelation.NONE && maximumAssistants != 0) {
                throw new IllegalArgumentException("social NONE cannot admit assistants");
            }
        }

        public static SocialPolicy none() {
            return new SocialPolicy(SocialRelation.NONE, 0.0D, 0, Set.of(), 200L, 0);
        }
    }

    public CreatureSpeciesPolicy {
        speciesId = MobAbilityDefinition.id(speciesId, "creature species id").toUpperCase(java.util.Locale.ROOT);
        category = Objects.requireNonNull(category, "category");
        disposition = Objects.requireNonNull(disposition, "disposition");
        archetype = Objects.requireNonNullElse(archetype, MobArchetype.BRUISER);
        allowedTemperaments = allowedTemperaments == null ? Set.of() : Set.copyOf(allowedTemperaments);
        temperamentPolicy = Objects.requireNonNullElse(temperamentPolicy, TemperamentPolicy.none());
        provocationPolicy = Objects.requireNonNullElse(provocationPolicy, ProvocationPolicy.NONE);
        techniqueIds = ids(techniqueIds);
        final EnumMap<MobRank, List<String>> ranked = new EnumMap<>(MobRank.class);
        if (rankTechniqueIds != null) {
            rankTechniqueIds.forEach((rank, ids) -> ranked.put(
                    Objects.requireNonNull(rank, "rank technique rank"), ids(ids)));
        }
        rankTechniqueIds = Map.copyOf(ranked);
        socialPolicy = Objects.requireNonNullElse(socialPolicy, SocialPolicy.none());
        rewardProfile = Objects.requireNonNullElse(rewardProfile, RewardProfile.VANILLA_ONLY);
        babyPolicy = Objects.requireNonNullElse(babyPolicy, BabyPolicy.IDENTITY_ONLY);
        tamePolicy = Objects.requireNonNullElse(tamePolicy, TamePolicy.NOT_TAMEABLE);

        if (category == Category.HOSTILE && disposition != Disposition.HOSTILE
                || category == Category.NEUTRAL && disposition != Disposition.NEUTRAL
                || category == Category.PASSIVE && disposition != Disposition.PASSIVE
                || category == Category.CIVILIAN && disposition != Disposition.NON_COMBAT) {
            throw new IllegalArgumentException("creature category/disposition mismatch: " + speciesId);
        }
        if (disposition == Disposition.NON_COMBAT
                && (levelEnabled || rankEnabled || !techniqueIds.isEmpty()
                || !rankTechniqueIds.isEmpty() || rewardProfile != RewardProfile.VANILLA_ONLY)) {
            throw new IllegalArgumentException("non-combat creature has combat projection: " + speciesId);
        }
        if (!rankEnabled && !rankTechniqueIds.isEmpty()) {
            throw new IllegalArgumentException("rank-disabled creature has rank techniques: " + speciesId);
        }
        if (disposition != Disposition.NON_COMBAT && (!levelEnabled || !rankEnabled)) {
            throw new IllegalArgumentException("combat creature lacks level/rank projection: " + speciesId);
        }
        if (disposition == Disposition.PASSIVE
                && (provocationPolicy != ProvocationPolicy.DIRECT_PLAYER
                || rewardProfile != RewardProfile.VANILLA_ONLY
                || allowedTemperaments.isEmpty())) {
            throw new IllegalArgumentException("passive creature violates engagement/reward policy: " + speciesId);
        }
        if (!allowedTemperaments.isEmpty()
                && (!temperamentPolicy.weights().keySet().containsAll(allowedTemperaments)
                || temperamentPolicy.weights().values().stream().mapToInt(Integer::intValue).sum() <= 0)) {
            throw new IllegalArgumentException("creature temperament distribution is incomplete: " + speciesId);
        }
        if (!allowedTemperaments.containsAll(temperamentPolicy.weights().keySet())
                || !allowedTemperaments.containsAll(temperamentPolicy.fightPercent().keySet())) {
            throw new IllegalArgumentException("creature temperament policy escapes allowed set: " + speciesId);
        }
    }

    public List<String> techniquesFor(final MobRank rank) {
        final LinkedHashSet<String> result = new LinkedHashSet<>(techniqueIds);
        if (rankEnabled && rank != null) {
            for (final MobRank threshold : MobRank.values()) {
                if (threshold.ordinal() > rank.ordinal()) break;
                result.addAll(rankTechniqueIds.getOrDefault(threshold, List.of()));
            }
        }
        return List.copyOf(result);
    }

    public boolean authoredCombat() {
        return disposition != Disposition.NON_COMBAT
                && (!techniqueIds.isEmpty() || !rankTechniqueIds.isEmpty());
    }

    public boolean authoredRewardEligible() {
        return rewardProfile == RewardProfile.HOSTILE
                || rewardProfile == RewardProfile.EXPLICIT_AUTHORED;
    }

    public static CreatureSpeciesPolicy nonCombatFallback(final String speciesId) {
        return new CreatureSpeciesPolicy(speciesId, Category.CIVILIAN, Disposition.NON_COMBAT,
                false, false, MobArchetype.BRUISER, Set.of(), TemperamentPolicy.none(),
                ProvocationPolicy.NONE, List.of(), Map.of(), SocialPolicy.none(),
                RewardProfile.VANILLA_ONLY, BabyPolicy.NON_COMBAT, TamePolicy.NOT_TAMEABLE);
    }

    private static List<String> ids(final List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        final LinkedHashSet<String> values = new LinkedHashSet<>();
        for (final String value : raw) values.add(MobAbilityDefinition.id(value, "creature technique"));
        if (values.size() != raw.size() || values.size() > 12) {
            throw new IllegalArgumentException("invalid creature technique list");
        }
        return List.copyOf(values);
    }

    private static double stableUnit(final UUID entityId, final long salt) {
        long value = entityId.getMostSignificantBits()
                ^ Long.rotateLeft(entityId.getLeastSignificantBits(), 29) ^ salt;
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}
