package hu.taliann.icesmp.itemization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ItemSetDefinition(String setId, String displayName,
                                Map<Integer, Map<String, Double>> tierStats) {

    /** Only stats with a concrete runtime consumer may appear in authored set tiers. */
    private static final java.util.Set<String> SET_STAT_CONSUMERS = java.util.Set.of(
            "ability_power", "max_health", "armor", "armor_toughness", "movement_speed");

    public ItemSetDefinition {
        setId = ItemStatCatalog.normalizeId(setId);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("item set display name is required");
        }
        displayName = displayName.trim();
        if (tierStats == null || tierStats.isEmpty() || tierStats.size() > 3) {
            throw new IllegalArgumentException("item set requires 1-3 bonus tiers");
        }
        final LinkedHashMap<Integer, Map<String, Double>> tiers = new LinkedHashMap<>();
        tierStats.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            final int pieces = entry.getKey() == null ? 0 : entry.getKey();
            if (pieces < 2 || pieces > 4) {
                throw new IllegalArgumentException("item set tier requires 2-4 pieces");
            }
            if (tiers.putIfAbsent(pieces, stats(entry.getValue())) != null) {
                throw new IllegalArgumentException("duplicate item set tier: " + pieces);
            }
        });
        tierStats = Collections.unmodifiableMap(tiers);
    }

    public Map<String, Double> activeStats(final int equippedPieces) {
        if (equippedPieces < 2) return Map.of();
        final LinkedHashMap<String, Double> active = new LinkedHashMap<>();
        tierStats.forEach((pieces, stats) -> {
            if (pieces <= equippedPieces) {
                stats.forEach((id, value) -> active.merge(id, value, Double::sum));
            }
        });
        return Collections.unmodifiableMap(active);
    }

    private static Map<String, Double> stats(final Map<String, Double> source) {
        if (source == null || source.isEmpty() || source.size() > 16) {
            throw new IllegalArgumentException("item set tier requires 1-16 stats");
        }
        final LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        source.forEach((raw, value) -> {
            final String id = ItemStatCatalog.require(raw).id();
            if (!SET_STAT_CONSUMERS.contains(id)) {
                throw new IllegalArgumentException("item set stat has no set consumer: " + id);
            }
            final double amount = value == null ? Double.NaN : value;
            if (!Double.isFinite(amount) || amount == 0.0D) {
                throw new IllegalArgumentException("invalid item set stat: " + raw);
            }
            if (result.putIfAbsent(id, amount) != null) {
                throw new IllegalArgumentException("duplicate item set stat: " + id);
            }
        });
        return Collections.unmodifiableMap(result);
    }
}