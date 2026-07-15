package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
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
 * "Démoni Kör" — a korábbi lendület-kitörés (dash + rövid gyorsaság) megmarad;
 * emellett az elrugaszkodás helyén egy "só" (cukor) item-entitást szór el, ami
 * proximity-alapon "felszedődik": az odaérő ellenfél megsemmisíti az itemet és
 * mérgezést kap.
 */
public final class DemonicCircleSpell extends BaseSpell {

    private final JavaPlugin plugin;
    private final NamespacedKey saltTag;

    public DemonicCircleSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "demonic_circle", "Démoni Kör", 45, SpellCostType.HUNGER, 3);
        this.plugin = plugin;
        this.saltTag = new NamespacedKey(plugin, "demonic_circle_salt");
    }

    @Override
    public void execute(final Player player) {
        dropSalt(player);
        dash(player);
        playFeedback(player);
    }

    /** A korábbi ConfiguredSpell .dash(1.6, 0.3) + selfEffect(SPEED, 4s, 0) viselkedése, változatlanul. */
    private void dash(final Player player) {
        final double dashForward = balance("dash-forward", 1.6D);
        final double dashUp = balance("dash-up", 0.3D);

        Vector velocity = player.getLocation().getDirection().setY(0.0D);
        if (velocity.lengthSquared() > 1.0E-4D) {
            velocity = velocity.normalize().multiply(dashForward);
        } else {
            velocity = new Vector();
        }
        velocity.setY(dashUp);
        player.setVelocity(velocity);

        final int speedDurationTicks = balanceInt("speed-duration-ticks", 4 * 20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, speedDurationTicks, 0, false, true, true));
    }

    private void playFeedback(final Player player) {
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0.0D, 1.0D, 0.0D),
                20, 0.4D, 0.6D, 0.4D, 0.02D);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.7F);
    }

    private void dropSalt(final Player player) {
        final UUID casterId = player.getUniqueId();
        final Location saltLocation = player.getLocation().add(0.0D, 0.1D, 0.0D);

        final Item item = player.getWorld().dropItem(saltLocation, taggedSalt());
        // Natural pickup stays disabled — the "felszedés" a proximity-tickben szimulálva van.
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setUnlimitedLifetime(true);
        item.setGlowing(true);
        item.addScoreboardTag("icesmp_demonic_circle_salt");

        final double proximity = balance("salt-proximity", 1.0D);
        final int poisonDurationTicks = balanceInt("salt-poison-duration-ticks", 3 * 20);

        item.getScheduler().runAtFixedRate(plugin, task -> {
            if (!item.isValid() || item.isDead()) {
                task.cancel();
                return;
            }

            for (final Entity nearby : item.getNearbyEntities(proximity, proximity, proximity)) {
                if (!(nearby instanceof LivingEntity living) || living.getUniqueId().equals(casterId)) {
                    continue;
                }

                living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonDurationTicks, 0, false, true, true));
                item.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, item.getLocation(), 10, 0.2D, 0.2D, 0.2D, 0.01D);
                item.remove();
                task.cancel();
                return;
            }
        }, null, 2L, 2L);

        final int lifetimeTicks = balanceInt("salt-lifetime-ticks", 30 * 20);
        item.getScheduler().runDelayed(plugin, task -> {
            if (item.isValid()) {
                item.remove();
            }
        }, null, lifetimeTicks);
    }

    private ItemStack taggedSalt() {
        final ItemStack stack = new ItemStack(Material.SUGAR);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            // Resource pack-hez: egyedi CMD (RESOURCE_PACK_CMD.md leltár; balansz-kulccsal felülbírálható).
            final org.bukkit.inventory.meta.components.CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(java.util.List.of((float) balanceInt("custom-model-data", 6103)));
            meta.setCustomModelDataComponent(cmd);
            meta.getPersistentDataContainer().set(saltTag, PersistentDataType.BOOLEAN, true);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
