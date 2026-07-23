package hu.taliann.icesmp;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import org.jspecify.annotations.NonNull;

/**
 * Loader-szint: a plugin classpath-jának összeállítása a betöltés előtt. Itt lehet
 * futásidőben Maven-függőségeket húzni (MavenLibraryResolver + RemoteRepository) a
 * jar-ba shadelés helyett — pl. adatbázis-driver, messaging-kliens. Jelenleg a plugin
 * minden függősége a Paper API-ból és a soft-depend hidakból jön (compileOnly +
 * join-classpath), ezért a builder üres; új runtime-lib igényekor IDE kerül a
 * resolver, NE a shadowJar-ba.
 */
@SuppressWarnings({"UnstableApiUsage", "unused"})
public final class IceSMPLoader implements PluginLoader {

    @Override
    public void classloader(final @NonNull PluginClasspathBuilder builder) {
        // Szándékosan üres — lásd az osztály-javadocot.
    }
}
