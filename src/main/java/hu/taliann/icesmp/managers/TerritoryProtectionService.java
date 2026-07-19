package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.data.Territory;
import hu.taliann.icesmp.data.TerritoryType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;

/**
 * Central rule resolver for territory-zone protection. Every zone type carries a
 * configurable rule set — {@code build}, {@code interact}, {@code pvp},
 * {@code explosions}, {@code fire} — read from
 * {@code territory.protection.rules.<típus>.<szabály>} with sensible baked-in
 * defaults (protected zones default-protect everything; normal faction land only
 * restricts building by non-members).
 *
 * <p>A rule that is {@code true} means "restricted/protected". For PROTECTED
 * zones a restricted action is denied to everyone; on normal FACTION land it is
 * denied only to players who are not members of the owning faction. Two bypass
 * permissions lift the player-action rules (build/interact): the full admin
 * bypass and the narrower builder bypass.
 *
 * <p>Threading (Folia): the zone lookup is a lock-free concurrent-map read and
 * all queries run on the calling event's region thread. The only cross-entity
 * touch is the PvP denial notice to the attacker, which is dispatched on the
 * attacker's own scheduler.
 */
public final class TerritoryProtectionService {

    /** Full admin bypass — overrides every player-action rule. */
    public static final String ADMIN_BYPASS = "icesmp.admin.territory.bypass";
    /** Builder bypass — may build/interact even in protected zones (not PvP). */
    public static final String BUILDER_BYPASS = hu.taliann.icesmp.core.Permissions.TERRITORY_BUILDER;

    public static final String BUILD = "build";
    public static final String INTERACT = "interact";
    public static final String PVP = "pvp";
    public static final String EXPLOSIONS = "explosions";
    public static final String FIRE = "fire";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TerritoryManager territoryManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;

    public TerritoryProtectionService(final JavaPlugin plugin, final ConfigManager configManager,
                                      final TerritoryManager territoryManager, final FactionManager factionManager,
                                      final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.territoryManager = territoryManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
    }

    // ==================== rule resolution ====================

    private static String typeKey(final TerritoryType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Baked-in default for a rule (true = protected). Faction land only guards building by default. */
    private static boolean defaultRule(final TerritoryType type, final String rule) {
        if (type == TerritoryType.DOOM_GATE) {
            // PvPvE no-man's land: fighting is LEGAL and interaction free by default;
            // the arena itself stays protected (build/explosions/fire).
            return !(PVP.equals(rule) || INTERACT.equals(rule));
        }
        if (type.isProtectedZone()) {
            return true;
        }
        // Normal faction land: only building is restricted (to non-members) by default.
        return BUILD.equals(rule);
    }

    /**
     * Whether the given rule is active for the zone type (config with kill-switch + default).
     * Two config schemas are read: the CLEAR {@code allow-<szabály>} form (true = SZABAD,
     * false = tiltva — this wins when set), and the legacy {@code <szabály>} form
     * (true = tiltva) as fallback so old configs keep working.
     */
    private boolean ruleEnabled(final TerritoryType type, final String rule) {
        if (type.isProtectedZone() && !configManager.getBoolean("territory.protection.protect-zones", true)) {
            return false;
        }
        final String base = "territory.protection.rules." + typeKey(type) + ".";
        if (configManager.getConfiguration().isSet(base + "allow-" + rule)) {
            return !configManager.getBoolean(base + "allow-" + rule, !defaultRule(type, rule));
        }
        return configManager.getBoolean(base + rule, defaultRule(type, rule));
    }

    // ==================== player actions (build / interact) ====================

    /**
     * Resolves whether a player's build/interact action at a location is denied,
     * returning the blocking zone (for the caller's message) or {@code null} when
     * allowed. Bypass permissions and faction membership are applied here.
     */
    private Territory blockingZone(final Player player, final Location location, final String rule) {
        final Territory zone = territoryManager.getTerritoryAt(location);
        if (zone == null || !ruleEnabled(zone.type(), rule)) {
            return null;
        }
        if (player.hasPermission(ADMIN_BYPASS) || player.hasPermission(BUILDER_BYPASS)) {
            return null;
        }
        if (zone.type().isProtectedZone()) {
            return zone; // protected: everyone denied
        }
        // Normal faction land: only non-members are denied.
        return factionManager.getFaction(player.getUniqueId()) == zone.faction() ? null : zone;
    }

    /** True (and warns) when the player may not build/break at the location. */
    public boolean denyBuild(final Player player, final Location location) {
        final Territory zone = blockingZone(player, location, BUILD);
        if (zone == null) {
            return false;
        }
        warn(player, zone, "territory-build-denied-protected",
                "<red>⛨ {name} — védett zóna, itt senki sem építhet.</red>",
                "territory-build-denied",
                "<red>Ez a(z) {faction} frakció területe — itt nem építhetsz.</red>");
        return true;
    }

    /** True (and warns) when the player may not interact (containers, doors…) at the location. */
    public boolean denyInteract(final Player player, final Location location) {
        final Territory zone = blockingZone(player, location, INTERACT);
        if (zone == null) {
            return false;
        }
        warn(player, zone, "territory-interact-denied-protected",
                "<red>⛨ {name} — védett zóna, itt nem interaktálhatsz.</red>",
                "territory-interact-denied",
                "<red>Ez a(z) {faction} frakció területe — itt nem interaktálhatsz.</red>");
        return true;
    }

    private void warn(final Player player, final Territory zone, final String protectedKey,
                      final String protectedDefault, final String factionKey, final String factionDefault) {
        if (zone.type().isProtectedZone()) {
            player.sendActionBar(messageManager.getMessage(protectedKey, protectedDefault,
                    Map.of("name", zone.name())));
        } else {
            player.sendActionBar(messageManager.getMessage(factionKey, factionDefault,
                    Map.of("faction", zone.faction().getDisplayName())));
        }
    }

    // ==================== PvP ====================

    /**
     * Entry-grace timestamps for the DOOM_GATE zone (spawn-kill protection): a
     * player crossing INTO the zone is PvP-immune for a few seconds, and loses
     * the grace early the moment they attack someone themselves. UUID-keyed
     * concurrent map, marked by TerritoryListener, cleared on quit/kick.
     */
    private final java.util.Map<java.util.UUID, Long> doomGraceUntil = new java.util.concurrent.ConcurrentHashMap<>();

    /** Marks a player's DOOM_GATE entry (starts the PvP grace window). */
    public void markDoomEntry(final java.util.UUID playerId) {
        final long seconds = Math.max(0L, configManager.getLong("territory.doom-gate.entry-grace-seconds", 8L));
        if (seconds > 0L && playerId != null) {
            doomGraceUntil.put(playerId, System.currentTimeMillis() + seconds * 1000L);
        }
    }

    /** Clears a player's doom-grace state (zone exit, quit/kick session cleanup). */
    public void clearDoomGrace(final java.util.UUID playerId) {
        if (playerId != null) {
            doomGraceUntil.remove(playerId);
        }
    }

    /** Whether the player is inside their DOOM_GATE entry-grace window. */
    private boolean hasDoomGrace(final java.util.UUID playerId) {
        final Long until = doomGraceUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            doomGraceUntil.remove(playerId);
            return false;
        }
        return true;
    }

