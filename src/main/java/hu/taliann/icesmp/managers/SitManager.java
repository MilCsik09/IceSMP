package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.moderation.EntityTaskSubmission;
import hu.taliann.icesmp.moderation.PaperEntityTaskSubmission;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import hu.taliann.icesmp.sit.SitGeometry;
import hu.taliann.icesmp.sit.SitPolicy;
import hu.taliann.icesmp.utils.TransientEntities;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** IceSMP-specific, Folia-owned, sit-only seat manager. */
public final class SitManager implements PlayerStateCleanup {

    public enum SitOrigin { COMMAND, CLICK }

    public enum SitResult {
        OK,
        DISABLED,
        ALREADY_SITTING,
        NOT_ON_GROUND,
        IN_LIQUID,
        IN_VEHICLE,
        WORLD_DISABLED,
        MATERIAL_NOT_ALLOWED,
        TOO_FAR,
        UNSAFE,
        OCCUPIED,
        FOREIGN_REGION,
        OBSTRUCTED
    }

    private enum AllowedTag {
        STAIRS(Tag.STAIRS),
        SLABS(Tag.SLABS),
        CARPETS(Tag.CARPETS);

        private final Tag<Material> tag;

        AllowedTag(final Tag<Material> tag) {
            this.tag = tag;
        }

        boolean matches(final Material material) {
            return tag.isTagged(material);
        }
    }

    private record Settings(boolean enabled,
                            boolean clickToSit,
                            boolean emptyHandOnly,
                            double maxClickDistance,
                            boolean allowUnsafeLocations,
                            boolean standUpOnDamage,
                            boolean standUpOnSneak,
                            boolean standUpOnBlockBreak,
                            Set<String> worldWhitelist,
                            Set<String> worldBlacklist,
                            Set<AllowedTag> allowedTags,
                            Set<Material> allowedMaterials,
                            Set<Material> materialBlacklist,
                            Set<String> blockedCommands) {
        static Settings disabled() {
            return new Settings(false, false, true, 4.5D, false,
                    true, true, true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        }
    }

    private static final long SHUTDOWN_DRAIN_MILLIS = 750L;
    private static final Set<Material> HAZARDOUS_MATERIALS = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.CACTUS,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.POWDER_SNOW,
            Material.POINTED_DRIPSTONE
    );

    private static final class SeatEntityHandle {
        private final UUID id;
        private final ArmorStand stand;
        private final EntityScheduler scheduler;
        private final Location anchor;
        private final AtomicBoolean removalStarted = new AtomicBoolean(false);
        private final CompletableFuture<Boolean> removed = new CompletableFuture<>();

        private SeatEntityHandle(final ArmorStand stand, final Location anchor) {
            this.id = stand.getUniqueId();
            this.stand = stand;
            this.scheduler = stand.getScheduler();
            this.anchor = anchor.clone();
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey seatKey;
    private final SitState seatState = new SitState();
    private final Map<UUID, SeatEntityHandle> handles = new ConcurrentHashMap<>();
    private volatile Settings settings;
    private volatile boolean shuttingDown;

    public SitManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.seatKey = new NamespacedKey(plugin, "icesmp_seat");
        this.settings = readSettings();
    }

    public boolean isEnabled() {
        return !shuttingDown && settings.enabled();
    }

    public boolean isClickToSitEnabled() {
        final Settings current = settings;
        return !shuttingDown && current.enabled() && current.clickToSit();
    }

    public boolean isEmptyHandOnly() { return settings.emptyHandOnly(); }
    public boolean shouldStandUpOnDamage() { return settings.standUpOnDamage(); }
    public boolean shouldStandUpOnSneak() { return settings.standUpOnSneak(); }
    public boolean shouldStandUpOnBlockBreak() { return settings.standUpOnBlockBreak(); }
    public boolean isSitting(final UUID playerId) { return seatState.isSeated(playerId); }
    public boolean hasActiveState(final UUID playerId) { return seatState.get(playerId) != null; }
    public boolean hasActiveSits() { return seatState.size() > 0; }
    public boolean isBlockedCommand(final String rawCommand) {
        return SitPolicy.isCommandBlocked(settings.blockedCommands(), rawCommand);
    }

