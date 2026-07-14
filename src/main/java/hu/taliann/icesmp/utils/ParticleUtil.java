package hu.taliann.icesmp.utils;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Data-safe particle spawning. Some particles REQUIRE a data object on modern Paper
 * (FLASH/ENTITY_EFFECT → {@link Color}, DUST → {@link Particle.DustOptions}, BLOCK →
 * BlockData, ITEM → ItemStack, SCULK_CHARGE → Float, SHRIEK → Integer) — spawning them
 * without one throws {@code IllegalArgumentException: missing required data class ...}
 * from an entity/region task (see the InvasionManager champion-tick console error).
 * This helper inspects {@link Particle#getDataType()} and supplies a sensible neutral
 * default, so hardcoded FLASH bursts and admin-configured particle names can never
 * crash a scheduler task. Unknown data types are skipped (fail-soft) instead of thrown.
 */
public final class ParticleUtil {

    private ParticleUtil() {
    }

    /** Count-only burst (offsets 0, extra 0) — the {@code spawnParticle(p, loc, n)} shape. */
    public static void spawn(final World world, final Particle particle, final Location location, final int count) {
        spawn(world, particle, location, count, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /** World-visible spawn with offsets/extra, supplying required particle data automatically. */
    public static void spawn(final World world, final Particle particle, final Location location, final int count,
                             final double offsetX, final double offsetY, final double offsetZ, final double extra) {
        if (world == null || particle == null || location == null) {
            return;
        }
        final Object data = defaultData(particle);
        if (data == SKIP) {
            return;
        }
        world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
    }

    /** Client-side (single viewer) spawn with offsets/extra, supplying required particle data automatically. */
    public static void spawn(final Player viewer, final Particle particle, final Location location, final int count,
                             final double offsetX, final double offsetY, final double offsetZ, final double extra) {
        if (viewer == null || particle == null || location == null) {
            return;
        }
        final Object data = defaultData(particle);
        if (data == SKIP) {
            return;
        }
        viewer.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
    }

    /** Sentinel for "required data type we can't synthesise — skip the spawn". */
    private static final Object SKIP = new Object();

    /**
     * A neutral default for the particle's required data type, null when none is needed,
     * or {@link #SKIP} for exotic types (Vibration…) we'd rather silently drop than crash on.
     */
    private static Object defaultData(final Particle particle) {
        final Class<?> dataType = particle.getDataType();
        if (dataType == Void.class) {
            return null;
        }
        if (dataType == Color.class) {
            return Color.WHITE;
        }
        if (dataType == Particle.DustOptions.class) {
            return new Particle.DustOptions(Color.WHITE, 1.0F);
        }
        if (dataType == org.bukkit.block.data.BlockData.class) {
            return Material.STONE.createBlockData();
        }
        if (dataType == ItemStack.class) {
            return new ItemStack(Material.STONE);
        }
        if (dataType == Float.class) {
            return 1.0F;
        }
        if (dataType == Integer.class) {
            return 0;
        }
        return SKIP;
    }
}
