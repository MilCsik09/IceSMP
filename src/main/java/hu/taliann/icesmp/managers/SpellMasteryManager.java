package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileOperation;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.SpellbookSection;
import hu.taliann.icesmp.playerprofile.transaction.PlayerProfileTransactionManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

/** PlayerProfile-backed spell mastery with restart-durable wallet compensation. */
public final class SpellMasteryManager {

    public enum UpgradeResult { SUCCESS, MAX_RANK, INSUFFICIENT_FUNDS }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;

    public SpellMasteryManager(final JavaPlugin plugin, final ConfigManager configManager,
                               final CurrencyManager currencyManager, final FactionManager factionManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.currencyManager = Objects.requireNonNull(currencyManager, "currencyManager");
        this.factionManager = Objects.requireNonNull(factionManager, "factionManager");
    }

    public boolean isEnabled() {
        return configManager.getBoolean("spells.mastery.enabled", true);
    }

    public int getMaxRank() {
        return Math.max(0, configManager.getInt("spells.mastery.max-rank", 5));
    }

    public int getRank(final Player player, final String spellId) {
        if (player == null || spellId == null) {
            return 0;
        }
        return getRanks(player.getUniqueId()).getOrDefault(
                SpellMasteryTransactionProtocol.normalizeSpell(spellId), 0);
    }

    public double getCooldownMultiplier(final Player player, final String spellId) {
        if (!isEnabled()) {
            return 1.0D;
        }
        final double perRank = Math.max(0.0D,
                configManager.getDouble("spells.mastery.cooldown-reduction-per-rank", 0.08D));
        final double floor = Math.max(0.1D, Math.min(1.0D,
                configManager.getDouble("spells.mastery.min-cooldown-multiplier", 0.5D)));
        return Math.max(floor, 1.0D - (getRank(player, spellId) * perRank));
    }

    public double getPowerMultiplier(final Player player, final String spellId) {
        if (!isEnabled()) {
            return 1.0D;
        }
        final double perRank = Math.max(0.0D,
                configManager.getDouble("spells.mastery.power-per-rank", 0.05D));
        final double cap = Math.max(1.0D,
                configManager.getDouble("spells.mastery.max-power-multiplier", 1.5D));
        return Math.min(cap, 1.0D + (getRank(player, spellId) * perRank));
    }

    public long getUpgradeCost(final Player player, final String spellId) {
        final long base = Math.max(1L,
                configManager.getLong("spells.mastery.upgrade-base-cost", 50L));
        return Math.multiplyExact(base, getRank(player, spellId) + 1L);
    }

    public CompletionStage<UpgradeResult> upgrade(final Player player, final String spellId) {
        Objects.requireNonNull(player, "player");
        final String normalized = SpellMasteryTransactionProtocol.normalizeSpell(spellId);
        if (normalized.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("spellId cannot be blank"));
        }
        final UUID playerId = player.getUniqueId();
        final int previousRank = getRanks(playerId).getOrDefault(normalized, 0);
        if (previousRank >= getMaxRank()) {
            return CompletableFuture.completedFuture(UpgradeResult.MAX_RANK);
        }
        final int targetRank = Math.addExact(previousRank, 1);
        final FactionType faction = factionManager.getEconomyFaction(playerId);
        final CurrencyType currency = CurrencyType.fromFactionType(faction);
        final long base = Math.max(1L,
                configManager.getLong("spells.mastery.upgrade-base-cost", 50L));
        final long cost = Math.multiplyExact(base, targetRank);
        final SpellMasteryTransactionProtocol.Identity identity =
                SpellMasteryTransactionProtocol.create(playerId, normalized, previousRank,
                        targetRank, currency, cost, UUID.randomUUID());

