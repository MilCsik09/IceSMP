package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.RaidManager;
import hu.taliann.icesmp.managers.SinManager;
import hu.taliann.icesmp.managers.StatsManager;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileBountyStore;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileEconomyStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Player murder, betrayal, sanctioned-kill and restart-safe bounty handling. */
public final class SinListener implements Listener {

    private volatile hu.taliann.icesmp.managers.HonorDuelManager honorDuelManager;
    private volatile hu.taliann.icesmp.managers.WarWindowManager warWindowManager;

    private final JavaPlugin plugin;
    private final SinManager sinManager;
    private final RaidManager raidManager;
    private final FactionManager factionManager;
    private final hu.taliann.icesmp.managers.TerritoryManager territoryManager;
    private final StatsManager statsManager;
    private final CurrencyManager currencyManager;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final PlayerProfileBountyStore bountyStore = new PlayerProfileBountyStore();
    private final PlayerProfileEconomyStore economyStore = new PlayerProfileEconomyStore();

    private record BountySettlement(boolean creditedNow, boolean completedNow) { }

    public SinListener(final JavaPlugin plugin, final SinManager sinManager,
                       final RaidManager raidManager,
                       final FactionManager factionManager,
                       final hu.taliann.icesmp.managers.TerritoryManager territoryManager,
                       final StatsManager statsManager,
                       final CurrencyManager currencyManager,
                       final ConfigManager configManager,
                       final MessageManager messageManager) {
        this.plugin = plugin;
        this.sinManager = sinManager;
        this.raidManager = raidManager;
        this.factionManager = factionManager;
        this.territoryManager = territoryManager;
        this.statsManager = statsManager;
        this.currencyManager = currencyManager;
        this.configManager = configManager;
        this.messageManager = messageManager;
    }

    public void setHonorDuelManager(
            final hu.taliann.icesmp.managers.HonorDuelManager honorDuelManager) {
        this.honorDuelManager = honorDuelManager;
    }

