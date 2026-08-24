package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Terjedő rontás-góc. A teljes balansz és életciklus a {@code corruption.*}
 * konfigurációból érkezik; a kód nem alkalmaz rejtett abszolút plafonokat.
 *
 * <p>A fajzat szintje a normál, helyfüggő mob-szint és a
 * {@code corruption.mob-level-bonus} összege. A mobtípusok, darabszám,
 * utánpótlás, sugár, terjedés, életidő és entitástulajdonságok mind élőben
 * konfigurálhatók.</p>
 */
public final class CorruptionManager implements PersistentStore {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final EventSpawnGuard spawnGuard;
    private final MessageManager messageManager;
    private final TerritoryManager territoryManager;
    private final FactionManager factionManager;
    private final SeasonManager seasonManager;
    private final File storageFile;
    private final NamespacedKey corruptMobKey;

    private volatile String worldName;
    private volatile int centerX;
    private volatile int centerY;
    private volatile int centerZ;
    private volatile double radius;
    private volatile boolean active;
    private final AtomicInteger purgeKills = new AtomicInteger();
    private volatile long nextAttemptAt;
    private volatile long spawnGraceUntil;
    private final Set<UUID> corruptMobs = ConcurrentHashMap.newKeySet();
    private final AtomicInteger pendingSpawns = new AtomicInteger();
    private volatile long legacyCleanupUntil;
    private volatile String cleanupWorldName;
    private volatile int cleanupCenterX;
    private volatile int cleanupCenterZ;
    private volatile double cleanupRadius;
    private volatile long lastSpreadDay = -1L;
    private volatile boolean dirty;

    /** Personal-loot hozzájárulás a jelenlegi góchoz. */
    private final Map<UUID, Integer> purgeContributors = new ConcurrentHashMap<>();

