package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Sole Profile v2 facade. Reads are cache-only; mutations are durable CAS operations. */
public interface ClassSpecProfileGateway {
    boolean isSessionReady(UUID playerId);
    Optional<UUID> currentSessionToken(UUID playerId);
    boolean isCurrentSession(UUID playerId,UUID sessionToken);
    void beginSessionActivation(UUID playerId,UUID sessionToken);
    void completeSessionActivation(UUID playerId,UUID sessionToken);
    void cancelSessionActivation(UUID playerId,UUID sessionToken);
    Optional<ClassProfile> currentProfile(UUID playerId);
    Optional<String> activeSpecId(UUID playerId);
    Optional<String> activeMechanic(UUID playerId,String key);
    Optional<CompanionProfile> activeCompanion(UUID playerId);
    Optional<ProfileOperation> operation(UUID playerId,String operationId);
    ProfileDiagnostic diagnostic(UUID playerId);
    void blockSession(UUID playerId,String reason);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> select(UUID playerId,SelectRequest request);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> reset(UUID playerId,ResetRequest request);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> seal(UUID playerId,SealRequest request);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> reconcile(UUID playerId,ReconcileRequest request);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> assignClass(UUID playerId,ClassAssignmentRequest request);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> mutateClassExperience(UUID playerId,ClassExperienceRequest request);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> incrementSoulforge(UUID playerId,String branch,int shardCost,String operationId);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> mutateSoulShards(UUID playerId,int delta,String operationId);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> mutateCompanion(UUID playerId,CompanionMutationRequest request);
    CompletionStage<RecoveryResult> recoverQuarantined(UUID playerId,String evidenceId,String auditId);
    Optional<String> quarantineEvidenceId(UUID playerId);
    CompletionStage<Void> awaitPlayerMutations(UUID playerId);
    default CompletionStage<Void> prepareShutdown(){return CompletableFuture.completedFuture(null);}
    void clearSession(UUID playerId);

    record ClassAssignmentRequest(String classId,int classLevel,int classExperience,String operationId){
        public ClassAssignmentRequest{classId=requireId(classId,"classId");operationId=requireId(operationId,"operationId");if(classLevel<1)throw new IllegalArgumentException("classLevel must be positive");if(classExperience<0)throw new IllegalArgumentException("classExperience cannot be negative");}
        public ClassAssignmentRequest(String classId,int classLevel,String operationId){this(classId,classLevel,0,operationId);}
    }
    record ClassExperienceRequest(Mode mode,int value,int baseXp,int incrementPerLevel,String operationId){
        public ClassExperienceRequest{Objects.requireNonNull(mode);operationId=requireId(operationId,"operationId");if(value<0)throw new IllegalArgumentException("class experience value cannot be negative");if(baseXp<1||incrementPerLevel<0)throw new IllegalArgumentException("invalid class level curve");}
        public enum Mode{ADD,SET}
    }
    record SelectRequest(String specializationId,LoadoutSlot slot,GateSnapshot gates){public SelectRequest{specializationId=requireId(specializationId,"specializationId");Objects.requireNonNull(slot);Objects.requireNonNull(gates);}}
    record ResetRequest(ResetMode mode,Optional<LoadoutSlot> slot,String operationId,String amount,String currencyId){
        public ResetRequest{Objects.requireNonNull(mode);slot=slot==null?Optional.empty():slot;operationId=requireId(operationId,"operationId");amount=amount==null?"0":amount.trim();currencyId=ClassSpecCatalog.normalize(currencyId);if(mode==ResetMode.LOADOUT_RESPEC&&slot.isEmpty())throw new IllegalArgumentException("Loadout respec requires a slot");if(mode==ResetMode.ADMIN_CLASS&&slot.isPresent())throw new IllegalArgumentException("Admin reset cannot target one slot");}
        public ResetRequest(ResetMode mode,Optional<LoadoutSlot> slot,String operationId){this(mode,slot,operationId,"0","");}
    }
    record SealRequest(LoadoutSlot slot,SealReason reason){public SealRequest{Objects.requireNonNull(slot);Objects.requireNonNull(reason);}}
    record ReconcileRequest(Map<LoadoutSlot,GateSnapshot> gatesBySlot){public ReconcileRequest{Objects.requireNonNull(gatesBySlot);EnumMap<LoadoutSlot,GateSnapshot> copy=new EnumMap<>(LoadoutSlot.class);gatesBySlot.forEach((k,v)->copy.put(Objects.requireNonNull(k),Objects.requireNonNull(v)));gatesBySlot=Collections.unmodifiableMap(copy);}}
    record CompanionMutationRequest(LoadoutSlot slot,Kind kind,UUID companionId,CompanionProfile companion,String text,int level,long experience,long resummonAtEpochMillis,List<String> equipment,Map<String,String> state,String operationId){
        public CompanionMutationRequest{Objects.requireNonNull(slot);Objects.requireNonNull(kind);text=text==null?"":text.trim();equipment=equipment==null?List.of():List.copyOf(equipment);state=state==null?Map.of():Map.copyOf(state);operationId=requireId(operationId,"operationId");if(level<0||experience<0||resummonAtEpochMillis<0)throw new IllegalArgumentException("negative companion progress/timestamp");}
        public enum Kind{ADD,REMOVE,RENAME,STANCE,PROGRESS,EQUIPMENT,STATE,RESPAWN_AT,SET_ACTIVE,DISMISS}
    }

    record RecoveryResult(ClassProfile profile,String evidenceId,String auditId,boolean idempotent){
        public RecoveryResult{Objects.requireNonNull(profile);evidenceId=requireId(evidenceId,"evidenceId");auditId=requireId(auditId,"auditId");}
    }
    enum ResetMode{LOADOUT_RESPEC,ADMIN_CLASS}
    private static String requireId(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" cannot be blank");return value.trim();}
}
