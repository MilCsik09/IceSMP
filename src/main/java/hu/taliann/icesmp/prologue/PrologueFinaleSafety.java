package hu.taliann.icesmp.prologue;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
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

    PrologueFinaleSafety(JavaPlugin plugin,PrologueManager state,PrologueFinaleRunState runState,
                         PrologueEncounterEngine encounters){
        this.plugin=plugin;this.state=state;this.runState=runState;this.encounters=encounters;
        victoryObserved.set(state.bossDefeated()||state.bossVictoryPending());
        if(state.bossVictoryPending()&&!state.bossDefeated())state.save();
    }
    void reset(){victoryObserved.set(false);victoryCommitInFlight.set(false);}
    boolean blocksBossSpawn(){return victoryObserved.get()||state.bossVictoryPending()||state.bossDefeated();}

    void pause(String actor){
        long remaining=encounters.pauseActive();
        try{state.pause(true,actor);}
        catch(RuntimeException x){encounters.resumeActive();throw x;}
        UUID id=state.finaleId();
        if(id!=null)runState.recordPausedEncounter(id,state.finalePhase(),remaining);
    }
    void resume(String actor,Consumer<RuntimeException> failure){
        if(state.bossVictoryPending()&&!state.bossDefeated()){retryVictory(actor,failure);return;}
        state.pause(false,actor);encounters.resumeActive();
    }

    void observeVictory(Consumer<RuntimeException> failure){
        if(!victoryObserved.compareAndSet(false,true))return;
        UUID id=state.finaleId();
        if(id==null){failure.accept(new IllegalStateException("Hiányzó Prologue finale-id"));return;}
        commit(id,"boss-death",false,true,failure);
    }
    private void retryVictory(String actor,Consumer<RuntimeException> failure){
        UUID id=state.finaleId();if(id==null)throw new IllegalStateException("Nincs aktív Prologue finale-id");
        victoryObserved.set(true);commit(id,actor+":retry",true,false,failure);
    }
    private void commit(UUID id,String actor,boolean resumeAfter,boolean reservePending,
                        Consumer<RuntimeException> failure){
        if(!victoryCommitInFlight.compareAndSet(false,true))return;
        try{
            Bukkit.getAsyncScheduler().runNow(plugin,t->{
                RuntimeException problem=null;
                try{
                    if(reservePending)state.markBossVictoryPending(id,"boss-death:pending");
                    state.recordBossVictory(id,actor);
                }catch(RuntimeException x){problem=x;failClosed(id,x);}
                RuntimeException result=problem;victoryCommitInFlight.set(false);
                scheduleResult(result,resumeAfter,actor,failure);
            });
        }catch(IllegalPluginAccessException unavailable){
            victoryCommitInFlight.set(false);
            RuntimeException problem=new IllegalStateException("A Prologue persistence schedulere nem elérhető",unavailable);
            failClosed(id,problem);failure.accept(problem);
        }
    }
    private void scheduleResult(RuntimeException result,boolean resumeAfter,String actor,
                                Consumer<RuntimeException> failure){
        if(!plugin.isEnabled())return;
        try{
            Bukkit.getGlobalRegionScheduler().run(plugin,g->{
                if(result!=null){failure.accept(result);return;}
                if(resumeAfter)try{state.pause(false,actor+":resume");}catch(RuntimeException x){failure.accept(x);}
            });
        }catch(IllegalPluginAccessException ignored){ }
    }
    private void failClosed(UUID id,RuntimeException problem){
        try{state.markBossVictoryPersistenceFailure(id,detail(problem),"boss-victory:persistence-failure");}
        catch(RuntimeException secondary){
            try{state.pause(true,"boss-victory:fail-closed");}catch(RuntimeException ignored){}
            plugin.getLogger().severe("Prologue finale completion receipt also failed: "+secondary);
        }
    }
    private static String detail(Throwable x){String v=x.getMessage();return v==null||v.isBlank()?x.getClass().getSimpleName():v;}
}
