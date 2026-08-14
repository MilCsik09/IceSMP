package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.commands.PrologueCommand;
import hu.taliann.icesmp.managers.ChronicleManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.EventSpawnGuard;
import hu.taliann.icesmp.managers.EventSpawnPointManager;
import hu.taliann.icesmp.managers.MajorEventGate;
import hu.taliann.icesmp.managers.SeasonMonumentManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** Runtime composition root installed immediately after the existing IceSMPCore. */
public final class PrologueRuntime implements Listener {
    private static volatile PrologueRuntime active;

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PrologueManager manager;
    private final PrologueWorldAccess worldAccess;
    private final PrologueParticipantTracker participants;
    private final PrologueEncounterEngine encounters;
    private final PrologueTimelineController timeline;
    private final PrologueHudController hud;
    private final PrologueRewardService rewards;
    private final PrologueFinaleManager finale;
    private final PrologueSeasonTransition seasonTransition;
    private final MajorEventGate eventGate;
    private ScheduledTask tickTask;
    private volatile long nextNaturalBreachAt;

    private PrologueRuntime(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = Objects.requireNonNull(ConfigManager.current(), "ConfigManager not installed");
        final EventSpawnPointManager spawnPoints = Objects.requireNonNull(
                EventSpawnPointManager.current(), "EventSpawnPointManager not installed");
        final EventSpawnGuard spawnGuard = Objects.requireNonNull(
                EventSpawnGuard.current(), "EventSpawnGuard not installed");
        this.eventGate = Objects.requireNonNull(MajorEventGate.current(), "MajorEventGate not installed");
        this.manager = new PrologueManager(plugin, config);
        this.worldAccess = new PrologueWorldAccess(spawnPoints);
        this.participants = new PrologueParticipantTracker(plugin, config, manager, worldAccess);
        this.encounters = new PrologueEncounterEngine(plugin, config, worldAccess,
                participants, spawnGuard, eventGate);
        this.timeline = new PrologueTimelineController(config, manager);
        this.hud = new PrologueHudController(plugin, config, manager, worldAccess);
        this.rewards = new PrologueRewardService(plugin, manager);
        this.seasonTransition = new PrologueSeasonTransition(plugin, config);
        this.finale = new PrologueFinaleManager(plugin, config, manager,
                new PrologueFinaleRunState(plugin), participants, encounters, rewards,
                seasonTransition, eventGate);
        reconcileSeasonGates();
    }

