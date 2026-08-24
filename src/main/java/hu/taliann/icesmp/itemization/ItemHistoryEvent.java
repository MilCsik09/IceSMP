package hu.taliann.icesmp.itemization;

import java.util.Objects;

public record ItemHistoryEvent(Type type, long occurredAt, String detail) {

    public enum Type {
        CREATED,
        CRAFTED,
        BOSS_DROP,
        FOUND,
        QUEST_REWARD,
        TRADED,
        MARKET_SOLD,
        RUNE_INSERTED,
        RUNE_REMOVED,
        RUNE_CHANGED,
        REROLLED,
        TEMPLATE_MIGRATED,
        ASCENDED,
        SIGNATURE_UPGRADED,
        SALVAGED,
        DESTROYED
    }

    public ItemHistoryEvent {
        Objects.requireNonNull(type, "type");
        if (occurredAt < 0L) {
            throw new IllegalArgumentException("negative item history timestamp");
        }
        detail = detail == null ? "" : detail.trim();
        if (detail.length() > 128) {
            throw new IllegalArgumentException("item history detail is too long");
        }
    }
}
