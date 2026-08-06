package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.LifecycleSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * CAS-backed death-to-respawn item escrow.
 *
 * <p>The deposit is durable before the respawn hand-back, so a crash, quit or kick between
 * death and respawn can no longer lose the escrowed items — the next claim (respawn or join)
 * empties the escrow in one section commit, which is what makes the delivery exact-once.</p>
 */
public final class PlayerProfileDeathEscrowStore {
    private static final String ITEMS_KEY = "death-escrow.items";
    private static final String CREATED_AT_KEY = "death-escrow.created-at";
    private static final int MAX_ITEMS = 32;

    /** Appends encoded item payloads to the escrow. */
    public CompletionStage<Integer> deposit(final UUID playerId,
                                            final List<String> encodedItems,
                                            final long now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(encodedItems, "encodedItems");
        if (encodedItems.isEmpty()) {
            throw new IllegalArgumentException("empty escrow deposit");
        }
        for (final String encoded : encodedItems) {
            if (encoded == null || encoded.isBlank()) {
                throw new IllegalArgumentException("blank escrow item payload");
            }
        }
        if (now < 0L) {
            throw new IllegalArgumentException("negative escrow timestamp");
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.LIFECYCLE, LifecycleSection.class, current -> {
                    final List<String> items = new ArrayList<>(readItems(current));
                    items.addAll(encodedItems);
                    if (items.size() > MAX_ITEMS) {
                        throw new IllegalStateException("death escrow limit exceeded");
                    }
                    final Map<String, Object> extensions = new LinkedHashMap<>(current.extensions());
                    extensions.put(ITEMS_KEY, List.copyOf(items));
                    extensions.put(CREATED_AT_KEY, now);
                    return PlayerProfileService.ConditionalMutation.changed(
                            withExtensions(current, extensions), items.size());
                });
    }

    /** Empties the escrow and returns its content; only one concurrent claimer can win. */
    public CompletionStage<List<String>> claim(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.LIFECYCLE, LifecycleSection.class, current -> {
                    final List<String> items = readItems(current);
                    if (items.isEmpty()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(List.of());
                    }
                    final Map<String, Object> extensions = new LinkedHashMap<>(current.extensions());
                    extensions.remove(ITEMS_KEY);
                    extensions.remove(CREATED_AT_KEY);
                    return PlayerProfileService.ConditionalMutation.changed(
                            withExtensions(current, extensions), items);
                });
    }

    /** Decodes the escrow payloads of one lifecycle section. */
    public static List<String> readItems(final LifecycleSection section) {
        final Object raw = section.extensions().get(ITEMS_KEY);
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalStateException("Invalid death escrow payload: " + raw);
        }
        final List<String> items = new ArrayList<>(list.size());
        for (final Object value : list) {
            if (!(value instanceof String encoded)) {
                throw new IllegalStateException("Invalid death escrow item: " + value);
            }
            items.add(encoded);
        }
        return List.copyOf(items);
    }

    private static LifecycleSection withExtensions(final LifecycleSection base,
                                                   final Map<String, Object> extensions) {
        return new LifecycleSection(base.status(), base.profileCreatedAt(), base.updatedAt(),
                base.lastJoinAt(), base.lastQuitAt(), base.sessionGeneration(), extensions);
    }
}
