package hu.taliann.icesmp.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe immutable editor sessions; all operations are presentation-only. */
public final class HudEditorStateMachine {

    private static final int HISTORY_LIMIT = 32;
    private static final List<Integer> STEPS = List.of(1, 5, 10);

    public record Session(HudLayoutSnapshot original, HudLayoutSnapshot working, int step,
                          HudPreviewSelection preview, List<HudLayoutSnapshot> undo,
                          long configGeneration, String configFingerprint) {
        public Session {
            original = original == null ? HudLayoutSnapshot.defaults() : original;
            working = working == null ? original : working;
            step = STEPS.contains(step) ? step : 1;
            preview = preview == null ? HudPreviewSelection.defaults() : preview;
            undo = undo == null ? List.of() : List.copyOf(undo);
            configFingerprint = configFingerprint == null ? "" : configFingerprint;
        }
    }

    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();

    public Session start(final UUID playerId, final HudLayoutSnapshot initial,
                         final long generation, final String fingerprint) {
        final HudLayoutSnapshot layout = initial == null ? HudLayoutSnapshot.defaults() : initial;
        final Session session = new Session(layout, layout, 1, HudPreviewSelection.defaults(),
                List.of(), generation, fingerprint);
        sessions.put(playerId, session);
        return session;
    }

    public Optional<Session> session(final UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public Session move(final UUID playerId, final int horizontalSteps, final int verticalSteps) {
        return updateLayout(playerId, session -> session.working.move(
                horizontalSteps * session.step, verticalSteps * session.step));
    }

    public Session margin(final UUID playerId, final int direction) {
        return updateLayout(playerId, session -> session.working.changeMargin(direction * session.step));
    }

    public Session scale(final UUID playerId, final int variants) {
        return updateLayout(playerId, session -> session.working.changeScale(variants));
    }

    public Session preset(final UUID playerId, final HudLayoutPreset preset) {
        return updateLayout(playerId, ignored -> preset.layout());
    }

    public Session reset(final UUID playerId) {
        return updateLayout(playerId, ignored -> HudLayoutSnapshot.defaults());
    }

    public Session undo(final UUID playerId) {
        return sessions.compute(playerId, (ignored, session) -> {
            if (session == null) throw new IllegalStateException("HUD editor session is not active");
            if (session.undo.isEmpty()) return session;
            final ArrayList<HudLayoutSnapshot> history = new ArrayList<>(session.undo);
            final HudLayoutSnapshot previous = history.removeLast();
            return copy(session, previous, session.step, session.preview, history);
        });
    }

    public Session step(final UUID playerId, final int step) {
        if (!STEPS.contains(step)) throw new IllegalArgumentException("Unsupported HUD editor step: " + step);
        return sessions.compute(playerId, (ignored, session) -> {
            if (session == null) throw new IllegalStateException("HUD editor session is not active");
            return copy(session, session.working, step, session.preview, session.undo);
        });
    }

    public Session previewFaction(final UUID playerId, final String faction) {
        if (!HudPreviewSelection.validFaction(faction)) throw new IllegalArgumentException("Unknown faction preview");
        return updatePreview(playerId, session -> session.preview.withFaction(faction));
    }

    public Session previewClass(final UUID playerId, final String playerClass) {
        if (!HudPreviewSelection.validClass(playerClass)) throw new IllegalArgumentException("Unknown class preview");
        return updatePreview(playerId, session -> session.preview.withClass(playerClass));
    }

    public Session previewState(final UUID playerId, final String state) {
        if (!HudPreviewSelection.validState(state)) throw new IllegalArgumentException("Unknown state preview");
        return updatePreview(playerId, session -> session.preview.withState(state));
    }

    public Optional<HudLayoutSnapshot> cancel(final UUID playerId) {
        final Session removed = sessions.remove(playerId);
        return removed == null ? Optional.empty() : Optional.of(removed.original);
    }

    public Optional<HudLayoutSnapshot> apply(final UUID playerId) {
        final Session removed = sessions.remove(playerId);
        return removed == null ? Optional.empty() : Optional.of(removed.working);
    }

    public void clear() {
        sessions.clear();
    }

    private Session updateLayout(final UUID playerId,
                                 final java.util.function.Function<Session, HudLayoutSnapshot> change) {
        return sessions.compute(playerId, (ignored, session) -> {
            if (session == null) throw new IllegalStateException("HUD editor session is not active");
            final HudLayoutSnapshot next = change.apply(session);
            if (next.equals(session.working)) return session;
            final ArrayList<HudLayoutSnapshot> history = new ArrayList<>(session.undo);
            history.add(session.working);
            if (history.size() > HISTORY_LIMIT) history.removeFirst();
            return copy(session, next, session.step, session.preview, history);
        });
    }

    private Session updatePreview(final UUID playerId,
                                  final java.util.function.Function<Session, HudPreviewSelection> change) {
        return sessions.compute(playerId, (ignored, session) -> {
            if (session == null) throw new IllegalStateException("HUD editor session is not active");
            return copy(session, session.working, session.step, change.apply(session), session.undo);
        });
    }

    private static Session copy(final Session session, final HudLayoutSnapshot working, final int step,
                                final HudPreviewSelection preview, final List<HudLayoutSnapshot> undo) {
        return new Session(session.original, working, step, preview, undo,
                session.configGeneration, session.configFingerprint);
    }
}
