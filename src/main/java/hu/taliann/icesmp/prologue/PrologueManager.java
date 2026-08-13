package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Olethropyla Season 0 tartós világállapotának egyetlen authorityje. */
public final class PrologueManager {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_AUDIT = 64;
    private static volatile PrologueManager active;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final File storageFile;
    private final Object lock = new Object();
    private final LinkedHashSet<UUID> finaleParticipants = new LinkedHashSet<>();
    private final ArrayDeque<String> audit = new ArrayDeque<>();

    private PrologueState state = PrologueState.DORMANT;
    private PrologueStage stage = PrologueStage.SILENCE;
    private PrologueFinalePhase finalePhase = PrologueFinalePhase.IDLE;
    private UUID finaleId;
    private int stability = PrologueStage.SILENCE.defaultStability();
    private long stateChangedAt;
    private long stageChangedAt;
    private long finalePhaseChangedAt;
    private boolean paused;
    private boolean bossDefeated;
    private boolean finaleVictory;
    private boolean gateUnlocked;
    private boolean rewardPlanCreated;
    private boolean rewardsCommitted;
    private boolean chronicleCommitted;
    private boolean monumentCommitted;
    private boolean seasonOneStarted;

    public PrologueManager(final JavaPlugin plugin, final ConfigManager configManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.configManager = Objects.requireNonNull(configManager);
        this.storageFile = new File(plugin.getDataFolder(), "prologue.yml");
        YamlStore.registerCriticalWrite(storageFile);
        plugin.getDataFolder().mkdirs();
        load();
        active = this;
    }

    public static PrologueManager current() { return active; }

