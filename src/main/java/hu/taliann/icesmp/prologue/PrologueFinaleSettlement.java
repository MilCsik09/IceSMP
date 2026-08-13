package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.ChronicleManager;
import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.SeasonMonumentManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Existing Prologue irreversible side effects, coordinated as idempotent settlement steps. */
final class PrologueFinaleSettlement {
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PrologueManager state;
    private final PrologueRewardService rewards;
    private final PrologueSeasonTransition seasonTransition;
    private final PrologueFinaleRunState runState;
    private final PrologueParticipantTracker participants;
    private final AtomicBoolean transitionInFlight=new AtomicBoolean();
    private volatile CompletableFuture<Void> rewardFuture;

    PrologueFinaleSettlement(JavaPlugin plugin,ConfigManager config,PrologueManager state,
                             PrologueRewardService rewards,PrologueSeasonTransition seasonTransition,
                             PrologueFinaleRunState runState,PrologueParticipantTracker participants){
        this.plugin=plugin;this.config=config;this.state=state;this.rewards=rewards;
        this.seasonTransition=seasonTransition;this.runState=runState;this.participants=participants;
    }

    void falseEnd(long phaseAgeMillis){
        long silence=Math.max(1L,config.getLong("world-events.prologue.finale.false-end-seconds",7L))*1000L;
        if(phaseAgeMillis<silence||!transitionInFlight.compareAndSet(false,true))return;
        Bukkit.getAsyncScheduler().runNow(plugin,t->{
            try{
                state.unlockGateAfterVictory("finale-victory");
                scheduleGlobal(this::visualAwakening);
            }catch(RuntimeException x){plugin.getLogger().severe("Gate activation commit failed; Prologue remains fail-closed: "+x);}
            finally{transitionInFlight.set(false);}
        });
    }

    void gateAwakening(){
        if(!transitionInFlight.compareAndSet(false,true))return;
        try{
            if(!state.rewardPlanCreated())state.markRewardPlanCreated("finale-reward-plan");
            if(!state.rewardsCommitted()){
                if(rewardFuture==null){
                    rewardFuture=rewards.commitFinaleParticipants(state.finaleParticipants()).toCompletableFuture();
                    rewardFuture.whenComplete((ignored,failure)->{
                        if(!scheduleGlobal(()->{
                            if(failure==null){
                                try{state.markRewardsCommitted("profile-v2-rewards");}
                                catch(RuntimeException x){plugin.getLogger().severe("Prologue reward completion commit failed: "+x);}
                            }else plugin.getLogger().severe("Prologue Profile v2 reward delivery failed: "+failure);
                            rewardFuture=null;
                        })){
                            rewardFuture=null;
                        }
                    });
                }
                return;
            }
            ChronicleManager chronicle=ChronicleManager.current();
            if(!state.chronicleCommitted()){
                if(chronicle==null||!chronicle.publishExtraordinaryOnce("prologue-gate-open",List.of(
                        "&5&l— Rendkívüli Krónika — Olethropyla —",
                        "&7A Kárhozat Éjszakáján az Első Expedíció kitartott a Senkiföldjén.",
                        "&dOlethropyla megnyílt. A Kapun túli út immár járható.",
                        "&8A Kapu eredete és az ősi csend titkai továbbra is megfejtetlenek.")))
                    throw new IllegalStateException("Chronicle commit unavailable");
                state.markChronicleCommitted("chronicle:prologue-gate-open");
            }
            SeasonMonumentManager monument=SeasonMonumentManager.current();
            if(!state.monumentCommitted()){
                if(monument==null||!monument.recordPrologueOnce("prologue-first-expedition",
                        state.finaleParticipants().size(),System.currentTimeMillis()))
                    throw new IllegalStateException("Prologue monument commit unavailable");
                state.markMonumentCommitted("monument:first-expedition");
            }
            if(state.finalePhase()!=PrologueFinalePhase.EPILOGUE)
                state.checkpoint(PrologueFinalePhase.EPILOGUE,"finale:epilogue");
        }catch(RuntimeException x){plugin.getLogger().severe("Prologue settlement blocked: "+x);}
        finally{transitionInFlight.set(false);}
    }

    void epilogue(long phaseAgeMillis){
        long delay=Math.max(0L,config.getLong("world-events.prologue.finale.epilogue-seconds",6L))*1000L;
        if(phaseAgeMillis<delay||!transitionInFlight.compareAndSet(false,true))return;
        try{
            if(!state.seasonOneStarted()){
                seasonTransition.prepareSeasonOne(state.finalePhaseChangedAt());
                state.markSeasonOneStarted("season-one-prepared");
            }
            UUID id=state.finaleId();
            state.complete("season-one-transition");
            seasonTransition.activateSeasonOne();
            runState.clear(id);
            participants.stop();
            Bukkit.broadcast(Component.text("A Prologue véget ért. Megkezdődött az első szezon.",NamedTextColor.GOLD));
        }catch(RuntimeException x){plugin.getLogger().severe("Season 1 transition blocked; Prologue remains fail-closed: "+x);}
        finally{transitionInFlight.set(false);}
    }

    void visualAwakening(){
        if(!plugin.isEnabled())return;
        for(Player player:Bukkit.getOnlinePlayers()){
            try{
                player.getScheduler().run(plugin,t->player.showTitle(
                        net.kyori.adventure.title.Title.title(Component.text("OLETHROPYLA",NamedTextColor.DARK_PURPLE),
                                Component.text("A Kárhozat Kapuja megnyílt.",NamedTextColor.GOLD))),null);
            }catch(IllegalPluginAccessException ignored){return;}
        }
        Bukkit.broadcast(Component.text("Olethropyla stabil átjáróvá vált.",NamedTextColor.LIGHT_PURPLE));
    }

    private boolean scheduleGlobal(Runnable work){
        if(!plugin.isEnabled())return false;
        try{
            Bukkit.getGlobalRegionScheduler().run(plugin,t->work.run());
            return true;
        }catch(IllegalPluginAccessException ignored){
            return false;
        }
    }

    void resetTransient(){rewardFuture=null;transitionInFlight.set(false);}
}
