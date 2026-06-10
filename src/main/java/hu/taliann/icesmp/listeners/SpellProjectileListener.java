package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.spells.WisplightSpell;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpellProjectileListener implements Listener {

    private final JavaPlugin plugin;

    public SpellProjectileListener(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(final ProjectileHitEvent event) {
        final Projectile projectile = event.getEntity();

        if (projectile.getScoreboardTags().contains(WisplightSpell.PROJECTILE_TAG)) {
            final Block targetBlock = resolveWisplightTarget(event);
            if (targetBlock != null) {
                WisplightSpell.spawnTemporaryLight(plugin, targetBlock.getLocation());
            }
            projectile.remove();
            return;
        }

        // Legacy support for earlier egg-based angry chicken shots.
        if (!projectile.getScoreboardTags().contains("icesmp_angry_chicken")) {
            return;
        }

        if (event.getHitEntity() instanceof LivingEntity livingEntity) {
            livingEntity.damage(8.0D, projectile.getShooter() instanceof LivingEntity shooter ? shooter : null);
            livingEntity.getWorld().spawnParticle(Particle.CRIT, livingEntity.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.3D, 0.3D, 0.3D, 0.02D);
            livingEntity.getWorld().playSound(livingEntity.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1.0F, 0.7F);
        }
    }

    private Block resolveWisplightTarget(final ProjectileHitEvent event) {
        if (event.getHitBlock() == null) {
            return event.getEntity().getLocation().getBlock();
        }

        final BlockFace face = event.getHitBlockFace();
        if (face == null) {
            return event.getHitBlock();
        }

        return event.getHitBlock().getRelative(face);
    }
}

