package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Apró világ-szabályok, amik eddig külön mini-pluginokban éltek (plugin-leépítés):
 * <ul>
 *   <li><b>Warden-halál XP</b> — az ICEsmpadditions.jar kiváltása: a Warden legyőzése
 *       konfigurálható, nagy XP-t dob (vanília ~5 helyett default 80–125);</li>
 *   <li><b>termés-taposás védelem</b> — a FarmProtect.jar kiváltása: a szántóföldet sem
 *       játékos-ugrálás, sem mob-taposás nem törheti fel.</li>
 * </ul>
 * Minden esemény a saját entitás régió-szálán fut, és csak az eseményt módosítja — Folia-safe.
 */
public final class WorldTweaksListener implements Listener {

    private final ConfigManager configManager;

    public WorldTweaksListener(final ConfigManager configManager) {
        this.configManager = configManager;
    }

    /** Warden-halál: felülírt, konfigurálható XP-drop (ICEsmpadditions-örökség). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onWardenDeath(final EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.WARDEN
                || !configManager.getBoolean("world-tweaks.warden-death-xp.enabled", true)) {
            return;
        }
        final int min = Math.max(0, configManager.getInt("world-tweaks.warden-death-xp.min", 80));
        final int max = Math.max(min + 1, configManager.getInt("world-tweaks.warden-death-xp.max", 125) + 1);
        event.setDroppedExp(ThreadLocalRandom.current().nextInt(min, max));
    }

    /** Játékos-taposás: szántóföldre ugrás/lépés nem töri fel a földet (FarmProtect-örökség). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerTrample(final PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.FARMLAND
                && configManager.getBoolean("world-tweaks.crop-trample-protection.players", true)) {
            event.setCancelled(true);
        }
    }

    /** Mob-taposás: mob (nem játékos) sem törheti fel a szántóföldet. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityTrample(final EntityInteractEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND
                && !(event.getEntity() instanceof Player)
                && configManager.getBoolean("world-tweaks.crop-trample-protection.mobs", true)) {
            event.setCancelled(true);
        }
    }
}
