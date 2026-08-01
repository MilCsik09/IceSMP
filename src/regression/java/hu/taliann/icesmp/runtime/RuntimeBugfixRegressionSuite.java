package hu.taliann.icesmp.runtime;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Focused contracts for the 2026-08 pet/lore/corruption/spectator fixes. */
public final class RuntimeBugfixRegressionSuite {

    private RuntimeBugfixRegressionSuite() {
    }

    public static void main(final String[] args) throws IOException {
        petDoesNotInheritOwnerScoreboardTeam();
        loreUsageIsReadableAndNotAParsedTag();
        spectatorClicksReachTheCommandMenu();
        corruptionHasHardCapsAndLifecycleCleanup();
        System.out.println("Runtime bugfix regression suite passed.");
    }

    private static void petDoesNotInheritOwnerScoreboardTeam() throws IOException {
        final String source = read("src/main/java/hu/taliann/icesmp/managers/PetManager.java");
        check(source.contains("tameable.setOwner(null)"),
                "pet adoption must clear the vanilla owner/team inheritance");
        check(!source.contains("tameable.setOwner(player)"),
                "pet adoption still assigns the player's scoreboard team");
        check(source.contains("pet.setTarget(null)"),
                "untamed companion must clear vanilla AI targets");
    }

    private static void loreUsageIsReadableAndNotAParsedTag() throws IOException {
        final String source = read("src/main/java/hu/taliann/icesmp/commands/LoreCommand.java");
        check(source.contains("lore-usage-header")
                        && source.contains("lore-usage-factions")
                        && source.contains("lore-usage-places")
                        && source.contains("lore-usage-chronicles"),
                "lore help must be split into readable lines");
        check(!source.contains("<lang|fagy|menedek|"),
                "lore help still contains the monolithic angle-bracket topic list");
    }

    private static void spectatorClicksReachTheCommandMenu() throws IOException {
        final String source = read("src/main/java/hu/taliann/icesmp/listeners/CommandMenuListener.java");
        check(source.contains("priority = EventPriority.HIGHEST, ignoreCancelled = false"),
                "command menu still ignores already-cancelled spectator clicks");
        check(source.indexOf("event.setCancelled(true)")
                        < source.indexOf("holder.getOwnerUuid()"),
                "owned GUI must be frozen before action/owner dispatch");
    }

    private static void corruptionHasHardCapsAndLifecycleCleanup() throws IOException {
        final String source = read("src/main/java/hu/taliann/icesmp/managers/CorruptionManager.java");
        check(source.contains("pendingSpawns") && source.contains("effectiveMobCap()"),
                "corruption spawns lack pending reservations or a hard cap");
        check(source.contains("mob.setPersistent(false)")
                        && source.contains("mob.setRemoveWhenFarAway(true)")
                        && source.contains("mob-lifespan-seconds"),
                "corruption mobs can still accumulate across distance/restarts");
        check(source.contains("min-world-spawn-distance")
                        && source.contains("beginLegacyCleanup(world)"),
                "corruption lacks spawn exclusion or legacy cleanup");
        check(!source.contains("mob.setRemoveWhenFarAway(false)"),
                "corruption still forces permanent far-away mobs");

        final YamlConfiguration world = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/config/world.yml").toFile());
        check(world.getDouble("corruption.min-world-spawn-distance") >= 256.0D,
                "packaged corruption spawn exclusion is too small");
        check(world.getInt("corruption.mob-cap")
                        <= world.getInt("corruption.absolute-mob-cap"),
                "configured corruption cap exceeds its absolute ceiling");
        check(world.getInt("corruption.spawn-batch") == 1,
                "corruption must replenish one mob at a time");
        check(world.getLong("corruption.mob-lifespan-seconds") > 0L,
                "corruption mobs need a finite lifespan");
    }

    private static String read(final String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
