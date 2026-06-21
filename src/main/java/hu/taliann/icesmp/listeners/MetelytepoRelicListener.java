package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.MetelytepoManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;

/**
 * Event listener for the Metelytepo relic mechanics.
 * Handles block breaking, damage modifiers, Justice/Honor Eye abilities, and death tracking.
 */
public final class MetelytepoRelicListener implements Listener {

    // Ability configuration constants
    private static final double JUSTICE_DAMAGE = 12.0D;
    private static final double NON_SINNER_DAMAGE_MULTIPLIER = 0.5D;
    private static final double UNDEAD_DAMAGE_MULTIPLIER = 1.5D;
    private static final int JUSTICE_EFFECT_DURATION_TICKS = 20 * 10;  // 10 seconds
    private static final int JUSTICE_EFFECT_LEVEL = 1;
    private static final int HONOR_EYE_GLOWING_DURATION_TICKS = 20 * 20;  // 20 seconds
    private static final int HONOR_EYE_GLOWING_LEVEL = 0;
    private static final double HONOR_EYE_RANGE = 100.0D;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final MetelytepoManager metelytepoManager;
    private final MessageManager messageManager;

    /**
     * Constructs a new MetelytepoRelicListener.
     *
     * @param metelytepoManager the manager for Metelytepo-specific mechanics
     * @param messageManager the manager for player-facing messages
     */
    public MetelytepoRelicListener(final MetelytepoManager metelytepoManager, final MessageManager messageManager) {
        this.metelytepoManager = metelytepoManager;
        this.messageManager = messageManager;
    }

    /**
     * Handles block breaking with the Metelytepo relic.
     * Properly drops items and plays sounds as if broken with a Diamond Pickaxe.
     *
     * @param event the block break event
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        final ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!metelytepoManager.isMetelytepo(weapon)) {
            return;
        }

        final Block block = event.getBlock();
        if (!Tag.MINEABLE_PICKAXE.isTagged(block.getType())) {
            return;
        }

        event.setCancelled(true);
        event.setDropItems(false);

        final ItemStack proxyPickaxe = createProxyPickaxeForMining(weapon);
        final var drops = block.getDrops(proxyPickaxe, player);

        final var breakSound = block.getBlockData().getSoundGroup().getBreakSound();

        block.setType(Material.AIR, false);
        for (final ItemStack drop : drops) {
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }

        block.getWorld().playSound(block.getLocation(), breakSound, 1.0F, 1.0F);
        if (event.getExpToDrop() > 0) {
            player.giveExp(event.getExpToDrop());
        }
    }

    /**
     * Handles damage events when attacking with the Metelytepo relic.
     * Applies damage modifiers based on target type and sneaking state.
     * If sneaking, triggers the Justice ability.
     *
     * @param event the entity damage event
     */
    @EventHandler(ignoreCancelled = true)
    public void onRelicDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (!metelytepoManager.isMetelytepo(player.getInventory().getItemInMainHand())) {
            return;
        }

        // Ignore the synthetic damage event triggered by Justice itself.
        if (metelytepoManager.consumeAbilityDamageBypass(target)) {
            return;
        }

        if (player.isSneaking()) {
            event.setCancelled(true);
            handleJustice(player, target);
            return;
        }

        if (!metelytepoManager.isSinner(target)) {
            playRejectedTargetSound(target.getLocation());
            event.setDamage(event.getDamage() * NON_SINNER_DAMAGE_MULTIPLIER);
            return;
        }

        if (metelytepoManager.isUndead(target)) {
            event.setDamage(event.getDamage() * UNDEAD_DAMAGE_MULTIPLIER);
            return;
        }

