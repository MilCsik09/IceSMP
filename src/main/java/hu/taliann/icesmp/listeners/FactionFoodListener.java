package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Set;

/**
 * K6 — frakció-ételek és fogyasztási kötelezettség. A kódex szerint a Fagy népe
 * halat, a Láng népe tojás-ételt eszik a frakció-aurája ellen: aki régóta nem
 * evett a "hazai konyhából", enyhe honvágy-debuffot kap (config-kapcsolható,
 * puha — rövid Éhség + action-bar emlékeztető). A K6 signature ételek fogyasztása
 * emellett kis, tematikus buffot is ad; a Tiltott Kakaóbabos Sütemény a "robbanó
 * csemege" (felfelé lökés + gyorsaság + effekt-robbanás, blokk-kár nélkül).
 *
 * <p>Folia: a consume-event a játékos saját régió-szálán fut (PDC-írás/buff ott
 * biztonságos); a periodikus kötelezettség-ellenőrzés a globális tickről hopol
 * minden online játékos saját schedulerére.
 */
public final class FactionFoodListener implements Listener {

    /** K6 signature étel-azonosítók (a recept-motor stampeli a PDC-be). */
    public static final String PISZTRANG = "fagyasztott_pisztrang";
    public static final String RANTOTTA = "fonixtojas_rantotta";
    public static final String SUTI = "kakaobabos_sutemeny";

    /** Vanília ételek, amelyek teljesítik a BLUE hal-kötelezettségét. */
    private static final Set<Material> FISH_FOODS = Set.of(
            Material.COD, Material.SALMON, Material.COOKED_COD, Material.COOKED_SALMON, Material.TROPICAL_FISH);
    /** Vanília tojásos ételek, amelyek teljesítik a RED kötelezettségét. */
    private static final Set<Material> EGG_FOODS = Set.of(Material.PUMPKIN_PIE, Material.CAKE);

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;
    private final NamespacedKey signatureKey;
    /** Az utolsó "hazai étel" fogyasztás időbélyege (player-PDC — nem szivárgó map). */
    private final NamespacedKey lastHomeFoodKey;

    private volatile long nextCheckAt;

    public FactionFoodListener(final JavaPlugin plugin, final ConfigManager configManager,
                               final FactionManager factionManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
        this.signatureKey = new NamespacedKey(plugin, "signature_item");
        this.lastHomeFoodKey = new NamespacedKey(plugin, "faction_food_ts");
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();
        final String sig = item.hasItemMeta()
                ? item.getItemMeta().getPersistentDataContainer().get(signatureKey, PersistentDataType.STRING)
                : null;
        final FactionType faction = factionManager.getFaction(player.getUniqueId());

        // A kötelezettség teljesítése: a frakcióhoz illő (vanília vagy signature) étel frissíti
        // az időbélyeget — a honvágy-debuff visszaszámlálója újraindul.
        final boolean homeFood = switch (faction) {
            case BLUE -> FISH_FOODS.contains(item.getType()) || PISZTRANG.equals(sig);
            case RED -> EGG_FOODS.contains(item.getType()) || RANTOTTA.equals(sig);
            default -> false;
        };
        if (homeFood) {
            player.getPersistentDataContainer().set(lastHomeFoodKey, PersistentDataType.LONG, System.currentTimeMillis());
        }

        // Signature étel-buffok (kicsik, tematikusak; a consume a játékos saját szálán fut).
        if (PISZTRANG.equals(sig)) {
            final int seconds = Math.max(1, configManager.getInt("factions.food-duty.pisztrang-buff-seconds", 60));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, seconds * 20, 0, true, true, true));
        } else if (RANTOTTA.equals(sig)) {
            final int seconds = Math.max(1, configManager.getInt("factions.food-duty.rantotta-buff-seconds", 60));
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, seconds * 20, 0, true, true, true));
        } else if (SUTI.equals(sig)) {
            // "Robbanó csemege": effekt-robbanás blokk-kár nélkül + felfelé lökés + gyorsaság.
            final int seconds = Math.max(1, configManager.getInt("factions.food-duty.suti-speed-seconds", 30));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 1, true, true, true));
            player.setVelocity(player.getVelocity().setY(Math.max(0.4D,
                    configManager.getDouble("factions.food-duty.suti-launch-y", 0.6D))));
            player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8F, 1.2F);
            hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(), org.bukkit.Particle.FIREWORK,
                    player.getLocation().add(0.0D, 1.0D, 0.0D), 30, 0.5D, 0.6D, 0.5D, 0.08D);
        }
    }

    /**
     * Periodikus honvágy-ellenőrzés a globális world-events tickről: a check-minutes
     * ütemben minden online BLUE/RED játékosnál a SAJÁT régió-szálán nézzük az utolsó
     * hazai étkezés időbélyegét. Új játékos / frissen váltó: az első ellenőrzés csak
     * beállítja az időbélyeget (türelmi idő indul), debuff nélkül.
     */
    public void tick() {
        if (!configManager.getBoolean("factions.food-duty.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < nextCheckAt) {
            return;
        }
        nextCheckAt = now + Math.max(1L, configManager.getLong("factions.food-duty.check-minutes", 5L)) * 60_000L;

        final long graceMillis = Math.max(1L, configManager.getLong("factions.food-duty.grace-hours", 12L)) * 3_600_000L;
        final int debuffSeconds = Math.max(1, configManager.getInt("factions.food-duty.debuff-seconds", 10));

        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            final FactionType faction = factionManager.getFaction(player.getUniqueId());
            if (faction != FactionType.BLUE && faction != FactionType.RED) {
                continue;
            }
            player.getScheduler().run(plugin, task -> {
                final Long last = player.getPersistentDataContainer().get(lastHomeFoodKey, PersistentDataType.LONG);
                if (last == null) {
                    // Türelmi idő indul — először csak jegyezzük az időt.
                    player.getPersistentDataContainer().set(lastHomeFoodKey, PersistentDataType.LONG, System.currentTimeMillis());
                    return;
                }
                if (System.currentTimeMillis() - last < graceMillis) {
                    return;
                }
                // Puha honvágy-debuff: rövid Éhség + emlékeztető (a súlyát a config szabja).
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, debuffSeconds * 20, 0, true, false, true));
                player.sendActionBar(messageManager.getMessage(
                        faction == FactionType.BLUE ? "faction-food-duty-blue" : "faction-food-duty-red",
                        faction == FactionType.BLUE
                                ? "<gray>❄ Hiányzik az otthon íze — a Jégmezők népe halon él. Egyél halat!</gray>"
                                : "<gray>🔥 Hiányzik az otthon íze — a Vérszavanna népe tojás-ételen él. Egyél rántottát!</gray>"));
            }, null);
        }
    }
}
