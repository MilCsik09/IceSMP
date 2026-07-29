package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.selection.CuboidSelectionService;
import hu.taliann.icesmp.selection.IdentityTaskRegistry;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Native AFK-zone domain regressions plus focused source-level Folia/reuse guards. */
public final class AfkZoneRegressionSuite {

    private AfkZoneRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        cuboidNormalizationAndOverflow();
        claimFootprintAndAfkVolumeRemainDistinct();
        previewLeaseIdentityAndGeneration();
        intervalRemainderAndSingleCycleGate();
        globalAfkTimingBounds();
        weightedRewardBoundaries();
        strictRewardAndCommandValidation();
        tombstoneAndGlobalLimitIsolation();
        sharedSelectionAndFoliaGuards();
        System.out.println("AFK-zone regression suite passed.");
    }

    private static void cuboidNormalizationAndOverflow() {
        final UUID world = UUID.randomUUID();
        final CuboidSelectionService.Cuboid normalized = CuboidSelectionService.normalize(
                new CuboidSelectionService.Corner(world, "world", 10, 70, 5),
                new CuboidSelectionService.Corner(world, "world", -2, 60, 9));
        assertEquals(-2, normalized.minX(), "min X normalization");
        assertEquals(10, normalized.maxX(), "max X normalization");
        assertEquals(11L, normalized.height(), "inclusive height");
        assertEquals(65L, normalized.footprint(), "inclusive footprint");
        assertEquals(715L, normalized.volume(), "inclusive volume");

        final CuboidSelectionService.Cuboid extreme = new CuboidSelectionService.Cuboid(
                world, "world", Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, extreme.footprint(), "footprint must saturate instead of wrap");
        assertEquals(Long.MAX_VALUE, extreme.volume(), "volume must saturate instead of wrap");
        expectFailure(() -> CuboidSelectionService.normalize(
                new CuboidSelectionService.Corner(world, "world", 0, 0, 0),
                new CuboidSelectionService.Corner(UUID.randomUUID(), "other", 1, 1, 1)),
                "cross-world cuboid normalization");
    }

    private static void claimFootprintAndAfkVolumeRemainDistinct() {
        final CuboidSelectionService.Cuboid tallButSmallFootprint = new CuboidSelectionService.Cuboid(
                UUID.randomUUID(), "world", 0, -64, 0, 59, 319, 59);
        assertEquals(3_600L, tallButSmallFootprint.footprint(),
                "claim compatibility is governed by the historical XZ column cap");
        assertEquals(1_382_400L, tallButSmallFootprint.volume(),
                "the same valid claim selection may exceed the AFK 3D safety cap");
        assertTrue(tallButSmallFootprint.footprint() <= 6_400L,
                "the representative selection remains claim-valid");
        assertTrue(tallButSmallFootprint.volume() > 1_000_000L,
                "the representative selection remains AFK-volume-invalid");
    }

    private static void previewLeaseIdentityAndGeneration() {
        final IdentityTaskRegistry<String, String> registry = new IdentityTaskRegistry<>();
        final IdentityTaskRegistry.Installation<String> first = registry.install("player");
        first.current().attach("old-task");
        final IdentityTaskRegistry.Installation<String> second = registry.install("player");
        second.current().attach("new-task");

        assertSame(first.current(), second.previous(), "replacement must return the exact old lease");
        assertFalse(registry.remove("player", first.current()),
                "an old retirement callback must not evict the replacement preview");
        assertTrue(registry.isCurrent("player", second.current()),
                "the replacement preview must stay registered");
        assertEquals(List.of(second.current()), registry.invalidateAndDrain(),
                "reload must drain the current preview exactly once");
        assertFalse(registry.isCurrent("player", second.current()),
                "a pre-reload lease must be stale after generation invalidation");
        assertEquals(0, registry.size(), "reload must leave no preview session");
    }

    private static void intervalRemainderAndSingleCycleGate() {
        assertEquals(new AfkRewardClock.Advance(999L, false),
                AfkRewardClock.advance(0L, 999L, 1_000L), "not yet due");
        assertEquals(new AfkRewardClock.Advance(0L, true),
                AfkRewardClock.advance(999L, 1L, 1_000L), "exact interval");
        assertEquals(new AfkRewardClock.Advance(600L, true),
                AfkRewardClock.advance(100L, 3_500L, 1_000L),
                "late tick retains remainder but opens only one payout gate");
        assertEquals(new AfkRewardClock.Advance(Long.MAX_VALUE % 1_000L, true),
                AfkRewardClock.advance(900L, Long.MAX_VALUE, 1_000L),
                "overflow must saturate and remain deterministic");
        expectFailure(() -> AfkRewardClock.advance(-1L, 0L, 1_000L), "negative progress");
        expectFailure(() -> AfkRewardClock.advance(0L, 0L, 0L), "zero interval");
    }

    private static void globalAfkTimingBounds() {
        assertEquals(5L, AfkZoneCatalog.safeRefreshTicks(5L), "minimum refresh tick");
        assertEquals(AfkZoneCatalog.MAX_REFRESH_TICKS,
                AfkZoneCatalog.safeRefreshTicks(AfkZoneCatalog.MAX_REFRESH_TICKS),
                "maximum refresh tick");
        assertEquals(20L, AfkZoneCatalog.safeRefreshTicks(4L),
                "too-frequent refresh must use the safe default");
        assertEquals(20L, AfkZoneCatalog.safeRefreshTicks(Long.MAX_VALUE),
                "overflow-sized refresh must use the safe default");
        assertEquals(1L, AfkZoneCatalog.safeAfkAfterSeconds(1L), "minimum AFK timeout");
        assertEquals(AfkZoneCatalog.MAX_AFK_AFTER_SECONDS,
                AfkZoneCatalog.safeAfkAfterSeconds(AfkZoneCatalog.MAX_AFK_AFTER_SECONDS),
                "maximum AFK timeout");
        assertEquals(180L, AfkZoneCatalog.safeAfkAfterSeconds(0L),
                "zero AFK timeout must use the safe default");
        assertEquals(180L, AfkZoneCatalog.safeAfkAfterSeconds(Long.MAX_VALUE),
                "overflow-sized AFK timeout must use the safe default");
    }

    private static void weightedRewardBoundaries() {
        final AfkZoneCatalog.Reward first = new AfkZoneCatalog.Reward(
                AfkZoneCatalog.RewardType.CURRENCY, 80.0D, CurrencyType.NEUTRAL,
                2L, null, 0, null, "first");
        final AfkZoneCatalog.Reward second = new AfkZoneCatalog.Reward(
                AfkZoneCatalog.RewardType.CURRENCY, 20.0D, CurrencyType.NEUTRAL,
                1L, null, 0, null, "second");
        final CuboidSelectionService.Cuboid cuboid = new CuboidSelectionService.Cuboid(
                UUID.randomUUID(), "world", 0, 0, 0, 1, 1, 1);
        final AfkZoneCatalog.Zone zone = new AfkZoneCatalog.Zone(
                "zone", "Zone", true, cuboid, "", 1_000L, 1,
                List.of(first, second), 100.0D, "", "", "", "", "", "",
                BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        assertSame(first, AfkZoneCatalog.pick(zone, 0.0D), "lower bound");
        assertSame(first, AfkZoneCatalog.pick(zone, 0.799999D), "first weighted range");
        assertSame(second, AfkZoneCatalog.pick(zone, 0.8D), "exact weighted boundary");
        assertSame(second, AfkZoneCatalog.pick(zone, Math.nextDown(1.0D)), "upper bound");
        expectFailure(() -> AfkZoneCatalog.pick(zone, Double.NaN), "NaN roll");
        expectFailure(() -> AfkZoneCatalog.pick(zone, 1.0D), "unit roll upper bound");

        final Map<String, AfkZoneCatalog.Zone> insertion = new LinkedHashMap<>();
        insertion.put("first", zone);
        insertion.put("second", zone);
        final AfkZoneCatalog.Snapshot snapshot = new AfkZoneCatalog.Snapshot(insertion, Map.of());
        assertEquals(List.of("first", "second"), new ArrayList<>(snapshot.zones().keySet()),
                "overlapping zones must keep deterministic config order");
    }

    private static void strictRewardAndCommandValidation() {
        assertNull(AfkZoneCatalog.validateCommandTemplate(
                "give {player} bread 1"), "documented command placeholder");
        assertNull(AfkZoneCatalog.validateCommandTemplate(
                "say {uuid} {zone}"), "all documented placeholders");
        assertNotNull(AfkZoneCatalog.validateCommandTemplate(
                "give {PLAYER} bread 1"), "placeholder case must match runtime replacement exactly");
        assertNotNull(AfkZoneCatalog.validateCommandTemplate(
                "op {unknown}"), "unknown placeholder");
        assertNotNull(AfkZoneCatalog.validateCommandTemplate(
                "say }"), "orphan close brace");
        assertNotNull(AfkZoneCatalog.validateCommandTemplate(
                "say {{player}}"), "nested placeholder");
        assertNotNull(AfkZoneCatalog.validateCommandTemplate(
                "say {player}\nstop"), "control character");

        final List<String> problems = new ArrayList<>();
        final List<AfkZoneCatalog.Reward> invalid = AfkZoneCatalog.parseRewards(List.of(
                Map.of("type", "ITEM", "weight", 1.0D, "material", "BREAD", "amount", Long.MAX_VALUE),
                Map.of("type", "CURRENCY", "weight", Double.NaN, "currency", "NEUTRAL", "amount", 1),
                Map.of("type", "COMMAND", "weight", 1.0D, "command", "give {player} bread\nstop"),
                Map.of("type", "UNKNOWN", "weight", 1.0D)
        ), 1_000L, 64, problems);
        assertEquals(0, invalid.size(), "invalid rewards must not enter the catalog");
        assertEquals(4, problems.size(), "every invalid sibling must be reported");

        final List<String> excessiveProblems = new ArrayList<>();
        final List<AfkZoneCatalog.Reward> excessive = AfkZoneCatalog.parseRewards(List.of(
                Map.of("type", "CURRENCY", "weight", 1.0D, "currency", "NEUTRAL", "amount", 1_001L)
        ), AfkZoneCatalog.MAX_CONFIGURED_CURRENCY_REWARD, 64, excessiveProblems);
        assertEquals(0, excessive.size(), "a physical reward above the hard stack budget must be rejected");
        assertEquals(1, excessiveProblems.size(), "the hard physical reward limit must be reported");

        final List<String> validProblems = new ArrayList<>();
        final List<AfkZoneCatalog.Reward> valid = AfkZoneCatalog.parseRewards(List.of(
                Map.of("type", "CURRENCY", "weight", 1.0D, "currency", "NEUTRAL", "amount", 2),
                Map.of("type", "COMMAND", "weight", 1.0D,
                        "command", "give {player} bread 1", "description", "kenyér")
        ), 1_000L, 64, validProblems);
        assertEquals(2, valid.size(), "valid reward definitions");
        assertEquals(List.of(), validProblems, "valid reward definitions must stay clean");
    }

    private static void tombstoneAndGlobalLimitIsolation() {
        final YamlConfiguration tombstone = new YamlConfiguration();
        tombstone.set("afk.zones.removed.deleted", true);
        tombstone.set("afk.zones.removed.world", "missing-world");
        final AfkZoneCatalog.Snapshot removed = AfkZoneCatalog.load(tombstone);
        assertEquals(Map.of(), removed.zones(), "deleted zone must not resurrect from packaged defaults");
        assertEquals(Map.of(), removed.errors(), "deleted zone must not produce irrelevant config errors");

        final YamlConfiguration limits = new YamlConfiguration();
        limits.set("afk.max-zone-volume", -1);
        limits.set("afk.max-currency-reward", Double.POSITIVE_INFINITY);
        limits.set("afk.max-item-amount", 65);
        final AfkZoneCatalog.Snapshot invalidLimits = AfkZoneCatalog.load(limits);
        assertEquals(3, invalidLimits.errors().get("_global").size(),
                "unsafe global limits must be reported while safe defaults remain in force");
    }

    private static void sharedSelectionAndFoliaGuards() throws Exception {
        final String claim = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/ClaimManager.java"));
        final String afk = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/AfkManager.java"));
        final String command = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/commands/AfkZoneCommand.java"));
        final String selection = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/selection/CuboidSelectionService.java"));
        final String core = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/core/IceSMPCore.java"));
        final String cleanup = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/PlayerSessionCleanupListener.java"));
        final String config = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/ConfigManager.java"));

        assertContains(claim, "CuboidSelectionService selectionService", "claim reuses shared selection");
        assertContains(afk, "CuboidSelectionService selectionService", "AFK reuses shared selection");
        assertContains(cleanup, "selectionService", "selection participates in central cleanup");
        assertNotContains(claim, "class Selection", "claim-specific selection state removed");
        assertNotContains(command, "case \"pos1\"", "AFK must not add pos1 command");
        assertNotContains(command, "case \"pos2\"", "AFK must not add pos2 command");
        assertContains(selection, "resultInternal(playerId, Long.MAX_VALUE)",
                "claim-compatible selection must not inherit the AFK 3D volume cap");
        assertContains(command, "selectionService.result(player.getUniqueId(),",
                "AFK create/replace must retain the explicit 3D volume cap");
        assertContains(afk, "AfkRewardClock.advance", "single payout gate used by runtime");
        assertContains(core, "scheduleHud();\n        scheduleAfk();",
                "AFK driver must be enabled independently after the HUD scheduler");
        assertContains(core, "private synchronized void scheduleAfk()",
                "AFK driver must own an explicit cancel/reschedule lifecycle");
        assertContains(core, "afkManager.reloadZones();\n            scheduleAfk();",
                "config reload must apply refresh-period changes");
        final int hudStart = core.indexOf("private void scheduleHud()");
        final int afkStart = core.indexOf("private synchronized void scheduleAfk()");
        assertTrue(hudStart >= 0 && afkStart > hudStart, "scheduler methods must be discoverable");
        assertNotContains(core.substring(hudStart, afkStart), "afkManager.tick()",
                "HUD disable must not suppress the native AFK driver");
        assertContains(afk, "CatalogRevision", "queued player ticks carry an immutable config generation");
        assertContains(afk, "catalog != revision", "stale player/global callbacks must fail closed");
        assertContains(afk, "if (!dispatched)", "command reward success is conditional on dispatch");
        assertContains(afk, "clearDetachedState", "retired entity schedulers clear transient AFK state");
        assertContains(afk, ".getScheduler().run", "player work uses entity scheduler");
        assertContains(afk, "getGlobalRegionScheduler().run", "console rewards use global scheduler");
        assertContains(selection, "IdentityTaskRegistry", "preview lifecycle uses tested identity leases");
        assertContains(selection, "runAtFixedRate", "preview is scheduled through player scheduler");
        assertContains(selection, "if (scheduled == null)",
                "retired entity scheduler must not insert null task into the preview registry");
        assertContains(config, "YamlStore.saveAtomic", "multi-key zone overrides use shared atomic YAML write");
        assertContains(afk, ".deleted", "zone deletion uses a merged-config tombstone");

        final String scoped = afk + command + selection;
        assertNotContains(scoped, "Bukkit.getScheduler()", "legacy Bukkit scheduler forbidden");
        assertNotContains(scoped, "BukkitRunnable", "BukkitRunnable forbidden");
        assertNotContains(scoped, "new Thread", "raw thread forbidden");
        assertNotContains(scoped, "new Timer", "Timer forbidden");
    }

    private static void assertContains(final String value, final String needle, final String message) {
        if (!value.contains(needle)) {
            throw new AssertionError(message + ": missing " + needle);
        }
    }

    private static void assertNotContains(final String value, final String needle, final String message) {
        if (value.contains(needle)) {
            throw new AssertionError(message + ": forbidden " + needle);
        }
    }

    private static void assertNull(final Object value, final String message) {
        if (value != null) {
            throw new AssertionError(message + ": expected null, actual=" + value);
        }
    }

    private static void assertNotNull(final Object value, final String message) {
        if (value == null) {
            throw new AssertionError(message + ": expected non-null");
        }
    }

    private static void assertSame(final Object expected, final Object actual, final String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected same instance");
        }
    }

    private static void assertTrue(final boolean value, final String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(final boolean value, final String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(final Object expected, final Object actual, final String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void expectFailure(final Runnable action, final String message) {
        try {
            action.run();
        } catch (final IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected failure: " + message);
    }
}