    public void load() {
        synchronized (lock) {
            if (!storageFile.exists()) {
                final long now = System.currentTimeMillis();
                state = parseState(configManager.getString(
                        "world-events.prologue.initial-state", "UNSTABLE"));
                stage = parseStage(configManager.getString(
                        "world-events.prologue.initial-stage", "SILENCE"));
                stability = configuredStageStability(stage);
                stateChangedAt = now;
                stageChangedAt = now;
                finalePhaseChangedAt = now;
                writeStateLocked();
                return;
            }
            final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
            if (yaml.getInt("prologue.schema-version", -1) != SCHEMA_VERSION) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Ismeretlen Prologue schema-version");
                throw new IllegalStateException("Invalid Prologue schema-version");
            }
            try {
                state = PrologueState.valueOf(yaml.getString("prologue.state", ""));
                stage = PrologueStage.valueOf(yaml.getString("prologue.stage", ""));
                finalePhase = PrologueFinalePhase.valueOf(
                        yaml.getString("prologue.finale.phase", "IDLE"));
                final String id = yaml.getString("prologue.finale.id", "");
                finaleId = id.isBlank() ? null : UUID.fromString(id);
                stability = yaml.getInt("prologue.stability", -1);
                stateChangedAt = yaml.getLong("prologue.state-changed-at", -1L);
                stageChangedAt = yaml.getLong("prologue.stage-changed-at", -1L);
                finalePhaseChangedAt = yaml.getLong("prologue.finale.phase-changed-at", -1L);
                paused = yaml.getBoolean("prologue.finale.paused", false);
                bossDefeated = yaml.getBoolean("prologue.finale.boss-defeated", false);
                finaleVictory = yaml.getBoolean("prologue.finale.victory", false);
                gateUnlocked = yaml.getBoolean("prologue.gate-unlocked", false);
                rewardPlanCreated = yaml.getBoolean("prologue.rewards.plan-created", false);
                rewardsCommitted = yaml.getBoolean("prologue.rewards.committed", false);
                chronicleCommitted = yaml.getBoolean("prologue.legacy.chronicle-committed", false);
                monumentCommitted = yaml.getBoolean("prologue.legacy.monument-committed", false);
                seasonOneStarted = yaml.getBoolean("prologue.season-one-started", false);
                finaleParticipants.clear();
                for (final String raw : yaml.getStringList("prologue.finale.participants")) {
                    finaleParticipants.add(UUID.fromString(raw));
                }
                audit.clear();
                for (final String line : yaml.getStringList("prologue.audit")) {
                    if (!line.isBlank()) audit.addLast(line);
                }
                validateLoadedState();
            } catch (final RuntimeException invalid) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Érvénytelen Prologue állapot: " + invalid.getMessage());
                throw invalid;
            }
        }
    }

    public void save() {
        synchronized (lock) { writeStateLocked(); }
    }

    public PrologueState state() { synchronized (lock) { return state; } }
    public PrologueStage stage() { synchronized (lock) { return stage; } }
    public PrologueFinalePhase finalePhase() { synchronized (lock) { return finalePhase; } }
    public UUID finaleId() { synchronized (lock) { return finaleId; } }
    public int stability() { synchronized (lock) { return stability; } }
    public long stageChangedAt() { synchronized (lock) { return stageChangedAt; } }
    public long finalePhaseChangedAt() { synchronized (lock) { return finalePhaseChangedAt; } }
    public boolean paused() { synchronized (lock) { return paused; } }
    public boolean bossDefeated() { synchronized (lock) { return bossDefeated; } }
    public boolean finaleVictory() { synchronized (lock) { return finaleVictory; } }
    public boolean gateUnlocked() { synchronized (lock) { return gateUnlocked; } }
    public boolean rewardPlanCreated() { synchronized (lock) { return rewardPlanCreated; } }
    public boolean rewardsCommitted() { synchronized (lock) { return rewardsCommitted; } }
    public boolean chronicleCommitted() { synchronized (lock) { return chronicleCommitted; } }
    public boolean monumentCommitted() { synchronized (lock) { return monumentCommitted; } }
    public boolean seasonOneStarted() { synchronized (lock) { return seasonOneStarted; } }
    public Set<UUID> finaleParticipants() {
        synchronized (lock) { return Set.copyOf(finaleParticipants); }
    }

    public void setStage(final PrologueStage next, final String actor) {
        Objects.requireNonNull(next);
        mutate(actor, "stage=" + next, () -> {
            if (state == PrologueState.COMPLETED || state == PrologueState.GATE_OPEN
                    || state == PrologueState.FINALE) {
                throw new IllegalStateException("A Prologue aktuális állapotában a stage nem módosítható");
            }
            stage = next;
            stability = configuredStageStability(next);
            stageChangedAt = System.currentTimeMillis();
            state = next.ordinal() >= PrologueStage.LEAK.ordinal()
                    ? PrologueState.BREACHING : PrologueState.UNSTABLE;
            stateChangedAt = stageChangedAt;
        });
    }

    public void setStability(final int value, final String actor) {
        mutate(actor, "stability=" + value, () -> stability = Math.max(0, Math.min(100, value)));
    }

    public UUID beginFinale(final String actor) {
        synchronized (lock) {
            if (state == PrologueState.COMPLETED || state == PrologueState.GATE_OPEN) {
                throw new IllegalStateException("A Prologue finálé már lezárult");
            }
            if (finalePhase.running() || state == PrologueState.FINALE) {
                return finaleId;
            }
        }
        final UUID id = UUID.randomUUID();
        mutate(actor, "finale-start=" + id, () -> {
            final long now = System.currentTimeMillis();
            finaleId = id;
            state = PrologueState.FINALE;
            stateChangedAt = now;
            finalePhase = PrologueFinalePhase.PREPARING;
            finalePhaseChangedAt = now;
            paused = false;
            bossDefeated = false;
            finaleVictory = false;
            rewardPlanCreated = false;
            rewardsCommitted = false;
            chronicleCommitted = false;
            monumentCommitted = false;
            seasonOneStarted = false;
            finaleParticipants.clear();
        });
        return id;
    }

    public void checkpoint(final PrologueFinalePhase phase, final String actor) {
        Objects.requireNonNull(phase);
        mutate(actor, "finale-phase=" + phase, () -> {
            if (finaleId == null || state != PrologueState.FINALE && state != PrologueState.GATE_OPEN) {
                throw new IllegalStateException("Nincs aktív tartós Prologue finálé");
            }
            if (phase.ordinal() < finalePhase.ordinal()
                    && phase != PrologueFinalePhase.ABORTED) {
                throw new IllegalStateException("A finálé checkpoint nem léphet vissza");
            }
            finalePhase = phase;
            finalePhaseChangedAt = System.currentTimeMillis();
        });
    }

    public void recordParticipants(final Collection<UUID> participants, final String actor) {
        mutate(actor, "participants=" + (participants == null ? 0 : participants.size()), () -> {
            finaleParticipants.clear();
            if (participants != null) finaleParticipants.addAll(participants);
        });
    }

    public void pause(final boolean value, final String actor) {
        mutate(actor, value ? "finale-pause" : "finale-resume", () -> {
            if (!finalePhase.running()) throw new IllegalStateException("Nincs futó finálé");
            paused = value;
        });
    }

    public void abort(final String actor) {
        mutate(actor, "finale-abort", () -> {
            if (finaleVictory || gateUnlocked) {
                throw new IllegalStateException("Győzelem vagy Gate-unlock után a finálé nem abortálható");
            }
            finalePhase = PrologueFinalePhase.ABORTED;
            finalePhaseChangedAt = System.currentTimeMillis();
            paused = false;
            state = stage.ordinal() >= PrologueStage.LEAK.ordinal()
                    ? PrologueState.BREACHING : PrologueState.UNSTABLE;
            stateChangedAt = finalePhaseChangedAt;
            finaleId = null;
            finaleParticipants.clear();
        });
    }

    public void recordBossVictory(final String actor) {
        mutate(actor, "boss-victory", () -> {
            if (finaleId == null || finalePhase.ordinal() < PrologueFinalePhase.BOSS_FIGHT.ordinal()) {
                throw new IllegalStateException("Boss-győzelem csak az aktív fináléban rögzíthető");
            }
            bossDefeated = true;
            finaleVictory = true;
            finalePhase = PrologueFinalePhase.FALSE_END;
            finalePhaseChangedAt = System.currentTimeMillis();
        });
    }

    public void unlockGateAfterVictory(final String actor) {
        mutate(actor, "gate-unlocked", () -> {
            if (!finaleVictory || !bossDefeated) {
                throw new IllegalStateException("A Kapu csak tartós finálégyőzelem után nyitható");
            }
            gateUnlocked = true;
            state = PrologueState.GATE_OPEN;
            stateChangedAt = System.currentTimeMillis();
            if (finalePhase.ordinal() < PrologueFinalePhase.GATE_AWAKENING.ordinal()) {
                finalePhase = PrologueFinalePhase.GATE_AWAKENING;
                finalePhaseChangedAt = stateChangedAt;
            }
        });
    }

    public void forceGateOpen(final String actor) {
        mutate(actor, "gate-force-open", () -> {
            gateUnlocked = true;
            state = PrologueState.GATE_OPEN;
            stateChangedAt = System.currentTimeMillis();
        });
    }

    public void markRewardPlanCreated(final String actor) {
        mutate(actor, "reward-plan-created", () -> {
            if (!finaleVictory) throw new IllegalStateException("Nincs finálégyőzelem");
            rewardPlanCreated = true;
        });
    }

    public void markRewardsCommitted(final String actor) {
        mutate(actor, "rewards-committed", () -> {
            if (!rewardPlanCreated) throw new IllegalStateException("Nincs tartós reward plan");
            rewardsCommitted = true;
        });
    }

    public void markChronicleCommitted(final String actor) {
        mutate(actor, "chronicle-committed", () -> chronicleCommitted = true);
    }

    public void markMonumentCommitted(final String actor) {
        mutate(actor, "monument-committed", () -> monumentCommitted = true);
    }

    public void markSeasonOneStarted(final String actor) {
        mutate(actor, "season-one-started", () -> {
            if (!gateUnlocked || !rewardsCommitted) {
                throw new IllegalStateException("Season 1 csak Gate-unlock és reward commit után indulhat");
            }
            seasonOneStarted = true;
        });
    }

    public void complete(final String actor) {
        mutate(actor, "prologue-completed", () -> {
            if (!gateUnlocked || !rewardsCommitted || !seasonOneStarted) {
                throw new IllegalStateException("A Prologue transition még nem teljes");
            }
            state = PrologueState.COMPLETED;
            finalePhase = PrologueFinalePhase.COMPLETED;
            paused = false;
            final long now = System.currentTimeMillis();
            stateChangedAt = now;
            finalePhaseChangedAt = now;
        });
    }

    private void mutate(final String actor, final String action, final Runnable mutation) {
        synchronized (lock) {
            final MemoryState before = snapshotLocked();
            mutation.run();
            appendAuditLocked(actor, action);
            try {
                writeStateLocked();
            } catch (final RuntimeException | Error failure) {
                restoreLocked(before);
                throw failure;
            }
        }
    }

    private int configuredStageStability(final PrologueStage value) {
        return Math.max(0, Math.min(100, configManager.getInt(
                "world-events.prologue.stages." + value.name().toLowerCase(Locale.ROOT)
                        + ".stability", value.defaultStability())));
    }

    private void appendAuditLocked(final String actor, final String action) {
        final String principal = actor == null || actor.isBlank() ? "system" : actor.trim();
        audit.addLast(System.currentTimeMillis() + "|" + principal + "|" + action);
        while (audit.size() > MAX_AUDIT) audit.removeFirst();
    }

    private void validateLoadedState() {
        if (stability < 0 || stability > 100 || stateChangedAt <= 0L
                || stageChangedAt <= 0L || finalePhaseChangedAt <= 0L) {
            throw new IllegalArgumentException("érvénytelen időbélyeg vagy stabilitás");
        }
        if (bossDefeated && !finaleVictory) {
            throw new IllegalArgumentException("bossDefeated victory nélkül");
        }
        if (finaleVictory && finaleId == null) {
            throw new IllegalArgumentException("victory finale-id nélkül");
        }
        if (state == PrologueState.COMPLETED
                && (!gateUnlocked || !rewardsCommitted || !seasonOneStarted)) {
            throw new IllegalArgumentException("COMPLETED hiányos transitionnel");
        }
        if (gateUnlocked && state != PrologueState.GATE_OPEN && state != PrologueState.COMPLETED) {
            throw new IllegalArgumentException("nyitott Gate inkompatibilis state-ben");
        }
    }

    private void writeStateLocked() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("prologue.schema-version", SCHEMA_VERSION);
        yaml.set("prologue.state", state.name());
        yaml.set("prologue.stage", stage.name());
        yaml.set("prologue.stability", stability);
        yaml.set("prologue.state-changed-at", stateChangedAt);
        yaml.set("prologue.stage-changed-at", stageChangedAt);
        yaml.set("prologue.gate-unlocked", gateUnlocked);
        yaml.set("prologue.season-one-started", seasonOneStarted);
        yaml.set("prologue.finale.id", finaleId == null ? "" : finaleId.toString());
        yaml.set("prologue.finale.phase", finalePhase.name());
        yaml.set("prologue.finale.phase-changed-at", finalePhaseChangedAt);
        yaml.set("prologue.finale.paused", paused);
        yaml.set("prologue.finale.boss-defeated", bossDefeated);
        yaml.set("prologue.finale.victory", finaleVictory);
        yaml.set("prologue.finale.participants",
                finaleParticipants.stream().map(UUID::toString).toList());
        yaml.set("prologue.rewards.plan-created", rewardPlanCreated);
        yaml.set("prologue.rewards.committed", rewardsCommitted);
        yaml.set("prologue.legacy.chronicle-committed", chronicleCommitted);
        yaml.set("prologue.legacy.monument-committed", monumentCommitted);
        yaml.set("prologue.audit", List.copyOf(audit));
        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            throw new UncheckedIOException("prologue.yml mentése sikertelen", exception);
        }
    }

    private MemoryState snapshotLocked() {
        return new MemoryState(state, stage, finalePhase, finaleId, stability,
                stateChangedAt, stageChangedAt, finalePhaseChangedAt, paused,
                bossDefeated, finaleVictory, gateUnlocked, rewardPlanCreated,
                rewardsCommitted, chronicleCommitted, monumentCommitted,
                seasonOneStarted, new LinkedHashSet<>(finaleParticipants),
                new ArrayDeque<>(audit));
    }

    private void restoreLocked(final MemoryState value) {
        state = value.state();
        stage = value.stage();
        finalePhase = value.finalePhase();
        finaleId = value.finaleId();
        stability = value.stability();
        stateChangedAt = value.stateChangedAt();
        stageChangedAt = value.stageChangedAt();
        finalePhaseChangedAt = value.finalePhaseChangedAt();
        paused = value.paused();
        bossDefeated = value.bossDefeated();
        finaleVictory = value.finaleVictory();
        gateUnlocked = value.gateUnlocked();
        rewardPlanCreated = value.rewardPlanCreated();
        rewardsCommitted = value.rewardsCommitted();
        chronicleCommitted = value.chronicleCommitted();
        monumentCommitted = value.monumentCommitted();
        seasonOneStarted = value.seasonOneStarted();
        finaleParticipants.clear();
        finaleParticipants.addAll(value.participants());
        audit.clear();
        audit.addAll(value.audit());
    }

    private static PrologueState parseState(final String raw) {
        try { return PrologueState.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (final RuntimeException ignored) { return PrologueState.UNSTABLE; }
    }

    private static PrologueStage parseStage(final String raw) {
        try { return PrologueStage.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (final RuntimeException ignored) { return PrologueStage.SILENCE; }
    }

    private record MemoryState(PrologueState state, PrologueStage stage,
                               PrologueFinalePhase finalePhase, UUID finaleId, int stability,
                               long stateChangedAt, long stageChangedAt, long finalePhaseChangedAt,
                               boolean paused, boolean bossDefeated, boolean finaleVictory,
                               boolean gateUnlocked, boolean rewardPlanCreated,
                               boolean rewardsCommitted, boolean chronicleCommitted,
                               boolean monumentCommitted, boolean seasonOneStarted,
                               LinkedHashSet<UUID> participants, ArrayDeque<String> audit) { }
}