    public static synchronized PrologueRuntime install(final JavaPlugin plugin) {
        if (active != null) return active;
        final PrologueRuntime runtime = new PrologueRuntime(plugin);
        active = runtime;
        Bukkit.getPluginManager().registerEvents(runtime, plugin);
        Bukkit.getPluginManager().registerEvents(runtime.encounters, plugin);
        Bukkit.getPluginManager().registerEvents(
                new PrologueCeasefireListener(runtime.worldAccess, runtime.config,
                        runtime.finale::ceasefireActive), plugin);
        runtime.eventGate.register("prologue",
                () -> runtime.finale.isActive() || runtime.encounters.isActive());
        plugin.registerCommand("prologue", "Season 0 / Kárhozat Kapuja live-ops",
                List.of(), new PrologueCommand(runtime));
        runtime.tickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                task -> runtime.tick(), 20L, 20L);
        for (final Player player : Bukkit.getOnlinePlayers()) {
            runtime.rewards.grantEligibleWhenProfileReady(player, 0);
        }
        return runtime;
    }

    public static PrologueRuntime current() { return active; }

    public static synchronized void shutdown() {
        final PrologueRuntime runtime = active;
        active = null;
        if (runtime == null) return;
        if (runtime.tickTask != null) runtime.tickTask.cancel();
        runtime.hud.shutdown();
        runtime.finale.shutdown();
        runtime.manager.save();
    }

    public PrologueManager manager() { return manager; }
    public PrologueFinaleManager finale() { return finale; }

    /**
     * Teszt-visszaállítás: leállítja a futó finálét, visszavonja a Season 1 átbillenést, majd a
     * tartós Prologue-állapotot DORMANT-ra tekeri. A sorrend kötött — a szezon-oldalnak a
     * Prologue-rewind ELŐTT kell rendeződnie, különben a content overlay Season 1 alatt kapcsolna
     * vissza Season 0 tartalomkorlátra.
     */
    public void resetForTesting(final String actor) {
        try {
            finale.abort(actor);
        } catch (final IllegalArgumentException | IllegalStateException noRunningFinale) {
            // nincs leállítandó futó finálé
        }
        if (manager.seasonOneStarted()) seasonTransition.rollbackSeasonOne();
        if (manager.chronicleCommitted()) {
            final ChronicleManager chronicle = ChronicleManager.current();
            if (chronicle != null) chronicle.forgetExtraordinary("prologue-gate-open");
        }
        if (manager.monumentCommitted()) {
            final SeasonMonumentManager monument = SeasonMonumentManager.current();
            if (monument != null) monument.forgetPrologue("prologue-first-expedition");
        }
        manager.rewind(actor);
        reconcileSeasonGates();
    }
    public PrologueWorldAccess worldAccess() { return worldAccess; }
    public PrologueEncounterEngine encounters() { return encounters; }

    private void tick() {
        reconcileSeasonGates();
        timeline.tick();
        if (finale.isActive()) participants.tickPresence();
        finale.tick();
        hud.setEventActive(finale.isActive() || encounters.isActive());
        hud.tick();
        tickNaturalBreach();
    }

    private void reconcileSeasonGates() {
        if (manager.state().completed()) {
            config.clearRuntimeOverride("world-events.season.enabled");
            config.clearRuntimeOverride("world-events.season-finale.enabled");
            config.clearRuntimeOverride("community-goals.enabled");
            return;
        }
        config.setRuntimeOverride("world-events.season.enabled", false);
        config.setRuntimeOverride("world-events.season-finale.enabled", false);
        config.setRuntimeOverride("community-goals.enabled", false);
    }

    private void tickNaturalBreach() {
        if (!PrologueContentPolicy.active(config) || finale.isActive() || encounters.isActive()
                || manager.state() != PrologueState.BREACHING) return;
        final PrologueStage stage = manager.stage();
        if (stage != PrologueStage.LEAK && stage != PrologueStage.COLLAPSE) return;
        final long now = System.currentTimeMillis();
        if (nextNaturalBreachAt == 0L) {
            nextNaturalBreachAt = now + naturalBreachInterval(stage);
            return;
        }
        if (now < nextNaturalBreachAt) return;
        nextNaturalBreachAt = now + naturalBreachInterval(stage);
        countPlayersNearGate(count -> {
            if (count <= 0 || finale.isActive() || encounters.isActive()) return;
            final BreachSeverity severity = stage == PrologueStage.COLLAPSE
                    ? BreachSeverity.MAJOR : BreachSeverity.MINOR;
            encounters.startBreach(severity, count,
                    () -> Bukkit.broadcast(Component.text(
                            "A Kárhozat Kapujánál a hasadék átmenetileg visszahúzódott.", NamedTextColor.GRAY)),
                    reason -> plugin.getLogger().warning("Natural Prologue breach failed: " + reason));
        });
    }

    private long naturalBreachInterval(final PrologueStage stage) {
        final long minutes = Math.max(2L, config.getLong(
                "world-events.prologue.breach.natural-interval-minutes",
                stage == PrologueStage.COLLAPSE ? 20L : 45L));
        return minutes * 60_000L;
    }

    public void startAdminBreach(final BreachSeverity severity, final CommandSender sender) {
        if (finale.isActive()) {
            sender.sendMessage("§cFinálé közben külön breach nem indítható.");
            return;
        }
        countPlayersNearGate(count -> {
            final boolean started = encounters.startBreach(severity, count,
                    () -> send(sender, "§aA Gate Breach lezárult."),
                    reason -> send(sender, "§cGate Breach hiba: " + reason));
            send(sender, started
                    ? "§aGate Breach indult. Résztvevő skálázási minta: §f" + count
                    : "§cA Gate Breach nem indítható.");
        });
    }

    private void countPlayersNearGate(final IntConsumer consumer) {
        final List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            consumer.accept(0);
            return;
        }
        final double radius = Math.max(16.0D, config.getDouble(
                "world-events.prologue.breach.participant-radius", 96.0D));
        final AtomicInteger remaining = new AtomicInteger(online.size());
        final AtomicInteger count = new AtomicInteger();
        for (final Player player : online) {
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline() && PrologueWorldAccess.within(
                        player.getLocation(), worldAccess.gateAnchor(), radius)) count.incrementAndGet();
                completeCount(remaining, count, consumer);
            }, () -> completeCount(remaining, count, consumer));
        }
    }

    private void completeCount(final AtomicInteger remaining, final AtomicInteger count,
                               final IntConsumer consumer) {
        if (remaining.decrementAndGet() == 0) {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> consumer.accept(count.get()));
        }
    }

    private void send(final CommandSender sender, final String message) {
        if (sender instanceof Player player) {
            player.getScheduler().run(plugin, task -> player.sendMessage(message), null);
        } else {
            sender.sendMessage(message);
        }
    }

    public String statusLine() {
        return "§5Prologue §7state=§f" + manager.state()
                + " §7stage=§f" + manager.stage()
                + " §7stability=§f" + manager.stability() + "%"
                + " §7finale=§f" + finale.phase()
                + (manager.paused() ? " §c[PAUSED]" : "")
                + (finale.isRehearsal() ? " §e[REHEARSAL]" : "")
                + " §7gate=§f" + (manager.gateUnlocked() ? "OPEN" : "CLOSED");
    }

    /** Az egyszeri transition commit-lánca: éles helyzetben ez mondja meg, hol tart a folyamat. */
    public String commitChainLine() {
        final String chain = flag("boss", manager.bossDefeated()) + flag("victory", manager.finaleVictory())
                + flag("gate", manager.gateUnlocked()) + flag("reward-plan", manager.rewardPlanCreated())
                + flag("rewards", manager.rewardsCommitted()) + flag("chronicle", manager.chronicleCommitted())
                + flag("monument", manager.monumentCommitted()) + flag("season1", manager.seasonOneStarted());
        return "§5Prologue §7commit:" + chain
                + " §7participants=§f" + manager.finaleParticipants().size()
                + " §7timeline=§f" + (manager.state() == PrologueState.DORMANT ? "§8INERT" : "§aÉLES")
                + (manager.bossVictoryPending()
                        ? " §c[VICTORY-PENDING: " + manager.bossVictoryFailure() + "]" : "");
    }

    private static String flag(final String label, final boolean value) {
        return (value ? " §a✔" : " §8✘") + label;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        rewards.grantEligibleWhenProfileReady(event.getPlayer(), 0);
    }
}
