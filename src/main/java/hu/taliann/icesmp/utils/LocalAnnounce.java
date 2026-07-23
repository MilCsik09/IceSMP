package hu.taliann.icesmp.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Broadcast-diéta (gameplay-audit): a SZEMÉLYES léptékű események (régészet,
 * hullócsillag, állat-vándorlás…) nem szerver-broadcastot kapnak, hanem csak a
 * helyszín környékén állók értesülnek róluk — a globális chat a nagy eseményeké
 * marad. Folia: a távolság-ellenőrzés és az üzenetküldés MINDEN címzett SAJÁT
 * régió-szálán fut (más játékos pozícióját tilos idegen szálról olvasni).
 */
public final class LocalAnnounce {

    private LocalAnnounce() {
    }

    /**
     * Elküldi az üzenetet minden játékosnak a hely {@code radius} sugarú körében
     * (azonos világ). Bármely szálról hívható.
     *
     * @param plugin  a scheduler-hophoz
     * @param center  a hír helyszíne
     * @param radius  hatósugár blokkban (0 vagy negatív = senki)
     * @param message a kézbesítendő üzenet
     */
    public static void nearby(final JavaPlugin plugin, final Location center, final double radius,
                              final Component message) {
        if (center == null || center.getWorld() == null || radius <= 0.0D) {
            return;
        }
        final double radiusSquared = radius * radius;
        for (final Player online : List.copyOf(Bukkit.getOnlinePlayers())) {
            online.getScheduler().run(plugin, task -> {
                if (online.getWorld().equals(center.getWorld())
                        && online.getLocation().distanceSquared(center) <= radiusSquared) {
                    online.sendMessage(message);
                }
            }, null);
        }
    }
}
