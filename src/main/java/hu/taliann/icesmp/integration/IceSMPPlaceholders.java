package hu.taliann.icesmp.integration;

import hu.taliann.icesmp.factions.FactionDisplayPalette;
import hu.taliann.icesmp.managers.HudManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * PlaceholderAPI bridge — exposes IceSMP player data as {@code %icesmp_<id>%} placeholders so an
 * external scoreboard/tab plugin (e.g. TAB) can display them (instead of IceSMP drawing its own
 * sidebar). This is a soft dependency: the class is only loaded/registered when PlaceholderAPI is
 * present (see {@code IceSMPCore.registerPlaceholders}, which calls {@link #register} reflectively),
 * so the plugin runs fine without PlaceholderAPI on the build- or runtime classpath.
 *
 * <p>Folia: placeholder requests can arrive on any thread (TAB refreshes asynchronously), so this
 * reads ONLY {@link HudManager.HudSnapshot} — an immutable snapshot refreshed on each player's own
 * region thread — never the live player or its PDC off-thread.
 *
 * <p>Available placeholders: {@code faction}, {@code faction_id}, {@code class} / {@code class_name},
 * {@code class_level} / {@code level}, {@code balance}, {@code resource}, {@code resource_max},
 * {@code resource_percent}, {@code resource_name}, {@code resource_bar}.
 */
public final class IceSMPPlaceholders extends PlaceholderExpansion {

    private final HudManager hudManager;

    private IceSMPPlaceholders(final HudManager hudManager) {
        this.hudManager = hudManager;
    }

    /**
     * Registers the expansion with PlaceholderAPI. Invoked reflectively by the core so the core has no
     * compile-time dependency on PlaceholderAPI.
     *
     * @param plugin the owning plugin (unused, kept for a stable reflective signature)
     * @param hudManager the HUD manager providing the per-player snapshot
     */
    public static void register(final JavaPlugin plugin, final HudManager hudManager) {
        new IceSMPPlaceholders(hudManager).register();
    }

    @Override
    public String getIdentifier() {
        return "icesmp";
    }

    @Override
    public String getAuthor() {
        return "Taliann";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(final OfflinePlayer player, final String params) {
        if (player == null || params == null) {
            return "";
        }
        final HudManager.HudSnapshot snapshot = hudManager.snapshot(player.getUniqueId());
        if (snapshot == null) {
            return "";
        }
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "faction" -> snapshot.faction();
            case "faction_id" -> snapshot.factionId();
            case "class", "class_name" -> snapshot.className();
            case "class_level", "level" -> String.valueOf(snapshot.classLevel());
            case "balance" -> snapshot.balance();
            case "resource" -> snapshot.hasClass() ? String.valueOf(snapshot.resource()) : "";
            case "resource_max" -> String.valueOf(snapshot.resourceMax());
            case "resource_percent" -> snapshot.hasClass() ? String.valueOf(snapshot.resourcePercent()) : "";
            case "resource_name" -> snapshot.resourceName();
            case "resource_bar" -> snapshot.hasClass() ? snapshot.resourceBar() : "";
            // Aktív világesemények egy sorban (max 2 név + "+N"), §-színekkel.
            case "event" -> snapshot.event();
            // A név frakciószíne a közös palettából; külső TAB/scoreboard is ugyanazt kapja.
            case "faction_color" -> FactionDisplayPalette.legacyPlayerName(snapshot.factionId());
            // Party frames for scoreboard plugins (TAB): the member count and one
            // plain line per member ("👑 Name ▮▮▮░░ 6❤"); blank outside a party.
            case "party_size" -> String.valueOf(snapshot.partyLines().size());
            case "party_1", "party_2", "party_3", "party_4", "party_5" -> {
                final int index = params.charAt(params.length() - 1) - '1';
                yield index < snapshot.partyLines().size() ? snapshot.partyLines().get(index) : "";
            }
            default -> null;
        };
    }
}
