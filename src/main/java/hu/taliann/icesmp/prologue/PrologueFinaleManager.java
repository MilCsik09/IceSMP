package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.ChronicleManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.MajorEventGate;
import hu.taliann.icesmp.managers.SeasonMonumentManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot, checkpointed Prologue finale orchestrator; separate from SeasonFinaleManager. */
public final class PrologueFinaleManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PrologueManager state;
    private final PrologueFinaleRunState runState;
    private final PrologueParticipantTracker participants;
    private final PrologueEncounterEngine encounters;
    private final PrologueRewardService rewards;
    private final PrologueSeasonTransition seasonTransition;
    private final MajorEventGate eventGate;
    private final AtomicBoolean transitionInFlight = new AtomicBoolean(false);
    private volatile CompletableFuture<Void> rewardFuture;
    private volatile boolean rehearsal;
    private volatile PrologueFinalePhase rehearsalPhase = PrologueFinalePhase.IDLE;
    private volatile long rehearsalPhaseChangedAt;
    private volatile int rehearsalBaseline;
    private volatile boolean gatheringWarningSent;

    public PrologueFinaleManager(final JavaPlugin plugin, final ConfigManager config,
                                 final PrologueManager state, final PrologueFinaleRunState runState,
                                 final PrologueParticipantTracker participants,
                                 final PrologueEncounterEngine encounters,
                                 final PrologueRewardService rewards,
                                 final PrologueSeasonTransition seasonTransition,
                                 final MajorEventGate eventGate) {
        this.plugin = plugin;
        this.config = config;
        this.state = state;
        this.runState = runState;
        this.participants = participants;
        this.encounters = encounters;
        this.rewards = rewards;
        this.seasonTransition = seasonTransition;
        this.eventGate = eventGate;
        if (state.finalePhase().running()) participants.resumeDurable();
    }

    public boolean isActive() {
        return rehearsal || state.finalePhase().running()
                || state.finalePhase().irreversibleVictoryPath() && !state.state().completed();
    }

    public boolean ceasefireActive() {
        final PrologueFinalePhase phase = phase();
        return isActive() && phase.ordinal() >= PrologueFinalePhase.GATHERING.ordinal()
                && phase.ordinal() <= PrologueFinalePhase.EPILOGUE.ordinal();
    }

    public boolean isRehearsal() { return rehearsal; }
    public PrologueFinalePhase phase() { return rehearsal ? rehearsalPhase : state.finalePhase(); }

    public synchronized boolean start(final boolean rehearsalMode, final String actor) {
        if (isActive() || encounters.isActive()) return false;
        if (eventGate != null && !eventGate.mayStartNaturally("prologue")) return false;
        gatheringWarningSent = false;
        if (rehearsalMode) {
            rehearsal = true;
            rehearsalPhase = PrologueFinalePhase.PREPARING;
            rehearsalPhaseChangedAt = System.currentTimeMillis();
            rehearsalBaseline = 0;
            participants.begin(false);
            return true;
        }
        final UUID finaleId = state.beginFinale(actor);
        runState.begin(finaleId);
        participants.begin(true);
        return true;
    }

    public synchronized void pause(final String actor) {
        if (rehearsal) throw new IllegalStateException("A rehearsal pause helyett abortálható és újraindítható");
        state.pause(true, actor);
    }

    public synchronized void resume(final String actor) {
        if (rehearsal) return;
        state.pause(false, actor);
    }

    public synchronized void abort(final String actor) {
        encounters.abortActive("A Prologue finálét admin megszakította.");
        participants.stop();
        rewardFuture = null;
        transitionInFlight.set(false);
        if (rehearsal) {
            rehearsal = false;
            rehearsalPhase = PrologueFinalePhase.IDLE;
            rehearsalBaseline = 0;
            return;
        }
        final UUID id = state.finaleId();
        state.abort(actor);
        runState.clear(id);
    }

    public void tick() {
        if (!isActive()) return;
        if (!rehearsal && state.paused()) return;
        switch (phase()) {
            case PREPARING -> preparing();
            case GATHERING -> gathering();
            case BREACH_1 -> wave(PrologueFinalePhase.BREACH_2, BreachSeverity.MINOR, false);
            case BREACH_2 -> wave(PrologueFinalePhase.ELITE_WAVE, BreachSeverity.MAJOR, false);
            case ELITE_WAVE -> wave(PrologueFinalePhase.BOSS_INTRO, BreachSeverity.CRITICAL, true);
            case BOSS_INTRO -> bossIntro();
            case BOSS_FIGHT -> bossFight();
            case FALSE_END -> falseEnd();
            case GATE_AWAKENING -> gateAwakening();
            case EPILOGUE -> epilogue();
            default -> { }
        }
    }

    private void preparing() {
        Bukkit.broadcast(Component.text("☠ KÁRHOZAT ÉJSZAKÁJA", NamedTextColor.DARK_RED)
                .append(Component.text(" — Olethropyla ismét megmozdult. Induljatok a Kapuhoz!",
                        NamedTextColor.GOLD)));
        advance(PrologueFinalePhase.GATHERING, "finale:gathering");
    }

    private void gathering() {
        final long window = Math.max(10L, config.getLong(
                "world-events.prologue.finale.gathering-seconds", 90L)) * 1_000L;
        if (phaseAgeMillis() < window) return;
        final int current = participants.currentParticipantCount();
        final int minimum = Math.max(1, config.getInt(
                "world-events.prologue.finale.minimum-participants", 5));
        if (current < minimum) {
            if (!gatheringWarningSent) {
                gatheringWarningSent = true;
                Bukkit.broadcast(Component.text("A Kapu előtt még nincs elég harcos a roham megkezdéséhez.",
                        NamedTextColor.YELLOW));
            }
            return;
        }
        if (rehearsal) rehearsalBaseline = current;
        else if (runState.baselineFor(state.finaleId()) <= 0) runState.setBaseline(state.finaleId(), current);
        advance(PrologueFinalePhase.BREACH_1, "finale:breach-1");
    }

    private void wave(final PrologueFinalePhase next, final BreachSeverity severity, final boolean elite) {
        if (encounters.isActive()) return;
        final int baseline = scalingBaseline();
        final boolean started = encounters.startWave("finale-" + phase().name().toLowerCase(), severity,
                baseline, elite,
                () -> advance(next, "finale:" + next.name().toLowerCase()), this::encounterFailure);
        if (!started) encounterFailure("A finálé hulláma nem indítható el.");
    }

    private void bossIntro() {
        final long delay = Math.max(2L, config.getLong(
                "world-events.prologue.finale.boss-intro-seconds", 6L)) * 1_000L;
        if (phaseAgeMillis() < delay) return;
        Bukkit.broadcast(Component.text("A hasadékban valami felel a hívásra…", NamedTextColor.DARK_PURPLE));
        advance(PrologueFinalePhase.BOSS_FIGHT, "finale:boss-fight");
    }

    private void bossFight() {
        if (!rehearsal && state.bossDefeated()) {
            advance(PrologueFinalePhase.FALSE_END, "recovery:boss-defeated");
            return;
        }
        if (encounters.isActive() || encounters.bossAlive()) return;
        final boolean started = encounters.startBoss(scalingBaseline(), this::bossVictory, this::encounterFailure);
        if (!started) encounterFailure("A Hasadék Őre nem indítható el.");
    }

    private void bossVictory() {
        if (rehearsal) {
            advance(PrologueFinalePhase.FALSE_END, "rehearsal:boss-victory");
            return;
        }
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                state.recordBossVictory("boss-death");
            } catch (final RuntimeException failure) {
                plugin.getLogger().severe("Prologue boss victory commit failed: " + failure);
            }
        });
    }

    private void falseEnd() {
        final long silence = Math.max(1L, config.getLong(
                "world-events.prologue.finale.false-end-seconds", 7L)) * 1_000L;
        if (phaseAgeMillis() < silence || !transitionInFlight.compareAndSet(false, true)) return;
        if (rehearsal) {
            visualAwakening();
            transitionInFlight.set(false);
            advance(PrologueFinalePhase.GATE_AWAKENING, "rehearsal:gate-awakening");
            return;
        }
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                state.unlockGateAfterVictory("finale-victory");
                Bukkit.getGlobalRegionScheduler().run(plugin, global -> visualAwakening());
            } catch (final RuntimeException failure) {
                plugin.getLogger().severe("Gate activation commit failed: " + failure);
            } finally {
                transitionInFlight.set(false);
            }
        });
    }

    private void visualAwakening() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("OLETHROPYLA", NamedTextColor.DARK_PURPLE),
                    Component.text("A Kárhozat Kapuja megnyílt.", NamedTextColor.GOLD))), null);
        }
        Bukkit.broadcast(Component.text("Olethropyla stabil átjáróvá vált.", NamedTextColor.LIGHT_PURPLE));
    }

    private void gateAwakening() {
        if (rehearsal) {
            advance(PrologueFinalePhase.EPILOGUE, "rehearsal:epilogue");
            return;
        }
        if (!transitionInFlight.compareAndSet(false, true)) return;
        try {
            if (!state.rewardPlanCreated()) state.markRewardPlanCreated("finale-reward-plan");
            if (!state.rewardsCommitted()) {
                final CompletableFuture<Void> existing = rewardFuture;
                if (existing == null) {
                    rewardFuture = rewards.commitFinaleParticipants(state.finaleParticipants()).toCompletableFuture();
                    rewardFuture.whenComplete((ignored, failure) -> Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                        if (failure == null) {
                            try { state.markRewardsCommitted("profile-v2-rewards"); }
                            catch (final RuntimeException commitFailure) {
                                plugin.getLogger().severe("Prologue reward completion commit failed: " + commitFailure);
                            }
                        } else {
                            plugin.getLogger().severe("Prologue Profile v2 reward delivery failed: " + failure);
                        }
                        rewardFuture = null;
                    }));
                }
                return;
            }
            final ChronicleManager chronicle = ChronicleManager.current();
            if (!state.chronicleCommitted()) {
                if (chronicle == null || !chronicle.publishExtraordinaryOnce("prologue-gate-open",
                        List.of("&5&l— Rendkívüli Krónika — Olethropyla —",
                                "&7A Kárhozat Éjszakáján az Első Expedíció kitartott a Senkiföldjén.",
                                "&dOlethropyla megnyílt. A Kapun túli út immár járható.",
                                "&8A Kapu eredete és az ősi csend titkai továbbra is megfejtetlenek."))) {
                    throw new IllegalStateException("Chronicle commit unavailable");
                }
                state.markChronicleCommitted("chronicle:prologue-gate-open");
            }
            final SeasonMonumentManager monument = SeasonMonumentManager.current();
            if (!state.monumentCommitted()) {
                if (monument == null || !monument.recordPrologueOnce("prologue-first-expedition",
                        state.finaleParticipants().size(), System.currentTimeMillis())) {
                    throw new IllegalStateException("Prologue monument commit unavailable");
                }
                state.markMonumentCommitted("monument:first-expedition");
            }
            advance(PrologueFinalePhase.EPILOGUE, "finale:epilogue");
        } catch (final RuntimeException failure) {
            plugin.getLogger().severe("Prologue legacy transaction blocked: " + failure);
        } finally {
            transitionInFlight.set(false);
        }
    }

    private void epilogue() {
        final long delay = Math.max(0L, config.getLong(
                "world-events.prologue.finale.epilogue-seconds", 6L)) * 1_000L;
        if (phaseAgeMillis() < delay || !transitionInFlight.compareAndSet(false, true)) return;
        if (rehearsal) {
            participants.stop();
            encounters.abortActive("Rehearsal lezárása");
            rehearsal = false;
            rehearsalPhase = PrologueFinalePhase.IDLE;
            rehearsalBaseline = 0;
            transitionInFlight.set(false);
            Bukkit.broadcast(Component.text("A Prologue rehearsal lezárult; tartós állapot nem változott.",
                    NamedTextColor.GRAY));
            return;
        }
        try {
            if (!state.seasonOneStarted()) {
                seasonTransition.prepareSeasonOne(state.finalePhaseChangedAt());
                state.markSeasonOneStarted("season-one-prepared");
            }
            final UUID id = state.finaleId();
            state.complete("season-one-transition");
            seasonTransition.activateSeasonOne();
            runState.clear(id);
            participants.stop();
            Bukkit.broadcast(Component.text("A Prologue véget ért. Megkezdődött az első szezon.",
                    NamedTextColor.GOLD));
        } catch (final RuntimeException failure) {
            plugin.getLogger().severe("Season 1 transition blocked; Prologue remains fail-closed: " + failure);
        } finally {
            transitionInFlight.set(false);
        }
    }

    private void encounterFailure(final String reason) {
        plugin.getLogger().warning("Prologue finale encounter failure: " + reason);
        if (rehearsal) {
            abort("rehearsal-failure");
            return;
        }
        try { state.pause(true, "encounter-failure"); }
        catch (final RuntimeException failure) {
            plugin.getLogger().severe("Prologue failure pause could not be persisted: " + failure);
        }
        Bukkit.broadcast(Component.text("A Kárhozat Éjszakája átmenetileg megállt. Az adminok biztonságosan folytathatják.",
                NamedTextColor.RED));
    }

    private int scalingBaseline() {
        final int configuredMin = Math.max(1, config.getInt(
                "world-events.prologue.scaling.minimum-players", 5));
        final int baseline = rehearsal ? rehearsalBaseline : runState.baselineFor(state.finaleId());
        return Math.max(configuredMin, baseline);
    }

    private long phaseAgeMillis() {
        final long changedAt = rehearsal ? rehearsalPhaseChangedAt : state.finalePhaseChangedAt();
        return Math.max(0L, System.currentTimeMillis() - changedAt);
    }

    private void advance(final PrologueFinalePhase next, final String actor) {
        if (rehearsal) {
            rehearsalPhase = next;
            rehearsalPhaseChangedAt = System.currentTimeMillis();
        } else if (state.finalePhase() != next) {
            state.checkpoint(next, actor);
        }
    }

    public void shutdown() {
        participants.stop();
        encounters.shutdown();
        rewardFuture = null;
        rehearsal = false;
    }
}
