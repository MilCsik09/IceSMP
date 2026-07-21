package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lore-ambiencia: a Kitaszítottak földje tele van élőholtakkal — a Néma
 * Királynő népe otthon jár. A manager a DARK territóriumokban tart fenn egy
 * magas szintű undead-populációt (hatókör configból: csak a capital VAGY az
 * összes DARK territórium): a mobok NEM égnek el a napon
 * (EventSpawnGuard.prepare), szinttel spawnolnak (MobScalingManager.forceLevel),
 * és korlátos élettartamúak — a populáció a tick-ből töltődik újra.
 * (A lore-név — pl. Thanaopolis — csak configban/szövegben él, kódban nem.)
 *
 * <p>A DARK játékosokat a meglévő frakció-passzíva miatt békén hagyják — a
 * betolakodónak viszont Thanaopolis maga a rémálom. Minden kulcs élő config
 * (dark-undead.*). Folia: a spawn a cél-helyszín régió-schedulerén fut;
 * a populáció-követés konkurrens map (uuid -> lejárat), a halál-listener és a
 * lifespan-remove is takarít.
 */
public final class DarkUndeadAmbienceManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TerritoryManager territoryManager;
    private final MobScalingManager mobScalingManager;
    private final EventSpawnGuard spawnGuard;
    private final org.bukkit.NamespacedKey markKey;
    /** spawnolt undead -> várható lejárat (élettartam-alapú prune, régió-érintés nélkül). */
    private final Map<UUID, Long> population = new ConcurrentHashMap<>();
    private volatile long nextSpawnAt;

    public DarkUndeadAmbienceManager(final JavaPlugin plugin, final ConfigManager configManager,
                                   final TerritoryManager territoryManager,
                                   final MobScalingManager mobScalingManager,
                                   final EventSpawnGuard spawnGuard) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.territoryManager = territoryManager;
        this.mobScalingManager = mobScalingManager;
        this.spawnGuard = spawnGuard;
        this.markKey = new org.bukkit.NamespacedKey(plugin, "dark_undead");
    }

    public boolean isMarked(final org.bukkit.entity.Entity entity) {
        return entity.getPersistentDataContainer().has(markKey,
                org.bukkit.persistence.PersistentDataType.BYTE);
    }

    /** A halál-listener hívja: kiesett a populációból. */
    public void onDeath(final UUID entityId) {
        population.remove(entityId);
    }

    /** A world-events tick hívja (global scheduler). */
    public void tick() {
        if (!configManager.getBoolean("dark-undead.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < nextSpawnAt) {
            return;
        }
        nextSpawnAt = now + Math.max(5, configManager.getInt("dark-undead.spawn-interval-seconds", 30)) * 1000L;
        // Lejárt (élettartamuk végén magától eltűnő) példányok kivezetése a számlálóból.
        population.entrySet().removeIf(entry -> entry.getValue() < now);

        final java.util.List<Territory> targets = targetTerritories();
        if (targets.isEmpty()) {
            return;
        }
        final int maxPopulation = Math.max(1, configManager.getInt("dark-undead.max-population", 24));
        final int batch = Math.min(
                Math.max(1, configManager.getInt("dark-undead.spawn-batch", 4)),
                maxPopulation - population.size());
        if (batch <= 0) {
            return;
        }
        final List<String> types = configManager.getStringList("dark-undead.types");
        final List<String> pool = types.isEmpty()
                ? List.of("ZOMBIE", "SKELETON", "HUSK", "STRAY", "WITHER_SKELETON") : types;
        final int minLevel = Math.max(1, configManager.getInt("dark-undead.min-level", 4));
        final int maxLevel = Math.max(minLevel, configManager.getInt("dark-undead.max-level", 7));
        final long lifespanMillis = Math.max(60, configManager.getInt("dark-undead.lifespan-seconds", 600)) * 1000L;

        for (int index = 0; index < batch; index++) {
            // Cél-territórium sorsolása, majd véletlen pont a körén belül
            // (a spawn a helyszín régió-szálán fut).
            final Territory territory = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
            final World world = Bukkit.getWorld(territory.world());
            if (world == null) {
                continue;
            }
            final double angle = ThreadLocalRandom.current().nextDouble(2.0D * Math.PI);
            final double distance = ThreadLocalRandom.current().nextDouble() * Math.max(4, territory.radius() - 4);
            final int x = territory.x() + (int) Math.round(Math.cos(angle) * distance);
            final int z = territory.z() + (int) Math.round(Math.sin(angle) * distance);
            final EntityType type;
            try {
                type = EntityType.valueOf(pool.get(ThreadLocalRandom.current().nextInt(pool.size()))
                        .toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException exception) {
                continue;
            }
            final Location target = new Location(world, x + 0.5D, 0.0D, z + 0.5D);
            world.getRegionScheduler().run(plugin, target, task -> {
                final int y = world.getHighestBlockYAt(x, z) + 1;
                target.setY(y);
                // Spawn-rules mátrix: a DARK territórium maga NEM tiltott (ez a lényeg),
                // de a claimek/WG-régiók belseje és a víz védve — mint minden spawnernél.
                if (spawnGuard.isBlocked("dark-undead", target)
                        || spawnGuard.isUnsafeSurface("dark-undead", world, x, z)) {
                    return;
                }
                final org.bukkit.entity.Entity spawned = world.spawnEntity(target, type);
                if (!(spawned instanceof Mob mob)) {
                    spawned.remove();
                    return;
                }
                EventSpawnGuard.prepare(mob); // napfény-égés/zombisodás ellen — Thanaopolis népe nappal is jár
                mob.getPersistentDataContainer().set(markKey,
                        org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                mob.setPersistent(false);
                if (mobScalingManager != null) {
                    mobScalingManager.forceLevel(mob,
                            ThreadLocalRandom.current().nextInt(minLevel, maxLevel + 1));
                }
                population.put(mob.getUniqueId(), System.currentTimeMillis() + lifespanMillis);
                // Élettartam-korlát a mob SAJÁT schedulerén (halálkor magától nyugdíjazódik).
                mob.getScheduler().runDelayed(plugin, lifespanTask -> {
                    population.remove(mob.getUniqueId());
                    mob.remove();
                }, null, lifespanMillis / 50L);
            });
        }
    }

    /** A hatókör territóriumai: territory-id felülbírálás > scope (capital|all). */
    private java.util.List<Territory> targetTerritories() {
        final String override = configManager.getString("dark-undead.territory-id", "");
        final boolean all = "all".equalsIgnoreCase(configManager.getString("dark-undead.scope", "capital"));
        final java.util.List<Territory> out = new java.util.ArrayList<>();
        for (final Territory territory : territoryManager.all()) {
            if (!override.isBlank()) {
                if (territory.id().equalsIgnoreCase(override)) {
                    out.add(territory);
                }
                continue;
            }
            if (territory.faction() == FactionType.DARK
                    && (all || territory.type() == TerritoryType.CAPITAL)) {
                out.add(territory);
            }
        }
        return out;
    }
}
