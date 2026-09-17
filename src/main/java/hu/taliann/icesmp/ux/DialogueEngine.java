package hu.taliann.icesmp.ux;

import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Player-isolated, replaceable dialogue runtime. Every delayed action is scheduled through the
 * owning Player scheduler; no global mutable current-dialogue or raw thread is used.
 */
public final class DialogueEngine {

    public record DialogueNode(String id, String speaker, Component text, long delayTicks,
                               long durationTicks, boolean skippable, Predicate<Player> condition,
                               Runnable onEnter, Runnable onComplete) {
        public DialogueNode {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("node id required");
            text = text == null ? Component.empty() : text;
            if (delayTicks < 0L || durationTicks < 0L)
                throw new IllegalArgumentException("dialogue timing must be non-negative");
            condition = condition == null ? player -> true : condition;
        }
    }

    public record DialogueSequence(String id, List<DialogueNode> nodes,
                                   MusicDirector.MusicContext musicContext, Runnable onComplete) {
        public DialogueSequence {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("sequence id required");
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    private static final class Session {
        private final List<DialogueNode> nodes;
        private int index;
        private boolean canSkip;
        private final String musicContextId;
        private final Runnable onComplete;
        private final List<ScheduledTask> tasks = new CopyOnWriteArrayList<>();
        private Runnable pendingCompletion;

        private Session(final DialogueSequence sequence) {
            nodes = sequence.nodes();
            musicContextId = sequence.musicContext() == null ? null : sequence.musicContext().id();
            onComplete = sequence.onComplete();
        }
    }

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final ConcurrentMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private volatile MusicDirector music;

    public DialogueEngine(final JavaPlugin plugin, final MessageManager messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public void setMusicDirector(final MusicDirector musicDirector) {
        music = musicDirector;
    }

    public void play(final Player player, final DialogueSequence sequence) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(sequence, "sequence");
        runOwned(player, () -> playOwned(player, sequence));
    }

    public boolean playQuestLines(final Player player, final String questId, final String phase,
                                  final String speaker, final List<String> lines) {
        return playQuestLines(player, questId, phase, speaker, lines, null);
    }

    /**
     * QuestManager adapter: keeps authored quest definitions authoritative while moving timing,
     * replacement and completion ownership into this runtime. The established quest cadence is
     * one line every 30 ticks. When a completion callback is supplied (for example quest choices),
     * the final line retains the legacy 30-tick tail before the callback executes.
     */
    public boolean playQuestLines(final Player player, final String questId, final String phase,
                                  final String speaker, final List<String> lines,
                                  final Runnable onComplete) {
        if (player == null || lines == null || lines.isEmpty()) return false;
        final List<DialogueNode> nodes = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            final Component line = messages.getMessage("quest.dialogue-line",
                    "<gold>{speaker}:</gold> <white>{line}</white>",
                    Map.of("speaker", speaker, "line", lines.get(i)));
            final boolean last = i == lines.size() - 1;
            nodes.add(new DialogueNode(questId + ":" + phase + ":" + i, "",
                    line, i == 0 ? 0L : 30L, last && onComplete != null ? 30L : 0L, true,
                    ignored -> true, null, null));
        }
        play(player, new DialogueSequence(questId + ":" + phase, nodes, null, onComplete));
        return true;
    }

    public void skip(final Player player) {
        if (player == null) return;
        runOwned(player, () -> skipOwned(player));
    }

    public void cancel(final UUID playerId) {
        if (playerId == null) return;
        final Session session = sessions.remove(playerId);
        if (session == null) return;
        if (session.musicContextId != null && music != null) removeMusic(playerId, session.musicContextId);
        cancelTasks(session);
        session.pendingCompletion = null;
    }

    public boolean active(final UUID playerId) {
        return playerId != null && sessions.containsKey(playerId);
    }

    public void clearPlayerState(final UUID playerId) {
        cancel(playerId);
    }

    public void shutdown() {
        for (final UUID id : List.copyOf(sessions.keySet())) cancel(id);
        sessions.clear();
    }

    static long waitAfter(final DialogueNode current, final DialogueNode next) {
        Objects.requireNonNull(current, "current");
        return current.durationTicks() + (next == null ? 0L : next.delayTicks());
    }

    private void playOwned(final Player player, final DialogueSequence sequence) {
        cancel(player.getUniqueId());
        final Session session = new Session(sequence);
        sessions.put(player.getUniqueId(), session);
        if (sequence.musicContext() != null && music != null) music.push(player, sequence.musicContext());
        if (session.nodes.isEmpty()) {
            finish(player, session);
            return;
        }
        final long firstDelay = session.nodes.get(0).delayTicks();
        if (firstDelay <= 0L) advanceOwned(player, session);
        else scheduleStart(player, session, firstDelay);
    }

    private void skipOwned(final Player player) {
        final Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.canSkip) return;
        cancelTasks(session);
        session.canSkip = false;
        final Runnable completion = session.pendingCompletion;
        session.pendingCompletion = null;
        if (!runHook(player.getUniqueId(), "node completion", completion)) return;
        if (session.index < session.nodes.size()) advanceOwned(player, session);
        else finish(player, session);
    }

