package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Perk behaviour for the crafted signature items (K2 — Cryghaliris; extensible for K3). Items are
 * recognised by the {@code signature_item} PDC id stamped by the recipe engine:
 * <ul>
 *   <li><b>kallan_szeletelo</b> (íj): gyorsabb nyíl + „páncéltörő" bónusz-sebzés a lövedéken.</li>
 *   <li><b>glatziendorfi_jegvert</b> (mellvért): viselve sebzés-csökkentés (Resistance I-jellegű).</li>
 *   <li><b>jegsarkany_kantar</b>: jobb katt egy hátason → tartós sebesség-bónusz (elfogy).</li>
 * </ul>
 * Folia: a lövés a lövő szálán fut (a friss lövedék régió-lokális); a sebzés-események az áldozat
 * szálán, ahol a nyíl/mellvért lokális; a kantár a hátas schedulerére hopol, majd vissza a
 * játékoséra az item-fogyasztáshoz.
 */
public final class SignatureItemListener implements Listener {

    public static final String KALLAN_BOW = "kallan_szeletelo";
    public static final String JEGVERT = "glatziendorfi_jegvert";
    public static final String KANTAR = "jegsarkany_kantar";
    public static final String TUZKOPO = "pyralingradi_tuzkopo";
    public static final String AGYAR = "verszavanna_agyara";
    public static final String TOLLKOPENY = "fonix_tollkopeny";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final NamespacedKey signatureKey;
    private final NamespacedKey pierceKey;
    private final NamespacedKey kantarAppliedKey;
    private final NamespacedKey kantarSpeedKey;

    public SignatureItemListener(final JavaPlugin plugin, final ConfigManager configManager,
                                 final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.signatureKey = new NamespacedKey(plugin, "signature_item");
        this.pierceKey = new NamespacedKey(plugin, "sig_pierce");
        this.kantarAppliedKey = new NamespacedKey(plugin, "sig_kantar");
        this.kantarSpeedKey = new NamespacedKey(plugin, "sig_kantar_speed");
    }

    /** The signature id of an item, or null. */
    private String idOf(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(signatureKey, PersistentDataType.STRING);
    }

    // ==================== Kallan Szeletelője ====================

    @EventHandler(ignoreCancelled = true)
    public void onShoot(final EntityShootBowEvent event) {
        final String sig = idOf(event.getBow());
        if (KALLAN_BOW.equals(sig)) {
            final double mult = Math.max(1.0D, configManager.getDouble("signature.kallan.arrow-velocity-mult", 1.5D));
            final Entity projectile = event.getProjectile();
            projectile.setVelocity(projectile.getVelocity().multiply(mult));
            final double pierce = Math.max(0.0D, configManager.getDouble("signature.kallan.armor-pierce", 0.15D));
            if (pierce > 0.0D) {
                projectile.getPersistentDataContainer().set(pierceKey, PersistentDataType.DOUBLE, pierce);
            }
        } else if (TUZKOPO.equals(sig)) {
            // A Tűzköpő lövedéke a „sivatagi vihar sebességével" repül; a felhúzási gyorsítást a
            // craftkor kapott Quick Charge adja (ProfessionRecipeBookListener).
            final double mult = Math.max(1.0D, configManager.getDouble("signature.tuzkopo.arrow-velocity-mult", 1.5D));
            final Entity projectile = event.getProjectile();
            projectile.setVelocity(projectile.getVelocity().multiply(mult));
        }
    }

    // ==================== A Vérszavanna Agyara ====================

