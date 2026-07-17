package hu.taliann.icesmp.utils;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * IDEAS A70: vanília advancement-toast felugratása tetszőleges címmel (quest-teljesítés
 * ünneplése a jobb felső sarokban). Nincs first-class toast API — a bevett plugin-trükköt
 * használjuk: egyszer használatos, rejtett advancement betöltése futásidőben, odaítélés,
 * majd visszavonás és eltávolítás.
 *
 * <p><b>Folia-lépcsők:</b> az advancement-registry módosítása (load/remove) a GLOBAL region
 * scheduleren fut; az odaítélés/visszavonás (a játékos advancement-progressje) a játékos
 * SAJÁT régió-szálán. A teljes folyamat try/catch — ha a szerver-implementáció elutasítja,
 * a toast egyszerűen elmarad (a chat-üzenet a fő visszajelzés, ez csak dísz), és egyszeri
 * konzol-warn jelzi.
 *
 * <p>A rejtett advancement ({@code hidden:true, show_toast:true, announce_to_chat:false})
 * nem szennyezi az advancement-képernyőt, és ~1 mp után nyomtalanul eltűnik a registryből.
 */
public final class ToastUtil {

    private static final AtomicBoolean warned = new AtomicBoolean(false);

    private ToastUtil() {
    }

    /**
     * Toast felugratása a játékosnak.
     *
     * @param plugin a plugin
     * @param player a címzett
     * @param title a toast címe (szín-kódok nélkül — a hívó felelőssége a lecsupaszítás)
     * @param iconMaterialKey a toast ikonja minecraft-item kulcsként (pl. "minecraft:writable_book")
     */
    public static void show(final JavaPlugin plugin, final Player player, final String title,
                            final String iconMaterialKey) {
        final NamespacedKey key = new NamespacedKey(plugin,
                "toast_" + UUID.randomUUID().toString().substring(0, 8));
        final String json = "{\"criteria\":{\"trigger\":{\"trigger\":\"minecraft:impossible\"}},"
                + "\"display\":{\"icon\":{\"id\":\"" + iconMaterialKey + "\"},"
                + "\"title\":{\"text\":\"" + escapeJson(title) + "\"},"
                + "\"description\":{\"text\":\"\"},"
                + "\"frame\":\"goal\",\"announce_to_chat\":false,\"show_toast\":true,\"hidden\":true}}";

        Bukkit.getGlobalRegionScheduler().run(plugin, loadTask -> {
            final Advancement advancement;
            try {
                advancement = Bukkit.getUnsafe().loadAdvancement(key, json);
            } catch (final Throwable throwable) {
                warnOnce(plugin, throwable);
                return;
            }
            if (advancement == null) {
                return;
            }
            player.getScheduler().run(plugin, awardTask -> {
                try {
                    player.getAdvancementProgress(advancement).awardCriteria("trigger");
                } catch (final Throwable throwable) {
                    warnOnce(plugin, throwable);
                    cleanup(plugin, key);
                    return;
                }
                // 1 mp múlva visszavonás (a toast már kiment) + registry-takarítás.
                player.getScheduler().runDelayed(plugin, revokeTask -> {
                    try {
                        player.getAdvancementProgress(advancement).revokeCriteria("trigger");
                    } catch (final Throwable ignored) {
                        // A játékos időközben elmehetett — a takarítás akkor is fut.
                    }
                    cleanup(plugin, key);
                }, () -> cleanup(plugin, key), 20L);
            }, () -> cleanup(plugin, key));
        });
    }

    private static void cleanup(final JavaPlugin plugin, final NamespacedKey key) {
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                Bukkit.getUnsafe().removeAdvancement(key);
            } catch (final Throwable ignored) {
                // Már nincs a registryben — rendben.
            }
        });
    }

    private static void warnOnce(final JavaPlugin plugin, final Throwable throwable) {
        if (warned.compareAndSet(false, true)) {
            plugin.getLogger().warning("A quest-toast nem támogatott ezen a szerver-implementáción ("
                    + throwable.getClass().getSimpleName() + ") — a toastok kimaradnak, a chat-üzenet marad.");
        }
    }

    private static String escapeJson(final String text) {
        final StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
