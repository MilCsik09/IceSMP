package hu.taliann.icesmp.data;

import java.util.Locale;

/**
 * The kind of a faction territory zone, deciding who may build inside it and
 * whether players can lay a personal {@code /claim} there.
 *
 * <ul>
 *   <li>{@link #FACTION} — normal faction land: only members of the owning
 *       faction may build; players MAY claim their own plots here.</li>
 *   <li>{@link #PROTECTED_FACTION} — a faction's protected core (walls,
 *       monuments): NOBODY may build, and claiming is forbidden.</li>
 *   <li>{@link #PROTECTED_CITY} — a protected (usually neutral) city/landmark:
 *       NOBODY may build, and claiming is forbidden.</li>
 *   <li>{@link #CAPITAL} — the faction seat: NOBODY may build and claiming is
 *       forbidden; additionally it is the banking/exchange gate
 *       ({@code TerritoryManager.isInCapital}).</li>
 *   <li>{@link #DOOM_GATE} — the Kárhozat Kapuja PvPvE no-man's land (lore: the
 *       giant Nether portal of the Seventh Blood War): the arena itself is
 *       protected (no build, no explosions/fire damage) but PvP is LEGAL by
 *       default and kills here carry no sin; mobs spawn with bonus levels and
 *       entering grants a short PvP grace. Claiming is forbidden.</li>
 * </ul>
 *
 * <p>"Protected" zones are the map's shield: their build ban is always in force
 * (only the admin bypass permission overrides it), independent of the softer
 * faction-members-only rule that the {@code territory.protection.*} switches
 * govern.
 */
public enum TerritoryType {

    FACTION("Frakcióterület", false, true),
    PROTECTED_FACTION("Védett frakcióterület", true, false),
    PROTECTED_CITY("Védett város", true, false),
    CAPITAL("Főváros", true, false),
    DOOM_GATE("Kárhozat-zóna", true, false);

    private final String displayName;
    private final boolean protectedZone;
    private final boolean claimable;

    TerritoryType(final String displayName, final boolean protectedZone, final boolean claimable) {
        this.displayName = displayName;
        this.protectedZone = protectedZone;
        this.claimable = claimable;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Whether NO player may build here (only the admin bypass overrides it). */
    public boolean isProtectedZone() {
        return protectedZone;
    }

    /** Whether players may lay a personal {@code /claim} inside this zone. */
    public boolean isClaimable() {
        return claimable;
    }

    /**
     * Parses a type from a command argument. Accepts the enum name, a hyphenated
     * form ({@code protected-city}), or the underscore form; returns {@code null}
     * for unknown input.
     */
    public static TerritoryType fromInput(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (final TerritoryType type : values()) {
            if (type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        return switch (normalized) {
            case "city", "varos", "vedett_varos" -> PROTECTED_CITY;
            case "protected", "vedett", "vedett_frakcio", "vedett_frakcioterulet" -> PROTECTED_FACTION;
            case "capital", "fovaros" -> CAPITAL;
            case "frakcio", "frakcioterulet", "normal" -> FACTION;
            case "doom", "doomgate", "karhozat", "karhozat_kapuja", "hasadek" -> DOOM_GATE;
            default -> null;
        };
    }
}
