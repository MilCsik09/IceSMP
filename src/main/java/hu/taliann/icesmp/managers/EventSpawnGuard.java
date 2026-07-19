package hu.taliann.icesmp.managers;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;

/**
 * Shared spawn-placement rules for the mob-spawning world events (world boss,
 * invasion, wild hunt) — the same "events never land in someone's town" rule set
 * the meteor and treasure events already enforce individually: no claimed faction
 * territory, no player claim, no WorldGuard region (towns/spawn; the bridge fails
 * open). Also carries the shared event-mob hardening (no overworld zombification,
 * no daylight burn), so an event mob never silently turns into an untracked
 * vanilla mob or burns away before players reach it.
 *
 * <p>Constructed AFTER ClaimManager in the DI order and setter-injected into the
 * three event managers (which are constructed earlier); each caller treats a
 * missing guard as "no restriction" so construction order can never NPE.
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
     * Whether an event may NOT spawn at the location: claimed faction territory,
     * player claim or protected (WorldGuard) region. Config-toggleable; reads are
     * lock-free/concurrent structures, safe from any region thread.
     *
     * @param location the candidate spawn location
     * @return true when the location is protected and the event must skip it
     */
    public boolean isBlocked(final Location location) {
        if (!configManager.getBoolean("world-events.avoid-territory", true)) {
            return false;
        }
        return territoryManager.getTerritoryAt(location) != null
                || claimManager.getClaimAt(location) != null
                || hu.taliann.icesmp.integration.ProtectionBridge.isProtected(location);
    }

    /**
     * Whether the surface at the column is unfit for a ground-mob event spawn
     * (liquid top — an ocean/lava surface would drown or strand the mob).
     * Must be called on the region thread that owns the column.
     */
    public static boolean isUnsafeSurface(final World world, final int x, final int z) {
        return world.getBlockAt(x, world.getHighestBlockYAt(x, z), z).isLiquid();
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
