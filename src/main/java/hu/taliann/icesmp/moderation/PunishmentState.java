package hu.taliann.icesmp.moderation;

/** Durable lifecycle state of a punishment ledger entry. */
public enum PunishmentState {
    ACTIVE,
    RECORDED,
    REVOKED,
    EXPIRED
}
