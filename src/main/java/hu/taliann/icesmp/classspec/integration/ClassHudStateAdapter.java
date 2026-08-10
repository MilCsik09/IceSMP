package hu.taliann.icesmp.classspec.integration;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Attaches common identity to one class runtime's structured, immutable HUD projection. */
public final class ClassHudStateAdapter {
    private final JobType job;
    private final Function<Player, SpecializationType> specialization;
    private final Function<Player, ClassHudMechanics> mechanics;

    public ClassHudStateAdapter(final JobType job,
                                final Function<Player, SpecializationType> specialization,
                                final Function<Player, ClassHudMechanics> mechanics) {
        this.job = Objects.requireNonNull(job);
        this.specialization = Objects.requireNonNull(specialization);
        this.mechanics = Objects.requireNonNull(mechanics);
    }

    public JobType job() { return job; }

    public ClassHudState snapshot(final Player player) {
        final SpecializationType spec = specialization.apply(player);
        final ClassHudMechanics projection = Objects.requireNonNullElseGet(
                mechanics.apply(player), ClassHudMechanics::empty);
        final List<String> rendered = projection.metrics().stream()
                .map(ClassHudMetric::text).filter(value -> !value.isBlank()).toList();
        return new ClassHudState(job.name().toLowerCase(java.util.Locale.ROOT),
                spec == null ? "" : spec.getId(),
                spec == null ? "" : PlainTextComponentSerializer.plainText().serialize(spec.getDisplayName()),
                projection.primary().text(), projection.secondary().text(), projection.state(),
                projection.proc(), projection.charges(), projection.chargesMax(), rendered,
                projection.metrics(), projection.slots());
    }
}
