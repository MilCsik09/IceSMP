package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Necromancer soul-shard resource (ideas.md "Nekromanta lélek-erőforrás").
 * Necromancer-spec players accumulate soul shards from kills (stored in player
 * PDC); shards are spent to raise a buffed Wither Skeleton champion through the
 * minion framework — a stronger payoff than the regular timed summons.
 */
public final class SoulShardManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MinionManager minionManager;
    private final MessageManager messageManager;
    private final NamespacedKey shardsKey;

    public SoulShardManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final MinionManager minionManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.minionManager = minionManager;
        this.messageManager = messageManager;
        this.shardsKey = new NamespacedKey(plugin, "soul_shards");
    }

    public int getShards(final Player player) {
        return player == null ? 0 : player.getPersistentDataContainer().getOrDefault(shardsKey, PersistentDataType.INTEGER, 0);
    }

    public void addShards(final Player player, final int amount) {
        if (player == null || amount <= 0) {
            return;
        }

        player.getPersistentDataContainer().set(shardsKey, PersistentDataType.INTEGER, getShards(player) + amount);
    }

    public boolean spendShards(final Player player, final int amount) {
        if (player == null || amount <= 0 || getShards(player) < amount) {
            return false;
        }

        player.getPersistentDataContainer().set(shardsKey, PersistentDataType.INTEGER, getShards(player) - amount);
        return true;
    }

    /**
     * Raises a buffed Wither Skeleton champion for the cost of soul shards,
     * respecting the per-player summon cap. Aimed at the nearest hostile mob.
     *
     * @param player the necromancer
     * @return null on success, otherwise a message key explaining the failure
     */
    public String summonChampion(final Player player) {
        final int cost = Math.max(1, configManager.getInt("souls.champion-cost", 10));
        final int maxActive = Math.max(1, configManager.getInt("pets.max-active", 8));
        if (minionManager.countActive(player.getUniqueId()) >= maxActive) {
            return "souls-champion-cap";
        }

        if (getShards(player) < cost) {
            return "souls-champion-insufficient";
        }

        spendShards(player, cost);

        final WitherSkeleton champion = player.getWorld().spawn(player.getLocation(), WitherSkeleton.class);
        champion.setPersistent(false);
        minionManager.tag(champion, player.getUniqueId());
        if (champion instanceof AbstractSkeleton skeleton) {
            skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            skeleton.getEquipment().setItemInMainHandDropChance(0.0F);
        }

        final double health = Math.max(20.0D, configManager.getDouble("souls.champion-health", 60.0D));
        final AttributeInstance maxHealth = champion.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
            champion.setHealth(health);
        }

        final LivingEntity target = nearestHostile(player);
        if (target != null) {
            champion.setTarget(target);
        }

        final int lifespanTicks = Math.max(20, configManager.getInt("souls.champion-lifespan-seconds", 60)) * 20;
        champion.getScheduler().runDelayed(plugin, task -> champion.remove(), null, lifespanTicks);

        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.4D, 0.6D, 0.4D, 0.03D);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5F, 1.4F);
        player.sendMessage(messageManager.getMessage(
                "souls-champion-summoned",
                "<dark_purple>Lélekszilánkjaidból bajnok támad fel ({cost} szilánk).</dark_purple>",
                Map.of("cost", String.valueOf(cost))
        ));
        return null;
    }

    private LivingEntity nearestHostile(final Player player) {
        LivingEntity nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (final Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 14.0D, 14.0D, 14.0D)) {
            if (!(entity instanceof Monster monster) || minionManager.isMinion(monster)) {
                continue;
            }
            final double sq = monster.getLocation().distanceSquared(player.getLocation());
            if (sq < nearestSq) {
                nearestSq = sq;
                nearest = monster;
            }
        }
        return nearest;
    }
}
