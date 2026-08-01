package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence-independent faction tax-debt state.
 *
 * <p>A debt belongs to the faction that assessed it, not to the player's current faction. This
 * keeps both its currency and its destination treasury stable across faction switches. The
 * manager serializes this model while holding its store lock; this class intentionally performs
 * no Bukkit, scheduler or file-system work and is therefore directly regression-testable.
 */
public final class FactionTaxDebtLedger {

    /** One durable debt bucket, keyed by player and assessing faction. */
    public record Debt(UUID playerId, FactionType faction, double amount, int evasionStrikes) {
        public Debt {
            if (playerId == null || faction == null) {
                throw new IllegalArgumentException("Tax debt requires a player and origin faction");
            }
            validateState(amount, evasionStrikes);
        }
    }

    /** Legacy scalar debt whose original faction was absent from the old persistence schema. */
    public record UnresolvedLegacyDebt(UUID playerId, double amount, int evasionStrikes) {
        public UnresolvedLegacyDebt {
            if (playerId == null) {
                throw new IllegalArgumentException("Legacy tax debt requires a player");
            }
            validateState(amount, evasionStrikes);
        }
    }

    private static final class State {
        private double amount;
        private int evasionStrikes;

        private State(final double amount, final int evasionStrikes) {
            this.amount = amount;
            this.evasionStrikes = evasionStrikes;
        }
    }

    private final Map<UUID, EnumMap<FactionType, State>> debts = new HashMap<>();
    private final Map<UUID, State> unresolvedLegacyDebts = new HashMap<>();

    public void clear() {
        debts.clear();
        unresolvedLegacyDebts.clear();
    }

    /** Active citizenship is strongest evidence; durable history is the fail-closed fallback. */
    public static Optional<FactionType> resolveLegacyOrigin(
            final Optional<FactionType> activeFaction,
            final Optional<FactionType> durableLastFaction) {
        if (activeFaction == null || durableLastFaction == null) {
            throw new IllegalArgumentException("Legacy origin evidence cannot be null");
        }
        return activeFaction.isPresent() ? activeFaction : durableLastFaction;
    }

    public void put(final UUID playerId, final FactionType faction,
                    final double amount, final int evasionStrikes) {
        validateKey(playerId, faction);
        validateState(amount, evasionStrikes);
        if (amount == 0.0D && evasionStrikes == 0) {
            remove(playerId, faction);
            return;
        }
        debts.computeIfAbsent(playerId, ignored -> new EnumMap<>(FactionType.class))
                .put(faction, new State(amount, evasionStrikes));
    }

    public void putUnresolvedLegacy(final UUID playerId, final double amount,
                                    final int evasionStrikes) {
        if (playerId == null) {
            throw new IllegalArgumentException("Legacy tax debt requires a player");
        }
        validateState(amount, evasionStrikes);
        if (amount == 0.0D && evasionStrikes == 0) {
            unresolvedLegacyDebts.remove(playerId);
            return;
        }
        unresolvedLegacyDebts.put(playerId, new State(amount, evasionStrikes));
    }

    /**
     * Binds an origin-less legacy record only after the player has an explicit faction again.
     * New records never use this path; it exists solely because the old scalar YAML format did
     * not retain the assessing faction. A null faction deliberately leaves the debt untouched.
     */
    public boolean bindUnresolvedLegacy(final UUID playerId, final FactionType explicitFaction) {
        if (playerId == null || explicitFaction == null) {
            return false;
        }
        final State legacy = unresolvedLegacyDebts.remove(playerId);
        if (legacy == null) {
            return false;
        }
        final State existing = debts
                .computeIfAbsent(playerId, ignored -> new EnumMap<>(FactionType.class))
                .get(explicitFaction);
        if (existing == null) {
            debts.get(playerId).put(explicitFaction,
                    new State(legacy.amount, legacy.evasionStrikes));
        } else {
            existing.amount = saturatingAmountAdd(existing.amount, legacy.amount);
            existing.evasionStrikes = saturatingIntAdd(
                    existing.evasionStrikes, legacy.evasionStrikes);
        }
        return true;
    }

    public double getTotalArrears(final UUID playerId) {
        final EnumMap<FactionType, State> playerDebts = debts.get(playerId);
        double total = 0.0D;
        if (playerDebts != null) {
            for (final State state : playerDebts.values()) {
                total = saturatingAmountAdd(total, state.amount);
            }
        }
        final State unresolved = unresolvedLegacyDebts.get(playerId);
        return unresolved == null ? total : saturatingAmountAdd(total, unresolved.amount);
    }

