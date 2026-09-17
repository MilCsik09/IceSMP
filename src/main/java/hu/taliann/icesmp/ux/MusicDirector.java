package hu.taliann.icesmp.ux;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Player-owned music context arbitration. Long tracks are resource-pack sound events; the server
 * only selects and transitions them. Looping is delegated to the client sound-event definition.
 */
public final class MusicDirector {

    public enum Type {
        AMBIENT, REGION, QUEST, EVENT, COMBAT, BOSS, CINEMATIC, DEV_PREVIEW
    }

    public record MusicContext(String id, Type type, int priority, String sound,
                               float volume, float pitch, boolean loop) {
        public MusicContext {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("music id required");
            Objects.requireNonNull(type, "type");
            if (sound == null || sound.isBlank()) throw new IllegalArgumentException("sound required");
            if (!Float.isFinite(volume) || volume < 0.0F) throw new IllegalArgumentException("invalid volume");
            if (!Float.isFinite(pitch) || pitch <= 0.0F) throw new IllegalArgumentException("invalid pitch");
        }
    }

    private record State(Map<String, MusicContext> contexts, String activeId) { }

    private final JavaPlugin plugin;
    /** Rebuildable runtime/session state; no durable player authority lives here. */
    private final ConcurrentMap<UUID, State> activeContexts = new ConcurrentHashMap<>();
    private volatile BiPredicate<UUID, MusicContext> enabled = (id, context) -> true;
    private volatile ToDoubleFunction<UUID> volume = id -> 1.0D;

    public MusicDirector(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void setPreferenceResolver(final BiPredicate<UUID, MusicContext> enabledResolver,
                                      final ToDoubleFunction<UUID> volumeResolver) {
        enabled = enabledResolver == null ? (id, context) -> true : enabledResolver;
        volume = volumeResolver == null ? id -> 1.0D : volumeResolver;
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) refresh(player);
    }

    /**
     * Adds or replaces a persistent music context. Non-loop contexts are treated as transient
     * sound cues: they play once and never become arbitration state that could block fallback.
     */
    public void push(final Player player, final MusicContext context) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(context, "context");
        runOwned(player, () -> {
            if (!context.loop()) {
                playTransientOwned(player, context);
                return;
            }
            final UUID id = player.getUniqueId();
            final State before = activeContexts.getOrDefault(id, new State(Map.of(), null));
            final Map<String, MusicContext> next = new LinkedHashMap<>(before.contexts());
            next.put(context.id(), context);
            transitionOwned(player, new State(Map.copyOf(next), before.activeId()));
        });
    }

    public void remove(final Player player, final String contextId) {
        if (player == null || contextId == null) return;
        runOwned(player, () -> {
            final State before = activeContexts.get(player.getUniqueId());
            if (before == null) return;
            final Map<String, MusicContext> next = new LinkedHashMap<>(before.contexts());
            next.remove(contextId);
            transitionOwned(player, new State(Map.copyOf(next), before.activeId()));
        });
    }

    public void clear(final Player player) {
        if (player == null) return;
        runOwned(player, () -> clearOwned(player));
    }

    public void refresh(final Player player) {
        if (player == null) return;
        runOwned(player, () -> {
            final State current = activeContexts.get(player.getUniqueId());
            if (current != null) transitionOwned(player, current);
        });
    }

    public void clearPlayerState(final UUID playerId) {
        if (playerId != null) activeContexts.remove(playerId);
    }

    public void shutdown() {
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            final State state = activeContexts.remove(player.getUniqueId());
            if (state == null || state.activeId() == null) continue;
            final MusicContext active = state.contexts().get(state.activeId());
            if (active == null) continue;
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline()) player.stopSound(active.sound());
            }, null);
        }
        activeContexts.clear();
    }

    public MusicContext active(final UUID playerId) {
        final State state = activeContexts.get(playerId);
        return state == null || state.activeId() == null ? null : state.contexts().get(state.activeId());
    }

    public static MusicContext select(final List<MusicContext> contexts) {
        return select(contexts, ignored -> true);
    }

    public static MusicContext select(final List<MusicContext> contexts,
                                      final Predicate<MusicContext> accepted) {
        if (contexts == null) return null;
        final Predicate<MusicContext> filter = accepted == null ? ignored -> true : accepted;
        return contexts.stream()
                .filter(Objects::nonNull)
                .filter(filter)
                .max(Comparator.comparingInt(MusicContext::priority)
                        .thenComparing(MusicContext::id))
                .orElse(null);
    }

    private void clearOwned(final Player player) {
        final State before = activeContexts.remove(player.getUniqueId());
        if (before != null && before.activeId() != null) {
            final MusicContext active = before.contexts().get(before.activeId());
            if (active != null) player.stopSound(active.sound());
        }
    }

    private void transitionOwned(final Player player, final State candidate) {
        final UUID playerId = player.getUniqueId();
        final MusicContext selected = select(new ArrayList<>(candidate.contexts().values()),
                context -> enabled.test(playerId, context));
        final String selectedId = selected == null ? null : selected.id();
        final State next = new State(candidate.contexts(), selectedId);
        final State previous = activeContexts.put(playerId, next);
        final String previousId = previous == null ? null : previous.activeId();
        final MusicContext previousActive = previous == null || previousId == null
                ? null : previous.contexts().get(previousId);
        if (Objects.equals(previousActive, selected)) return;
        if (previousActive != null) player.stopSound(previousActive.sound());
        if (selected != null) playOwned(player, selected);
    }

    private void playTransientOwned(final Player player, final MusicContext context) {
        if (!enabled.test(player.getUniqueId(), context)) return;
        playOwned(player, context);
    }

    private void playOwned(final Player player, final MusicContext context) {
        final double raw = volume.applyAsDouble(player.getUniqueId());
        final double multiplier = Double.isFinite(raw)
                ? Math.max(0.0D, Math.min(1.0D, raw)) : 1.0D;
        player.playSound(player.getLocation(), context.sound(),
                context.volume() * (float) multiplier, context.pitch());
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