    public void setWarWindowManager(
            final hu.taliann.icesmp.managers.WarWindowManager warWindowManager) {
        this.warWindowManager = warWindowManager;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        recoverPendingBounty(event.getPlayer(), 0);
    }

    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent event) {
        final Player victim = event.getEntity();
        final Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        final FactionType killerFaction = factionManager.getChosenFaction(
                killer.getUniqueId()).orElse(null);
        final FactionType victimFaction = factionManager.getChosenFaction(
                victim.getUniqueId()).orElse(null);

        if (raidManager.isSanctionedKill(killer.getUniqueId(), victim.getUniqueId())) {
            final boolean scored = raidManager.recordKill(killerFaction, victim.getLocation());
            killer.getScheduler().run(plugin, task -> {
                statsManager.recordRaidKill(killer);
                killer.sendMessage(messageManager.getMessage(
                        scored ? "faction-raid-kill" : "faction-raid-kill-outside-zone",
                        scored ? "<gold>⚔ Raid-ölés jóváírva a(z) {faction} oldalán!</gold>"
                                : "<gray>⚔ Szentesített raid-ölés, de a raid-zónán kívül — nem ér pontot.</gray>",
                        Map.of("faction", killerFaction.getDisplayName())));
            }, null);
            return;
        }

        final hu.taliann.icesmp.managers.HonorDuelManager duelRef = honorDuelManager;
        if (duelRef != null && duelRef.isDuelPair(
                killer.getUniqueId(), victim.getUniqueId())) {
            killer.getScheduler().run(plugin, task -> {
                if (duelRef.settleKill(killer, victim)) {
                    killer.sendMessage(messageManager.getMessage(
                            "duel-honor-won",
                            "<gold>⚔ A becsület-párbaj a tiéd — egy bűnöd letörölve. <gray>A sértett fél elégtételt kapott.</gray></gold>"));
                }
            }, null);
            return;
        }

        final hu.taliann.icesmp.managers.WarWindowManager warRef = warWindowManager;
        if (warRef != null && warRef.isSanctionedWarKill(killerFaction, victimFaction)) {
            final java.util.UUID victimId = victim.getUniqueId();
            final long rewardWindow = warRef.rewardWindowToken();
            killer.getScheduler().run(plugin, task -> {
                final FactionType liveFaction = factionManager.getChosenFaction(
                        killer.getUniqueId()).orElse(null);
                final FactionType liveVictimFaction = factionManager.getChosenFaction(
                        victimId).orElse(null);
                if (killer.isOnline() && liveFaction == killerFaction
                        && liveVictimFaction == victimFaction
                        && warRef.isCurrentRewardWindow(rewardWindow)
                        && warRef.isSanctionedWarKill(liveFaction, liveVictimFaction)) {
                    warRef.handleWarKill(killer, victimId, liveFaction);
                }
            }, null);
            return;
        }

        if (tryReserveBounty(victim, killer)) {
            return;
        }

        if (configManager.getBoolean("territory.doom-gate.sin-exempt", true)) {
            final hu.taliann.icesmp.data.Territory zone =
                    territoryManager.getTerritoryAt(victim.getLocation());
            if (zone != null && zone.type()
                    == hu.taliann.icesmp.data.TerritoryType.DOOM_GATE) {
                killer.getScheduler().run(plugin, task -> killer.sendActionBar(
                        messageManager.getMessage(
                                "sinner.doom-gate-kill",
                                "<dark_red>☠ A Kárhozat Kapujánál nincs törvény — az ölés itt nem bűn.</dark_red>")), null);
                return;
            }
        }

        if (victimFaction == FactionType.DARK
                && configManager.getBoolean(
                        "factions.sins.dark-victim-exempt", true)) {
            killer.getScheduler().run(plugin, task -> killer.sendActionBar(
                    messageManager.getMessage(
                            "sinner.dark-victim-kill",
                            "<gray>☠ A Kitaszított a törvényen kívül áll — az ölése nem bűn.</gray>")), null);
            return;
        }

        final boolean betrayal = killerFaction != null
                && killerFaction == victimFaction
                && killerFaction != FactionType.NEUTRAL;
        final int weight;
        final String messageKey;
        final String messageDefault;
        if (betrayal) {
            if (!configManager.getBoolean("factions.sins.betrayal-counts", true)) {
                return;
            }
            weight = Math.max(1,
                    configManager.getInt("factions.sins.betrayal-weight", 2));
            messageKey = "sinner.betrayal-recorded";
            messageDefault = "<dark_purple>Bűnt követtél el: árulás — a saját frakciótársadat ölted meg. "
                    + "<gray>Bűneid: <white>{count}</white>/<white>{threshold}</white></gray></dark_purple>";
        } else {
            if (!configManager.getBoolean("factions.sins.murder-counts", true)) {
                return;
            }
            weight = 1;
            messageKey = "sinner.sin-recorded";
            messageDefault = "<dark_purple>Bűnt követtél el: gyilkosság. "
                    + "<gray>Bűneid: <white>{count}</white>/<white>{threshold}</white></gray></dark_purple>";
        }

        final int threshold = Math.max(0,
                configManager.getInt("factions.sins.exile-threshold", 4));
        killer.getScheduler().run(plugin, task -> {
            final int sinCount = sinManager.addSin(killer, weight);
            killer.sendMessage(messageManager.getMessage(
                    messageKey, messageDefault,
                    Map.of("count", String.valueOf(sinCount),
                            "threshold", String.valueOf(threshold))));
        }, null);
    }

    /** Returns true when the high-sin kill is handled as a justified bounty execution. */
    private boolean tryReserveBounty(final Player victim, final Player killer) {
        if (!configManager.getBoolean("factions.sins.bounty.enabled", true)) {
            return false;
        }
        final int victimSins = sinManager.getSinCount(victim);
        final int minimum = Math.max(1,
                configManager.getInt("factions.sins.bounty.min-sins", 3));
        if (!sinManager.isWanted(victim)) {
            return false;
        }

        final CurrencyType currency = resolveBountyCurrency();
        final double rewardPerSin = configManager.getDouble(
                "factions.sins.bounty.reward-per-sin", 25.0D);
        final double reward = Double.isFinite(rewardPerSin) && rewardPerSin > 0.0D
                ? victimSins * rewardPerSin : 0.0D;
        final long amountMilli;
        try {
            amountMilli = Double.isFinite(reward)
                    ? PlayerProfileEconomyStore.toMilli(Math.max(0.0D, reward)) : 0L;
        } catch (final RuntimeException invalidReward) {
            plugin.getLogger().severe("Invalid bounty reward; settlement continues without money: "
                    + invalidReward.getMessage());
            reserveBounty(victim, killer, currency, 0L, minimum);
            return true;
        }
        reserveBounty(victim, killer, currency, amountMilli, minimum);
        return true;
    }

    private void reserveBounty(final Player victim, final Player killer,
                               final CurrencyType currency, final long amountMilli,
                               final int minimum) {
        final boolean clearSins = configManager.getBoolean(
                "factions.sins.bounty.clear-sins-on-death", true);
        final long cooldownMillis = safeHoursToMillis(configManager.getLong(
                "factions.sins.bounty.per-victim-cooldown-hours", 12L));
        final String victimName = victim.getName();
        final String killerName = killer.getName();
        bountyStore.reserve(victim.getUniqueId(), killer.getUniqueId(), currency,
                        amountMilli, minimum, clearSins, cooldownMillis)
                .whenComplete((reservation, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().severe("Bounty reservation failed for "
                                + victim.getUniqueId() + ": " + failure.getMessage());
                        notifyPlayer(killer.getUniqueId(), "bounty-persistence-failed",
                                "<red>💰 A fejpénz tartós foglalása meghiúsult; kifizetés nem történt.</red>");
                        return;
                    }
                    if (reservation == null || reservation.isEmpty()) {
                        notifyPlayer(killer.getUniqueId(), "bounty-cooldown",
                                "<gray>💰 Erre a fejre nemrég már fizettek — a vérdíj elmarad, és a bűnlista is megmarad.</gray>");
                        return;
                    }
                    final PlayerProfileBountyStore.Reservation reserved =
                            reservation.orElseThrow();
                    settleBounty(reserved.pending(), reserved.created(),
                            killerName, victimName);
                });
    }

    private void settleBounty(
            final PlayerProfileBountyStore.PendingBounty pending,
            final boolean announce,
            final String hunterName,
            final String targetName) {
        final CompletionStage<Boolean> credit;
        if (pending.amountMilli() == 0L) {
            credit = CompletableFuture.completedFuture(false);
        } else {
            credit = economyStore.creditOnce(pending.hunterId(), pending.currency(),
                            pending.amountMilli(), "bounty-credit-" + pending.operationId())
                    .thenApply(PlayerProfileEconomyStore.CreditResult::applied);
        }
        credit.thenCompose(creditedNow -> bountyStore.complete(
                        pending.victimId(), pending)
                .thenApply(completedNow ->
                        new BountySettlement(creditedNow, completedNow)))
                .whenComplete((settlement, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().severe("Bounty outbox settlement failed for "
                                + pending.operationId() + ": " + failure.getMessage());
                        return;
                    }
                    if (settlement == null || !settlement.completedNow()) {
                        return;
                    }
                    final double amount = PlayerProfileEconomyStore.fromMilli(
                            pending.amountMilli());
                    notifyBountyHunter(pending, amount, !announce);
                    if (announce) {
                        Bukkit.getServer().broadcast(messageManager.getMessage(
                                "bounty-claimed",
                                "<gold>💰 {hunter} beváltotta a fejpénzt {target} fejére: {reward} {currency} a bankjába került!</gold>",
                                Map.of("hunter", hunterName,
                                        "target", targetName,
                                        "reward", currencyManager.formatBalance(amount),
                                        "currency", pending.currency().getDisplayName())));
                    }
                });
    }

    private void recoverPendingBounty(final Player victim, final int attempt) {
        final long delay = attempt == 0 ? 1L : 10L;
        victim.getScheduler().runDelayed(plugin, task -> {
            try {
                final Optional<PlayerProfileBountyStore.PendingBounty> pending =
                        bountyStore.pending(victim.getUniqueId());
                pending.ifPresent(value -> settleBounty(value, false,
                        "", victim.getName()));
            } catch (final PlayerProfileAuthority.ProfileNotReadyException notReady) {
                if (attempt < 12 && victim.isOnline()) {
                    recoverPendingBounty(victim, attempt + 1);
                }
            } catch (final RuntimeException corrupt) {
                plugin.getLogger().severe("Bounty recovery failed for "
                        + victim.getUniqueId() + ": " + corrupt.getMessage());
            }
        }, null, delay);
    }

    private void notifyBountyHunter(
            final PlayerProfileBountyStore.PendingBounty pending,
            final double amount,
            final boolean recovered) {
        final Player hunter = Bukkit.getPlayer(pending.hunterId());
        if (hunter == null) {
            return;
        }
        hunter.getScheduler().run(plugin, task -> hunter.sendMessage(
                messageManager.getMessage(
                        recovered ? "bounty-recovered" : "bounty-paid",
                        recovered
                                ? "<gold>💰 Egy félbemaradt fejpénz-kifizetést helyreállítottunk: <white>{reward} {currency}</white> a bankodba került.</gold>"
                                : "<gold>💰 Fejpénz jóváírva: <white>{reward} {currency}</white> a bankodban.</gold>",
                        Map.of("reward", currencyManager.formatBalance(amount),
                                "currency", pending.currency().getDisplayName()))), null);
    }

    private void notifyPlayer(final java.util.UUID playerId,
                              final String key, final String fallback) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.getScheduler().run(plugin,
                    task -> player.sendMessage(messageManager.getMessage(key, fallback)), null);
        }
    }

    private CurrencyType resolveBountyCurrency() {
        final CurrencyType configured = CurrencyType.fromInput(
                configManager.getString("factions.sins.bounty.currency", "NEUTRAL"));
        return configured == null
                ? CurrencyType.fromFactionType(FactionType.NEUTRAL) : configured;
    }

    private static long safeHoursToMillis(final long hours) {
        if (hours <= 0L) return 0L;
        return hours > Long.MAX_VALUE / 3_600_000L
                ? Long.MAX_VALUE : hours * 3_600_000L;
    }
}
