package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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
 * "Vadgomba" — a kaszter egy vörös gomba item-entitást hajít el a nézésirányba.
 * Az item 3 másodperc múlva (ha addig nem semmisül meg) "felrobban": nem rombol
 * blokkot, csak gyengítő hatásokat szór a 4 blokkos körzetben lévő ellenfelekre.
 */
public final class WildMushroomSpell extends BaseSpell {

    private final JavaPlugin plugin;
    private final NamespacedKey itemTag;

    public WildMushroomSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "wild_mushroom", "Vadgomba", 60, SpellCostType.XP, 40);
        this.plugin = plugin;
        this.itemTag = new NamespacedKey(plugin, "wild_mushroom_item");
    }

    @Override
    public void execute(final Player player) {
        final UUID casterId = player.getUniqueId();
        final Vector direction = player.getEyeLocation().getDirection().normalize();
        final Location spawnLocation = player.getEyeLocation().add(direction.multiply(0.6D));

        final Item item = player.getWorld().dropItem(spawnLocation, taggedMushroom());
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setUnlimitedLifetime(true);
        item.setGlowing(true);
        item.addScoreboardTag("icesmp_wild_mushroom");

        final double throwPower = balance("throw-power", 0.55D);
        final Vector velocity = direction.clone().multiply(throwPower);
        velocity.setY(Math.max(velocity.getY(), 0.25D));
        item.setVelocity(velocity);

        final int fuseTicks = balanceInt("fuse-ticks", 3 * 20);
        // Folia: the fuse runs on the item's own entity scheduler. If the item is destroyed
        // (picked up despite the delay guard, burned, void, …) the task is retired and never
        // fires — no explosion, matching the "megsemmisült item" requirement.
        item.getScheduler().runDelayed(plugin, task -> detonate(item, casterId), null, fuseTicks);

        player.sendMessage(resolveMessage("spell.wild_mushroom.cast",
                "<green>Elhajítasz egy vadgombát...</green>"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6F, 0.7F);
    }

    private ItemStack taggedMushroom() {
        final ItemStack stack = new ItemStack(Material.RED_MUSHROOM);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(itemTag, PersistentDataType.BOOLEAN, true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void detonate(final Item item, final UUID casterId) {
        if (!item.isValid() || item.isDead()) {
            return;
        }

        final Location center = item.getLocation();
        final World world = center.getWorld();
        item.remove();

        world.spawnParticle(Particle.EXPLOSION, center, 1);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.1F);

        final double radius = balance("radius", 4.0D);
        final int effectDurationTicks = balanceInt("effect-duration-ticks", 4 * 20);
        for (final Entity nearby : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.getUniqueId().equals(casterId)) {
                continue;
            }

            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, effectDurationTicks, 0, false, true, true));
            living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, effectDurationTicks, 0, false, true, true));
        }
    }
}
