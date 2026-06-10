package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.plugin.java.JavaPlugin;

public final class WisplightSpell extends BaseSpell {

    public static final String PROJECTILE_TAG = "icesmp_wisplight";
    private static final long DURATION_TICKS = 20L * 60L * 5L;

    private final JavaPlugin plugin;

    public WisplightSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "wisplight", "Wisplight", 0, SpellCostType.HUNGER, 1);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        final Snowball projectile = player.launchProjectile(Snowball.class,
                player.getEyeLocation().getDirection().normalize().multiply(1.8D));
        projectile.addScoreboardTag(PROJECTILE_TAG);
    }

    public static void spawnTemporaryLight(final JavaPlugin plugin, final Location location) {
        if (plugin == null || location == null || location.getWorld() == null) {
            return;
        }

        final Block block = location.getBlock();
        if (!block.getType().isAir() && block.getType() != Material.LIGHT) {
            return;
        }

        block.setType(Material.LIGHT, false);
        if (block.getBlockData() instanceof Light lightData) {
            lightData.setLevel(15);
            block.setBlockData(lightData, true);
        }

        location.getWorld().spawnParticle(Particle.END_ROD,
                block.getLocation().add(0.5D, 0.5D, 0.5D),
                25, 0.2D, 0.2D, 0.2D, 0.01D);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (block.getType() == Material.LIGHT) {
                block.setType(Material.AIR, false);
            }
        }, DURATION_TICKS);
    }
}


