package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.SiegeWeaponFactory;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fires the siege cannon: right-clicking the tagged item during an active raid
 * consumes one and detonates a block-safe blast at the aimed location. Outside
 * raids it just warns the player. A short per-player debounce prevents
 * accidental double-fires.
 */
public final class SiegeWeaponListener implements Listener {

    private final JavaPlugin plugin;
    private final SiegeWeaponFactory siegeWeaponFactory;
    private final RaidManager raidManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final Map<java.util.UUID, Long> debounce = new ConcurrentHashMap<>();

    public SiegeWeaponListener(final JavaPlugin plugin, final SiegeWeaponFactory siegeWeaponFactory,
                              final RaidManager raidManager,
                              final ConfigManager configManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.siegeWeaponFactory = siegeWeaponFactory;
        this.raidManager = raidManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        final Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        final Player player = event.getPlayer();
        final ItemStack hand = player.getInventory().getItemInMainHand();
        if (!siegeWeaponFactory.isSiegeWeapon(hand)) {
            return;
        }

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        if (!raidManager.isRaidActive()) {
            player.sendActionBar(messageManager.getMessage("siege-not-raid", "<red>Az ostromágyú csak raid alatt működik.</red>"));
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - debounce.getOrDefault(player.getUniqueId(), 0L) < 750L) {
            return;
        }
        debounce.put(player.getUniqueId(), now);

        final double range = Math.max(4.0D, configManager.getDouble("factions.raid.siege.range", 40.0D));
        final float power = (float) Math.max(1.0D, configManager.getDouble("factions.raid.siege.power", 4.0D));

        final RayTraceResult hit = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), range, FluidCollisionMode.NEVER, true);
        final Location impact = hit != null && hit.getHitPosition() != null
                ? hit.getHitPosition().toLocation(player.getWorld())
                : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(range));

        // Decrement the ammo on the player's own region thread (the interact event runs here).
        hand.setAmount(hand.getAmount() - 1);

        // Folia: the impact can be up to `range` blocks away — a different region than the player's.
        // Detonate on the impact location's own region thread so the explosion/particles/sound never
        // touch a region we don't own from here.
        final World world = player.getWorld();
        plugin.getServer().getRegionScheduler().run(plugin, impact, task -> {
            // Block-safe, no-fire explosion: damages entities, spares the terrain.
            world.createExplosion(impact, power, false, false, player);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, impact, 1);
            world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 0.8F);
        });
    }

    @EventHandler
    public void onPlayerQuit(final org.bukkit.event.player.PlayerQuitEvent event) {
        debounce.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(final org.bukkit.event.player.PlayerKickEvent event) {
        debounce.remove(event.getPlayer().getUniqueId());
    }
}
