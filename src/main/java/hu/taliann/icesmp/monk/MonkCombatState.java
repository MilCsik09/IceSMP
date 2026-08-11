package hu.taliann.icesmp.monk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-player transient Szerzetes combat state.
 *
 * <p>Áramlás is the class layer: technique variety builds flow, repetition does not. Szélfutó
 * tracks one explicit martial chain, Sörfőző tracks one bounded deferred-damage pool and
 * Ködszövő tracks up to three linked allies by UUID/label. Durable class/spec/doctrine/mastery
 * state remains in PlayerProfile.</p>
 */
public final class MonkCombatState {

    private static final int RECENT_TECHNIQUES = 3;

    private final Deque<String> recentTechniques = new ArrayDeque<>(RECENT_TECHNIQUES);
    private int flow;
    private long flowLastGainAt;
    private long flowLastDecayAt;

    private int chainStep;
    private long chainLastStepAt;

    private double staggerPool;
    private double pendingDeferFraction;

    private final List<UUID> linkIds = new ArrayList<>(3);
    private final List<String> linkLabels = new ArrayList<>(3);

    // ===== Áramlás (class core) =====

    /**
     * Records a cast technique. A technique not among the recent distinct ones builds flow;
     * repeating a recent technique earns nothing. Returns the earned flow.
     */
    public synchronized int recordTechnique(final String spellId, final long now,
                                            final int varietyGain,
                                            final long decayDelayMillis,
                                            final double decayPerSecond) {
        decayFlow(now, decayDelayMillis, decayPerSecond);
        final String id = normalize(spellId);
        final boolean fresh = !recentTechniques.contains(id);
        if (recentTechniques.size() >= RECENT_TECHNIQUES) recentTechniques.pollFirst();
        recentTechniques.addLast(id);
        if (!fresh) return 0;
        final int gain = Math.max(0, varietyGain);
        flow = clampPercent(flow + gain);
        flowLastGainAt = now;
        flowLastDecayAt = now;
        return gain;
    }

    public synchronized int flow(final long now, final long decayDelayMillis,
                                 final double decayPerSecond) {
        decayFlow(now, decayDelayMillis, decayPerSecond);
        return flow;
    }

    // ===== Szélfutó: Harcművészeti Lánc =====

    /**
     * Advances the explicit chain: the expected next step advances, the first step restarts,
     * anything else resets. Returns the chain progress after the cast.
     */
    public synchronized int recordChainStep(final String spellId, final List<String> steps,
                                            final long now, final long windowMillis) {
        final String id = normalize(spellId);
        if (steps.isEmpty()) return 0;
        final boolean stale = chainStep > 0
                && now - chainLastStepAt > Math.max(1L, windowMillis);
        if (stale) chainStep = 0;
        if (chainStep < steps.size() && id.equals(normalize(steps.get(chainStep)))) {
            chainStep++;
        } else if (id.equals(normalize(steps.get(0)))) {
            chainStep = 1;
        } else {
            chainStep = 0;
        }
        chainLastStepAt = now;
        return chainStep;
    }

    public synchronized int chainStep(final long now, final long windowMillis) {
        if (chainStep > 0 && now - chainLastStepAt > Math.max(1L, windowMillis)) {
            chainStep = 0;
        }
        return chainStep;
    }

    /** Finisher consume at or above the threshold; retention supports the level-50 doctrine. */
    public synchronized boolean consumeChain(final int threshold, final int retainedSteps) {
        if (chainStep < Math.max(1, threshold)) return false;
        chainStep = Math.max(0, Math.min(chainStep, retainedSteps));
        return true;
    }

    // ===== Sörfőző: Stagger =====

    /**
     * How much of a hit's FINAL (already mitigated) damage may be deferred, given the room left
     * in the pool. Working in final-damage units is what keeps the Stagger damage-conserving:
     * the player must end up losing the same total health, only spread over time.
     */
    public static double acceptedDefer(final double finalDamage, final double deferPercent,
                                       final double poolRoom) {
        if (finalDamage <= 0.0D) return 0.0D;
        final double share = Math.max(0.0D, Math.min(MAX_DEFER_PERCENT, deferPercent)) / 100.0D;
        return Math.max(0.0D, Math.min(finalDamage * share, Math.max(0.0D, poolRoom)));
    }