    public CorruptionManager(final JavaPlugin plugin, final ConfigManager configManager,
                             final MobScalingManager mobScalingManager, final EventSpawnGuard spawnGuard,
                             final MessageManager messageManager, final TerritoryManager territoryManager,
                             final FactionManager factionManager, final SeasonManager seasonManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.spawnGuard = spawnGuard;
        this.messageManager = messageManager;
        this.territoryManager = territoryManager;
        this.factionManager = factionManager;
        this.seasonManager = seasonManager;
        this.storageFile = new File(plugin.getDataFolder(), "corruption.yml");
        this.corruptMobKey = hu.taliann.icesmp.factions.FactionCombatMarkers.CORRUPTION_MOB;
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public void load() {
        active = false;
        if (!storageFile.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
        if (!yaml.getBoolean("active", false)) {
            return;
        }
        worldName = yaml.getString("world", "");
        centerX = yaml.getInt("x");
        centerY = yaml.getInt("y");
        centerZ = yaml.getInt("z");
        radius = yaml.getDouble("radius", configManager.getDouble("corruption.initial-radius", 0.0D));
        purgeKills.set(yaml.getInt("purge-kills", 0));
        active = true;

        final World world = Bukkit.getWorld(worldName);
        if (world != null && isInsideSpawnExclusion(world, centerX, centerZ)) {
            plugin.getLogger().warning("Removing persisted corruption zone inside the configured world-spawn exclusion at "
                    + centerX + "," + centerZ + ".");
            beginLegacyCleanup(world);
            deactivate();
            return;
        }
        plugin.getLogger().info("Resumed corruption zone at " + centerX + "," + centerZ + " r=" + radius);
    }

    @Override
    public synchronized void save() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("active", active);
            if (active) {
                yaml.set("world", worldName);
                yaml.set("x", centerX);
                yaml.set("y", centerY);
                yaml.set("z", centerZ);
                yaml.set("radius", radius);
                yaml.set("purge-kills", purgeKills.get());
            }
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save corruption.yml: " + exception.getMessage());
            throw new java.io.UncheckedIOException("Failed to save corruption.yml", exception);
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isCorruptMob(final UUID entityId) {
        return entityId != null && corruptMobs.contains(entityId);
    }

    /** Death/unload lifecycle hook; returns whether the entity was a tracked corruption spawn. */
    public boolean forgetCorruptMob(final UUID entityId) {
        return entityId != null && corruptMobs.remove(entityId);
    }

    /**
     * Chunkbetöltéskor visszaveszi a megjelölt fajzatokat, illetve a konfigurált
     * legacy-ablakban eltávolítja a korábbi, marker nélküli perzisztens példányokat.
     */
    public void handleLoadedEntities(final Collection<? extends Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        final int cap = configuredMobCap();
        for (final Entity entity : entities) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            final Location location = mob.getLocation();
            final boolean tagged = mob.getPersistentDataContainer().has(corruptMobKey, PersistentDataType.BYTE);
            final boolean activeCandidate = active && isWithinActiveArea(location);
            final boolean cleanupCandidate = now < legacyCleanupUntil && isWithinCleanupArea(location);
            final boolean legacy = (activeCandidate || cleanupCandidate) && isLikelyLegacyCorruptMob(mob);
            if (!tagged && !legacy) {
                continue;
            }
            if (cleanupCandidate || !activeCandidate || cap <= 0 || corruptMobs.size() >= cap) {
                corruptMobs.remove(mob.getUniqueId());
                mob.remove();
                continue;
            }
            trackCorruptMob(mob);
        }
    }

    private List<EntityType> configuredMobTypes() {
        final java.util.ArrayList<EntityType> types = new java.util.ArrayList<>();
        for (final String raw : configManager.getStringList("corruption.mob-types")) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                final EntityType type = EntityType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
                final Class<? extends Entity> entityClass = type.getEntityClass();
                if (entityClass != null && Mob.class.isAssignableFrom(entityClass)) {
                    types.add(type);
                }
            } catch (final IllegalArgumentException ignored) {
                // A hibás bejegyzés kimarad. A lista élőben újraolvasható reload után.
            }
        }
        return List.copyOf(types);
    }

    private List<String> configuredMobTemplates() {
        final java.util.ArrayList<String> templates = new java.util.ArrayList<>();
        for (final String raw : configManager.getStringList("corruption.mob-templates")) {
            if (raw != null && !raw.isBlank()) templates.add(raw.trim().toLowerCase(java.util.Locale.ROOT));
        }
        return List.copyOf(templates);
    }

    private boolean isLikelyLegacyCorruptMob(final Mob mob) {
        return mob.isGlowing()
                && !mob.getRemoveWhenFarAway()
                && mobScalingManager.getLevel(mob) > 0
                && configuredMobTypes().contains(mob.getType());
    }

    private boolean isWithinActiveArea(final Location location) {
        final double configuredLimit = configuredRadiusLimit();
        final double referenceRadius = configuredLimit > 0.0D
                ? Math.max(radius, configuredLimit)
                : radius;
        final double searchRadius = referenceRadius
                + configManager.getDouble("corruption.tracking-padding", 0.0D);
        return isWithinHorizontalArea(location, worldName, centerX, centerZ, searchRadius);
    }

    private boolean isWithinCleanupArea(final Location location) {
        return isWithinHorizontalArea(location, cleanupWorldName,
                cleanupCenterX, cleanupCenterZ, cleanupRadius);
    }

    private static boolean isWithinHorizontalArea(final Location location, final String expectedWorld,
                                                   final int x, final int z, final double areaRadius) {
        if (location == null || location.getWorld() == null || expectedWorld == null
                || !expectedWorld.equals(location.getWorld().getName())) {
            return false;
        }
        final double dx = location.getX() - (x + 0.5D);
        final double dz = location.getZ() - (z + 0.5D);
        return dx * dx + dz * dz <= areaRadius * areaRadius;
    }

    private boolean isInsideSpawnExclusion(final World world, final int x, final int z) {
        final double minimum = configManager.getDouble("corruption.min-world-spawn-distance", 0.0D);
        if (minimum <= 0.0D) {
            return false;
        }
        final Location spawn = world.getSpawnLocation();
        final double dx = (x + 0.5D) - spawn.getX();
        final double dz = (z + 0.5D) - spawn.getZ();
        return dx * dx + dz * dz < minimum * minimum;
    }

    private long configuredSpawnGraceMillis() {
        return configManager.getLong("corruption.spawn-grace-seconds", 0L) * 1000L;
    }

    private int configuredMobCap() {
        return configManager.getInt("corruption.mob-cap", 0);
    }

    private int configuredMobLevel(final Location location) {
        return mobScalingManager.resolveLevel(location)
                + configManager.getInt("corruption.mob-level-bonus", 0);
    }

    private double configuredRadiusLimit() {
        return configManager.getDouble("corruption.radius-cap", 0.0D);
    }

    private double configuredSpreadPerNight() {
        return configManager.getDouble("corruption.spread-per-night", 0.0D);
    }

    private void trackCorruptMob(final Mob mob) {
        mob.getPersistentDataContainer().set(corruptMobKey, PersistentDataType.BYTE, (byte) 1);
        mob.setGlowing(configManager.getBoolean("corruption.mob-glowing", false));
        mob.setPersistent(configManager.getBoolean("corruption.mob-persistent", false));
        mob.setRemoveWhenFarAway(configManager.getBoolean("corruption.mob-remove-when-far-away", false));

        final UUID mobId = mob.getUniqueId();
        hu.taliann.icesmp.utils.TransientEntities.register(plugin, mob);
        if (!corruptMobs.add(mobId)) {
            return;
        }
        final long seconds = configManager.getLong("corruption.mob-lifespan-seconds", 0L);
        if (seconds <= 0L) {
            return;
        }
        mob.getScheduler().runDelayed(plugin, task -> {
            corruptMobs.remove(mobId);
            if (mob.isValid()) {
                mob.remove();
            }
        }, () -> corruptMobs.remove(mobId), seconds * 20L);
    }

    private void beginLegacyCleanup(final World world) {
        cleanupWorldName = world.getName();
        cleanupCenterX = centerX;
        cleanupCenterZ = centerZ;
        final double configuredLimit = configuredRadiusLimit();
        final double referenceRadius = configuredLimit > 0.0D
                ? Math.max(radius, configuredLimit)
                : radius;
        cleanupRadius = referenceRadius
                + configManager.getDouble("corruption.legacy-cleanup-padding", 0.0D);

        final long seconds = configManager.getLong("corruption.legacy-cleanup-seconds", 0L);
        legacyCleanupUntil = seconds > 0L
                ? System.currentTimeMillis() + seconds * 1000L
                : System.currentTimeMillis();

        final int minChunkX = ((int) Math.floor(cleanupCenterX - cleanupRadius)) >> 4;
        final int maxChunkX = ((int) Math.floor(cleanupCenterX + cleanupRadius)) >> 4;
        final int minChunkZ = ((int) Math.floor(cleanupCenterZ - cleanupRadius)) >> 4;
        final int maxChunkZ = ((int) Math.floor(cleanupCenterZ + cleanupRadius)) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                final int chunkX = cx;
                final int chunkZ = cz;
                final Location owner = new Location(world, (chunkX << 4) + 8.0D, centerY,
                        (chunkZ << 4) + 8.0D);
                plugin.getServer().getRegionScheduler().run(plugin, owner, task -> {
                    if (world.isChunkLoaded(chunkX, chunkZ)) {
                        handleLoadedEntities(Arrays.asList(world.getChunkAt(chunkX, chunkZ).getEntities()));
                    }
                });
            }
        }
    }

    public void recordPurgeKill() {
        purgeKills.incrementAndGet();
        dirty = true;
    }

    public void recordPurgeKill(final UUID killerId) {
        recordPurgeKill();
        if (killerId != null) {
            purgeContributors.merge(killerId, 1, Integer::sum);
        }
    }

    public int getPurgeKills() {
        return purgeKills.get();
    }

    public int getRequiredPurgeKills() {
        return configManager.getInt("corruption.purge-kills-required", 0);
    }

    public boolean isInAura(final Location loc) {
        if (!active || loc == null || loc.getWorld() == null
                || !loc.getWorld().getName().equals(worldName)
                || !configManager.getBoolean("corruption.aura.enabled", false)) {
            return false;
        }
        final double auraRadius = configManager.getDouble("corruption.aura.radius", 0.0D);
        if (auraRadius <= 0.0D) {
            return false;
        }
        final double dx = loc.getX() - (centerX + 0.5D);
        final double dz = loc.getZ() - (centerZ + 0.5D);
        final double dy = loc.getY() - centerY;
        return dx * dx + dz * dz + dy * dy <= auraRadius * auraRadius;
    }

    public boolean isCoreBlock(final org.bukkit.block.Block block) {
        return active && block != null && block.getType() == Material.SCULK_CATALYST
                && block.getWorld().getName().equals(worldName)
                && block.getX() == centerX && block.getY() == centerY && block.getZ() == centerZ;
    }

    /** A közös világesemény-driverből hívott periodikus lépés. */
    public void tick() {
        if (!configManager.getBoolean("corruption.enabled", false)) {
            if (active) {
                deactivate();
            }
            return;
        }
        if (dirty) {
            dirty = false;
            save();
        }
        final long now = System.currentTimeMillis();
        if (active) {
            spreadAndSpawn();
            return;
        }
        if (now < spawnGraceUntil || now < nextAttemptAt) {
            return;
        }

        nextAttemptAt = now + configManager.getLong("corruption.interval-minutes", 0L) * 60_000L;
        final double chance = configManager.getDouble("corruption.chance-percent", 0.0D);
        if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return;
        }
        trySpawn(null);
    }

    public synchronized boolean forceSpawnAt(final Location where) {
        if (active || System.currentTimeMillis() < spawnGraceUntil || where.getWorld() == null) {
            return false;
        }
        spawnGraceUntil = System.currentTimeMillis() + configuredSpawnGraceMillis();
        final World world = where.getWorld();
        final int x = where.getBlockX();
        final int z = where.getBlockZ();
        plugin.getServer().getRegionScheduler().run(plugin, where,
                task -> placeCore(world, x, z, false));
        return true;
    }

    public synchronized boolean forceSpawn(final Player anchor) {
        if (active || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        return trySpawn(anchor);
    }

    private synchronized void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        final World world = Bukkit.getWorld(worldName == null ? "" : worldName);
        if (world != null) {
            final int x = centerX;
            final int y = centerY;
            final int z = centerZ;
            plugin.getServer().getRegionScheduler().run(plugin, new Location(world, x, y, z), task -> {
                if (world.getBlockAt(x, y, z).getType() == Material.SCULK_CATALYST) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            });
        }
        for (final UUID id : corruptMobs) {
            hu.taliann.icesmp.utils.TransientEntities.removeById(plugin, id);
        }
        corruptMobs.clear();
        pendingSpawns.set(0);
        save();
        plugin.getLogger().info("Corruption zone deactivated.");
    }

    private synchronized boolean trySpawn(final Player preferredAnchor) {
        if (active || System.currentTimeMillis() < spawnGraceUntil) {
            return false;
        }
        spawnGraceUntil = System.currentTimeMillis() + configuredSpawnGraceMillis();

        Player anchor = preferredAnchor;
        if (anchor == null) {
            if (tryDarkEdgeSpawn()) {
                return true;
            }
            final List<? extends Player> online = List.copyOf(Bukkit.getOnlinePlayers());
            if (online.isEmpty()) {
                return false;
            }
            anchor = online.get(ThreadLocalRandom.current().nextInt(online.size()));
        }

        final Player target = anchor;
        final int offset = Math.abs(configManager.getInt("corruption.spawn-offset", 0));
        target.getScheduler().run(plugin, task -> {
            final Location base = target.getLocation().clone();
            final int x = base.getBlockX() + ThreadLocalRandom.current().nextInt(-offset, offset + 1);
            final int z = base.getBlockZ() + ThreadLocalRandom.current().nextInt(-offset, offset + 1);
            final World world = base.getWorld();
            if (world != null) {
                plugin.getServer().getRegionScheduler().run(plugin, new Location(world, x, 0, z),
                        regionTask -> placeCore(world, x, z, false));
            }
        }, null);
        return true;
    }

    private boolean tryDarkEdgeSpawn() {
        final double chance = configManager.getDouble("corruption.dark-bias.chance-percent", 0.0D);
        if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return false;
        }

        final java.util.ArrayList<hu.taliann.icesmp.data.Territory> darks = new java.util.ArrayList<>();
        for (final hu.taliann.icesmp.data.Territory territory : territoryManager.all()) {
            if (territory.faction() == hu.taliann.icesmp.data.FactionType.DARK) {
                darks.add(territory);
            }
        }
        if (darks.isEmpty()) {
            return false;
        }

        final hu.taliann.icesmp.data.Territory source =
                darks.get(ThreadLocalRandom.current().nextInt(darks.size()));
        final World world = Bukkit.getWorld(source.world());
        if (world == null) {
            return false;
        }

        final int firstEdge = configManager.getInt("corruption.dark-bias.min-edge-distance", 0);
        final int secondEdge = configManager.getInt("corruption.dark-bias.max-edge-distance", 0);
        final int lowerEdge = Math.min(firstEdge, secondEdge);
        final int upperEdge = Math.max(firstEdge, secondEdge);
        final double edgeDistance = lowerEdge == upperEdge
                ? lowerEdge
                : ThreadLocalRandom.current().nextDouble(lowerEdge, upperEdge + 1.0D);
        final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
        final double distance = source.radius() + edgeDistance;
        final int x = source.x() + (int) Math.round(Math.cos(angle) * distance);
        final int z = source.z() + (int) Math.round(Math.sin(angle) * distance);
        plugin.getServer().getRegionScheduler().run(plugin, new Location(world, x, 0, z),
                task -> placeCore(world, x, z, true));
        return true;
    }

    private void placeCore(final World world, final int x, final int z, final boolean fromDarkEdge) {
        final int y = world.getHighestBlockYAt(x, z);
        final Location core = new Location(world, x, y + 1, z);
        if (isInsideSpawnExclusion(world, x, z)
                || spawnGuard.isBlocked("corruption", core)
                || spawnGuard.isUnsafeSurface("corruption", world, x, z)) {
            return;
        }

        world.getBlockAt(x, y + 1, z).setType(Material.SCULK_CATALYST, false);
        worldName = world.getName();
        centerX = x;
        centerY = y + 1;
        centerZ = z;
        radius = configManager.getDouble("corruption.initial-radius", 0.0D);
        purgeKills.set(0);
        purgeContributors.clear();
        lastSpreadDay = -1L;
        active = true;
        spawnGraceUntil = 0L;
        save();

        world.playSound(core, org.bukkit.Sound.ENTITY_WARDEN_EMERGE, 1.5F, 0.5F);
        hu.taliann.icesmp.utils.ParticleUtil.spawn(
                world, org.bukkit.Particle.SCULK_SOUL, core, 40, 2.0D, 1.0D, 2.0D, 0.05D);
        final String key = fromDarkEdge ? "corruption-spawned-dark" : "corruption-spawned";
        final String fallback = fromDarkEdge
                ? "<dark_purple>🕸 RONTÁS-GÓC szivárgott ki a Kitaszítottak földjének pereméről ({world}: {x}, {z})! Irtás: {kills}, majd SHIFT+jobb katt a magon.</dark_purple>"
                : "<dark_purple>🕸 RONTÁS-GÓC nyílt a vadonban ({world}: {x}, {z})! Irtás: {kills}, majd SHIFT+jobb katt a magon.</dark_purple>";
        Bukkit.getServer().broadcast(messageManager.getMessage(key, fallback,
                Map.of("world", world.getName(), "x", String.valueOf(x), "z", String.valueOf(z),
                        "kills", String.valueOf(getRequiredPurgeKills()))));
    }

    private void spreadAndSpawn() {
        final World world = Bukkit.getWorld(worldName == null ? "" : worldName);
        if (world == null) {
            return;
        }
        if (isInsideSpawnExclusion(world, centerX, centerZ)) {
            plugin.getLogger().warning("Deactivating corruption zone inside the configured world-spawn exclusion at "
                    + centerX + "," + centerZ + ".");
            beginLegacyCleanup(world);
            deactivate();
            return;
        }

        final long day = world.getFullTime() / 24000L;
        if (!world.isDayTime() && day != lastSpreadDay) {
            lastSpreadDay = day;
            final double limit = configuredRadiusLimit();
            double next = radius + configuredSpreadPerNight();
            if (limit > 0.0D && next > limit) {
                next = limit;
            }
            if (Double.compare(next, radius) != 0) {
                radius = next;
                save();
                Bukkit.getServer().broadcast(messageManager.getMessage(
                        "corruption-spread",
                        "<dark_purple>🕸 A rontás terjed… (sugár: {radius} blokk).</dark_purple>",
                        Map.of("radius", String.valueOf((int) radius))));
            }
        }

        corruptMobs.removeIf(id -> !hu.taliann.icesmp.utils.TransientEntities.isAlive(id));

        final int cap = configuredMobCap();
        final int batch = configManager.getInt("corruption.spawn-batch", 0);
        final List<String> pool = configuredMobTemplates();
        if (cap <= 0 || batch <= 0 || pool.isEmpty()) {
            return;
        }
        final int available = cap - corruptMobs.size() - pendingSpawns.get();
        if (available <= 0) {
            return;
        }

        final int spawnCount = Math.min(batch, available);
        for (int i = 0; i < spawnCount; i++) {
            final double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0D);
            final double distance = radius > 0.0D
                    ? ThreadLocalRandom.current().nextDouble(radius)
                    : 0.0D;
            final int x = centerX + (int) Math.round(Math.cos(angle) * distance);
            final int z = centerZ + (int) Math.round(Math.sin(angle) * distance);
            pendingSpawns.incrementAndGet();
            plugin.getServer().getRegionScheduler().run(plugin, new Location(world, x, 0, z), task -> {
                try {
                    if (!active || !world.getName().equals(worldName)) {
                        return;
                    }
                    final int y = world.getHighestBlockYAt(x, z) + 1;
                    final Location spot = new Location(world, x + 0.5D, y, z + 0.5D);
                    if (isInsideSpawnExclusion(world, x, z)
                            || spawnGuard.isBlocked("corruption", spot)
                            || spawnGuard.isUnsafeSurface("corruption", world, x, z)) {
                        return;
                    }

                    final String templateId = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                    final int level = configuredMobLevel(spot);
                    final hu.taliann.icesmp.pve.AuthoredCreatureSpawnService spawns =
                            hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.current();
                    if (spawns == null || level < 1) return;
                    final Mob mob = spawns.spawn(spot,
                            hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.Request.template(
                                    "corruption", "corruption:active", "wave", templateId, level,
                                    hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.RewardOwner.GENERIC,
                                    true, 1.0D, 1.0D, 0L));
                    if (mob == null) return;
                    trackCorruptMob(mob);
                } finally {
                    pendingSpawns.decrementAndGet();
                }
            });
        }
    }

    public synchronized boolean tryCleanse(final Player cleanser) {
        if (!active) {
            return false;
        }
        if (purgeKills.get() < getRequiredPurgeKills()) {
            cleanser.sendMessage(messageManager.getMessage(
                    "corruption-core-strong",
                    "<dark_purple>🕸 A mag még erős — irts több korrupt fajzatot! ({kills}/{required})</dark_purple>",
                    Map.of("kills", String.valueOf(purgeKills.get()),
                            "required", String.valueOf(getRequiredPurgeKills()))));
            return false;
        }

        final World world = Bukkit.getWorld(worldName == null ? "" : worldName);
        active = false;
        save();
        AdvancementService.award(cleanser, "cleanse");
        if (world != null) {
            final Location core = new Location(world, centerX, centerY, centerZ);
            world.getBlockAt(centerX, centerY, centerZ).setType(Material.AIR, false);
            world.playSound(core, org.bukkit.Sound.BLOCK_SCULK_CATALYST_BREAK, 1.5F, 0.6F);
            world.playSound(core, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.4F);
            hu.taliann.icesmp.utils.ParticleUtil.spawn(
                    world, org.bukkit.Particle.TOTEM_OF_UNDYING, core, 40, 2.0D, 1.5D, 2.0D, 0.1D);

            final int rolls = configManager.getInt("corruption.reward-rolls", 0);
            if (rolls > 0) {
                for (final org.bukkit.inventory.ItemStack loot
                        : LootTable.roll(configManager, "corruption.loot", rolls)) {
                    world.dropItemNaturally(core, loot);
                }
            }

            final int buffMinutes = configManager.getInt("corruption.reward-buff-minutes", 0);
            if (buffMinutes > 0) {
                cleanser.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.REGENERATION,
                        buffMinutes * 60 * 20, 0, false, true, true));
            }

            final int minKills = configManager.getInt("corruption.contributor-min-kills", 0);
            final double ratio = configManager.getDouble("corruption.contributor-loot-ratio", 0.0D);
            final int contributorRolls = (int) Math.round(rolls * ratio);
            if (contributorRolls > 0) {
                for (final Map.Entry<UUID, Integer> entry : Map.copyOf(purgeContributors).entrySet()) {
                    if (entry.getValue() < minKills || entry.getKey().equals(cleanser.getUniqueId())) {
                        continue;
                    }
                    final Player contributor = Bukkit.getPlayer(entry.getKey());
                    if (contributor == null) {
                        continue;
                    }
                    contributor.getScheduler().run(plugin, task -> {
                        for (final org.bukkit.inventory.ItemStack loot
                                : LootTable.roll(configManager, "corruption.loot", contributorRolls)) {
                            contributor.getInventory().addItem(loot).values().forEach(left ->
                                    contributor.getWorld().dropItemNaturally(contributor.getLocation(), left));
                        }
                        contributor.sendMessage(messageManager.getMessage(
                                "corruption-contributor-loot",
                                "<green>🕸 Irtottad a rontás fajzatait — a Fa hálája téged is elér.</green>"));
                    }, null);
                }
            }
        }

        purgeContributors.clear();
        for (final UUID id : corruptMobs) {
            hu.taliann.icesmp.utils.TransientEntities.removeById(plugin, id);
        }
        corruptMobs.clear();
        pendingSpawns.set(0);

        factionManager.getChosenFaction(cleanser.getUniqueId()).ifPresent(faction ->
                seasonManager.addPoints(faction,
                        configManager.getInt("corruption.season-points", 0), "cleanse"));
        Bukkit.getServer().broadcast(messageManager.getMessage(
                "corruption-cleansed",
                "<green>🌿 {player} megtörte a rontás magját — a góc szertefoszlik.</green>",
                Map.of("player", cleanser.getName())));
        return true;
    }

    public void shutdown() {
        hu.taliann.icesmp.utils.TransientEntities.removeAllOnShutdown(corruptMobs);
    }
}
