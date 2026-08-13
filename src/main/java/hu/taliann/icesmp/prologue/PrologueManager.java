package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

/** Olethropyla Season 0 tartós világállapotának egyetlen authorityje. */
public final class PrologueManager {
    private static final int SCHEMA_VERSION=1,MAX_AUDIT=64;
    private static volatile PrologueManager active;
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final File file;
    private final Object lock=new Object();
    private final LinkedHashSet<UUID> participants=new LinkedHashSet<>();
    private final ArrayDeque<String> audit=new ArrayDeque<>();

    private PrologueState state=PrologueState.DORMANT;
    private PrologueStage stage=PrologueStage.SILENCE;
    private PrologueFinalePhase finalePhase=PrologueFinalePhase.IDLE;
    private UUID finaleId;
    private int stability=PrologueStage.SILENCE.defaultStability();
    private long stateChangedAt,stageChangedAt,finalePhaseChangedAt;
    private boolean paused,bossDefeated,finaleVictory,gateUnlocked,rewardPlanCreated,rewardsCommitted,
            chronicleCommitted,monumentCommitted,seasonOneStarted,bossVictoryPending;
    private long pauseStartedAt,pauseAccumulatedMillis;
    private String bossVictoryFailure="";

    public PrologueManager(JavaPlugin plugin,ConfigManager config){
        this.plugin=Objects.requireNonNull(plugin);this.config=Objects.requireNonNull(config);
        file=new File(plugin.getDataFolder(),"prologue.yml");YamlStore.registerCriticalWrite(file);plugin.getDataFolder().mkdirs();load();active=this;
    }
    public static PrologueManager current(){return active;}

    public void load(){synchronized(lock){
        if(!file.exists()){long now=System.currentTimeMillis();state=parseState(config.getString("world-events.prologue.initial-state","UNSTABLE"));
            stage=parseStage(config.getString("world-events.prologue.initial-stage","SILENCE"));stability=configuredStageStability(stage);
            stateChangedAt=stageChangedAt=finalePhaseChangedAt=now;write();return;}
        YamlConfiguration y=YamlStore.loadTracked(file,plugin.getLogger());
        if(y.getInt("prologue.schema-version",-1)!=SCHEMA_VERSION){YamlStore.failCorrupt(file,plugin.getLogger(),"Ismeretlen Prologue schema-version");throw new IllegalStateException("Invalid Prologue schema-version");}
        try{
            state=PrologueState.valueOf(y.getString("prologue.state",""));stage=PrologueStage.valueOf(y.getString("prologue.stage",""));
            finalePhase=PrologueFinalePhase.valueOf(y.getString("prologue.finale.phase","IDLE"));
            String raw=y.getString("prologue.finale.id","");finaleId=raw.isBlank()?null:UUID.fromString(raw);
            stability=y.getInt("prologue.stability",-1);stateChangedAt=y.getLong("prologue.state-changed-at",-1);
            stageChangedAt=y.getLong("prologue.stage-changed-at",-1);finalePhaseChangedAt=y.getLong("prologue.finale.phase-changed-at",-1);
            paused=y.getBoolean("prologue.finale.paused",false);pauseStartedAt=y.getLong("prologue.finale.pause-started-at",0);
            pauseAccumulatedMillis=Math.max(0,y.getLong("prologue.finale.pause-accumulated-millis",0));
            bossDefeated=y.getBoolean("prologue.finale.boss-defeated",false);finaleVictory=y.getBoolean("prologue.finale.victory",false);
            bossVictoryPending=y.getBoolean("prologue.finale.boss-victory-pending",false);
            bossVictoryFailure=y.getString("prologue.finale.boss-victory-failure","");
            gateUnlocked=y.getBoolean("prologue.gate-unlocked",false);rewardPlanCreated=y.getBoolean("prologue.rewards.plan-created",false);
            rewardsCommitted=y.getBoolean("prologue.rewards.committed",false);chronicleCommitted=y.getBoolean("prologue.legacy.chronicle-committed",false);
            monumentCommitted=y.getBoolean("prologue.legacy.monument-committed",false);seasonOneStarted=y.getBoolean("prologue.season-one-started",false);
            participants.clear();for(String p:y.getStringList("prologue.finale.participants"))participants.add(UUID.fromString(p));
            audit.clear();for(String line:y.getStringList("prologue.audit"))if(!line.isBlank())audit.addLast(line);
            if(paused&&pauseStartedAt<=0)pauseStartedAt=System.currentTimeMillis();
            if(bossVictoryPending&&!bossDefeated){paused=true;if(pauseStartedAt<=0)pauseStartedAt=System.currentTimeMillis();}
            validate();
        }catch(RuntimeException invalid){YamlStore.failCorrupt(file,plugin.getLogger(),"Érvénytelen Prologue állapot: "+invalid.getMessage());throw invalid;}
    }}
    public void save(){synchronized(lock){write();}}
    public PrologueState state(){synchronized(lock){return state;}}public PrologueStage stage(){synchronized(lock){return stage;}}
    public PrologueFinalePhase finalePhase(){synchronized(lock){return finalePhase;}}public UUID finaleId(){synchronized(lock){return finaleId;}}
    public int stability(){synchronized(lock){return stability;}}public long stageChangedAt(){synchronized(lock){return stageChangedAt;}}
    public long finalePhaseChangedAt(){synchronized(lock){return finalePhaseChangedAt;}}public boolean paused(){synchronized(lock){return paused;}}
    public boolean bossDefeated(){synchronized(lock){return bossDefeated;}}public boolean finaleVictory(){synchronized(lock){return finaleVictory;}}
    public boolean bossVictoryPending(){synchronized(lock){return bossVictoryPending;}}public String bossVictoryFailure(){synchronized(lock){return bossVictoryFailure;}}
    public boolean gateUnlocked(){synchronized(lock){return gateUnlocked;}}public boolean rewardPlanCreated(){synchronized(lock){return rewardPlanCreated;}}
    public boolean rewardsCommitted(){synchronized(lock){return rewardsCommitted;}}public boolean chronicleCommitted(){synchronized(lock){return chronicleCommitted;}}
    public boolean monumentCommitted(){synchronized(lock){return monumentCommitted;}}public boolean seasonOneStarted(){synchronized(lock){return seasonOneStarted;}}
    public Set<UUID> finaleParticipants(){synchronized(lock){return Set.copyOf(participants);}}
    public long finalePhaseAgeMillis(long now){synchronized(lock){long pausedNow=paused&&pauseStartedAt>0?Math.max(0,now-pauseStartedAt):0;
        return Math.max(0,now-finalePhaseChangedAt-pauseAccumulatedMillis-pausedNow);}}

