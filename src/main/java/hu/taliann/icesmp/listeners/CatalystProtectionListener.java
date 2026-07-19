package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.items.CatalystItemFactory;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Lélekkapocs (Ability Catalyst) cannot be thrown away — the Tree's gift stays
 * with its bearer (lore: docs/LORE.md VI). Q-drop is cancelled, and on death the
 * catalyst is lifted out of the drops and handed back on respawn, so it can never
 * end up on the ground. All handled events fire on the player's own region thread
 * (Folia-safe); the death→respawn stash is a concurrent map, cleared on quit.
 */
public final class CatalystProtectionListener implements Listener {

    private final CatalystItemFactory catalystItemFactory;
    private final MessageManager messageManager;

    /** Catalysts lifted from a player's death drops, waiting for the respawn hand-back. */
    private final Map<UUID, List<ItemStack>> keptOnDeath = new ConcurrentHashMap<>();

    public CatalystProtectionListener(final CatalystItemFactory catalystItemFactory,
                                      final MessageManager messageManager) {
        this.catalystItemFactory = catalystItemFactory;
        this.messageManager = messageManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        if (!catalystItemFactory.isCatalyst(event.getItemDrop().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(messageManager.get("catalyst-no-drop",
                "&8A Lélekkapocs nem hagyja el a gazdáját — a Fa ajándéka veled marad."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(final PlayerDeathEvent event) {
        final List<ItemStack> kept = new ArrayList<>();
        final Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            final ItemStack drop = drops.next();
            if (catalystItemFactory.isCatalyst(drop)) {
                drops.remove();
                kept.add(drop);
            }
        }
        if (!kept.isEmpty()) {
            keptOnDeath.put(event.getEntity().getUniqueId(), kept);
        }
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final List<ItemStack> kept = keptOnDeath.remove(player.getUniqueId());
        if (kept == null) {
            return;
        }
        for (final ItemStack catalyst : kept) {
            // Inventory is empty right after respawn, but stay safe against kept-inventory setups.
            player.getInventory().addItem(catalyst).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        // Died-then-quit before respawn: the stash must not leak. The catalyst is freely
        // re-obtainable (a Fa újat ád — Job GUI / givecatalyst), so dropping it is safe.
        keptOnDeath.remove(event.getPlayer().getUniqueId());
    }
}
