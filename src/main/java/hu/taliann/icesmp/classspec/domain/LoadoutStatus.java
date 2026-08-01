package hu.taliann.icesmp.classspec.domain;

/** Durable lifecycle state of one specialization loadout. */
public enum LoadoutStatus {
    EMPTY,
    ACTIVE,
    INACTIVE,
    SEALED,
    MIGRATION_REVIEW
}
