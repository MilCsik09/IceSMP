package hu.taliann.icesmp.ux;

import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
        private final String sequenceId;
        private final List<DialogueNode> nodes;
        private int index;
        private boolean canSkip;
        private final String musicContextId;
        private final Runnable onComplete;
        private final List<ScheduledTask> tasks = new ArrayList<>();

        private Session(final DialogueSequence sequence) {
            sequenceId = sequence.id();
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
        cancel(player.getUniqueId());
        final Session session = new Session(sequence);
        sessions.put(player.getUniqueId(), session);
        if (sequence.musicContext() != null && music != null) music.push(player, sequence.musicContext());
        advance(player, session);
    }

    /**
     * QuestManager adapter: keeps authored quest definitions authoritative while moving only
     * timing, replacement and cleanup into this runtime.
     */
    public boolean playQuestLines(final Player player, final String questId, final String phase,
                                  final String speaker, final List<String> lines) {
        if (player == null || lines == null || lines.isEmpty()) return false;
        final List<DialogueNode> nodes = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            final Component line = messages.getMessage("quest.dialogue-line",
                    "<gold>{speaker}:</gold> <white>{line}</white>",
                    Map.of("speaker", speaker, "line", lines.get(i)));
            nodes.add(new DialogueNode(questId + ":" + phase + ":" + i, "",
                    line, i == 0 ? 0L : 30L, 30L, true,
                    ignored -> true, null, null));
        }
        play(player, new DialogueSequence(questId + ":" + phase, nodes, null, null));
        return true;
    }

    public void skip(final Player player) {
        if (player == null) return;
        final Session session = sessions.get(player.getUniqueId());
        if (session == null || !session.canSkip) return;
        finish(player, session);
    }

    public void cancel(final UUID playerId) {
        final Session session = sessions.remove(playerId);
        if (session == null) return;
        final Player player = Bukkit.getPlayer(playerId);
        if (session.musicContextId != null && music != null) removeMusic(playerId, session.musicContextId);
        for (final ScheduledTask task : List.copyOf(session.tasks)) task.cancel();
        session.tasks.clear();
    }

    public boolean active(final UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void clearPlayerState(final UUID playerId) {
        cancel(playerId);
    }

    public void shutdown() {
        for (final UUID id : List.copyOf(sessions.keySet())) cancel(id);
        sessions.clear();
    }

    private void removeMusic(final UUID playerId, final String contextId) {
        final MusicDirector director = music;
        if (director == null) return;
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            director.clearPlayerState(playerId);
            return;
        }
        final Runnable remove = () -> director.remove(player, contextId);
        if (Bukkit.isOwnedByCurrentRegion(player)) remove.run();
        else player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) remove.run();
        }, null);
    }

    private void advance(final Player player, final Session session) {
        if (sessions.get(player.getUniqueId()) != session) return;
        if (session.index >= session.nodes.size()) {
            finish(player, session);
            return;
        }
        final DialogueNode node = session.nodes.get(session.index++);
        if (!node.condition().test(player)) {
            advance(player, session);
            return;
        }
        session.canSkip = node.skippable();
        if (node.onEnter() != null) node.onEnter().run();
        final Component line = node.speaker() == null || node.speaker().isBlank()
                ? node.text()
                : Component.text(node.speaker(), NamedTextColor.GOLD)
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                .append(node.text());
        player.sendMessage(line);
        final long nextDelay = session.index < session.nodes.size()
                ? session.nodes.get(session.index).delayTicks() : 0L;
        final long wait = node.durationTicks() + nextDelay;
        if (session.index < session.nodes.size()) {
            scheduleNext(player, session, wait, node.onComplete());
        } else {
            scheduleFinish(player, session, wait, node.onComplete());
        }
    }

    private void scheduleNext(final Player player, final Session session, final long delay,
                              final Runnable completion) {
        final ScheduledTask scheduled = player.getScheduler().runDelayed(plugin,
                task -> {
                    final Player current = Bukkit.getPlayer(player.getUniqueId());
                    if (current == null || !current.isOnline()) {
                        clearPlayerState(player.getUniqueId());
                        return;
                    }
                    if (completion != null) completion.run();
                    advance(current, session);
                },
                () -> { if (sessions.get(player.getUniqueId()) == session) clearPlayerState(player.getUniqueId()); }, Math.max(1L, delay));
        if (scheduled != null) session.tasks.add(scheduled);
    }

    private void scheduleFinish(final Player player, final Session session, final long delay,
                                final Runnable completion) {
        if (delay <= 0L) {
            if (completion != null) completion.run();
            finish(player, session);
            return;
        }
        final ScheduledTask scheduled = player.getScheduler().runDelayed(plugin,
                task -> {
                    if (completion != null) completion.run();
                    finish(player, session);
                },
                () -> { if (sessions.get(player.getUniqueId()) == session) clearPlayerState(player.getUniqueId()); }, delay);
        if (scheduled != null) session.tasks.add(scheduled);
    }

    private void finish(final Player player, final Session session) {
        if (!sessions.remove(player.getUniqueId(), session)) return;
        if (session.musicContextId != null && music != null) removeMusic(player.getUniqueId(), session.musicContextId);
        if (session.onComplete != null) session.onComplete.run();
        for (final ScheduledTask task : List.copyOf(session.tasks)) task.cancel();
        session.tasks.clear();
    }
}
