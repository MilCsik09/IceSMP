package hu.taliann.icesmp.resourcepack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/** Regressions for the additive Paper resource-pack layer, stable id and immutable hash URL. */
public final class ResourcePackRegressionSuite {

    private ResourcePackRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        listenerUsesAdditiveApiAndStableId();
        packagedConfigMatchesTheStableId();
        bundledMetadataUsesMatchingImmutableHash();
        System.out.println("Resource pack regression suite passed.");
    }

    private static void listenerUsesAdditiveApiAndStableId() throws Exception {
        final String source = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/ResourcePackListener.java"));
        check(source.contains("player.addResourcePack("),
                "IceSMP resource pack no longer uses Paper's additive API");
        check(!source.contains("player.setResourcePack("),
                "IceSMP must not overwrite the native/server or another plugin's pack layer");
        check(source.contains("UUID.fromString(\"7c847f1e-d942-3c8f-bd46-5c43bb1a3e67\")"),
                "stable IceSMP pack UUID changed unexpectedly");
    }

    private static void packagedConfigMatchesTheStableId() throws Exception {
        final String config = Files.readString(Path.of("src/main/resources/config.yml"));
        final UUID configured = UUID.fromString(extractQuotedValue(config, "  id:"));
        check(configured.equals(UUID.fromString("7c847f1e-d942-3c8f-bd46-5c43bb1a3e67")),
                "resource-pack.id no longer matches the stable IceSMP layer id");
    }

    private static void bundledMetadataUsesMatchingImmutableHash() throws Exception {
        final Properties metadata = new Properties();
        try (var reader = Files.newBufferedReader(
                Path.of("src/main/resources/resource-pack.properties"))) {
            metadata.load(reader);
        }
        final String url = metadata.getProperty("url", "");
        final String sha1 = metadata.getProperty("sha1", "");
        check(sha1.matches("[0-9a-f]{40}"), "bundled resource-pack SHA-1 is invalid");
        check(url.startsWith("https://assets.icesmp.taliann.dev/resource-packs/icesmp-")
                        && url.endsWith(sha1 + ".zip"),
                "resource-pack URL is not the immutable object matching the bundled SHA-1");
    }

    private static String extractQuotedValue(final String source, final String prefix) {
        for (final String line : source.split("\\R")) {
            if (!line.startsWith(prefix)) {
                continue;
            }
            final int first = line.indexOf('"');
            final int last = line.lastIndexOf('"');
            if (first >= 0 && last > first) {
                return line.substring(first + 1, last);
            }
        }
        throw new AssertionError("missing config line: " + prefix);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
