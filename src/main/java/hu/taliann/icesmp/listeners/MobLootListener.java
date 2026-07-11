package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.InvasionManager;
import hu.taliann.icesmp.managers.MasterworkAffixService;
import hu.taliann.icesmp.managers.WildHuntManager;
import hu.taliann.icesmp.managers.WorldBossManager;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * WoW-style mob loot: slain mobs have a chance to drop a unique, random-attribute gear item
 * (rolled by {@link MasterworkAffixService}). The tier balances the source — ordinary mobs roll
 * the weaker {@code drop} tier, while world bosses / invasion champions / wild-hunt beasts roll
 * the {@code boss} tier, on par with profession crafts. Config: {@code loot} (loot.yml).
 */
public final class MobLootListener implements Listener {

    private final ConfigManager configManager;
    private final MasterworkAffixService affixService;
    private final WorldBossManager worldBossManager;
    private final InvasionManager invasionManager;
    private final WildHuntManager wildHuntManager;

    public MobLootListener(final ConfigManager configManager, final MasterworkAffixService affixService,
                           final WorldBossManager worldBossManager, final InvasionManager invasionManager,
                           final WildHuntManager wildHuntManager) {
        this.configManager = configManager;
        this.affixService = affixService;
        this.worldBossManager = worldBossManager;
        this.invasionManager = invasionManager;
        this.wildHuntManager = wildHuntManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        if (!configManager.getBoolean("loot.enabled", true) || !affixService.isEnabled()) {
            return;
        }
        final LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }

        final boolean bossTier = worldBossManager.isWorldBoss(entity)
                || invasionManager.isInvasionMob(entity.getUniqueId())
                || wildHuntManager.isWildHunt(entity.getUniqueId());

        // Ordinary mobs may require a player kill; boss/event mobs always yield their loot.
        if (!bossTier && configManager.getBoolean("loot.require-player-kill", true) && entity.getKiller() == null) {
            return;
        }

        final String path = bossTier ? "loot.boss-drop" : "loot.mob-drop";
        final String tier = bossTier ? MasterworkAffixService.TIER_BOSS : MasterworkAffixService.TIER_DROP;

        final double chance = configManager.getDouble(path + ".chance", bossTier ? 1.0D : 0.02D);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        final Material base = pickGear(configManager.getStringList(path + ".gear-pool"));
        if (base == null) {
            return;
        }
        // Mob loot always gets a random name (never a designed masterwork name); negative affixes
        // are possible on the weaker tiers (config negative-affix-chance).
        final ItemStack rolled = affixService.roll(new ItemStack(base), tier, true);
        event.getDrops().add(rolled);
    }

    private Material pickGear(final List<String> pool) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        // Try a few times to land on a valid material name (skips admin typos gracefully).
        for (int attempt = 0; attempt < 4; attempt++) {
            final String name = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            final Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material != null && !material.isAir()) {
                return material;
            }
        }
        return null;
    }
}
