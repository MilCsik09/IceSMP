package hu.taliann.icesmp.utils;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
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

import java.util.function.Consumer;

/**
 * Display-entity effekt-réteg (BlockDisplay/ItemDisplay) a particle mellé — ott, ahol az effekt
 * geometria, tartós vagy kliens-oldalon interpolált (claim-fényfal, telegraph, kirakat). Egy spawn
 * + egy animációs csomag váltja ki a tickenkénti particle-szórást.
 *
 * <p><b>Folia:</b> a display entitás valódi entitás — spawn/módosítás CSAK a cél-régió szálán, ezért
 * minden belépő a {@code getRegionScheduler()}-en fut. Auto-despawn az entitás SAJÁT schedulerén,
 * így a régióval együtt él/hal.
 *
 * <p><b>Szemét-védelem:</b> minden fx-entitás {@code setPersistent(false)} (restart/chunk-unload
 * nem menti ki) + {@link #FX_TAG} scoreboard-tag ({@code DisplayFxCleanupListener} chunk-loadkor
 * söpri az esetleges maradékot). A kettő együtt garantálja: crash után sem marad szemét a világban.
 */
public final class DisplayFxUtil {

    private DisplayFxUtil() {
    }

    public static final String FX_TAG = "icesmp_fx";

    private static final AxisAngle4f NO_ROT = new AxisAngle4f(0.0F, 0.0F, 0.0F, 1.0F);

    /**
     * Egy BlockDisplay spawnolása a hely régió-szálán, tagelve és nem-perzisztensen; a {@code setup}
     * a spawn után fut (transzformáció, glow, láthatóság), majd {@code despawnTicks} múlva remove.
     */
    public static void spawnBlockDisplay(final Plugin plugin, final Location loc, final BlockData block,
                                         final int despawnTicks, final Consumer<BlockDisplay> setup) {
        if (plugin == null || loc == null || loc.getWorld() == null || block == null) {
            return;
        }
        plugin.getServer().getRegionScheduler().run(plugin, loc, task -> {
            final BlockDisplay display = loc.getWorld().spawn(loc, BlockDisplay.class, e -> {
                e.setBlock(block);
                e.setPersistent(false);
                e.addScoreboardTag(FX_TAG);
            });
            if (setup != null) {
                setup.accept(display);
            }
            if (despawnTicks > 0) {
                scheduleDespawn(plugin, display, despawnTicks);
            }
        });
    }

    /** Egy ItemDisplay spawnolása ugyanazzal az életciklussal (kirakat, crate-pörgetés). */
    public static void spawnItemDisplay(final Plugin plugin, final Location loc, final ItemStack item,
                                        final int despawnTicks, final Consumer<ItemDisplay> setup) {
        if (plugin == null || loc == null || loc.getWorld() == null || item == null) {
            return;
        }
        plugin.getServer().getRegionScheduler().run(plugin, loc, task -> {
            final ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, e -> {
                e.setItemStack(item);
                e.setPersistent(false);
                e.addScoreboardTag(FX_TAG);
            });
            if (setup != null) {
                setup.accept(display);
            }
            if (despawnTicks > 0) {
                scheduleDespawn(plugin, display, despawnTicks);
            }
        });
    }

    /** Csak a megadott néző lássa (a többi online játékosról elrejtjük) — per-nézős claim-show/telegraph. */
    public static void showOnlyTo(final Plugin plugin, final Entity fx, final Player viewer) {
        if (plugin == null || fx == null || viewer == null) {
            return;
        }
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(viewer.getUniqueId())) {
                online.hideEntity(plugin, fx);
            }
        }
    }

    /**
     * Kliens-oldali interpoláció: a jelenlegi állapotból a cél-transzformációba {@code durationTicks}
     * alatt (a szerver egyetlen csomagot küld). A spawn utáni tickre időzítve, hogy a kezdő és a cél
     * állapot elkülönüljön.
     */
    public static void animateTo(final Plugin plugin, final Display display, final Transformation target,
                                 final int durationTicks) {
        if (plugin == null || display == null || target == null) {
            return;
        }
        display.getScheduler().runDelayed(plugin, task -> {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(durationTicks);
            display.setTransformation(target);
        }, null, 1L);
    }

    /** Egyszerű, forgatás nélküli skála-transzformáció (fal-nyújtás, telegraph-terítés). */
    public static Transformation scale(final float x, final float y, final float z) {
        return new Transformation(new Vector3f(0.0F, 0.0F, 0.0F), NO_ROT, new Vector3f(x, y, z), NO_ROT);
    }

    /**
     * Egyetlen megnyújtott, opcionálisan izzó fal-szegmens a {@code corner} saroktól +X/+Y/+Z
     * irányban ({@code sizeX×sizeY×sizeZ}), per-nézősen, auto-despawnnal. Egy claim-él = egy entitás.
     */
    public static void wallSegment(final Plugin plugin, final Location corner, final float sizeX, final float sizeY,
                                   final float sizeZ, final BlockData block, final Color glow, final int despawnTicks,
                                   final Player viewer) {
        spawnBlockDisplay(plugin, corner, block, despawnTicks, display -> {
            display.setTransformation(scale(sizeX, sizeY, sizeZ));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(2.0F);
            if (glow != null) {
                display.setGlowing(true);
                display.setGlowColorOverride(glow);
            }
            if (viewer != null) {
                showOnlyTo(plugin, display, viewer);
            }
        });
    }

    private static void scheduleDespawn(final Plugin plugin, final Display display, final int ticks) {
        display.getScheduler().runDelayed(plugin, task -> display.remove(), null, ticks);
    }
}
