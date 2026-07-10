package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class HideSpell extends BaseSpell {

    private static final long HIDE_DURATION_TICKS = 20L * 10L;

    private static final Map<UUID, ItemStack[]> HIDDEN_ARMOR = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;

    public HideSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "hide", "Elrejtzes", 8 * 60, SpellCostType.XP, 550);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        final int teleportRadius = balanceInt("teleport-radius", 10);
        final Location origin = player.getLocation();
        final Location destination = randomNearbySafeLocation(origin, teleportRadius);
        if (destination != null) {
            player.teleportAsync(destination);
        }

        HIDDEN_ARMOR.put(player.getUniqueId(), player.getInventory().getArmorContents().clone());
        player.getInventory().setArmorContents(new ItemStack[]{null, null, null, null});

        final int durationTicks = balanceInt("duration-ticks", (int) HIDE_DURATION_TICKS);
        final int speedAmplifier = balanceInt("speed-amplifier", 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, durationTicks, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, speedAmplifier, false, true, true));
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation(), 40, 0.5D, 0.5D, 0.5D, 0.01D);

        final UUID playerId = player.getUniqueId();
        // Folia: schedule on the player's own region scheduler, not the (unsupported) global Bukkit scheduler.
        // The retired-callback ensures the armor is restored even if the task is retired before firing
        // (e.g. the entity is removed); clearHide is idempotent. Normal logout is also covered by the
        // PlayerQuit cleanup, which restores the armor while the player is still online.
        player.getScheduler().runDelayed(plugin, task -> clearHide(playerId), () -> clearHide(playerId), durationTicks);
    }

    private Location randomNearbySafeLocation(final Location origin, final int radius) {
        for (int tries = 0; tries < 12; tries++) {
            final int dx = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            final int dz = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            final int x = origin.getBlockX() + dx;
            final int z = origin.getBlockZ() + dz;
            final int y = origin.getWorld().getHighestBlockYAt(x, z) + 1;
            final Location candidate = new Location(origin.getWorld(), x + 0.5D, y, z + 0.5D);
            if (candidate.getBlock().isPassable() && candidate.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable()) {
                return candidate;
            }
        }

        return null;
    }

    public static boolean isHidden(final Player player) {
        return HIDDEN_ARMOR.containsKey(player.getUniqueId());
    }

    public static void clearHide(final Player player) {
        clearHide(player.getUniqueId());
    }

    public static void clearHide(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        final ItemStack[] hiddenArmor = HIDDEN_ARMOR.remove(playerId);
        if (player != null) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.removePotionEffect(PotionEffectType.SPEED);
        }

        if (hiddenArmor != null && player != null) {
            player.getInventory().setArmorContents(hiddenArmor);
        }
    }

    public static void cleanup(final UUID playerId) {
        clearHide(playerId);
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        clearHide(playerId);
    }
}

