package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.factions.FactionFoodPolicy;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
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
    /** DARK-étel: buffot ad, de a Kitaszítottakra NINCS honvágy-kötelezettség (nincs otthonuk). */
    public static final String HAMUKENYER = "mortengradi_hamukenyer";
    /** 2. hullám — BLUE ünnepi étel (a tervtábla Sárkány-pörköltje): hal-kötelezettség + rövid Erő. */
    public static final String PORKOLT = "sarkany_porkolt";
    /** 3. hullám — ünnepi étel MINDEN frakciónak (a pörkölt párjai). */
    public static final String VADLAKOMA = "vadlakoma";
    public static final String LEPENY = "vandorunnep_lepenye";
    public static final String HAMULAKOMA = "hamvak_lakomaja";

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
    /** Melyik frakció konyhájához tartozik az időbélyeg — frakcióváltásnál a régi bélyeg
     * érvénytelen (különben a váltó azonnali debuffot VAGY jogtalan kedvezményt kapna). */
    private final NamespacedKey foodFactionKey;

    private volatile long nextCheckAt;

    public FactionFoodListener(final JavaPlugin plugin, final ConfigManager configManager,
                               final FactionManager factionManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
        this.signatureKey = new NamespacedKey(plugin, "signature_item");
        this.lastHomeFoodKey = new NamespacedKey(plugin, "faction_food_ts");
        this.foodFactionKey = new NamespacedKey(plugin, "faction_food_faction");
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();
        final boolean trustedFoodMarker = item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(
                hu.taliann.icesmp.items.ItemDataFactory.FOOD_V2_KEY, PersistentDataType.BYTE);
        final String sig = item.hasItemMeta()
                ? item.getItemMeta().getPersistentDataContainer().get(
                signatureKey, PersistentDataType.STRING) : null;
        final String trustedSignature = trustedFoodMarker ? sig : null;
        final FactionType faction = factionManager.getChosenFaction(
                player.getUniqueId()).orElse(null);
        final FileConfiguration liveConfig = configManager.snapshot().configuration();

        // Régi fejlesztői FOOD_V2 stackek a buffot még a CONSUMABLE komponensben hordozhatták.
        // A Paper csak setItem esetén használja a módosított itemet; így a régi payloadot a
        // fogyasztás előtt eltávolítjuk, majd ugyanazt a live-membership policyt alkalmazzuk.
        final ItemStack sanitized = hu.taliann.icesmp.items.ItemDataFactory
                .withoutEmbeddedSignatureFoodEffects(item);
        if (sanitized != item) {
            event.setItem(sanitized);
        }

        // A kötelezettség teljesítése: a frakcióhoz illő (vanília vagy signature) étel frissíti
        // az időbélyeget — a honvágy-debuff visszaszámlálója újraindul.
        final boolean homeFood = faction != null && switch (faction) {
            case BLUE -> FISH_FOODS.contains(item.getType())
                    || PISZTRANG.equals(trustedSignature) || PORKOLT.equals(trustedSignature);
            case RED -> EGG_FOODS.contains(item.getType())
                    || RANTOTTA.equals(trustedSignature) || VADLAKOMA.equals(trustedSignature);
            default -> false;
        };
        if (homeFood) {
            player.getPersistentDataContainer().set(lastHomeFoodKey, PersistentDataType.LONG, System.currentTimeMillis());
            player.getPersistentDataContainer().set(foodFactionKey, PersistentDataType.STRING, faction.name());
        }

        // A tárgy csak stabil signature azonosítót hordoz. Minden buffot az elfogyasztás
        // pillanatában, a live explicit tagság alapján adunk; régi item és frakcióváltás sem
        // tud itembe égetett jogosultságot megtartani.
        if (FactionFoodPolicy.mayApplyBuff(faction, sig, trustedFoodMarker)) {
            if (PISZTRANG.equals(sig)) {
                addTimedEffect(player, PotionEffectType.ABSORPTION,
                        getInt(liveConfig, "factions.food-duty.pisztrang-buff-seconds", 60), 0, true);
            } else if (RANTOTTA.equals(sig)) {
                addTimedEffect(player, PotionEffectType.FIRE_RESISTANCE,
                        getInt(liveConfig, "factions.food-duty.rantotta-buff-seconds", 60), 0, true);
            } else if (PORKOLT.equals(sig)) {
                addTimedEffect(player, PotionEffectType.STRENGTH,
                        getInt(liveConfig, "factions.food-duty.porkolt-buff-seconds", 45), 0, true);
            } else if (VADLAKOMA.equals(sig)) {
                // RED ünnepi étel: a vadászok lakomája — gyorsaság + rövid tűz-oltalom.
                final int seconds = getInt(liveConfig,
                        "factions.food-duty.vadlakoma-buff-seconds", 45);
                addTimedEffect(player, PotionEffectType.SPEED, seconds, 0, true);
                addTimedEffect(player, PotionEffectType.FIRE_RESISTANCE, seconds, 0, true);
            } else if (LEPENY.equals(sig)) {
                // NEUTRAL ünnepi étel: aratóünnep — szerencse a piachoz/zsákmányhoz + fürgeség.
                final int seconds = getInt(liveConfig,
                        "factions.food-duty.lepeny-buff-seconds", 60);
                addTimedEffect(player, PotionEffectType.LUCK, seconds, 0, true);
                addTimedEffect(player, PotionEffectType.SPEED, seconds, 0, true);
            } else if (HAMULAKOMA.equals(sig)) {
                // DARK ünnepi étel: a megosztott keserű tál — felszívódás + éjjellátás.
                final int seconds = getInt(liveConfig,
                        "factions.food-duty.hamulakoma-buff-seconds", 60);
                addTimedEffect(player, PotionEffectType.ABSORPTION, seconds, 0, true);
                addTimedEffect(player, PotionEffectType.NIGHT_VISION, seconds, 0, true);
            } else if (HAMUKENYER.equals(sig)) {
                addTimedEffect(player, PotionEffectType.NIGHT_VISION,
                        getInt(liveConfig, "factions.food-duty.hamukenyer-buff-seconds", 60), 0, true);
            } else if (SUTI.equals(sig)) {
                // "Robbanó csemege": effekt-robbanás blokk-kár nélkül + felfelé lökés + gyorsaság.
                addTimedEffect(player, PotionEffectType.SPEED,
                        getInt(liveConfig, "factions.food-duty.suti-speed-seconds", 30), 1, true);
                player.setVelocity(player.getVelocity().setY(Math.max(0.4D,
                        getDouble(liveConfig, "factions.food-duty.suti-launch-y", 0.6D))));
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8F, 1.2F);
                hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(), org.bukkit.Particle.FIREWORK,
                        player.getLocation().add(0.0D, 1.0D, 0.0D), 30, 0.5D, 0.6D, 0.5D, 0.08D);
            }
        }
    }

    /**
     * Periodikus honvágy-ellenőrzés a globális world-events tickről: a check-minutes
     * ütemben minden online BLUE/RED játékosnál a SAJÁT régió-szálán nézzük az utolsó
     * hazai étkezés időbélyegét. Új játékos / frissen váltó: az első ellenőrzés csak
     * beállítja az időbélyeget (türelmi idő indul), debuff nélkül.
     */
    public void tick() {
        final FileConfiguration liveConfig = configManager.snapshot().configuration();
        if (!getBoolean(liveConfig, "factions.food-duty.enabled", true)) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < nextCheckAt) {
            return;
        }
        final long intervalMillis = FactionFoodPolicy.durationMillis(
                getLong(liveConfig, "factions.food-duty.check-minutes", 5L), 60_000L);
        final long nextDeadline = FactionFoodPolicy.deadline(now, intervalMillis);
        if (nextDeadline <= 0L) {
            return;
        }
        nextCheckAt = nextDeadline;

        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            player.getScheduler().run(plugin, task -> {
                if (!player.isOnline()) {
                    return;
                }
                final FileConfiguration callbackConfig =
                        configManager.snapshot().configuration();
                final FactionType faction = factionManager.getChosenFaction(
                        player.getUniqueId()).orElse(null);
                if (!FactionFoodPolicy.mayRunDutyCallback(
                        getBoolean(callbackConfig, "factions.food-duty.enabled", true), faction)) {
                    return;
                }
                final long graceMillis = FactionFoodPolicy.durationMillis(
                        getLong(callbackConfig, "factions.food-duty.grace-hours", 12L),
                        3_600_000L);
                final int debuffTicks = FactionFoodPolicy.durationTicks(
                        getInt(callbackConfig, "factions.food-duty.debuff-seconds", 10));
                if (graceMillis <= 0L || debuffTicks <= 0) {
                    return;
                }
                final long callbackNow = System.currentTimeMillis();
                final Long last = player.getPersistentDataContainer().get(
                        lastHomeFoodKey, PersistentDataType.LONG);
                final String tsFaction = player.getPersistentDataContainer().get(
                        foodFactionKey, PersistentDataType.STRING);
                if (last == null || last < 0L || last > callbackNow
                        || !faction.name().equals(tsFaction)) {
                    // Új játékos, sérült/jövőbeli bélyeg VAGY frissen váltó: a türelmi idő
                    // újraindul — először csak jegyezzük az időt, debuff nélkül.
                    player.getPersistentDataContainer().set(
                            lastHomeFoodKey, PersistentDataType.LONG, callbackNow);
                    player.getPersistentDataContainer().set(
                            foodFactionKey, PersistentDataType.STRING, faction.name());
                    return;
                }
                if (!FactionFoodPolicy.hasGraceElapsed(callbackNow, last, graceMillis)) {
                    return;
                }
                // Puha honvágy-debuff: rövid Éhség + emlékeztető (a súlyát a config szabja).
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.HUNGER, debuffTicks, 0, true, false, true));
                player.sendActionBar(messageManager.getMessage(
                        faction == FactionType.BLUE ? "faction-food-duty-blue" : "faction-food-duty-red",
                        faction == FactionType.BLUE
                                ? "<gray>❄ Hiányzik az otthon íze — a Jégmezők népe halon él. Egyél halat!</gray>"
                                : "<gray>🔥 Hiányzik az otthon íze — a Vérszavanna népe tojás-ételen él. Egyél rántottát, sütőtökös pitét vagy tortát!</gray>"));
            }, null);
        }
    }

    private static void addTimedEffect(final Player player, final PotionEffectType type,
                                       final int seconds, final int amplifier,
                                       final boolean particles) {
        final int ticks = FactionFoodPolicy.durationTicks(seconds);
        if (ticks > 0) {
            player.addPotionEffect(new PotionEffect(
                    type, ticks, amplifier, true, particles, true));
        }
    }

    private static boolean getBoolean(final FileConfiguration config, final String path,
                                      final boolean fallback) {
        return config == null ? fallback : config.getBoolean(path, fallback);
    }

    private static int getInt(final FileConfiguration config, final String path,
                              final int fallback) {
        return config == null ? fallback : config.getInt(path, fallback);
    }

    private static long getLong(final FileConfiguration config, final String path,
                                final long fallback) {
        return config == null ? fallback : config.getLong(path, fallback);
    }

    private static double getDouble(final FileConfiguration config, final String path,
                                    final double fallback) {
        return config == null ? fallback : config.getDouble(path, fallback);
    }
}
