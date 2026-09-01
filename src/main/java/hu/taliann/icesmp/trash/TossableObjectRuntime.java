package hu.taliann.icesmp.trash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded visual toss proxy; canonical inventory items never leave player ownership. */
public final class TossableObjectRuntime {

    private static final int MAX_ACTIVE = 128;
    private final JavaPlugin plugin;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final AtomicInteger activeCount = new AtomicInteger();

    public TossableObjectRuntime(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean toss(final Player player, final ItemStack item,
                        final TrashAnomalyBehavior behavior) {
        if (activeCount.incrementAndGet() > MAX_ACTIVE) {
            activeCount.decrementAndGet();
            return false;
        }
        final Vector forward = player.getEyeLocation().getDirection().normalize();
        final Location origin = player.getEyeLocation().add(forward.clone().multiply(0.55D))
                .subtract(0.0D, 0.35D, 0.0D);
        final ItemStack shown = item.clone();
        shown.setAmount(1);
        final ItemDisplay display;
        try {
            display = origin.getWorld().spawn(origin, ItemDisplay.class, entity -> {
                entity.setItemStack(shown);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                entity.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                entity.setViewRange(24.0F);
                entity.setShadowRadius(0.0F);
                entity.setPersistent(false);
            });
        } catch (final RuntimeException rejected) {
            activeCount.decrementAndGet();
            return false;
        }
        active.add(display.getUniqueId());
        final int duration = behavior == TrashAnomalyBehavior.HETPARTOS_KAVICS ? 42 : 24;
        final int[] age = {0};
        try {
            display.getScheduler().runAtFixedRate(plugin, task -> {
                if (!display.isValid() || ++age[0] > duration) {
                    task.cancel();
                    release(display.getUniqueId());
                    if (display.isValid()) display.remove();
                    if (behavior == TrashAnomalyBehavior.HAZUDNI_NEM_TUDO_DOBOKOCKA) {
                        player.getScheduler().run(plugin,
                                ignored -> {
                                    if (player.isOnline()) player.sendActionBar(
                                            Component.text("1", NamedTextColor.GRAY));
                                },
                                null);
                    }
                    return;
                }
                if (behavior == TrashAnomalyBehavior.HETPARTOS_KAVICS) {
                    final double progress = age[0] / (double) duration;
                    final Location sample = origin.clone().add(
                            forward.clone().multiply(progress * 5.5D));
                    if (sample.getBlockX() >> 4 != origin.getBlockX() >> 4
                            || sample.getBlockZ() >> 4 != origin.getBlockZ() >> 4
                            || age[0] <= 35
                            && sample.getBlock().getType() != org.bukkit.Material.WATER
                            && !sample.getBlock().isPassable()) {
                        task.cancel();
                        release(display.getUniqueId());
                        display.remove();
                        return;
                    }
                }
                display.setTransformation(transformation(behavior, forward, age[0], duration));
            }, () -> release(display.getUniqueId()), 1L, 1L);
        } catch (final RuntimeException rejected) {
            release(display.getUniqueId());
            if (display.isValid()) display.remove();
            return false;
        }
        return true;
    }

    public void shutdown() {
        for (final UUID entityId : Set.copyOf(active)) {
            final Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.getScheduler().run(plugin, ignored -> entity.remove(), null);
            }
        }
        active.clear();
        activeCount.set(0);
    }

    private void release(final UUID entityId) {
        if (active.remove(entityId)) activeCount.decrementAndGet();
    }

    private static Transformation transformation(final TrashAnomalyBehavior behavior,
                                                 final Vector forward, final int age,
                                                 final int duration) {
        final float progress = age / (float) duration;
        final float distance;
        final float height;
        if (behavior == TrashAnomalyBehavior.HETPARTOS_KAVICS) {
            distance = progress * 5.5F;
            final float local = (progress * 7.0F) % 1.0F;
            height = age > 35 ? -0.18F * (age - 35)
                    : 0.08F + 0.55F * 4.0F * local * (1.0F - local)
                    * (1.0F - progress * 0.65F);
        } else {
            distance = progress * 2.4F;
            height = 1.45F * 4.0F * progress * (1.0F - progress);
        }
        final Vector3f translation = new Vector3f(
                (float) forward.getX() * distance,
                height + (float) forward.getY() * distance * 0.35F,
                (float) forward.getZ() * distance);
        final AxisAngle4f rotation;
        if (behavior == TrashAnomalyBehavior.FELREVERT_GARAS && progress > 0.82F) {
            rotation = new AxisAngle4f((float) (Math.PI / 2.0D), 1.0F, 0.0F, 0.0F);
        } else {
            rotation = new AxisAngle4f(progress * (float) (Math.PI * 6.0D), 0.3F, 1.0F, 0.2F);
        }
        return new Transformation(translation, rotation, new Vector3f(0.55F), new AxisAngle4f());
    }
}
