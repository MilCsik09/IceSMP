package hu.taliann.icesmp.storage;

import hu.taliann.icesmp.itemization.ItemDeveloperMutation.Kind;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Retained result in the existing item mutation WAL, including both exact physical projections. */
public record ItemDeveloperReceipt(ItemMutationJournal.Entry entry, Kind kind, int sourceSlot,
                                   int targetSlot, UUID resultItemId, long beforeRevision,
                                   long afterRevision, State state, long resolvedAt, Optional<UUID> reverses) {
    public enum State { PENDING, OBSERVED, ABORTED }
    public ItemDeveloperReceipt(ItemMutationJournal.Entry entry, Kind kind, int sourceSlot, int targetSlot,
            UUID resultItemId, long beforeRevision, long afterRevision, State state, long resolvedAt) {
        this(entry, kind, sourceSlot, targetSlot, resultItemId, beforeRevision, afterRevision, state, resolvedAt, Optional.empty());
    }
    public ItemDeveloperReceipt {
        Objects.requireNonNull(entry); Objects.requireNonNull(kind);
        Objects.requireNonNull(resultItemId); Objects.requireNonNull(state); Objects.requireNonNull(reverses);
        if (!HiddenDevAuthority.isDeveloper(entry.playerId()) || !entry.type().equals((reverses.isPresent() ? "DEV_REVERT_" : "DEV_") + kind.name())
                || sourceSlot < 0 || sourceSlot > 40 || targetSlot < 0 || targetSlot > 40
                || entry.beforeInventory().size() != 41 || beforeRevision < 0 || afterRevision < 0
                || entry.beforeInventory().stream().anyMatch(ItemDeveloperReceipt::invalidProjection)
                || entry.afterInventory().stream().anyMatch(ItemDeveloperReceipt::invalidProjection)
                || resolvedAt < 0 || (state == State.PENDING ? resolvedAt != 0 : resolvedAt < entry.createdAt())) {
            throw new IllegalArgumentException("Invalid developer item receipt");
        }
        final boolean deletion = reverses.isPresent() && kind == Kind.CLONE_PROTOTYPE;
        if (reverses.isPresent()) {
            if (reverses.orElseThrow().equals(entry.operationId()) || kind == Kind.REFRESH_PRESENTATION
                    || sourceSlot != targetSlot || !resultItemId.equals(entry.itemId())
                    || (deletion ? targetSlot > 35 || afterRevision != beforeRevision
                        || !entry.afterInventory().get(targetSlot).equals("-") : afterRevision <= beforeRevision)) {
                throw new IllegalArgumentException("Invalid native developer compensation receipt");
            }
        } else if (kind == Kind.CLONE_PROTOTYPE) {
            if (sourceSlot == targetSlot || targetSlot > 35 || resultItemId.equals(entry.itemId())
                    || afterRevision != 0 || !entry.beforeInventory().get(targetSlot).equals("-")
                    || !entry.beforeInventory().get(sourceSlot).equals(entry.afterInventory().get(sourceSlot))) {
                throw new IllegalArgumentException("Invalid native prototype copy receipt");
            }
        } else if (sourceSlot != targetSlot || !resultItemId.equals(entry.itemId())
                || (kind == Kind.REFRESH_PRESENTATION ? afterRevision != beforeRevision
                : afterRevision <= beforeRevision)) {
            throw new IllegalArgumentException("Invalid native developer mutation identity");
        }
        if (entry.beforeInventory().get(sourceSlot).equals("-") || !deletion && entry.afterInventory().get(targetSlot).equals("-")) {
            throw new IllegalArgumentException("Native developer item witness missing");
        }
        if (kind != Kind.REFRESH_PRESENTATION && entry.beforeInventory().equals(entry.afterInventory())) {
            throw new IllegalArgumentException("Missing native item mutation witness");
        }
        for (int slot = 0; slot < 41; slot++) {
            if (slot != targetSlot && !entry.beforeInventory().get(slot).equals(entry.afterInventory().get(slot))) {
                throw new IllegalArgumentException("Developer item mutation changed an unrelated slot");
            }
        }
    }

    private static boolean invalidProjection(String value) {
        if (value.equals("-")) return false;
        if (value.length() > 65_536) return true;
        try { return java.util.Base64.getDecoder().decode(value).length == 0; }
        catch (IllegalArgumentException malformed) { return true; }
    }

    public ItemDeveloperReceipt resolve(State next, long now) {
        if (state != State.PENDING || next == State.PENDING) throw new IllegalStateException("Terminal native result");
        return new ItemDeveloperReceipt(entry, kind, sourceSlot, targetSlot, resultItemId,
                beforeRevision, afterRevision, next, Math.max(entry.createdAt(), now), reverses);
    }
}
