package hu.taliann.icesmp.managers;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;

/**
 * Shared spawn-placement rules for the world events: the "events never land in
 * someone's town" rule set, configurable per event AND per protection type via
 * the {@code world-events.spawn-rules.<event>.<protection>} matrix (all default
 * true), under the {@code world-events.avoid-territory} master switch. The
 * protection types: {@code territory} = claimed faction territory, {@code claim}
 * = player claim, {@code region} = WorldGuard region (towns/spawn; the bridge
 * fails open), {@code water} = liquid surface (would drown/strand a ground mob).
 * Also carries the shared event-mob hardening (no overworld zombification, no
 * daylight burn), so an event mob never silently turns into an untracked vanilla
 * mob or burns away before players reach it.
 *
 * <p>Constructed after ClaimManager in the DI order; the event managers built
 * earlier receive it via setter and treat a missing guard as "no restriction"
 * so construction order can never NPE.
 */
public final class EventSpawnGuard {

    private final ConfigManager configManager;
    private final TerritoryManager territoryManager;
    private final ClaimManager claimManager;

    public EventSpawnGuard(final ConfigManager configManager, final TerritoryManager territoryManager,
                           final ClaimManager claimManager) {
        this.configManager = configManager;
        this.territoryManager = territoryManager;
        this.claimManager = claimManager;
    }

    /**
     * Whether the event may NOT spawn at the location per its protection rules
     * (territory / claim / region). Reads are lock-free/concurrent structures,
     * safe from any region thread.
     *
     * @param eventKey the event's spawn-rules key (e.g. "world-boss", "invasion")
     * @param location the candidate spawn location
     * @return true when a protection rule blocks the location
     */
    public boolean isBlocked(final String eventKey, final Location location) {
        if (!configManager.getBoolean("world-events.avoid-territory", true)) {
            return false;
        }
        if (rule(eventKey, "territory") && territoryManager.getTerritoryAt(location) != null) {
            return true;
        }
        if (rule(eventKey, "claim") && claimManager.getClaimAt(location) != null) {
            return true;
        }
        return rule(eventKey, "region") && hu.taliann.icesmp.integration.ProtectionBridge.isProtected(location);
    }

    /**
     * Whether the surface at the column is unfit for the event's ground spawn
     * (liquid top; per-event {@code water} rule). Must be called on the region
     * thread that owns the column.
     */
    public boolean isUnsafeSurface(final String eventKey, final World world, final int x, final int z) {
        return rule(eventKey, "water")
                && world.getBlockAt(x, world.getHighestBlockYAt(x, z), z).isLiquid();
    }

    /** One cell of the per-event spawn-rules matrix (default: every protection on). */
    private boolean rule(final String eventKey, final String protection) {
        return configManager.getBoolean("world-events.spawn-rules." + eventKey + "." + protection, true);
    }

    /**
     * Hardens a freshly spawned event mob: piglin/hoglin types never zombify in
     * the overworld (a conversion would spawn a NEW entity and orphan the event's
     * PDC-tag/tracking), and skeleton/zombie/phantom types don't burn in daylight
     * (a daytime event would otherwise kill its own mobs). Call on the spawning
     * region thread right after the spawn.
     */
    public static void prepare(final Mob mob) {
        if (mob instanceof org.bukkit.entity.PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
        } else if (mob instanceof org.bukkit.entity.Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        } else if (mob instanceof org.bukkit.entity.AbstractSkeleton skeleton) {
            skeleton.setShouldBurnInDay(false);
        } else if (mob instanceof org.bukkit.entity.Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        } else if (mob instanceof org.bukkit.entity.Phantom phantom) {
            phantom.setShouldBurnInDay(false);
        }
    }
}
