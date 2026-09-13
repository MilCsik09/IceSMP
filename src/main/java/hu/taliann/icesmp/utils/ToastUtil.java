package hu.taliann.icesmp.utils;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vanília advancement-toast felugratása a jobb felső sarokban (quest-teljesítés).
 *
 * <p><b>Hogyan:</b> a jar datapackje egy REJTETT, {@code show_toast:true} advancementet
 * szállít ({@code icesmp:toast_quest}); a toast megjelenítése
 * = a bejegyzés odaítélése, majd azonnali visszavonása, hogy legközelebb újra felugorhasson.
 * A rejtett bejegyzés nem szennyezi a haladás-fület.
 *
 * <p><b>Miért nem dinamikus a cím:</b> korábban minden toast SAJÁT, véletlen kulcsú
 * advancementet töltött be futásidőben a {@code @Deprecated Bukkit.getUnsafe()} úton, hogy a
 * cím a quest neve lehessen. Ennek két baja volt: (1) nem támogatott API-ra épült, (2) a
 * Bukkit a betöltött bejegyzéseket a világ {@code datapacks/bukkit/} packjébe írja, tehát a
 * véletlen kulcsok ott halmozódhattak. A konkrét megnevezés amúgy is ott van, ahol a játékos
 * olvassa: a chat-üzenetben — a toast mindig csak dísz volt.
 *
 * <p><b>Folia:</b> az odaítélés/visszavonás a JÁTÉKOS advancement-progresszét írja, ezért a
 * játékos saját régió-szálán fut. Ha a datapack nem töltött be (nincs ilyen advancement), a
 * toast elmarad és egyszeri WARNING jelzi — a chat-üzenet a fő visszajelzés.
 */
public final class ToastUtil {

    /** Toast-fajták: a datapack fix bejegyzéseihez képeznek le. */
    public enum Kind {
        QUEST("toast_quest");

        private final String advancementId;

        Kind(final String advancementId) {
            this.advancementId = advancementId;
        }
    }

    private static final AtomicBoolean warned = new AtomicBoolean(false);

    private ToastUtil() {
    }

    /**
     * Toast felugratása a játékosnak.
     *
     * @param plugin a plugin
     * @param player a címzett
     * @param kind melyik fix toast-bejegyzés (a cím ebből jön, a datapackből)
     */
    public static void show(final JavaPlugin plugin, final Player player, final Kind kind) {
        if (player == null || kind == null) {
            return;
        }
        final Advancement advancement = Bukkit.getAdvancement(new NamespacedKey("icesmp", kind.advancementId));
        if (advancement == null) {
            warnOnce(plugin);
            return;
        }
        player.getScheduler().run(plugin, awardTask -> {
            final AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (final String criterion : progress.getRemainingCriteria()) {
                progress.awardCriteria(criterion);
            }
            // A visszavonás UTÁN ugorhat fel újra legközelebb; 1 mp-et hagyunk a kliensnek.
            player.getScheduler().runDelayed(plugin, revokeTask -> {
                final AdvancementProgress current = player.getAdvancementProgress(advancement);
                for (final String criterion : current.getAwardedCriteria()) {
                    current.revokeCriteria(criterion);
                }
            }, null, 20L);
        }, null);
    }

    private static void warnOnce(final JavaPlugin plugin) {
        if (warned.compareAndSet(false, true)) {
            plugin.getLogger().warning("A toast-advancementek nincsenek betöltve (icesmp datapack) — "
                    + "a quest-toast kimarad, a chat-üzenet marad. Ellenőrizd a datapack-felderítést a logban.");
        }
    }
}
