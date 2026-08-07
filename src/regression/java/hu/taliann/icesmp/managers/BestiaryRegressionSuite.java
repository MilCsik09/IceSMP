package hu.taliann.icesmp.managers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * B21 — bestiárium-mélység regressziók: nevezők, boss-archetípus lajstrom-kánon,
 * ritka-variáns entry-formátum és a kill-rögzítés forrás-szerződései (a boss a
 * PDC-archetípust jegyzi, a mob-kill a faj-kulccsal EGY commitban számol).
 */
public final class BestiaryRegressionSuite {
    private static int assertions;

    private BestiaryRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        monsterDenominatorIsStableAndSorted();
        bossRosterExposesCanonicalIdsAndPlainNames();
        killRecordingSourceContracts();
        guiSourceContracts();
        System.out.println("Bestiary regression suite passed. assertions=" + assertions);
    }

    private static void monsterDenominatorIsStableAndSorted() {
        final List<org.bukkit.entity.EntityType> types = BestiaryManager.knownMonsterTypes();
        check(!types.isEmpty(), "monster denominator must not be empty");
        check(types.contains(org.bukkit.entity.EntityType.ZOMBIE)
                        && types.contains(org.bukkit.entity.EntityType.SKELETON),
                "canonical monsters present in denominator");
        check(!types.contains(org.bukkit.entity.EntityType.COW),
                "peaceful mobs stay outside the denominator");
        for (int index = 1; index < types.size(); index++) {
            check(types.get(index - 1).name().compareTo(types.get(index).name()) < 0,
                    "denominator is name-sorted and duplicate-free");
        }
    }

    /**
     * A BossArchetype enum PotionEffectType-mezői registry-t igényelnek, ezért a roster
     * headless csak FORRÁSBÓL olvasható; a registry-mentes plainArchetypeName viszont a
     * valódi metóduson fut.
     */
    private static void bossRosterExposesCanonicalIdsAndPlainNames() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/WorldBossManager.java"));
        final java.util.LinkedHashMap<String, String> roster = new java.util.LinkedHashMap<>();
        final java.util.regex.Matcher rows = java.util.regex.Pattern.compile(
                "([A-Z_]+)\\(EntityType\\.[A-Z_]+,\\s*\"([^\"]+)\"").matcher(source);
        while (rows.find()) {
            roster.put(rows.group(1).toLowerCase(java.util.Locale.ROOT), rows.group(2));
        }
        check(roster.size() >= 10, "boss roster exposes every archetype");
        check(roster.containsKey("ring_warden") && roster.containsKey("bone_king"),
                "archetype ids are lowercase enum names");
        check(WorldBossManager.plainArchetypeName(roster.get("ring_warden"))
                .equals("A Gyűrűk Őre"), "plain name strips codes, symbols and the tag");
        check(WorldBossManager.plainArchetypeName(roster.get("piglin_warlord"))
                .equals("Pokoli Hadúr"), "plain name canonical for the last roster row");
        check(source.contains("archetypeDisplayNames()"),
                "runtime roster accessor exists for the GUI/PAPI denominators");
    }

    private static void killRecordingSourceContracts() throws Exception {
        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/BestiaryListener.java"));
        check(listener.contains("worldBossManager.archetypeId(event.getEntity())"),
                "boss kills record the PDC archetype, not the vanilla EntityType");
        check(listener.contains("BestiaryManager.entryId(event.getEntity())"),
                "mob kills use the shared canonical entry id");
        final String combat = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/StatsCombatListener.java"));
        check(combat.contains("recordMobKill(killer.getUniqueId(),")
                        && combat.contains("BestiaryManager.entryId"),
                "species counter rides the existing per-kill statistics commit");
        final String store = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileStatisticsStore.java"));
        final int mutate = store.indexOf("recordMobKill");
        final int merge = store.indexOf("lifetime.merge(MOB_KILLS", mutate);
        final int species = store.indexOf("BESTIARY_KILL_PREFIX + species", mutate);
        check(mutate > 0 && merge > mutate && species > merge,
                "total and species keys mutate inside one section commit");
    }

    private static void guiSourceContracts() throws Exception {
        final String gui = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/gui/BestiaryGUI.java"));
        check(gui.contains("GRAY_STAINED_GLASS_PANE") && gui.contains("???"),
                "unknown entries render as ??? silhouettes");
        check(gui.contains("PAGE_SIZE = 45"), "category view paginates at 45 entries");
        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/BestiaryListener.java"));
        check(listener.contains("event.setCancelled(true)"),
                "bestiary GUI stays read-only");
        final String papi = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/integration/IceSMPPlaceholders.java"));
        check(papi.contains("bestiary_") && papi.contains("_total"),
                "PAPI exposes bestiary counters and totals");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
