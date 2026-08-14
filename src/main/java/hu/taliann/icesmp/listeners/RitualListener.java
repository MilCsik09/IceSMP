package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.RitualManager;
import hu.taliann.icesmp.prologue.PrologueContentPolicy;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
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

/** Triggers ritual altars and applies the central Prologue relic-acquisition gate. */
public final class RitualListener implements Listener {

    private final RitualManager ritualManager;
    private final Map<UUID, Long> debounce = new ConcurrentHashMap<>();

    public RitualListener(final RitualManager ritualManager) {
        this.ritualManager = ritualManager;
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        final Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        final Block block = event.getClickedBlock();
        if (block == null) return;
        final long now = System.currentTimeMillis();
        if (now - debounce.getOrDefault(player.getUniqueId(), 0L) < 1000L) return;

        if (isBlockedRelicRitual(block)) {
            debounce.put(player.getUniqueId(), now);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "A relikviák a Prologue alatt még nem ébrednek fel.",
                    net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));
            return;
        }

        if (ritualManager.tryRitual(player, block)) {
            debounce.put(player.getUniqueId(), now);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    private static boolean isBlockedRelicRitual(final Block block) {
        final ConfigManager config = ConfigManager.current();
        if (config == null || PrologueContentPolicy.relicAcquisitionAvailable(config)
                || config.getConfiguration() == null) return false;
        final ConfigurationSection rituals = config.getConfiguration().getConfigurationSection("rituals");
        if (rituals == null) return false;
        for (final String id : rituals.getKeys(false)) {
            final ConfigurationSection ritual = rituals.getConfigurationSection(id);
            if (ritual == null || !"relic".equalsIgnoreCase(ritual.getString("type", "relic"))) continue;
            final Material core = Material.matchMaterial(ritual.getString("altar-block", ""));
            if (core == block.getType()) return true;
        }
        return false;
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