        final double bonus = Math.max(0.0D, JUSTICE_DAMAGE - event.getDamage());
        event.setDamage(event.getDamage() + bonus);
    }

    @EventHandler
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        final Action action = event.getAction();
        final boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        final boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!leftClick && !rightClick) {
            return;
        }

        final Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        final ItemStack item = player.getInventory().getItemInMainHand();
        if (!metelytepoManager.isMetelytepo(item)) {
            return;
        }

        if (leftClick) {
            handleJustice(player);
            return;
        }

        handleHonorEye(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        final Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (!metelytepoManager.isMetelytepo(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        handleHonorEye(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        final LivingEntity victim = event.getEntity();

        final Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }

        if (!metelytepoManager.isProtectedEntityType(victim.getType())) {
            return;
        }

        if (victim instanceof Player victimPlayer && metelytepoManager.isSinner(victimPlayer)) {
            return;
        }

        if (!metelytepoManager.isSinner(killer)) {
            metelytepoManager.markAsSinner(killer);
            killer.sendMessage(messageManager.getSinnerMarked());
        }
    }

    private void handleJustice(final Player player) {
        if (metelytepoManager.isOnJusticeCooldown(player)) {
            final long remaining = Math.max(1L, (metelytepoManager.getJusticeRemainingMillis(player) + 999L) / 1000L);
            player.sendActionBar(LEGACY.deserialize(messageManager.getAbilityCooldown(remaining)));
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45F, 1.2F);

        final Entity targetEntity = player.getTargetEntity(5, false);
        if (!(targetEntity instanceof LivingEntity target)) {
            player.sendActionBar(LEGACY.deserialize(messageManager.getNoTarget()));
            return;
        }

        handleJustice(player, target);
    }

    private void handleJustice(final Player player, final LivingEntity target) {
        if (metelytepoManager.isOnJusticeCooldown(player)) {
            final long remaining = Math.max(1L, (metelytepoManager.getJusticeRemainingMillis(player) + 999L) / 1000L);
            player.sendActionBar(LEGACY.deserialize(messageManager.getAbilityCooldown(remaining)));
            return;
        }

        final boolean sinner = metelytepoManager.isSinner(target);
        if (!sinner) {
            playRejectedTargetSound(target.getLocation());
            player.sendActionBar(LEGACY.deserialize(messageManager.getTargetNotSinner()));
            return;
        }

        // Atomic fire-gate: acquire the cooldown here (not after the effect) so a duplicate
        // interact/interact-entity event in the same tick can't fire Justice twice.
        if (!metelytepoManager.tryConsumeJusticeCooldown(player)) {
            return;
        }

        metelytepoManager.markAbilityDamageBypass(target, 1200L);
        target.damage(JUSTICE_DAMAGE, player);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, JUSTICE_EFFECT_DURATION_TICKS, JUSTICE_EFFECT_LEVEL, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, JUSTICE_EFFECT_DURATION_TICKS, JUSTICE_EFFECT_LEVEL, true, true, true));

        final double impactRadius = Math.max(0.9D, target.getHeight() * 0.45D);
        drawDarkCircle(target.getLocation(), impactRadius);
        target.getWorld().spawnParticle(Particle.SQUID_INK, target.getLocation().add(0.0D, 0.15D, 0.0D), 24, 0.2D, 0.15D, 0.2D, 0.01D);
        target.getWorld().spawnParticle(Particle.SCULK_SOUL, target.getLocation().add(0.0D, 0.8D, 0.0D), 10, 0.35D, 0.2D, 0.35D, 0.03D);
        player.getWorld().playSound(target.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0F, 0.85F);
        player.sendActionBar(LEGACY.deserialize(messageManager.getJusticeActivated()));

        player.showTitle(Title.title(
                LEGACY.deserialize(messageManager.get("messages.justice-title", "&5Justice")),
                LEGACY.deserialize(messageManager.get("messages.justice-subtitle", "&dItelet vegrehajtva")),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(250))
        ));
    }

    private void drawDarkCircle(final Location center, final double radius) {
        if (center == null || radius <= 0.0D) {
            return;
        }

        final var world = center.getWorld();
        if (world == null) {
            return;
        }

        final double step = Math.PI / 18.0D;
        for (double angle = 0.0D; angle < Math.PI * 2.0D; angle += step) {
            final double x = center.getX() + Math.cos(angle) * radius;
            final double z = center.getZ() + Math.sin(angle) * radius;
            final double y = center.getY() + 0.15D;

            world.spawnParticle(Particle.SQUID_INK, x, y, z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
            if (((int) Math.round(angle * 10.0D)) % 2 == 0) {
                world.spawnParticle(Particle.SCULK_SOUL, x, y + 0.1D, z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
        }
    }

    private void playRejectedTargetSound(final Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        location.getWorld().playSound(location, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8F, 0.5F);
        location.getWorld().playSound(location, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.3F, 0.5F);
    }

    private void handleHonorEye(final Player player) {
        if (metelytepoManager.isOnHonorEyeCooldown(player)) {
            final long remaining = Math.max(1L, (metelytepoManager.getHonorEyeRemainingMillis(player) + 999L) / 1000L);
            player.sendActionBar(LEGACY.deserialize(messageManager.getAbilityCooldown(remaining)));
            return;
        }

        // Atomic fire-gate (see handleJustice): prevents a duplicate interact event firing twice.
        if (!metelytepoManager.tryConsumeHonorEyeCooldown(player)) {
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45F, 1.2F);

        for (final Entity entity : player.getNearbyEntities(HONOR_EYE_RANGE, HONOR_EYE_RANGE, HONOR_EYE_RANGE)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }

            if (living instanceof Player targetPlayer) {
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, HONOR_EYE_GLOWING_DURATION_TICKS, HONOR_EYE_GLOWING_LEVEL, true, true, true));
                continue;
            }

            if (!(living instanceof Monster)) {
                continue;
            }

            if (!metelytepoManager.isUndead(living) && !metelytepoManager.isSinner(living)) {
                continue;
            }

            living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, HONOR_EYE_GLOWING_DURATION_TICKS, HONOR_EYE_GLOWING_LEVEL, true, true, true));

            metelytepoManager.freezeUndead(living, 20L * 60L);
        }

        // FLASH throws on this runtime because it expects extra particle data; use a no-data burst combo.
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0, 1.0, 0.0), 80, 1.4, 1.0, 1.4, 0.05);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0, 1.0, 0.0), 120, 1.6, 1.1, 1.6, 0.01);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.0F);
        player.sendActionBar(LEGACY.deserialize(messageManager.getHonorEyeActivated()));

        player.showTitle(Title.title(
                LEGACY.deserialize(messageManager.get("messages.honor-eye-title", "&6&lBECSULETSZEM AKTIVALVA")),
                LEGACY.deserialize(messageManager.get("messages.honor-eye-subtitle", "&eA bunosok felfedve")),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(250))
        ));
    }


    /**
     * Creates a proxy diamond pickaxe with the same enchantments as the source weapon.
     * Used for calculating proper block drops.
     *
     * @param sourceWeapon the weapon to copy enchantments from
     * @return a new diamond pickaxe with copied enchantments
     */
    private ItemStack createProxyPickaxeForMining(final ItemStack sourceWeapon) {
        final ItemStack proxy = new ItemStack(Material.DIAMOND_PICKAXE);
        proxy.addUnsafeEnchantments(sourceWeapon.getEnchantments());
        return proxy;
    }
}


