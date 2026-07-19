package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * "Rúnacsapás" — a kaszter egy megrúnázott kő item-entitást hajít el; az első
 * élőlény, aki (a kasztert kivéve) hozzáér, sebzést és lassítást kap, az item
 * pedig eltűnik. Legfeljebb 10 másodpercig marad a világban.
 */
public final class RuneStrikeSpell extends BaseSpell {

    private final JavaPlugin plugin;
    private final NamespacedKey itemTag;

    public RuneStrikeSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "rune_strike", "Rúnacsapás", 40, SpellCostType.HUNGER, 3);
        this.plugin = plugin;
        this.itemTag = new NamespacedKey(plugin, "rune_strike_item");
    }

    @Override
    public void execute(final Player player) {
        final UUID casterId = player.getUniqueId();
        final Vector direction = player.getEyeLocation().getDirection().normalize();
        final Location spawnLocation = player.getEyeLocation().add(direction.multiply(0.6D));

        final Item item = player.getWorld().dropItem(spawnLocation, taggedStone());
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setUnlimitedLifetime(true);
        item.setGlowing(true);
        item.addScoreboardTag("icesmp_rune_strike");

        final double throwPower = balance("throw-power", 0.5D);
        item.setVelocity(direction.clone().multiply(throwPower));

        final double hitRadius = balance("hit-radius", 1.2D);
        final double damage = balance("damage", 7.0D);
        final int slowDurationTicks = balanceInt("slow-duration-ticks", 4 * 20);

        // Folia: driven on the item's own entity scheduler, mirroring the AngryChickenSpell
        // proximity-projectile pattern.
        item.getScheduler().runAtFixedRate(plugin, task -> {
            if (!item.isValid() || item.isDead()) {
                task.cancel();
                return;
            }

            for (final Entity nearby : item.getNearbyEntities(hitRadius, hitRadius, hitRadius)) {
                if (!(nearby instanceof LivingEntity living) || living.getUniqueId().equals(casterId)
                        || SpellTargetingUtil.isAlly(casterId, living)) {
                    continue;
                }

                final Player caster = Bukkit.getPlayer(casterId);
                if (caster != null && Bukkit.isOwnedByCurrentRegion(caster)) {
                    hu.taliann.icesmp.utils.SpellDamageUtil.damageBySpell(caster, living, damage);
                } else {
                    living.damage(damage);
                }
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDurationTicks, 0, false, true, true));

                item.getWorld().spawnParticle(Particle.CRIT, item.getLocation(), 12, 0.3D, 0.3D, 0.3D, 0.02D);
                item.getWorld().playSound(item.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.9F);
                item.remove();
                task.cancel();
                return;
            }
        }, null, 1L, 1L);

        final int lifetimeTicks = balanceInt("lifetime-ticks", 10 * 20);
        item.getScheduler().runDelayed(plugin, task -> {
            if (item.isValid()) {
                item.remove();
            }
        }, null, lifetimeTicks);

        player.sendMessage(resolveMessage("spell.rune_strike.cast", "<gray>Rúnát vésett kő repül a levegőben...</gray>"));
    }

    private ItemStack taggedStone() {
        final ItemStack stack = new ItemStack(Material.STONE);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            // Resource pack-hez: egyedi CMD (RESOURCE_PACK_CMD.md leltár; balansz-kulccsal felülbírálható).
            final org.bukkit.inventory.meta.components.CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(java.util.List.of((float) balanceInt("custom-model-data", 6102)));
            meta.setCustomModelDataComponent(cmd);
            meta.getPersistentDataContainer().set(itemTag, PersistentDataType.BOOLEAN, true);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
