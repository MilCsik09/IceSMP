package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.RitualManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Triggers ritual altars: sneak-right-clicking a configured altar block
 * attempts to summon the matching relic by sacrificing items. A short
 * per-player debounce prevents accidental double-triggers.
 */
public final class RitualListener implements Listener {

    private final RitualManager ritualManager;
    private final Map<UUID, Long> debounce = new ConcurrentHashMap<>();

    public RitualListener(final RitualManager ritualManager) {
        this.ritualManager = ritualManager;
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        final Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        final Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - debounce.getOrDefault(player.getUniqueId(), 0L) < 1000L) {
            return;
        }

        if (ritualManager.tryRitual(player, block)) {
            debounce.put(player.getUniqueId(), now);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        debounce.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(final PlayerKickEvent event) {
        debounce.remove(event.getPlayer().getUniqueId());
    }
}