    public void setStage(PrologueStage next,String actor){Objects.requireNonNull(next);mutate(actor,"stage="+next,()->{
        if(state==PrologueState.COMPLETED||state==PrologueState.GATE_OPEN||state==PrologueState.FINALE)throw new IllegalStateException("A Prologue aktuális állapotában a stage nem módosítható");
        stage=next;stability=configuredStageStability(next);stageChangedAt=System.currentTimeMillis();state=next.ordinal()>=PrologueStage.LEAK.ordinal()?PrologueState.BREACHING:PrologueState.UNSTABLE;stateChangedAt=stageChangedAt;});}
    public void setStability(int value,String actor){mutate(actor,"stability="+value,()->stability=Math.max(0,Math.min(100,value)));}

    public UUID beginFinale(String actor){synchronized(lock){if(state==PrologueState.COMPLETED||state==PrologueState.GATE_OPEN)throw new IllegalStateException("A Prologue finálé már lezárult");if(finalePhase.running()||state==PrologueState.FINALE)return finaleId;}
        UUID id=UUID.randomUUID();mutate(actor,"finale-start="+id,()->{long now=System.currentTimeMillis();finaleId=id;state=PrologueState.FINALE;stateChangedAt=now;finalePhase=PrologueFinalePhase.PREPARING;finalePhaseChangedAt=now;
            paused=false;pauseStartedAt=pauseAccumulatedMillis=0;bossDefeated=finaleVictory=bossVictoryPending=false;bossVictoryFailure="";
            rewardPlanCreated=rewardsCommitted=chronicleCommitted=monumentCommitted=seasonOneStarted=false;participants.clear();});return id;}
    public void checkpoint(PrologueFinalePhase phase,String actor){Objects.requireNonNull(phase);mutate(actor,"finale-phase="+phase,()->{
        if(paused)throw new IllegalStateException("Pause alatt a finálé checkpoint nem léphet tovább");
        if(finaleId==null||state!=PrologueState.FINALE&&state!=PrologueState.GATE_OPEN)throw new IllegalStateException("Nincs aktív tartós Prologue finálé");
        if(phase.ordinal()<finalePhase.ordinal()&&phase!=PrologueFinalePhase.ABORTED)throw new IllegalStateException("A finálé checkpoint nem léphet vissza");
        finalePhase=phase;finalePhaseChangedAt=System.currentTimeMillis();pauseStartedAt=pauseAccumulatedMillis=0;});}
    public void recordParticipants(Collection<UUID> values,String actor){mutate(actor,"participants="+(values==null?0:values.size()),()->{participants.clear();if(values!=null)participants.addAll(values);});}

