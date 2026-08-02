package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.ClassProfile;
import hu.taliann.icesmp.classspec.domain.ProfileStatus;
import hu.taliann.icesmp.classspec.persistence.ClassProfileRepository;
import hu.taliann.icesmp.classspec.persistence.ProfileRepositoryException;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Greenfield load/initialize/flush lifecycle. No legacy read, migration or fallback exists. */
public final class ClassProfileLifecycleService {
    private final ClassProfileRepository repository;
    private final AtomicBoolean accepting=new AtomicBoolean(true);
    private final Object admissionLock=new Object();
    private final Set<CompletableFuture<JoinResult>> joins=ConcurrentHashMap.newKeySet();
    public ClassProfileLifecycleService(ClassProfileRepository repository){this.repository=Objects.requireNonNull(repository);}

    public CompletionStage<JoinResult> join(UUID playerId){
        Objects.requireNonNull(playerId);CompletableFuture<JoinResult> admitted=new CompletableFuture<>();synchronized(admissionLock){if(!accepting.get())return CompletableFuture.completedFuture(JoinResult.blocked("Profile v2 lifecycle stopped",null));joins.add(admitted);}CompletionStage<ClassProfileRepository.LoadResult> load;try{load=repository.load(playerId);}catch(RuntimeException x){finishBlocked(playerId,admitted,x);return admitted;}
        load.thenCompose(result->switch(result.status()){case FOUND->CompletableFuture.completedFuture(validate(result.profile()));case MISSING->initialize(playerId);case QUARANTINED->CompletableFuture.completedFuture(JoinResult.quarantined(result.diagnostic(),result.evidenceId()));}).exceptionally(x->{String d=message(x);repository.blockSession(playerId,d);return JoinResult.blocked(d,repository.cached(playerId).orElse(null));}).whenComplete((result,failure)->{if(failure==null)admitted.complete(result);else admitted.completeExceptionally(failure);joins.remove(admitted);});return admitted;
    }
    private CompletionStage<JoinResult> initialize(UUID id){ClassProfile empty=ClassProfile.empty(id,0L);return repository.save(id,ClassProfileRepository.MISSING_REVISION,empty).handle((saved,failure)->new Init(saved,failure)).thenCompose(done->{if(done.failure==null)return CompletableFuture.completedFuture(JoinResult.ready(done.profile,true));Throwable root=unwrap(done.failure);if(root instanceof ProfileRepositoryException.RevisionConflict)return repository.load(id).thenApply(result->result.status()==ClassProfileRepository.Status.FOUND?validate(result.profile()):JoinResult.blocked("Concurrent initialization winner could not be reloaded",null));return CompletableFuture.failedFuture(root);});}
    private JoinResult validate(ClassProfile p){if(p.status()==ProfileStatus.QUARANTINED)return JoinResult.quarantined(p.diagnostics().quarantineReason(),p.diagnostics().quarantineEvidenceId());if(p.status()!=ProfileStatus.READY||!p.isGameplayUsable())return JoinResult.blocked(p.pendingOperation().map(op->"pending durable operation requires recovery: "+op.operationId()).orElseGet(()->p.diagnostics().sessionBlockReason().isBlank()?"profile requires explicit review":p.diagnostics().sessionBlockReason()),p);return JoinResult.ready(p,false);}
    public CompletionStage<Void> logout(UUID id){Objects.requireNonNull(id);return repository.flush(id).whenComplete((v,x)->{if(x==null)repository.invalidate(id);});}
    public CompletionStage<Void> prepareDisable(){CompletableFuture<?>[] pending;synchronized(admissionLock){accepting.set(false);pending=joins.toArray(CompletableFuture[]::new);}return CompletableFuture.allOf(pending).thenCompose(v->repository.flushAll());}
    public boolean accepting(){return accepting.get();}
    private void finishBlocked(UUID id,CompletableFuture<JoinResult> result,Throwable failure){String d=message(failure);repository.blockSession(id,d);result.complete(JoinResult.blocked(d,repository.cached(id).orElse(null)));joins.remove(result);}
    public record JoinResult(Status status,ClassProfile profile,String diagnostic,String evidenceId,boolean initialized){public JoinResult{Objects.requireNonNull(status);diagnostic=diagnostic==null?"":diagnostic;evidenceId=evidenceId==null?"":evidenceId;if(status==Status.READY&&profile==null)throw new IllegalArgumentException("READY requires profile");}public static JoinResult ready(ClassProfile p,boolean initialized){return new JoinResult(Status.READY,Objects.requireNonNull(p),"","",initialized);}public static JoinResult blocked(String d,ClassProfile p){return new JoinResult(Status.BLOCKED,p,d,"",false);}public static JoinResult quarantined(String d,String e){return new JoinResult(Status.QUARANTINED,null,d,e,false);}public Optional<ClassProfile> profileOptional(){return Optional.ofNullable(profile);}}
    public enum Status{READY,BLOCKED,QUARANTINED}
    private record Init(ClassProfile profile,Throwable failure){}
    private static Throwable unwrap(Throwable x){Throwable c=x;while((c instanceof java.util.concurrent.CompletionException||c instanceof java.util.concurrent.ExecutionException)&&c.getCause()!=null)c=c.getCause();return c;}private static String message(Throwable x){Throwable c=unwrap(x);return c.getMessage()==null?c.getClass().getSimpleName():c.getMessage();}
}