    /**
     * The event's damage is scaled by (1 - fraction), and every modifier in the pipeline is
     * multiplicative, so the deferred amount can be recovered exactly from whatever final damage
     * the pipeline ends up with — even if another plugin adjusts the hit after us.
     */
    public static double bankedFromReducedFinal(final double reducedFinalDamage,
                                                final double fraction) {
        if (reducedFinalDamage <= 0.0D || fraction <= 0.0D) return 0.0D;
        final double bounded = Math.min(fraction, MAX_DEFER_PERCENT / 100.0D);
        return reducedFinalDamage * bounded / (1.0D - bounded);
    }

    /** The hard ceiling on how much of a hit may ever be deferred. */
    public static final double MAX_DEFER_PERCENT = 80.0D;

    /** Phase one records the fraction the event was scaled by; phase two banks the exact amount. */
    public synchronized void setPendingDeferFraction(final double fraction) {
        pendingDeferFraction = Math.max(0.0D, Math.min(MAX_DEFER_PERCENT / 100.0D, fraction));
    }

    public synchronized double takePendingDeferFraction() {
        final double pending = pendingDeferFraction;
        pendingDeferFraction = 0.0D;
        return pending;
    }

    /** Defers part of a hit into the pool, bounded by the cap. Returns the actually deferred amount. */
    public synchronized double stagger(final double amount, final double poolCap) {
        final double deferred = Math.max(0.0D,
                Math.min(amount, Math.max(0.0D, poolCap) - staggerPool));
        staggerPool += deferred;
        return deferred;
    }

    /** Takes the next drain tick out of the pool. */
    public synchronized double drainStagger(final double amount) {
        final double drained = Math.max(0.0D, Math.min(amount, staggerPool));
        staggerPool -= drained;
        return drained;
    }

    /** Purifies a fraction of the pool. Returns the cleared amount. */
    public synchronized double purifyStagger(final double percent) {
        final double cleared = staggerPool * Math.max(0.0D, Math.min(100.0D, percent)) / 100.0D;
        staggerPool -= cleared;
        return cleared;
    }

    /** The full pool, taken at once — the logout/spec-switch consequence path. */
    public synchronized double collapseStagger() {
        final double all = staggerPool;
        staggerPool = 0.0D;
        pendingDeferFraction = 0.0D;
        return all;
    }

    public synchronized double staggerPool() {
        return staggerPool;
    }

    // ===== Ködszövő: Ködszál =====

    /** Links an ally; at capacity the oldest link is replaced. Returns false if already linked. */
    public synchronized boolean addLink(final UUID allyId, final String label,
                                        final int maximumLinks) {
        Objects.requireNonNull(allyId, "allyId");
        if (linkIds.contains(allyId)) return false;
        final int cap = Math.max(1, maximumLinks);
        while (linkIds.size() >= cap) {
            linkIds.remove(0);
            linkLabels.remove(0);
        }
        linkIds.add(allyId);
        linkLabels.add(label == null ? "" : label.trim());
        return true;
    }

    public synchronized List<UUID> linkIds() {
        return List.copyOf(linkIds);
    }

    public synchronized List<String> linkLabels() {
        return List.copyOf(linkLabels);
    }

    public synchronized void removeLink(final UUID allyId) {
        final int index = linkIds.indexOf(allyId);
        if (index >= 0) {
            linkIds.remove(index);
            linkLabels.remove(index);
        }
    }

    /** Spec switch cleanup; the caller applies the collapsed Stagger before invoking this. */
    public synchronized void clearSpecializationState() {
        recentTechniques.clear();
        flow = 0;
        flowLastGainAt = 0L;
        flowLastDecayAt = 0L;
        chainStep = 0;
        chainLastStepAt = 0L;
        staggerPool = 0.0D;
        linkIds.clear();
        linkLabels.clear();
    }

    /** Death/logout/admin reset cleanup. */
    public synchronized void clearAll() {
        clearSpecializationState();
    }

    private void decayFlow(final long now, final long decayDelayMillis,
                           final double decayPerSecond) {
        if (flow <= 0 || flowLastGainAt <= 0L || decayPerSecond <= 0.0D) return;
        final long decayStartsAt = flowLastGainAt + Math.max(0L, decayDelayMillis);
        if (now <= decayStartsAt) return;
        final long from = Math.max(decayStartsAt, flowLastDecayAt);
        if (now <= from) return;
        final int decay = (int) Math.floor((now - from) / 1000.0D * decayPerSecond);
        if (decay > 0) {
            flow = clampPercent(flow - decay);
            flowLastDecayAt = now;
        }
    }

    private static int clampPercent(final int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String normalize(final String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
