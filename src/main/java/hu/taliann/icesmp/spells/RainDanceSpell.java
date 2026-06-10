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

        final Location center = player.getLocation();
        final int radius = 50;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if ((x * x) + (y * y) + (z * z) > (radius * radius)) {
                        continue;
                    }

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

