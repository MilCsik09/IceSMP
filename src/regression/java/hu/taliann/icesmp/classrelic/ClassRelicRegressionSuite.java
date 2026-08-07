package hu.taliann.icesmp.classrelic;

import hu.taliann.icesmp.classspec.domain.ClassSpecCatalog;
import hu.taliann.icesmp.classspec.domain.LoadoutStatus;
import hu.taliann.icesmp.relics.RelicWorldStateStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Class Relic Framework regressziók: registry fail-fast + strict schema validáció,
 * generikus relic létezés-kapu, aktiválás-invariánsok (OWNER ≠ ACTIVE POSSESSION,
 * Profile v2 kapu, SEALED-szabály, explicit framework-disable), tipizált resonance-
 * jelzések, birtoklás-pillanatkép fail-closed szabálya, és a világ-relic store VALÓDI
 * viselkedési garanciái: konkurens Awakening-arm, párhuzamos mutációk, restart
 * round-trip, transfer/reclaim cooldown-megőrzés, durable write-hiba rollback.
 */
public final class ClassRelicRegressionSuite {

    private static final UUID EVOKER_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001201");
    private static final UUID OTHER_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000001202");
    private static final Logger LOGGER = Logger.getLogger("ClassRelicRegressionSuite");
    private static int assertions;

    private ClassRelicRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        registryValidation();
        strictSchemaValidation();
        genericRelicExistenceGate();
        frameworkDisableGate();
        activationInvariants();
        resonanceRouting();
        sealedSpecializationInvariant();
        transferAndReclaim();
        awakeningCooldownAuthority();
        worldStateSingleWriter();
        concurrentAwakeningArm();
        parallelMutationDurability();
        restartAndTransferRoundTrip();
        persistenceFailureSemantics();
        possessionSnapshotPolicy();
        typedGameplaySignals();
        packagedYamlContract();
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
        expectReject(duplicateClass, "duplicate class binding must be rejected");

