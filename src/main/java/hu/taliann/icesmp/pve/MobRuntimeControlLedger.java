package hu.taliann.icesmp.pve;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Entity-owner-only evidence for one-shot native controls; absence after detach/restart leaves outcome unknown. */
public final class MobRuntimeControlLedger {
    public enum Kind { FORCE_ABILITY, REFRESH }
    public record Request(UUID operationId, Kind kind, String abilityId, String expectedStamp) {
        public Request {
            Objects.requireNonNull(operationId); Objects.requireNonNull(kind); stamp(expectedStamp);
            abilityId = Objects.requireNonNull(abilityId);
            if (kind == Kind.FORCE_ABILITY) MobAbilityDefinition.id(abilityId, "ability");
            else if (!abilityId.isEmpty()) throw new IllegalArgumentException("Refresh has no ability");
        }
    }
    public record Accepted(Request request, String afterStamp, long acceptedAt) {
        public Accepted {
            Objects.requireNonNull(request); stamp(afterStamp);
            final var before = request.expectedStamp().split("/"); final var after = afterStamp.split("/");
            if (acceptedAt < 1 || !before[0].equals(after[0]) || Long.parseLong(before[1]) == Long.MAX_VALUE
                    || Long.parseLong(after[1]) != Long.parseLong(before[1]) + 1 || Long.parseLong(after[2]) < Long.parseLong(before[2]))
                throw new IllegalArgumentException("Control acceptance revision or timestamp");
        }
    }
    public record View(String stamp, Map<UUID, Accepted> accepted) {
        public View {
            MobRuntimeControlLedger.stamp(stamp); accepted = Map.copyOf(accepted);
            if (accepted.size() > 32 || accepted.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue().request().operationId())))
                throw new IllegalArgumentException("Control evidence bounds or identity");
            final var current = stamp.split("/");
            for (final var value : accepted.values()) {
                final var after = value.afterStamp().split("/");
                if (!after[0].equals(current[0]) || Long.parseLong(after[1]) > Long.parseLong(current[1]) || Long.parseLong(after[2]) > Long.parseLong(current[2]))
                    throw new IllegalArgumentException("Control evidence outside runtime generation/revision");
            }
        }
    }
    private final UUID generation = UUID.randomUUID();
    private final LinkedHashMap<UUID, Accepted> accepted = new LinkedHashMap<>();
    private long revision;
    private boolean applying;
    private String token(long castEpoch) {
        if (castEpoch < 0) throw new Rejected("RUNTIME_REVISION_EXHAUSTED");
        return generation + "/" + revision + "/" + castEpoch;
    }
    public View view(long castEpoch) { return new View(token(castEpoch), accepted); }
    public Accepted execute(Request request, LongSupplier castEpoch, BooleanSupplier mutation, LongSupplier clock) {
        Objects.requireNonNull(request); Objects.requireNonNull(castEpoch); Objects.requireNonNull(mutation); Objects.requireNonNull(clock);
        final Accepted previous = accepted.get(request.operationId());
        if (previous != null) {
            if (!previous.request().equals(request)) throw new Rejected("CONTROL_OPERATION_CONFLICT");
            return previous;
        }
        if (applying) throw new Rejected("CONTROL_BUSY");
        if (!request.expectedStamp().equals(token(castEpoch.getAsLong()))) throw new Rejected("CONFLICT");
        if (revision == Long.MAX_VALUE) throw new Rejected("RUNTIME_REVISION_EXHAUSTED");
        // An entered attempt may change native targeting/cast state before reporting failure.
        // Advance first so an absent acceptance can never be mistaken for an untouched runtime.
        revision++;
        applying = true;
        try {
            if (!mutation.getAsBoolean()) throw new Rejected("CONTROL_UNAVAILABLE");
            final Accepted result = new Accepted(request, token(castEpoch.getAsLong()), Math.max(1, clock.getAsLong()));
            accepted.put(request.operationId(), result);
            if (accepted.size() > 32) accepted.remove(accepted.keySet().iterator().next());
            return result;
        } finally { applying = false; }
    }
    public static String stamp(String value) {
        if (value == null || value.length() > 80) throw new IllegalArgumentException("Control stamp");
        final String[] parts = value.split("/", -1);
        if (parts.length != 3 || !UUID.fromString(parts[0]).toString().equals(parts[0])
                || !parts[1].matches("0|[1-9][0-9]{0,18}") || !parts[2].matches("0|[1-9][0-9]{0,18}")
                || Long.parseLong(parts[1]) < 0 || Long.parseLong(parts[2]) < 0) throw new IllegalArgumentException("Control stamp");
        return value;
    }
    public static final class Rejected extends IllegalStateException {
        private final String code;
        public Rejected(String code) { super(code); this.code = code; }
        public String code() { return code; }
    }
}
