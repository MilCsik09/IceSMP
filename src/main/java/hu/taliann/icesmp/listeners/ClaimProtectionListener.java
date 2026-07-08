package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ClaimManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Enforces the native chunk-claim protection (SimpleClaimSystem replacement):
 * inside someone else's claim a player cannot break/place blocks, open storage
 * containers, use buckets, or place/break hangings — unless the owner trusted
 * them, they hold the admin bypass permission, or (config-gated) they are a
 * registered raid attacker plundering an enemy-faction claim (containers only,
 * mirroring the wartime looting rule). Explosions and block-eating mobs
 * (enderman, ravager) never damage claimed chunks. PvP is deliberately NOT
 * touched: a claim protects property, not people (war server).
 *
 * <p>All handlers fire on the acting block/entity's region thread, and the
 * claim lookup is a lock-free map read — Folia-safe with no hops.
 */
public final class ClaimProtectionListener implements Listener {

    /** Same bypass node the kingdom-territory protection uses — one admin switch. */
    private static final String BYPASS_PERMISSION = "icesmp.admin.territory.bypass";

    private final ClaimManager claimManager;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final RaidManager raidManager;
    private final MessageManager messageManager;

    public ClaimProtectionListener(final ClaimManager claimManager, final ConfigManager configManager,
                                   final FactionManager factionManager, final RaidManager raidManager,
                                   final MessageManager messageManager) {
        this.claimManager = claimManager;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.raidManager = raidManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(final BlockBreakEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(final BlockPlaceEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    /** Storage containers in a foreign claim stay shut (raid plunder excepted, if enabled). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !configManager.getBoolean("claims.protect-containers", true)) {
            return;
        }
        final Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Container)) {
            return;
        }
        final Player player = event.getPlayer();
        if (!denied(player, block.getLocation())) {
            return;
        }
        // War plunder: a REGISTERED raid attacker may open (never break) containers in
        // an enemy-faction member's claim — the same sanction as territory looting.
        if (configManager.getBoolean("claims.raid-lootable", false) && isSanctionedPlunder(player, block.getLocation())) {
            return;
        }
        event.setCancelled(true);
        warn(player, block.getLocation());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketEmpty(final PlayerBucketEmptyEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketFill(final PlayerBucketFillEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    /** Explosions never crater claimed chunks (creeper, TNT, crystal…). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityExplode(final EntityExplodeEvent event) {
        if (configManager.getBoolean("claims.protect-explosions", true)) {
            event.blockList().removeIf(block -> claimManager.getClaimAt(block.getLocation()) != null);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(final BlockExplodeEvent event) {
        if (configManager.getBoolean("claims.protect-explosions", true)) {
            event.blockList().removeIf(block -> claimManager.getClaimAt(block.getLocation()) != null);
        }
    }

    /** Block-eating/-moving mobs (enderman, ravager…) leave claimed chunks alone. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityChangeBlock(final EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player)
                && claimManager.getClaimAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingPlace(final HangingPlaceEvent event) {
        final Player player = event.getPlayer();
        if (player != null && denied(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            warn(player, event.getEntity().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHangingBreak(final HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && denied(player, event.getEntity().getLocation())) {
            event.setCancelled(true);
            warn(player, event.getEntity().getLocation());
        }
    }

    /** True when the location is someone else's claim and the player has no access/bypass. */
    private boolean denied(final Player player, final Location location) {
        return !claimManager.canUse(player.getUniqueId(), location)
                && !player.hasPermission(BYPASS_PERMISSION);
    }

    private boolean isSanctionedPlunder(final Player player, final Location location) {
        final ClaimManager.Claim claim = claimManager.getClaimAt(location);
        if (claim == null) {
            return false;
        }
        final FactionType ownerFaction = factionManager.getFaction(claim.getOwner());
        return ownerFaction != null && raidManager.isSanctionedLooting(player.getUniqueId(), ownerFaction);
    }

    /** Action-bar notice (not chat) so a build-spam doesn't flood the chat window. */
    private void warn(final Player player, final Location deniedAt) {
        final ClaimManager.Claim claim = claimManager.getClaimAt(deniedAt);
        player.sendActionBar(messageManager.getMessage(
                "claim-protected", "&c✖ Ez a terület claimelve van ({owner}).",
                java.util.Map.of("owner", claim == null ? "?" : claim.getOwnerName())));
    }
}
