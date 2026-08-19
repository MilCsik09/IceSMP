package hu.taliann.icesmp.professions;

import hu.taliann.icesmp.itemization.ArmorFamily;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;

import java.util.LinkedHashMap;
import java.util.Locale;
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
    private final ConcurrentHashMap<String, LongAdder> materialConsumed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> materialProduced = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> materialFaucets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> materialSinks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> vendorInput = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> vendorOutput = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> marketMovement = new ConcurrentHashMap<>();

    public static ProfessionEconomyTelemetry global() { return GLOBAL; }

    public void recordCraft(final ProfessionRecipeCatalog.Recipe recipe, final int batches,
                            final int masterworkCount, final boolean highTier) {
        final int count = Math.max(0, batches);
        crafted.add(count);
        if ("processing".equalsIgnoreCase(recipe.kind())) processed.add(count);
        masterworks.add(Math.max(0, masterworkCount));
        if (highTier) highTierCrafts.add(count);
        byProfession.computeIfAbsent(recipe.profession().getId(), ignored -> new LongAdder()).add(count);
        if (count == 0) return;
        recipe.uniqueIngredients().forEach((materialId, amount) -> add(
                materialConsumed, materialId, (long) Math.max(0, amount) * count));
        if (recipe.uniqueResult() != null && !recipe.uniqueResult().isBlank()) {
            add(materialProduced, recipe.uniqueResult(),
                    (long) Math.max(0, recipe.resultAmount()) * count);
        }
    }

    public void recordSalvage(final ArmorFamily family) {
        salvaged.increment();
        if (family != null) salvageByFamily.computeIfAbsent(family, ignored -> new LongAdder()).increment();
    }

    /** Records a world/PvE/gathering faucet for one managed material without storing per-event history. */
    public void recordFaucet(final String sourceCategory, final String materialId, final int amount) {
        add(materialFaucets, normalizeCategory(sourceCategory) + '.' + normalizeId(materialId), amount);
    }

    /** Records a non-craft terminal sink such as an upgrade/service consumption. */
    public void recordSink(final String sinkCategory, final String materialId, final int amount) {
        add(materialSinks, normalizeCategory(sinkCategory) + '.' + normalizeId(materialId), amount);
    }

    /** Positive amount means the vendor removed material from players; output means NPC-created supply. */
    public void recordVendorFlow(final String materialId, final int amount, final boolean outputToPlayer) {
        add(outputToPlayer ? vendorOutput : vendorInput, normalizeId(materialId), amount);
    }

    /** Aggregate traded quantity; price history remains owned by the existing market authority. */
    public void recordMarketMovement(final String materialId, final int amount) {
        add(marketMovement, normalizeId(materialId), amount);
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
        append(result, "material.consumed.", materialConsumed);
        append(result, "material.produced.", materialProduced);
        append(result, "material.faucet.", materialFaucets);
        append(result, "material.sink.", materialSinks);
        append(result, "vendor.input.", vendorInput);
        append(result, "vendor.output.", vendorOutput);
        append(result, "market.movement.", marketMovement);
        return Map.copyOf(result);
    }

    private static void append(final Map<String, Long> target, final String prefix,
                               final Map<String, LongAdder> source) {
        source.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> target.put(prefix + entry.getKey(), entry.getValue().sum()));
    }

    private static void add(final ConcurrentHashMap<String, LongAdder> target,
                            final String key, final long amount) {
        if (amount <= 0L || key == null || key.isBlank()) return;
        target.computeIfAbsent(key, ignored -> new LongAdder()).add(amount);
    }

    private static String normalizeCategory(final String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String normalizeId(final String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
