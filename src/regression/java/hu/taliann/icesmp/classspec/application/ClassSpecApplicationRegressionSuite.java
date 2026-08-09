package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.*;
import hu.taliann.icesmp.playerprofile.domain.section.ClassSpecSection;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Dependency-free greenfield mutation, fencing and DARK gate regressions. */
public final class ClassSpecApplicationRegressionSuite {
    private static final UUID PLAYER=UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static int assertions;
    private ClassSpecApplicationRegressionSuite(){}
    public static void main(String[] args){
        greenfieldClassAndSpec(); activationReconcileBeforeReady(); allDarkSpecsRequireGates(); completeGateSetSealsAndUnseals(); runtimeFailureIsVisible(); staleSessionFencesRuntime(); concurrentMutationsSerialize(); operationReceiptsAreDurableAndParameterBound(); companionIsolation(); shutdownDrainsAndRejectsNewWork();
        System.out.println("Class/spec application regression suite passed. assertions="+assertions);
    }
    private static void greenfieldClassAndSpec(){
        Harness h=harness(ClassSpecSection.empty(0),ClassSpecRuntimePort.noop());
        var assigned=h.gateway.assignClass(PLAYER,new ClassSpecProfileGateway.ClassAssignmentRequest("wizard",1,0,"assign-1")).toCompletableFuture().join();
        check(assigned.committed(),"class assignment");check(h.store.profile.primaryClassId().equals("wizard"),"class durable");
        var selected=h.gateway.select(PLAYER,new ClassSpecProfileGateway.SelectRequest("elementalist",LoadoutSlot.FIRST,satisfied())).toCompletableFuture().join();
        check(selected.committed(),"spec selection");check(h.gateway.activeSpecId(PLAYER).orElseThrow().equals("elementalist"),"active spec");check(h.store.profile.revision()==2,"exact revisions");
    }
    private static void activationReconcileBeforeReady(){
        FakeStore store=new FakeStore(active("necromancer",Map.of("necromancer.soulforge.shards","5")));
        ProfileSessionRegistry sessions=new ProfileSessionRegistry();
        UUID token=sessions.begin(PLAYER);
        AtomicInteger runtimeCalls=new AtomicInteger();
        ClassSpecRuntimePort runtime=new ClassSpecRuntimePort(){
            public CompletionStage<Void> profileCommitted(UUID p,UUID t,ClassSpecSection a,ClassSpecSection b,MutationKind k){runtimeCalls.incrementAndGet();check(t.equals(token),"activation runtime uses exact generation");return CompletableFuture.completedFuture(null);}
            public CompletionStage<Void> failClosed(UUID p,UUID t,String r){return CompletableFuture.completedFuture(null);}
        };
        DefaultClassSpecProfileGateway gateway=new DefaultClassSpecProfileGateway(store,runtime,sessions);
        check(!gateway.isSessionReady(PLAYER),"activation starts before READY");
        var sealed=gateway.reconcileDuringActivation(PLAYER,token,
                new ClassSpecProfileGateway.ReconcileRequest(Map.of(LoadoutSlot.FIRST,missingAll())))
                .toCompletableFuture().join();
        check(sealed.committed(),"DARK reconcile commits during activation");
        check(store.profile.loadout(LoadoutSlot.FIRST).status()==LoadoutStatus.SEALED,
                "gate loss seals before READY");
        check(store.profile.activeSlot()==null,"activation seal clears active slot without auto-switch");
        check(runtimeCalls.get()==1,"activation reconcile runs runtime once");
        gateway.completeSessionActivation(PLAYER,token);
        check(gateway.isSessionReady(PLAYER),"sealed usable profile can reach READY");

        UUID replacement=sessions.begin(PLAYER);
        var stale=gateway.reconcileDuringActivation(PLAYER,token,
                new ClassSpecProfileGateway.ReconcileRequest(Map.of()))
                .toCompletableFuture().join();
        check(stale.status()==ProfileMutationResult.Status.REJECTED,"retired activation rejected");
        check(stale.detail().contains("stale activation"),"retired generation diagnostic");
        check(runtimeCalls.get()==1,"retired activation never runs runtime");
        gateway.completeSessionActivation(PLAYER,replacement);
    }
    private static void allDarkSpecsRequireGates(){
        for(String spec:DarkSpecializationPolicy.IDS){
            Harness h=harness(classOnly(ClassSpecCatalog.parentOf(spec)),ClassSpecRuntimePort.noop());
            var rejected=h.gateway.select(PLAYER,new ClassSpecProfileGateway.SelectRequest(spec,LoadoutSlot.FIRST,missingAll())).toCompletableFuture().join();
            check(rejected.status()==ProfileMutationResult.Status.REJECTED,spec+" gate reject");check(h.store.saves==0,spec+" no persistence");
            var accepted=h.gateway.select(PLAYER,new ClassSpecProfileGateway.SelectRequest(spec,LoadoutSlot.FIRST,satisfied())).toCompletableFuture().join();
            check(accepted.committed(),spec+" accepted");
        }
    }
    private static void completeGateSetSealsAndUnseals(){
        Harness h=harness(active("necromancer",Map.of("necromancer.soulforge.shards","5")),ClassSpecRuntimePort.noop());
        var sealed=h.gateway.reconcile(PLAYER,new ClassSpecProfileGateway.ReconcileRequest(Map.of(LoadoutSlot.FIRST,missingAll()))).toCompletableFuture().join();
        check(sealed.committed(),"seal committed");ClassLoadout load=h.store.profile.loadout(LoadoutSlot.FIRST);check(load.status()==LoadoutStatus.SEALED,"sealed");check(load.sealReason().causes().equals(Set.of(SealCause.FACTION_MISSING,SealCause.SINNER_MARK_MISSING,SealCause.QUEST_REQUIREMENT_MISSING)),"complete gate set");check(h.store.profile.activeSlot()==null,"active cleared");
        long revision=h.store.profile.revision();var same=h.gateway.reconcile(PLAYER,new ClassSpecProfileGateway.ReconcileRequest(Map.of(LoadoutSlot.FIRST,missingAll()))).toCompletableFuture().join();check(same.status()==ProfileMutationResult.Status.NO_CHANGE,"same gates no change");check(h.store.profile.revision()==revision,"no revision jump");
        var partial=h.gateway.reconcile(PLAYER,new ClassSpecProfileGateway.ReconcileRequest(Map.of(LoadoutSlot.FIRST,missingQuest()))).toCompletableFuture().join();check(partial.committed(),"partial recovery updates complete gate set");check(h.store.profile.loadout(LoadoutSlot.FIRST).status()==LoadoutStatus.SEALED,"partial recovery remains sealed");check(h.store.profile.loadout(LoadoutSlot.FIRST).sealReason().causes().equals(Set.of(SealCause.QUEST_REQUIREMENT_MISSING)),"remaining gate retained");
        var unsealed=h.gateway.reconcile(PLAYER,new ClassSpecProfileGateway.ReconcileRequest(Map.of(LoadoutSlot.FIRST,satisfied()))).toCompletableFuture().join();check(unsealed.committed(),"unseal once");check(h.store.profile.loadout(LoadoutSlot.FIRST).status()==LoadoutStatus.INACTIVE,"unsealed inactive");check(h.store.profile.loadout(LoadoutSlot.FIRST).mechanicState().get("necromancer.soulforge.shards").equals("5"),"mechanics preserved");
    }
    private static void runtimeFailureIsVisible(){
        ClassSpecRuntimePort failing=new ClassSpecRuntimePort(){public CompletionStage<Void> profileCommitted(UUID p,UUID t,ClassSpecSection a,ClassSpecSection b,MutationKind k){return CompletableFuture.failedFuture(new IllegalStateException("runtime boom"));}public CompletionStage<Void> failClosed(UUID p,UUID t,String r){return CompletableFuture.completedFuture(null);}};
        Harness h=harness(ClassSpecSection.empty(0),failing);var result=h.gateway.assignClass(PLAYER,new ClassSpecProfileGateway.ClassAssignmentRequest("wizard",1,0,"assign-runtime")).toCompletableFuture().join();
        check(result.status()==ProfileMutationResult.Status.RUNTIME_EFFECT_FAILED,"runtime failure status");check(result.durableMutationApplied(),"durable retained");check(h.store.profile.primaryClassId().equals("wizard"),"durable authority");check(h.store.blockReason.contains("runtime reconciliation failed"),"observable block");check(!h.gateway.isSessionReady(PLAYER),"fail closed session");
    }
    private static void staleSessionFencesRuntime(){
        ControlledStore store=new ControlledStore(ClassSpecSection.empty(0));ProfileSessionRegistry sessions=new ProfileSessionRegistry();UUID old=sessions.begin(PLAYER);sessions.markReady(PLAYER,old);AtomicInteger runtimeCalls=new AtomicInteger();ClassSpecRuntimePort runtime=new ClassSpecRuntimePort(){public CompletionStage<Void> profileCommitted(UUID p,UUID t,ClassSpecSection a,ClassSpecSection b,MutationKind k){runtimeCalls.incrementAndGet();return CompletableFuture.completedFuture(null);}public CompletionStage<Void> failClosed(UUID p,UUID t,String r){return CompletableFuture.completedFuture(null);}};DefaultClassSpecProfileGateway gateway=new DefaultClassSpecProfileGateway(store,runtime,sessions);
        var future=gateway.assignClass(PLAYER,new ClassSpecProfileGateway.ClassAssignmentRequest("wizard",1,0,"stale-assign")).toCompletableFuture();check(store.pending!=null,"save admitted");UUID replacement=sessions.begin(PLAYER);sessions.markReady(PLAYER,replacement);store.commitPending();var result=future.join();check(result.status()==ProfileMutationResult.Status.STALE_SESSION,"stale result");check(runtimeCalls.get()==0,"old runtime fenced");check(store.profile.primaryClassId().equals("wizard"),"durable commit remains");
    }
    private static void concurrentMutationsSerialize(){
        ControlledStore store=new ControlledStore(classOnly("wizard"));ProfileSessionRegistry sessions=readySessions();DefaultClassSpecProfileGateway gateway=new DefaultClassSpecProfileGateway(store,ClassSpecRuntimePort.noop(),sessions);
        var first=gateway.mutateClassExperience(PLAYER,new ClassSpecProfileGateway.ClassExperienceRequest(ClassSpecProfileGateway.ClassExperienceRequest.Mode.ADD,100,100,20,"xp-1")).toCompletableFuture();
        var second=gateway.mutateClassExperience(PLAYER,new ClassSpecProfileGateway.ClassExperienceRequest(ClassSpecProfileGateway.ClassExperienceRequest.Mode.ADD,50,100,20,"xp-2")).toCompletableFuture();
        check(store.saveCalls==1,"second save queued");store.commitPending();first.join();store.awaitSaveCalls(2);check(store.saveCalls==2,"second began after first");store.commitPending();second.join();check(store.profile.classExperience()==150,"both mutations kept");check(store.profile.revision()==2,"serial revisions");
    }
    private static void operationReceiptsAreDurableAndParameterBound(){
        Harness h=harness(active("necromancer",Map.of("necromancer.soulforge.shards","5")),ClassSpecRuntimePort.noop());
        var first=h.gateway.incrementSoulforge(PLAYER,"elet",2,"sf-op").toCompletableFuture().join();check(first.committed(),"soulforge commit");long rev=h.store.profile.revision();check(h.store.profile.operation("sf-op").isPresent(),"receipt persisted");check(h.gateway.incrementSoulforge(PLAYER,"elet",2,"sf-op").toCompletableFuture().join().status()==ProfileMutationResult.Status.NO_CHANGE,"retry idempotent");check(h.store.profile.revision()==rev,"retry no revision");check(h.gateway.incrementSoulforge(PLAYER,"elet",3,"sf-op").toCompletableFuture().join().status()==ProfileMutationResult.Status.REJECTED,"parameter reuse rejected");
        check(h.store.profile.loadout(LoadoutSlot.FIRST).mechanicState().get("necromancer.soulforge.shards").equals("3"),"shard debit atomic");check(h.store.profile.loadout(LoadoutSlot.FIRST).mechanicState().get("necromancer.soulforge.elet").equals("1"),"rank atomic");
    }
    private static void companionIsolation(){
        Harness h=harness(active("beast_master",Map.of()),ClassSpecRuntimePort.noop());UUID id=UUID.fromString("00000000-0000-0000-0000-000000000399");CompanionProfile wolf=new CompanionProfile(id,"beast_master.stable","WOLF","Fang",1,0,"","ACTIVE",List.of(),0,Map.of());
        var add=h.gateway.mutateCompanion(PLAYER,new ClassSpecProfileGateway.CompanionMutationRequest(LoadoutSlot.FIRST,ClassSpecProfileGateway.CompanionMutationRequest.Kind.ADD,id,wolf,"",0,0,0,List.of(),Map.of(),"pet-add")).toCompletableFuture().join();check(add.committed(),"pet add");check(h.gateway.activeCompanion(PLAYER).orElseThrow().companionId().equals(id),"active pet");
        CompanionProfile wrong=new CompanionProfile(UUID.randomUUID(),"necromancer.court","ZOMBIE","Wrong",1,0,"","ACTIVE",List.of(),0,Map.of());var rejected=h.gateway.mutateCompanion(PLAYER,new ClassSpecProfileGateway.CompanionMutationRequest(LoadoutSlot.FIRST,ClassSpecProfileGateway.CompanionMutationRequest.Kind.ADD,wrong.companionId(),wrong,"",0,0,0,List.of(),Map.of(),"pet-wrong")).toCompletableFuture().join();check(rejected.status()==ProfileMutationResult.Status.REJECTED,"namespace isolation");
        var progress=h.gateway.mutateCompanion(PLAYER,new ClassSpecProfileGateway.CompanionMutationRequest(LoadoutSlot.FIRST,ClassSpecProfileGateway.CompanionMutationRequest.Kind.PROGRESS,id,null,"",2,15,0,List.of(),Map.of(),"pet-xp")).toCompletableFuture().join();check(progress.committed(),"pet progress");check(h.gateway.activeCompanion(PLAYER).orElseThrow().level()==2,"pet level");
    }
    private static void shutdownDrainsAndRejectsNewWork(){
        ControlledStore store=new ControlledStore(classOnly("wizard"));ProfileSessionRegistry sessions=readySessions();DefaultClassSpecProfileGateway gateway=new DefaultClassSpecProfileGateway(store,ClassSpecRuntimePort.noop(),sessions);var mutation=gateway.mutateClassExperience(PLAYER,new ClassSpecProfileGateway.ClassExperienceRequest(ClassSpecProfileGateway.ClassExperienceRequest.Mode.ADD,1,100,20,"shutdown-xp"));var shutdown=gateway.prepareShutdown().toCompletableFuture();check(!shutdown.isDone(),"shutdown waits");store.commitPending();mutation.toCompletableFuture().join();shutdown.join();check(!sessions.accepting(),"sessions stopped");expect(CompletionException.class,()->gateway.mutateClassExperience(PLAYER,new ClassSpecProfileGateway.ClassExperienceRequest(ClassSpecProfileGateway.ClassExperienceRequest.Mode.ADD,1,100,20,"late")).toCompletableFuture().join());
    }
    private static Harness harness(ClassSpecSection profile,ClassSpecRuntimePort runtime){FakeStore store=new FakeStore(profile);ProfileSessionRegistry sessions=readySessions();return new Harness(store,sessions,new DefaultClassSpecProfileGateway(store,runtime,sessions));}
    private static ProfileSessionRegistry readySessions(){ProfileSessionRegistry sessions=new ProfileSessionRegistry();UUID token=sessions.begin(PLAYER);sessions.markReady(PLAYER,token);return sessions;}
    private static ClassSpecSection classOnly(String clazz){return ClassSpecSection.builder().primaryClassId(clazz).classLevel(1).build();}
    private static ClassSpecSection active(String spec,Map<String,String> mechanics){String clazz=ClassSpecCatalog.parentOf(spec);return ClassSpecSection.builder().primaryClassId(clazz).classLevel(25).activeSlot(LoadoutSlot.FIRST).loadout(LoadoutSlot.FIRST,new ClassLoadout(spec,LoadoutStatus.ACTIVE,null,Map.of(),MasteryProgress.empty(),null,Set.of(),"",CapstoneStatus.LOCKED,Map.of(),mechanics,"")).build();}
    private static GateSnapshot satisfied(){return new GateSnapshot(GateState.satisfied(),Map.of(GateState.Gate.FACTION,"dark",GateState.Gate.SINNER,"sinner",GateState.Gate.QUEST,"quest"));}
    private static GateSnapshot missingAll(){return new GateSnapshot(GateState.ofRequirements(true,false,true,false,true,false),Map.of(GateState.Gate.FACTION,"dark",GateState.Gate.SINNER,"sinner",GateState.Gate.QUEST,"quest"));}
    private static GateSnapshot missingQuest(){return new GateSnapshot(GateState.ofRequirements(true,true,true,true,true,false),Map.of(GateState.Gate.FACTION,"dark",GateState.Gate.SINNER,"sinner",GateState.Gate.QUEST,"quest"));}
    private record Harness(FakeStore store,ProfileSessionRegistry sessions,DefaultClassSpecProfileGateway gateway){}
    private static class FakeStore implements ClassSpecSectionMutationStore{volatile ClassSpecSection profile;volatile String blockReason="";int saves;FakeStore(ClassSpecSection p){profile=p;}public Optional<ClassSpecSection> cached(UUID id){return Optional.ofNullable(profile);}public Optional<String> sessionBlockReason(UUID id){return blockReason.isBlank()?Optional.empty():Optional.of(blockReason);}public CompletionStage<SaveResult> save(UUID id,long expected,ClassSpecSection candidate){saves++;if(profile==null||profile.revision()!=expected)return CompletableFuture.completedFuture(SaveResult.conflict(profile,profile==null?-1:profile.revision()));profile=candidate;return CompletableFuture.completedFuture(SaveResult.committed(candidate));}public CompletionStage<ClassSpecSection> recover(UUID id,String e,String a){return CompletableFuture.failedFuture(new UnsupportedOperationException());}public void blockSession(UUID id,String reason){blockReason=reason;}}
    private static final class ControlledStore extends FakeStore{CompletableFuture<SaveResult> pending;ClassSpecSection candidate;long expected;int saveCalls;ControlledStore(ClassSpecSection p){super(p);}@Override public CompletionStage<SaveResult> save(UUID id,long e,ClassSpecSection c){saveCalls++;expected=e;candidate=c;pending=new CompletableFuture<>();return pending;}void commitPending(){CompletableFuture<SaveResult> future=pending;ClassSpecSection next=candidate;long expectedRevision=expected;check(future!=null,"pending save");pending=null;candidate=null;if(profile.revision()!=expectedRevision)future.complete(SaveResult.conflict(profile,profile.revision()));else{profile=next;future.complete(SaveResult.committed(next));}}void awaitSaveCalls(int count){long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(saveCalls<count&&System.nanoTime()<deadline)Thread.onSpinWait();check(saveCalls>=count,"timed out awaiting save call");}}
    private static void check(boolean v,String m){assertions++;if(!v)throw new AssertionError(m);}private static void expect(Class<? extends Throwable> type,Throwing r){assertions++;try{r.run();throw new AssertionError("Expected "+type.getSimpleName());}catch(Throwable x){if(!type.isInstance(x))throw new AssertionError("Expected "+type.getSimpleName()+" got "+x,x);}}private interface Throwing{void run()throws Exception;}
}
