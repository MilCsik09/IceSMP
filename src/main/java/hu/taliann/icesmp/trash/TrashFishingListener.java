package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.managers.AfkManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Objects;

/** Silent extra physical Trash catch; the vanilla catch is never replaced. */
public final class TrashFishingListener implements Listener {

    private final JavaPlugin plugin;
    private final TrashLootService loot;
    private final TrashContextResolver contexts;
    private final AfkManager afkManager;

    public TrashFishingListener(final JavaPlugin plugin, final TrashLootService loot,
                                final TrashContextResolver contexts, final AfkManager afkManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.loot = Objects.requireNonNull(loot, "loot");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.afkManager = Objects.requireNonNull(afkManager, "afkManager");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCatch(final PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        final Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL || afkManager.isAfk(player.getUniqueId())) return;
        final Location hook = event.getHook().getLocation().clone();
        final Location pullTarget = player.getLocation().clone();
        if (hook.getWorld() == null) return;
        plugin.getServer().getRegionScheduler().run(plugin, hook, task -> {
            if (!hook.getWorld().isChunkLoaded(hook.getBlockX() >> 4, hook.getBlockZ() >> 4)) return;
            final ItemStack stack = loot.roll(TrashLootSource.FISHING,
                    contexts.resolve(TrashLootSource.FISHING, hook, null)).orElse(null);
            if (stack == null) return;
            final Item dropped = hook.getWorld().dropItem(hook, stack);
            dropped.setVelocity(pullVelocity(hook, pullTarget));
        });
    }

    private static Vector pullVelocity(final Location from, final Location target) {
        if (target.getWorld() == null || from.getWorld() == null
                || !target.getWorld().equals(from.getWorld())) return new Vector(0.0D, 0.16D, 0.0D);
        final Vector direction = target.toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 0.0001D) return new Vector(0.0D, 0.16D, 0.0D);
        return direction.normalize().multiply(0.18D).setY(0.16D);
    }
}
