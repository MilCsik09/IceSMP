package hu.taliann.icesmp.classspec.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable, fully validated Profile v2 aggregate and sole class/spec authority. */
public final class ClassProfile {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_CLASS_LEVEL = 50;
    public static final int MAX_OPERATION_RECEIPTS = 64;

    private final UUID ownerId;
    private final int schemaVersion;
    private final long revision;
    private final ProfileStatus status;
    private final String primaryClassId;
    private final int classLevel;
    private final int classExperience;
    private final LoadoutSlot activeSlot;
    private final boolean secondSpecUnlocked;
    private final List<ClassLoadout> loadouts;
    private final Map<String, ProfileOperation> operations;
    private final ProfileDiagnostics diagnostics;

    private ClassProfile(final Builder b) {
        ownerId = Objects.requireNonNull(b.ownerId, "ownerId");
        schemaVersion=b.schemaVersion; revision=b.revision; status=Objects.requireNonNull(b.status,"status");
        primaryClassId=ClassSpecCatalog.normalize(b.primaryClassId); classLevel=b.classLevel; classExperience=b.classExperience; activeSlot=b.activeSlot;
        secondSpecUnlocked=b.secondSpecUnlocked; loadouts=List.copyOf(b.loadouts); operations=copyOperations(b.operations);
        diagnostics=Objects.requireNonNull(b.diagnostics,"diagnostics"); validate();
    }
    public static Builder builder(UUID ownerId){return new Builder(ownerId);}
    public static ClassProfile empty(UUID ownerId,long revision){return builder(ownerId).revision(revision).build();}
    public UUID ownerId(){return ownerId;} public int schemaVersion(){return schemaVersion;} public long revision(){return revision;}
    public ProfileStatus status(){return status;} public String primaryClassId(){return primaryClassId;} public int classLevel(){return classLevel;} public int classExperience(){return classExperience;}
    public LoadoutSlot activeSlot(){return activeSlot;} public boolean secondSpecUnlocked(){return secondSpecUnlocked;} public List<ClassLoadout> loadouts(){return loadouts;}
    public ClassLoadout loadout(LoadoutSlot slot){return loadouts.get(Objects.requireNonNull(slot,"slot").index());}
    public Map<String,ProfileOperation> operations(){return operations;}
    public Optional<ProfileOperation> operation(String id){return Optional.ofNullable(operations.get(cleanOperationId(id)));}
    public Optional<ProfileOperation> pendingOperation(){return operations.values().stream().filter(o->o.status()==ProfileOperationStatus.PENDING).findFirst();}
    public ProfileDiagnostics diagnostics(){return diagnostics;}
    public boolean isGameplayUsable(){return status==ProfileStatus.READY&&!diagnostics.sessionBlocked()&&pendingOperation().isEmpty();}
    public Builder toBuilder(){requireNormalMutationAllowed();return copyBuilder();}
    public Builder toRecoveryBuilder(){return copyBuilder();}
    public ClassProfile withoutClass(){requireNormalMutationAllowed();return builder(ownerId).revision(NumericGuards.nextRevision(revision)).operations(operations).diagnostics(diagnostics).build();}
    private Builder copyBuilder(){return new Builder(ownerId).schemaVersion(schemaVersion).revision(revision).status(status).primaryClassId(primaryClassId).classLevel(classLevel).classExperience(classExperience).activeSlot(activeSlot).secondSpecUnlocked(secondSpecUnlocked).loadout(LoadoutSlot.FIRST,loadout(LoadoutSlot.FIRST)).loadout(LoadoutSlot.SECOND,loadout(LoadoutSlot.SECOND)).operations(operations).diagnostics(diagnostics);}
    private void requireNormalMutationAllowed(){if(status!=ProfileStatus.READY||diagnostics.sessionBlocked())throw new IllegalStateException("Profile requires explicit recovery: "+status);}
    private void validate(){
        if(schemaVersion!=SCHEMA_VERSION)throw new IllegalArgumentException("Profile v2 requires schema version "+SCHEMA_VERSION);
        if(revision<0L)throw new IllegalArgumentException("Profile revision must be non-negative");
        if(loadouts.size()!=2||loadouts.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Profile v2 requires exactly two loadout slots");
        if(primaryClassId.isEmpty()){
            if(classLevel!=0||classExperience!=0||activeSlot!=null||secondSpecUnlocked||loadouts.stream().anyMatch(l->l.status()!=LoadoutStatus.EMPTY))throw new IllegalArgumentException("A classless profile may not retain class/spec state");
        }else{
            if(!ClassSpecCatalog.isKnownClass(primaryClassId))throw new IllegalArgumentException("Unknown primary class: "+primaryClassId);
            if(classLevel<1||classLevel>MAX_CLASS_LEVEL)throw new IllegalArgumentException("Class level is outside the supported range");
            if(classExperience<0)throw new IllegalArgumentException("Class experience cannot be negative");
        }
        final ClassLoadout first=loadout(LoadoutSlot.FIRST),second=loadout(LoadoutSlot.SECOND);
        if(!secondSpecUnlocked&&second.status()!=LoadoutStatus.EMPTY)throw new IllegalArgumentException("The second loadout is locked");
        if(!first.specializationId().isEmpty()&&first.specializationId().equals(second.specializationId()))throw new IllegalArgumentException("The same specialization may not occupy both slots");
        for(final ClassLoadout l:loadouts)if(l.status()!=LoadoutStatus.EMPTY&&(!ClassSpecCatalog.isKnownSpecialization(l.specializationId())||!ClassSpecCatalog.belongsTo(l.specializationId(),primaryClassId)))throw new IllegalArgumentException("Specialization does not belong to primary class: "+l.specializationId());
        int activeCount=0;for(final ClassLoadout l:loadouts)if(l.status()==LoadoutStatus.ACTIVE)activeCount++;
        if(activeSlot==null){if(activeCount!=0)throw new IllegalArgumentException("An ACTIVE loadout requires activeSlot");}
        else if(activeCount!=1||loadout(activeSlot).status()!=LoadoutStatus.ACTIVE)throw new IllegalArgumentException("activeSlot must identify the only ACTIVE loadout");
        if(status!=ProfileStatus.READY&&(activeSlot!=null||activeCount!=0))throw new IllegalArgumentException("Review/quarantine profiles cannot activate gameplay");
        if(status==ProfileStatus.QUARANTINED&&!diagnostics.hasQuarantineEvidence())throw new IllegalArgumentException("QUARANTINED requires preserved evidence diagnostics");
        if(status!=ProfileStatus.QUARANTINED&&diagnostics.hasQuarantineEvidence())throw new IllegalArgumentException("Only QUARANTINED may retain quarantine diagnostics");
        if(status==ProfileStatus.REVIEW&&diagnostics.recoveryAuditId().isEmpty()&&diagnostics.sessionBlockReason().isEmpty())throw new IllegalArgumentException("REVIEW requires an audit or block reason");
        long pending=operations.values().stream().filter(o->o.status()==ProfileOperationStatus.PENDING).count();if(pending>1L)throw new IllegalArgumentException("Only one durable operation may be pending per profile");
    }
    private static Map<String,ProfileOperation> copyOperations(Map<String,ProfileOperation> source){Objects.requireNonNull(source,"operations");if(source.size()>MAX_OPERATION_RECEIPTS)throw new IllegalArgumentException("Operation receipt limit exceeded");final Map<String,ProfileOperation> result=new LinkedHashMap<>();for(var e:source.entrySet()){final ProfileOperation o=Objects.requireNonNull(e.getValue(),"operation");final String key=cleanOperationId(e.getKey());if(!key.equals(o.operationId()))throw new IllegalArgumentException("Operation key must equal operation id");if(result.putIfAbsent(key,o)!=null)throw new IllegalArgumentException("Duplicate operation id: "+key);}return Map.copyOf(result);}
    private static String cleanOperationId(String id){if(id==null||id.isBlank())throw new IllegalArgumentException("operationId must be non-blank");final String c=id.trim();if(c.length()>ProfileOperation.MAX_ID_LENGTH)throw new IllegalArgumentException("operationId is too long");return c;}
    @Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof ClassProfile p))return false;return schemaVersion==p.schemaVersion&&revision==p.revision&&classLevel==p.classLevel&&classExperience==p.classExperience&&secondSpecUnlocked==p.secondSpecUnlocked&&ownerId.equals(p.ownerId)&&status==p.status&&primaryClassId.equals(p.primaryClassId)&&activeSlot==p.activeSlot&&loadouts.equals(p.loadouts)&&operations.equals(p.operations)&&diagnostics.equals(p.diagnostics);}
    @Override public int hashCode(){return Objects.hash(ownerId,schemaVersion,revision,status,primaryClassId,classLevel,classExperience,activeSlot,secondSpecUnlocked,loadouts,operations,diagnostics);}
    public static final class Builder{
        private final UUID ownerId;private int schemaVersion=SCHEMA_VERSION;private long revision;private ProfileStatus status=ProfileStatus.READY;private String primaryClassId="";private int classLevel;private int classExperience;private LoadoutSlot activeSlot;private boolean secondSpecUnlocked;private final List<ClassLoadout> loadouts=new ArrayList<>(List.of(ClassLoadout.empty(),ClassLoadout.empty()));private final Map<String,ProfileOperation> operations=new LinkedHashMap<>();private ProfileDiagnostics diagnostics=ProfileDiagnostics.none();
        private Builder(UUID ownerId){this.ownerId=Objects.requireNonNull(ownerId,"ownerId");}
        public Builder schemaVersion(int v){schemaVersion=v;return this;}public Builder revision(long v){revision=v;return this;}public Builder status(ProfileStatus v){status=v;return this;}public Builder primaryClassId(String v){primaryClassId=v;return this;}public Builder classLevel(int v){classLevel=v;return this;}public Builder classExperience(int v){classExperience=v;return this;}public Builder activeSlot(LoadoutSlot v){activeSlot=v;return this;}public Builder secondSpecUnlocked(boolean v){secondSpecUnlocked=v;return this;}public Builder loadout(LoadoutSlot slot,ClassLoadout v){loadouts.set(Objects.requireNonNull(slot).index(),Objects.requireNonNull(v));return this;}public Builder operation(ProfileOperation v){operations.put(Objects.requireNonNull(v).operationId(),v);return this;}public Builder operations(Map<String,ProfileOperation> v){operations.clear();operations.putAll(Objects.requireNonNull(v));return this;}public Builder diagnostics(ProfileDiagnostics v){diagnostics=v;return this;}public ClassProfile build(){return new ClassProfile(this);}
    }
}