    /** Cheap material/world prefilter used before a right-click is claimed by the listener. */
    public boolean isConfiguredSeatBlock(final Block block) {
        if (block == null || !isEnabled() || !Bukkit.isOwnedByCurrentRegion(block)) {
            return false;
        }
        final Settings current = settings;
        return worldAllowed(current, block.getWorld()) && materialAllowed(current, block.getType());
    }

    /** Creates a seat only while the current thread owns both the player and support block. */
    public SitResult sit(final Player player, final Block supportBlock, final SitOrigin origin) {
        final Settings current = settings;
        if (shuttingDown || !current.enabled()) {
            return SitResult.DISABLED;
        }
        if (player == null || supportBlock == null
                || !Bukkit.isOwnedByCurrentRegion(player)
                || !Bukkit.isOwnedByCurrentRegion(supportBlock)) {
            return SitResult.FOREIGN_REGION;
        }
        final UUID playerId = player.getUniqueId();
        if (seatState.get(playerId) != null) {
            return SitResult.ALREADY_SITTING;
        }
        if (!worldAllowed(current, player.getWorld())
                || !player.getWorld().getUID().equals(supportBlock.getWorld().getUID())) {
            return SitResult.WORLD_DISABLED;
        }
        if (origin == SitOrigin.CLICK && !current.clickToSit()) {
            return SitResult.DISABLED;
        }
        if (origin == SitOrigin.CLICK && !withinClickDistance(player, supportBlock, current.maxClickDistance())) {
            return SitResult.TOO_FAR;
        }
        if (!materialAllowed(current, supportBlock.getType())) {
            return SitResult.MATERIAL_NOT_ALLOWED;
        }
        if (!current.allowUnsafeLocations() && !isSafeSeatBlock(supportBlock)) {
            return SitResult.UNSAFE;
        }
        if (player.isInsideVehicle()) {
            return SitResult.IN_VEHICLE;
        }
        if (player.isInWater() || player.getLocation().getBlock().isLiquid()) {
            return SitResult.IN_LIQUID;
        }
        if (!player.isOnGround()) {
            return SitResult.NOT_ON_GROUND;
        }

        final SitState.SeatKey key = seatKey(supportBlock);
        final SitState.ReserveResult reserved = seatState.reserve(playerId, key);
        if (reserved == SitState.ReserveResult.PLAYER_BUSY) {
            return SitResult.ALREADY_SITTING;
        }
        if (reserved == SitState.ReserveResult.BLOCK_OCCUPIED) {
            return SitResult.OCCUPIED;
        }

        SeatEntityHandle handle = null;
        try {
            final Location seatLocation = computeSeatLocation(supportBlock);
            final ArmorStand stand = supportBlock.getWorld().spawn(seatLocation, ArmorStand.class, spawned -> {
                spawned.setInvisible(true);
                spawned.setMarker(true);
                spawned.setGravity(false);
                spawned.setInvulnerable(true);
                spawned.setPersistent(false);
                spawned.setSilent(true);
                spawned.setCanPickupItems(false);
                spawned.getPersistentDataContainer().set(seatKey, PersistentDataType.BYTE, (byte) 1);
            });
            TransientEntities.register(plugin, stand);
            handle = new SeatEntityHandle(stand, seatLocation);
            handles.put(handle.id, handle);
            if (!seatState.activate(playerId, key, handle.id)) {
                seatState.release(playerId);
                removeHandle(handle);
                return SitResult.OCCUPIED;
            }
            if (!stand.addPassenger(player)) {
                final SitState.SeatLease released = seatState.release(playerId);
                removeReleasedStand(released);
                return SitResult.OBSTRUCTED;
            }
            return SitResult.OK;
        } catch (final RuntimeException failure) {
            final SitState.SeatLease released = seatState.release(playerId);
            if (handle != null) {
                removeHandle(handle);
            } else {
                removeReleasedStand(released);
            }
            plugin.getLogger().warning("Ülőhely létrehozása sikertelen: " + failure.getMessage());
            return SitResult.OBSTRUCTED;
        }
    }

