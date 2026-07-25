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

/**
 * K9 — a Suttogók eseménykezelője.
 *
 * <p><b>A Sötét Rítus:</b> éjjel, SCULK-blokkon állva, MAGÁNYOSAN (nincs másik játékos
 * a konfigurált sugáron belül), kézben a Suttogás-meghívóval SHIFT+jobb katt — a
 * meghívó elég, az ára saját vér (HP-áldozat). Ha valaki mégis a közelben van, a rítus
 * MEGHIÚSUL és a szemtanúk Tanú-tokent + a jelölt nagy gyanút kap.
 *
 * <p><b>Rajtakapott árulás:</b> ha egy Suttogó a saját (látható) frakciótársát öli meg,
 * a közelben álló nem-Suttogó játékosok Tanú-tokent kapnak, és a gyilkos gyanúja nagyot
 * ugrik — a bűn-rendszer büntetése ettől függetlenül fut.
 *
 * <p>Folia: a death-event a VICTIM régió-szálán fut; a killer/gyanú-írás a killer saját
 * schedulerén, a szemtanú-értesítés a szemtanú saját schedulerén történik.
 */
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

    /** Join a játékos saját régió-szálán fut — a Suttogó-cache itt töltődik (PDC-olvasás). */
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
        if (!player.isSneaking() || !whisperManager.isEnabled()) {
            return;
        }
        final ItemStack hand = player.getInventory().getItemInMainHand();
        if (!"suttogas_meghivo".equals(uniqueMaterials.idOf(hand))) {
            return;
        }
        event.setCancelled(true);

        if (!whisperManager.canBecomeWhisperer(player)) {
            player.sendMessage(messageManager.get("whisper-rite-invalid",
                    "&7A Suttogás nem szól hozzád — vagy már hallod, vagy a Királynő már a magáénak tud."));
            return;
        }
        // Éjjel (vagy örök félhomályú dimenzióban)…
        final org.bukkit.World world = player.getWorld();
        if (world.getEnvironment() == org.bukkit.World.Environment.NORMAL && world.isDayTime()) {
            player.sendMessage(messageManager.get("whisper-rite-day",
                    "&7A Suttogás csak az éj leple alatt hallható."));
            return;
        }
        // …sculk-on állva…
        final Material below = player.getLocation().clone().add(0.0D, -0.5D, 0.0D).getBlock().getType();
        if (below != Material.SCULK && below != Material.SCULK_CATALYST) {
            player.sendMessage(messageManager.get("whisper-rite-ground",
                    "&7A meghívó hideg marad — a Suttogás a mélység burjánzó sötétjét (sculk) kívánja a lábad alá."));
            return;
        }
        // …és MAGÁNYOSAN. Ha mégis lát valaki: a rítus meghiúsul, a szemtanúk Tanú-tokent kapnak.
        final double radius = Math.max(4.0D, configManager.getDouble("factions.whisper.rite-alone-radius", 16.0D));
        boolean witnessed = false;
        for (final Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player witness && !witness.getUniqueId().equals(player.getUniqueId())) {
                witnessed = true;
                whisperManager.grantWitnessToken(witness.getUniqueId());
                witness.getScheduler().run(plugin, task -> witness.sendMessage(messageManager.getMessage(
                        "whisper-witness-rite",
                        "<dark_purple>👁 Sötét rítust láttál… A vádhoz: <white>/suttogas vad {player}</white> (amíg az emlék friss).</dark_purple>",
                        Map.of("player", player.getName()))), null);
            }
        }
        if (witnessed) {
            whisperManager.addSuspicion(player, Math.max(0.0D,
                    configManager.getDouble("factions.whisper.caught-rite-suspicion", 50.0D)));
            player.sendMessage(messageManager.get("whisper-rite-witnessed",
                    "&cSzemek a sötétben — a rítus megszakadt, és valaki LÁTOTT."));
            return;
        }

        // Az ár: saját vér. A meghívó elfogy, a suttogás befogad.
        final double hpCost = Math.max(0.0D, configManager.getDouble("factions.whisper.rite-hp-cost", 6.0D));
        if (hpCost > 0.0D && player.getHealth() <= hpCost + 1.0D) {
            player.sendMessage(messageManager.get("whisper-rite-weak",
                    "&cTúl gyenge vagy a vér-áldozathoz — a Suttogás nem fogad el haldoklót."));
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        if (hpCost > 0.0D) {
            player.setHealth(Math.max(1.0D, player.getHealth() - hpCost));
        }
        whisperManager.makeWhisperer(player);
        // Rejtett, toast/chat-mentes bejegyzés — a Suttogó-státusz titkos marad.
        hu.taliann.icesmp.managers.AdvancementService.award(player, "whisperer");
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.BLINDNESS, 2 * 20, 0, false, false, false));
        player.getWorld().spawnParticle(Particle.SQUID_INK, player.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.5D, 0.8D, 0.5D, 0.02D);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.8F, 0.4F);
        player.playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.0F, 0.6F);
        player.sendMessage(messageManager.get("whisper-rite-success",
                "&5✧ A vércsepp a sculkba szivárog… és a sötét MEGSZÓLAL. Mostantól hallod a Suttogást (&f/suttogas <üzenet>&5). Őrizd a titkot — a lelepleződés a Kitaszítottak közé taszít."));
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
        final FactionType killerFaction = factionManager.getFaction(killer.getUniqueId());
        final FactionType victimFaction = factionManager.getFaction(victim.getUniqueId());
        if (killerFaction != victimFaction || killerFaction == FactionType.NEUTRAL || killerFaction == FactionType.DARK) {
            return;
        }
        if (raidManager.isSanctionedKill(killer.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        // Szemtanúk: a halál körüli nem-gyilkos játékosok Tanú-tokent kapnak (a VICTIM
        // régió-lokális környezete), plusz árulkodó jel a levegőben.
        final double radius = Math.max(4.0D, configManager.getDouble("factions.whisper.witness-radius", 24.0D));
        for (final Entity nearby : victim.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player witness && !witness.getUniqueId().equals(killer.getUniqueId())) {
                whisperManager.grantWitnessToken(witness.getUniqueId());
                witness.getScheduler().run(plugin, task -> witness.sendMessage(messageManager.getMessage(
                        "whisper-witness-betrayal",
                        "<dark_purple>👁 Testvérgyilkosságot láttál… Ha Suttogót sejtesz: <white>/suttogas vad {player}</white>.</dark_purple>",
                        Map.of("player", killer.getName()))), null);
            }
        }
        victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0.0D, 1.0D, 0.0D), 14, 0.4D, 0.7D, 0.4D, 0.02D);

        // A Suttogó gyilkos gyanúja a SAJÁT szálán nő (küszöbnél azonnali leleplezés).
        final double amount = Math.max(0.0D, configManager.getDouble("factions.whisper.betrayal-suspicion", 40.0D));
        killer.getScheduler().run(plugin, task -> whisperManager.addSuspicion(killer, amount), null);
    }
}
