package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.spells.ArmamentSpell;
import hu.taliann.icesmp.spells.DoubleJumpSpell;
import hu.taliann.icesmp.spells.HideSpell;
import hu.taliann.icesmp.spells.LuckyStarSpell;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class SpellStateListener implements Listener {

    private final NamespacedKey armamentTag;

    public SpellStateListener(final JavaPlugin plugin) {
        this.armamentTag = new NamespacedKey(plugin, "armament_item");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamaged(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && HideSpell.isHidden(player)) {
            HideSpell.clearHide(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamagedByEntity(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !LuckyStarSpell.shouldDodge(player)) {
            return;
        }

        event.setCancelled(true);
        player.setVelocity(new Vector(0.0D, 0.0D, 0.0D));

        if (event.getDamager() instanceof Projectile projectile) {
            projectile.setVelocity(projectile.getVelocity().multiply(-0.8D));
            projectile.setShooter(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (hasArmamentTag(event.getCurrentItem()) || hasArmamentTag(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        for (final ItemStack itemStack : event.getNewItems().values()) {
            if (hasArmamentTag(itemStack)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        if (hasArmamentTag(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        if (!DoubleJumpSpell.hasUsedDoubleJump(player.getUniqueId())) {
            return;
        }

        if (isGrounded(player)) {
            DoubleJumpSpell.clearDoubleJump(player.getUniqueId());
        }
    }

    private boolean isGrounded(final Player player) {
        // Avoid deprecated Entity#isOnGround by checking the collision block under feet.
        return !player.getLocation().subtract(0.0D, 0.05D, 0.0D).getBlock().isPassable();
    }

    private boolean hasArmamentTag(final ItemStack itemStack) {
        return ArmamentSpell.hasArmamentTag(itemStack, armamentTag);
    }
}

