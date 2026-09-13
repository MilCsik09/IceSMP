package hu.taliann.icesmp.dev.weaver.persistence;

import java.util.Map;

/** Invoked only by the single journal IO executor; implementations provide actual durable replacement. */
public interface WeaverJournalStorage {
    WeaverJournalState readState() throws Exception;
    Map<String, WeaverAuditEntry> readAudit() throws Exception;
    default void validateStateCapacity(final WeaverJournalState state) { }
    default void validateAuditCapacity(final Map<String, WeaverAuditEntry> audit) { }
    void writeState(WeaverJournalState state) throws Exception;
    void writeAudit(Map<String, WeaverAuditEntry> audit) throws Exception;
}
