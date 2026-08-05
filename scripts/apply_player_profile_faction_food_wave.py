#!/usr/bin/env python3
"""Move faction home-food timestamps from player PDC to PlayerProfile faction state."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def write_store() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileFactionFoodStore.java"
    path.write_text('''package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSectionExtensions;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.FactionSection;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** CAS-backed faction-food duty timestamp and faction binding. */
public final class PlayerProfileFactionFoodStore {
    private static final String LAST_KEY = "food-duty.last-home-food";
    private static final String FACTION_KEY = "food-duty.faction";

    public FoodState read(final UUID playerId) {
        final FactionSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.FACTION, FactionSection.class);
        final Object rawLast = section.extensions().get(LAST_KEY);
        final long last;
        if (rawLast == null) last = -1L;
        else if (rawLast instanceof Number number) last = number.longValue();
        else throw new IllegalStateException("Invalid food-duty timestamp type");
        if (last < -1L) throw new IllegalStateException("Negative food-duty timestamp");
        final Object rawFaction = section.extensions().get(FACTION_KEY);
        final String faction;
        if (rawFaction == null) faction = "";
        else if (rawFaction instanceof String value) faction = normalizeFaction(value);
        else throw new IllegalStateException("Invalid food-duty faction type");
        return new FoodState(last, faction);
    }

    public CompletionStage<FoodState> record(final UUID playerId,
                                             final String rawFaction,
                                             final long timestamp) {
        final String faction = normalizeFaction(rawFaction);
        if (timestamp < 0L) throw new IllegalArgumentException("negative food-duty timestamp");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.FACTION, FactionSection.class, current -> {
                    final FoodState existing = readCurrent(current);
                    final FoodState result = new FoodState(timestamp, faction);
                    if (existing.equals(result)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(result);
                    }
                    FactionSection next = PlayerProfileSectionExtensions.put(current, LAST_KEY, timestamp);
                    next = PlayerProfileSectionExtensions.put(next, FACTION_KEY, faction);
                    return PlayerProfileService.ConditionalMutation.changed(next, result);
                });
    }

    private static FoodState readCurrent(final FactionSection section) {
        final Object rawLast = section.extensions().get(LAST_KEY);
        final long last = rawLast == null ? -1L : ((Number) rawLast).longValue();
        final Object rawFaction = section.extensions().get(FACTION_KEY);
        final String faction = rawFaction == null ? "" : normalizeFaction((String) rawFaction);
        return new FoodState(last, faction);
    }

    private static String normalizeFaction(final String raw) {
        final String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!value.isEmpty() && !java.util.Set.of("RED", "BLUE", "NEUTRAL", "DARK").contains(value)) {
            throw new IllegalArgumentException("invalid faction food binding: " + raw);
        }
        return value;
    }

    public record FoodState(long lastHomeFoodAt, String faction) {
        public FoodState {
            if (lastHomeFoodAt < -1L) throw new IllegalArgumentException("invalid timestamp");
            faction = normalizeFaction(faction);
        }
    }
}
''', encoding="utf-8")


def patch_listener() -> None:
    path = ROOT / "src/main/java/hu/taliann/icesmp/listeners/FactionFoodListener.java"
    text = path.read_text(encoding="utf-8")
    if "PlayerProfileFactionFoodStore foodStore" in text and "faction_food_ts" not in text:
        return
    text = text.replace('''    /** Az utolsó "hazai étel" fogyasztás időbélyege (player-PDC — nem szivárgó map). */
    private final NamespacedKey lastHomeFoodKey;
    /** Melyik frakció konyhájához tartozik az időbélyeg — frakcióváltásnál a régi bélyeg
     * érvénytelen (különben a váltó azonnali debuffot VAGY jogtalan kedvezményt kapna). */
    private final NamespacedKey foodFactionKey;
''', '''    /** Durable food-duty state; runtime reads are PlayerProfile cache reads. */
    private final hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionFoodStore
            foodStore = new hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionFoodStore();
''')
    text = text.replace('''        this.lastHomeFoodKey = new NamespacedKey(plugin, "faction_food_ts");
        this.foodFactionKey = new NamespacedKey(plugin, "faction_food_faction");
''', '')
    text = text.replace('''        if (homeFood) {
            player.getPersistentDataContainer().set(lastHomeFoodKey, PersistentDataType.LONG, System.currentTimeMillis());
            player.getPersistentDataContainer().set(foodFactionKey, PersistentDataType.STRING, faction.name());
        }
''', '''        if (homeFood) {
            foodStore.record(player.getUniqueId(), faction.name(), System.currentTimeMillis())
                    .exceptionally(failure -> {
                        plugin.getLogger().severe("PlayerProfile faction-food timestamp commit failed for "
                                + player.getUniqueId() + ": " + failure.getMessage());
                        return null;
                    });
        }
''')
    old = '''                final long callbackNow = System.currentTimeMillis();
                final Long last = player.getPersistentDataContainer().get(
                        lastHomeFoodKey, PersistentDataType.LONG);
                final String tsFaction = player.getPersistentDataContainer().get(
                        foodFactionKey, PersistentDataType.STRING);
                if (last == null || last < 0L || last > callbackNow
                        || !faction.name().equals(tsFaction)) {
                    // Új játékos, sérült/jövőbeli bélyeg VAGY frissen váltó: a türelmi idő
                    // újraindul — először csak jegyezzük az időt, debuff nélkül.
                    player.getPersistentDataContainer().set(
                            lastHomeFoodKey, PersistentDataType.LONG, callbackNow);
                    player.getPersistentDataContainer().set(
                            foodFactionKey, PersistentDataType.STRING, faction.name());
                    return;
                }
                if (!FactionFoodPolicy.hasGraceElapsed(callbackNow, last, graceMillis)) {
'''
    new = '''                final long callbackNow = System.currentTimeMillis();
                final hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionFoodStore.FoodState
                        foodState = foodStore.read(player.getUniqueId());
                final long last = foodState.lastHomeFoodAt();
                if (last < 0L || last > callbackNow
                        || !faction.name().equals(foodState.faction())) {
                    // Új játékos, sérült/jövőbeli bélyeg VAGY frissen váltó: a türelmi idő
                    // újraindul — először csak tartósan jegyezzük az időt, debuff nélkül.
                    foodStore.record(player.getUniqueId(), faction.name(), callbackNow)
                            .exceptionally(failure -> {
                                plugin.getLogger().severe("PlayerProfile faction-food grace commit failed for "
                                        + player.getUniqueId() + ": " + failure.getMessage());
                                return null;
                            });
                    return;
                }
                if (!FactionFoodPolicy.hasGraceElapsed(callbackNow, last, graceMillis)) {
'''
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError(f"FactionFood duty block count={text.count(old)}")
        text = text.replace(old, new, 1)
    text = text.replace('a játékos saját régió-szálán fut (PDC-írás/buff ott',
                        'a játékos saját régió-szálán fut (buff és runtime művelet ott')
    path.write_text(text, encoding="utf-8")


def write_regression() -> None:
    path = ROOT / "src/regression/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileFactionFoodStoreRegressionSuite.java"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('''package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

/** Faction-food timestamp binding and restart durability regressions. */
public final class PlayerProfileFactionFoodStoreRegressionSuite {
    private static int assertions;
    private PlayerProfileFactionFoodStoreRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-food-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000001088");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileFactionFoodStore store = new PlayerProfileFactionFoodStore();
            check(store.read(player).lastHomeFoodAt() == -1L, "greenfield timestamp missing");
            final var first = store.record(player, "blue", 1000L).toCompletableFuture().join();
            check(first.lastHomeFoodAt() == 1000L && "BLUE".equals(first.faction()),
                    "timestamp and faction normalized");
            final long revision = repository.cached(player).orElseThrow().faction().revision();
            store.record(player, "BLUE", 1000L).toCompletableFuture().join();
            check(repository.cached(player).orElseThrow().faction().revision() == revision,
                    "identical food state is no-op");
            store.record(player, "RED", 2000L).toCompletableFuture().join();
            repository.invalidate(player);
            final var durable = store.read(player);
            check(durable.lastHomeFoodAt() == 2000L, "timestamp restart durable");
            check("RED".equals(durable.faction()), "faction restart durable");
            expect(IllegalArgumentException.class, () -> store.record(player, "INVALID", 3L));
            check(service.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(),
                    "repository drained");
        } finally {
            authority.uninstall();
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (final Exception ignored) { }
                });
            }
        }
        System.out.println("PlayerProfile faction food regression suite passed. assertions=" + assertions);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void expect(final Class<? extends Throwable> expected, final Throwing action) {
        assertions++;
        try { action.run(); throw new AssertionError("Expected " + expected.getSimpleName()); }
        catch (final Throwable failure) {
            if (!expected.isInstance(failure)) throw new AssertionError(failure);
        }
    }
    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
''', encoding="utf-8")


def patch_gradle() -> None:
    path = ROOT / "build.gradle.kts"
    text = path.read_text(encoding="utf-8")
    task = '''val playerProfileFactionFoodRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs PlayerProfile faction-food timestamp regressions."
    dependsOn(tasks.named(regressionTest.classesTaskName))
    classpath = regressionTest.runtimeClasspath
    mainClass.set("hu.taliann.icesmp.playerprofile.application.PlayerProfileFactionFoodStoreRegressionSuite")
}

'''
    anchor = 'val playerProfileYamlRegressionTest by tasks.registering(JavaExec::class) {'
    if task not in text:
        if text.count(anchor) != 1:
            raise RuntimeError(f"food task anchor count={text.count(anchor)}")
        text = text.replace(anchor, task + anchor, 1)
    old = '''        playerProfileRepositoryEnumerationRegressionTest, playerProfileStatisticsRegressionTest,
        playerProfileYamlRegressionTest, playerProfileTransactionRegressionTest,
'''
    new = '''        playerProfileRepositoryEnumerationRegressionTest, playerProfileStatisticsRegressionTest,
        playerProfileFactionFoodRegressionTest, playerProfileYamlRegressionTest,
        playerProfileTransactionRegressionTest,
'''
    if new not in text:
        if text.count(old) != 1:
            raise RuntimeError(f"food dependency anchor count={text.count(old)}")
        text = text.replace(old, new, 1)
    path.write_text(text, encoding="utf-8")


def main() -> int:
    write_store()
    patch_listener()
    write_regression()
    patch_gradle()
    print("PlayerProfile faction-food authority wave applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
