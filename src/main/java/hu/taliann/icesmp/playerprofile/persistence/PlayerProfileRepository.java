package hu.taliann.icesmp.playerprofile.persistence;

import hu.taliann.icesmp.playerprofile.domain.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;

public interface PlayerProfileRepository {
    long MISSING_REVISION=-1L;
    CompletionStage<PlayerProfileSnapshot> load(UUID playerId);
    /** Read-only lookup. Missing profiles are not initialized. */
    CompletionStage<Optional<PlayerProfileSnapshot>> find(UUID playerId);
    /** Storage-independent case-insensitive identity lookup. */
    CompletionStage<Optional<PlayerProfileSnapshot>> findByName(String playerName);
    default CompletionStage<PlayerProfileSnapshot> loadSnapshot(UUID playerId){return load(playerId);}
    CompletionStage<Optional<ProfileSectionSnapshot<?>>> loadSection(UUID playerId, ProfileSectionId section);
    CompletionStage<SectionSaveResult> saveSection(UUID playerId, ProfileSectionId section, long expectedRevision, ProfileSectionSnapshot<?> next);
    CompletionStage<SectionSaveResult> saveSection(UUID playerId, ProfileSectionId section, long expectedRevision, long expectedGeneration, ProfileSectionSnapshot<?> next);
    CompletionStage<QuarantineResult> quarantineSection(UUID playerId,ProfileSectionId section,byte[] originalPayload,String reason);
    CompletionStage<RecoveryResult> recoverSection(UUID playerId,ProfileSectionId section,String evidenceId,String auditId);
    Optional<PlayerProfileSnapshot> cached(UUID playerId);
    void invalidate(UUID playerId);
    CompletionStage<Void> flush(UUID playerId);
    CompletionStage<Void> flushAll();
    CompletionStage<ShutdownResult> shutdown(Duration timeout);

    record SectionSaveResult(Status status,PlayerProfileSnapshot snapshot,String detail,String evidenceId){
        public SectionSaveResult{Objects.requireNonNull(status);detail=detail==null?"":detail;evidenceId=evidenceId==null?"":evidenceId;}
        public enum Status{COMMITTED,STALE_REVISION,STALE_GENERATION,SECTION_QUARANTINED,REJECTED}
    }
    record QuarantineResult(PlayerProfileSnapshot snapshot,String evidenceId,String detail){public QuarantineResult{Objects.requireNonNull(snapshot);evidenceId=Objects.requireNonNull(evidenceId);detail=detail==null?"":detail;}}
    record RecoveryResult(PlayerProfileSnapshot snapshot,String evidenceId,String auditId,boolean idempotent){public RecoveryResult{Objects.requireNonNull(snapshot);Objects.requireNonNull(evidenceId);Objects.requireNonNull(auditId);}}
    record ShutdownResult(boolean drained,int pendingOperations,String detail){public ShutdownResult{if(pendingOperations<0)throw new IllegalArgumentException("negative pending operations");detail=detail==null?"":detail;}}
}
