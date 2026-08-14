package hu.taliann.icesmp.classspec.persistence;

import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ClassSpecSectionRepository {
    long MISSING_REVISION=-1L;
    CompletionStage<LoadResult> load(UUID playerId);
    CompletionStage<ClassSpecSection> save(UUID playerId,long expectedRevision,ClassSpecSection nextProfile);
    CompletionStage<QuarantineRecord> quarantine(UUID playerId,byte[] originalPayload,String reason);
    CompletionStage<ClassSpecSection> recover(UUID playerId,String evidenceId,String auditId);
    CompletionStage<Void> flush(UUID playerId);
    CompletionStage<Void> flushAll();
    CompletionStage<ShutdownResult> shutdown(Duration timeout);
    void invalidate(UUID playerId);
    Optional<ClassSpecSection> cached(UUID playerId);
    Optional<String> sessionBlockReason(UUID playerId);
    Optional<String> quarantineReason(UUID playerId);
    void blockSession(UUID playerId,String reason);

    record LoadResult(Status status,ClassSpecSection profile,String diagnostic,String evidenceId){
        public LoadResult{Objects.requireNonNull(status);diagnostic=diagnostic==null?"":diagnostic;evidenceId=evidenceId==null?"":evidenceId;if(status==Status.FOUND&&profile==null)throw new IllegalArgumentException("FOUND requires profile");if(status!=Status.FOUND&&profile!=null)throw new IllegalArgumentException("Only FOUND exposes profile");if(status==Status.QUARANTINED&&evidenceId.isBlank())throw new IllegalArgumentException("Quarantine requires evidence");}
        public static LoadResult missing(){return new LoadResult(Status.MISSING,null,"","");}
        public static LoadResult found(ClassSpecSection p){return new LoadResult(Status.FOUND,Objects.requireNonNull(p),"","");}
        public static LoadResult quarantined(String d,String e){return new LoadResult(Status.QUARANTINED,null,d,e);}
    }
    enum Status{MISSING,FOUND,QUARANTINED}
    record QuarantineRecord(UUID playerId,long createdAtEpochMillis,String reason,String evidenceId,String fileName){public QuarantineRecord{Objects.requireNonNull(playerId);reason=reason==null?"":reason;evidenceId=evidenceId==null?"":evidenceId;fileName=fileName==null?"":fileName;}}
    record ShutdownResult(boolean drained,int pendingOperations,String detail){public ShutdownResult{detail=detail==null?"":detail;if(pendingOperations<0)throw new IllegalArgumentException("negative pending count");}}
}
