package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.ConfigManager;

import java.util.List;
import java.util.Locale;

/** Egyetlen content/progression authority a Prologue kapuihoz; DORMANT állapotban pass-through. */
public final class PrologueContentPolicy {
    private static final String ROOT = "world-events.prologue.";
    private static final List<String> DEFAULT_PROLOGUE_RARITIES =
            List.of("ocska", "kozonseges", "nem_mindennapi", "ritka");

    private PrologueContentPolicy() { }

    public static boolean enabled(final ConfigManager config) {
        return config != null && config.getBoolean(ROOT + "enabled", true);
    }

    public static boolean active(final ConfigManager config) {
        if (!enabled(config)) return false;
        final PrologueManager manager = PrologueManager.current();
        return manager != null && restrictsContent(manager.state());
    }

    static boolean restrictsContent(final PrologueState state) {
        return state != null && state != PrologueState.DORMANT && !state.completed();
    }

    public static boolean normalSeasonLifecycleAllowed(final ConfigManager config) {
        return !active(config);
    }

    public static int classLevelCap(final ConfigManager config) {
        return Math.max(1, Math.min(50, config.getInt(ROOT + "progression.class-level-cap", 25)));
    }

    public static int clampClassExperience(final ConfigManager config, final int requestedExperience,
                                           final int baseXp, final int increment) {
        if (!active(config)) return Math.max(0, requestedExperience);
        return PrologueProgression.clampExperienceToLevelCap(requestedExperience,
                classLevelCap(config), baseXp, increment);
    }

    public static boolean specializationAvailable(final ConfigManager config) {
        return !active(config) || config.getBoolean(ROOT + "progression.specializations-enabled", false);
    }

    public static boolean relicAcquisitionAvailable(final ConfigManager config) {
        return !active(config) || config.getBoolean(ROOT + "progression.relics-enabled", false);
    }

    public static boolean blueprintDropsAvailable(final ConfigManager config) {
        return !active(config) || config.getBoolean(ROOT + "progression.blueprints-enabled", false);
    }

    public static boolean rarityAvailable(final ConfigManager config, final String rarityId) {
        if (!active(config) || rarityId == null || rarityId.isBlank()) return true;
        List<String> allowed = config.getStringList(ROOT + "progression.allowed-rarities").stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).toList();
        if (allowed.isEmpty()) allowed = DEFAULT_PROLOGUE_RARITIES;
        return allowed.contains(rarityId.toLowerCase(Locale.ROOT));
    }

    public static boolean uniqueMaterialAvailable(final ConfigManager config, final String materialId) {
        if (!active(config) || materialId == null || materialId.isBlank()) return true;
        final List<String> blocked = config.getStringList(ROOT + "progression.blocked-unique-materials").stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).toList();
        return !blocked.contains(materialId.toLowerCase(Locale.ROOT));
    }

    public static boolean netherTraversalAvailable(final ConfigManager config) {
        if (!netherGateAuthorityActive(config)) return true;
        final PrologueManager manager = PrologueManager.current();
        return manager != null && manager.gateUnlocked();
    }

    public static boolean netherGateAuthorityActive(final ConfigManager config) {
        if (!enabled(config)) return false;
        final PrologueManager manager = PrologueManager.current();
        return manager != null && controlsNetherGate(manager.state());
    }

    static boolean controlsNetherGate(final PrologueState state) {
        return state != null && state != PrologueState.DORMANT;
    }

    public static double catchUpMultiplier(final ConfigManager config, final int classLevel,
                                           final String operationId) {
        final PrologueManager manager = PrologueManager.current();
        if (!enabled(config) || manager == null || !catchUpEnabledFor(manager.state())
                || !config.getBoolean(ROOT + "catch-up.enabled", true)) return 1.0D;
        final int target = Math.max(1, config.getInt(ROOT + "catch-up.target-level", 25));
        if (classLevel >= target || isAdministrativeXp(operationId)) return 1.0D;
        final double configured = config.getDouble(ROOT + "catch-up.multiplier", 1.75D);
        return Double.isFinite(configured) ? Math.max(1.0D, Math.min(4.0D, configured)) : 1.0D;
    }

    static boolean catchUpEnabledFor(final PrologueState state) {
        return state != null && state.completed();
    }

    private static boolean isAdministrativeXp(final String operationId) {
        if (operationId == null) return false;
        final String id = operationId.toLowerCase(Locale.ROOT);
        return id.startsWith("admin:") || id.startsWith("class-admin:")
                || id.startsWith("setxp:") || id.startsWith("debug:");
    }
}