    private void removeMusic(final UUID playerId, final String contextId) {
        final MusicDirector director = music;
        if (director == null) return;
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            director.clearPlayerState(playerId);
            return;
        }
        director.remove(player, contextId);
    }

    private void advanceOwned(final Player player, final Session session) {
        final UUID playerId = player.getUniqueId();
        while (sessions.get(playerId) == session && session.index < session.nodes.size()) {
            final DialogueNode node = session.nodes.get(session.index++);
            final boolean allowed;
            try {
                allowed = node.condition().test(player);
            } catch (final RuntimeException | LinkageError failure) {
                hookFailure(playerId, "node condition", failure);
                return;
            }
            if (!allowed) continue;
            session.canSkip = node.skippable();
            if (!runHook(playerId, "node enter", node.onEnter())) return;
            final Component line = node.speaker() == null || node.speaker().isBlank()
                    ? node.text()
                    : Component.text(node.speaker(), NamedTextColor.GOLD)
                    .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                    .append(node.text());
            player.sendMessage(line);
            final DialogueNode next = session.index < session.nodes.size()
                    ? session.nodes.get(session.index) : null;
            final long wait = waitAfter(node, next);
            if (next != null) scheduleNext(player, session, wait, node.onComplete());
            else scheduleFinish(player, session, wait, node.onComplete());
            return;
        }
        if (sessions.get(playerId) == session) finish(player, session);
    }

    private void scheduleStart(final Player player, final Session session, final long delay) {
        final ScheduledTask scheduled = player.getScheduler().runDelayed(plugin,
                task -> {
                    if (sessions.get(player.getUniqueId()) == session && player.isOnline())
                        advanceOwned(player, session);
                },
                () -> {
                    if (sessions.get(player.getUniqueId()) == session)
                        clearPlayerState(player.getUniqueId());
                }, Math.max(1L, delay));
        if (scheduled != null) session.tasks.add(scheduled);
    }

    private void scheduleNext(final Player player, final Session session, final long delay,
                              final Runnable completion) {
        session.pendingCompletion = completion;
        final ScheduledTask scheduled = player.getScheduler().runDelayed(plugin,
                task -> {
                    if (sessions.get(player.getUniqueId()) != session) return;
                    session.pendingCompletion = null;
                    if (!player.isOnline()) {
                        clearPlayerState(player.getUniqueId());
                        return;
                    }
                    if (!runHook(player.getUniqueId(), "node completion", completion)) return;
                    advanceOwned(player, session);
                },
                () -> {
                    if (sessions.get(player.getUniqueId()) == session)
                        clearPlayerState(player.getUniqueId());
                }, Math.max(1L, delay));
        if (scheduled != null) session.tasks.add(scheduled);
    }

    private void scheduleFinish(final Player player, final Session session, final long delay,
                                final Runnable completion) {
        session.pendingCompletion = completion;
        if (delay <= 0L) {
            session.pendingCompletion = null;
            if (runHook(player.getUniqueId(), "node completion", completion)) finish(player, session);
            return;
        }
        final ScheduledTask scheduled = player.getScheduler().runDelayed(plugin,
                task -> {
                    if (sessions.get(player.getUniqueId()) != session) return;
                    session.pendingCompletion = null;
                    if (runHook(player.getUniqueId(), "node completion", completion))
                        finish(player, session);
                },
                () -> {
                    if (sessions.get(player.getUniqueId()) == session)
                        clearPlayerState(player.getUniqueId());
                }, delay);
        if (scheduled != null) session.tasks.add(scheduled);
    }

    private void finish(final Player player, final Session session) {
        if (!sessions.remove(player.getUniqueId(), session)) return;
        session.pendingCompletion = null;
        if (session.musicContextId != null && music != null)
            removeMusic(player.getUniqueId(), session.musicContextId);
        cancelTasks(session);
        runHook(player.getUniqueId(), "sequence completion", session.onComplete);
    }

    private boolean runHook(final UUID playerId, final String label, final Runnable hook) {
        if (hook == null) return true;
        try {
            hook.run();
            return true;
        } catch (final RuntimeException | LinkageError failure) {
            hookFailure(playerId, label, failure);
            return false;
        }
    }

    private void hookFailure(final UUID playerId, final String label, final Throwable failure) {
        plugin.getLogger().warning("Dialogue " + label + " failed for " + playerId + ": "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        cancel(playerId);
    }

    private static void cancelTasks(final Session session) {
        for (final ScheduledTask task : List.copyOf(session.tasks)) task.cancel();
        session.tasks.clear();
    }

    private void runOwned(final Player player, final Runnable action) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            action.run();
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) action.run();
        }, null);
    }
}
