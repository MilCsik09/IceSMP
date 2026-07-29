package hu.taliann.icesmp.managers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Dependency-free regression coverage for the retained global AFK product boundary. */
public final class AfkRegressionSuite {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    private AfkRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        trackerContract();
        commandRoutingContract();
        tablistOrderingContract();
        productionBoundaryContract();
        System.out.println("AFK regression suite passed.");
    }

    private static void trackerContract() {
        final GlobalAfkTracker tracker = new GlobalAfkTracker();
        final UUID player = UUID.fromString("00000000-0000-0000-0000-000000000001");

        expect(!tracker.isAfk(player, 1_000L, 2L), "unknown player must be active");
        tracker.recordActivity(player, 1_000L);
        expect(!tracker.isAfk(player, 2_999L, 2L), "idle threshold fired early");
        expect(tracker.isAfk(player, 3_000L, 2L), "exact idle threshold must be AFK");

        expect(!tracker.toggleAfk(player, 3_100L, 2L), "automatic AFK must toggle back to active");
        expect(!tracker.isAfk(player, 3_100L, 2L), "toggle-off must reset inactivity baseline");
        expect(!tracker.isAfk(player, 5_099L, 2L), "fresh baseline must not instantly expire");

        tracker.recordActivity(player, 5_100L);
        expect(tracker.toggleAfk(player, 5_101L, 2L), "active player must toggle manual AFK on");
        expect(tracker.isManuallyAfk(player), "manual flag not retained");
        expect(tracker.isAfk(player, 5_101L, 2L), "manual AFK not visible");
        expect(!tracker.toggleAfk(player, 5_200L, 2L), "second toggle must turn manual AFK off");
        expect(!tracker.isAfk(player, 5_200L, 2L), "manual toggle-off left AFK state behind");

        expect(tracker.toggleAfk(player, 5_300L, 2L), "manual AFK could not be re-enabled");
        tracker.recordActivity(player, 5_400L);
        expect(!tracker.isManuallyAfk(player), "activity did not clear manual AFK");
        expect(!tracker.isAfk(player, 5_400L, 2L), "activity did not return player to active");

        tracker.recordActivity(player, 10_000L);
        expect(!tracker.isAfk(player, 9_000L, 1L), "clock rollback must not create AFK");
        tracker.recordActivity(player, 0L);
        expect(!tracker.isAfk(player, 999L, 0L), "timeout lower clamp failed");
        expect(tracker.isAfk(player, 1_000L, 0L), "timeout lower boundary failed");
        expect(!tracker.isAfk(player, 31_535_999_999L, Long.MAX_VALUE),
                "timeout upper clamp fired early");
        expect(tracker.isAfk(player, 31_536_000_000L, Long.MAX_VALUE),
                "timeout upper clamp boundary failed");

        tracker.recordActivity(player, Long.MIN_VALUE);
        expect(tracker.isAfk(player, Long.MAX_VALUE, 1L), "elapsed overflow must saturate to AFK");
        tracker.clear(player);
        expect(!tracker.isAfk(player, Long.MAX_VALUE, 1L), "session cleanup left AFK state");
        tracker.recordActivity(null, 0L);
        expect(!tracker.toggleAfk(null, 0L, 1L), "null id toggle must stay active");
    }

    private static void commandRoutingContract() {
        expect(GlobalAfkTracker.isAfkToggleCommand("/afk"), "literal /afk not recognized");
        expect(GlobalAfkTracker.isAfkToggleCommand("  /AFK "), "case/whitespace /afk not recognized");
        expect(GlobalAfkTracker.isAfkToggleCommand("/icesmp:afk"), "namespaced /afk not recognized");
        expect(!GlobalAfkTracker.isAfkToggleCommand("/afks"), "similar command incorrectly exempt");
        expect(!GlobalAfkTracker.isAfkToggleCommand("/other:afk"), "foreign namespace incorrectly exempt");
        expect(!GlobalAfkTracker.isAfkToggleCommand("/msg afk"), "argument text incorrectly exempt");
        expect(!GlobalAfkTracker.isAfkToggleCommand(null), "null command incorrectly recognized");
    }

    private static void tablistOrderingContract() {
        final String active = TablistOrdering.key(0, "zzzzzzzzzzzzzzzz", false);
        final String afk = TablistOrdering.key(0, "alice", true);
        expect(active.compareTo(afk) < 0, "active player must sort before AFK player in one rank");
        expect(TablistOrdering.key(0, "alice", true)
                        .compareTo(TablistOrdering.key(1, "alice", false)) < 0,
                "rank priority must remain stronger than AFK state");
        expect(("nt" + active).length() <= 16, "scoreboard team name exceeds 16 characters");
    }

    private static void productionBoundaryContract() throws IOException {
        final String manager = read("src/main/java/hu/taliann/icesmp/managers/AfkManager.java");
        final String core = read("src/main/java/hu/taliann/icesmp/core/IceSMPCore.java");
        final String listener = read("src/main/java/hu/taliann/icesmp/listeners/AfkActivityListener.java");
        final String command = read("src/main/java/hu/taliann/icesmp/commands/AfkCommand.java");
        final String tablist = read("src/main/java/hu/taliann/icesmp/managers/TablistManager.java");
        final String dungeonListener = read("src/main/java/hu/taliann/icesmp/listeners/DungeonLootListener.java");
        final String dungeonService = read("src/main/java/hu/taliann/icesmp/managers/DungeonLootService.java");
        final String mobLoot = read("src/main/java/hu/taliann/icesmp/listeners/MobLootListener.java");
        final String mobKill = read("src/main/java/hu/taliann/icesmp/utils/MobKillUtil.java");
        final String worldBossListener = read(
                "src/main/java/hu/taliann/icesmp/listeners/WorldBossListener.java");
        final String worldBossManager = read(
                "src/main/java/hu/taliann/icesmp/managers/WorldBossManager.java");
        final String wildHuntManager = read(
                "src/main/java/hu/taliann/icesmp/managers/WildHuntManager.java");
        final String config = read("src/main/resources/config/afk.yml");
        final String messages = read("src/main/resources/messages/afk.yml");

        assertAbsent(manager, "CurrencyManager", "payOutTokens", "BossBar", "currentZone",
                "zoneProgress", "bossBars", "record Zone");
        assertAbsent(core, "afkTask", "afkManager.tick()", "\"afk.refresh-ticks\"");
        assertAbsent(config, "refresh-ticks:", "zones:", "reward:", "bossbar:");
        assertAbsent(messages, "afk-zone-enter", "afk-zone-leave", "afk-reward-received");

        expect(listener.contains("GlobalAfkTracker.isAfkToggleCommand"),
                "activity listener does not protect the toggle command");
        expect(command.contains("afkManager.toggleAfk"), "/afk does not use overall toggle");
        expect(core.contains("plugin, task -> tablistManager.tick()"),
                "native tablist schedule missing");
        expect(!core.substring(core.indexOf("private void scheduleHud()"),
                        core.indexOf("private void scheduleHealth()"))
                        .contains("if (!configManager.getBoolean(\"hud.enabled\""),
                "tablist schedule is still hidden behind the HUD switch");
        expect(tablist.contains("releaseNativeOutput()"), "tablist disable cleanup missing");
        expect(dungeonListener.contains("handleBossDeath(event.getEntity(), event.getDrops(), bossRewardsAllowed)"),
                "dungeon boss loot does not consume the shared reward gate");
        expect(dungeonService.contains("if (!allowRewards)"),
                "virtual dungeon chest can bypass the AFK gate");
        expect(dungeonService.contains("if (allowLoot)"), "dungeon lifecycle cannot suppress loot");
        expect(mobLoot.contains("killer != null && hu.taliann.icesmp.utils.MobKillUtil.isAfkRewardBlocked"),
                "boss-tier generic loot can bypass the shared reward gate");
        expect(mobKill.contains("isAfkRewardBlocked"), "shared reward gate lost global AFK wiring");
        expect(worldBossListener.contains("rewardsAllowed = !MobKillUtil.isAfkRewardBlocked"),
                "world-boss rewards do not consume the shared AFK gate");
        expect(worldBossManager.contains("if (!allowRewards)"),
                "world-boss lifecycle cannot close without paying rewards");
        expect(core.contains("wildHuntManager.setAfkManager(afkManager)"),
                "Wild Hunt AFK manager is not wired by the core");
        expect(wildHuntManager.contains("setAfkManager"),
                "Wild Hunt lost the AFK manager injection point");
        expect(wildHuntManager.contains("isAfkRewardBlocked"),
                "Wild Hunt personal loot can bypass the global AFK gate");
    }

    private static String read(final String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative));
    }

    private static void assertAbsent(final String source, final String... forbidden) {
        for (final String token : forbidden) {
            expect(!source.contains(token), "forbidden rewarded-zone token returned: " + token);
        }
    }

    private static void expect(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
