package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileDailyBudgetStore;
import hu.taliann.icesmp.session.PlayerStateCleanup;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * G6 — consent-based honor duel. Durable weekly usage is owned by PlayerProfile;
 * invitations and active pairs are transient runtime state.
 */
public final class HonorDuelManager implements PlayerStateCleanup {

    private static final String WEEKLY_BUDGET = "honor_duel_weekly";
    private static final long WEEK_MILLIS = 7L * 86_400_000L;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final SinManager sinManager;
    private final FactionManager factionManager;
    private final SeasonManager seasonManager;
    private final hu.taliann.icesmp.utils.MessageManager messageManager;
    private final PlayerProfileDailyBudgetStore budgetStore =
            new PlayerProfileDailyBudgetStore();

    /** challenged -> challenger. */
    private final Map<UUID, UUID> pending = new ConcurrentHashMap<>();
    /** challenged -> invitation expiry. */
    private final Map<UUID, Long> pendingExpiry = new ConcurrentHashMap<>();
    /** participant -> opponent, in both directions. */
    private final Map<UUID, UUID> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> endsAt = new ConcurrentHashMap<>();
    /** challenged -> unique acceptance token; fences quit/retry/stale callbacks. */
    private final Map<UUID, UUID> acceptTokens = new ConcurrentHashMap<>();

    private volatile CombatTagManager combatTagManagerRef;

    public HonorDuelManager(final JavaPlugin plugin, final ConfigManager configManager,
                            final SinManager sinManager, final FactionManager factionManager,
                            final SeasonManager seasonManager,
                            final hu.taliann.icesmp.utils.MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.sinManager = sinManager;
        this.factionManager = factionManager;
        this.seasonManager = seasonManager;
    }

    public void setCombatTagManager(final CombatTagManager combatTagManager) {
        this.combatTagManagerRef = combatTagManager;
    }

    /** Challenge creation; returns an error key or null. */
    public String challenge(final Player challenger, final Player target) {
        if (!configManager.getBoolean("honor-duel.enabled", true)) {
            return "duel-disabled";
        }
        if (sinManager.getSinCount(challenger) <= 0) {
            return "duel-not-sinner";
        }
        if (challenger.getUniqueId().equals(target.getUniqueId())) {
            return "duel-self";
        }
        if (active.containsKey(challenger.getUniqueId())
                || active.containsKey(target.getUniqueId())
                || acceptTokens.containsKey(target.getUniqueId())) {
            return "duel-busy";
        }
        final long week = currentWeek();
        final long used;
        try {
            final PlayerProfileDailyBudgetStore.BudgetState state =
                    budgetStore.read(challenger.getUniqueId(), WEEKLY_BUDGET);
            used = state.day() == week ? state.spent() : 0L;
        } catch (final RuntimeException profileUnavailable) {
            return "duel-profile";
        }
        if (used >= weeklyLimit()) {
            return "duel-limit";
        }

        final long now = System.currentTimeMillis();
        final Long existing = pendingExpiry.get(target.getUniqueId());
        if (existing != null && existing > now) {
            return "duel-pending";
        }
        if (pending.containsValue(challenger.getUniqueId())) {
            return "duel-pending";
        }
        pending.put(target.getUniqueId(), challenger.getUniqueId());
        pendingExpiry.put(target.getUniqueId(), now
                + Math.max(10, configManager.getInt(
                        "honor-duel.challenge-expiry-seconds", 60)) * 1000L);
        return null;
    }

    /**
     * Claims and accepts an invitation. The active duel is published only after the
     * weekly PlayerProfile budget commits and the target owner scheduler revalidates the token.
     */
    public CompletionStage<String> accept(final Player target) {
        final UUID targetId = target.getUniqueId();
        final UUID challengerId = pending.get(targetId);
        final Long expiry = pendingExpiry.get(targetId);
        final Player challenger = challengerId == null ? null : Bukkit.getPlayer(challengerId);
        if (challenger == null || expiry == null || expiry < System.currentTimeMillis()) {
            pending.remove(targetId);
            pendingExpiry.remove(targetId);
            return CompletableFuture.completedFuture("duel-no-challenge");
        }
        if (active.containsKey(challengerId) || active.containsKey(targetId)) {
            return CompletableFuture.completedFuture("duel-busy");
        }

        final UUID token = UUID.randomUUID();
        if (acceptTokens.putIfAbsent(targetId, token) != null) {
            return CompletableFuture.completedFuture("duel-busy");
        }
        if (!pending.remove(targetId, challengerId)
                || !pendingExpiry.remove(targetId, expiry)) {
            acceptTokens.remove(targetId, token);
            return CompletableFuture.completedFuture("duel-no-challenge");
        }

        final long week = currentWeek();
        return budgetStore.reserve(challengerId, WEEKLY_BUDGET,
                        week, 1L, weeklyLimit())
                .thenCompose(reservation -> {
                    if (!reservation.allowed()) {
                        return CompletableFuture.completedFuture("duel-limit");
                    }
                    return activateOnOwner(target, challengerId, token, reservation);
                })
                .exceptionally(failure -> {
                    restorePending(targetId, challengerId, expiry);
                    plugin.getLogger().severe("Honor-duel PlayerProfile reservation failed for "
                            + challengerId + ": " + failure.getMessage());
                    return "duel-profile";
                })
                .whenComplete((ignored, failure) -> acceptTokens.remove(targetId, token));
    }

