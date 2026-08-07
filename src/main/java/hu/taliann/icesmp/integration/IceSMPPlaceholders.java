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
    private final hu.taliann.icesmp.managers.ConfigManager configManager;

    private final hu.taliann.icesmp.managers.BestiaryManager bestiaryManager;
    private final hu.taliann.icesmp.managers.ProfessionRecipeCatalog recipeCatalog;
    private final hu.taliann.icesmp.managers.TerritoryManager territoryManager;

    private IceSMPPlaceholders(final HudManager hudManager,
                               final hu.taliann.icesmp.managers.ConfigManager configManager,
                               final hu.taliann.icesmp.managers.BestiaryManager bestiaryManager,
                               final hu.taliann.icesmp.managers.ProfessionRecipeCatalog recipeCatalog,
                               final hu.taliann.icesmp.managers.TerritoryManager territoryManager) {
        this.hudManager = hudManager;
        this.configManager = configManager;
        this.bestiaryManager = bestiaryManager;
        this.recipeCatalog = recipeCatalog;
        this.territoryManager = territoryManager;
    }

    /**
     * Registers the expansion with PlaceholderAPI. Invoked reflectively by the core so the core has no
     * compile-time dependency on PlaceholderAPI.
     *
     * @param plugin the owning plugin (unused, kept for a stable reflective signature)
     * @param hudManager the HUD manager providing the per-player snapshot
     */
    public static void register(final JavaPlugin plugin, final HudManager hudManager,
                                final hu.taliann.icesmp.managers.ConfigManager configManager,
                                final hu.taliann.icesmp.managers.BestiaryManager bestiaryManager,
                                final hu.taliann.icesmp.managers.ProfessionRecipeCatalog recipeCatalog,
                                final hu.taliann.icesmp.managers.TerritoryManager territoryManager) {
        new IceSMPPlaceholders(hudManager, configManager, bestiaryManager, recipeCatalog,
                territoryManager).register();
    }

    /** `%icesmp_bestiary_<kategória>%` és `_total` párja; nem-bestiárium paramra {@code null}. */
    private String bestiaryParam(final OfflinePlayer player, final String params) {
        if (!params.startsWith("bestiary_")) {
            return null;
        }
        final boolean total = params.endsWith("_total");
        final String raw = params.substring("bestiary_".length(),
                total ? params.length() - "_total".length() : params.length());
        final hu.taliann.icesmp.managers.BestiaryManager.Category category;
        try {
            category = hu.taliann.icesmp.managers.BestiaryManager.Category
                    .valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException unknown) {
            return "";
        }
        if (!total) {
            return String.valueOf(bestiaryManager.count(player.getUniqueId(), category));
        }
        return String.valueOf(switch (category) {
            case MOBS -> hu.taliann.icesmp.managers.BestiaryManager.knownMonsterTypes().size();
            case RECIPES -> recipeCatalog.allIds().size();
            case TERRITORIES -> territoryManager.all().size();
            case BOSSES -> hu.taliann.icesmp.managers.WorldBossManager.archetypeDisplayNames().size();
        });
    }

    private static hu.taliann.icesmp.data.FactionType parseFaction(final String factionId) {
        if (factionId == null || factionId.isBlank()) {
            return null;
        }
        try {
            return hu.taliann.icesmp.data.FactionType.valueOf(
                    factionId.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException unknown) {
            return null;
        }
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
        // A bestiárium-lekérés nem HUD-függő: offline/HUD nélküli játékosra is válaszol.
        final String bestiary = bestiaryParam(player, params.toLowerCase(Locale.ROOT));
        if (bestiary != null) {
            return bestiary;
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
            case "faction_color" -> FactionDisplayPalette.legacyCode(
                    hu.taliann.icesmp.managers.TablistManager.factionColor(
                            configManager, parseFaction(snapshot.factionId())));
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
