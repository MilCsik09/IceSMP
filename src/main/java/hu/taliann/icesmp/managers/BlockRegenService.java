package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.BlockRegenJournal;
import hu.taliann.icesmp.storage.PersistentStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Restores protected blocks without drops.
 *
 * <p>For tile entities the WAL remains open after the live restore. The block receives a token and
 * the current JVM boot id in its TileState PDC. The record is marked APPLIED only after either
 * {@code World.save(true)} has flushed the marker/content pair or the token is observed under a
 * different process boot id after restart. This removes both crash directions:
 * <ul>
 *   <li>no replay of an already durable container snapshot (item duplication);</li>
 *   <li>no durable APPLIED marker before the restored chunk itself is durable (item loss).</li>
 * </ul>
 */
public final class BlockRegenService implements PersistentStore, Listener {

    private enum MarkerStatus {
        NONE,
        CURRENT_BOOT,
        PRIOR_BOOT
    }

    private record SnapshotPayload(String token, byte[] bytes) {
    }

    private static final long IN_FLIGHT_TIMEOUT_MILLIS = 60_000L;
    private static final long RETRY_MILLIS = 1_000L;
    private static final long PERSISTENCE_PROOF_RETRY_MILLIS = 30_000L;
    private static final long WORLD_WAIT_MILLIS = 30_000L;
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final String NBT_PREFIX = "nbt:";
    private static final String NBT_V2_PREFIX = "nbt2:";
    private static final NamespacedKey REGEN_TOKEN =
            new NamespacedKey("icesmp", "block_regen_token");
    private static final NamespacedKey REGEN_BOOT =
            new NamespacedKey("icesmp", "block_regen_boot");
    private static final String PROCESS_BOOT = processBootId();

    public static final String DEBRIS_TAG = "icesmp_debris";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final BlockRegenJournal journal;
    private final Queue<BlockRegenJournal.Record> queue = new ConcurrentLinkedQueue<>();
    private final Map<Long, Long> inFlight = new ConcurrentHashMap<>();
    private final Map<Long, Long> retryAfter = new ConcurrentHashMap<>();
    private final Set<Long> applyingMarked = ConcurrentHashMap.newKeySet();
    private final Set<Long> invalidRecordLogged = ConcurrentHashMap.newKeySet();
    private final Set<Long> restoreFailureLogged = ConcurrentHashMap.newKeySet();
    private final Set<Long> persistenceProofInFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> missingWorldLogged = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> physicsShield = new ConcurrentHashMap<>();
    private final Set<String> pendingShield = ConcurrentHashMap.newKeySet();
    private final Map<String, long[]> captureHistory = new ConcurrentHashMap<>();
    /**
     * A one-tick control loop makes restore-interval-ticks genuinely live. The old fixed-rate core
     * task may still call tick(); the CAS gate below coalesces both callers into one pass.
     */
    private final AtomicBoolean dynamicTickerStarted = new AtomicBoolean();
    private final AtomicLong lastIntervalTicks = new AtomicLong(-1L);
    private final AtomicLong nextPassNanos = new AtomicLong();