    private CompletionStage<String> activateOnOwner(
            final Player target,
            final UUID challengerId,
            final UUID token,
            final PlayerProfileDailyBudgetStore.Reservation reservation) {
        final CompletableFuture<String> result = new CompletableFuture<>();
        target.getScheduler().run(plugin, task -> {
            final UUID targetId = target.getUniqueId();
            final Player liveChallenger = Bukkit.getPlayer(challengerId);
            if (!token.equals(acceptTokens.get(targetId))
                    || liveChallenger == null
                    || active.containsKey(challengerId)
                    || active.containsKey(targetId)) {
                compensate(challengerId, reservation, result, "duel-busy");
                return;
            }
            final long end = System.currentTimeMillis()
                    + Math.max(30, configManager.getInt(
                            "honor-duel.window-seconds", 180)) * 1000L;
            active.put(challengerId, targetId);
            active.put(targetId, challengerId);
            endsAt.put(challengerId, end);
            endsAt.put(targetId, end);
            result.complete(null);
        }, () -> compensate(challengerId, reservation, result, "duel-busy"));
        return result;
    }

    private void compensate(final UUID challengerId,
                            final PlayerProfileDailyBudgetStore.Reservation reservation,
                            final CompletableFuture<String> result,
                            final String error) {
        budgetStore.rollback(challengerId, WEEKLY_BUDGET, reservation, 1L)
                .whenComplete((rolledBack, failure) -> {
                    if (failure != null || !Boolean.TRUE.equals(rolledBack)) {
                        plugin.getLogger().severe("Honor-duel weekly budget compensation failed for "
                                + challengerId + "; admin audit required.");
                    }
                    result.complete(error);
                });
    }

    private void restorePending(final UUID targetId, final UUID challengerId,
                                final long expiry) {
        if (expiry <= System.currentTimeMillis()
                || active.containsKey(targetId)
                || active.containsKey(challengerId)) {
            return;
        }
        pending.putIfAbsent(targetId, challengerId);
        pendingExpiry.putIfAbsent(targetId, expiry);
    }

    public boolean declined(final Player target) {
        acceptTokens.remove(target.getUniqueId());
        pendingExpiry.remove(target.getUniqueId());
        return pending.remove(target.getUniqueId()) != null;
    }

    public boolean isDuelPair(final UUID a, final UUID b) {
        final Long end = endsAt.get(a);
        if (end == null || System.currentTimeMillis() > end) {
            clearPair(a);
            return false;
        }
        return b.equals(active.get(a));
    }

    public boolean settleKill(final Player killer, final Player victim) {
        if (!isDuelPair(killer.getUniqueId(), victim.getUniqueId())) {
            return false;
        }
        clearPair(killer.getUniqueId());
        final CombatTagManager tags = this.combatTagManagerRef;
        if (tags != null && tags.isEnabled()
                && tags.pairFightSeconds(killer.getUniqueId(), victim.getUniqueId())
                < tags.minFightSeconds()) {
            killer.sendMessage(messageManager.getMessage(
                    "duel-too-quick",
                    "<gray>⚔ A párbaj véget ért, de valódi összecsapás nélkül se bűn-tisztulás, se liga-pont nem jár.</gray>"));
            return true;
        }
        if (sinManager.getSinCount(killer) > 0) {
            sinManager.reduceSin(killer, 1);
        }
        final FactionType killerFaction = factionManager.getChosenFaction(
                killer.getUniqueId()).orElse(null);
        final FactionType victimFaction = factionManager.getChosenFaction(
                victim.getUniqueId()).orElse(null);
        if (killerFaction != null && victimFaction != null
                && killerFaction != victimFaction) {
            seasonManager.addPoints(killerFaction,
                    Math.max(0, configManager.getInt(
                            "honor-duel.season-points", 2)), "duel");
        }
        return true;
    }

    private long currentWeek() {
        return System.currentTimeMillis() / WEEK_MILLIS;
    }

    private long weeklyLimit() {
        return Math.max(1L, configManager.getInt("honor-duel.weekly-limit", 2));
    }

    private void clearPair(final UUID any) {
        final UUID other = active.remove(any);
        endsAt.remove(any);
        if (other != null) {
            active.remove(other);
            endsAt.remove(other);
        }
    }

    @Override
    public void clearPlayerState(final UUID playerId) {
        acceptTokens.remove(playerId);
        pending.remove(playerId);
        pendingExpiry.remove(playerId);
        pending.entrySet().removeIf(entry -> {
            if (playerId.equals(entry.getValue())) {
                pendingExpiry.remove(entry.getKey());
                acceptTokens.remove(entry.getKey());
                return true;
            }
            return false;
        });
        clearPair(playerId);
    }
}
