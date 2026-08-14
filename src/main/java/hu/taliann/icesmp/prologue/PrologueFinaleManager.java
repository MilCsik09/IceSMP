package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.MajorEventGate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** One-shot, checkpointed Prologue finale orchestrator; separate from SeasonFinaleManager. */
public final class PrologueFinaleManager {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PrologueManager state;
    private final PrologueFinaleRunState runState;
    private final PrologueParticipantTracker participants;
    private final PrologueEncounterEngine encounters;
    private final MajorEventGate eventGate;
    private final PrologueFinaleSafety safety;
    private final PrologueFinaleSettlement settlement;
    private volatile boolean rehearsal;
    private volatile PrologueFinalePhase rehearsalPhase=PrologueFinalePhase.IDLE;
    private volatile long rehearsalPhaseChangedAt;
    private volatile int rehearsalBaseline;
    private volatile boolean gatheringWarningSent;

    public PrologueFinaleManager(JavaPlugin plugin,ConfigManager config,PrologueManager state,
                                 PrologueFinaleRunState runState,PrologueParticipantTracker participants,
                                 PrologueEncounterEngine encounters,PrologueRewardService rewards,
                                 PrologueSeasonTransition seasonTransition,MajorEventGate eventGate){
        this.plugin=plugin;this.config=config;this.state=state;this.runState=runState;
        this.participants=participants;this.encounters=encounters;this.eventGate=eventGate;
        safety=new PrologueFinaleSafety(plugin,state,runState,encounters);
        settlement=new PrologueFinaleSettlement(plugin,config,state,rewards,seasonTransition,runState,participants);
        if(state.finalePhase().running())participants.resumeDurable();
    }

    public boolean isActive(){
        return rehearsal||state.finalePhase().running()
                ||state.finalePhase().irreversibleVictoryPath()&&!state.state().completed();
    }
    public boolean ceasefireActive(){
        PrologueFinalePhase phase=phase();
        return isActive()&&phase.ordinal()>=PrologueFinalePhase.GATHERING.ordinal()
                &&phase.ordinal()<=PrologueFinalePhase.EPILOGUE.ordinal();
    }
    public boolean isRehearsal(){return rehearsal;}
    public PrologueFinalePhase phase(){return rehearsal?rehearsalPhase:state.finalePhase();}

    public synchronized boolean start(boolean rehearsalMode,String actor){
        if(isActive()||encounters.isActive())return false;
        if(eventGate!=null&&!eventGate.mayStartNaturally("prologue"))return false;
        gatheringWarningSent=false;safety.reset();settlement.resetTransient();
        if(rehearsalMode){
            rehearsal=true;rehearsalPhase=PrologueFinalePhase.PREPARING;
            rehearsalPhaseChangedAt=System.currentTimeMillis();rehearsalBaseline=0;participants.begin(false);return true;
        }
        UUID id=state.beginFinale(actor);runState.begin(id);participants.begin(true);return true;
    }

    public synchronized void pause(String actor){
        if(rehearsal)throw new IllegalStateException("A rehearsal pause helyett abortálható és újraindítható");
        safety.pause(actor);
    }
    public synchronized void resume(String actor){
        if(rehearsal)return;
        safety.resume(actor,this::bossVictoryPersistenceFailure);
    }
    public synchronized void abort(String actor){
        encounters.abortActiveSilently();participants.stop();settlement.resetTransient();
        if(rehearsal){rehearsal=false;rehearsalPhase=PrologueFinalePhase.IDLE;rehearsalBaseline=0;return;}
        UUID id=state.finaleId();state.abort(actor);runState.clear(id);safety.reset();
    }

    public void tick(){
        if(!isActive())return;
        if(!rehearsal&&state.paused())return;
        switch(phase()){
            case PREPARING->preparing();
            case GATHERING->gathering();
            case BREACH_1->wave(PrologueFinalePhase.BREACH_2,BreachSeverity.MINOR,false);
            case BREACH_2->wave(PrologueFinalePhase.ELITE_WAVE,BreachSeverity.MAJOR,false);
            case ELITE_WAVE->wave(PrologueFinalePhase.BOSS_INTRO,BreachSeverity.CRITICAL,true);
            case BOSS_INTRO->bossIntro();
            case BOSS_FIGHT->bossFight();
            case FALSE_END->falseEnd();
            case GATE_AWAKENING->gateAwakening();
            case EPILOGUE->epilogue();
            default->{}
        }
    }