    public BlockRegenService(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.journal = new BlockRegenJournal(plugin.getDataFolder(), plugin.getLogger());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isEnabled() {
        return configManager.getBoolean("territory.protection.regen.enabled", true);
    }

    public boolean isZoneRegenEnabled(final String zoneKey) {
        final boolean def = !"wilderness".equals(zoneKey) && !"faction".equals(zoneKey);
        return configManager.getBoolean("territory.protection.regen.zones." + zoneKey, def);
    }

    public long explosionDelayMillis() {
        return Math.max(5L,
                configManager.getLong("territory.protection.regen.delay-seconds", 180L)) * 1000L;
    }

    public long restoreIntervalTicks() {
        return Math.max(1L,
                configManager.getLong("territory.protection.regen.restore-interval-ticks", 10L));
    }

    public int blocksPerPass() {
        return Math.max(1,
                configManager.getInt("territory.protection.regen.blocks-per-pass", 3));
    }

    private long supportGraceMillis() {
        return Math.max(5L, configManager.getLong(
                "territory.protection.regen.support-grace-seconds", 120L)) * 1000L;
    }

    public boolean isSiegeBreakEnabled() {
        return configManager.getBoolean(
                "territory.protection.regen.player-break.siege-enabled", true);
    }

    public long siegeBreakDelayMillis() {
        return Math.max(5L, configManager.getLong(
                "territory.protection.regen.player-break.siege-delay-seconds", 300L)) * 1000L;
    }

    public boolean isAlwaysBreakEnabled() {
        return configManager.getBoolean(
                "territory.protection.regen.player-break.always-enabled", false);
    }

    public long alwaysBreakDelayMillis() {
        return Math.max(5L, configManager.getLong(
                "territory.protection.regen.player-break.always-delay-seconds", 120L)) * 1000L;
    }

    public boolean capture(final Block block, final long delayMillis) {
        return capture(block, delayMillis, true);
    }

    public boolean capture(final Block block, final long delayMillis, final boolean loopGuarded) {
        if (!journal.isHealthy() || block.getType() == org.bukkit.Material.TNT) {
            return false;
        }
        if (isPending(block)) {
            return true;
        }
        if (loopGuarded && isRecaptureLooping(block)) {
            return false;
        }

        if (block.getState() instanceof TileState) {
            if (!isTileEntityExplodeEnabled()) {
                return false;
            }
            final String extra;
            try {
                final org.bukkit.structure.Structure snapshot =
                        Bukkit.getStructureManager().createStructure();
                snapshot.fill(block.getLocation(), new org.bukkit.util.BlockVector(1, 1, 1), false);
                final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                Bukkit.getStructureManager().saveStructure(bytes, snapshot);
                extra = NBT_V2_PREFIX + UUID.randomUUID() + ':'
                        + Base64.getEncoder().encodeToString(bytes.toByteArray());
            } catch (final IOException | RuntimeException failure) {
                plugin.getLogger().warning("Tile-entity pillanatkép hiba ("
                        + block.getType() + "): " + failure);
                return false;
            }

            final BlockRegenJournal.Record record = newRecord(block, extra, delayMillis);
            if (!journal.appendPending(record, true)) {
                return false;
            }
            // Clear only after the snapshot is fsynced. A double chest must clear its own half.
            if (block.getState(false) instanceof Chest chest) {
                chest.getBlockInventory().clear();
            } else if (block.getState(false) instanceof InventoryHolder holder) {
                holder.getInventory().clear();
            }
            enqueue(record, block);
            return true;
        }

        final BlockRegenJournal.Record record = newRecord(block, null, delayMillis);
        if (!journal.appendPending(record, false)) {
            return false;
        }
        enqueue(record, block);
        return true;
    }

    private BlockRegenJournal.Record newRecord(final Block block, final String extra,
                                                final long delayMillis) {
        return new BlockRegenJournal.Record(journal.nextId(), block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), block.getBlockData().getAsString(),
                extra, System.currentTimeMillis() + delayMillis);
    }

    private void enqueue(final BlockRegenJournal.Record record, final Block block) {
        queue.add(record);
        pendingShield.add(posKey(block));
    }

    private boolean isShieldEnabled() {
        return configManager.getBoolean(
                "territory.protection.regen.physics-shield-enabled", true);
    }

    private long physicsShieldMillis() {
        return Math.max(0L, configManager.getLong(
                "territory.protection.regen.physics-shield-seconds", 300L)) * 1000L;
    }