    public void pause(boolean value,String actor){synchronized(lock){if(paused==value)return;}mutate(actor,value?"finale-pause":"finale-resume",()->{
        if(!finalePhase.running())throw new IllegalStateException("Nincs futó finálé");long now=System.currentTimeMillis();
        if(value){paused=true;pauseStartedAt=now;}else{if(bossVictoryPending&&!bossDefeated)throw new IllegalStateException("A boss-győzelem tartós rögzítése még függőben van");
            if(pauseStartedAt>0)pauseAccumulatedMillis=Math.addExact(pauseAccumulatedMillis,Math.max(0,now-pauseStartedAt));pauseStartedAt=0;paused=false;}});}
    public void abort(String actor){mutate(actor,"finale-abort",()->{
        if(finaleVictory||gateUnlocked||bossVictoryPending)throw new IllegalStateException("Boss-győzelem vagy Gate-unlock után a finálé nem abortálható");
        finalePhase=PrologueFinalePhase.ABORTED;finalePhaseChangedAt=System.currentTimeMillis();paused=false;pauseStartedAt=pauseAccumulatedMillis=0;
        state=stage.ordinal()>=PrologueStage.LEAK.ordinal()?PrologueState.BREACHING:PrologueState.UNSTABLE;stateChangedAt=finalePhaseChangedAt;finaleId=null;participants.clear();});}

    public void markBossVictoryPending(UUID expected,String actor){Objects.requireNonNull(expected);mutate(actor,"boss-victory-pending="+expected,()->{
        requireFinale(expected);if(bossDefeated)return;if(finalePhase!=PrologueFinalePhase.BOSS_FIGHT)throw new IllegalStateException("Boss-győzelem csak BOSS_FIGHT alatt foglalható");
        bossVictoryPending=true;bossVictoryFailure="";});}
    public void recordBossVictory(String actor){UUID id=finaleId();if(id==null)throw new IllegalStateException("Nincs aktív finálé");recordBossVictory(id,actor);}
    public void recordBossVictory(UUID expected,String actor){Objects.requireNonNull(expected);mutate(actor,"boss-victory="+expected,()->{
        requireFinale(expected);if(bossDefeated)return;if(!bossVictoryPending)throw new IllegalStateException("Nincs tartós boss-victory pending receipt");
        bossDefeated=finaleVictory=true;bossVictoryPending=false;bossVictoryFailure="";finalePhase=PrologueFinalePhase.FALSE_END;
        finalePhaseChangedAt=System.currentTimeMillis();pauseAccumulatedMillis=0;if(paused)pauseStartedAt=finalePhaseChangedAt;});}
    public void markBossVictoryPersistenceFailure(UUID expected,String detail,String actor){Objects.requireNonNull(expected);mutate(actor,"boss-victory-persistence-failure="+expected,()->{
        requireFinale(expected);if(bossDefeated)return;bossVictoryPending=true;bossVictoryFailure=cleanFailure(detail);if(!paused){paused=true;pauseStartedAt=System.currentTimeMillis();}});}

