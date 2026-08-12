package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;

import java.util.Comparator;
import java.util.Map;

public final class ConfigStagedBatchValidator {
    private ConfigStagedBatchValidator() { }

    public static String validate(final ConfigEditSession.Snapshot snapshot,
                                  final ConfigManager configManager) {
        if (snapshot == null || configManager == null) {
            return "A staged konfigurációs pillanatkép nem érhető el.";
        }
        for (final Map.Entry<String, Object> change : snapshot.changes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).toList()) {
            final String key = change.getKey();
            final Object value = snapshot.resolvedValue(key);
            ConfigMenuGUI.Entry scalar = ConfigMenuGUI.findEntry(key);
            if (scalar == null) scalar = BlockRegenConfigMenuGUI.findEntry(key);
            if (scalar == null) scalar = TransactionalOperationalConfigMenuGUI.findEntry(key);
            if (scalar != null) {
                final String scalarProblem = validateScalar(scalar, value);
                if (scalarProblem != null) return key + ": " + scalarProblem;
                final String problem = OperationalConfigPolicy.validate(
                        key, value, configManager, snapshot);
                if (problem != null) return key + ": " + problem;
                continue;
            }
            AdvancedConfigEntry advanced = ServerWorldConfigMenuGUI.findEntry(key);
            if (advanced == null) {
                advanced = TransactionalCrateConfigMenuGUI.findEntry(key, configManager);
            }
            if (advanced != null) {
                final String problem = AdvancedConfigPolicy.validate(
                        advanced, value, configManager, snapshot);
                if (problem != null) return key + ": " + problem;
                continue;
            }
            if (key.startsWith("crates.") && key.endsWith(".rewards")
                    && CrateRewardEditor.rewards(value).isEmpty()) {
                return key + ": A crate staged rewardlistája nem lehet üres vagy olvashatatlan.";
            }
        }
        return null;
    }

    private static String validateScalar(final ConfigMenuGUI.Entry entry, final Object value) {
        return switch (entry.type()) {
            case TOGGLE -> value instanceof Boolean
                    ? null : "A kapcsoló csak boolean értéket fogad.";
            case NUMBER -> finiteInRange(entry, value, false);
            case INTEGER -> finiteInRange(entry, value, true);
            case CYCLE -> value instanceof String text && entry.options().contains(text)
                    ? null : "Az érték nincs az engedélyezett opciók között.";
        };
    }

    private static String finiteInRange(final ConfigMenuGUI.Entry entry, final Object value,
                                        final boolean integerOnly) {
        if (!(value instanceof Number number)) return "Az értéknek számnak kell lennie.";
        final double numeric = number.doubleValue();
        if (!Double.isFinite(numeric)) return "Az értéknek véges számnak kell lennie.";
        if (integerOnly && numeric != Math.rint(numeric)) return "Az értéknek egész számnak kell lennie.";
        return numeric >= entry.min() && numeric <= entry.max()
                ? null : "Az érték kívül esik az engedélyezett tartományon.";
    }
}