    public boolean isRestoredShielded(final Block block) {
        if (physicsShield.isEmpty() || !isShieldEnabled()) {
            return false;
        }
        final String key = posKey(block);
        final Long until = physicsShield.get(key);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            physicsShield.remove(key);
            return false;
        }
        return true;
    }

    public boolean isCraterPos(final Block block) {
        return !pendingShield.isEmpty() && isShieldEnabled()
                && pendingShield.contains(posKey(block));
    }

    private boolean isRecaptureLooping(final Block block) {
        final long windowMillis = Math.max(30L, configManager.getLong(
                "territory.protection.regen.recapture-window-seconds", 600L)) * 1000L;
        final int maxRecaptures = Math.max(1, configManager.getInt(
                "territory.protection.regen.max-recaptures", 3));
        final long now = System.currentTimeMillis();
        final long[] entry = captureHistory.computeIfAbsent(
                posKey(block), ignored -> new long[]{0L, now});
        synchronized (entry) {
            if (now - entry[1] > windowMillis) {
                entry[0] = 0L;
                entry[1] = now;
            }
            entry[0]++;
            return entry[0] > maxRecaptures;
        }
    }

    public boolean isPending(final Block block) {
        return pendingShield.contains(posKey(block));
    }

    public boolean isTileEntityExplodeEnabled() {
        return configManager.getBoolean(
                "territory.protection.regen.tile-entity-explode", false);
    }

    public void playWardEffect(final Block block) {
        final Location fx = block.getLocation().add(0.5D, 0.5D, 0.5D);
        block.getWorld().spawnParticle(
                org.bukkit.Particle.ENCHANT, fx, 25, 0.4D, 0.4D, 0.4D, 0.5D);
        block.getWorld().playSound(
                fx, org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7F, 1.6F);
    }

    public static boolean isTileEntity(final Block block) {
        return block.getState() instanceof TileState;
    }

    public void spawnDebris(final Block block, final Location center) {
        if (!configManager.getBoolean("territory.protection.regen.debris-enabled", true)) {
            return;
        }
        final double percent = boundedConfig(
                "territory.protection.regen.debris-percent", 100.0D, 0.0D, 100.0D);
        if (Math.random() * 100.0D >= percent) {
            return;
        }
        final Location from = block.getLocation().add(0.5D, 0.5D, 0.5D);
        final org.bukkit.util.Vector direction =
                from.toVector().subtract(center.toVector());
        if (direction.lengthSquared() < 0.01D) {
            direction.setY(1.0D);
        }

        final double power = boundedConfig(
                "territory.protection.regen.debris-launch-power", 0.6D, 0.0D, 5.0D);
        final double horizontalMultiplier = boundedConfig(
                "territory.protection.regen.debris-horizontal-multiplier", 1.0D, 0.0D, 5.0D);
        final double verticalMultiplier = boundedConfig(
                "territory.protection.regen.debris-vertical-multiplier", 1.0D, 0.0D, 5.0D);
        final double horizontalSpread = boundedConfig(
                "territory.protection.regen.debris-horizontal-spread", 0.0D, 0.0D, 3.0D);
        final double extraUpward = boundedConfig(
                "territory.protection.regen.debris-extra-upward-velocity", 0.0D, 0.0D, 3.0D);

        final org.bukkit.util.Vector radial = direction.normalize().multiply(power);
        final double angle = Math.random() * Math.PI * 2.0D;
        final double spreadRadius = Math.sqrt(Math.random()) * horizontalSpread;
        final double spreadX = Math.cos(angle) * spreadRadius;
        final double spreadZ = Math.sin(angle) * spreadRadius;
        final double baseLift = 0.35D + Math.random() * 0.2D;

        final org.bukkit.entity.FallingBlock debris =
                block.getWorld().spawnFallingBlock(from, block.getBlockData());
        debris.setDropItem(false);
        debris.setCancelDrop(true);
        debris.addScoreboardTag(DEBRIS_TAG);
        debris.setGravity(configManager.getBoolean(
                "territory.protection.regen.debris-gravity-enabled", true));
        debris.setVelocity(new org.bukkit.util.Vector(
                radial.getX() * horizontalMultiplier + spreadX,
                (radial.getY() + baseLift) * verticalMultiplier + extraUpward,
                radial.getZ() * horizontalMultiplier + spreadZ));

        final long lifetimeTicks = Math.max(20L, configManager.getLong(
                "territory.protection.regen.debris-lifetime-seconds", 4L) * 20L);
        debris.getScheduler().runDelayed(plugin, task -> {
            if (debris.isValid()) {
                debris.getWorld().spawnParticle(org.bukkit.Particle.BLOCK_CRUMBLE,
                        debris.getLocation(), 12, 0.2D, 0.2D, 0.2D,
                        block.getBlockData());
                debris.remove();
            }
        }, null, lifetimeTicks);
    }

    private double boundedConfig(final String key, final double fallback,
                                 final double minimum, final double maximum) {
        final double configured = configManager.getDouble(key, fallback);
        if (!Double.isFinite(configured)) {
            return fallback;
        }
        return Math.max(minimum, Math.min(maximum, configured));
    }

    /**
     * One or more schedulers may call this method. The CAS window guarantees a single pass and
     * detects interval changes live; changing restore-interval-ticks resets the wait immediately.
     */
    public void tick() {
        if (!acquireTickWindow() || !journal.isHealthy()) {
            return;
        }
        final long now = System.currentTimeMillis();
        physicsShield.values().removeIf(until -> until <= now);
        final long historyWindow = Math.max(30L, configManager.getLong(
                "territory.protection.regen.recapture-window-seconds", 600L)) * 1000L;
        captureHistory.values().removeIf(value -> now - value[1] > historyWindow);
        retryAfter.values().removeIf(until -> until <= now);
        inFlight.values().removeIf(since -> now - since > IN_FLIGHT_TIMEOUT_MILLIS);

        final List<BlockRegenJournal.Record> due = new ArrayList<>();
        for (final BlockRegenJournal.Record record : queue) {
            if (record.restoreAt() <= now && !inFlight.containsKey(record.id())
                    && !retryAfter.containsKey(record.id())
                    && !persistenceProofInFlight.contains(record.id())) {
                due.add(record);
                if (due.size() >= blocksPerPass()) {
                    break;
                }
            }
        }
        due.sort(Comparator.comparingInt(BlockRegenJournal.Record::y));
        for (final BlockRegenJournal.Record record : due) {
            dispatch(record, now);
        }
    }

    private boolean acquireTickWindow() {
        final long intervalTicks = restoreIntervalTicks();
        if (lastIntervalTicks.getAndSet(intervalTicks) != intervalTicks) {
            nextPassNanos.set(0L);
        }
        final long now = System.nanoTime();
        while (true) {
            final long next = nextPassNanos.get();
            if (now < next) {
                return false;
            }
            if (nextPassNanos.compareAndSet(next, now + intervalTicks * NANOS_PER_TICK)) {
                return true;
            }
        }
    }

    private void dispatch(final BlockRegenJournal.Record record, final long now) {
        final World world = Bukkit.getWorld(record.world());
        if (world == null) {
            retryAfter.put(record.id(), now + WORLD_WAIT_MILLIS);
            if (missingWorldLogged.add(record.world())) {
                plugin.getLogger().warning("A(z) " + record.world()
                        + " világ nincs betöltve — a blokk-visszaépítés vár rá.");
            }
            return;
        }
        missingWorldLogged.remove(record.world());
        final Location location =
                new Location(world, record.x(), record.y(), record.z());
        inFlight.put(record.id(), now);
        if (applyingMarked.add(record.id()) && !journal.markApplying(record)) {
            applyingMarked.remove(record.id());
            defer(record, RETRY_MILLIS);
            return;
        }

        Bukkit.getRegionScheduler().run(plugin, location, task -> {
            if (!queue.contains(record)) {
                clearInFlight(record);
                return;
            }
            final Block target =
                    world.getBlockAt(record.x(), record.y(), record.z());

            if (record.extra() != null) {
                final MarkerStatus marker = markerStatus(target, record);
                if (marker == MarkerStatus.PRIOR_BOOT) {
                    if (!commit(record)) {
                        defer(record, RETRY_MILLIS);
                    }
                    return;
                }
                if (marker == MarkerStatus.CURRENT_BOOT) {
                    requestPersistenceProof(record, world, location);
                    return;
                }
            }

            restore(record, world, location, target);
        });
    }

    private void restore(final BlockRegenJournal.Record record, final World world,
                         final Location location, final Block target) {
        try {
            final org.bukkit.block.data.BlockData data =
                    Bukkit.createBlockData(record.blockData());
            if (!target.getLocation().toCenterLocation()
                    .getNearbyLivingEntities(0.9D).isEmpty()) {
                defer(record, RETRY_MILLIS);
                return;
            }
            final long now = System.currentTimeMillis();
            if (now - record.restoreAt() <= supportGraceMillis()) {
                final boolean unsupported = data.getMaterial().hasGravity()
                        ? !target.getRelative(org.bukkit.block.BlockFace.DOWN).isSolid()
                        : !data.isSupported(location);
                if (unsupported) {
                    defer(record, RETRY_MILLIS);
                    return;
                }
            }

            target.setBlockData(data, false);
            if (record.extra() != null && !restoreExtra(target, record)) {
                defer(record, RETRY_MILLIS);
                return;
            }
            invalidRecordLogged.remove(record.id());
            restoreFailureLogged.remove(record.id());
            applyRestoreEffects(world, location, data);
            final long shield = physicsShieldMillis();
            if (shield > 0L) {
                physicsShield.put(posKey(target), System.currentTimeMillis() + shield);
            }

            if (record.extra() != null) {
                requestPersistenceProof(record, world, location);
            } else if (!commit(record)) {
                defer(record, RETRY_MILLIS);
            }
        } catch (final IllegalArgumentException invalid) {
            if (invalidRecordLogged.add(record.id())) {
                plugin.getLogger().severe("Visszaépíthetetlen blokk-adat MEGTARTVA kézi "
                        + "javításhoz (" + record.blockData() + " @ " + record.world() + " "
                        + record.x() + "," + record.y() + "," + record.z() + "): "
                        + invalid.getMessage());
            }
            defer(record, PERSISTENCE_PROOF_RETRY_MILLIS);
        }
    }

    private boolean restoreExtra(final Block block,
                                 final BlockRegenJournal.Record record) {
        final SnapshotPayload payload;
        try {
            payload = snapshotPayload(record);
        } catch (final RuntimeException malformed) {
            if (restoreFailureLogged.add(record.id())) {
                plugin.getLogger().severe("Tile-entity pillanatkép sérült, a rekord MEGMARAD ("
                        + record.world() + " " + record.x() + "," + record.y() + ","
                        + record.z() + "): " + malformed.getMessage());
            }
            return false;
        }

        try {
            // A failed/partial previous placement may already have populated the live container.
            // Clear it before replay so retry is replacement, never additive duplication.
            if (block.getState(false) instanceof Chest chest) {
                chest.getBlockInventory().clear();
            } else if (block.getState(false) instanceof InventoryHolder holder) {
                holder.getInventory().clear();
            }

            final org.bukkit.structure.Structure snapshot =
                    Bukkit.getStructureManager().loadStructure(
                            new ByteArrayInputStream(payload.bytes()));
            snapshot.place(block.getLocation(), false,
                    org.bukkit.block.structure.StructureRotation.NONE,
                    org.bukkit.block.structure.Mirror.NONE,
                    0, 1.0F, new Random());

            if (!(block.getState(false) instanceof TileState tile)) {
                throw new IllegalStateException(
                        "A struktúra-visszaállítás után nincs TileState.");
            }
            tile.getPersistentDataContainer().set(
                    REGEN_TOKEN, PersistentDataType.STRING, payload.token());
            tile.getPersistentDataContainer().set(
                    REGEN_BOOT, PersistentDataType.STRING, PROCESS_BOOT);
            if (!tile.update(true, false)) {
                throw new IllegalStateException("A TileState marker update(false)-t adott.");
            }
            return true;
        } catch (final IOException | RuntimeException failure) {
            if (restoreFailureLogged.add(record.id())) {
                plugin.getLogger().severe("Tile-entity visszaállítás hiba, a rekord MEGMARAD ("
                        + record.world() + " " + record.x() + "," + record.y() + ","
                        + record.z() + "): " + failure);
            }
            return false;
        }
    }

    private MarkerStatus markerStatus(final Block block,
                                      final BlockRegenJournal.Record record) {
        final SnapshotPayload payload;
        try {
            payload = snapshotPayload(record);
        } catch (final RuntimeException malformed) {
            return MarkerStatus.NONE;
        }
        if (!(block.getState(false) instanceof TileState tile)) {
            return MarkerStatus.NONE;
        }
        final String token = tile.getPersistentDataContainer().get(
                REGEN_TOKEN, PersistentDataType.STRING);
        if (!payload.token().equals(token)) {
            return MarkerStatus.NONE;
        }
        final String boot = tile.getPersistentDataContainer().get(
                REGEN_BOOT, PersistentDataType.STRING);
        if (boot == null) {
            return MarkerStatus.NONE;
        }
        return PROCESS_BOOT.equals(boot)
                ? MarkerStatus.CURRENT_BOOT : MarkerStatus.PRIOR_BOOT;
    }

    private SnapshotPayload snapshotPayload(final BlockRegenJournal.Record record) {
        final String extra = record.extra();
        if (extra == null) {
            throw new IllegalArgumentException("hiányzó extra");
        }
        if (extra.startsWith(NBT_V2_PREFIX)) {
            final int tokenEnd = extra.indexOf(':', NBT_V2_PREFIX.length());
            if (tokenEnd < 0) {
                throw new IllegalArgumentException("hiányzó nbt2 token");
            }
            final String token = extra.substring(NBT_V2_PREFIX.length(), tokenEnd);
            UUID.fromString(token);
            final byte[] bytes = Base64.getDecoder().decode(extra.substring(tokenEnd + 1));
            if (bytes.length == 0) {
                throw new IllegalArgumentException("üres nbt2 snapshot");
            }
            return new SnapshotPayload(token, bytes);
        }
        if (extra.startsWith(NBT_PREFIX)) {
            final byte[] bytes = Base64.getDecoder().decode(extra.substring(NBT_PREFIX.length()));
            if (bytes.length == 0) {
                throw new IllegalArgumentException("üres legacy NBT snapshot");
            }
            final String identity = record.world() + ';' + record.x() + ';' + record.y()
                    + ';' + record.z() + ';' + record.restoreAt() + ';' + extra;
            final String token = UUID.nameUUIDFromBytes(
                    identity.getBytes(StandardCharsets.UTF_8)).toString();
            return new SnapshotPayload(token, bytes);
        }
        throw new IllegalArgumentException("ismeretlen extra formátum");
    }


    /**
     * Saves and flushes the world on the global-region scheduler, then returns to the owning
     * region to verify the token and durably close the WAL entry. Interactions with pending
     * positions are cancelled by this listener until that proof completes.
     */
    private void requestPersistenceProof(final BlockRegenJournal.Record record,
                                         final World world, final Location location) {
        clearInFlight(record);
        if (!persistenceProofInFlight.add(record.id())) {
            retryAfter.put(record.id(),
                    System.currentTimeMillis() + PERSISTENCE_PROOF_RETRY_MILLIS);
            return;
        }
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                world.save(true);
            } catch (final RuntimeException failure) {
                persistenceProofInFlight.remove(record.id());
                if (restoreFailureLogged.add(record.id())) {
                    plugin.getLogger().severe("A világ tartós flush-a sikertelen, a block-regen "
                            + "rekord MEGMARAD (" + record.world() + "): " + failure);
                }
                defer(record, PERSISTENCE_PROOF_RETRY_MILLIS);
                return;
            }
            Bukkit.getRegionScheduler().run(plugin, location, regionTask -> {
                persistenceProofInFlight.remove(record.id());
                if (!queue.contains(record)) {
                    clearInFlight(record);
                    return;
                }
                final Block target = world.getBlockAt(
                        record.x(), record.y(), record.z());
                if (markerStatus(target, record) == MarkerStatus.NONE) {
                    defer(record, RETRY_MILLIS);
                    return;
                }
                if (!commit(record)) {
                    defer(record, RETRY_MILLIS);
                }
            });
        });
    }

    // ==================== pending-position isolation ====================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPendingBreak(final BlockBreakEvent event) {
        if (isPending(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPendingPlace(final BlockPlaceEvent event) {
        if (isPending(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPendingInteract(final PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && isPending(event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPendingInventoryMove(final InventoryMoveItemEvent event) {
        final Location source = event.getSource().getLocation();
        final Location destination = event.getDestination().getLocation();
        if (isPending(source) || isPending(destination)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPendingEntityExplosion(final EntityExplodeEvent event) {
        event.blockList().removeIf(this::isPending);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPendingBlockExplosion(final BlockExplodeEvent event) {
        event.blockList().removeIf(this::isPending);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPendingLiquid(final BlockFromToEvent event) {
        if (isPending(event.getBlock()) || isPending(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPendingPistonExtend(final BlockPistonExtendEvent event) {
        if (pistonTouchesPending(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPendingPistonRetract(final BlockPistonRetractEvent event) {
        if (pistonTouchesPending(event.getBlocks(), event.getDirection().getOppositeFace())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonTouchesPending(final List<Block> blocks,
                                         final org.bukkit.block.BlockFace movement) {
        for (final Block block : blocks) {
            if (isPending(block) || isPending(block.getRelative(movement))) {
                return true;
            }
        }
        return false;
    }

    private boolean isPending(final Location location) {
        return location != null && location.getWorld() != null
                && pendingShield.contains(location.getWorld().getName() + ';'
                + location.getBlockX() + ';' + location.getBlockY() + ';'
                + location.getBlockZ());
    }

    private void applyRestoreEffects(final World world, final Location location,
                                     final org.bukkit.block.data.BlockData data) {
        if (!configManager.getBoolean(
                "territory.protection.regen.restore-effects-enabled", true)) {
            return;
        }
        final Location effect = location.clone().add(0.5D, 0.5D, 0.5D);
        world.playSound(effect, data.getSoundGroup().getPlaceSound(), 0.6F,
                0.8F + (float) (Math.random() * 0.4D));
        world.spawnParticle(org.bukkit.Particle.CLOUD,
                effect, 4, 0.25D, 0.25D, 0.25D, 0.01D);
    }

    private boolean commit(final BlockRegenJournal.Record record) {
        if (!journal.markApplied(record)) {
            return false;
        }
        queue.remove(record);
        pendingShield.remove(posKey(record));
        clearInFlight(record);
        applyingMarked.remove(record.id());
        invalidRecordLogged.remove(record.id());
        restoreFailureLogged.remove(record.id());
        return true;
    }

    private void defer(final BlockRegenJournal.Record record, final long delayMillis) {
        retryAfter.put(record.id(), System.currentTimeMillis() + delayMillis);
        inFlight.remove(record.id());
    }

    private void clearInFlight(final BlockRegenJournal.Record record) {
        inFlight.remove(record.id());
        retryAfter.remove(record.id());
    }

    private static String posKey(final Block block) {
        return block.getWorld().getName() + ';' + block.getX()
                + ';' + block.getY() + ';' + block.getZ();
    }

    private static String posKey(final BlockRegenJournal.Record record) {
        return record.world() + ';' + record.x()
                + ';' + record.y() + ';' + record.z();
    }

    private static String processBootId() {
        final String identity = ManagementFactory.getRuntimeMXBean().getStartTime()
                + ":" + ManagementFactory.getRuntimeMXBean().getName();
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Override
    public void load() {
        queue.clear();
        pendingShield.clear();
        inFlight.clear();
        retryAfter.clear();
        applyingMarked.clear();
        invalidRecordLogged.clear();
        restoreFailureLogged.clear();
        persistenceProofInFlight.clear();
        missingWorldLogged.clear();
        for (final BlockRegenJournal.Record record : journal.loadAll()) {
            queue.add(record);
            pendingShield.add(posKey(record));
        }
        nextPassNanos.set(0L);
        lastIntervalTicks.set(-1L);
        if (dynamicTickerStarted.compareAndSet(false, true)) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                    plugin, task -> tick(), 1L, 1L);
        }
    }

    @Override
    public synchronized void save() {
        try {
            journal.checkpoint(queue);
        } catch (final IOException failure) {
            plugin.getLogger().severe(
                    "block-regen checkpoint mentési hiba: " + failure);
            throw new IllegalStateException(
                    "A block-regen journal checkpointja nem írható.", failure);
        }
    }
}
