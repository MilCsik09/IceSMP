package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InnerFocusSpell extends BaseSpell {

    private static final Map<UUID, Float> FROZEN_PLAYERS = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;

    public InnerFocusSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "inner_focus", "Belso Fokusz", 8 * 60, SpellCostType.HUNGER, 20);
        this.plugin = plugin;
    }

    @Override
    public boolean canCast(final Player player) {
        // Recast guard: while frozen, a second cast would save the already-zeroed walk speed
        // as "original" and permanently strand the player at 0 speed.
        return player.getFoodLevel() >= 20 && !FROZEN_PLAYERS.containsKey(player.getUniqueId());
    }

    @Override
    public void consumeCost(final Player player) {
        player.setFoodLevel(0);
    }

    @Override
    public void execute(final Player player) {
        final float originalWalkSpeed = player.getWalkSpeed();
        final UUID playerId = player.getUniqueId();
        FROZEN_PLAYERS.put(playerId, originalWalkSpeed);
        player.setWalkSpeed(0.0F);
        final int durationTicks = balanceInt("duration-ticks", 20 * 5);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, balanceInt("resistance-amplifier", 5), false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, balanceInt("regen-amplifier", 1), false, true, true));

        // Folia: per-player region scheduler instead of the unsupported Bukkit scheduler.
        player.getScheduler().runDelayed(plugin, task -> restorePlayer(playerId, originalWalkSpeed),
                () -> restorePlayer(playerId, originalWalkSpeed), durationTicks);
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        final Float originalSpeed = FROZEN_PLAYERS.remove(playerId);
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }

        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.setWalkSpeed(originalSpeed == null ? 0.2F : originalSpeed);
    }

    private static void restorePlayer(final UUID playerId, final float fallbackSpeed) {
        final Float originalSpeed = FROZEN_PLAYERS.remove(playerId);
        final Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer != null) {
            onlinePlayer.setWalkSpeed(originalSpeed == null ? fallbackSpeed : originalSpeed);
        }
    }
}