    private void preparing(){
        Bukkit.broadcast(Component.text("☠ KÁRHOZAT ÉJSZAKÁJA",NamedTextColor.DARK_RED)
                .append(Component.text(" — Olethropyla ismét megmozdult. Induljatok a Kapuhoz!",NamedTextColor.GOLD)));
        advance(PrologueFinalePhase.GATHERING,"finale:gathering");
    }
    private void gathering(){
        long window=Math.max(10L,config.getLong("world-events.prologue.finale.gathering-seconds",90L))*1000L;
        if(phaseAgeMillis()<window)return;
        int current=participants.currentParticipantCount();
        int minimum=Math.max(1,config.getInt("world-events.prologue.finale.minimum-participants",5));
        if(current<minimum){
            if(!gatheringWarningSent){gatheringWarningSent=true;Bukkit.broadcast(Component.text(
                    "A Kapu előtt még nincs elég harcos a roham megkezdéséhez.",NamedTextColor.YELLOW));}
            return;
        }
        if(rehearsal)rehearsalBaseline=current;
        else if(runState.baselineFor(state.finaleId())<=0)runState.setBaseline(state.finaleId(),current);
        advance(PrologueFinalePhase.BREACH_1,"finale:breach-1");
    }
    private void wave(PrologueFinalePhase next,BreachSeverity severity,boolean elite){
        if(encounters.isActive())return;
        long remaining=rehearsal?-1L:runState.remainingTimeoutFor(state.finaleId(),phase());
        boolean started=encounters.startWave("finale-"+phase().name().toLowerCase(),severity,scalingBaseline(),elite,
                ()->advance(next,"finale:"+next.name().toLowerCase()),this::encounterFailure,remaining);
        if(!started)encounterFailure("A finálé hulláma nem indítható el.");
    }
    private void bossIntro(){
        long delay=Math.max(2L,config.getLong("world-events.prologue.finale.boss-intro-seconds",6L))*1000L;
        if(phaseAgeMillis()<delay)return;
        Bukkit.broadcast(Component.text("A hasadékban valami felel a hívásra…",NamedTextColor.DARK_PURPLE));
        advance(PrologueFinalePhase.BOSS_FIGHT,"finale:boss-fight");
    }
    private void bossFight(){
        if(!rehearsal&&state.bossDefeated()){advance(PrologueFinalePhase.FALSE_END,"recovery:boss-defeated");return;}
        if(!rehearsal&&safety.blocksBossSpawn())return;
        if(encounters.isActive()||encounters.bossAlive())return;
        long remaining=rehearsal?-1L:runState.remainingTimeoutFor(state.finaleId(),PrologueFinalePhase.BOSS_FIGHT);
        if(!encounters.startBoss(scalingBaseline(),this::bossVictory,this::encounterFailure,remaining))
            encounterFailure("A Hasadék Őre nem indítható el.");
    }
    private void bossVictory(){
        if(rehearsal){advance(PrologueFinalePhase.FALSE_END,"rehearsal:boss-victory");return;}
        safety.observeVictory(this::bossVictoryPersistenceFailure);
    }
    private void bossVictoryPersistenceFailure(RuntimeException failure){
        plugin.getLogger().severe("Prologue boss victory commit failed; no second boss may spawn: "+failure);
        Bukkit.broadcast(Component.text(
                "A finálé győzelmi állapota nem rögzíthető; az esemény biztonsági okból szünetel.",NamedTextColor.RED));
    }

    private void falseEnd(){
        if(rehearsal){
            long silence=Math.max(1L,config.getLong("world-events.prologue.finale.false-end-seconds",7L))*1000L;
            if(phaseAgeMillis()<silence)return;
            settlement.visualAwakening();advance(PrologueFinalePhase.GATE_AWAKENING,"rehearsal:gate-awakening");return;
        }
        settlement.falseEnd(phaseAgeMillis());
    }
    private void gateAwakening(){
        if(rehearsal){advance(PrologueFinalePhase.EPILOGUE,"rehearsal:epilogue");return;}
        settlement.gateAwakening();
    }
    private void epilogue(){
        if(rehearsal){
            long delay=Math.max(0L,config.getLong("world-events.prologue.finale.epilogue-seconds",6L))*1000L;
            if(phaseAgeMillis()<delay)return;
            participants.stop();encounters.abortActiveSilently();rehearsal=false;
            rehearsalPhase=PrologueFinalePhase.IDLE;rehearsalBaseline=0;
            Bukkit.broadcast(Component.text("A Prologue rehearsal lezárult; tartós állapot nem változott.",NamedTextColor.GRAY));return;
        }
        settlement.epilogue(phaseAgeMillis());
    }

    private void encounterFailure(String reason){
        plugin.getLogger().warning("Prologue finale encounter failure: "+reason);
        if(rehearsal){abort("rehearsal-failure");return;}
        try{state.pause(true,"encounter-failure");}
        catch(RuntimeException x){plugin.getLogger().severe("Prologue failure pause could not be persisted: "+x);}
        Bukkit.broadcast(Component.text(
                "A Kárhozat Éjszakája átmenetileg megállt. Az adminok biztonságosan folytathatják.",NamedTextColor.RED));
    }
    private int scalingBaseline(){
        int min=Math.max(1,config.getInt("world-events.prologue.scaling.minimum-players",5));
        return Math.max(min,rehearsal?rehearsalBaseline:runState.baselineFor(state.finaleId()));
    }
    private long phaseAgeMillis(){
        return rehearsal?Math.max(0L,System.currentTimeMillis()-rehearsalPhaseChangedAt)
                :state.finalePhaseAgeMillis(System.currentTimeMillis());
    }
    private synchronized void advance(PrologueFinalePhase next,String actor){
        if(rehearsal){rehearsalPhase=next;rehearsalPhaseChangedAt=System.currentTimeMillis();return;}
        if(state.paused()||state.finalePhase()==next)return;
        PrologueFinalePhase previous=state.finalePhase();UUID id=state.finaleId();state.checkpoint(next,actor);
        if(id!=null&&runState.remainingTimeoutFor(id,previous)>0L){
            try{runState.clearPausedEncounter(id);}
            catch(RuntimeException x){plugin.getLogger().warning("Stale Prologue timeout receipt cleanup failed: "+x);}
        }
    }
    public void shutdown(){participants.stop();encounters.shutdown();settlement.resetTransient();rehearsal=false;}
}