    public void unlockGateAfterVictory(String actor){mutate(actor,"gate-unlocked",()->{if(!finaleVictory||!bossDefeated)throw new IllegalStateException("A Kapu csak tartós finálégyőzelem után nyitható");
        gateUnlocked=true;state=PrologueState.GATE_OPEN;stateChangedAt=System.currentTimeMillis();if(finalePhase.ordinal()<PrologueFinalePhase.GATE_AWAKENING.ordinal()){finalePhase=PrologueFinalePhase.GATE_AWAKENING;finalePhaseChangedAt=stateChangedAt;pauseAccumulatedMillis=0;if(paused)pauseStartedAt=stateChangedAt;}});}
    public void forceGateOpen(String actor){mutate(actor,"gate-force-open",()->{gateUnlocked=true;state=PrologueState.GATE_OPEN;stateChangedAt=System.currentTimeMillis();});}
    public void markRewardPlanCreated(String actor){mutate(actor,"reward-plan-created",()->{if(!finaleVictory)throw new IllegalStateException("Nincs finálégyőzelem");rewardPlanCreated=true;});}
    public void markRewardsCommitted(String actor){mutate(actor,"rewards-committed",()->{if(!rewardPlanCreated)throw new IllegalStateException("Nincs tartós reward plan");rewardsCommitted=true;});}
    public void markChronicleCommitted(String actor){mutate(actor,"chronicle-committed",()->chronicleCommitted=true);}
    public void markMonumentCommitted(String actor){mutate(actor,"monument-committed",()->monumentCommitted=true);}
    public void markSeasonOneStarted(String actor){mutate(actor,"season-one-started",()->{if(!gateUnlocked||!rewardsCommitted)throw new IllegalStateException("Season 1 csak Gate-unlock és reward commit után indulhat");seasonOneStarted=true;});}
    public void complete(String actor){mutate(actor,"prologue-completed",()->{if(!gateUnlocked||!rewardsCommitted||!seasonOneStarted)throw new IllegalStateException("A Prologue transition még nem teljes");
        state=PrologueState.COMPLETED;finalePhase=PrologueFinalePhase.COMPLETED;paused=false;pauseStartedAt=pauseAccumulatedMillis=0;long now=System.currentTimeMillis();stateChangedAt=finalePhaseChangedAt=now;});}

    private void requireFinale(UUID expected){if(finaleId==null||!finaleId.equals(expected))throw new IllegalStateException("Prologue finale-id mismatch");}
    private void mutate(String actor,String action,Runnable change){synchronized(lock){Memory before=snapshot();change.run();appendAudit(actor,action);try{write();}catch(RuntimeException|Error x){restore(before);throw x;}}}
    private int configuredStageStability(PrologueStage value){return Math.max(0,Math.min(100,config.getInt("world-events.prologue.stages."+value.name().toLowerCase(Locale.ROOT)+".stability",value.defaultStability())));}
    private void appendAudit(String actor,String action){audit.addLast(System.currentTimeMillis()+"|"+(actor==null||actor.isBlank()?"system":actor.trim())+"|"+action);while(audit.size()>MAX_AUDIT)audit.removeFirst();}
    private void validate(){if(stability<0||stability>100||stateChangedAt<=0||stageChangedAt<=0||finalePhaseChangedAt<=0)throw new IllegalArgumentException("érvénytelen időbélyeg vagy stabilitás");
        if(bossDefeated&&!finaleVictory)throw new IllegalArgumentException("bossDefeated victory nélkül");if(finaleVictory&&finaleId==null)throw new IllegalArgumentException("victory finale-id nélkül");
        if(bossVictoryPending&&(finaleId==null||bossDefeated))throw new IllegalArgumentException("érvénytelen boss-victory pending állapot");
        if(!bossVictoryFailure.isBlank()&&!bossVictoryPending)throw new IllegalArgumentException("boss-victory failure pending receipt nélkül");
        if(paused&&pauseStartedAt<=0)throw new IllegalArgumentException("pause timestamp hiányzik");
        if(state==PrologueState.COMPLETED&&(!gateUnlocked||!rewardsCommitted||!seasonOneStarted))throw new IllegalArgumentException("COMPLETED hiányos transitionnel");
        if(gateUnlocked&&state!=PrologueState.GATE_OPEN&&state!=PrologueState.COMPLETED)throw new IllegalArgumentException("nyitott Gate inkompatibilis state-ben");}