    /**
     * Whether player-vs-player damage is denied at the victim's location. The
     * denial notice is sent to the attacker on the attacker's own scheduler
     * (Folia cross-entity touch). The admin bypass lets staff fight anywhere.
     * In the DOOM_GATE zone PvP is legal, but a freshly entered victim is
     * covered by a short entry grace — and an attacker forfeits their own
     * grace the moment they swing first.
     */
    public boolean denyPvp(final Player victim, final Player attacker) {
        final Territory zone = territoryManager.getTerritoryAt(victim.getLocation());
        if (zone != null && zone.type() == TerritoryType.DOOM_GATE) {
            // Attacking voids the attacker's own protection (no grace-abuse ganking).
            clearDoomGrace(attacker.getUniqueId());
            if (hasDoomGrace(victim.getUniqueId())) {
                attacker.getScheduler().run(plugin, task -> attacker.sendActionBar(messageManager.getMessage(
                        "territory-doom-grace",
                        "<gray>⚔ A belépő még a Kapu árnyékának védelme alatt áll — pár pillanat, és szabad a préda.</gray>")), null);
                return true;
            }
        }
        return denyCombat(victim.getLocation(), attacker, true);
    }

    /**
     * Whether damage to a victim at {@code victimLocation} is denied by the zone's
     * PvP rule. {@code attacker} may be {@code null} for unattributed sources
     * (e.g. a TNT with no player origin) — such damage is still blocked in a
     * safe zone, just without a notice. A non-null attacker with the admin bypass
     * is allowed to fight, and (when {@code notify}) is told why on their own
     * scheduler. Used for melee, projectiles, pets, TNT and harmful potions.
     */
    public boolean denyCombat(final Location victimLocation, final Player attacker, final boolean notify) {
        final Territory zone = territoryManager.getTerritoryAt(victimLocation);
        if (zone == null || !ruleEnabled(zone.type(), PVP)) {
            return false;
        }
        if (attacker != null && attacker.hasPermission(ADMIN_BYPASS)) {
            return false;
        }
        if (notify && attacker != null) {
            attacker.getScheduler().run(plugin, task -> attacker.sendActionBar(messageManager.getMessage(
                    "territory-pvp-denied", "<red>⛨ {name} — biztonságos zóna, itt tilos a PvP.</red>",
                    Map.of("name", zone.name()))), null);
        }
        return true;
    }

    // ==================== environment (explosions / fire / terrain) ====================

    /**
     * Whether unauthorised, ownerless terrain changes (mob griefing, liquid flow,
     * pistons) are forbidden here: true inside a PROTECTED zone whose build rule is
     * active. Normal faction land is left to ordinary survival mechanics.
     */
    public boolean isTerrainProtectedAt(final Location location) {
        final Territory zone = territoryManager.getTerritoryAt(location);
        return zone != null && zone.type().isProtectedZone() && ruleEnabled(zone.type(), BUILD);
    }

    /** Whether an explosion may not damage the block at this location. */
    public boolean isExplosionBlockedAt(final Location location) {
        final Territory zone = territoryManager.getTerritoryAt(location);
        return zone != null && ruleEnabled(zone.type(), EXPLOSIONS);
    }

    /** Whether fire (ignite/spread/burn) is forbidden at this location. */
    public boolean isFireBlockedAt(final Location location) {
        final Territory zone = territoryManager.getTerritoryAt(location);
        return zone != null && ruleEnabled(zone.type(), FIRE);
    }
}
