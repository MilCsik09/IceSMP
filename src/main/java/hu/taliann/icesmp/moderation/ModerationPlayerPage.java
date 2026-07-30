package hu.taliann.icesmp.moderation;

/** Immutable, overflow-safe slice metadata for the moderation player list. */
public record ModerationPlayerPage(
        int index,
        int pageCount,
        int fromInclusive,
        int toExclusive) {

    public static final int PAGE_SIZE = 45;

    public static ModerationPlayerPage of(final int requestedIndex, final int itemCount) {
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount must not be negative");
        }
        final int pageCount = itemCount == 0 ? 1 : ((itemCount - 1) / PAGE_SIZE) + 1;
        final int index = Math.max(0, Math.min(requestedIndex, pageCount - 1));
        final int fromInclusive = index * PAGE_SIZE;
        final int toExclusive = fromInclusive + Math.min(PAGE_SIZE, itemCount - fromInclusive);
        return new ModerationPlayerPage(index, pageCount, fromInclusive, toExclusive);
    }

    public boolean hasPrevious() {
        return index > 0;
    }

    public boolean hasNext() {
        return index + 1 < pageCount;
    }
}