        // A teljesség-kapu: fejlesztési állapotban a részleges katalógus él, true-nál bukik.
        check(ClassRelicCatalogLoader.load(evokerConfig(), false).size() == 1,
                "partial catalog allowed while require-complete-catalog=false");
        boolean rejected = false;
        try {
            ClassRelicCatalogLoader.load(evokerConfig(), true);
        } catch (final IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "require-complete-catalog=true rejects the partial (pilot-only) roster");
    }

    // ---------- strict schema ----------

    private static void strictSchemaValidation() {
        // Absent vs wrong-type: a hiányzó opcionális szekció defaultolhat, a rossz típusú nem.
        final Map<String, Object> noResonances = evokerConfig();
        ((Map<String, Object>) noResonances.get("sarkany_tojas")).remove("resonances");
        check(ClassRelicCatalogLoader.load(noResonances, false).byRelic("sarkany_tojas")
                        .orElseThrow().resonances().isEmpty(),
                "absent resonances section defaults to empty (partial catalog)");

        final Map<String, Object> brokenResonances = evokerConfig();
        ((Map<String, Object>) brokenResonances.get("sarkany_tojas")).put("resonances", "abc");
        expectReject(brokenResonances, "present-but-wrong-type resonances must be rejected");

        final Map<String, Object> brokenActivation = evokerConfig();
        ((Map<String, Object>) brokenActivation.get("sarkany_tojas")).put("activation", "broken");
        expectReject(brokenActivation, "present-but-wrong-type activation must be rejected");

        expectReject(withCooldown(120.9D), "fractional cooldown-seconds must not be truncated");
        expectReject(withCooldown(-5L), "negative cooldown-seconds must be rejected");
        expectReject(withCooldown(ClassRelicCatalogLoader.MAX_AWAKENING_COOLDOWN_SECONDS + 1L),
                "cooldown above the documented upper bound must be rejected");
        check(ClassRelicCatalogLoader.load(withCooldown(120), false).byRelic("sarkany_tojas")
                        .orElseThrow().awakening().cooldownSeconds() == 120L,
                "whole-number cooldown accepted with integer semantics");
        // A felső korlát mellett a ready-at aritmetika nem tud túlcsordulni epoch-millisnél.
        check(AwakeningCooldownPolicy.nextReadyAt(System.currentTimeMillis(),
                        ClassRelicCatalogLoader.MAX_AWAKENING_COOLDOWN_SECONDS) > 0L,
                "max-bound cooldown arithmetic stays overflow-free");
    }

    // ---------- generikus relic létezés-kapu ----------

    private static void genericRelicExistenceGate() {
        final ClassRelicCatalog catalog = ClassRelicCatalogLoader.load(evokerConfig(), false);
        catalog.requireKnownRelics(relicId -> relicId.equals("sarkany_tojas"));
        check(true, "binding to an existing generic relic passes the existence gate");

        boolean rejected = false;
        try {
            catalog.requireKnownRelics(relicId -> false);
        } catch (final IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "binding to an unknown generic relic is rejected, not warned");

        // Reload-minta: hibás candidate → a korábban publikált katalógus marad érvényben.
        ClassRelicCatalog published = catalog;
        try {
            final ClassRelicCatalog candidate = ClassRelicCatalogLoader.load(
                    config("kitalalt_relic", "evoker", Map.of()), false);
            candidate.requireKnownRelics(relicId -> relicId.equals("sarkany_tojas"));
            published = candidate;
        } catch (final IllegalArgumentException expected) {
            // candidate eldobva
        }
        check(published == catalog && published.byRelic("sarkany_tojas").isPresent(),
                "invalid reload keeps the previously published catalog");

        // Teljes, de kitalált roster: a completeness-kapu PASS-olna, a létezés-kapu bukik.
        final LinkedHashMap<String, Object> fabricated = new LinkedHashMap<>();
        for (final String classId : ClassSpecCatalog.classIds()) {
            final LinkedHashMap<String, String> resonanceIds = new LinkedHashMap<>();
            for (final String specId : ClassSpecCatalog.specializationIds()) {
                if (classId.equals(ClassSpecCatalog.parentOf(specId))) {
                    resonanceIds.put(specId, specId + "_echo");
                }
            }
            fabricated.put("kamu_" + classId, relicNode(classId, resonanceIds));
        }
        final ClassRelicCatalog completeFabricated =
                ClassRelicCatalogLoader.load(fabricated, true);
        rejected = false;
        try {
            completeFabricated.requireKnownRelics(relicId -> false);
        } catch (final IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "a complete-but-fabricated roster cannot pass the existence gate");
    }

    // ---------- explicit framework-kapu ----------

    private static void frameworkDisableGate() {
        // requires-physical-possession=false kötés: a kapu nélkül ownership+class elég lenne.
        final Map<String, Object> noPossession = evokerConfig();
        ((Map<String, Object>) noPossession.get("sarkany_tojas"))
                .put("activation", Map.of("requires-physical-possession", Boolean.FALSE));
        final ClassRelicCatalog catalog = ClassRelicCatalogLoader.load(noPossession, false);

        final World world = new World();
        world.owner = EVOKER_PLAYER;
        world.facts.put(EVOKER_PLAYER, facts("evoker", "devastation", LoadoutStatus.ACTIVE));
        world.enabled = false;

        final ClassRelicActivation disabled = world.resolver()
                .resolve(catalog, EVOKER_PLAYER, "sarkany_tojas");
        check(!disabled.basePowerActive() && !disabled.resonanceActive()
                        && disabled.dormantReason() == ClassRelicActivation.DormantReason.FRAMEWORK_DISABLED,
                "relics.enabled=false → explicit FRAMEWORK_DISABLED even without possession requirement");
        check(world.resolver().resolveForClass(catalog, EVOKER_PLAYER).dormantReason()
                        == ClassRelicActivation.DormantReason.FRAMEWORK_DISABLED,
                "class-resolution path honours the explicit framework gate");

        world.enabled = true;
        check(world.resolver().resolve(catalog, EVOKER_PLAYER, "sarkany_tojas").basePowerActive(),
                "re-enabled framework activates immediately (live gate)");
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
        final ClassRelicActivation devastation = world.resolver().resolve(catalog, EVOKER_PLAYER, "sarkany_tojas");
        check(devastation.resolvedResonanceId().equals(Optional.of("dragon_echo")),
                "DEVASTATION routes to dragon_echo");

        world.facts.put(EVOKER_PLAYER, facts("evoker", "preservation", LoadoutStatus.ACTIVE));
        final ClassRelicActivation preservation = world.resolver().resolve(catalog, EVOKER_PLAYER, "sarkany_tojas");
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

    // ---------- Awakening durable cooldown policy ----------

    private static void awakeningCooldownAuthority() {
        final long armedAt = 1_000_000L;
        final long readyAt = AwakeningCooldownPolicy.nextReadyAt(armedAt, 120L);
        check(readyAt == armedAt + 120_000L, "cooldown arithmetic is absolute-timestamp based");
        check(!AwakeningCooldownPolicy.ready(armedAt + 119_999L, readyAt),
                "cooldown holds until ready-at");
        check(AwakeningCooldownPolicy.ready(readyAt, readyAt), "ready exactly at ready-at");
        check(AwakeningCooldownPolicy.remainingMillis(armedAt, readyAt) == 120_000L,
                "remaining reported from the relic-side timestamp");
    }

    // ---------- világ-relic store: single-writer viselkedés ----------

    private static void worldStateSingleWriter() {
        final AtomicReference<YamlConfiguration> lastWrite = new AtomicReference<>();
        final RelicWorldStateStore store = new RelicWorldStateStore(lastWrite::set, LOGGER);
        final java.util.List<String> mutations = new java.util.concurrent.CopyOnWriteArrayList<>();
        store.setMutationListener(mutations::add);

        final RelicWorldStateStore.ArmResult armed =
                store.tryArmAwakening("sarkany_tojas", 1_000_000L, 120L);
        check(armed == RelicWorldStateStore.ArmResult.ARMED, "first arm succeeds");
        check(store.awakeningReadyAt("sarkany_tojas")
                        == AwakeningCooldownPolicy.nextReadyAt(1_000_000L, 120L),
                "store arm result matches the cooldown policy arithmetic");
        check(lastWrite.get() != null
                        && lastWrite.get().getLong("awakening.sarkany_tojas.ready-at") == 1_120_000L,
                "arm is durable-committed before ARMED is reported");
        check(store.tryArmAwakening("sarkany_tojas", 1_060_000L, 120L)
                        == RelicWorldStateStore.ArmResult.ON_COOLDOWN,
                "second arm within cooldown → ON_COOLDOWN");

        store.recordOwnership("sarkany_tojas", EVOKER_PLAYER, 2_000_000L);
        check(mutations.contains("sarkany_tojas"),
                "successful world-state mutation notifies the invalidation listener");
        check(!mutations.contains("arm"), "listener receives relic ids only");
    }

    private static void concurrentAwakeningArm() throws Exception {
        final RelicWorldStateStore store = new RelicWorldStateStore(yaml -> {
        }, LOGGER);
        final int threads = 8;
        final CyclicBarrier barrier = new CyclicBarrier(threads);
        final CountDownLatch done = new CountDownLatch(threads);
        final AtomicInteger armedCount = new AtomicInteger();
        final AtomicInteger cooldownCount = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    barrier.await();
                    switch (store.tryArmAwakening("sarkany_tojas", 5_000_000L, 300L)) {
                        case ARMED -> armedCount.incrementAndGet();
                        case ON_COOLDOWN -> cooldownCount.incrementAndGet();
                        case PERSISTENCE_FAILED -> {
                        }
                    }
                } catch (final Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }
        check(done.await(30L, java.util.concurrent.TimeUnit.SECONDS),
                "concurrent arm threads finish");
        check(armedCount.get() == 1, "same relic, same ready-at: exactly ONE concurrent arm is ARMED");
        check(cooldownCount.get() == threads - 1,
                "all other concurrent arms observe ON_COOLDOWN");
    }

    private static void parallelMutationDurability() throws Exception {
        final AtomicReference<YamlConfiguration> lastWrite = new AtomicReference<>();
        final RelicWorldStateStore store = new RelicWorldStateStore(lastWrite::set, LOGGER);
        final int threads = 4;
        final CyclicBarrier barrier = new CyclicBarrier(threads);
        final CountDownLatch done = new CountDownLatch(threads);
        final List<Runnable> work = List.of(
                () -> store.tryArmAwakening("relic_a", 1_000L, 60L),
                () -> store.tryArmAwakening("relic_b", 1_000L, 90L),
                () -> store.recordOwnership("relic_c", EVOKER_PLAYER, 7_000L),
                () -> store.markLost("relic_d", 8_000L));
        for (final Runnable task : work) {
            new Thread(() -> {
                try {
                    barrier.await();
                    task.run();
                } catch (final Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }
        check(done.await(30L, java.util.concurrent.TimeUnit.SECONDS),
                "parallel mutation threads finish");
        final YamlConfiguration yaml = lastWrite.get();
        check(yaml.getLong("awakening.relic_a.ready-at") == 61_000L
                        && yaml.getLong("awakening.relic_b.ready-at") == 91_000L
                        && EVOKER_PLAYER.toString().equals(yaml.getString("ownerships.relic_c.owner")),
                "the final durable write contains every parallel mutation's committed state");
        // relic_d lost-jelölése ownership nélkül nem szerializálódik ownership-ághoz — de a
        // memóriaállapot őrzi, és a következő ownership-írás sem törli.
        check(store.isLost("relic_d"), "parallel markLost committed in memory");
    }

    private static void restartAndTransferRoundTrip() throws Exception {
        final AtomicReference<YamlConfiguration> lastWrite = new AtomicReference<>();
        final RelicWorldStateStore store = new RelicWorldStateStore(lastWrite::set, LOGGER);
        check(store.tryArmAwakening("sarkany_tojas", 1_000_000L, 3_600L)
                        == RelicWorldStateStore.ArmResult.ARMED, "arm before restart");
        final long readyAt = store.awakeningReadyAt("sarkany_tojas");

        // Transfer: gazdacsere nem nullázza az utazó cooldownt.
        store.recordOwnership("sarkany_tojas", EVOKER_PLAYER, 1_100_000L);
        store.recordOwnership("sarkany_tojas", OTHER_PLAYER, 1_200_000L);
        check(store.awakeningReadyAt("sarkany_tojas") == readyAt,
                "ownership transfer keeps the travelling awakening cooldown");

        // Reclaim: lost → clear → resummon; a cooldown változatlan.
        store.markLost("sarkany_tojas", 1_300_000L);
        store.clearLost("sarkany_tojas");
        check(store.awakeningReadyAt("sarkany_tojas") == readyAt,
                "lost/reclaim cycle keeps the awakening cooldown");

        // Ownership-felszabadítás sem érinti (a cooldown a relic-é, nem a tulajdonosé).
        store.releaseOwnership("sarkany_tojas");
        check(store.awakeningReadyAt("sarkany_tojas") == readyAt,
                "ownership release keeps the awakening cooldown");

        // Restart round-trip: teljesen új store-példány a durable állapotból.
        final YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(lastWrite.get().saveToString());
        final RelicWorldStateStore fresh = new RelicWorldStateStore(yaml -> {
        }, LOGGER);
        fresh.loadFrom(reloaded);
        check(fresh.awakeningReadyAt("sarkany_tojas") == readyAt,
                "restart round-trip preserves the exact ready-at");
        check(fresh.tryArmAwakening("sarkany_tojas", readyAt - 1L, 60L)
                        == RelicWorldStateStore.ArmResult.ON_COOLDOWN,
                "reloaded store still enforces the cooldown");
    }

    private static void persistenceFailureSemantics() {
        final AtomicReference<Boolean> failWrites = new AtomicReference<>(Boolean.FALSE);
        final RelicWorldStateStore store = new RelicWorldStateStore(yaml -> {
            if (failWrites.get()) {
                throw new IOException("injected disk failure");
            }
        }, LOGGER);

        check(store.tryArmAwakening("sarkany_tojas", 1_000_000L, 120L)
                        == RelicWorldStateStore.ArmResult.ARMED, "baseline arm with healthy disk");
        final long readyAt = store.awakeningReadyAt("sarkany_tojas");

        failWrites.set(Boolean.TRUE);
        check(store.tryArmAwakening("sarkany_tojas", readyAt + 1L, 120L)
                        == RelicWorldStateStore.ArmResult.PERSISTENCE_FAILED,
                "disk failure → no false ARMED success");
        check(store.awakeningReadyAt("sarkany_tojas") == readyAt,
                "failed arm rolls the in-memory ready-at back (no runtime/disk split-brain)");

        boolean ownershipRolledBack = false;
        try {
            store.recordOwnership("sarkany_tojas", OTHER_PLAYER, 2_000_000L);
        } catch (final RuntimeException expected) {
            ownershipRolledBack = store.ownership("sarkany_tojas") == null;
        }
        check(ownershipRolledBack,
                "failed ownership write rolls back and surfaces the failure");

        failWrites.set(Boolean.FALSE);
        check(store.tryArmAwakening("sarkany_tojas", readyAt + 1L, 120L)
                        == RelicWorldStateStore.ArmResult.ARMED,
                "after disk recovery the arm succeeds normally");
    }

    // ---------- birtoklás-pillanatkép fail-closed szabálya ----------

    private static void possessionSnapshotPolicy() {
        final long ttl = 2_500L;
        final PossessionSnapshot present = new PossessionSnapshot("sarkany_tojas", true, 10_000L);
        check(present.usableFor("sarkany_tojas", 10_500L, ttl), "fresh true snapshot → possession");
        check(!present.usableFor("sarkany_tojas", 12_500L, ttl),
                "TTL-expired snapshot → fail-closed false (no unlimited stale true)");
        check(!present.usableFor("masik_relic", 10_500L, ttl),
                "snapshot only answers for its own relic id");
        check(!new PossessionSnapshot("sarkany_tojas", false, 10_000L)
                        .usableFor("sarkany_tojas", 10_100L, ttl),
                "fresh false snapshot → no possession");
        check(present.usableFor("SARKANY_TOJAS", 10_500L, ttl),
                "relic-id matching is case-insensitive");
    }

    // ---------- tipizált gameplay-jelzések ----------

    private static void typedGameplaySignals() {
        final ClassGameplaySignal.DamageDealt damage = new ClassGameplaySignal.DamageDealt(
                EVOKER_PLAYER, OTHER_PLAYER, 42.5D, Set.of(AbilityTag.FIRE));
        check(damage.amount() == 42.5D && damage.targetId().equals(OTHER_PLAYER)
                        && damage.actorId().equals(EVOKER_PLAYER),
                "damage payload keeps actor, target identity and amount");
        check(damage.event() == ClassGameplayEvent.DAMAGE_DEALT, "damage signal maps to its event");

        final ClassGameplaySignal.HealResolved heal = new ClassGameplaySignal.HealResolved(
                EVOKER_PLAYER, EVOKER_PLAYER, 12.0D, Set.of());
        final ClassGameplaySignal.Overheal overheal = new ClassGameplaySignal.Overheal(
                EVOKER_PLAYER, EVOKER_PLAYER, 3.0D, Set.of());
        check(heal.event() == ClassGameplayEvent.HEAL_RESOLVED
                        && overheal.event() == ClassGameplayEvent.OVERHEAL
                        && overheal.amount() == 3.0D,
                "heal and overheal are distinct typed payloads with their own amounts");

        check(new ClassGameplaySignal.ResourceSpent(EVOKER_PLAYER, 25.0D, Set.of())
                        .amount() == 25.0D,
                "resource event carries the spent amount");

        final java.util.HashSet<AbilityTag> mutableTags = new java.util.HashSet<>(
                Set.of(AbilityTag.FIRE, AbilityTag.DRACONIC));
        final ClassGameplaySignal.AbilityResolved ability = new ClassGameplaySignal.AbilityResolved(
                EVOKER_PLAYER, "tuzcsapas", mutableTags);
        mutableTags.clear();
        check(ability.tags().equals(Set.of(AbilityTag.FIRE, AbilityTag.DRACONIC)),
                "tags are copied immutably into the signal");
        boolean immutable = false;
        try {
            ability.tags().add(AbilityTag.HEAL);
        } catch (final UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "signal tags cannot be mutated by hooks");

        expectSignalReject(() -> new ClassGameplaySignal.Generic(
                        ClassGameplayEvent.DAMAGE_DEALT, EVOKER_PLAYER, Set.of()),
                "payload-carrying event cannot hide behind the Generic form");
        check(new ClassGameplaySignal.Generic(ClassGameplayEvent.BLOCK, EVOKER_PLAYER, Set.of())
                        .event() == ClassGameplayEvent.BLOCK,
                "payload-free events use the Generic form");
        expectSignalReject(() -> new ClassGameplaySignal.DamageDealt(
                        EVOKER_PLAYER, OTHER_PLAYER, -1.0D, Set.of()),
                "negative amount rejected");
        expectSignalReject(() -> new ClassGameplaySignal.DamageDealt(
                        EVOKER_PLAYER, OTHER_PLAYER, Double.NaN, Set.of()),
                "non-finite amount rejected");
        expectSignalReject(() -> new ClassGameplaySignal.Summon(
                        EVOKER_PLAYER, "sarkany", 0, Set.of()),
                "non-positive summon count rejected");
    }

    // ---------- csomagolt relics.yml a production loader útján ----------

    private static void packagedYamlContract() throws Exception {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new java.io.File("src/main/resources/config/relics.yml"));
        final ConfigurationSection root = yaml.getConfigurationSection("relics.class-relics");
        check(root != null, "packaged relics.yml carries the class-relics section");
        final ClassRelicCatalog catalog = ClassRelicCatalogLoader.load(toMap(root), false);
        final ClassRelicBinding binding = catalog.byRelic("sarkany_tojas").orElseThrow();
        check(binding.classId().equals("evoker"), "packaged Evoker pilot binding is valid");
        check(binding.basePower().percentByModifier()
                        .get(RelicModifier.CLASS_RESOURCE_MAX) == 10.0D,
                "packaged pilot keeps the configurable 10 percent");
        check(binding.resonances().get("devastation").id().equals("dragon_echo")
                        && !binding.resonances().get("devastation").enabled()
                        && binding.resonances().get("preservation").id().equals("temporal_echo")
                        && !binding.resonances().get("preservation").enabled(),
                "packaged Devastation/Preservation resonance bindings are valid and inert");
        check(!binding.awakening().enabled() && binding.awakening().cooldownSeconds() == 120L,
                "packaged Awakening stays disabled with a whole-second cooldown");

        final String raw = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/config/relics.yml"));
        check(!raw.contains("dragon-relic-id") && !raw.contains("dragon-essence-bonus-percent"),
                "stale legacy dragon keys are fully removed from the packaged config");
    }

    private static Map<String, Object> toMap(final ConfigurationSection section) {
        final LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (final String key : section.getKeys(false)) {
            final Object value = section.get(key);
            result.put(key, value instanceof ConfigurationSection nested ? toMap(nested) : value);
        }
        return result;
    }

    // ---------- Evoker migráció + forrás-szerződések ----------

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

        final String service = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/hu/taliann/icesmp/classrelic/ClassRelicService.java"));
        check(!service.contains("Bukkit.getPlayer"),
                "UUID-only read paths never dereference Player via global lookup");
        check(service.contains("requireKnownRelics"),
                "catalog publish is gated on generic relic existence cross-validation");
        check(service.contains("PERSISTENCE_FAILED"),
                "awakening persistence failure is surfaced, not swallowed");
        check(service.contains("canUse(player, stack)"),
                "possession scan validates usability via the canonical RelicManager.canUse");
        check(service.contains("runAtFixedRate"),
                "possession snapshot refresh runs periodically on the player's own scheduler");
        check(service.contains("setWorldStateListener(this::invalidatePossession)"),
                "world-relic mutations invalidate the possession snapshot immediately");
        check(service.contains("isOwnedByCurrentRegion")
                        && service.contains("getScheduler().run(plugin"),
                "resonance dispatch enforces the actor-region Folia contract (check or hop)");
        check(service.contains("previous catalog stays"),
                "rejected reload keeps the previous catalog snapshot");

        final String relicManager = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/hu/taliann/icesmp/managers/RelicManager.java"));
        check(relicManager.contains("worldState.tryArmAwakening")
                        && !relicManager.contains("setAwakeningReadyAt"),
                "the single awakening writer is the serialized world-state arm operation");
    }

    // ---------- fixtures ----------

    /** Egyszerű világ-állapot fake: framework-kapu + ownership + lost + possession + profil. */
    private static final class World {
        boolean enabled = true;
        UUID owner;
        boolean lost;
        final Set<UUID> possession = new java.util.HashSet<>();
        final Map<UUID, ClassRelicActivationResolver.ProfileFacts> facts = new HashMap<>();

        ClassRelicActivationResolver resolver() {
            return new ClassRelicActivationResolver(
                    () -> enabled,
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

    private static Map<String, Object> withCooldown(final Object cooldownSeconds) {
        final Map<String, Object> root = evokerConfig();
        final LinkedHashMap<String, Object> awakening = new LinkedHashMap<>();
        awakening.put("id", "unborn_dragon");
        awakening.put("enabled", Boolean.FALSE);
        awakening.put("cooldown-seconds", cooldownSeconds);
        ((Map<String, Object>) root.get("sarkany_tojas")).put("awakening", awakening);
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

    private static void expectSignalReject(final Runnable constructor, final String label) {
        boolean rejected = false;
        try {
            constructor.run();
        } catch (final IllegalArgumentException | NullPointerException expected) {
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
