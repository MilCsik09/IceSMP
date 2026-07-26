package hu.taliann.icesmp.utils;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Ownership-safe lifecycle registry for plugin-spawned entities. */
public final class TransientEntities {

    private static final long HEARTBEAT_PERIOD_TICKS = 20L;
    private static final long HEARTBEAT_TIMEOUT_MILLIS = 120_000L;
    private static final NamespacedKey WORLD_BOSS_KEY =
            new NamespacedKey("icesmp", "world_boss");
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static final ConcurrentHashMap<UUID, Handle> HANDLES = new ConcurrentHashMap<>();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private static final class Handle {
        private final UUID id;
        private final long generation;
        private final Plugin plugin;
        private final Entity entity;
        private final EntityScheduler scheduler;
        private final AtomicBoolean alive = new AtomicBoolean(true);
        private final AtomicLong heartbeat = new AtomicLong(System.currentTimeMillis());

        private Handle(final Plugin plugin, final Entity entity) {
            this.id = entity.getUniqueId();
            this.generation = GENERATIONS.incrementAndGet();
            this.plugin = plugin;
            this.entity = entity;
            this.scheduler = entity.getScheduler();
        }
    }

    private TransientEntities() {
    }

    public static void install(final JavaPlugin plugin) {
        if (plugin == null || !INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(new LifecycleListener(plugin), plugin);
    }

    /** Registers while on the entity's owning region; duplicate explicit registration is harmless. */
    public static void register(final Plugin plugin, final Entity entity) {
        if (plugin == null || entity == null) {
            return;
        }
        final Handle existing = HANDLES.get(entity.getUniqueId());
        if (existing != null && existing.alive.get()) {
            existing.heartbeat.set(System.currentTimeMillis());
            return;
        }
        final Handle handle = new Handle(plugin, entity);
        HANDLES.put(handle.id, handle);
        handle.scheduler.runAtFixedRate(plugin, task -> {
            if (!handle.entity.isValid()) {
                task.cancel();
                retire(handle.id, handle.generation);
                return;
            }
            // World-boss manager state is intentionally in-memory only. Persisting the entity across
            // a crash would create an untracked, unrewardable orphan after restart.
            if (handle.entity.getPersistentDataContainer().has(
                    WORLD_BOSS_KEY, PersistentDataType.BYTE)
                    && handle.entity.isPersistent()) {
                handle.entity.setPersistent(false);
            }
            handle.heartbeat.set(System.currentTimeMillis());
        }, () -> retire(handle.id, handle.generation), 1L, HEARTBEAT_PERIOD_TICKS);
    }

    /** Pure atomic liveness read; never calls Bukkit.getEntity or a foreign live entity method. */
    public static boolean isAlive(final UUID id) {
        if (id == null) {
            return false;
        }
        final Handle handle = HANDLES.get(id);
        if (handle == null || !handle.alive.get()) {
            return false;
        }
        if (System.currentTimeMillis() - handle.heartbeat.get()
                <= HEARTBEAT_TIMEOUT_MILLIS) {
            return true;
        }
        retire(id, handle.generation);
        return false;
    }

    /** Schedules removal via the captured scheduler; no global UUID-to-entity lookup occurs. */
    public static void removeById(final Plugin plugin, final UUID id) {
        if (id == null) {
            return;
        }
        final Handle handle = HANDLES.get(id);
        if (handle == null || !handle.alive.get()) {
            return;
        }
        final Plugin owner = plugin == null ? handle.plugin : plugin;
        handle.scheduler.run(owner, task -> {
            try {
                if (handle.entity.isValid()) {
                    handle.entity.remove();
                }
            } finally {
                retire(handle.id, handle.generation);
            }
        }, () -> retire(handle.id, handle.generation));
    }

    public static void markGone(final UUID id) {
        final Handle handle = id == null ? null : HANDLES.get(id);
        if (handle != null) {
            retire(id, handle.generation);
        }
    }

    public static void removeAllOnShutdown(final Collection<UUID> ids) {
        if (ids == null) {
            return;
        }
        for (final UUID id : List.copyOf(ids)) {
            removeOnShutdown(id);
        }
        ids.clear();
    }

    public static void removeOnShutdown(final UUID id) {
        final Handle handle = id == null ? null : HANDLES.get(id);
        if (handle != null) {
            removeById(handle.plugin, id);
        }
    }

    /** Release observation handles only; never delete another plugin's observed CUSTOM entity. */
    public static void shutdown() {
        for (final Handle handle : HANDLES.values()) {
            handle.alive.set(false);
        }
        HANDLES.clear();
        INSTALLED.set(false);
    }

    static int trackedCount() {
        return HANDLES.size();
    }

    private static void retire(final UUID id, final long generation) {
        HANDLES.computeIfPresent(id, (ignored, current) -> {
            if (current.generation != generation) {
                return current;
            }
            current.alive.set(false);
            return null;
        });
    }

    private static final class LifecycleListener implements Listener {
        private final JavaPlugin plugin;

        private LifecycleListener(final JavaPlugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onCustomSpawn(final CreatureSpawnEvent event) {
            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
                register(plugin, event.getEntity());
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onDeath(final EntityDeathEvent event) {
            markGone(event.getEntity().getUniqueId());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onUnload(final EntitiesUnloadEvent event) {
            for (final Entity entity : event.getEntities()) {
                markGone(entity.getUniqueId());
            }
        }
    }
}
