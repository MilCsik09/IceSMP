package hu.taliann.icesmp.ux;

import hu.taliann.icesmp.managers.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                               boolean skippable, Predicate<Player> condition,
                               Runnable onEnter, Runnable onComplete) {
        public DialogueNode {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("node id required");
            text = text == null ? Component.empty() : text;
            if (delayTicks < 0L) throw new IllegalArgumentException("delay must be non-negative");
            condition = condition == null ? player -> true : condition;
        }
    }

    public record DialogueSequence(String id, List<DialogueNode> nodes,
                                   String musicContextId, Runnable onComplete) {
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
        private final List<ScheduledTask> tasks = new ArrayList<>();

        private Session(final DialogueSequence sequence) {
            sequenceId = sequence.id();
            nodes = sequence.nodes();
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
            final int index = i;
            nodes.add(new DialogueNode(questId + ":" + phase + ":" + i, speaker,
                    Component.text(lines.get(i)), i == 0 ? 0L : 30L, true,
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
        if (node.onComplete() != null) node.onComplete().run();
        if (session.index < session.nodes.size()) {
            scheduleNext(player, session, session.nodes.get(session.index).delayTicks());
        } else {
            finish(player, session);
        }
    }

    private void scheduleNext(final Player player, final Session session, final long delay) {
        final ScheduledTask scheduled = player.getScheduler().runDelayed(plugin,
                task -> {
                    final Player current = Bukkit.getPlayer(player.getUniqueId());
                    if (current == null || !current.isOnline()) {
                        clearPlayerState(player.getUniqueId());
                        return;
                    }
                    advance(current, session);
                },
                () -> clearPlayerState(player.getUniqueId()), Math.max(1L, delay));
        if (scheduled != null) session.tasks.add(scheduled);
    }

    private void finish(final Player player, final Session session) {
        if (!sessions.remove(player.getUniqueId(), session)) return;
        for (final ScheduledTask task : List.copyOf(session.tasks)) task.cancel();
        session.tasks.clear();
    }
}
