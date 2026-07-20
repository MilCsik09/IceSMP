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
 * Lore-ambiencia: Mortengrad, a DARK főváros tele van élőholtakkal — a Néma
 * Királynő népe otthon jár. A manager a DARK capital-territóriumban tart fenn
 * egy magas szintű undead-populációt: a mobok NEM égnek el a napon
 * (EventSpawnGuard.prepare), szinttel spawnolnak (MobScalingManager.forceLevel),
 * és korlátos élettartamúak — a populáció a tick-ből töltődik újra.
 *
 * <p>A DARK játékosokat a meglévő frakció-passzíva miatt békén hagyják — a
 * betolakodónak viszont Mortengrad maga a rémálom. Minden kulcs élő config
 * (mortengrad-undead.*). Folia: a spawn a cél-helyszín régió-schedulerén fut;
 * a populáció-követés konkurrens map (uuid -> lejárat), a halál-listener és a
 * lifespan-remove is takarít.
 */
public final class MortengradUndeadManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TerritoryManager territoryManager;
    private final MobScalingManager mobScalingManager;
    private final org.bukkit.NamespacedKey markKey;
    /** spawnolt undead -> várható lejárat (élettartam-alapú prune, régió-érintés nélkül). */
    private final Map<UUID, Long> population = new ConcurrentHashMap<>();
    private volatile long nextSpawnAt;

    public MortengradUndeadManager(final JavaPlugin plugin, final ConfigManager configManager,
                                   final TerritoryManager territoryManager,
                                   final MobScalingManager mobScalingManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.territoryManager = territoryManager;
        this.mobScalingManager = mobScalingManager;
        this.markKey = new org.bukkit.NamespacedKey(plugin, "mortengrad_undead");
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
        if (!configManager.getBoolean("mortengrad-undead.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < nextSpawnAt) {
            return;
        }
        nextSpawnAt = now + Math.max(5, configManager.getInt("mortengrad-undead.spawn-interval-seconds", 30)) * 1000L;
        // Lejárt (élettartamuk végén magától eltűnő) példányok kivezetése a számlálóból.
        population.entrySet().removeIf(entry -> entry.getValue() < now);

        final Territory capital = findDarkCapital();
        if (capital == null) {
            return;
        }
        final World world = Bukkit.getWorld(capital.world());
        if (world == null) {
            return;
        }
        final int maxPopulation = Math.max(1, configManager.getInt("mortengrad-undead.max-population", 24));
        final int batch = Math.min(
                Math.max(1, configManager.getInt("mortengrad-undead.spawn-batch", 4)),
                maxPopulation - population.size());
        if (batch <= 0) {
            return;
        }
        final List<String> types = configManager.getStringList("mortengrad-undead.types");
        final List<String> pool = types.isEmpty()
                ? List.of("ZOMBIE", "SKELETON", "HUSK", "STRAY", "WITHER_SKELETON") : types;
        final int minLevel = Math.max(1, configManager.getInt("mortengrad-undead.min-level", 4));
        final int maxLevel = Math.max(minLevel, configManager.getInt("mortengrad-undead.max-level", 7));
        final long lifespanMillis = Math.max(60, configManager.getInt("mortengrad-undead.lifespan-seconds", 600)) * 1000L;

        for (int index = 0; index < batch; index++) {
            // Véletlen pont a capital-körön belül (a spawn a helyszín régió-szálán fut).
            final double angle = ThreadLocalRandom.current().nextDouble(2.0D * Math.PI);
            final double distance = ThreadLocalRandom.current().nextDouble() * Math.max(4, capital.radius() - 4);
            final int x = capital.x() + (int) Math.round(Math.cos(angle) * distance);
            final int z = capital.z() + (int) Math.round(Math.sin(angle) * distance);
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
                final org.bukkit.entity.Entity spawned = world.spawnEntity(target, type);
                if (!(spawned instanceof Mob mob)) {
                    spawned.remove();
                    return;
                }
                EventSpawnGuard.prepare(mob); // napfény-égés/zombisodás ellen — Mortengrad népe nappal is jár
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

    private Territory findDarkCapital() {
        final String override = configManager.getString("mortengrad-undead.territory-id", "");
        for (final Territory territory : territoryManager.all()) {
            if (!override.isBlank()) {
                if (territory.id().equalsIgnoreCase(override)) {
                    return territory;
                }
                continue;
            }
            if (territory.faction() == FactionType.DARK && territory.type() == TerritoryType.CAPITAL) {
                return territory;
            }
        }
        return null;
    }
}
