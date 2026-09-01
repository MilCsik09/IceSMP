package hu.taliann.icesmp.trash;

import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Source, authority, secrecy and bounded-runtime gates for Phase D. */
public final class TrashAnomalyRegressionSuite {

    private static final Path ROOT = Path.of("src/main/java/hu/taliann/icesmp");
    private static final Path CATALOG = Path.of("src/main/resources/content/trash/catalog.yml");
    private static final Path RUNTIME = ROOT.resolve("trash/TrashAnomalyRuntime.java");
    private static final Path TOSSES = ROOT.resolve("trash/TossableObjectRuntime.java");
    private static final Path STORE = ROOT.resolve("trash/TrashAnomalyStateStore.java");
    private static final Path CORE = ROOT.resolve("core/IceSMPCore.java");

    private TrashAnomalyRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        preservesClosedFortyTwoBehaviorAuthority();
        preservesEveryAuthoredRuntimeBinding();
        preservesAuthoredStackCompassEchoAndPairBehavior();
        preservesBoundedFoliaPrimitives();
        preservesDurableOpaqueMemory();
        preservesTransactionalTransforms();
        preservesSecretRuntimeBoundary();
        System.out.println("Trash anomaly regression suite passed.");
    }

    private static void preservesAuthoredStackCompassEchoAndPairBehavior() throws Exception {
        check(!TrashAnomalyPolicy.blocksGroundMerge(false, false),
                "equivalent fresh Anomaly entities must remain mergeable");
        check(TrashAnomalyPolicy.blocksGroundMerge(true, false)
                        && TrashAnomalyPolicy.blocksGroundMerge(false, true),
                "individual runtime state must block merge from either side");
        final TrashAnomalyPolicy.OppositePoint east = TrashAnomalyPolicy.oppositePoint(
                0.0D, 0.0D, 10.0D, 0.0D, 100.0D);
        check(east != null && Math.abs(east.x() + 100.0D) < 0.0001D
                        && Math.abs(east.z()) < 0.0001D,
                "compass projection is not the exact 180-degree opposite vector");
        final TrashAnomalyPolicy.OppositePoint diagonal = TrashAnomalyPolicy.oppositePoint(
                4.0D, -2.0D, 13.0D, 10.0D, 256.0D);
        check(diagonal != null, "valid diagonal compass vector was rejected");
        final double sourceX = 9.0D;
        final double sourceZ = 12.0D;
        final double projectedX = diagonal.x() - 4.0D;
        final double projectedZ = diagonal.z() + 2.0D;
        check(Math.abs(sourceX * projectedZ - sourceZ * projectedX) < 0.0001D
                        && sourceX * projectedX + sourceZ * projectedZ < 0.0D,
                "diagonal compass vector is not collinear and opposite");

        final String runtime = Files.readString(RUNTIME);
        require(runtime, "runtimeStateEntities.contains(event.getTarget().getUniqueId())",
                "ownership-safe target runtime-state merge guard");
        require(runtime, "releasePhysics(event.getEntity().getUniqueId())",
                "successful merge task retirement");
        check(!runtime.contains("|| behaviorOf(event.getEntity().getItemStack()).isPresent()"),
                "special identity alone still blocks ground merging");
        require(runtime, "player.sendEquipmentChange(player, hand, projected)",
                "player-only compass needle projection");
        require(runtime, "captured.sound(), captured.volume(), captured.pitch()",
                "exact captured eligible sound replay");
        check(!runtime.contains("Sound.BLOCK_AMETHYST_BLOCK_RESONATE"),
                "delayed echo still substitutes a fixed authored sound");
        require(runtime, "final List<Item> candidates", "bounded multi-candidate pair probe");
        require(runtime, "claimed.compareAndSet(false, true)",
                "single-winner compatible pair claim");
    }

    private static void preservesClosedFortyTwoBehaviorAuthority() {
        check(TrashAnomalyBehavior.values().length == 42,
                "Phase D behavior denominator drifted");
        final TrashCatalog.Parsed parsed = TrashCatalog.parse(
                YamlConfiguration.loadConfiguration(CATALOG.toFile()));
        final List<TrashDefinition> anomalies = parsed.definitions().values().stream()
                .filter(definition -> definition.internalKind() == TrashKind.ANOMALY).toList();
        check(anomalies.size() == 42, "catalog anomaly denominator drifted");
        final Set<TrashAnomalyBehavior> bound = anomalies.stream()
                .map(TrashDefinition::behavior).map(TrashAnomalyBehavior::parse)
                .collect(Collectors.toUnmodifiableSet());
        check(bound.equals(EnumSet.allOf(TrashAnomalyBehavior.class)),
                "catalog/runtime behavior vocabulary drifted");
        check(EnumSet.allOf(TrashAnomalyBehavior.Primitive.class).equals(
                bound.stream().map(TrashAnomalyBehavior::primitive)
                        .collect(Collectors.toSet())), "reusable primitive coverage drifted");
    }

    private static void preservesEveryAuthoredRuntimeBinding() throws Exception {
        final String runtime = Files.readString(RUNTIME) + Files.readString(TOSSES);
        for (final TrashAnomalyBehavior behavior : TrashAnomalyBehavior.values()) {
            require(runtime, behavior.name(), "runtime binding for " + behavior.name());
        }
        require(ROOT.resolve("trash/TrashCatalog.java"),
                "TrashAnomalyBehavior.parse(behavior)", "fail-closed catalog behavior parse");
        require(runtime, "behaviorOf(final ItemStack stack)", "single runtime behavior resolver");
        require(runtime, "definition.internalKind() != TrashKind.ANOMALY",
                "kind-authority dispatch boundary");
    }

    private static void preservesBoundedFoliaPrimitives() throws Exception {
        final String runtime = Files.readString(RUNTIME);
        final String policy = Files.readString(ROOT.resolve("trash/TrashAnomalyPolicy.java"));
        final String tosses = Files.readString(TOSSES);
        require(runtime, "MAX_ACTIVE_PHYSICS_PER_WORLD = 256", "per-world physics cap");
        require(runtime, "MAX_PENDING_ECHOES = 256", "global delayed-sound cap");
        require(runtime, "MAX_SEEK_RADIUS = 12.0D", "bounded seek radius");
        require(runtime, "MAX_NEARBY_ENTITIES = 24", "bounded nearby entity work");
        require(runtime, "getGlobalRegionScheduler().runAtFixedRate", "global player enumeration");
        require(runtime, "player.getScheduler().run(plugin", "player ownership hop");
        require(runtime, "item.getScheduler().runAtFixedRate", "item ownership tick");
        require(runtime, "living.getScheduler().run(plugin", "recognition entity ownership hop");
        require(runtime, "coin.getScheduler().run(plugin", "currency entity ownership hop");
        require(runtime, "frame.getScheduler().run(plugin", "item-frame ownership hop");
        require(runtime, "dropped.getScheduler().run(plugin", "dropped-item ownership hop");
        require(runtime, "Bukkit.getGlobalRegionScheduler().runDelayed", "bounded echo delay");
        require(runtime, "Bukkit.getRegionScheduler().run(plugin, origin",
                "location-owned delayed echo");
        check(!runtime.contains("Bukkit.getScheduler()") && !tosses.contains("Bukkit.getScheduler()"),
                "legacy Bukkit scheduler entered Phase D");
        check(!runtime.contains("getWorld().getEntities()") && !runtime.contains("getLoadedChunks()"),
                "unbounded world scan entered Phase D");
        check(!runtime.contains("hasRuntimeState(event.getTarget())"),
                "merge callback must not read the other Item PDC");
        check(!policy.contains("org.bukkit"), "pure Anomaly policy gained a Bukkit dependency");
        require(tosses, "sample.getBlockX() >> 4 != origin.getBlockX() >> 4",
                "toss block reads remain in the owned chunk");
    }

    private static void preservesDurableOpaqueMemory() throws Exception {
        final String store = Files.readString(STORE);
        require(store, "implements PersistentStore", "central persistence lifecycle");
        require(store, "trash-anomaly-state.yml", "dedicated anomaly memory store");
        require(store, "YamlStore.registerCriticalWrite", "critical state write circuit");
        require(store, "YamlStore.saveAtomic", "atomic anomaly memory save");
        require(store, "MAX_COUNTER = 1_000_000_000L", "bounded anomaly counters");
        require(store, "MAX_INSTANCES = 100_000", "bounded anomaly instance memory");
        require(store, "LOCAL_PLAYER_DEATHS", "death memory");
        require(store, "WATCHED_TICKS", "held-watch memory");
        require(store, "addDurably", "rare significant counter immediate commit");
        require(store, "states.put(instanceId, before)", "counter rollback on write failure");
        final String core = Files.readString(CORE);
        final int history = core.indexOf("trashHistoryStore,");
        final int anomaly = core.indexOf("trashAnomalyStateStore,", history);
        final int recycle = core.indexOf("trashRecyclePool);", anomaly);
        check(history >= 0 && anomaly > history && recycle > anomaly,
                "persistent authority load order drifted");
    }

    private static void preservesTransactionalTransforms() throws Exception {
        final String runtime = Files.readString(RUNTIME);
        require(runtime, "history.transformOnSuccess", "authored lifecycle transition");
        require(runtime, "event.setNewCurrent(event.getOldCurrent())",
                "one rising-edge suppression");
        require(runtime, "item.getPersistentDataContainer().remove(runtimeStateKey)",
                "one-shot mechanism marker consumption");
        require(runtime, "pairReservations", "pair double-consumption reservation");
        require(runtime, "rollback(rollbackLocation, consumed)",
                "pair rollback path");
        require(runtime, "releasePhysics(final UUID itemId)",
                "retired-safe physics release");
        require(runtime, "item.setGravity(true)", "shutdown/unload gravity restoration");
        require(runtime, "splitAndRecord(held, TrashHistoryEvent.WORLD_EVENT_PRESENT",
                "attachment singleton split before world placement");
        require(runtime, "claims.canUse", "claim interaction preflight");
        require(runtime, "territoryProtection.denyInteract", "territory interaction preflight");
    }

    private static void preservesSecretRuntimeBoundary() throws Exception {
        final String runtime = Files.readString(RUNTIME);
        check(!runtime.contains("getLogger()") && !runtime.contains("sendMessage("),
                "normal logging/chat leaked hidden Phase D state");
        check(!runtime.contains("trash_kind") && !runtime.contains("trash_behavior"),
                "hidden kind/behavior leaked into physical state");
        for (final Path publicDoc : List.of(Path.of("docs/PLAYER_GUIDE.md"),
                Path.of("docs/ADMIN_GUIDE.md"), Path.of("docs/FEATURES.md"),
                Path.of("docs/LATEST_CHANGES.md"), Path.of("docs/LORE.md"))) {
            if (!Files.exists(publicDoc)) continue;
            final String source = Files.readString(publicDoc);
            check(!source.contains("TrashAnomalyRuntime")
                            && !source.contains("trash-anomaly-state")
                            && !source.contains("felrevert_garas"),
                    "Phase D leaked into public/admin docs: " + publicDoc);
        }
    }

    private static void require(final Path source, final String token, final String description)
            throws Exception {
        require(Files.readString(source), token, description);
    }

    private static void require(final String source, final String token, final String description) {
        check(source.contains(token), "missing " + description + ": " + token);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
