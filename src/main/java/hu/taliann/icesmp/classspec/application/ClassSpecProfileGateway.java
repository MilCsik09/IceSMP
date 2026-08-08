package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.*;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;
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
    Optional<ClassSpecSection> currentProfile(UUID playerId);
    Optional<String> activeSpecId(UUID playerId);
    Optional<String> activeMechanic(UUID playerId,String key);
    Optional<CompanionProfile> activeCompanion(UUID playerId);
    Optional<ProfileOperation> operation(UUID playerId,String operationId);
    ProfileDiagnostic diagnostic(UUID playerId);
    void blockSession(UUID playerId,String reason);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> select(UUID playerId,SelectRequest request);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> switchLoadout(UUID playerId,SwitchRequest request);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> chooseDoctrine(UUID playerId,DoctrineChoiceRequest request);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> contributeMastery(UUID playerId,MasteryContributionRequest request);
    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> setCapstone(UUID playerId,CapstoneRequest request);
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
    record ClassExperienceRequest(Mode mode,int value,int baseXp,int incrementPerLevel,int secondSpecUnlockLevel,String operationId){
        public ClassExperienceRequest{Objects.requireNonNull(mode);operationId=requireId(operationId,"operationId");if(value<0)throw new IllegalArgumentException("class experience value cannot be negative");if(baseXp<1||incrementPerLevel<0)throw new IllegalArgumentException("invalid class level curve");if(secondSpecUnlockLevel<1)throw new IllegalArgumentException("secondSpecUnlockLevel must be positive");}
        public ClassExperienceRequest(Mode mode,int value,int baseXp,int incrementPerLevel,String operationId){this(mode,value,baseXp,incrementPerLevel,Integer.MAX_VALUE,operationId);}
        public enum Mode{ADD,SET}
    }
    record SelectRequest(String specializationId,LoadoutSlot slot,GateSnapshot gates){public SelectRequest{specializationId=requireId(specializationId,"specializationId");Objects.requireNonNull(slot);Objects.requireNonNull(gates);}}
    record SwitchRequest(LoadoutSlot slot){public SwitchRequest{Objects.requireNonNull(slot);}}
    record DoctrineChoiceRequest(LoadoutSlot slot,String tier,String choice){public DoctrineChoiceRequest{Objects.requireNonNull(slot);tier=requireId(tier,"tier");choice=requireId(choice,"choice");}}
    record MasteryContributionRequest(LoadoutSlot slot,long experience,long experiencePerRank){public MasteryContributionRequest{Objects.requireNonNull(slot);if(experience<1L)throw new IllegalArgumentException("mastery experience contribution must be positive");if(experiencePerRank<1L)throw new IllegalArgumentException("mastery experiencePerRank must be positive");}}
    record CapstoneRequest(LoadoutSlot slot,CapstoneStatus status){public CapstoneRequest{Objects.requireNonNull(slot);Objects.requireNonNull(status);}}
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

    record RecoveryResult(ClassSpecSection profile,String evidenceId,String auditId,boolean idempotent){
        public RecoveryResult{Objects.requireNonNull(profile);evidenceId=requireId(evidenceId,"evidenceId");auditId=requireId(auditId,"auditId");}
    }
    enum ResetMode{LOADOUT_RESPEC,ADMIN_CLASS}
    private static String requireId(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" cannot be blank");return value.trim();}
}