    @EventHandler(ignoreCancelled = true)
    public void onMelee(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        // Közelharc: a támadó és az áldozat ütés-távolságon belül van — Folián ez azonos régiót
        // jelent (a kereszt-régió kockázat a távolsági/projectile esetekre áll), a felszerelés-
        // olvasás itt biztonságos.
        if (!AGYAR.equals(idOf(attacker.getInventory().getItemInMainHand()))) {
            return;
        }
        final boolean offhandAxe = attacker.getInventory().getItemInOffHand().getType().name().endsWith("_AXE");
        final double mult = offhandAxe
                ? Math.max(1.0D, configManager.getDouble("signature.agyar.offhand-axe-mult", 1.3D))
                : Math.max(1.0D, configManager.getDouble("signature.agyar.damage-mult", 1.15D));
        event.setDamage(event.getDamage() * mult);
    }

    @EventHandler(ignoreCancelled = true)
    public void onArrowDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow)) {
            return;
        }
        final Double pierce = arrow.getPersistentDataContainer().get(pierceKey, PersistentDataType.DOUBLE);
        if (pierce == null || pierce <= 0.0D) {
            return;
        }
        // A „páncéltörés" végső-sebzés bónuszként valósul meg — a vanília páncél-formulát nem
        // hackeljük, így a vanília-limiteken belül marad (lásd K2 buktatók).
        event.setDamage(event.getDamage() * (1.0D + pierce));
    }

    // ==================== Glatziendorfi Jégvért ====================

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamaged(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        final String chest = idOf(player.getInventory().getChestplate());
        if (JEGVERT.equals(chest)) {
            final double mult = Math.min(1.0D, Math.max(0.0D,
                    configManager.getDouble("signature.jegvert.damage-mult", 0.8D)));
            event.setDamage(event.getDamage() * mult);
            return;
        }
        // Főnix-Tollköpeny: tűz/láva-védelem tárgyként hordozva — bárki viselheti, aki megszerzi
        // (a craft RED-kapus, a köpeny kereskedhető).
        if (TOLLKOPENY.equals(chest) && isFireCause(event.getCause())
                && configManager.getBoolean("signature.tollkopeny.fire-immunity", true)) {
            event.setCancelled(true);
        }
    }

    private static boolean isFireCause(final EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }

    // ==================== Jégsárkány-Kantár ====================

    @EventHandler(ignoreCancelled = true)
    public void onKantarUse(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Player player = event.getPlayer();
        if (!KANTAR.equals(idOf(player.getInventory().getItemInMainHand()))) {
            return;
        }
        if (!(event.getRightClicked() instanceof AbstractHorse horse)) {
            return;
        }
        event.setCancelled(true);
        final double add = Math.max(0.0D, configManager.getDouble("signature.kantar.speed-add", 0.05D));
        // A hátas MÁSIK entitás — minden érintése a saját schedulerén (Folia), majd vissza a
        // játékos szálára az item-fogyasztáshoz és üzenethez.
        horse.getScheduler().run(plugin, task -> {
            if (horse.getPersistentDataContainer().has(kantarAppliedKey, PersistentDataType.BYTE)) {
                player.getScheduler().run(plugin, t2 -> player.sendMessage(messageManager.get(
                        "signature-kantar-already", "&7Ez a hátas már felkantározott.")), null);
                return;
            }
            final AttributeInstance speed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed == null) {
                return;
            }
            speed.addModifier(new AttributeModifier(kantarSpeedKey, add, AttributeModifier.Operation.ADD_NUMBER));
            horse.getPersistentDataContainer().set(kantarAppliedKey, PersistentDataType.BYTE, (byte) 1);
            player.getScheduler().run(plugin, t2 -> {
                // A kantár csak akkor fogy el, ha még mindig a kézben van (a hop alatt elrakhatta) —
                // ritka kihagyás elfogadható, dupla-fogyasztás nem történhet.
                final ItemStack hand = player.getInventory().getItemInMainHand();
                if (KANTAR.equals(idOf(hand))) {
                    hand.setAmount(hand.getAmount() - 1);
                }
                player.sendMessage(messageManager.get("signature-kantar-applied",
                        "&b❄ A vad sárkányvér megszelídül — a hátasod léptei felgyorsultak."));
            }, null);
        }, null);
    }
}
