package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.classspec.application.ClassSpecProfileGateway;
import hu.taliann.icesmp.classspec.application.ProfileMutationResult;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Necromancer soul shards stored only in the active Profile v2 loadout. */
public final class SoulShardManager {
    public static final String SHARD_KEY = "necromancer.soulforge.shards";
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MinionManager minionManager;
    private final MessageManager messageManager;
    private volatile ClassSpecProfileGateway profileGateway;

    public SoulShardManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final MinionManager minionManager, final MessageManager messageManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.configManager = Objects.requireNonNull(configManager);
        this.minionManager = Objects.requireNonNull(minionManager);
        this.messageManager = Objects.requireNonNull(messageManager);
    }

    public void setProfileGateway(final ClassSpecProfileGateway gateway) {
        profileGateway = Objects.requireNonNull(gateway);
    }

    private ClassSpecProfileGateway gateway() {
        final ClassSpecProfileGateway gateway = profileGateway;
        if (gateway == null) throw new IllegalStateException("Profile v2 gateway is not initialized");
        return gateway;
    }

    public int getShards(final Player player) {
        if (player == null) return 0;
        try {
            return gateway().activeMechanic(player.getUniqueId(), SHARD_KEY)
                    .map(Integer::parseInt).filter(value -> value >= 0).orElse(0);
        } catch (final NumberFormatException invalid) {
            gateway().blockSession(player.getUniqueId(), "Invalid durable soul shard value");
            return 0;
        }
    }

    /** Legacy direct mutation is disabled. */
    public void addShards(final Player player, final int amount) { }
    /** Legacy direct mutation is disabled. */
    public boolean spendShards(final Player player, final int amount) { return false; }

    public CompletionStage<Boolean> addShardsV2(final Player player, final int amount,
                                                 final String operationId) {
        if (player == null || amount <= 0) return CompletableFuture.completedFuture(false);
        return mutate(player.getUniqueId(), amount, operationId);
    }

    public CompletionStage<Boolean> spendShardsV2(final Player player, final int amount,
                                                   final String operationId) {
        if (player == null || amount <= 0 || getShards(player) < amount) {
            return CompletableFuture.completedFuture(false);
        }
        return mutate(player.getUniqueId(), -amount, operationId);
    }

    private CompletionStage<Boolean> mutate(final UUID playerId, final int delta,
                                             final String operationId) {
        return gateway().mutateSoulShards(playerId, delta, operationId).thenApply(result ->
                result.committed() || result.status() == ProfileMutationResult.Status.NO_CHANGE);
    }

    /** Legacy synchronous summon is intentionally disabled. */
    public String summonChampion(final Player player) { return "souls-profile-v2-async-required"; }

    public CompletionStage<String> summonChampionV2(final Player player) {
        if (player == null || !gateway().activeSpecId(player.getUniqueId()).filter("necromancer"::equals).isPresent()) {
            return CompletableFuture.completedFuture("souls-necromancer-only");
        }
        final int cost = Math.max(1, configManager.getInt("souls.champion-cost", 10));
        final int maxActive = Math.max(1, configManager.getInt("pets.max-active", 8));
        if (minionManager.countActive(player.getUniqueId()) >= maxActive) {
            return CompletableFuture.completedFuture("souls-champion-cap");
        }
        if (getShards(player) < cost) return CompletableFuture.completedFuture("souls-champion-insufficient");
        final String debitId = "soul-champion:" + player.getUniqueId() + ':' + UUID.randomUUID();
        return spendShardsV2(player, cost, debitId).thenCompose(spent -> {
            if (!spent) return CompletableFuture.completedFuture("souls-champion-insufficient");
            final CompletableFuture<String> completion = new CompletableFuture<>();
            player.getScheduler().run(plugin, task -> {
                try {
                    spawnChampion(player, cost);
                    completion.complete(null);
                } catch (final Throwable spawnFailure) {
                    addShardsV2(player, cost, debitId + ":compensate").whenComplete((refunded, refundFailure) -> {
                        if (refundFailure != null || !Boolean.TRUE.equals(refunded)) {
                            gateway().blockSession(player.getUniqueId(),
                                    "Champion spawn failed and shard compensation failed");
                            completion.complete("souls-champion-refund-failed");
                        } else completion.complete("souls-champion-spawn-failed");
                    });
                }
            }, () -> addShardsV2(player, cost, debitId + ":scheduler-compensate")
                    .whenComplete((ignored, failure) -> completion.complete(
                            failure == null ? "souls-champion-spawn-failed" : "souls-champion-refund-failed")));
            return completion;
        });
    }

    private void spawnChampion(final Player player, final int cost) {
        final WitherSkeleton champion = player.getWorld().spawn(player.getLocation(), WitherSkeleton.class);
        EventSpawnGuard.prepare(champion);
        champion.setPersistent(false);
        minionManager.tag(champion, player.getUniqueId());
        if (champion instanceof AbstractSkeleton skeleton) {
            skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            skeleton.getEquipment().setItemInMainHandDropChance(0.0F);
        }
        final double health = Math.max(20.0D, configManager.getDouble("souls.champion-health", 60.0D));
        final AttributeInstance maxHealth = champion.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) { maxHealth.setBaseValue(health); champion.setHealth(health); }
        final LivingEntity target = nearestHostile(player);
        if (target != null) champion.setTarget(target);
        final int lifespanTicks = Math.max(20,
                configManager.getInt("souls.champion-lifespan-seconds", 60)) * 20;
        champion.getScheduler().runDelayed(plugin, task -> champion.remove(), null, lifespanTicks);
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                player.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.4D, 0.6D, 0.4D, 0.03D);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5F, 1.4F);
        player.sendMessage(messageManager.getMessage("souls-champion-summoned",
                "<dark_purple>Lélekszilánkjaidból bajnok támad fel ({cost} szilánk).</dark_purple>",
                Map.of("cost", String.valueOf(cost))));
    }

    private LivingEntity nearestHostile(final Player player) {
        LivingEntity nearest = null; double nearestSq = Double.MAX_VALUE;
        for (final Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 14, 14, 14)) {
            if (!(entity instanceof Monster monster) || minionManager.isMinion(monster)) continue;
            final double distance = monster.getLocation().distanceSquared(player.getLocation());
            if (distance < nearestSq) { nearestSq = distance; nearest = monster; }
        }
        return nearest;
    }
}
