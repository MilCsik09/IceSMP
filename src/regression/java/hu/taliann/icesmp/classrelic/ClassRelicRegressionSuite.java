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
        visibilityBeforeDurability();
        atomicLoadPublish();
        ownerBoundLostMatrix();
        claimTransferRecoveryProtocol();
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
        store.recordOwnership("relic_d", OTHER_PLAYER, 5_000L);
        final List<Runnable> work = List.of(
                () -> store.tryArmAwakening("relic_a", 1_000L, 60L),
                () -> store.tryArmAwakening("relic_b", 1_000L, 90L),
                () -> store.recordOwnership("relic_c", EVOKER_PLAYER, 7_000L),
                () -> store.markLost("relic_d", OTHER_PLAYER, 8_000L));
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
                        && EVOKER_PLAYER.toString().equals(yaml.getString("ownerships.relic_c.owner"))
                        && yaml.getLong("ownerships.relic_d.lost-since") == 8_000L,
                "the final durable write contains every parallel mutation's committed state");
        check(store.isLost("relic_d"), "parallel owner-bound markLost committed");
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
        check(store.markLost("sarkany_tojas", OTHER_PLAYER, 1_300_000L)
                        == RelicWorldStateStore.MarkLostResult.MARKED, "owner marks lost");
        check(store.clearLost("sarkany_tojas", OTHER_PLAYER), "owner clears lost");
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

    // ---------- publish-commit sorrend: candidate sosem látható durable commit előtt ----------

    private static void visibilityBeforeDurability() throws Exception {
        final CountDownLatch writerEntered = new CountDownLatch(1);
        final CountDownLatch releaseWriter = new CountDownLatch(1);
        final AtomicReference<Boolean> failWrite = new AtomicReference<>(Boolean.FALSE);
        final RelicWorldStateStore store = new RelicWorldStateStore(yaml -> {
            writerEntered.countDown();
            try {
                releaseWriter.await(30L, java.util.concurrent.TimeUnit.SECONDS);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            if (failWrite.get()) {
                throw new IOException("injected failure after visibility check");
            }
        }, LOGGER);

        // Siker-ág: a blokkolt durable írás ALATT az olvasó a régi pillanatképet látja.
        Thread mutation = new Thread(() ->
                store.recordOwnership("sarkany_tojas", EVOKER_PLAYER, 1_000L));
        mutation.start();
        check(writerEntered.await(30L, java.util.concurrent.TimeUnit.SECONDS),
                "durable writer reached");
        check(store.ownership("sarkany_tojas") == null,
                "reader NEVER sees the candidate before the durable commit succeeds");
        releaseWriter.countDown();
        mutation.join(30_000L);
        check(store.ownership("sarkany_tojas") != null
                        && store.ownership("sarkany_tojas").owner().equals(EVOKER_PLAYER),
                "reader sees the new snapshot only after the durable commit");

        // Hiba-ág: a candidate az írás-hiba után sem válhat láthatóvá.
        final CountDownLatch writerEntered2 = new CountDownLatch(1);
        final CountDownLatch releaseWriter2 = new CountDownLatch(1);
        final RelicWorldStateStore failing = new RelicWorldStateStore(yaml -> {
            writerEntered2.countDown();
            try {
                releaseWriter2.await(30L, java.util.concurrent.TimeUnit.SECONDS);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("injected failure");
        }, LOGGER);
        final Thread failingMutation = new Thread(() -> {
            try {
                failing.recordOwnership("sarkany_tojas", OTHER_PLAYER, 2_000L);
            } catch (final RuntimeException expected) {
            }
        });
        failingMutation.start();
        check(writerEntered2.await(30L, java.util.concurrent.TimeUnit.SECONDS),
                "failing durable writer reached");
        check(failing.ownership("sarkany_tojas") == null,
                "reader sees the old snapshot while the failing write is in flight");
        releaseWriter2.countDown();
        failingMutation.join(30_000L);
        check(failing.ownership("sarkany_tojas") == null,
                "a failed candidate NEVER becomes the runtime authority");
    }

    // ---------- atomikus load-publish: nincs üres/félig-töltött köztes állapot ----------

    private static void atomicLoadPublish() throws Exception {
        final YamlConfiguration stateA = new YamlConfiguration();
        stateA.set("ownerships.relic_x.owner", EVOKER_PLAYER.toString());
        stateA.set("ownerships.relic_x.last-seen", 1L);
        stateA.set("ownerships.relic_y.owner", EVOKER_PLAYER.toString());
        stateA.set("ownerships.relic_y.last-seen", 1L);
        final YamlConfiguration stateB = new YamlConfiguration();
        stateB.set("ownerships.relic_x.owner", OTHER_PLAYER.toString());
        stateB.set("ownerships.relic_x.last-seen", 2L);
        stateB.set("ownerships.relic_y.owner", OTHER_PLAYER.toString());
        stateB.set("ownerships.relic_y.last-seen", 2L);

        final RelicWorldStateStore store = new RelicWorldStateStore(yaml -> {
        }, LOGGER);
        final AtomicReference<String> violation = new AtomicReference<>();
        final CountDownLatch stop = new CountDownLatch(1);
        final Thread reader = new Thread(() -> {
            while (stop.getCount() > 0 && violation.get() == null) {
                final var snapshot = store.snapshot();
                final RelicOwnershipPair pair = new RelicOwnershipPair(
                        snapshot.ownerships().get("relic_x"), snapshot.ownerships().get("relic_y"));
                if (!pair.consistent()) {
                    violation.set("hybrid snapshot observed: " + pair);
                }
            }
        });
        reader.start();
        for (int round = 0; round < 500; round++) {
            store.loadFrom(round % 2 == 0 ? stateA : stateB);
        }
        stop.countDown();
        reader.join(30_000L);
        check(violation.get() == null,
                "concurrent readers only ever observe COMPLETE load snapshots ("
                        + violation.get() + ")");
    }

    /** relic_x és relic_y mindig együtt, azonos tulajdonossal mozog — a hibrid látvány sérülés. */
    private record RelicOwnershipPair(hu.taliann.icesmp.relics.RelicOwnership x,
                                      hu.taliann.icesmp.relics.RelicOwnership y) {
        boolean consistent() {
            if (x == null && y == null) {
                return true;
            }
            return x != null && y != null && x.owner().equals(y.owner());
        }
    }

    // ---------- owner-kötött lost mutáció ----------

    private static void ownerBoundLostMatrix() throws Exception {
        final AtomicReference<YamlConfiguration> lastWrite = new AtomicReference<>();
        final RelicWorldStateStore store = new RelicWorldStateStore(lastWrite::set, LOGGER);

        check(store.markLost("sarkany_tojas", EVOKER_PLAYER, 1_000L)
                        == RelicWorldStateStore.MarkLostResult.NOT_OWNER,
                "markLost without any owner is rejected (no orphan lost)");
        check(!store.isLost("sarkany_tojas"), "no lost state without ownership");

        store.recordOwnership("sarkany_tojas", EVOKER_PLAYER, 2_000L);
        check(store.markLost("sarkany_tojas", OTHER_PLAYER, 3_000L)
                        == RelicWorldStateStore.MarkLostResult.NOT_OWNER,
                "a stale copy's previous owner cannot mark someone else's live relic lost");
        check(!store.isLost("sarkany_tojas")
                        && store.ownership("sarkany_tojas").owner().equals(EVOKER_PLAYER),
                "rejected markLost leaves ownership and lost state untouched");

        check(store.markLost("sarkany_tojas", EVOKER_PLAYER, 4_000L)
                        == RelicWorldStateStore.MarkLostResult.MARKED,
                "the proven current owner marks lost");
        check(store.isLost("sarkany_tojas"), "lost state recorded for the owner");
        check(!store.clearLost("sarkany_tojas", OTHER_PLAYER),
                "a non-owner cannot clear the owner's lost mark (reclaim stays available)");
        check(store.isLost("sarkany_tojas"), "lost mark survives the foreign clear attempt");

        // Restart round-trip: owner + lost EGYÜTT jön vissza (árva lost nem reprezentálható).
        final YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(lastWrite.get().saveToString());
        final RelicWorldStateStore fresh = new RelicWorldStateStore(yaml -> {
        }, LOGGER);
        fresh.loadFrom(reloaded);
        check(fresh.ownership("sarkany_tojas").owner().equals(EVOKER_PLAYER)
                        && fresh.isLost("sarkany_tojas"),
                "owner and lost state restart together");
        check(fresh.clearLost("sarkany_tojas", EVOKER_PLAYER),
                "the owner clears their own lost mark");
    }

    // ---------- claim/transfer recovery-protokoll (failure injection minden lépés után) ----------

    /**
     * A fizikai oldal modellje: relic-id → (birtokolt példány PDC-tulajdonosa). A
     * recovery-döntések PONTOSAN a production RelicManager join-recovery szabályait
     * követik: CLAIM/RECLAIM receipt → kézbesítés csak akkor, ha a tulajnál nincs
     * példány; TRANSFER receipt → a példány PDC-átírása, ha a tulajnál van.
     */
    private static final class PhysicalModel {
        final java.util.HashMap<String, UUID> itemPdcOwner = new java.util.HashMap<>();

        void recover(final RelicWorldStateStore store, final UUID joiningPlayer) {
            for (final var entry : store.pendingOperationsFor(joiningPlayer).entrySet()) {
                final String relicId = entry.getKey();
                final var ownership = store.ownership(relicId);
                if (ownership == null || !ownership.owner().equals(joiningPlayer)) {
                    continue;
                }
                switch (entry.getValue().type()) {
                    case CLAIM, RECLAIM -> {
                        if (!joiningPlayer.equals(itemPdcOwner.get(relicId))) {
                            itemPdcOwner.put(relicId, joiningPlayer);
                        }
                        store.completeOperation(relicId);
                    }
                    case TRANSFER -> {
                        if (itemPdcOwner.containsKey(relicId)) {
                            itemPdcOwner.put(relicId, joiningPlayer);
                            store.completeOperation(relicId);
                        }
                    }
                }
            }
        }

        void assertConsistent(final RelicWorldStateStore store, final String relicId,
                              final String label) {
            final var ownership = store.ownership(relicId);
            check(ownership != null, label + ": exactly one world owner exists");
            final UUID pdc = itemPdcOwner.get(relicId);
            check(pdc == null || pdc.equals(ownership.owner()),
                    label + ": item PDC owner matches the world owner");
            check(!store.isLost(relicId) || itemPdcOwner.get(relicId) == null,
                    label + ": lost implies no live physical copy in the model");
        }
    }

    private static RelicWorldStateStore reopened(final AtomicReference<YamlConfiguration> lastWrite,
                                                 final RelicWorldStateStore.DurableWriter writer)
            throws Exception {
        final RelicWorldStateStore fresh = new RelicWorldStateStore(writer, LOGGER);
        if (lastWrite.get() != null) {
            final YamlConfiguration reloaded = new YamlConfiguration();
            reloaded.loadFromString(lastWrite.get().saveToString());
            fresh.loadFrom(reloaded);
        }
        return fresh;
    }

    private static void claimTransferRecoveryProtocol() throws Exception {
        final AtomicReference<YamlConfiguration> lastWrite = new AtomicReference<>();
        final AtomicReference<Boolean> failWrites = new AtomicReference<>(Boolean.FALSE);
        final RelicWorldStateStore.DurableWriter writer = yaml -> {
            if (failWrites.get()) {
                throw new IOException("injected disk failure");
            }
            lastWrite.set(yaml);
        };

        // --- Claim: hiba a világ-commit ELŐTT (a begin írása bukik) → semmi sem történt.
        RelicWorldStateStore store = new RelicWorldStateStore(writer, LOGGER);
        final PhysicalModel model = new PhysicalModel();
        failWrites.set(Boolean.TRUE);
        boolean rejected = false;
        try {
            store.beginClaim("sarkany_tojas", EVOKER_PLAYER, 1_000L,
                    hu.taliann.icesmp.relics.RelicWorldStateSnapshot
                            .PendingRelicOperation.Type.CLAIM);
        } catch (final RuntimeException expected) {
            rejected = true;
        }
        check(rejected && store.ownership("sarkany_tojas") == null
                        && store.pendingOperation("sarkany_tojas") == null,
                "claim: failure before the world commit leaves nothing behind (fail-closed)");

        // --- Claim: crash a világ-commit UTÁN, a kézbesítés ELŐTT → recovery kézbesít.
        failWrites.set(Boolean.FALSE);
        store.beginClaim("sarkany_tojas", EVOKER_PLAYER, 1_000L,
                hu.taliann.icesmp.relics.RelicWorldStateSnapshot
                        .PendingRelicOperation.Type.CLAIM);
        check(lastWrite.get().getString("operations.sarkany_tojas.type").equals("CLAIM")
                        && EVOKER_PLAYER.toString()
                        .equals(lastWrite.get().getString("ownerships.sarkany_tojas.owner")),
                "claim: ownership, lost-clear and the delivery receipt commit in ONE durable write");
        store = reopened(lastWrite, writer);
        check(store.pendingOperation("sarkany_tojas") != null,
                "claim: the delivery receipt survives the crash");
        model.recover(store, EVOKER_PLAYER);
        check(EVOKER_PLAYER.equals(model.itemPdcOwner.get("sarkany_tojas"))
                        && store.pendingOperation("sarkany_tojas") == null,
                "claim: recovery delivers exactly one item and settles the receipt");
        model.assertConsistent(store, "sarkany_tojas", "claim recovery");

        // --- Claim: crash a kézbesítés UTÁN, a receipt-zárás előtt → recovery NEM duplikál.
        store.beginClaim("sarkany_tojas", EVOKER_PLAYER, 2_000L,
                hu.taliann.icesmp.relics.RelicWorldStateSnapshot
                        .PendingRelicOperation.Type.RECLAIM);
        model.itemPdcOwner.put("sarkany_tojas", EVOKER_PLAYER);
        store = reopened(lastWrite, writer);
        final int itemsBefore = model.itemPdcOwner.size();
        model.recover(store, EVOKER_PLAYER);
        check(model.itemPdcOwner.size() == itemsBefore
                        && store.pendingOperation("sarkany_tojas") == null,
                "claim: recovery after delivery only settles the receipt — no duplicate relic");
        model.assertConsistent(store, "sarkany_tojas", "claim idempotent recovery");

        // --- Reclaim: lost=true → begin (lost törlődik + receipt) → crash → recovery.
        check(store.markLost("sarkany_tojas", EVOKER_PLAYER, 3_000L)
                        == RelicWorldStateStore.MarkLostResult.MARKED, "reclaim: relic lost");
        model.itemPdcOwner.remove("sarkany_tojas");
        store.beginClaim("sarkany_tojas", EVOKER_PLAYER, 4_000L,
                hu.taliann.icesmp.relics.RelicWorldStateSnapshot
                        .PendingRelicOperation.Type.RECLAIM);
        store = reopened(lastWrite, writer);
        check(!store.isLost("sarkany_tojas"),
                "reclaim: the lost mark clears in the same durable commit as the receipt");
        model.recover(store, EVOKER_PLAYER);
        check(EVOKER_PLAYER.equals(model.itemPdcOwner.get("sarkany_tojas")),
                "reclaim: recovery delivers the resummoned relic");
        model.assertConsistent(store, "sarkany_tojas", "reclaim recovery");

        // --- Transfer: hiba a világ-commit ELŐTT → régi tulajdon és PDC érintetlen.
        failWrites.set(Boolean.TRUE);
        rejected = false;
        try {
            store.beginTransfer("sarkany_tojas", EVOKER_PLAYER, OTHER_PLAYER, 5_000L);
        } catch (final RuntimeException expected) {
            rejected = true;
        }
        check(rejected && store.ownership("sarkany_tojas").owner().equals(EVOKER_PLAYER)
                        && EVOKER_PLAYER.equals(model.itemPdcOwner.get("sarkany_tojas")),
                "transfer: failure before the world commit changes nothing");

        // --- Transfer: crash a világ-commit UTÁN, a PDC-átírás ELŐTT → recovery átírja.
        failWrites.set(Boolean.FALSE);
        store.beginTransfer("sarkany_tojas", EVOKER_PLAYER, OTHER_PLAYER, 6_000L);
        model.itemPdcOwner.put("sarkany_tojas", OTHER_PLAYER); // a gyilkos felvette a dropot
        store = reopened(lastWrite, writer);
        check(store.ownership("sarkany_tojas").owner().equals(OTHER_PLAYER)
                        && store.pendingOperation("sarkany_tojas") != null,
                "transfer: world owner committed first, the PDC receipt survives the crash");
        model.itemPdcOwner.put("sarkany_tojas", EVOKER_PLAYER); // PDC még a régi tulajé
        model.recover(store, OTHER_PLAYER);
        check(OTHER_PLAYER.equals(model.itemPdcOwner.get("sarkany_tojas"))
                        && store.pendingOperation("sarkany_tojas") == null,
                "transfer: recovery rewrites the PDC to the new owner and settles the receipt");
        model.assertConsistent(store, "sarkany_tojas", "transfer recovery");

        // --- Transfer: crash a PDC-átírás UTÁN → recovery csak lezár (idempotens).
        store.beginTransfer("sarkany_tojas", OTHER_PLAYER, EVOKER_PLAYER, 7_000L);
        model.itemPdcOwner.put("sarkany_tojas", EVOKER_PLAYER);
        store = reopened(lastWrite, writer);
        model.recover(store, EVOKER_PLAYER);
        check(EVOKER_PLAYER.equals(model.itemPdcOwner.get("sarkany_tojas"))
                        && store.pendingOperation("sarkany_tojas") == null,
                "transfer: recovery after the PDC rewrite is a pure receipt settle");
        model.assertConsistent(store, "sarkany_tojas", "transfer idempotent recovery");

        // --- A receipt-zárás írás-hibája: a receipt függőben marad, később lezárható.
        store.beginClaim("sarkany_tojas", EVOKER_PLAYER, 8_000L,
                hu.taliann.icesmp.relics.RelicWorldStateSnapshot
                        .PendingRelicOperation.Type.CLAIM);
        failWrites.set(Boolean.TRUE);
        rejected = false;
        try {
            store.completeOperation("sarkany_tojas");
        } catch (final RuntimeException expected) {
            rejected = true;
        }
        check(rejected && store.pendingOperation("sarkany_tojas") != null,
                "receipt settle failure keeps the receipt pending (no silent loss)");
        failWrites.set(Boolean.FALSE);
        check(store.completeOperation("sarkany_tojas")
                        && store.pendingOperation("sarkany_tojas") == null,
                "the pending receipt settles once the disk recovers");
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

        final ClassGameplaySignal.Kill kill = new ClassGameplaySignal.Kill(
                EVOKER_PLAYER, OTHER_PLAYER, "wither_skeleton", Set.of(AbilityTag.MELEE));
        check(kill.event() == ClassGameplayEvent.KILL
                        && kill.targetId().equals(OTHER_PLAYER)
                        && kill.targetKind().equals("wither_skeleton"),
                "kill signal carries target identity and semantic kind");

        final ClassGameplaySignal.Block block = new ClassGameplaySignal.Block(
                EVOKER_PLAYER, java.util.Optional.of(OTHER_PLAYER), 7.5D, Set.of());
        check(block.event() == ClassGameplayEvent.BLOCK
                        && block.sourceId().orElseThrow().equals(OTHER_PLAYER)
                        && block.preventedAmount() == 7.5D,
                "block signal carries source identity and prevented damage");

        final ClassGameplaySignal.FormChanged form = new ClassGameplaySignal.FormChanged(
                EVOKER_PLAYER, "", "sarkany_alak", Set.of());
        check(form.event() == ClassGameplayEvent.FORM_CHANGED
                        && form.previousFormId().isEmpty()
                        && form.newFormId().equals("sarkany_alak"),
                "form-change signal carries previous and new form (first change allowed)");

        check(new ClassGameplaySignal.MovementAbility(EVOKER_PLAYER, "arnyeklepes", Set.of())
                        .event() == ClassGameplayEvent.MOVEMENT_ABILITY,
                "movement ability signal carries the semantic ability id");
        final ClassGameplaySignal.LowHealthEntered lowHealth =
                new ClassGameplaySignal.LowHealthEntered(EVOKER_PLAYER, 0.18D, 0.2D, Set.of());
        check(lowHealth.event() == ClassGameplayEvent.LOW_HEALTH_ENTERED
                        && lowHealth.healthRatio() == 0.18D && lowHealth.thresholdRatio() == 0.2D,
                "low-health signal carries the ratio and the crossed threshold");
        check(new ClassGameplaySignal.ResourceFull(EVOKER_PLAYER, Set.of())
                        .event() == ClassGameplayEvent.RESOURCE_FULL,
                "resource-full has an explicit typed form");

        expectSignalReject(() -> new ClassGameplaySignal.Generic(
                        ClassGameplayEvent.DAMAGE_DEALT, EVOKER_PLAYER, Set.of()),
                "payload-carrying event cannot hide behind the Generic form");
        expectSignalReject(() -> new ClassGameplaySignal.Generic(
                        ClassGameplayEvent.KILL, EVOKER_PLAYER, Set.of()),
                "kill cannot hide behind the Generic form");
        expectSignalReject(() -> new ClassGameplaySignal.Generic(
                        ClassGameplayEvent.BLOCK, EVOKER_PLAYER, Set.of()),
                "block cannot hide behind the Generic form");
        check(new ClassGameplaySignal.Generic(ClassGameplayEvent.DODGE, EVOKER_PLAYER, Set.of())
                        .event() == ClassGameplayEvent.DODGE,
                "the remaining payload-free event uses the Generic form");
        expectSignalReject(() -> new ClassGameplaySignal.DamageDealt(
                        EVOKER_PLAYER, OTHER_PLAYER, -1.0D, Set.of()),
                "negative amount rejected");
        expectSignalReject(() -> new ClassGameplaySignal.DamageDealt(
                        EVOKER_PLAYER, OTHER_PLAYER, Double.NaN, Set.of()),
                "non-finite amount rejected");
        expectSignalReject(() -> new ClassGameplaySignal.Summon(
                        EVOKER_PLAYER, "sarkany", 0, Set.of()),
                "non-positive summon count rejected");
        expectSignalReject(() -> new ClassGameplaySignal.LowHealthEntered(
                        EVOKER_PLAYER, 1.2D, 0.2D, Set.of()),
                "out-of-range health ratio rejected");
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

        check(!service.contains("relicManager.isEnabled()"),
                "the generic-relic existence gate runs unconditionally (disabled runtime too)");

        final String relicManager = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/hu/taliann/icesmp/managers/RelicManager.java"));
        check(relicManager.contains("worldState.tryArmAwakening")
                        && !relicManager.contains("setAwakeningReadyAt"),
                "the single awakening writer is the serialized world-state arm operation");
        check(relicManager.contains("beginClaim") && relicManager.contains("beginTransfer")
                        && relicManager.contains("recoverPendingOperations"),
                "the production give/transfer flows run on the receipt-based recovery protocol");
        check(relicManager.contains("ownership == null || isExpiredFor"),
                "canUse is fail-closed: no active central owner means no usable physical copy");
        check(relicManager.contains("definition(s) loaded for validation only"),
                "generic relic definitions load even while the relic runtime is disabled");
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
