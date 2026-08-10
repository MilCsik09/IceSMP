package hu.taliann.icesmp.classspec.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free contract audit for the optional BetterHud/PAPI projection. */
public final class BetterHudIntegrationRegressionSuite {
    private BetterHudIntegrationRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        immutableSnapshotContract();
        classMappingsAndAuthorityBoundary();
        placeholderAndFallbackContract();
        packagedLayoutContract();
        System.out.println("BetterHud integration regression suite passed.");
    }

    private static void immutableSnapshotContract() {
        final ArrayList<String> mutable = new ArrayList<>(List.of("Tempo 42"));
        final ClassHudState state = new ClassHudState("warrior", "berserker", "Berserker",
                "Tempo 42", "Vér 2/5", "harc", "túlpörgés", 2, 5, mutable);
        mutable.clear();
        check(state.mechanics().size() == 1 && state.charges() == 2 && state.chargesMax() == 5,
                "snapshot must defensively copy and retain generic fields");
        try {
            state.mechanics().add("mutation");
            throw new AssertionError("mechanics list must be immutable");
        } catch (final UnsupportedOperationException expected) { }
    }

    private static void classMappingsAndAuthorityBoundary() throws Exception {
        final String runtime = read("src/main/java/hu/taliann/icesmp/classspec/integration/BukkitClassSpecRuntimeAdapter.java");
        check(count(runtime, "new ClassHudStateAdapter(JobType.") == 13,
                "all 13 classes must map through the generic adapter");
        check(!runtime.contains("BetterHud") && !runtime.contains("PersistentDataContainer"),
                "class adapter must neither mutate BetterHud nor establish PDC authority");
    }

    private static void placeholderAndFallbackContract() throws Exception {
        final String papi = read("src/main/java/hu/taliann/icesmp/integration/IceSMPPlaceholders.java");
        final String hud = read("src/main/java/hu/taliann/icesmp/managers/HudManager.java");
        check(papi.contains("hudManager.snapshot(player.getUniqueId())")
                        && !papi.contains("getPlayer()") && !papi.contains("getPersistentDataContainer"),
                "async PAPI path must read only the immutable cache");
        for (final String key : List.of("class_spec", "class_mechanic_primary", "class_mechanic_secondary",
                "class_state", "class_proc", "class_charges", "class_charges_max")) {
            check(papi.contains("\"" + key + "\""), "missing generic placeholder: " + key);
        }
        check(hud.contains("!betterHudActive() && job != null")
                        && hud.contains("isPluginEnabled(\"PlaceholderAPI\")"),
                "native class row must be suppressed only when BetterHud+PAPI are ready");
    }

    private static void packagedLayoutContract() throws Exception {
        final String layout = read("deploy/betterhud/layouts/icesmp-class-layout.yml");
        final String hud = read("deploy/betterhud/huds/icesmp-class-hud.yml");
        check(layout.contains("papi:icesmp_resource_bar") && layout.contains("papi:icesmp_class_proc"),
                "layout must expose bar, mechanics and proc text");
        check(hud.contains("icesmp_class_layout"), "HUD package must reference generic layout");
    }

    private static String read(final String path) throws Exception { return Files.readString(Path.of(path)); }
    private static int count(final String text, final String needle) {
        int count = 0, at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) { count++; at += needle.length(); }
        return count;
    }
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
