package hu.taliann.icesmp.classspec.integration;

import hu.taliann.icesmp.data.JobType;
import hu.taliann.icesmp.data.SpecializationType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Converts one class runtime's compact component into the stable generic HUD contract. */
public final class ClassHudStateAdapter {
    private final JobType job;
    private final Function<Player, SpecializationType> specialization;
    private final Function<Player, Component> mechanics;

    public ClassHudStateAdapter(final JobType job,
                                final Function<Player, SpecializationType> specialization,
                                final Function<Player, Component> mechanics) {
        this.job = Objects.requireNonNull(job);
        this.specialization = Objects.requireNonNull(specialization);
        this.mechanics = Objects.requireNonNull(mechanics);
    }

    public JobType job() { return job; }

    public ClassHudState snapshot(final Player player) {
        final SpecializationType spec = specialization.apply(player);
        final String rendered = PlainTextComponentSerializer.plainText().serialize(mechanics.apply(player));
        final List<String> parts = new ArrayList<>();
        for (final String raw : rendered.split("\\s*[•]\\s*")) {
            final String value = raw.trim();
            if (!value.isEmpty()) parts.add(value);
        }
        final String primary = parts.isEmpty() ? "" : parts.getFirst();
        final String secondary = parts.size() < 2 ? "" : parts.get(1);
        final String state = parts.size() < 3 ? "" : parts.get(2);
        final String proc = parts.size() < 4 ? "" : parts.get(3);
        final int[] charges = inferCharges(parts);
        return new ClassHudState(job.name().toLowerCase(java.util.Locale.ROOT),
                spec == null ? "" : spec.getId(),
                spec == null ? "" : PlainTextComponentSerializer.plainText().serialize(spec.getDisplayName()),
                primary, secondary, state, proc, charges[0], charges[1], parts);
    }

    private static int[] inferCharges(final List<String> parts) {
        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:^|\\s)(\\d+)\\s*/\\s*(\\d+)(?:\\s|$)");
        for (final String part : parts) {
            final java.util.regex.Matcher matcher = pattern.matcher(part);
            if (matcher.find()) return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
        }
        return new int[]{0, 0};
    }
}