    /** Region-local stand-up; callers must already own the player. */
    public boolean standUp(final Player player) {
        if (player == null || !Bukkit.isOwnedByCurrentRegion(player)) {
            return false;
        }
        final SitState.SeatLease lease = seatState.release(player.getUniqueId());
        if (lease == null) {
            return false;
        }
        final UUID standId = lease.standId();
        final Entity vehicle = player.getVehicle();
        if (vehicle != null && standId != null && standId.equals(vehicle.getUniqueId())) {
            player.leaveVehicle();
            final SeatEntityHandle handle = handles.get(standId);
            if (handle != null) {
                removeHandleHere(handle);
            } else if (vehicle.isValid()) {
                vehicle.remove();
                TransientEntities.markGone(standId);
            }
        } else {
            removeReleasedStand(lease);
        }
        return true;
    }

    /** Completes a dismount without recursively calling leaveVehicle from inside the event. */
    public boolean completeDismount(final Player player, final Entity dismounted) {
        if (player == null || dismounted == null
                || !Bukkit.isOwnedByCurrentRegion(player)
                || !Bukkit.isOwnedByCurrentRegion(dismounted)) {
            return false;
        }
        final SitState.SeatLease current = seatState.get(player.getUniqueId());
        if (current == null || current.standId() == null
                || !current.standId().equals(dismounted.getUniqueId())) {
            return false;
        }
        final SitState.SeatLease released = seatState.release(player.getUniqueId());
        final SeatEntityHandle handle = handles.get(released.standId());
        if (handle != null) {
            removeHandleHere(handle);
        } else {
            if (dismounted.isValid()) {
                dismounted.remove();
            }
            TransientEntities.markGone(released.standId());
        }
        return true;
    }

    public boolean resetPlayer(final Player player) {
        return standUp(player);
    }

    /** Scheduler-safe reset entry point for block events, reload, quit cleanup and disable. */
    public void requestReset(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            releaseAndRemove(playerId);
            return;
        }
        PaperEntityTaskSubmission.run(plugin, player.getScheduler(),
                () -> resetPlayer(player),
                () -> releaseAndRemove(playerId));
    }

    public UUID findSitterOnBlock(final Block block) {
        if (block == null || !hasActiveSits()) {
            return null;
        }
        return seatState.occupant(seatKey(block));
    }

    /** Replaces the immutable policy and terminalizes active sessions from the old generation. */
    public void reload() {
        settings = readSettings();
        requestResetAll();
    }

