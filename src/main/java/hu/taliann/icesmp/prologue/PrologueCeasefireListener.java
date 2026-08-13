package hu.taliann.icesmp.prologue;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.function.BooleanSupplier;

/** Finale-context override; does not mutate the permanent DOOM_GATE PvP territory rule. */
public final class PrologueCeasefireListener implements Listener {
    private final PrologueWorldAccess worldAccess;
    private final hu.taliann.icesmp.managers.ConfigManager config;
    private final BooleanSupplier active;

    public PrologueCeasefireListener(final PrologueWorldAccess worldAccess,
                                     final hu.taliann.icesmp.managers.ConfigManager config,
                                     final BooleanSupplier active) {
        this.worldAccess = worldAccess;
        this.config = config;
        this.active = active;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPvp(final EntityDamageByEntityEvent event) {
        if (!active.getAsBoolean() || !(event.getEntity() instanceof Player victim)) return;
        final Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        final double radius = Math.max(16.0D, config.getDouble(
                "world-events.prologue.finale.ceasefire-radius", 110.0D));
        if (PrologueWorldAccess.within(victim.getLocation(), worldAccess.gateAnchor(), radius)
                || PrologueWorldAccess.within(attacker.getLocation(), worldAccess.gateAnchor(), radius)) {
            event.setCancelled(true);
        }
    }

    private static Player attacker(final Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
