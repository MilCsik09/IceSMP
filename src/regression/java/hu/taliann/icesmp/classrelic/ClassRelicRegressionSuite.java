package hu.taliann.icesmp.classrelic;

import hu.taliann.icesmp.classspec.domain.LoadoutStatus;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Class Relic Framework regressziók: registry fail-fast validáció, aktiválás-invariánsok
 * (OWNER ≠ ACTIVE POSSESSION, Profile v2 kapu, SEALED-szabály), resonance-routing,
 * transfer/reclaim viselkedés, durable Awakening-cooldown policy és az Evoker-migráció
 * forrás-szerződései (nincs relic-hardcode a ResourceBonusService-ben).
 */
public final class ClassRelicRegressionSuite {

    private static final UUID EVOKER_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001201");
    private static final UUID OTHER_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001202");
    private static int assertions;

    private ClassRelicRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        registryValidation();
        activationInvariants();
        resonanceRouting();
        sealedSpecializationInvariant();
        transferAndReclaim();
        awakeningCooldownAuthority();
        evokerMigrationContracts();
        System.out.println("Class relic regression suite passed. assertions=" + assertions);
    }

    // ---------- registry ----------

    private static void registryValidation() {
        check(ClassRelicCatalogLoader.load(evokerConfig(), false).byRelic("sarkany_tojas").isPresent(),
                "known class binding accepted");
        expectReject(config("sarkany_tojas", "not_a_class", Map.of()),
                "unknown class must be rejected");
        expectReject(config("sarkany_tojas", "evoker", Map.of("berserker", "dragon_echo")),
                "spec-parent mismatch must be rejected");
        expectReject(config("sarkany_tojas", "evoker", Map.of("no_such_spec", "dragon_echo")),
                "unknown specialization must be rejected");

        final Map<String, Object> duplicateClass = evokerConfig();
        duplicateClass.put("masik_relic", relicNode("evoker", Map.of()));
        boolean rejected = false;
        try {
            ClassRelicCatalogLoader.load(duplicateClass, false);
        } catch (final IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "duplicate class binding must be rejected");

        // A teljesség-kapu: fejlesztési állapotban a részleges katalógus él, true-nál bukik.
        check(ClassRelicCatalogLoader.load(evokerConfig(), false).size() == 1,
                "partial catalog allowed while require-complete-catalog=false");
        rejected = false;
        try {
            ClassRelicCatalogLoader.load(evokerConfig(), true);
        } catch (final IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "require-complete-catalog=true rejects the partial (pilot-only) roster");
    }

    // ---------- activation ----------

    private static void activationInvariants() {
        final ClassRelicCatalog catalog = ClassRelicCatalogLoader.load(evokerConfig(), false);
        final World world = new World();
        world.owner = EVOKER_PLAYER;
        world.possession.add(EVOKER_PLAYER);
        world.facts.put(EVOKER_PLAYER, facts("evoker", "devastation", LoadoutStatus.ACTIVE));

        ClassRelicActivation activation = world.resolver().resolve(catalog, EVOKER_PLAYER, "sarkany_tojas");
        check(activation.basePowerActive(), "owner + possession + matching class → base power ACTIVE");

        world.facts.put(EVOKER_PLAYER, facts("wizard", "elementalist", LoadoutStatus.ACTIVE));
        activation = world.resolver().resolve(catalog, EVOKER_PLAYER, "sarkany_tojas");
        check(!activation.basePowerActive()
                        && activation.dormantReason() == ClassRelicActivation.DormantReason.WRONG_CLASS,
                "wrong class → DORMANT");

        world.facts.put(EVOKER_PLAYER, facts("evoker", "devastation", LoadoutStatus.ACTIVE));
        world.possession.clear();
        activation = world.resolver().resolve(catalog, EVOKER_PLAYER, "sarkany_tojas");
        check(!activation.basePowerActive()
                        && activation.dormantReason() == ClassRelicActivation.DormantReason.NO_PHYSICAL_POSSESSION,
                "ownership without usable physical relic → DORMANT (OWNER != POSSESSION)");

        world.possession.add(EVOKER_PLAYER);
        world.facts.remove(EVOKER_PLAYER);
        activation = world.resolver().resolve(catalog, EVOKER_PLAYER, "sarkany_tojas");
        check(!activation.basePowerActive()
                        && activation.dormantReason() == ClassRelicActivation.DormantReason.PROFILE_NOT_USABLE,
                "blocked/review/quarantined profile view is empty → DORMANT");

        world.facts.put(EVOKER_PLAYER, facts("evoker", "devastation", LoadoutStatus.ACTIVE));
        check(world.resolver().resolve(catalog, EVOKER_PLAYER, "ismeretlen_relic").dormantReason()
                        == ClassRelicActivation.DormantReason.NO_BINDING,
                "unbound relic id → NO_BINDING");
    }

    // ---------- resonance routing ----------

    private static void resonanceRouting() {
        final ClassRelicCatalog catalog = ClassRelicCatalogLoader.load(evokerConfig(), false);
        final World world = new World();
        world.owner = EVOKER_PLAYER;
        world.possession.add(EVOKER_PLAYER);

        world.facts.put(EVOKER_PLAYER, facts("evoker", "devastation", LoadoutStatus.ACTIVE));
        ClassRelicActivation devastation = world.resolver().resolve(catalog, EVOKER_PLAYER, "sarkany_tojas");
        check(devastation.resolvedResonanceId().equals(Optional.of("dragon_echo")),
                "DEVASTATION routes to dragon_echo");

        world.facts.put(EVOKER_PLAYER, facts("evoker", "preservation", LoadoutStatus.ACTIVE));
        ClassRelicActivation preservation = world.resolver().resolve(catalog, EVOKER_PLAYER, "sarkany_tojas");
        check(preservation.resolvedResonanceId().equals(Optional.of("temporal_echo")),
                "PRESERVATION routes to temporal_echo");
        check(!devastation.resolvedResonanceId().equals(preservation.resolvedResonanceId()),
                "the two specs resolve DIFFERENT resonance bindings");
        check(!devastation.resonanceActive() && !preservation.resonanceActive(),
                "disabled resonance routes but stays inert");

        world.facts.put(EVOKER_PLAYER, facts("evoker", "", LoadoutStatus.EMPTY));
        check(world.resolver().resolve(catalog, EVOKER_PLAYER, "sarkany_tojas")
                        .resolvedResonanceId().isEmpty(),
                "no active spec → no resonance routing");
    }

    // ---------- SEALED invariáns (DARK spec) ----------

    private static void sealedSpecializationInvariant() {
        // Generikus resolver-invariáns: jövőbeli Wizard-relic + SEALED necromancer.
        final ClassRelicCatalog catalog = ClassRelicCatalogLoader.load(
                config("szettort_eg_prizmaja", "wizard",
                        Map.of("necromancer", "lich_echo", "elementalist", "storm_echo")), false);
        final World world = new World();
        world.owner = EVOKER_PLAYER;
        world.possession.add(EVOKER_PLAYER);
        world.facts.put(EVOKER_PLAYER, facts("wizard", "necromancer", LoadoutStatus.SEALED));

        final ClassRelicActivation activation = world.resolver()
                .resolve(catalog, EVOKER_PLAYER, "szettort_eg_prizmaja");
        check(activation.basePowerActive(), "class power survives a SEALED specialization");
        check(activation.resolvedResonanceId().isEmpty() && !activation.resonanceActive(),
                "SEALED specialization must NOT resonate");
    }

    // ---------- transfer és reclaim ----------

    private static void transferAndReclaim() {
        final ClassRelicCatalog catalog = ClassRelicCatalogLoader.load(evokerConfig(), false);
        final World world = new World();
        world.owner = EVOKER_PLAYER;
        world.possession.add(EVOKER_PLAYER);
        world.facts.put(EVOKER_PLAYER, facts("evoker", "devastation", LoadoutStatus.ACTIVE));
        world.facts.put(OTHER_PLAYER, facts("evoker", "preservation", LoadoutStatus.ACTIVE));

        check(world.resolver().resolve(catalog, EVOKER_PLAYER, "sarkany_tojas").basePowerActive(),
                "pre-transfer: old owner active");

        world.owner = OTHER_PLAYER;
        world.possession.clear();
        world.possession.add(OTHER_PLAYER);
        check(world.resolver().resolve(catalog, OTHER_PLAYER, "sarkany_tojas").basePowerActive(),
                "post-transfer: new matching-class owner activates");
        check(world.resolver().resolve(catalog, EVOKER_PLAYER, "sarkany_tojas").dormantReason()
                        == ClassRelicActivation.DormantReason.NOT_OWNER,
                "post-transfer: old owner immediately dormant");

        world.lost = true;
        check(world.resolver().resolve(catalog, OTHER_PLAYER, "sarkany_tojas").dormantReason()
                        == ClassRelicActivation.DormantReason.RELIC_LOST,
                "lost/reclaim state → no gameplay power");
        world.lost = false;
        check(world.resolver().resolve(catalog, OTHER_PLAYER, "sarkany_tojas").basePowerActive(),
                "after re-summon the relic activates again");
    }

    // ---------- Awakening durable cooldown ----------

    private static void awakeningCooldownAuthority() {
        final long armedAt = 1_000_000L;
        final long readyAt = AwakeningCooldownPolicy.nextReadyAt(armedAt, 120L);
        check(readyAt == armedAt + 120_000L, "cooldown arithmetic is absolute-timestamp based");
        check(!AwakeningCooldownPolicy.ready(armedAt + 119_999L, readyAt),
                "cooldown holds until ready-at");
        check(AwakeningCooldownPolicy.ready(readyAt, readyAt), "ready exactly at ready-at");
        check(AwakeningCooldownPolicy.remainingMillis(armedAt, readyAt) == 120_000L,
                "remaining reported from the relic-side timestamp");
        // A ready-at a RELIC-kel utazik: a policy tulajdonos-független, transfer nem nullázhat,
        // és a perzisztencia forrás-szerződése a RelicManager saját szekciója.
    }

    // ---------- Evoker migráció ----------

    private static void evokerMigrationContracts() throws Exception {
        final String bonus = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/hu/taliann/icesmp/managers/ResourceBonusService.java"));
        check(!bonus.contains("sarkany_tojas") && !bonus.contains("dragon-relic-id"),
                "no relic-id hardcode remains in ResourceBonusService");
        check(!bonus.contains("isEvoker") && !bonus.contains("JobType.EVOKER"),
                "no legacy class check remains in ResourceBonusService");
        check(!bonus.contains("RelicManager"),
                "ResourceBonusService no longer reads relic ownership directly");
        check(bonus.contains("RelicModifier.CLASS_RESOURCE_MAX"),
                "resource bonus flows through the generic Class Relic modifier API");

        // A modifier-matematika a pilot értékével: 10% → 1.10 szorzó, más kasztnak 1.0.
        final ClassRelicCatalog catalog = ClassRelicCatalogLoader.load(evokerConfig(), false);
        final ClassRelicBinding binding = catalog.byRelic("sarkany_tojas").orElseThrow();
        check(binding.basePower().percentByModifier()
                        .get(RelicModifier.CLASS_RESOURCE_MAX) == 10.0D,
                "pilot keeps the existing configurable 10 percent");

        final String relicManager = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/hu/taliann/icesmp/managers/RelicManager.java"));
        check(relicManager.contains("awakening.\" + entry.getKey() + \".ready-at")
                        && relicManager.contains("getAwakeningReadyAt"),
                "awakening ready-at persists in the world-level relic store");
        check(!relicManager.contains("awakeningReadyAt.remove"),
                "transfer/reclaim paths never clear the travelling awakening cooldown");

        final String service = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/hu/taliann/icesmp/classrelic/ClassRelicService.java"));
        check(service.contains("isOwnedByCurrentRegion"),
                "inventory possession scan is region-thread guarded (Folia)");
        check(service.contains("previous catalog stays"),
                "rejected reload keeps the previous catalog snapshot");
    }

    // ---------- fixtures ----------

    /** Egyszerű világ-állapot fake: ownership + lost + possession + profil-tények. */
    private static final class World {
        UUID owner;
        boolean lost;
        final Set<UUID> possession = new java.util.HashSet<>();
        final Map<UUID, ClassRelicActivationResolver.ProfileFacts> facts = new HashMap<>();

        ClassRelicActivationResolver resolver() {
            return new ClassRelicActivationResolver(
                    new ClassRelicActivationResolver.OwnershipView() {
                        @Override
                        public Optional<UUID> ownerOf(final String relicId) {
                            return Optional.ofNullable(owner);
                        }

                        @Override
                        public boolean isLost(final String relicId) {
                            return lost;
                        }
                    },
                    (playerId, relicId) -> possession.contains(playerId),
                    playerId -> Optional.ofNullable(facts.get(playerId)));
        }
    }

    private static ClassRelicActivationResolver.ProfileFacts facts(
            final String classId, final String specId, final LoadoutStatus status) {
        return new ClassRelicActivationResolver.ProfileFacts(classId, specId, status);
    }

    private static Map<String, Object> evokerConfig() {
        return config("sarkany_tojas", "evoker",
                Map.of("devastation", "dragon_echo", "preservation", "temporal_echo"));
    }

    private static Map<String, Object> config(final String relicId, final String classId,
                                              final Map<String, String> resonanceIds) {
        final LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put(relicId, relicNode(classId, resonanceIds));
        return root;
    }

    private static Map<String, Object> relicNode(final String classId,
                                                 final Map<String, String> resonanceIds) {
        final LinkedHashMap<String, Object> node = new LinkedHashMap<>();
        node.put("class", classId);
        node.put("activation", Map.of("requires-physical-possession", Boolean.TRUE));
        node.put("base-power", Map.of("id", "dragon_essence", "max-resource-percent", 10.0D));
        final LinkedHashMap<String, Object> resonances = new LinkedHashMap<>();
        resonanceIds.forEach((spec, id) ->
                resonances.put(spec, Map.of("id", id, "enabled", Boolean.FALSE)));
        node.put("resonances", resonances);
        node.put("awakening", Map.of("id", "unborn_dragon", "enabled", Boolean.FALSE,
                "cooldown-seconds", 120L));
        return node;
    }

    private static void expectReject(final Map<String, Object> config, final String label) {
        boolean rejected = false;
        try {
            ClassRelicCatalogLoader.load(config, false);
        } catch (final IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, label);
    }

    private static void check(final boolean condition, final String label) {
        assertions++;
        if (!condition) {
            throw new AssertionError("Class relic regression failed: " + label);
        }
    }
}
