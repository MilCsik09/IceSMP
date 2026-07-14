package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Áhítat Aurája" — 10 másodpercig tüske-aurát ad a kaszterre: amíg aktív, a rá
 * mért közelharci ütések sebzést vernek vissza a támadóra. Mivel a spell-osztály
 * listenert nem regisztrálhat, az állapotot egy statikus {@code shouldReflect}/
 * {@code reflectAmount} API-n keresztül teszi elérhetővé — a tényleges
 * visszaütést a {@code SpellStateListener}-be szánt handler végzi (lásd a
 * regisztrációs jelentést).
 */
public final class DevotionAuraSpell extends BaseSpell {

    private static final Map<UUID, Long> AURA_UNTIL = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;

    public DevotionAuraSpell(final JavaPlugin plugin, final MessageManager messageManager) {
        super(messageManager, "devotion_aura", "Áhítat Aurája", 60, SpellCostType.HUNGER, 4);
        this.plugin = plugin;
    }

    @Override
    public void execute(final Player player) {
        final int durationTicks = balanceInt("duration-ticks", 10 * 20);
        final UUID playerId = player.getUniqueId();
        AURA_UNTIL.put(playerId, System.currentTimeMillis() + (durationTicks * 50L));

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, 0, false, true, true));
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D),
                15, 0.4D, 0.6D, 0.4D, 0.02D);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.0F);
        player.sendMessage(resolveMessage("spell.devotion_aura.on",
                "<gold>Áhítat Aurája aktív: a rád mért ütések tüskét kapnak.</gold>"));

        // Folia: per-player region scheduler instead of the unsupported Bukkit scheduler;
        // the retired-callback still clears the map entry if the player is already gone.
        player.getScheduler().runDelayed(plugin, task -> AURA_UNTIL.remove(playerId),
                () -> AURA_UNTIL.remove(playerId), durationTicks);
    }

    /**
     * Rövid visszaverés-debounce: a tüske-sebzés maga is damage-event, így két aurás játékos
     * egymást ütve szinkron végtelen pingpongba kerülne — védőnként legfeljebb 2 visszaverés/mp.
     * Az entry az aura lejártával a runDelayed-takarítással együtt ürül.
     */
    private static final Map<UUID, Long> REFLECT_DEBOUNCE = new ConcurrentHashMap<>();
    private static final long REFLECT_DEBOUNCE_MILLIS = 500L;

    /** Igaz, ha a játékosnál jelenleg aktív az Áhítat Aurája (a SpellStateListener handlerének kell hívnia). */
    public static boolean shouldReflect(final Player player) {
        if (AURA_UNTIL.getOrDefault(player.getUniqueId(), 0L) <= System.currentTimeMillis()) {
            return false;
        }
        final long now = System.currentTimeMillis();
        final Long last = REFLECT_DEBOUNCE.get(player.getUniqueId());
        if (last != null && now - last < REFLECT_DEBOUNCE_MILLIS) {
            return false;
        }
        REFLECT_DEBOUNCE.put(player.getUniqueId(), now);
        return true;
    }

    /** A visszavert sebzés mértéke, spell-balance.devotion_aura.reflect-damage-dzsel hangolható. */
    public static double reflectAmount() {
        return balanceStatic("devotion_aura", "reflect-damage", 1.0D);
    }

    public static void cleanup(final UUID playerId) {
        if (playerId != null) {
            AURA_UNTIL.remove(playerId);
            REFLECT_DEBOUNCE.remove(playerId);
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        cleanup(playerId);
    }
}