    private void write(){YamlConfiguration y=new YamlConfiguration();y.set("prologue.schema-version",SCHEMA_VERSION);y.set("prologue.state",state.name());y.set("prologue.stage",stage.name());y.set("prologue.stability",stability);
        y.set("prologue.state-changed-at",stateChangedAt);y.set("prologue.stage-changed-at",stageChangedAt);y.set("prologue.gate-unlocked",gateUnlocked);y.set("prologue.season-one-started",seasonOneStarted);
        y.set("prologue.finale.id",finaleId==null?"":finaleId.toString());y.set("prologue.finale.phase",finalePhase.name());y.set("prologue.finale.phase-changed-at",finalePhaseChangedAt);
        y.set("prologue.finale.paused",paused);y.set("prologue.finale.pause-started-at",pauseStartedAt);y.set("prologue.finale.pause-accumulated-millis",pauseAccumulatedMillis);
        y.set("prologue.finale.boss-defeated",bossDefeated);y.set("prologue.finale.victory",finaleVictory);y.set("prologue.finale.boss-victory-pending",bossVictoryPending);y.set("prologue.finale.boss-victory-failure",bossVictoryFailure);
        y.set("prologue.finale.participants",participants.stream().map(UUID::toString).toList());y.set("prologue.rewards.plan-created",rewardPlanCreated);y.set("prologue.rewards.committed",rewardsCommitted);
        y.set("prologue.legacy.chronicle-committed",chronicleCommitted);y.set("prologue.legacy.monument-committed",monumentCommitted);y.set("prologue.audit",List.copyOf(audit));
        try{YamlStore.saveAtomic(file,y);}catch(IOException x){throw new UncheckedIOException("prologue.yml mentése sikertelen",x);}}
    private Memory snapshot(){return new Memory(state,stage,finalePhase,finaleId,stability,stateChangedAt,stageChangedAt,finalePhaseChangedAt,paused,pauseStartedAt,pauseAccumulatedMillis,bossDefeated,finaleVictory,bossVictoryPending,bossVictoryFailure,gateUnlocked,rewardPlanCreated,rewardsCommitted,chronicleCommitted,monumentCommitted,seasonOneStarted,new LinkedHashSet<>(participants),new ArrayDeque<>(audit));}
    private void restore(Memory m){state=m.state;stage=m.stage;finalePhase=m.phase;finaleId=m.id;stability=m.stability;stateChangedAt=m.stateAt;stageChangedAt=m.stageAt;finalePhaseChangedAt=m.phaseAt;paused=m.paused;pauseStartedAt=m.pauseAt;pauseAccumulatedMillis=m.pauseAccum;
        bossDefeated=m.bossDefeated;finaleVictory=m.victory;bossVictoryPending=m.pending;bossVictoryFailure=m.failure;gateUnlocked=m.gate;rewardPlanCreated=m.plan;rewardsCommitted=m.rewards;chronicleCommitted=m.chronicle;monumentCommitted=m.monument;seasonOneStarted=m.seasonOne;
        participants.clear();participants.addAll(m.participants);audit.clear();audit.addAll(m.audit);}
    private static String cleanFailure(String value){String v=value==null?"unknown persistence failure":value.trim();return v.length()>256?v.substring(0,256):v;}
    private static PrologueState parseState(String raw){try{return PrologueState.valueOf(raw.trim().toUpperCase(Locale.ROOT));}catch(RuntimeException x){return PrologueState.UNSTABLE;}}
    private static PrologueStage parseStage(String raw){try{return PrologueStage.valueOf(raw.trim().toUpperCase(Locale.ROOT));}catch(RuntimeException x){return PrologueStage.SILENCE;}}
    private record Memory(PrologueState state,PrologueStage stage,PrologueFinalePhase phase,UUID id,int stability,long stateAt,long stageAt,long phaseAt,
                          boolean paused,long pauseAt,long pauseAccum,boolean bossDefeated,boolean victory,boolean pending,String failure,boolean gate,boolean plan,
                          boolean rewards,boolean chronicle,boolean monument,boolean seasonOne,LinkedHashSet<UUID> participants,ArrayDeque<String> audit){}
}
