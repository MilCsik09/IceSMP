package hu.taliann.icesmp.trash;

import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Source, authority, secrecy and bounded-runtime gates for Phase E. */
public final class TrashRelicRegressionSuite {

    private static final Path ROOT = Path.of("src/main/java/hu/taliann/icesmp");
    private static final Path CATALOG = Path.of("src/main/resources/content/trash/catalog.yml");
    private static final Path RUNTIME = ROOT.resolve("trash/TrashRelicRuntime.java");
    private static final Path FRACTURES = ROOT.resolve("trash/TrashSpatialFractureStore.java");
    private static final Path HISTORY = ROOT.resolve("trash/TrashHistoryService.java");
    private static final Path CORE = ROOT.resolve("core/IceSMPCore.java");

    private TrashRelicRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        preservesClosedTwentyThreeBehaviorAuthority();
        preservesEveryAuthoredRuntimeBinding();
        preservesBoundedTypedFoliaPrimitives();
        preservesTransactionalConsumeAndReservationPolicy();
        preservesCrashSafeSpatialFractures();
        preservesTransactionalLifecycleProjection();
        preservesRuntimeLifecycleAndEventExclusion();
        preservesSecretRuntimeBoundary();
        System.out.println("Trash relic regression suite passed.");
    }

    private static void preservesClosedTwentyThreeBehaviorAuthority() {
        check(TrashRelicBehavior.values().length == 23,
                "Phase E behavior denominator drifted");
        final TrashCatalog.Parsed parsed = TrashCatalog.parse(
                YamlConfiguration.loadConfiguration(CATALOG.toFile()));
        final List<TrashDefinition> relics = parsed.definitions().values().stream()
                .filter(definition -> definition.internalKind() == TrashKind.TRASH_RELIC).toList();
        check(relics.size() == 23, "catalog consuming-identity denominator drifted");
        final Set<TrashRelicBehavior> bound = relics.stream()
                .map(TrashDefinition::behavior).map(TrashRelicBehavior::parse)
                .collect(Collectors.toUnmodifiableSet());
        check(bound.equals(EnumSet.allOf(TrashRelicBehavior.class)),
                "catalog/runtime consuming vocabulary drifted");
    }

    private static void preservesEveryAuthoredRuntimeBinding() throws Exception {
        final String runtime = Files.readString(RUNTIME);
        for (final TrashRelicBehavior behavior : TrashRelicBehavior.values()) {
            require(runtime, behavior.name(), "runtime binding for " + behavior.name());
        }
        require(ROOT.resolve("trash/TrashCatalog.java"),
                "TrashRelicBehavior.parse(behavior)", "fail-closed catalog behavior parse");
        require(runtime, "behaviorOf(final ItemStack stack)", "single behavior resolver");
        require(runtime, "definition.internalKind() != TrashKind.TRASH_RELIC",
                "hidden kind-authority dispatch boundary");
        require(runtime, "eligibleConsumable", "consumable eligibility boundary");
        require(runtime, "!item.hasItemMeta()", "custom consumable deny-by-default policy");
    }

    private static void preservesBoundedTypedFoliaPrimitives() throws Exception {
        final String runtime = Files.readString(RUNTIME);
        require(runtime, "MAX_FIELDS_PER_WORLD = 32", "per-world field cap");
        require(runtime, "MAX_FIELDS_GLOBAL = 128", "global field safety cap");
        require(runtime, "MAX_NEARBY_ENTITIES = 24", "nearby entity cap");
        require(runtime, "MAX_ANCHORED_DROPS = 64", "death-bundle work cap");
        require(runtime, "MAX_TRACKED_PROJECTILES = 256", "projectile task cap");
        require(runtime, "projectileTrackerPermits.tryAcquire()", "atomic projectile task permit");
        require(runtime, "hasFieldKind(FieldKind.PROJECTILE_WALL)",
                "inactive projectile-wall fast path");
        require(runtime, "projectile.getScheduler().runAtFixedRate", "projectile owner tick");
        require(runtime, "living.getScheduler().run(plugin", "remote entity ownership hop");
        require(runtime, "player.getScheduler().run(plugin", "player ownership hop");
        require(runtime, "Bukkit.getRegionScheduler().run(plugin, destination",
                "travel destination region preflight");
        require(runtime, "claimBestBucket", "explicit RNG opt-in seam");
        require(runtime, "suppressesAcousticsAt", "explicit acoustic opt-in seam");
        require(runtime, "constrainDisplacement", "explicit displacement opt-in seam");
        require(runtime, "blocksCombatAt", "explicit combat-rule opt-in seam");
        check(!runtime.contains("Bukkit.getScheduler()"),
                "legacy Bukkit scheduler entered Phase E");
        check(!runtime.contains("getWorld().getEntities()")
                        && !runtime.contains("getLoadedChunks()"),
                "unbounded world scan entered Phase E");
    }

    private static void preservesTransactionalConsumeAndReservationPolicy() throws Exception {
        check(TrashRelicPolicy.consumptionCommitted(true, 4, 3),
                "committed consumable decrement was rejected");
        check(TrashRelicPolicy.consumptionCommitted(false, 1, 1),
                "committed final consumable replacement was rejected");
        check(!TrashRelicPolicy.consumptionCommitted(true, 4, 4),
                "cancelled consumable use was accepted");
        check(!TrashRelicPolicy.consumptionCommitted(false, 0, 0),
                "empty consumable snapshot was accepted");
        check(TrashRelicPolicy.mayTrackProjectile(true, 255, 256),
                "bounded active projectile tracker was rejected");
        check(!TrashRelicPolicy.mayTrackProjectile(false, 0, 256),
                "projectile task started without a wall");
        check(!TrashRelicPolicy.mayTrackProjectile(true, 256, 256),
                "projectile task exceeded its cap");

        final String runtime = Files.readString(RUNTIME);
        final String history = Files.readString(HISTORY);
        require(runtime, "priority = EventPriority.MONITOR, ignoreCancelled = true)\n"
                        + "    public void onConsume", "commit-observing consume handler");
        require(runtime, "TrashRelicPolicy.consumptionCommitted", "consume commit proof");
        require(runtime, "player.getInventory().getHeldItemSlot()",
                "exact consumed main-hand slot snapshot");
        require(runtime, "sameItemInConsumedSlot", "post-consume hand replacement proof");
        require(history, "transformInventorySlotAndAddOnSuccess",
                "atomic mug/input inventory projection");
        require(runtime, "trash_brick_reservation", "opaque brick reservation marker");
        require(history, "individualizeHandOnSuccess", "single-unit brick reservation");
        require(runtime, "consumeBrickReservation(owner, field.reservationToken())",
                "exact brick reservation consumption");
        require(runtime, "transformHelmetOnSuccess", "equipped helmet-only transform");
        check(runtime.indexOf("consumeBrickReservation(owner, field.reservationToken())")
                        < runtime.lastIndexOf("projectile.remove()"),
                "projectile was removed before brick transformation committed");
    }

    private static void preservesCrashSafeSpatialFractures() throws Exception {
        final String fractures = Files.readString(FRACTURES);
        require(fractures, "implements PersistentStore", "central persistence lifecycle");
        require(fractures, "trash-spatial-fractures.yml", "dedicated journal");
        require(fractures, "YamlStore.registerCriticalWrite", "critical write circuit");
        require(fractures, "YamlStore.saveAtomic", "atomic journal write");
        require(fractures, "MAX_OPEN = 32", "server fracture cap");
        require(fractures, "MAX_OPEN_PER_PLAYER = 2", "per-player fracture cap");
        require(fractures, "block.getState() instanceof TileState", "tile/container exclusion");
        require(fractures, "persistOrRestore(() -> open.remove(id))",
                "journal-before-world-mutation order");
        require(fractures, "setType(org.bukkit.Material.AIR, false)",
                "bounded aperture apply");
        require(fractures, "setBlockData(data, false)", "snapshot restore");
        require(fractures, "public synchronized void recover()", "restart recovery");
        require(fractures, "public synchronized void recoverWorld", "late world recovery");
        require(fractures, "public synchronized void shutdown()", "shutdown restore request");
        require(Files.readString(RUNTIME), "fractures.shutdown()", "runtime fracture shutdown");
        final String core = Files.readString(CORE);
        final int history = core.indexOf("trashHistoryStore,");
        final int anomaly = core.indexOf("trashAnomalyStateStore,", history);
        final int spatial = core.indexOf("trashSpatialFractureStore,", anomaly);
        final int recycle = core.indexOf("trashRecyclePool);", spatial);
        check(history >= 0 && anomaly > history && spatial > anomaly && recycle > spatial,
                "persistent authority load order drifted");
    }

    private static void preservesTransactionalLifecycleProjection() throws Exception {
        final String history = Files.readString(HISTORY);
        final String runtime = Files.readString(RUNTIME);
        require(history, "transformInventorySlotOnSuccess", "slot transaction boundary");
        require(history, "final ItemStack[] before = cloneContents", "inventory rollback snapshot");
        require(history, "store.transact(() ->", "durable history transaction");
        require(history, "player.getInventory().setContents(before)",
                "inventory projection rollback");
        require(runtime, "history.transformInventorySlotOnSuccess", "runtime transition path");
        require(runtime, "dropTransformed", "pre-death self-drop and transfer path");
        require(runtime, "event.setDamage(Math.max(0.0D, player.getHealth() - 1.0D))",
                "survive-at-one hook");
        require(runtime, "event.setCancelled(true)", "one-shot veto path");
        require(runtime, "deathAnchorKey", "death-drop anchor marker");
    }

    private static void preservesRuntimeLifecycleAndEventExclusion() throws Exception {
        final String core = Files.readString(CORE);
        require(core, "trashRelicRuntime.start()", "startup recovery");
        require(core, "trashRelicRuntime::shutdown", "shutdown cleanup");
        require(core, "registerEvents(trashRelicRuntime", "listener registration");
        require(core, "majorEventGate.register(\"blood-moon\"",
                "blood moon mutual-exclusion authority");
        require(core, "majorEventGate.register(\"season-finale\"",
                "season finale event lock");
        require(ROOT.resolve("listeners/PlayerSessionCleanupListener.java"),
                "trashRelicRuntime", "per-player runtime cleanup");
    }

    private static void preservesSecretRuntimeBoundary() throws Exception {
        final String runtime = Files.readString(RUNTIME);
        check(!runtime.contains("getLogger()") && !runtime.contains("sendMessage("),
                "normal logging/chat leaked hidden Phase E state");
        check(!runtime.contains("trash_kind") && !runtime.contains("trash_behavior"),
                "hidden kind/behavior leaked into physical state");
        for (final Path publicDoc : List.of(Path.of("docs/PLAYER_GUIDE.md"),
                Path.of("docs/ADMIN_GUIDE.md"), Path.of("docs/FEATURES.md"),
                Path.of("docs/LATEST_CHANGES.md"), Path.of("docs/LORE.md"))) {
            if (!Files.exists(publicDoc)) continue;
            final String source = Files.readString(publicDoc);
            check(!source.contains("TrashRelicRuntime")
                            && !source.contains("trash-spatial-fractures")
                            && !source.contains("Trash Relic primitives"),
                    "Phase E leaked into public/admin docs: " + publicDoc);
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
