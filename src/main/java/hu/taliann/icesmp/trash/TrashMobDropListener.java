package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.managers.AfkManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.MobKillUtil;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Independent flavor-faucet roll behind the shared Survival/AFK/spawner/minion gate. */
public final class TrashMobDropListener implements Listener {

    private final ConfigManager configManager;
    private final AfkManager afkManager;
    private final TrashLootService loot;
    private final TrashContextResolver contexts;

    public TrashMobDropListener(final ConfigManager configManager, final AfkManager afkManager,
                                final TrashLootService loot, final TrashContextResolver contexts) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.afkManager = Objects.requireNonNull(afkManager, "afkManager");
        this.loot = Objects.requireNonNull(loot, "loot");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(final EntityDeathEvent event) {
        final LivingEntity victim = event.getEntity();
        if (victim instanceof Player) return;
        final MobKillUtil.KillContext eligible = MobKillUtil.eligibleKill(victim,
                MobKillUtil.RewardKind.FLAVOR, configManager, afkManager);
        if (eligible == null || !eligible.claimOnce("trash")) return;
        final ItemStack drop = loot.roll(TrashLootSource.MOB,
                contexts.resolve(TrashLootSource.MOB, victim.getLocation(), victim)).orElse(null);
        if (drop != null) event.getDrops().add(drop);
    }
}
