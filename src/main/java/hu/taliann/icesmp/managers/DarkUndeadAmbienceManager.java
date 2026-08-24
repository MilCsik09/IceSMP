package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import hu.taliann.icesmp.pve.AuthoredCreatureSpawnService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Maintains ambient undead inside DARK territories. Candidates are retried
 * finitely and must have stable solid footing; there is no airborne fallback.
 */
public final class DarkUndeadAmbienceManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TerritoryManager territoryManager;
    private final MobScalingManager mobScalingManager;
    private final EventSpawnGuard spawnGuard;
    private final org.bukkit.NamespacedKey markKey;
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

    public void onDeath(final UUID entityId) {
        population.remove(entityId);
    }

    public void tick() {
        if (!configManager.getBoolean("dark-undead.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < nextSpawnAt) {
            return;
        }
        nextSpawnAt = now + Math.max(5,
                configManager.getInt("dark-undead.spawn-interval-seconds", 30)) * 1000L;
        population.entrySet().removeIf(entry -> entry.getValue() < now);

        final List<Territory> targets = targetTerritories();
        if (targets.isEmpty()) {
            return;
        }
        final int maxPopulation = Math.max(1,
                configManager.getInt("dark-undead.max-population", 24));
        final int batch = Math.min(
                Math.max(1, configManager.getInt("dark-undead.spawn-batch", 4)),
                maxPopulation - population.size());
        if (batch <= 0) {
            return;
        }
        final List<String> configuredTemplates = configManager.getStringList("dark-undead.templates");
        final List<String> pool = configuredTemplates.isEmpty()
                ? List.of("gallows_runner", "barrow_bulwark", "dune_titheman",
                "moonbone_archer", "blackiron_votary") : List.copyOf(configuredTemplates);
        final int minLevel = Math.max(1, configManager.getInt("dark-undead.min-level", 4));
        final int maxLevel = Math.max(minLevel, configManager.getInt("dark-undead.max-level", 7));
        final long lifespanMillis = Math.max(60,
                configManager.getInt("dark-undead.lifespan-seconds", 600)) * 1000L;
        final int attempts = Math.max(1,
                configManager.getInt("dark-undead.spawn-attempts-per-mob", 12));

        for (int index = 0; index < batch; index++) {
            final Territory territory = targets.get(
                    ThreadLocalRandom.current().nextInt(targets.size()));
            trySpawn(territory, pool, minLevel, maxLevel, lifespanMillis, attempts);
        }
    }

    private void trySpawn(final Territory territory, final List<String> pool,
                          final int minLevel, final int maxLevel,
                          final long lifespanMillis, final int remainingAttempts) {
        if (remainingAttempts <= 0) {
            return;
        }
        final World world = Bukkit.getWorld(territory.world());
        if (world == null) {
            return;
        }
        final int[] column = randomColumnInside(territory);
        if (column == null) {
            trySpawn(territory, pool, minLevel, maxLevel, lifespanMillis,
                    remainingAttempts - 1);
            return;
        }
        final int x = column[0];
        final int z = column[1];
        final Location owner = new Location(world, x + 0.5D, world.getMinHeight(), z + 0.5D);
        plugin.getServer().getRegionScheduler().run(plugin, owner, task -> {
            final Location target = spawnGuard.resolveSafeStandingLocation(
                    "dark-undead", world, x, z);
            if (target == null) {
                trySpawn(territory, pool, minLevel, maxLevel, lifespanMillis,
                        remainingAttempts - 1);
                return;
            }
            final Territory actual = territoryManager.getTerritoryAt(target);
            if (actual == null || !actual.id().equals(territory.id())
                    || spawnGuard.isBlocked("dark-undead", target)) {
                trySpawn(territory, pool, minLevel, maxLevel, lifespanMillis,
                        remainingAttempts - 1);
                return;
            }

            final String templateId = randomTemplate(pool);
            if (templateId == null) {
                trySpawn(territory, pool, minLevel, maxLevel, lifespanMillis,
                        remainingAttempts - 1);
                return;
            }
            final AuthoredCreatureSpawnService spawns = AuthoredCreatureSpawnService.current();
            if (spawns == null) {
                plugin.getLogger().warning("Dark-undead spawn aborted: authored creature authority unavailable.");
                return;
            }
            final int level = ThreadLocalRandom.current().nextInt(minLevel, maxLevel + 1);
            final long lifespanTicks = Math.min(72_000L, Math.max(40L, lifespanMillis / 50L));
            final Mob mob;
            try {
                mob = spawns.spawn(target, AuthoredCreatureSpawnService.Request.template(
                        "dark_undead", "territory:" + territory.id(), "ambient_hostile",
                        templateId, level, AuthoredCreatureSpawnService.RewardOwner.GENERIC,
                        true, 1.0D, 1.0D, lifespanTicks));
            } catch (final RuntimeException invalid) {
                plugin.getLogger().warning("Dark-undead spawn failed closed: " + invalid.getMessage());
                trySpawn(territory, pool, minLevel, maxLevel, lifespanMillis,
                        remainingAttempts - 1);
                return;
            }
            mob.getPersistentDataContainer().set(markKey,
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            mob.setFallDistance(0.0F);
            mob.setVelocity(new org.bukkit.util.Vector(0.0D, 0.0D, 0.0D));
            population.put(mob.getUniqueId(), System.currentTimeMillis() + lifespanMillis);
        });
    }

    private static int[] randomColumnInside(final Territory territory) {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            final double angle = random.nextDouble(2.0D * Math.PI);
            final double distance = Math.sqrt(random.nextDouble())
                    * Math.max(1.0D, territory.radius() - 1.0D);
            final int x = territory.x() + (int) Math.round(Math.cos(angle) * distance);
            final int z = territory.z() + (int) Math.round(Math.sin(angle) * distance);
            if (territory.contains(territory.world(), x + 0.5D, z + 0.5D)) {
                return new int[] {x, z};
            }
        }
        return null;
    }

    private static String randomTemplate(final List<String> pool) {
        if (pool.isEmpty()) return null;
        final String value = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private List<Territory> targetTerritories() {
        final String override = configManager.getString("dark-undead.territory-id", "");
        final boolean all = "all".equalsIgnoreCase(
                configManager.getString("dark-undead.scope", "capital"));
        final List<Territory> out = new java.util.ArrayList<>();
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
