package hu.taliann.icesmp.utils;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.function.Consumer;

public final class DisplayFxUtil {
    private DisplayFxUtil() {}
    public static final String FX_TAG = "icesmp_fx";
    private static final AxisAngle4f NO_ROT = new AxisAngle4f(0.0F, 0.0F, 0.0F, 1.0F);

    public static void spawnBlockDisplay(final Plugin plugin, final Location loc, final BlockData block,
                                         final int despawnTicks, final Consumer<BlockDisplay> setup) {
        if (plugin == null || loc == null || loc.getWorld() == null || block == null) return;
        plugin.getServer().getRegionScheduler().run(plugin, loc, task -> {
            final BlockDisplay display = loc.getWorld().spawn(loc, BlockDisplay.class, entity -> {
                entity.setBlock(block);
                entity.setPersistent(false);
                entity.addScoreboardTag(FX_TAG);
            });
            TransientEntities.register(plugin, display);
            if (setup != null) setup.accept(display);
            if (despawnTicks > 0) scheduleDespawn(plugin, display, despawnTicks);
        });
    }

    public static void spawnItemDisplay(final Plugin plugin, final Location loc, final ItemStack item,
                                        final int despawnTicks, final Consumer<ItemDisplay> setup) {
        if (plugin == null || loc == null || loc.getWorld() == null || item == null) return;
        plugin.getServer().getRegionScheduler().run(plugin, loc, task -> {
            final ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, entity -> {
                entity.setItemStack(item);
                entity.setPersistent(false);
                entity.addScoreboardTag(FX_TAG);
            });
            TransientEntities.register(plugin, display);
            if (setup != null) setup.accept(display);
            if (despawnTicks > 0) scheduleDespawn(plugin, display, despawnTicks);
        });
    }

    public static void showOnlyTo(final Plugin plugin, final Entity fx, final Player viewer) {
        if (plugin == null || fx == null || viewer == null) return;
        final UUID viewerId = viewer.getUniqueId();
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewerId)) continue;
            online.getScheduler().run(plugin, task -> online.hideEntity(plugin, fx), null);
        }
    }

    public static void animateTo(final Plugin plugin, final Display display, final Transformation target,
                                 final int durationTicks) {
        if (plugin == null || display == null || target == null) return;
        display.getScheduler().runDelayed(plugin, task -> {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(durationTicks);
            display.setTransformation(target);
        }, null, 1L);
    }

    public static Transformation scale(final float x, final float y, final float z) {
        return new Transformation(new Vector3f(0.0F, 0.0F, 0.0F), NO_ROT,
                new Vector3f(x, y, z), NO_ROT);
    }

    public static void driftHorizontal(final Plugin plugin, final Display display, final float sizeXZ,
                                       final float thickness, final float driftX, final float driftZ,
                                       final int durationTicks) {
        animateTo(plugin, display, new Transformation(new Vector3f(driftX, 0.0F, driftZ), NO_ROT,
                new Vector3f(sizeXZ, thickness, sizeXZ), NO_ROT), durationTicks);
    }

    public static Transformation flatCentered(final float sizeXZ, final float thickness) {
        final float half = sizeXZ / 2.0F;
        return new Transformation(new Vector3f(-half, 0.0F, -half), NO_ROT,
                new Vector3f(sizeXZ, thickness, sizeXZ), NO_ROT);
    }

    public static void groundTelegraph(final Plugin plugin, final Location center, final double radius,
                                       final int growTicks, final Color glow, final BlockData block) {
        if (center == null) return;
        final float full = (float) (radius * 2.0D);
        spawnBlockDisplay(plugin, center.clone().add(0.0D, 0.05D, 0.0D), block, growTicks + 12, display -> {
            display.setTransformation(flatCentered(0.2F, 0.12F));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(3.0F);
            if (glow != null) {
                display.setGlowing(true);
                display.setGlowColorOverride(glow);
            }
            animateTo(plugin, display, flatCentered(full, 0.12F), growTicks);
        });
    }

    public static void wallSegment(final Plugin plugin, final Location corner, final float sizeX,
                                   final float sizeY, final float sizeZ, final BlockData block,
                                   final Color glow, final int despawnTicks, final Player viewer) {
        spawnBlockDisplay(plugin, corner, block, despawnTicks, display -> {
            display.setTransformation(scale(sizeX, sizeY, sizeZ));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(2.0F);
            if (glow != null) {
                display.setGlowing(true);
                display.setGlowColorOverride(glow);
            }
            if (viewer != null) showOnlyTo(plugin, display, viewer);
        });
    }

    /**
     * Spawns one vertical wall column from the real terrain surface of the sampled
     * X/Z column. Region ownership is acquired for every boundary column.
     */
    public static void terrainWallColumn(final Plugin plugin, final World world,
                                         final int sampleX, final int sampleZ,
                                         final double displayX, final double displayZ,
                                         final float sizeX, final float sizeY, final float sizeZ,
                                         final BlockData block, final Color glow,
                                         final int despawnTicks, final Player viewer) {
        if (plugin == null || world == null || block == null || sizeY <= 0.0F) return;
        final Location owner = new Location(world, sampleX + 0.5D, world.getMinHeight(), sampleZ + 0.5D);
        plugin.getServer().getRegionScheduler().run(plugin, owner, task -> {
            if (!world.isChunkLoaded(sampleX >> 4, sampleZ >> 4)) return;
            final int floorY = world.getHighestBlockYAt(
                    sampleX, sampleZ, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            final Location corner = new Location(world, displayX, floorY + 1.0D, displayZ);
            wallSegment(plugin, corner, sizeX, sizeY, sizeZ,
                    block, glow, despawnTicks, viewer);
        });
    }

    /**
     * Spawns one wall column exactly inside an inclusive claimed Y range.
     * Region ownership is acquired from the sampled X/Z column; nothing is rendered
     * below minY or above maxY.
     */
    public static void claimedWallColumn(final Plugin plugin, final World world,
                                         final int sampleX, final int sampleZ,
                                         final double displayX, final double displayZ,
                                         final float sizeX, final float sizeZ,
                                         final int minY, final int maxY,
                                         final BlockData block, final Color glow,
                                         final int despawnTicks, final Player viewer) {
        if (plugin == null || world == null || block == null) return;
        final int clampedMinY = Math.max(world.getMinHeight(), minY);
        final int clampedMaxY = Math.min(world.getMaxHeight() - 1, maxY);
        if (clampedMinY > clampedMaxY) return;
        final Location owner = new Location(
                world, sampleX + 0.5D, clampedMinY, sampleZ + 0.5D);
        plugin.getServer().getRegionScheduler().run(plugin, owner, task -> {
            if (!world.isChunkLoaded(sampleX >> 4, sampleZ >> 4)) return;
            final Location corner = new Location(world, displayX, clampedMinY, displayZ);
            wallSegment(plugin, corner, sizeX,
                    clampedMaxY - clampedMinY + 1.0F, sizeZ,
                    block, glow, despawnTicks, viewer);
        });
    }

    private static void scheduleDespawn(final Plugin plugin, final Display display, final int ticks) {
        final UUID id = display.getUniqueId();
        display.getScheduler().runDelayed(plugin, task -> {
            display.remove();
            TransientEntities.markGone(id);
        }, () -> TransientEntities.markGone(id), ticks);
    }
}
