package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.factions.FactionFoodPolicy;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.FactionManager;
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

/** Applies simple live-membership buffs from trusted faction signature foods. */
public final class FactionFoodListener implements Listener {

    public static final String PISZTRANG = "fagyasztott_pisztrang";
    public static final String RANTOTTA = "fonixtojas_rantotta";
    public static final String SUTI = "kakaobabos_sutemeny";
    public static final String HAMUKENYER = "mortengradi_hamukenyer";
    public static final String PORKOLT = "sarkany_porkolt";
    public static final String VADLAKOMA = "vadlakoma";
    public static final String LEPENY = "vandorunnep_lepenye";
    public static final String HAMULAKOMA = "hamvak_lakomaja";

    private final ConfigManager configManager;
    private final FactionManager factionManager;
    private final NamespacedKey signatureKey;

    public FactionFoodListener(final JavaPlugin plugin, final ConfigManager configManager,
                               final FactionManager factionManager) {
        this.configManager = configManager;
        this.factionManager = factionManager;
        this.signatureKey = new NamespacedKey(plugin, "signature_item");
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();
        final boolean trustedFoodMarker = item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(
                hu.taliann.icesmp.items.ItemDataFactory.FOOD_V2_KEY, PersistentDataType.BYTE);
        final String signature = item.hasItemMeta()
                ? item.getItemMeta().getPersistentDataContainer().get(
                signatureKey, PersistentDataType.STRING) : null;
        final FactionType faction = factionManager.getChosenFaction(
                player.getUniqueId()).orElse(null);
        final FileConfiguration liveConfig = configManager.snapshot().configuration();

        final ItemStack sanitized = hu.taliann.icesmp.items.ItemDataFactory
                .withoutEmbeddedSignatureFoodEffects(item);
        if (sanitized != item) {
            event.setItem(sanitized);
        }

        if (!FactionFoodPolicy.mayApplyBuff(faction, signature, trustedFoodMarker)) {
            return;
        }
        if (PISZTRANG.equals(signature)) {
            addTimedEffect(player, PotionEffectType.ABSORPTION,
                    getInt(liveConfig, "factions.signature-food.pisztrang-buff-seconds", 60), 0, true);
        } else if (RANTOTTA.equals(signature)) {
            addTimedEffect(player, PotionEffectType.FIRE_RESISTANCE,
                    getInt(liveConfig, "factions.signature-food.rantotta-buff-seconds", 60), 0, true);
        } else if (PORKOLT.equals(signature)) {
            addTimedEffect(player, PotionEffectType.STRENGTH,
                    getInt(liveConfig, "factions.signature-food.porkolt-buff-seconds", 45), 0, true);
        } else if (VADLAKOMA.equals(signature)) {
            final int seconds = getInt(liveConfig,
                    "factions.signature-food.vadlakoma-buff-seconds", 45);
            addTimedEffect(player, PotionEffectType.SPEED, seconds, 0, true);
            addTimedEffect(player, PotionEffectType.FIRE_RESISTANCE, seconds, 0, true);
        } else if (LEPENY.equals(signature)) {
            final int seconds = getInt(liveConfig,
                    "factions.signature-food.lepeny-buff-seconds", 60);
            addTimedEffect(player, PotionEffectType.LUCK, seconds, 0, true);
            addTimedEffect(player, PotionEffectType.SPEED, seconds, 0, true);
        } else if (HAMULAKOMA.equals(signature)) {
            final int seconds = getInt(liveConfig,
                    "factions.signature-food.hamulakoma-buff-seconds", 60);
            addTimedEffect(player, PotionEffectType.ABSORPTION, seconds, 0, true);
            addTimedEffect(player, PotionEffectType.NIGHT_VISION, seconds, 0, true);
        } else if (HAMUKENYER.equals(signature)) {
            addTimedEffect(player, PotionEffectType.NIGHT_VISION,
                    getInt(liveConfig, "factions.signature-food.hamukenyer-buff-seconds", 60), 0, true);
        } else if (SUTI.equals(signature)) {
            addTimedEffect(player, PotionEffectType.SPEED,
                    getInt(liveConfig, "factions.signature-food.suti-speed-seconds", 30), 1, true);
            player.setVelocity(player.getVelocity().setY(Math.max(0.4D,
                    getDouble(liveConfig, "factions.signature-food.suti-launch-y", 0.6D))));
            player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8F, 1.2F);
            hu.taliann.icesmp.utils.ParticleUtil.spawn(player.getWorld(), org.bukkit.Particle.FIREWORK,
                    player.getLocation().add(0.0D, 1.0D, 0.0D),
                    30, 0.5D, 0.6D, 0.5D, 0.08D);
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

    private static int getInt(final FileConfiguration config, final String path,
                              final int fallback) {
        return config == null ? fallback : config.getInt(path, fallback);
    }

    private static double getDouble(final FileConfiguration config, final String path,
                                    final double fallback) {
        return config == null ? fallback : config.getDouble(path, fallback);
    }
}
