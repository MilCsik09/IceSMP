package hu.taliann.icesmp.utils;

import hu.taliann.icesmp.managers.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Config-vezérelt szöveg-animációk az IceSMP natív tablist/HUD felületeihez.
 * Egy animáció = frame-lista + intervallum ({@code tablist.animations.<név>});
 * az aktuális frame-et a RENDSZERIDŐ választja ki, így nincs szükség külön schedulerre —
 * az animáció "magától halad", bármelyik renderelő tick olvassa (Folia-safe: csak
 * konkurens cache-t és időt olvas).
 *
 * <p>Az intervallum alsó korlátja {@link #MIN_INTERVAL_MILLIS}: az 50 ms-os marquee
 * packet-flood nem gazdaságos — a defaultok eleve ritkított frame-ekkel
 * portolódtak.
 */
public final class TextAnimator {

    private static final long MIN_INTERVAL_MILLIS = 250L;

    private record Animation(long intervalMillis, List<String> frames) {
    }

    private final ConfigManager configManager;
    /** Frame-listák cache-e — a ConfigManager-olvasás YAML-bejárás, tickenként túl drága lenne. */
    private final Map<String, Animation> cache = new ConcurrentHashMap<>();
    private volatile long cacheBuiltAt;

    public TextAnimator(final ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Az animáció aktuális frame-je (legacy &/&#RRGGBB kódokkal, ahogy a config tartalmazza),
     * vagy "" ha nincs ilyen animáció. Reload-barát: a cache 10 másodpercenként újraépül,
     * így az /icesmp reload után az új frame-ek maximum ennyi késéssel élesednek.
     */
    public String frame(final String name) {
        final long now = System.currentTimeMillis();
        if (now - cacheBuiltAt > 10_000L) {
            cacheBuiltAt = now;
            cache.clear();
        }
        final Animation animation = cache.computeIfAbsent(name, this::load);
        if (animation.frames().isEmpty()) {
            return "";
        }
        final int index = (int) ((now / animation.intervalMillis()) % animation.frames().size());
        return animation.frames().get(index);
    }

    private Animation load(final String name) {
        final ConfigurationSection section = configManager.getConfiguration() == null ? null
                : configManager.getConfiguration().getConfigurationSection("tablist.animations." + name);
        if (section == null) {
            return new Animation(MIN_INTERVAL_MILLIS, List.of());
        }
        final long interval = Math.max(MIN_INTERVAL_MILLIS, section.getLong("interval-millis", 1000L));
        return new Animation(interval, List.copyOf(section.getStringList("frames")));
    }

    // ==================== közös legacy-szöveg segédek ====================

    /**
     * Legacy '&' + '&#RRGGBB' kódok Component-té —
     * a header/footer, a tab-nevek és az oldalsáv-elválasztók közös renderelője.
     */
    public static net.kyori.adventure.text.Component legacy(final String text) {
        if (text == null || text.isEmpty()) {
            return net.kyori.adventure.text.Component.empty();
        }
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize(translateHex(text).replace('&', '§'));
    }

    /** '&#RRGGBB' → '§x§R§R§G§G§B§B' (a LegacyComponentSerializer ezt a formát érti). */
    public static String translateHex(final String text) {
        if (!text.contains("&#")) {
            return text;
        }
        final StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&' && i + 7 < text.length() && text.charAt(i + 1) == '#'
                    && isHex(text, i + 2)) {
                out.append("§x");
                for (int j = i + 2; j < i + 8; j++) {
                    out.append('§').append(text.charAt(j));
                }
                i += 7;
            } else {
                out.append(text.charAt(i));
            }
        }
        return out.toString();
    }

    private static boolean isHex(final String text, final int from) {
        for (int i = from; i < from + 6; i++) {
            final char c = text.charAt(i);
            if (Character.digit(c, 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