    public double getArrears(final UUID playerId, final FactionType faction) {
        final State state = state(playerId, faction);
        return state == null ? 0.0D : state.amount;
    }

    public int getEvasionStrikes(final UUID playerId, final FactionType faction) {
        final State state = state(playerId, faction);
        return state == null ? 0 : state.evasionStrikes;
    }

    public List<Debt> debtsFor(final UUID playerId) {
        final EnumMap<FactionType, State> playerDebts = debts.get(playerId);
        if (playerDebts == null || playerDebts.isEmpty()) {
            return List.of();
        }
        final List<Debt> snapshot = new ArrayList<>(playerDebts.size());
        for (final Map.Entry<FactionType, State> entry : playerDebts.entrySet()) {
            snapshot.add(new Debt(playerId, entry.getKey(),
                    entry.getValue().amount, entry.getValue().evasionStrikes));
        }
        return List.copyOf(snapshot);
    }

    public List<Debt> snapshot() {
        final List<Debt> snapshot = new ArrayList<>();
        for (final UUID playerId : debts.keySet()) {
            snapshot.addAll(debtsFor(playerId));
        }
        return List.copyOf(snapshot);
    }

    public List<UnresolvedLegacyDebt> unresolvedLegacySnapshot() {
        final List<UnresolvedLegacyDebt> snapshot = new ArrayList<>(unresolvedLegacyDebts.size());
        for (final Map.Entry<UUID, State> entry : unresolvedLegacyDebts.entrySet()) {
            snapshot.add(new UnresolvedLegacyDebt(entry.getKey(),
                    entry.getValue().amount, entry.getValue().evasionStrikes));
        }
        return List.copyOf(snapshot);
    }

    public boolean setArrears(final UUID playerId, final FactionType faction,
                              final double amount) {
        validateKey(playerId, faction);
        validateState(amount, 0);
        final State existing = state(playerId, faction);
        final double previous = existing == null ? 0.0D : existing.amount;
        if (existing == null) {
            if (amount > 0.0D) {
                put(playerId, faction, amount, 0);
            }
        } else {
            existing.amount = amount;
            removeIfEmpty(playerId, faction, existing);
        }
        return Double.compare(previous, amount) != 0;
    }

    public int incrementEvasionStrikes(final UUID playerId, final FactionType faction,
                                       final int maximum) {
        validateKey(playerId, faction);
        if (maximum <= 0) {
            return getEvasionStrikes(playerId, faction);
        }
        final EnumMap<FactionType, State> playerDebts =
                debts.computeIfAbsent(playerId, ignored -> new EnumMap<>(FactionType.class));
        final State state = playerDebts.computeIfAbsent(faction, ignored -> new State(0.0D, 0));
        state.evasionStrikes = state.evasionStrikes >= maximum
                ? maximum : state.evasionStrikes + 1;
        return state.evasionStrikes;
    }

    public boolean clearEvasionStrikes(final UUID playerId, final FactionType faction) {
        final State state = state(playerId, faction);
        if (state == null || state.evasionStrikes == 0) {
            return false;
        }
        state.evasionStrikes = 0;
        removeIfEmpty(playerId, faction, state);
        return true;
    }

    private State state(final UUID playerId, final FactionType faction) {
        final EnumMap<FactionType, State> playerDebts = debts.get(playerId);
        return playerDebts == null || faction == null ? null : playerDebts.get(faction);
    }

    private void remove(final UUID playerId, final FactionType faction) {
        final EnumMap<FactionType, State> playerDebts = debts.get(playerId);
        if (playerDebts == null) {
            return;
        }
        playerDebts.remove(faction);
        if (playerDebts.isEmpty()) {
            debts.remove(playerId);
        }
    }

    private void removeIfEmpty(final UUID playerId, final FactionType faction,
                               final State state) {
        if (state.amount == 0.0D && state.evasionStrikes == 0) {
            remove(playerId, faction);
        }
    }

    private static void validateKey(final UUID playerId, final FactionType faction) {
        if (playerId == null || faction == null) {
            throw new IllegalArgumentException("Tax debt requires a player and origin faction");
        }
    }

    private static void validateState(final double amount, final int evasionStrikes) {
        if (!Double.isFinite(amount) || amount < 0.0D || evasionStrikes < 0) {
            throw new IllegalArgumentException("Tax debt values must be finite and non-negative");
        }
    }

    private static double saturatingAmountAdd(final double first, final double second) {
        final double sum = first + second;
        return Double.isFinite(sum) ? sum : Double.MAX_VALUE;
    }

    private static int saturatingIntAdd(final int first, final int second) {
        return first > Integer.MAX_VALUE - second ? Integer.MAX_VALUE : first + second;
    }
}