        return async(() -> currencyManager.debitOperation(playerId, currency, cost,
                        identity.operationId()))
                .thenCompose(debit -> {
                    if (debit == null) {
                        return CompletableFuture.completedFuture(
                                new ProfileCommit(UpgradeResult.INSUFFICIENT_FUNDS, null, false));
                    }
                    return PlayerProfileAuthority.current().transact(playerId, current -> {
                        final SpellbookSection spellbook = current.spellbook().value();
                        final int durableRank = Math.toIntExact(
                                spellbook.mastery().getOrDefault(normalized, 0L));
                        if (durableRank != previousRank) {
                            throw new StaleMasteryUpgradeException(
                                    "mastery rank changed before durable commit");
                        }
                        if (targetRank > getMaxRank()) {
                            throw new StaleMasteryUpgradeException("mastery maximum changed");
                        }
                        final Map<String, Long> mastery = new LinkedHashMap<>(spellbook.mastery());
                        mastery.put(normalized, (long) targetRank);
                        final SpellbookSection next = new SpellbookSection(
                                spellbook.provenance(), spellbook.selectedSpell(), spellbook.favorites(),
                                mastery, spellbook.persistentCooldowns(), spellbook.uiState(),
                                spellbook.extensions());
                        return new PlayerProfileTransactionManager.TransactionPlan<>(
                                identity.operationId(), SpellMasteryTransactionProtocol.OPERATION_TYPE,
                                identity.fingerprint(),
                                List.of(new PlayerProfileTransactionManager.SectionUpdate(
                                        ProfileSectionId.SPELLBOOK,
                                        current.spellbook().revision(), next)),
                                UpgradeResult.SUCCESS);
                    }).handle((result, failure) -> new ProfileCommit(result, failure, true));
                })
                .thenCompose(profileCommit -> finalizeWallet(identity, profileCommit));
    }

    /** Reconciles wallet debits whose matching PlayerProfile transaction was interrupted. */
    public CompletionStage<Void> recoverPendingOperations() {
        return async(() -> currencyManager.durableOperationsByPrefix(
                        SpellMasteryTransactionProtocol.OPERATION_PREFIX))
                .thenCompose(operations -> {
                    CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
                    for (final CurrencyManager.DurableWalletOperation operation : operations) {
                        if (operation.status() != CurrencyManager.DurableWalletOperationStatus.DEBITED) {
                            continue;
                        }
                        chain = chain.thenCompose(ignored -> reconcile(operation));
                    }
                    return chain;
                });
    }

    public void runOnOwnerThread(final Player player, final Runnable action) {
        player.getScheduler().run(plugin, ignored -> action.run(), null);
    }

    private CompletionStage<UpgradeResult> finalizeWallet(
            final SpellMasteryTransactionProtocol.Identity identity,
            final ProfileCommit profileCommit) {
        if (!profileCommit.debited()) {
            return CompletableFuture.completedFuture(profileCommit.result());
        }
        if (profileCommit.failure() != null) {
            return async(() -> currencyManager.rollbackOperation(identity.operationId()))
                    .handle((rolledBack, rollbackFailure) -> {
                        final Throwable original = unwrap(profileCommit.failure());
                        if (rollbackFailure != null) {
                            original.addSuppressed(unwrap(rollbackFailure));
                        }
                        throw new java.util.concurrent.CompletionException(original);
                    });
        }
        return async(() -> currencyManager.commitOperation(identity.operationId()))
                .handle((ignored, finalizeFailure) -> {
                    if (finalizeFailure != null) {
                        plugin.getLogger().log(Level.SEVERE,
                                "Spell mastery wallet finalize deferred to restart recovery: "
                                        + identity.operationId(),
                                unwrap(finalizeFailure));
                    }
                    return profileCommit.result();
                });
    }

    private CompletionStage<Void> reconcile(final CurrencyManager.DurableWalletOperation wallet) {
        final UUID playerId = wallet.playerId();
        return PlayerProfileAuthority.current().repository().loadSnapshot(playerId)
                .thenCompose(profile -> {
                    final PlayerProfileOperation receipt = profile.operations().value().operations()
                            .get(wallet.operationId());
                    boolean committed = false;
                    if (receipt != null) {
                        try {
                            SpellMasteryTransactionProtocol.verifyCommittedReceipt(wallet, receipt);
                            committed = true;
                        } catch (final IllegalStateException invalidReceipt) {
                            plugin.getLogger().log(Level.WARNING,
                                    "Rejecting invalid spell mastery receipt during recovery: "
                                            + wallet.operationId(), invalidReceipt);
                        }
                    }
                    final boolean shouldCommit = committed;
                    return async(() -> {
                        if (shouldCommit) {
                            currencyManager.commitOperation(wallet.operationId());
                        } else {
                            currencyManager.rollbackOperation(wallet.operationId());
                        }
                        return null;
                    });
                });
    }

    private Map<String, Integer> getRanks(final UUID playerId) {
        final SpellbookSection spellbook = PlayerProfileAuthority.current().requireSection(
                playerId, ProfileSectionId.SPELLBOOK, SpellbookSection.class);
        final Map<String, Integer> ranks = new LinkedHashMap<>();
        spellbook.mastery().forEach((spell, rank) -> ranks.put(spell, Math.toIntExact(rank)));
        return Map.copyOf(ranks);
    }

    private <T> CompletionStage<T> async(final java.util.concurrent.Callable<T> work) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> {
            try {
                result.complete(work.call());
            } catch (final Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private static Throwable unwrap(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record ProfileCommit(UpgradeResult result, Throwable failure, boolean debited) {
    }

    private static final class StaleMasteryUpgradeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private StaleMasteryUpgradeException(final String message) {
            super(message);
        }
    }
}
