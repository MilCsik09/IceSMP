package hu.taliann.icesmp.prologue;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Coordinates pause semantics and the one-shot durable finale entity victory. */
final class PrologueFinaleSafety {
    private final JavaPlugin plugin;
    private final PrologueManager state;
    private final PrologueFinaleRunState runState;
    private final PrologueEncounterEngine encounters;
    private final AtomicBoolean victoryObserved=new AtomicBoolean();
    private final AtomicBoolean victoryCommitInFlight=new AtomicBoolean();

    PrologueFinaleSafety(JavaPlugin plugin,PrologueManager state,PrologueFinaleRunState runState,PrologueEncounterEngine encounters){
        this.plugin=plugin;this.state=state;this.runState=runState;this.encounters=encounters;
        victoryObserved.set(state.bossDefeated()||state.bossVictoryPending());
        if(state.bossVictoryPending()&&!state.bossDefeated())state.save();
    }
    void reset(){victoryObserved.set(false);victoryCommitInFlight.set(false);}
    boolean blocksBossSpawn(){return victoryObserved.get()||state.bossVictoryPending()||state.bossDefeated();}

    void pause(String actor){
        long remaining=encounters.pauseActive();
        try{state.pause(true,actor);UUID id=state.finaleId();if(id!=null)runState.recordPausedEncounter(id,state.finalePhase(),remaining);}
        catch(RuntimeException x){encounters.resumeActive();throw x;}
    }
    void resume(String actor,Consumer<RuntimeException> failure){
        if(state.bossVictoryPending()&&!state.bossDefeated()){retryVictory(actor,failure);return;}
        state.pause(false,actor);encounters.resumeActive();
    }

    void observeVictory(Consumer<RuntimeException> failure){
        if(!victoryObserved.compareAndSet(false,true))return;UUID id=state.finaleId();
        if(id==null){RuntimeException x=new IllegalStateException("Hiányzó Prologue finale-id");failure.accept(x);return;}
        try{state.markBossVictoryPending(id,"boss-death:pending");}
        catch(RuntimeException x){encounters.pauseActive();failClosed(id,x);failure.accept(x);return;}
        commit(id,"boss-death",false,failure);
    }
    private void retryVictory(String actor,Consumer<RuntimeException> failure){
        UUID id=state.finaleId();if(id==null)throw new IllegalStateException("Nincs aktív Prologue finale-id");
        victoryObserved.set(true);commit(id,actor+":retry",true,failure);
    }
    private void commit(UUID id,String actor,boolean resumeAfter,Consumer<RuntimeException> failure){
        if(!victoryCommitInFlight.compareAndSet(false,true))return;
        Bukkit.getAsyncScheduler().runNow(plugin,t->{
            RuntimeException problem=null;try{state.recordBossVictory(id,actor);}catch(RuntimeException x){problem=x;failClosed(id,x);}
            RuntimeException result=problem;victoryCommitInFlight.set(false);
            Bukkit.getGlobalRegionScheduler().run(plugin,g->{
                if(result!=null){failure.accept(result);return;}
                if(resumeAfter)try{state.pause(false,actor+":resume");}catch(RuntimeException x){failure.accept(x);}
            });
        });
    }
    private void failClosed(UUID id,RuntimeException problem){
        try{state.markBossVictoryPersistenceFailure(id,detail(problem),"boss-victory:persistence-failure");}
        catch(RuntimeException secondary){try{state.pause(true,"boss-victory:fail-closed");}catch(RuntimeException ignored){}
            plugin.getLogger().severe("Prologue finale victory failure receipt also failed: "+secondary);}
    }
    private static String detail(Throwable x){String v=x.getMessage();return v==null||v.isBlank()?x.getClass().getSimpleName():v;}
}
