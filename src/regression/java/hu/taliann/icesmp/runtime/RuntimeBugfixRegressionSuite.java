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
        corruptionIsFullyConfigurable();
        resourcePackReloadOnlyResendsEffectiveChanges();
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

    private static void corruptionIsFullyConfigurable() throws IOException {
        final String source = read("src/main/java/hu/taliann/icesmp/managers/CorruptionManager.java");
        check(source.contains("pendingSpawns") && source.contains("configuredMobCap()"),
                "corruption spawns lost pending reservations or the configurable cap");
        check(source.contains("mobScalingManager.resolveLevel(location)")
                        && source.contains("corruption.mob-level-bonus"),
                "corruption mobs must use normal location level plus a configurable bonus");
        check(source.contains("configuredMobTypes()")
                        && source.contains("corruption.mob-types")
                        && !source.contains("EntityType[] POOL"),
                "corruption mob types are still hardcoded");
        check(source.contains("corruption.mob-glowing")
                        && source.contains("corruption.mob-persistent")
                        && source.contains("corruption.mob-remove-when-far-away")
                        && source.contains("corruption.mob-lifespan-seconds"),
                "corruption mob lifecycle/visuals are not fully configurable");
        check(!source.contains("absolute-mob-")
                        && !source.contains("absolute-radius-")
                        && !source.contains("absolute-spread-"),
                "corruption still contains an absolute hard ceiling");
        check(!source.contains("86_400L"),
                "corruption lifespan still has a hardcoded upper ceiling");

        final YamlConfiguration world = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/config/world.yml").toFile());
        check(!world.isSet("corruption.absolute-mob-cap")
                        && !world.isSet("corruption.absolute-mob-level")
                        && !world.isSet("corruption.absolute-radius-cap")
                        && !world.isSet("corruption.absolute-spread-per-night"),
                "packaged config still exposes absolute hard ceilings");
        check(world.isList("corruption.mob-types")
                        && !world.getStringList("corruption.mob-types").isEmpty(),
                "corruption mob pool must be configurable");
        check(world.isSet("corruption.mob-level-bonus"),
                "corruption normal-level bonus is missing from config");
        check(world.isSet("corruption.mob-cap")
                        && world.isSet("corruption.spawn-batch")
                        && world.isSet("corruption.radius-cap")
                        && world.isSet("corruption.spread-per-night")
                        && world.isSet("corruption.mob-lifespan-seconds"),
                "corruption balance controls are missing from config");
    }

    private static void resourcePackReloadOnlyResendsEffectiveChanges() throws IOException {
        final String listener = read("src/main/java/hu/taliann/icesmp/listeners/ResourcePackListener.java");
        check(listener.contains("sameRequest(previous, current)"),
                "resource-pack reload must compare the previous and current effective requests");
        check(listener.contains("applyTransition(player, previous, current)"),
                "changed resource-pack requests must be applied on each player's scheduler");
        check(listener.contains("player.removeResourcePack(previous.id())"),
                "disabling or replacing the resource-pack layer must remove its previous UUID");

        final String plugin = read("src/main/java/hu/taliann/icesmp/IceSMP.java");
        check(plugin.contains("resourcePackListener.resendCurrent()"),
                "hot plugin enable must force-send the already loaded snapshot");
        check(!plugin.contains("resourcePackListener.reloadAndResend();"),
                "plugin enable must not route through config change detection");
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
