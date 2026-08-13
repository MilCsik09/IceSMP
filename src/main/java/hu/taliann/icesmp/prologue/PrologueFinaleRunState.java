package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/** Durable finale companion for scaling and paused encounter timeout recovery. */
public final class PrologueFinaleRunState {
    private final JavaPlugin plugin;
    private final File file;
    private UUID finaleId;
    private int scalingBaseline;
    private PrologueFinalePhase pausedEncounterPhase;
    private long pausedEncounterRemainingMillis=-1L;

    public PrologueFinaleRunState(final JavaPlugin plugin){
        this.plugin=plugin;file=new File(plugin.getDataFolder(),"prologue-finale.yml");YamlStore.registerCriticalWrite(file);load();
    }
    public synchronized void load(){
        finaleId=null;scalingBaseline=0;pausedEncounterPhase=null;pausedEncounterRemainingMillis=-1L;
        if(!file.exists())return;YamlConfiguration y=YamlStore.loadTracked(file,plugin.getLogger());String raw=y.getString("finale-id","");
        if(!raw.isBlank())finaleId=UUID.fromString(raw);scalingBaseline=Math.max(0,y.getInt("scaling-baseline",0));
        String phase=y.getString("paused-encounter.phase","");if(!phase.isBlank())pausedEncounterPhase=PrologueFinalePhase.valueOf(phase);
        pausedEncounterRemainingMillis=y.getLong("paused-encounter.remaining-millis",-1L);
        if(pausedEncounterRemainingMillis<=0L){pausedEncounterRemainingMillis=-1L;pausedEncounterPhase=null;}
    }
    public synchronized int baselineFor(UUID id){return id!=null&&id.equals(finaleId)?scalingBaseline:0;}
    public synchronized long remainingTimeoutFor(UUID id,PrologueFinalePhase phase){
        return id!=null&&id.equals(finaleId)&&phase!=null&&phase==pausedEncounterPhase?pausedEncounterRemainingMillis:-1L;
    }
    public synchronized void begin(UUID id){finaleId=id;scalingBaseline=0;pausedEncounterPhase=null;pausedEncounterRemainingMillis=-1L;save();}
    public synchronized void setBaseline(UUID id,int value){require(id);scalingBaseline=Math.max(0,value);save();}
    public synchronized void recordPausedEncounter(UUID id,PrologueFinalePhase phase,long remainingMillis){
        require(id);if(remainingMillis>0L&&phase!=null){pausedEncounterPhase=phase;pausedEncounterRemainingMillis=remainingMillis;}
        else{pausedEncounterPhase=null;pausedEncounterRemainingMillis=-1L;}save();
    }
    public synchronized void clearPausedEncounter(UUID id){require(id);pausedEncounterPhase=null;pausedEncounterRemainingMillis=-1L;save();}
    public synchronized void clear(UUID id){if(id!=null&&finaleId!=null&&!id.equals(finaleId))return;finaleId=null;scalingBaseline=0;pausedEncounterPhase=null;pausedEncounterRemainingMillis=-1L;save();}
    private void require(UUID id){if(id==null||!id.equals(finaleId))throw new IllegalStateException("finale-id mismatch");}
    private void save(){try{YamlConfiguration y=new YamlConfiguration();y.set("finale-id",finaleId==null?"":finaleId.toString());y.set("scaling-baseline",scalingBaseline);
        y.set("paused-encounter.phase",pausedEncounterPhase==null?"":pausedEncounterPhase.name());y.set("paused-encounter.remaining-millis",pausedEncounterRemainingMillis);YamlStore.saveAtomic(file,y);}
        catch(IOException x){throw new UncheckedIOException("prologue-finale.yml mentése sikertelen",x);}}
}
