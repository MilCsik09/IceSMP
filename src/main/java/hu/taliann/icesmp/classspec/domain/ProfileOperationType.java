package hu.taliann.icesmp.classspec.domain;

/** Durable operations whose idempotency must survive restart. */
public enum ProfileOperationType {
    SOULFORGE_UPGRADE,
    SOUL_SHARD_MUTATION,
    RESPEC,
    ADMIN_RECOVERY,
    COMPANION_MUTATION,
    CLASS_EXPERIENCE
}
