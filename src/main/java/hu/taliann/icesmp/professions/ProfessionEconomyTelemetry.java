package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.itemization.ArmorFamily;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Bounded aggregate counters for admin/economy diagnostics; no unbounded per-operation history. */
public final class ProfessionEconomyTelemetry {
    private static final ProfessionEconomyTelemetry GLOBAL = new ProfessionEconomyTelemetry();

    private final LongAdder crafted = new LongAdder();
    private final LongAdder processed = new LongAdder();
    private final LongAdder masterworks = new LongAdder();
    private final LongAdder highTierCrafts = new LongAdder();
    private final LongAdder salvaged = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> byProfession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ArmorFamily, LongAdder> salvageByFamily = new ConcurrentHashMap<>();

    public static ProfessionEconomyTelemetry global() { return GLOBAL; }

    public void recordCraft(final ProfessionRecipeCatalog.Recipe recipe, final int batches,
                            final int masterworkCount, final boolean highTier) {
        final int count = Math.max(0, batches);
        crafted.add(count);
        if ("processing".equalsIgnoreCase(recipe.kind())) processed.add(count);
        masterworks.add(Math.max(0, masterworkCount));
        if (highTier) highTierCrafts.add(count);
        byProfession.computeIfAbsent(recipe.profession().getId(), ignored -> new LongAdder()).add(count);
    }

    public void recordSalvage(final ArmorFamily family) {
        salvaged.increment();
        if (family != null) salvageByFamily.computeIfAbsent(family, ignored -> new LongAdder()).increment();
    }

    public Map<String, Long> snapshot() {
        final LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        result.put("crafted", crafted.sum());
        result.put("processed", processed.sum());
        result.put("masterworks", masterworks.sum());
        result.put("high_tier_crafts", highTierCrafts.sum());
        result.put("salvaged", salvaged.sum());
        byProfession.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put("profession." + entry.getKey(), entry.getValue().sum()));
        salvageByFamily.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put("salvage." + entry.getKey().id(), entry.getValue().sum()));
        return Map.copyOf(result);
    }
}
