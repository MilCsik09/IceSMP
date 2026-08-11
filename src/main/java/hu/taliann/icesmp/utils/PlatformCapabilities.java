package hu.taliann.icesmp.utils;

import io.papermc.paper.ServerBuildInfo;
import net.kyori.adventure.key.Key;

/** Stable Paper/Folia capability checks shared by runtime presentation adapters. */
public final class PlatformCapabilities {

    private static final Key FOLIA_BRAND = Key.key("papermc", "folia");

    private PlatformCapabilities() {
    }

    /** Uses Paper's supported build identity API instead of implementation-class probing. */
    public static boolean isFolia() {
        return RuntimeIdentity.FOLIA;
    }

    /** Folia deliberately does not support the global Bukkit scoreboard API. */
    public static boolean supportsBukkitScoreboards() {
        return supportsBukkitScoreboards(isFolia());
    }

    static boolean supportsBukkitScoreboards(final boolean folia) {
        return !folia;
    }

    private static final class RuntimeIdentity {
        private static final boolean FOLIA = ServerBuildInfo.buildInfo().isBrandCompatible(FOLIA_BRAND);
    }
}
