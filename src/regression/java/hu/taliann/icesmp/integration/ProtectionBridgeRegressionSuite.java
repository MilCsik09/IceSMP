package hu.taliann.icesmp.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free contract checks for the optional WorldGuard/WorldEdit bridge. */
public final class ProtectionBridgeRegressionSuite {

    private ProtectionBridgeRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        run();
    }

    public static void run() throws IOException {
        final String bridge = source(
                "src/main/java/hu/taliann/icesmp/integration/ProtectionBridge.java");
        final String entrypoint = source(
                "src/main/java/hu/taliann/icesmp/IceSMP.java");
        final String claims = source(
                "src/main/java/hu/taliann/icesmp/managers/ClaimManager.java");
        final String manifest = source("src/main/resources/paper-plugin.yml");

        check(!bridge.contains("com.sk89q.worldguard.bukkit.BukkitAdapter"),
                "a nem létező WorldGuard BukkitAdapter FQCN visszakerült");
        check(count(bridge, "com.sk89q.worldedit.bukkit.BukkitAdapter") == 1,
                "a WorldEdit BukkitAdapter FQCN nincs egyetlen közös konstansba zárva");
        check(bridge.contains("Class.forName(BUKKIT_ADAPTER_CLASS)"),
                "a közös BukkitAdapter konstans nincs a tényleges Class.forName feloldásba kötve");
        check(bridge.contains("bukkitAdapter.getMethod(\"adapt\", Location.class)")
                        && bridge.contains("bukkitAdapter.getMethod(\"adapt\", World.class)"),
                "a pont- és box-lekérdezés nem ugyanazt a helyes adaptert használja");

        final String worldEditDependency = between(
                manifest, "    WorldEdit:\n", "    WorldGuard:\n");
        check(worldEditDependency.contains("load: BEFORE")
                        && worldEditDependency.contains("required: false")
                        && worldEditDependency.contains("join-classpath: true"),
                "a WorldEdit/FAWE opcionális Paper-classpath függőség hiányos");

        final String managerNull = between(
                bridge, "            if (manager == null) {\n",
                "            final Object min =");
        check(managerNull.contains("return null;")
                        && !managerNull.contains("Boolean.FALSE"),
                "a hiányzó/hibás region-manager nem UNKNOWN/fail-closed");
        check(bridge.contains("world.getMaxHeight() - 1"),
                "a WorldGuard cuboid exkluzív Bukkit max-height értéket kap");

        final String acquire = between(
                bridge, "        final org.bukkit.plugin.Plugin worldGuard =\n",
                "        final Chain resolved = resolveChain();\n");
        check(acquire.contains("if (worldGuard == null)")
                        && acquire.contains("absent = true;")
                        && acquire.contains("if (!worldGuard.isEnabled())")
                        && acquire.contains("absent = false;"),
                "a valóban hiányzó és a letiltott WorldGuard nincs szétválasztva");

        check(bridge.contains("failureDescription(throwable)")
                        && bridge.contains("Bukkit.getLogger().log(Level.WARNING")
                        && bridge.contains("root.getMessage()"),
                "a WorldGuard-feloldási hiba nem őrzi meg az okot és a stack trace-t");
        check(entrypoint.contains("getPlugin(\"WorldGuard\") != null")
                        && entrypoint.contains("ProtectionBridge.isHealthy()"),
                "az induláskori WorldGuard bridge health-check hiányzik");
        check(bridge.contains("return Boolean.TRUE.equals(queryProtected(location));"),
                "az event-oldali WorldGuard UNKNOWN válasz nem fail-open");
        check(claims.contains("if (overlap == null || overlap)"),
                "a claim WorldGuard UNKNOWN válasza nem fail-closed");

        System.out.println("WorldGuard bridge regression suite passed.");
    }

    private static String source(final String path) throws IOException {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }

    private static String between(final String source, final String start,
                                  final String end) {
        final int from = source.indexOf(start);
        final int to = from < 0 ? -1 : source.indexOf(end, from + start.length());
        check(from >= 0 && to > from,
                "nem található regressziós forrásszakasz: " + start.trim());
        return source.substring(from, to);
    }

    private static int count(final String source, final String needle) {
        int occurrences = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            occurrences++;
            offset += needle.length();
        }
        return occurrences;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
