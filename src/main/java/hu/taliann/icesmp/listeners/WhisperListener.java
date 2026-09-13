package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.managers.WhisperManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/** Owner-thread rite discovery and witnessed, explicit signs of the hidden role. */
public final class WhisperListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final WhisperManager whisperManager;
    private final FactionManager factionManager;
    private final RaidManager raidManager;
    private final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials;
    private final MessageManager messageManager;

    public WhisperListener(final JavaPlugin plugin, final ConfigManager configManager,
                           final WhisperManager whisperManager, final FactionManager factionManager,
                           final RaidManager raidManager,
                           final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials,
                           final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.whisperManager = whisperManager;
        this.factionManager = factionManager;
        this.raidManager = raidManager;
        this.uniqueMaterials = uniqueMaterials;
        this.messageManager = messageManager;
    }

    // ==================== A Sötét Rítus ====================

    /** Join a játékos saját régió-szálán fut — a Suttogó-cache itt töltődik a tartós profilból. */
    @EventHandler
    public void onJoin(final org.bukkit.event.player.PlayerJoinEvent event) {
        whisperManager.handleJoin(event.getPlayer());
    }

    // Szándékosan NINCS ignoreCancelled: a RIGHT_CLICK_AIR "cancelled" állapottal
    // érkezik, és elnyelné a levegőbe-kattintós rítus-invokációt.
    @EventHandler
    public void onRite(final PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        final Player player = event.getPlayer();
        if (whisperManager.ritualPending(player.getUniqueId())) { event.setCancelled(true); return; }
        if (!whisperManager.isEnabled()) {
            return;
        }
        final ItemStack hand = player.getInventory().getItemInMainHand();
        if (!"suttogas_meghivo".equals(uniqueMaterials.idOf(hand))) {
            return;
        }
        event.setCancelled(true);
        if (!player.isSneaking()) { offerHint(player); return; }

        if (!whisperManager.canBecomeWhisperer(player)) {
            final long remaining;
            try { remaining = whisperManager.returnRemainingMillis(player); }
            catch (final RuntimeException unavailable) {
                player.sendMessage(messageManager.get("whisper-profile-unavailable", "&cA titkos profil most nem érhető el. Próbáld újra."));
                return;
            }
            if (remaining > 0L) {
                player.sendMessage(messageManager.get("whisper-rite-wait", "&7Új rítus előtt még &f%s mp &7várakozás szükséges.", (remaining + 999L) / 1000L));
                return;
            }
            player.sendMessage(messageManager.get("whisper-rite-invalid",
                    "&7A Suttogás nem szól hozzád — vagy már hallod, vagy a Királynő már a magáénak tud téged."));
            return;
        }
        // The rite needs the overworld night; Nether/End are not an always-open shortcut.
        final org.bukkit.World world = player.getWorld();
        if (world.getEnvironment() != org.bukkit.World.Environment.NORMAL || world.isDayTime()) {
            player.sendMessage(messageManager.get("whisper-rite-day",
                    "&7A Suttogás a normál világ éjszakáját kívánja. A Nether és a Vég nem helyettesíti az éjt."));
            return;
        }
        // …sculk-on állva…
        final var ground = player.getLocation().add(0.0D, -0.5D, 0.0D);
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(ground)) return;
        final Material below = ground.getBlock().getType();
        if (below != Material.SCULK && below != Material.SCULK_CATALYST) {
            player.sendMessage(messageManager.get("whisper-rite-ground",
                    "&7A meghívó hideg marad — a Suttogás a mélység burjánzó sötétjét kívánja a lábad alá. &8(sculk vagy sculk-katalizátor blokkon állj)"));
            return;
        }
        // Az ár: saját vér. A meghívó elfogy, a suttogás befogad.
        final double configuredCost = configManager.getDouble("factions.whisper.rite-hp-cost", 6.0D);
        final double hpCost = Double.isFinite(configuredCost) ? Math.max(0.0D, configuredCost) : 6.0D;
        if (hpCost > 0.0D && player.getHealth() <= hpCost + 1.0D) {
            player.sendMessage(messageManager.get("whisper-rite-weak",
                    "&cTúl gyenge vagy a vér-áldozathoz — a Suttogás nem fogad el haldoklót."));
            return;
        }
        final double radius = Math.max(4.0D, Math.min(64.0D,
                configManager.getDouble("factions.whisper.rite-witness-radius", 16.0D)));
        whisperManager.beginRite(player, hpCost, radius, () -> {
        // Rejtett, toast/chat-mentes bejegyzés — a Suttogó-státusz titkos marad.
        hu.taliann.icesmp.managers.AdvancementService.award(player, "whisperer");
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.BLINDNESS, 2 * 20, 0, false, false, false));
        player.getWorld().spawnParticle(Particle.SQUID_INK, player.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.5D, 0.8D, 0.5D, 0.02D);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.8F, 0.4F);
        player.playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.0F, 0.6F);
        player.sendMessage(messageManager.get("whisper-rite-success",
                "&5✧ A vércsepp a burjánzó sötétbe szivárog… és a mélység MEGSZÓLAL. Mostantól hallod a Suttogást (&f/suttogas <üzenet>&5). &f/suttogas állapot &5és &f/suttogas megbízás&5. A harmadik hiteles vád száműz; a DARK eskü külön döntés. Visszalépés: &f/suttogas megtagadás"));
        });
    }

    // ==================== Rajtakapott árulás ====================

    @EventHandler
    public void onBetrayalKill(final PlayerDeathEvent event) {
        if (!whisperManager.isEnabled()) {
            return;
        }
        final Player victim = event.getEntity();
        final Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        final FactionType killerFaction = factionManager.getChosenFaction(killer.getUniqueId()).orElse(null);
        final FactionType victimFaction = factionManager.getChosenFaction(victim.getUniqueId()).orElse(null);
        if (killerFaction == null || killerFaction != victimFaction
                || killerFaction == FactionType.DARK) {
            return;
        }
        if (raidManager.isSanctionedKill(killer.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        final double radius = Math.max(4.0D, Math.min(64.0D, configManager.getDouble("factions.whisper.witness-radius", 24.0D)));
        final var deathScene = victim.getEyeLocation().clone();
        final long deathAt = System.currentTimeMillis();
        final var witnesses = victim.getNearbyEntities(radius, radius, radius).stream()
                .filter(Player.class::isInstance).limit(64).map(Entity::getUniqueId).toList();
        killer.getScheduler().run(plugin, task -> {
            if (System.currentTimeMillis() - deathAt > 1_000L || !whisperManager.isWhisperer(killer)
                    || killer.getWorld() != deathScene.getWorld()
                    || killer.getEyeLocation().distanceSquared(deathScene) > radius * radius) return;
            final var scene = whisperManager.capture(killer,
                    hu.taliann.icesmp.playerprofile.application.PlayerProfileWhisperStore.EvidenceType.BETRAYAL);
            if (!scene.identifiable()) return;
            killer.getWorld().spawnParticle(Particle.SOUL, killer.getLocation().add(0, 1, 0), 14, 0.4, 0.7, 0.4, 0.02);
            for (final var witness : witnesses) whisperManager.observe(witness, scene, radius, "whisper-witness-betrayal");
        }, null);
    }

    private void offerHint(final Player player) {
        if (!whisperManager.isEnabled() || player.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL
                || player.getWorld().isDayTime()
                || !"suttogas_meghivo".equals(uniqueMaterials.idOf(player.getInventory().getItemInMainHand()))) return;
        final var below = player.getLocation().add(0, -0.5, 0);
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(below)) return;
        final var type = below.getBlock().getType();
        if (type == Material.SCULK || type == Material.SCULK_CATALYST) whisperManager.hint(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(final org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.hasChangedBlock()) offerHint(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(final org.bukkit.event.player.PlayerItemHeldEvent event) {
        if (whisperManager.ritualPending(event.getPlayer().getUniqueId())) { event.setCancelled(true); return; }
        event.getPlayer().getScheduler().run(plugin, task -> offerHint(event.getPlayer()), null);
    }

    @EventHandler
    public void onRespawn(final org.bukkit.event.player.PlayerRespawnEvent event) {
        whisperManager.respawned(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(final org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (whisperManager.ritualPending(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && whisperManager.ritualPending(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && whisperManager.ritualPending(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(final org.bukkit.event.player.PlayerDropItemEvent event) {
        if (whisperManager.ritualPending(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(final org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        if (whisperManager.ritualPending(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(final org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && whisperManager.ritualPending(player.getUniqueId())) event.setCancelled(true);
    }

}
