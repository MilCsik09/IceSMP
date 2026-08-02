package hu.taliann.icesmp.classspec.profile;

import hu.taliann.icesmp.classspec.domain.*;
import hu.taliann.icesmp.classspec.persistence.ClassProfileCodec;

import java.util.*;

/** Executable greenfield domain and deterministic ICS2 codec regressions. */
public final class ClassProfileV2RegressionSuite {
    private static int assertions;
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID OTHER=UUID.fromString("00000000-0000-0000-0000-000000000202");
    private ClassProfileV2RegressionSuite(){}
    public static void main(String[] args)throws Exception{
        emptyProfileIsOwnerBound(); domainInvariants(); numericBounds(); codecRoundTrip(); codecOwnerBinding(); codecCorruption(); deterministicCollections();
        System.out.println("ClassProfile v2 domain/codec regression tests passed. assertions="+assertions);
    }
    private static void emptyProfileIsOwnerBound(){
        ClassProfile p=ClassProfile.empty(OWNER,0);check(p.ownerId().equals(OWNER),"owner missing");check(p.revision()==0,"revision zero");check(p.status()==ProfileStatus.READY,"ready");check(p.activeSlot()==null,"inactive");check(p.loadouts().size()==2,"two slots");
        expect(NullPointerException.class,()->ClassProfile.empty(null,0));expect(IllegalArgumentException.class,()->ClassProfile.empty(OWNER,-1));
    }
    private static void domainInvariants(){
        ClassLoadout necro=loadout("necromancer",LoadoutStatus.ACTIVE,null,Map.of(),Map.of());
        ClassProfile active=ClassProfile.builder(OWNER).revision(4).primaryClassId("wizard").classLevel(20).classExperience(900).activeSlot(LoadoutSlot.FIRST).loadout(LoadoutSlot.FIRST,necro).build();
        check(active.isGameplayUsable(),"active profile usable");check(active.loadout(LoadoutSlot.FIRST).specializationId().equals("necromancer"),"spec stored");
        expect(IllegalArgumentException.class,()->ClassProfile.builder(OWNER).primaryClassId("wizard").classLevel(10).activeSlot(LoadoutSlot.FIRST).loadout(LoadoutSlot.FIRST,loadout("guardian",LoadoutStatus.ACTIVE,null,Map.of(),Map.of())).build());
        expect(IllegalArgumentException.class,()->ClassProfile.builder(OWNER).primaryClassId("wizard").classLevel(10).activeSlot(LoadoutSlot.FIRST).loadout(LoadoutSlot.FIRST,loadout("necromancer",LoadoutStatus.SEALED,new SealReason(SealCause.FACTION_MISSING,"dark",""),Map.of(),Map.of())).build());
        expect(IllegalArgumentException.class,()->new ClassLoadout("",LoadoutStatus.EMPTY,null,Map.of(),MasteryProgress.empty(),null,Set.of(),"",CapstoneStatus.LOCKED,Map.of(),Map.of("hidden","x"),""));
        CompanionProfile beast=new CompanionProfile(UUID.randomUUID(),"beast_master.stable","WOLF","Fang",1,0,"","ACTIVE",List.of(),0,Map.of());
        expect(IllegalArgumentException.class,()->loadout("necromancer",LoadoutStatus.INACTIVE,null,Map.of(beast.companionId(),beast),Map.of()));
        ClassProfile review=ClassProfile.builder(OWNER).status(ProfileStatus.REVIEW).diagnostics(new ProfileDiagnostics("","","","manual review")).build();expect(IllegalStateException.class,review::toBuilder);expect(IllegalStateException.class,review::withoutClass);
        ClassProfile quarantined=ClassProfile.builder(OWNER).status(ProfileStatus.QUARANTINED).diagnostics(new ProfileDiagnostics("ev-1","bad digest","","quarantined")).build();expect(IllegalStateException.class,quarantined::toBuilder);check(!quarantined.isGameplayUsable(),"quarantine blocked");
        SealReason both=new SealReason(Map.of(SealCause.FACTION_MISSING,"dark",SealCause.SINNER_MARK_MISSING,"sinner"),"gates");check(both.causes().size()==2,"complete seal set");check(both.gateRestorableOnly(),"restorable gates");
    }
    private static void numericBounds(){
        check(NumericGuards.addInt(2,3,"x")==5,"safe int");check(NumericGuards.addLong(2,3,"x")==5,"safe long");check(NumericGuards.nextRevision(9)==10,"next revision");
        expect(IllegalArgumentException.class,()->NumericGuards.addInt(Integer.MAX_VALUE,1,"x"));expect(IllegalArgumentException.class,()->NumericGuards.addLong(Long.MAX_VALUE,1,"x"));expect(IllegalStateException.class,()->NumericGuards.nextRevision(Long.MAX_VALUE));
        expect(IllegalArgumentException.class,()->new MasteryProgress(-1,0));expect(IllegalArgumentException.class,()->new CompanionProfile(UUID.randomUUID(),"beast_master.stable","WOLF","",0,0,"","",List.of(),0,Map.of()));
    }
    private static void codecRoundTrip()throws Exception{
        CompanionProfile c=new CompanionProfile(UUID.fromString("00000000-0000-0000-0000-000000000299"),"necromancer.court","ZOMBIE","Court",7,77,"bone","ACTIVE",List.of("pet_armor"),1234,Map.of("ritual_summoned","true"));
        ProfileOperation op=new ProfileOperation("op-1",ProfileOperationType.COMPANION_MUTATION,ProfileOperationStatus.COMMITTED,"ADD","0","none",3,"ok");
        ClassProfile p=ClassProfile.builder(OWNER).revision(4).primaryClassId("wizard").classLevel(20).classExperience(900).activeSlot(LoadoutSlot.FIRST).loadout(LoadoutSlot.FIRST,loadout("necromancer",LoadoutStatus.ACTIVE,null,Map.of(c.companionId(),c),Map.of("companion.active_id",c.companionId().toString()))).operation(op).build();
        ClassProfileCodec codec=new ClassProfileCodec();byte[] encoded=codec.encode(p);ClassProfile decoded=codec.decodeForOwner(encoded,OWNER);check(p.equals(decoded),"roundtrip exact");check(Arrays.equals(encoded,codec.encode(decoded)),"bit deterministic");check(ClassProfileCodec.digestHex(encoded).length()==64,"digest hex");
    }
    private static void codecOwnerBinding()throws Exception{
        ClassProfileCodec codec=new ClassProfileCodec();byte[] payload=codec.encode(ClassProfile.empty(OWNER,0));check(codec.decodeForOwner(payload,OWNER).ownerId().equals(OWNER),"correct owner");expect(ClassProfileCodec.DecodeException.class,()->codec.decodeForOwner(payload,OTHER));
    }
    private static void codecCorruption(){
        ClassProfileCodec codec=new ClassProfileCodec();byte[] payload=codec.encode(ClassProfile.empty(OWNER,0));
        byte[] changed=payload.clone();changed[20]^=1;expect(ClassProfileCodec.DecodeException.class,()->codec.decode(changed));
        byte[] trailing=Arrays.copyOf(payload,payload.length+1);expect(ClassProfileCodec.DecodeException.class,()->codec.decode(trailing));
        expect(ClassProfileCodec.DecodeException.class,()->codec.decode(Arrays.copyOf(payload,7)));
        byte[] magic=payload.clone();magic[0]='X';expect(ClassProfileCodec.DecodeException.class,()->codec.decode(magic));
        expect(IllegalArgumentException.class,()->new ClassProfileCodec(new ClassProfileCodec.Limits(128,256,2)));
    }
    private static void deterministicCollections(){
        CompanionProfile c1=new CompanionProfile(UUID.fromString("00000000-0000-0000-0000-000000000211"),"beast_master.stable","WOLF","A",1,0,"","ACTIVE",List.of(),0,Map.of("z","2","a","1"));
        CompanionProfile c2=new CompanionProfile(UUID.fromString("00000000-0000-0000-0000-000000000212"),"beast_master.stable","FOX","B",1,0,"","ACTIVE",List.of(),0,Map.of());
        Map<UUID,CompanionProfile> first=new LinkedHashMap<>();first.put(c1.companionId(),c1);first.put(c2.companionId(),c2);Map<UUID,CompanionProfile> second=new LinkedHashMap<>();second.put(c2.companionId(),c2);second.put(c1.companionId(),c1);
        ClassProfile p1=ClassProfile.builder(OWNER).primaryClassId("archer").classLevel(1).loadout(LoadoutSlot.FIRST,loadout("beast_master",LoadoutStatus.INACTIVE,null,first,Map.of())).build();
        ClassProfile p2=ClassProfile.builder(OWNER).primaryClassId("archer").classLevel(1).loadout(LoadoutSlot.FIRST,loadout("beast_master",LoadoutStatus.INACTIVE,null,second,Map.of())).build();
        check(Arrays.equals(new ClassProfileCodec().encode(p1),new ClassProfileCodec().encode(p2)),"insertion order independent");
        expect(IllegalArgumentException.class,()->new CompanionProfile(UUID.randomUUID(),"beast_master.stable","WOLF","A",1,0,"","ACTIVE",List.of(),0,Map.of(" A ","1","a","2")));
    }
    private static ClassLoadout loadout(String spec,LoadoutStatus status,SealReason seal,Map<UUID,CompanionProfile> roster,Map<String,String> mechanics){return new ClassLoadout(spec,status,seal,Map.of(),MasteryProgress.empty(),null,Set.of(),"",CapstoneStatus.LOCKED,roster,mechanics,"");}
    private static void check(boolean v,String m){assertions++;if(!v)throw new AssertionError(m);}private static void expect(Class<? extends Throwable> type,Throwing r){assertions++;try{r.run();throw new AssertionError("Expected "+type.getSimpleName());}catch(Throwable x){if(!type.isInstance(x))throw new AssertionError("Expected "+type.getSimpleName()+" but got "+x,x);}}private interface Throwing{void run()throws Exception;}
}
