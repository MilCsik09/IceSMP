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
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;

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

    /**
     * Action-bar notice on claim-border crossing (claim edge feedback). Gated on
     * block-level movement (like TerritoryListener) because claims are block-precise
     * and Y-bounded; the change detection and per-player state live in the
     * ClaimManager (cleaned on quit), and the lookup is a lock-free index read.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld().equals(to.getWorld())) {
            return;
        }

        final Player player = event.getPlayer();
        final String change = claimManager.handleChunkEnter(player, to);
        if (change == null) {
            return;
        }
        switch (change) {
            case "own" -> player.sendActionBar(messageManager.getMessage(
                    "claim-enter-own", "<green>⚑ Saját birtokod</green>"));
            case "foreign" -> {
                final ClaimManager.Claim claim = claimManager.getClaimAt(to);
                player.sendActionBar(messageManager.getMessage(
                        "claim-enter-foreign", "<red>⚑ {owner} birtoka</red>",
                        Map.of("owner", claim == null ? "?" : claim.getOwnerName())));
            }
            default -> player.sendActionBar(messageManager.getMessage(
                    "claim-leave", "<gray>Vadon — szabad terület</gray>"));
        }
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

    /** Explosions never permanently crater claimed chunks — regen mellett látványosan
     *  robbannak, majd a claim pontosan visszagyógyul (drop nélkül). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityExplode(final EntityExplodeEvent event) {
        if (configManager.getBoolean("claims.protect-explosions", true) && applyRegen(event.blockList(), event.getEntity().getLocation())) {
            event.setYield(0.0F);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(final BlockExplodeEvent event) {
        if (configManager.getBoolean("claims.protect-explosions", true) && applyRegen(event.blockList(), event.getBlock().getLocation())) {
            event.setYield(0.0F);
        }
    }

    /** @return true, ha volt regen-re fogott claim-blokk (yield-nullázást kér) */
    private boolean applyRegen(final java.util.List<org.bukkit.block.Block> blocks,
                               final org.bukkit.Location center) {
        final hu.taliann.icesmp.managers.BlockRegenService regen = this.blockRegenService;
        if (regen == null || !regen.isEnabled()) {
            blocks.removeIf(block -> claimManager.getClaimAt(block.getLocation()) != null);
            return false;
        }
        boolean captured = false;
        final long delay = regen.explosionDelayMillis();
        final java.util.Iterator<org.bukkit.block.Block> it = blocks.iterator();
        while (it.hasNext()) {
            final org.bukkit.block.Block block = it.next();
            if (claimManager.getClaimAt(block.getLocation()) == null) {
                continue;
            }
            if (block.getType() == org.bukkit.Material.TNT) {
                continue; // a lánc-robbanás él, de a TNT nem épül vissza (nincs hurok)
            }
            if (!regen.capture(block, delay)) {
                it.remove();
                if (hu.taliann.icesmp.managers.BlockRegenService.isTileEntity(block)) {
                    regen.playWardEffect(block); // az óvó rúnák látványa
                }
            } else {
                regen.spawnDebris(block, center);
                captured = true;
            }
        }
        return captured;
    }

    /** Fizika-lepattanás (fáklya a kirobbant falról) claimben: drop nélkül, regen-nel. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockDestroy(final com.destroystokyo.paper.event.block.BlockDestroyEvent event) {
        final hu.taliann.icesmp.managers.BlockRegenService regen = this.blockRegenService;
        if (regen == null || !regen.isEnabled()
                || claimManager.getClaimAt(event.getBlock().getLocation()) == null) {
            return;
        }
        if (regen.capture(event.getBlock(), regen.explosionDelayMillis())) {
            event.setWillDrop(false);
        }
    }

    private volatile hu.taliann.icesmp.managers.BlockRegenService blockRegenService;

    public void setBlockRegenService(final hu.taliann.icesmp.managers.BlockRegenService service) {
        this.blockRegenService = service;
    }

    // ==================== terrain (tűz / folyadék / dugattyú) ====================
    // Mirrors TerritoryProtectionListener: without these, fire spread and piston push/pull
    // bypass the claim protection entirely (neither fires BlockBreak/BlockPlaceEvent).

    /** Player ignition inside a claim follows the build rule; natural ignition is always blocked. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onIgnite(final BlockIgniteEvent event) {
        if (!configManager.getBoolean("claims.protect-fire", true)
                || claimManager.getClaimAt(event.getBlock().getLocation()) == null) {
            return;
        }
        final Player player = event.getPlayer();
        if (player == null || denied(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            if (player != null) {
                warn(player, event.getBlock().getLocation());
            }
        }
    }

    /** Claimed blocks never burn away. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBurn(final BlockBurnEvent event) {
        if (configManager.getBoolean("claims.protect-fire", true)
                && claimManager.getClaimAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    /** Fire never spreads onto claimed blocks. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSpread(final BlockSpreadEvent event) {
        if (event.getSource().getType() == org.bukkit.Material.FIRE
                && configManager.getBoolean("claims.protect-fire", true)
                && claimManager.getClaimAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    /** Liquid may not flow into a claim from outside (or from another owner's claim). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onLiquidFlow(final BlockFromToEvent event) {
        if (!configManager.getBoolean("claims.protect-terrain", true)) {
            return;
        }
        final ClaimManager.Claim to = claimManager.getClaimAt(event.getToBlock().getLocation());
        if (to == null) {
            return;
        }
        final ClaimManager.Claim from = claimManager.getClaimAt(event.getBlock().getLocation());
        if (from == null || !from.getOwner().equals(to.getOwner())) {
            event.setCancelled(true);
        }
    }

    /** Pistons outside a claim may not push blocks into it or pull blocks out of it. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonExtend(final BlockPistonExtendEvent event) {
        if (!configManager.getBoolean("claims.protect-terrain", true)) {
            return;
        }
        // The extending head itself claims the space ahead even with no carried blocks.
        final java.util.UUID pistonOwner = claimOwnerAt(event.getBlock().getLocation());
        if (isForeignClaim(event.getBlock().getRelative(event.getDirection()).getLocation(), pistonOwner)
                || pistonCrossesForeignClaim(event.getBlocks(), event.getDirection(), pistonOwner)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonRetract(final BlockPistonRetractEvent event) {
        if (!configManager.getBoolean("claims.protect-terrain", true)) {
            return;
        }
        // On retract the pulled blocks move TOWARD the piston — opposite getDirection().
        final java.util.UUID pistonOwner = claimOwnerAt(event.getBlock().getLocation());
        if (pistonCrossesForeignClaim(event.getBlocks(), event.getDirection().getOppositeFace(), pistonOwner)) {
            event.setCancelled(true);
        }
    }

    private java.util.UUID claimOwnerAt(final Location location) {
        final ClaimManager.Claim claim = claimManager.getClaimAt(location);
        return claim == null ? null : claim.getOwner();
    }

    /** True when the location lies in a claim NOT owned by the piston's owner — machines fully inside one claim keep working. */
    private boolean isForeignClaim(final Location location, final java.util.UUID pistonOwner) {
        final ClaimManager.Claim claim = claimManager.getClaimAt(location);
        return claim != null && !claim.getOwner().equals(pistonOwner);
    }

    private boolean pistonCrossesForeignClaim(final java.util.List<org.bukkit.block.Block> blocks,
                                              final org.bukkit.block.BlockFace movement,
                                              final java.util.UUID pistonOwner) {
        for (final org.bukkit.block.Block block : blocks) {
            if (isForeignClaim(block.getLocation(), pistonOwner)
                    || isForeignClaim(block.getRelative(movement).getLocation(), pistonOwner)) {
                return true;
            }
        }
        return false;
    }

    /** Block-eating/-moving mobs (enderman, ravager, Wither-test…) leave claimed
     *  chunks alone — regen-nel a rombolás látványosan megtörténik és visszagyógyul. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityChangeBlock(final EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Player
                || claimManager.getClaimAt(event.getBlock().getLocation()) == null) {
            return;
        }
        event.setCancelled(true);
        final hu.taliann.icesmp.managers.BlockRegenService regen = this.blockRegenService;
        if (regen != null && regen.isEnabled() && event.getTo().isAir()
                && !hu.taliann.icesmp.managers.BlockRegenService.isTileEntity(event.getBlock())
                && regen.capture(event.getBlock(), regen.explosionDelayMillis())) {
            regen.spawnDebris(event.getBlock(), event.getEntity().getLocation());
            event.getBlock().setType(org.bukkit.Material.AIR, false);
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
