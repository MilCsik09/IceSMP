package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.AbundanceManager;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies the {@link AbundanceManager} window's calm/fertility effects: crops
 * gain an extra growth stage, animals sometimes bear twins, and a fraction of
 * natural hostile spawns are hushed. Every handler runs on the block/entity's own
 * region thread, so the mutations are Folia-safe. Effects never touch terrain
 * beyond advancing a crop that was already growing.
 */
public final class AbundanceListener implements Listener {

    private final AbundanceManager abundanceManager;

    public AbundanceListener(final AbundanceManager abundanceManager) {
        this.abundanceManager = abundanceManager;
    }

    /** Faster crops: a growing crop has a chance to jump an extra age stage. */
    @EventHandler(ignoreCancelled = true)
    public void onGrow(final BlockGrowEvent event) {
        final double chance = abundanceManager.cropBoostChance();
        if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        final BlockState next = event.getNewState();
        if (next.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()) {
            age.setAge(Math.min(age.getMaximumAge(), age.getAge() + 1));
            next.setBlockData(age);
        }
    }

    /** Fertile animals: a chance to spawn a second baby right after a successful breed. */
    @EventHandler(ignoreCancelled = true)
    public void onBreed(final EntityBreedEvent event) {
        final double chance = abundanceManager.twinChance();
        if (chance <= 0.0D || !(event.getEntity() instanceof Animals baby)
                || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        // A twin: spawn another baby of the same type at the newborn's location.
        // (entity Ageable#setBaby — distinct from the block-data Ageable used for crops above.)
        final Entity twin = baby.getWorld().spawnEntity(baby.getLocation(), baby.getType());
        if (twin instanceof org.bukkit.entity.Ageable ageable) {
            ageable.setBaby();
        }
    }

    /** Calmer nights: hush a fraction of natural hostile spawns (plugin spawns are untouched). */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(final CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                || !(event.getEntity() instanceof Monster)) {
            return;
        }
        final double chance = abundanceManager.calmChance();
        if (chance > 0.0D && ThreadLocalRandom.current().nextDouble() < chance) {
            event.setCancelled(true);
        }
    }
}
