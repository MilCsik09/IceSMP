package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.*;

/** Native history acknowledgement and exact owner-inventory projection; never a natural-history claim. */
public record TrashDeveloperReceipt(UUID operationId, UUID actor, Kind kind, UUID instanceId,
        String baseId, String beforePhase, long beforeRevision, String afterPhase, long afterRevision,
        long recordedAt, List<SlotChange> slots, boolean projectionObserved, Optional<UUID> reverses) {
    public static final int MAX_PROJECTION_CHARACTERS = 65_536;

    public TrashDeveloperReceipt(UUID operationId, UUID actor, Kind kind, UUID instanceId,
            String baseId, String beforePhase, long beforeRevision, String afterPhase, long afterRevision,
            long recordedAt, List<SlotChange> slots, boolean projectionObserved) {
        this(operationId, actor, kind, instanceId, baseId, beforePhase, beforeRevision, afterPhase,
                afterRevision, recordedAt, slots, projectionObserved, Optional.empty());
    }

    public TrashDeveloperReceipt {
        Objects.requireNonNull(operationId); Objects.requireNonNull(actor); Objects.requireNonNull(kind);
        Objects.requireNonNull(instanceId); Objects.requireNonNull(baseId);
        Objects.requireNonNull(beforePhase); Objects.requireNonNull(afterPhase);
        Objects.requireNonNull(reverses);
        slots = List.copyOf(slots);
        if (!HiddenDevAuthority.isDeveloper(actor) || baseId.isBlank() || beforePhase.isBlank()
                || afterPhase.isBlank() || beforeRevision < 0 || afterRevision <= beforeRevision || recordedAt < 1
                || slots.isEmpty() || slots.size() > 2 || slots.stream().map(SlotChange::slot).distinct().count() != slots.size()
                || slots.stream().noneMatch(slot -> !slot.before().equals(slot.after()))
                || slots.stream().mapToInt(slot -> slot.before().length() + slot.after().length()).sum() > MAX_PROJECTION_CHARACTERS
                || kind == Kind.SANDBOX_COPY && beforeRevision != 0
                || kind == Kind.INDIVIDUALIZE && !beforePhase.equals(afterPhase)
                || kind == Kind.REPAIR && !beforePhase.equals(afterPhase)
                || (kind == Kind.REVERT) != reverses.isPresent()
                || reverses.filter(operationId::equals).isPresent()
                || kind == Kind.TRANSITION_SUCCESS && (!beforePhase.equals("base") || afterPhase.equals("base"))) {
            throw new IllegalArgumentException("Invalid native Trash developer receipt");
        }
    }

    public TrashDeveloperReceipt withObservedProjection() {
        return new TrashDeveloperReceipt(operationId, actor, kind, instanceId, baseId, beforePhase,
                beforeRevision, afterPhase, afterRevision, recordedAt, slots, true, reverses);
    }

    public int projectionCharacters() {
        return slots.stream().mapToInt(slot -> slot.before().length() + slot.after().length()).sum();
    }

    public enum Kind {
        INDIVIDUALIZE(TrashHistoryEvent.DEV_INDIVIDUALIZED),
        TRANSITION_SUCCESS(TrashHistoryEvent.DEV_TRANSITIONED),
        REPAIR(TrashHistoryEvent.DEV_REPAIRED),
        SANDBOX_COPY(TrashHistoryEvent.DEV_PROTOTYPED),
        REVERT(TrashHistoryEvent.DEV_REVERTED);
        private final TrashHistoryEvent event;
        Kind(TrashHistoryEvent event) { this.event = event; }
        public TrashHistoryEvent event() { return event; }
    }

    /** Empty string represents an empty native inventory slot; other values are canonical native bytes. */
    public record SlotChange(int slot, String before, String after) {
        public SlotChange {
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            if (slot < 0 || slot > 40) throw new IllegalArgumentException("Invalid Trash projection slot");
            validateBytes(before); validateBytes(after);
        }
        private static void validateBytes(String encoded) {
            if (encoded.isEmpty()) return;
            if (encoded.length() > MAX_PROJECTION_CHARACTERS
                    || !Base64.getEncoder().encodeToString(Base64.getDecoder().decode(encoded)).equals(encoded)) {
                throw new IllegalArgumentException("Invalid native item encoding");
            }
        }
    }
}