    /** Stops admissions, tracks owner-scheduler removals and performs a short bounded drain. */
    public void shutdown() {
        shuttingDown = true;
        settings = Settings.disabled();
        requestResetAll();
        final CompletableFuture<?>[] pending = handles.values().stream()
                .map(handle -> handle.removed)
                .toArray(CompletableFuture[]::new);
        if (pending.length == 0) {
            return;
        }
        try {
            CompletableFuture.allOf(pending).get(SHUTDOWN_DRAIN_MILLIS, TimeUnit.MILLISECONDS);
        } catch (final Exception timeoutOrFailure) {
            plugin.getLogger().warning("Sit shutdown: " + handles.size()
                    + " seat entity eltávolítása még függőben; mind nem-persistent és PDC-azonosított.");
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        requestReset(playerId);
    }

    private void requestResetAll() {
        for (final UUID playerId : seatState.playerIds()) {
            requestReset(playerId);
        }
    }

    private void releaseAndRemove(final UUID playerId) {
        final SitState.SeatLease released = seatState.release(playerId);
        removeReleasedStand(released);
    }

    private void removeReleasedStand(final SitState.SeatLease lease) {
        if (lease == null || lease.standId() == null) {
            return;
        }
        final SeatEntityHandle handle = handles.get(lease.standId());
        if (handle != null) {
            removeHandle(handle);
        } else {
            TransientEntities.removeById(plugin, lease.standId());
        }
    }

    private void removeHandle(final SeatEntityHandle handle) {
        if (handle == null || !handle.removalStarted.compareAndSet(false, true)) {
            return;
        }
        if (Bukkit.isOwnedByCurrentRegion(handle.stand)) {
            removeHandleHere(handle);
            return;
        }
        PaperEntityTaskSubmission.run(plugin, handle.scheduler,
                () -> removeHandleHere(handle),
                () -> scheduleRegionFallback(handle));
    }

    private void scheduleRegionFallback(final SeatEntityHandle handle) {
        EntityTaskSubmission.submit(
                (task, ignored) -> plugin.getServer().getRegionScheduler().run(
                        plugin, handle.anchor, scheduled -> task.run()),
                () -> removeHandleHere(handle),
                () -> requestRegistryRemoval(handle));
    }

    /**
     * Last-resort removal request through the captured entity scheduler registry. The local handle
     * is retired only after this safe fallback has been requested; the ArmorStand is non-persistent.
     */
    private void requestRegistryRemoval(final SeatEntityHandle handle) {
        TransientEntities.removeById(plugin, handle.id);
        finishRemoval(handle, false);
    }

    private void removeHandleHere(final SeatEntityHandle handle) {
        boolean removed = false;
        try {
            if (handle.stand.isValid()) {
                handle.stand.remove();
            }
            removed = true;
            TransientEntities.markGone(handle.id);
        } catch (final RuntimeException failure) {
            plugin.getLogger().warning("Sit seat entity eltávolítása sikertelen: " + failure.getMessage());
            TransientEntities.removeById(plugin, handle.id);
        } finally {
            finishRemoval(handle, removed);
        }
    }

    private void finishRemoval(final SeatEntityHandle handle, final boolean removed) {
        handles.remove(handle.id, handle);
        handle.removed.complete(removed);
    }

    private SitState.SeatKey seatKey(final Block block) {
        return new SitState.SeatKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private Location computeSeatLocation(final Block block) {
        final BlockData data = block.getBlockData();
        final SitGeometry.Shape shape;
        final int snowLayers;
        if (data instanceof Stairs stairs) {
            shape = stairs.getHalf() == Bisected.Half.TOP
                    ? SitGeometry.Shape.STAIRS_TOP : SitGeometry.Shape.STAIRS_BOTTOM;
            snowLayers = 1;
        } else if (data instanceof Slab slab) {
            shape = slab.getType() == Slab.Type.BOTTOM
                    ? SitGeometry.Shape.SLAB_BOTTOM : SitGeometry.Shape.SLAB_TOP_OR_DOUBLE;
            snowLayers = 1;
        } else if (data instanceof Snow snow) {
            shape = SitGeometry.Shape.SNOW;
            snowLayers = snow.getLayers();
        } else if (Tag.CARPETS.isTagged(block.getType())
                || block.getType() == Material.MOSS_CARPET
                || block.getType() == Material.PALE_MOSS_CARPET) {
            shape = SitGeometry.Shape.CARPET;
            snowLayers = 1;
        } else {
            shape = SitGeometry.Shape.GENERIC;
            snowLayers = 1;
        }
        final double offset = SitGeometry.offset(shape, snowLayers);
        return new Location(block.getWorld(), block.getX() + 0.5D,
                block.getY() + offset, block.getZ() + 0.5D);
    }

    private boolean withinClickDistance(final Player player, final Block block, final double maxDistance) {
        final Location eye = player.getEyeLocation();
        final Location center = new Location(block.getWorld(), block.getX() + 0.5D,
                block.getY() + 0.5D, block.getZ() + 0.5D);
        return eye.getWorld() != null && eye.getWorld().getUID().equals(block.getWorld().getUID())
                && eye.distanceSquared(center) <= maxDistance * maxDistance;
    }

    private boolean isSafeSeatBlock(final Block block) {
        if (isHazardous(block) || block.isLiquid()) {
            return false;
        }
        final BlockData data = block.getBlockData();
        if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return false;
        }
        final Block feetSpace = block.getRelative(BlockFace.UP);
        final Block headSpace = feetSpace.getRelative(BlockFace.UP);
        return !isHazardous(feetSpace) && !isHazardous(headSpace)
                && !feetSpace.isLiquid() && !headSpace.isLiquid()
                && feetSpace.isPassable() && headSpace.isPassable();
    }

    private boolean isHazardous(final Block block) {
        return block != null && HAZARDOUS_MATERIALS.contains(block.getType());
    }

    private boolean materialAllowed(final Settings current, final Material material) {
        if (material == null || current.materialBlacklist().contains(material)) {
            return false;
        }
        if (current.allowedMaterials().contains(material)) {
            return true;
        }
        for (final AllowedTag tag : current.allowedTags()) {
            if (tag.matches(material)) {
                return true;
            }
        }
        return false;
    }

    private boolean worldAllowed(final Settings current, final World world) {
        return world != null && SitPolicy.isWorldAllowed(
                current.worldWhitelist(), current.worldBlacklist(), world.getName());
    }

    private Settings readSettings() {
        final FileConfiguration config = configManager.getConfiguration();
        if (config == null) {
            plugin.getLogger().warning("A sit konfiguráció nem érhető el; az ülés biztonságosan letiltva.");
            return Settings.disabled();
        }
        final List<String> problems = new ArrayList<>();
        final boolean enabled = readBoolean(config, "sit.enabled", true, problems);
        final boolean clickToSit = readBoolean(config, "sit.click-to-sit", true, problems);
        final boolean emptyHandOnly = readBoolean(config, "sit.empty-hand-only", true, problems);
        final boolean allowUnsafe = readBoolean(config, "sit.allow-unsafe-locations", false, problems);
        final boolean damage = readBoolean(config, "sit.stand-up.damage", true, problems);
        final boolean sneak = readBoolean(config, "sit.stand-up.sneak", true, problems);
        final boolean blockBreak = readBoolean(config, "sit.stand-up.block-break", true, problems);

        double maxDistance = 4.5D;
        try {
            maxDistance = SitPolicy.validateClickDistance(readDouble(
                    config, "sit.max-click-distance", 4.5D, problems));
        } catch (final IllegalArgumentException problem) {
            problems.add("sit.max-click-distance: " + problem.getMessage());
        }

        final Set<String> whitelist = normalizeList(config, "sit.worlds.whitelist", List.of(), problems);
        final Set<String> blacklist = normalizeList(config, "sit.worlds.blacklist", List.of(), problems);
        final Set<String> overlap = new HashSet<>(whitelist);
        overlap.retainAll(blacklist);
        if (!overlap.isEmpty()) {
            problems.add("sit.worlds: ugyanaz a világ whitelistben és blacklistben is szerepel: " + overlap);
        }

        final Set<AllowedTag> tags = parseTags(readStringList(config, "sit.seats.tags",
                List.of("STAIRS", "SLABS", "CARPETS"), problems), problems);
        final Set<Material> materials = parseMaterials(readStringList(config, "sit.seats.materials",
                List.of("MOSS_CARPET", "PALE_MOSS_CARPET", "SNOW"), problems),
                "sit.seats.materials", problems);
        final Set<Material> materialBlacklist = parseMaterials(readStringList(config,
                "sit.seats.material-blacklist",
                List.of("POWDER_SNOW", "MAGMA_BLOCK", "CAMPFIRE", "SOUL_CAMPFIRE"), problems),
                "sit.seats.material-blacklist", problems);

        Set<String> blockedCommands = Set.of();
        try {
            blockedCommands = SitPolicy.normalizeCommandRoots(readStringList(config,
                    "sit.blocked-commands", List.of("home", "spawn", "warp", "tpa", "tpaccept", "rtp"), problems));
        } catch (final IllegalArgumentException problem) {
            problems.add("sit.blocked-commands: " + problem.getMessage());
        }

        if (!problems.isEmpty()) {
            for (final String problem : problems) {
                plugin.getLogger().warning("Sit config: " + problem);
            }
            plugin.getLogger().warning("A natív sit feature hibás konfiguráció miatt biztonságosan letiltva.");
            return Settings.disabled();
        }

        return new Settings(enabled, clickToSit, emptyHandOnly, maxDistance, allowUnsafe,
                damage, sneak, blockBreak, whitelist, blacklist, tags, materials,
                materialBlacklist, blockedCommands);
    }

    private boolean readBoolean(final FileConfiguration config, final String path,
                                final boolean fallback, final List<String> problems) {
        if (!config.isSet(path)) {
            return fallback;
        }
        final Object value = config.get(path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        problems.add(path + " boolean értéket vár, kapott: " + value);
        return fallback;
    }

    private double readDouble(final FileConfiguration config, final String path,
                              final double fallback, final List<String> problems) {
        if (!config.isSet(path)) {
            return fallback;
        }
        final Object value = config.get(path);
        try {
            return SitPolicy.finiteNumber(value);
        } catch (final IllegalArgumentException problem) {
            problems.add(path + " véges numerikus YAML értéket vár, kapott: " + value
                    + " (" + problem.getMessage() + ")");
            return fallback;
        }
    }

    private List<String> readStringList(final FileConfiguration config, final String path,
                                        final List<String> fallback, final List<String> problems) {
        if (!config.isSet(path)) {
            return fallback;
        }
        if (!config.isList(path)) {
            problems.add(path + " string-listát vár");
            return fallback;
        }
        final List<?> raw = config.getList(path, List.of());
        final List<String> result = new ArrayList<>();
        for (final Object value : raw) {
            if (!(value instanceof String text)) {
                problems.add(path + " csak string értékeket tartalmazhat: " + value);
            } else {
                result.add(text);
            }
        }
        return result;
    }

    private Set<String> normalizeList(final FileConfiguration config, final String path,
                                      final List<String> fallback, final List<String> problems) {
        try {
            return SitPolicy.normalizeIdentifiers(readStringList(config, path, fallback, problems));
        } catch (final IllegalArgumentException problem) {
            problems.add(path + ": " + problem.getMessage());
            return Set.of();
        }
    }

    private Set<AllowedTag> parseTags(final List<String> rawTags, final List<String> problems) {
        final Set<AllowedTag> parsed = EnumSet.noneOf(AllowedTag.class);
        for (final String raw : rawTags) {
            try {
                parsed.add(AllowedTag.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (final IllegalArgumentException failure) {
                problems.add("sit.seats.tags: ismeretlen Bukkit tag '" + raw
                        + "' (használható: STAIRS, SLABS, CARPETS)");
            }
        }
        return Set.copyOf(parsed);
    }

    private Set<Material> parseMaterials(final List<String> rawMaterials,
                                         final String path, final List<String> problems) {
        final Set<Material> parsed = EnumSet.noneOf(Material.class);
        for (final String raw : rawMaterials) {
            final Material material = Material.matchMaterial(raw);
            if (material == null || !material.isBlock()) {
                problems.add(path + ": ismeretlen vagy nem blokk Material '" + raw + "'");
            } else {
                parsed.add(material);
            }
        }
        return Set.copyOf(parsed);
    }
}
