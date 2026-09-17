package hu.taliann.icesmp.ux;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.function.BiPredicate;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private final ConcurrentMap<UUID, State> states = new ConcurrentHashMap<>();
    private volatile BiPredicate<UUID, MusicContext> enabled = (id, context) -> true;
    private volatile ToDoubleFunction<UUID> volume = id -> 1.0D;

    public MusicDirector(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void setPreferenceResolver(final BiPredicate<UUID, MusicContext> enabledResolver,
                                      final ToDoubleFunction<UUID> volumeResolver) {
        enabled = enabledResolver == null ? (id, context) -> true : enabledResolver;
        volume = volumeResolver == null ? id -> 1.0D : volumeResolver;
    }

    public void push(final Player player, final MusicContext context) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(context, "context");
        final UUID id = player.getUniqueId();
        final State before = states.getOrDefault(id, new State(Map.of(), null));
        final Map<String, MusicContext> next = new LinkedHashMap<>(before.contexts());
        next.put(context.id(), context);
        transition(player, new State(Map.copyOf(next), before.activeId()));
    }

    public void remove(final Player player, final String contextId) {
        if (player == null || contextId == null) return;
        final State before = states.get(player.getUniqueId());
        if (before == null) return;
        final Map<String, MusicContext> next = new LinkedHashMap<>(before.contexts());
        next.remove(contextId);
        transition(player, new State(Map.copyOf(next), before.activeId()));
    }

    public void clear(final Player player) {
        if (player == null) return;
        final State before = states.remove(player.getUniqueId());
        if (before != null && before.activeId() != null) {
            final MusicContext active = before.contexts().get(before.activeId());
            if (active != null) player.stopSound(active.sound());
        }
    }

    public void clearPlayerState(final UUID playerId) {
        if (playerId != null) states.remove(playerId);
    }

    public void shutdown() {
        for (final Player player : List.copyOf(Bukkit.getOnlinePlayers())) clear(player);
        states.clear();
    }

    public MusicContext active(final UUID playerId) {
        final State state = states.get(playerId);
        return state == null || state.activeId() == null ? null : state.contexts().get(state.activeId());
    }

    public static MusicContext select(final List<MusicContext> contexts) {
        return contexts == null ? null : contexts.stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(MusicContext::priority)
                        .thenComparing(MusicContext::id))
                .orElse(null);
    }

    private void transition(final Player player, final State candidate) {
        final MusicContext selected = select(new ArrayList<>(candidate.contexts().values()));
        final String selectedId = selected == null ? null : selected.id();
        final State next = new State(candidate.contexts(), selectedId);
        final State previous = states.put(player.getUniqueId(), next);
        final String previousId = previous == null ? null : previous.activeId();
        final MusicContext previousActive = previous == null || previousId == null
                ? null : previous.contexts().get(previousId);
        if (Objects.equals(previousActive, selected)) return;
        if (previousActive != null) player.stopSound(previousActive.sound());
        if (selected != null && enabled.test(player.getUniqueId(), selected)) {
            final double multiplier = Math.max(0.0D, Math.min(1.0D, volume.applyAsDouble(player.getUniqueId())));
            player.playSound(player.getLocation(), selected.sound(),
                    selected.volume() * (float) multiplier, selected.pitch());
        }
    }
}
