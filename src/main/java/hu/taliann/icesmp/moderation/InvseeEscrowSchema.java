package hu.taliann.icesmp.moderation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dependency-free structural validation for invsee-escrow.yml. */
public final class InvseeEscrowSchema {
    public static final int VERSION = 1;

    public record Entry(UUID playerId, List<?> payloads) {
        public Entry {
            payloads = List.copyOf(payloads);
        }
    }

    private InvseeEscrowSchema() {
    }

    public static List<Entry> validate(final Set<String> rootKeys, final Object schemaValue,
                                       final Map<String, ?> returns, final int maxPlayers,
                                       final int maxItems) {
        if (rootKeys == null || !Set.of("schema-version", "returns").containsAll(rootKeys)) {
            throw new IllegalArgumentException("unknown escrow root key");
        }
        final int schema = StrictYamlNumber.requireInt(schemaValue, "schema-version");
        if (schema != VERSION) {
            throw new IllegalArgumentException("unsupported escrow schema-version: " + schema);
        }
        if (maxPlayers < 0 || maxItems < 0) {
            throw new IllegalArgumentException("escrow limits must be non-negative");
        }
        if (returns == null) {
            return List.of();
        }
        if (returns.size() > maxPlayers) {
            throw new IllegalArgumentException("too many escrow players");
        }
        final List<Entry> entries = new ArrayList<>(returns.size());
        final Set<UUID> normalized = new HashSet<>();
        long total = 0L;
        for (final Map.Entry<String, ?> rawEntry : returns.entrySet()) {
            final UUID playerId;
            try {
                playerId = UUID.fromString(rawEntry.getKey());
            } catch (final RuntimeException invalid) {
                throw new IllegalArgumentException("invalid escrow player UUID: " + rawEntry.getKey(), invalid);
            }
            if (!normalized.add(playerId)) {
                throw new IllegalArgumentException("duplicate normalized escrow player UUID: " + rawEntry.getKey());
            }
            if (!(rawEntry.getValue() instanceof List<?> payloads) || payloads.isEmpty()) {
                throw new IllegalArgumentException("escrow entry must be a non-empty payload list: " + rawEntry.getKey());
            }
            for (final Object payload : payloads) {
                if (payload == null) {
                    throw new IllegalArgumentException("escrow entry contains a null payload: " + rawEntry.getKey());
                }
                total = Math.addExact(total, 1L);
                if (total > maxItems) {
                    throw new IllegalArgumentException("too many escrow items");
                }
            }
            entries.add(new Entry(playerId, payloads));
        }
        return List.copyOf(entries);
    }
}
