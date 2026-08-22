package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.MoneyPouchItemFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.MobScalingManager;
import hu.taliann.icesmp.utils.MobKillUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/** WoW-stílusú pénz-drop; a Mohóság rúna csak ACTIVE canonical főkézből jár. */
public final class MobMoneyDropListener implements Listener {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MobScalingManager mobScalingManager;
    private final MoneyPouchItemFactory pouchFactory;
    private final hu.taliann.icesmp.managers.AfkManager afkManager;
    private final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService;
    private final org.bukkit.NamespacedKey spawnerMobKey;

    public MobMoneyDropListener(final org.bukkit.plugin.java.JavaPlugin plugin,
                                final ConfigManager configManager,
                                final MobScalingManager mobScalingManager,
                                final MoneyPouchItemFactory pouchFactory,
                                final hu.taliann.icesmp.managers.AfkManager afkManager,
                                final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mobScalingManager = mobScalingManager;
        this.pouchFactory = pouchFactory;
        this.afkManager = afkManager;
        this.itemIdentityService = itemIdentityService;
        this.spawnerMobKey = new org.bukkit.NamespacedKey(plugin, "spawner_mob");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(final CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            event.getEntity().getPersistentDataContainer().set(spawnerMobKey,
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(final EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();
        if (!(entity instanceof Monster) || !configManager.getBoolean("mob-money-drop.enabled", true)) return;
        final MobKillUtil.KillContext kill = MobKillUtil.eligibleKill(entity,
                MobKillUtil.RewardKind.FAUCET, configManager, afkManager);
        if (kill == null) return;
        final double baseChance = Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("mob-money-drop.chance-percent", 20.0D)));
        final double runeBonus = Math.max(0.0D,
                configManager.getDouble("runes.runa_moho.money-drop-bonus-percent", 5.0D));

        final Random rng = kill.dropRandom("mob-money");
        final double roll = rng.nextDouble() * 100.0D;
        if (roll >= Math.min(100.0D, baseChance + runeBonus)) return;
        final int mobLevel = mobScalingManager == null ? 1 : Math.max(1, mobScalingManager.getLevel(entity));
        final long amount = rollAmount(rng, mobLevel);

        if (roll < baseChance) {
            final ItemStack pouch = payout(kill, amount);
            if (pouch != null) event.getDrops().add(pouch);
            return;
        }

        final World world = kill.victimWorld();
        if (world == null) return;
        kill.runOnKiller(plugin, killer -> {
            final ItemStack hand = killer.getInventory().getItemInMainHand();
            if (!hu.taliann.icesmp.itemization.EquipmentProficiencyService.allowsGameplayContribution(
                    killer, hand, hu.taliann.icesmp.itemization.ItemTemplate.Slot.MAIN_HAND)
                    || !itemIdentityService.hasRune(hand, "runa_moho")) {
                return;
            }
            final ItemStack pouch = payout(kill, amount);
            if (pouch == null) return;
            final Location at = kill.victimLocation();
            Bukkit.getRegionScheduler().run(plugin, at, task -> world.dropItemNaturally(at, pouch));
        });
    }

    private long rollAmount(final Random rng, final int mobLevel) {
        final double min = Math.max(0.01D, configManager.getDouble("mob-money-drop.min-amount", 1.0D));
        final double max = Math.max(min, configManager.getDouble("mob-money-drop.max-amount", 4.0D));
        final double perLevel = Math.max(0.0D, configManager.getDouble("mob-money-drop.per-level-bonus", 0.5D));
        return Math.max(1L, Math.round(min + rng.nextDouble() * (max - min) + (mobLevel - 1) * perLevel));
    }

    private ItemStack payout(final MobKillUtil.KillContext kill, final long amount) {
        if (!kill.claimOnce("mob-money") || !tryConsumeDailyBudget(kill.killerId(), amount)) return null;
        return pouchFactory.createRandom(amount);
    }

    private final hu.taliann.icesmp.utils.DailyBudget.InMemory<java.util.UUID> moneyBudget =
            new hu.taliann.icesmp.utils.DailyBudget.InMemory<>(512);

    private boolean tryConsumeDailyBudget(final java.util.UUID playerId, final long amount) {
        return moneyBudget.tryConsume(playerId, amount,
                configManager.getDouble("mob-money-drop.daily-cap", 300.0D));
    }
}
