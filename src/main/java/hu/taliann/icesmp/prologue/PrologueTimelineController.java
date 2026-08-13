package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.ConfigManager;

import java.util.Locale;

/** Small time-driven escalation controller; no parallel rank/progression system. */
public final class PrologueTimelineController {
    private final ConfigManager config;
    private final PrologueManager prologue;

    public PrologueTimelineController(final ConfigManager config, final PrologueManager prologue) {
        this.config = config;
        this.prologue = prologue;
    }

    public void tick() {
        if (!config.getBoolean("world-events.prologue.timeline.auto-advance", true)) return;
        final PrologueState state = prologue.state();
        if (state != PrologueState.UNSTABLE && state != PrologueState.BREACHING) return;
        final PrologueStage current = prologue.stage();
        if (current == PrologueStage.COLLAPSE) return;
        final long hours = Math.max(1L, config.getLong(
                "world-events.prologue.stages." + current.name().toLowerCase(Locale.ROOT)
                        + ".duration-hours", defaultDurationHours(current)));
        final long durationMillis = hours > Long.MAX_VALUE / 3_600_000L
                ? Long.MAX_VALUE : hours * 3_600_000L;
        if (System.currentTimeMillis() - prologue.stageChangedAt() < durationMillis) return;
        prologue.setStage(PrologueStage.values()[current.ordinal() + 1], "timeline");
    }

    private static long defaultDurationHours(final PrologueStage stage) {
        return switch (stage) {
            case SILENCE -> 72L;
            case CRACKS -> 72L;
            case LEAK -> 48L;
            case COLLAPSE -> Long.MAX_VALUE;
        };
    }
}
