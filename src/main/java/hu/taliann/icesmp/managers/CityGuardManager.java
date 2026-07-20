package hu.taliann.icesmp.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * D11 — járőröző városi őrség (a Vámház őreinek megtestesülése): config-útvonalú
 * őr-NPC-k a fővárosokban. V1: saját (plugin-spawnolta) nevesített, AI-mentes
 * villager-őrök, akik waypointról waypointra lépnek — MINDEN lépés az őr SAJÁT
 * entity-schedulerén fut (az útvonal több régiót szelhet át), kis teleport-
 * lépésekkel (teleportAsync). Éjjel sűrűbb a léptetés („riadó-tempó”), nappal
 * lassabb őrjárat. Élő kulcsok: city-guards.*. Az őr sebezhetetlen és nem
 * perzisztens — restartkor újraspawnol.
 */
public final class CityGuardManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    /** guard-id -> aktív entitás (a tick pótolja, ha eltűnt). */
    private final Map<String, UUID> guards = new ConcurrentHashMap<>();
    private final Map<String, Integer> waypointIndex = new ConcurrentHashMap<>();
    private volatile long nextTickAt;

    public CityGuardManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /** A world-events tick hívja (global scheduler): spawn-pótlás + léptetés-indítás. */
    public void tick() {
        if (!configManager.getBoolean("city-guards.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < nextTickAt) {
            return;
        }
        nextTickAt = now + 1000L * Math.max(1, configManager.getInt("city-guards.step-seconds", 2));
        final org.bukkit.configuration.ConfigurationSection root =
                configManager.getConfiguration() == null ? null
                : configManager.getConfiguration().getConfigurationSection("city-guards.guards");
        if (root == null) {
            return;
        }
        for (final String id : root.getKeys(false)) {
            final List<Location> route = parseRoute(root.getConfigurationSection(id));
            if (route.size() < 2) {
                continue;
            }
            final UUID existing = guards.get(id);
            if (existing == null) {
                spawnGuard(id, root.getConfigurationSection(id), route.get(0));
                continue;
            }
            stepGuard(id, existing, route);
        }
    }

    private List<Location> parseRoute(final org.bukkit.configuration.ConfigurationSection guard) {
        final List<Location> out = new ArrayList<>();
        if (guard == null) {
            return out;
        }
        final World world = Bukkit.getWorld(guard.getString("world", "world"));
        if (world == null) {
            return out;
        }
        for (final String raw : guard.getStringList("route")) {
            final String[] parts = raw.split(",");
            if (parts.length >= 3) {
                try {
                    out.add(new Location(world, Double.parseDouble(parts[0].trim()) + 0.5D,
                            Double.parseDouble(parts[1].trim()),
                            Double.parseDouble(parts[2].trim()) + 0.5D));
                } catch (final NumberFormatException ignored) {
                    // hibás sor — kihagyjuk
                }
            }
        }
        return out;
    }

    private void spawnGuard(final String id, final org.bukkit.configuration.ConfigurationSection guard,
                            final Location start) {
        final String name = guard == null ? id : guard.getString("name", "Városi őr");
        start.getWorld().getRegionScheduler().run(plugin, start, task -> {
            final Villager villager = start.getWorld().spawn(start, Villager.class, mob -> {
                mob.setAI(false);
                mob.setInvulnerable(true);
                mob.setPersistent(false);
                mob.setSilent(true);
                mob.customName(net.kyori.adventure.text.Component.text("🛡 " + name,
                        net.kyori.adventure.text.format.NamedTextColor.GOLD));
                mob.setCustomNameVisible(true);
                mob.setProfession(Villager.Profession.WEAPONSMITH);
            });
            guards.put(id, villager.getUniqueId());
            waypointIndex.put(id, 1);
        });
    }

    private void stepGuard(final String id, final UUID entityId, final List<Location> route) {
        final int targetIndex = waypointIndex.getOrDefault(id, 1) % route.size();
        final Location target = route.get(targetIndex);
        // Léptetés az őr SAJÁT régió-szálán — a lookup + mozgatás is ott biztonságos.
        target.getWorld().getRegionScheduler().run(plugin, target, task -> {
            final org.bukkit.entity.Entity entity = Bukkit.getEntity(entityId);
            if (entity == null || !entity.isValid()) {
                guards.remove(id); // a következő tick újraspawnolja
                return;
            }
            entity.getScheduler().run(plugin, moveTask -> {
                final Location from = entity.getLocation();
                final boolean night = from.getWorld().getTime() >= 13000L;
                final double stepBlocks = night
                        ? configManager.getDouble("city-guards.night-step-blocks", 3.0D)
                        : configManager.getDouble("city-guards.day-step-blocks", 1.5D);
                final double distance = from.distance(target);
                if (distance <= stepBlocks) {
                    entity.teleportAsync(target);
                    waypointIndex.put(id, (targetIndex + 1) % route.size());
                } else {
                    final org.bukkit.util.Vector direction = target.toVector()
                            .subtract(from.toVector()).normalize().multiply(stepBlocks);
                    final Location next = from.clone().add(direction);
                    next.setY(from.getWorld().getHighestBlockYAt(next.getBlockX(), next.getBlockZ()) + 1.0D);
                    next.setDirection(direction);
                    entity.teleportAsync(next);
                }
            }, null);
        });
    }

    /** Leállításkor: az őrök levétele (mindegyik a saját régió-szálán). */
    public void shutdown() {
        for (final UUID entityId : guards.values()) {
            final org.bukkit.entity.Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.getScheduler().run(plugin, task -> entity.remove(), null);
            }
        }
        guards.clear();
    }
}
