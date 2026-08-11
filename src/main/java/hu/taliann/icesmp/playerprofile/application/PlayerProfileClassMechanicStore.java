package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.classspec.domain.ClassLoadout;
import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Specialization-local mechanic state mutation with aligned internal/section revision. */
public final class PlayerProfileClassMechanicStore {

    public Optional<String> read(final UUID playerId, final String key) {
        final ClassSpecSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.CLASS_SPEC, ClassSpecSection.class);
        final LoadoutSlot active = section.activeSlot();
        if (active == null) return Optional.empty();
        return Optional.ofNullable(section.loadout(active).mechanicState().get(key(key)));
    }

    public CompletionStage<Boolean> putIfAbsent(final UUID playerId,
                                                final String key,
                                                final String value) {
        final String id = key(key);
        final String cleanValue = value(value);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.CLASS_SPEC, ClassSpecSection.class, current -> {
                    final LoadoutSlot active = current.activeSlot();
                    if (active == null) throw new IllegalStateException("no active specialization");
                    final ClassLoadout loadout = current.loadout(active);
                    if (loadout.mechanicState().containsKey(id)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    final LinkedHashMap<String, String> mechanics =
                            new LinkedHashMap<>(loadout.mechanicState());
                    mechanics.put(id, cleanValue);
                    final ClassSpecSection next = current.toBuilder()
                            .revision(Math.addExact(current.revision(), 1L))
                            .loadout(active, loadout.withMechanicState(mechanics))
                            .build();
                    return PlayerProfileService.ConditionalMutation.changed(next, true);
                });
    }

    public CompletionStage<Void> put(final UUID playerId, final String key,
                                     final String value) {
        final String id = key(key);
        final String cleanValue = value(value);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.CLASS_SPEC, ClassSpecSection.class, current -> {
                    final LoadoutSlot active = current.activeSlot();
                    if (active == null) throw new IllegalStateException("no active specialization");
                    final ClassLoadout loadout = current.loadout(active);
                    if (cleanValue.equals(loadout.mechanicState().get(id))) {
                        return PlayerProfileService.ConditionalMutation.unchanged(null);
                    }
                    final LinkedHashMap<String, String> mechanics =
                            new LinkedHashMap<>(loadout.mechanicState());
                    mechanics.put(id, cleanValue);
                    final ClassSpecSection next = current.toBuilder()
                            .revision(Math.addExact(current.revision(), 1L))
                            .loadout(active, loadout.withMechanicState(mechanics))
                            .build();
                    return PlayerProfileService.ConditionalMutation.changed(next, null);
                });
    }

    private static String key(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("mechanic key required");
        final String id = raw.trim().toLowerCase(Locale.ROOT);
        if (id.length() > 96 || !id.matches("[a-z0-9][a-z0-9._:-]*")) {
            throw new IllegalArgumentException("invalid mechanic key: " + raw);
        }
        return id;
    }

    private static String value(final String raw) {
        if (raw == null || raw.isBlank() || raw.trim().length() > 512) {
            throw new IllegalArgumentException("invalid mechanic value");
        }
        return raw.trim();
    }
}
