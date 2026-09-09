package hu.taliann.icesmp.trash;

/** Significant server-side provenance events; none of these names are player-facing. */
public enum TrashHistoryEvent {
    CREATED_FISHING,
    CREATED_MOB_DROP,
    CREATED_AMBIENT,
    OWNER_OBSERVED,
    OWNER_COUNT_MILESTONE,
    VENDOR_SOLD,
    VENDOR_RECYCLED,
    TRANSFORMED,
    REPAIRED,
    HELD_BY_KING,
    PRESENT_AT_PLAYER_DEATH,
    NETHER_TRANSIT,
    WORLD_EVENT_PRESENT,
    ACTIVATED,
    DEV_INDIVIDUALIZED,
    DEV_TRANSITIONED,
    DEV_REPAIRED,
    DEV_PROTOTYPED;

    public boolean developer() {
        return this == DEV_INDIVIDUALIZED || this == DEV_TRANSITIONED
                || this == DEV_REPAIRED || this == DEV_PROTOTYPED;
    }
}
