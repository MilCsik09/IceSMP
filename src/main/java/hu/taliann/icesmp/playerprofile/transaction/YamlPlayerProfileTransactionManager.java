package hu.taliann.icesmp.playerprofile.transaction;

import hu.taliann.icesmp.playerprofile.domain.*;
import hu.taliann.icesmp.playerprofile.domain.section.OperationSection;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import java.time.*;import java.util.*;import java.util.concurrent.*;

/** Cross-section WAL transaction adapter. SQL can replace this without changing callers. */
public final class YamlPlayerProfileTransactionManager implements PlayerProfileTransactionManager {
    private static final int MAX_OPERATION_RECEIPTS = 512;
    private final YamlPlayerProfileRepository repository;private final Clock clock;
    public YamlPlayerProfileTransactionManager(YamlPlayerProfileRepository repository){this(repository,Clock.systemUTC());}
    public YamlPlayerProfileTransactionManager(YamlPlayerProfileRepository repository,Clock clock){this.repository=Objects.requireNonNull(repository);this.clock=Objects.requireNonNull(clock);}
    @Override public <T> CompletionStage<T> execute(UUID playerId,ProfileTransactionWork<T> work){Objects.requireNonNull(playerId);Objects.requireNonNull(work);return repository.loadSnapshot(playerId).thenCompose(snapshot->{TransactionPlan<T> plan=Objects.requireNonNull(work.prepare(snapshot));PlayerProfileOperation existing=snapshot.operations().value().operations().get(plan.operationId());if(existing!=null){if(!existing.fingerprint().equals(plan.fingerprint())||!existing.type().equals(plan.type()))return CompletableFuture.failedFuture(new IllegalStateException("operation id reused with different parameters"));if(existing.status()==PlayerProfileOperation.Status.COMMITTED)return CompletableFuture.completedFuture(plan.result());return CompletableFuture.failedFuture(new IllegalStateException("operation requires recovery"));}
        ProfileSectionSnapshot<OperationSection> ops=snapshot.operations();Map<String,PlayerProfileOperation> ledger=new LinkedHashMap<>(ops.value().operations());if(ledger.size()>=MAX_OPERATION_RECEIPTS){Optional<Map.Entry<String,PlayerProfileOperation>> evictable=ledger.entrySet().stream().filter(e->e.getValue().status()!=PlayerProfileOperation.Status.PREPARED).min(Comparator.comparing(e->e.getValue().updatedAt()));if(evictable.isEmpty())return CompletableFuture.failedFuture(new PlayerProfileTransactionManager.LedgerSaturated());ledger.remove(evictable.orElseThrow().getKey());}
        EnumMap<ProfileSectionId,ProfileSectionSnapshot<?>> replacements=new EnumMap<>(ProfileSectionId.class);Instant now=clock.instant();for(SectionUpdate update:plan.updates()){ProfileSectionSnapshot<?> current=snapshot.section(update.section()).orElseThrow();if(current.revision()!=update.expectedRevision())return CompletableFuture.failedFuture(new IllegalStateException("stale section revision"));replacements.put(update.section(),new ProfileSectionSnapshot<>(update.section(),update.section().currentSchema(),Math.addExact(current.revision(),1L),now,update.next(),SectionHealth.healthy(),current.extensions()));}
        ledger.put(plan.operationId(),new PlayerProfileOperation(plan.operationId(),plan.type(),PlayerProfileOperation.Status.COMMITTED,plan.fingerprint(),now,now,Map.of()));OperationSection nextOps=new OperationSection(ledger,ops.value().extensions());replacements.put(ProfileSectionId.OPERATIONS,new ProfileSectionSnapshot<>(ProfileSectionId.OPERATIONS,ops.schema(),Math.addExact(ops.revision(),1L),now,nextOps,SectionHealth.healthy(),ops.extensions()));
        return repository.commitSections(playerId,snapshot.profileRevision(),replacements,plan.operationId(),plan.fingerprint()).thenApply(ignored->plan.result());});}
}
