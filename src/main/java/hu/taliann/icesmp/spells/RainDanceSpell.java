package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;

public final class RainDanceSpell extends BaseSpell {

    public RainDanceSpell(final MessageManager messageManager) {
        super(messageManager, "rain_dance", "Esotanc", 3600, SpellCostType.XP, 352);
    }

    @Override
    public void execute(final Player player) {
        final var world = player.getWorld();
        world.setStorm(true);
        world.setThundering(false);

        // Folia: keep the crop-growth scan small and region-local — a 50-radius cube was ~1M block
        // reads on the region thread AND crossed region boundaries (illegal). 6 wide / 3 tall stays
        // inside the caster's region and is cheap; crops only grow on the surface, so the Y band is small.
        final Location center = player.getLocation();
        final int radius = balanceInt("radius", 6);
        final int vertical = balanceInt("vertical-range", 3);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if ((x * x) + (z * z) > (radius * radius)) {
                    continue;
                }
                for (int y = -vertical; y <= vertical; y++) {
                    final Block block = center.getBlock().getRelative(x, y, z);
                    if (!(block.getBlockData() instanceof Ageable ageable)) {
                        continue;
                    }
                    ageable.setAge(ageable.getMaximumAge());
                    block.setBlockData(ageable, true);
                }
            }
        }
    }
}

