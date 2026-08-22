package hu.taliann.icesmp.prologue;

import hu.taliann.icesmp.managers.ConfigManager;
import hu.taliann.icesmp.managers.EventSpawnGuard;
import hu.taliann.icesmp.managers.MajorEventGate;
import hu.taliann.icesmp.utils.TransientEntities;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Folia-safe reusable Gate Breach / finale encounter engine. */
public final class PrologueEncounterEngine implements Listener {
    private static final String EVENT_KEY="prologue";
    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final PrologueWorldAccess worldAccess;
    private final PrologueParticipantTracker participants;
    private final EventSpawnGuard spawnGuard;
    private final MajorEventGate eventGate;
    private final NamespacedKey prologueMobKey,encounterKey,roleKey;
    private final Set<UUID> transientEntities=ConcurrentHashMap.newKeySet();
    private final Map<UUID,String> entityEncounters=new ConcurrentHashMap<>();
    private final Map<UUID,MobHandle> liveMobs=new ConcurrentHashMap<>();
    private volatile ActiveEncounter activeEncounter;
    private volatile UUID bossId;
    private volatile boolean shuttingDown;

    public PrologueEncounterEngine(JavaPlugin plugin,ConfigManager config,PrologueWorldAccess worldAccess,
                                   PrologueParticipantTracker participants,EventSpawnGuard spawnGuard,
                                   MajorEventGate eventGate){
        this.plugin=plugin;this.config=config;this.worldAccess=worldAccess;this.participants=participants;
        this.spawnGuard=spawnGuard;this.eventGate=eventGate;
        prologueMobKey=new NamespacedKey(plugin,"prologue_mob");
        encounterKey=new NamespacedKey(plugin,"prologue_encounter");
        roleKey=new NamespacedKey(plugin,"prologue_role");
    }

    public boolean isActive(){ActiveEncounter e=activeEncounter;return e!=null&&!e.finished.get();}
    public boolean isPaused(){ActiveEncounter e=activeEncounter;return e!=null&&!e.finished.get()&&e.paused.get();}
    public boolean bossAlive(){UUID id=bossId;return id!=null&&TransientEntities.isAlive(id);}
    public UUID bossId(){return bossId;}
    public long activeRemainingTimeoutMillis(){ActiveEncounter e=activeEncounter;return e==null||e.finished.get()?-1L:e.clock.remainingMillis(now());}
    public boolean isPrologueEntity(Entity e){return e!=null&&e.getPersistentDataContainer().has(prologueMobKey,PersistentDataType.BYTE);}
    public boolean isBoss(Entity e){return isPrologueEntity(e)&&"boss".equals(e.getPersistentDataContainer().get(roleKey,PersistentDataType.STRING));}

    public boolean startBreach(BreachSeverity severity,int participantCount,Runnable completion,Consumer<String> failure){
        if(!PrologueContentPolicy.active(config)||severity==null||isActive()
                ||(eventGate!=null&&!eventGate.mayStartNaturally(EVENT_KEY)))return false;
        return startBreachWave(severity,1,participantCount,completion==null?()->{}:completion,failure==null?x->{}:failure);
    }
    private boolean startBreachWave(BreachSeverity severity,int wave,int players,Runnable completion,Consumer<String> failure){
        boolean elite=wave==severity.waves();
        return startWave("breach-"+severity.name().toLowerCase(Locale.ROOT)+'-'+wave,severity,players,elite,()->{
            if(wave>=severity.waves()){completion.run();return;}
            long delay=Math.max(1L,config.getLong("world-events.prologue.breach.wave-delay-seconds",5L))*20L;
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin,t->startBreachWave(severity,wave+1,players,completion,failure),delay);
        },failure);
    }

    public boolean startWave(String id,BreachSeverity severity,int players,boolean elite,Runnable done,Consumer<String> fail){
        return startWave(id,severity,players,elite,done,fail,-1L);
    }
    boolean startWave(String id,BreachSeverity severity,int players,boolean elite,Runnable done,Consumer<String> fail,long timeoutOverride){
        if(!PrologueContentPolicy.active(config)||id==null||id.isBlank()||severity==null||isActive())return false;
        Location anchor=worldAccess.breachAnchor();
        if(anchor==null||anchor.getWorld()==null){if(fail!=null)fail.accept("Nincs beállítva Prologue breach/gate anchor.");return false;}
        int min=Math.max(1,config.getInt("world-events.prologue.scaling.minimum-players",5));
        int max=Math.max(min,config.getInt("world-events.prologue.scaling.maximum-players",45));
        int base=Math.max(1,config.getInt("world-events.prologue.breach."+severity.name().toLowerCase(Locale.ROOT)+".base-count",
                switch(severity){case MINOR->4;case MAJOR->7;case CRITICAL->10;}));
        int count=PrologueScaling.mobCount((int)Math.round(base*severity.mobMultiplier()),players,min,max,
                Math.max(0D,config.getDouble("world-events.prologue.scaling.mob-per-extra-player",.40D)),
                Math.max(1,config.getInt("world-events.prologue.scaling.minimum-mob-count",4)),
                Math.max(4,config.getInt("world-events.prologue.scaling.maximum-mob-count",28)));
        long fallback=Math.max(30L,config.getLong("world-events.prologue.breach.timeout-seconds",300L))*1000L;
        ActiveEncounter e=new ActiveEncounter(id,done==null?()->{}:done,fail==null?x->{}:fail,count,timeout(timeoutOverride,fallback));
        activeEncounter=e;warningPulse(anchor,severity);
        double radius=Math.max(3D,config.getDouble("world-events.prologue.breach.spawn-radius",10D));
        List<EntityType> types=mobTypes("world-events.prologue.breach.mob-types",List.of(EntityType.PIGLIN,EntityType.PIGLIN_BRUTE,EntityType.HOGLIN,EntityType.BLAZE,EntityType.WITHER_SKELETON));
        for(int i=0;i<count;i++){
            int index=i,x=anchor.getBlockX()+(int)Math.round(Math.cos(Math.PI*2D*i/Math.max(1,count))*radius),
                    z=anchor.getBlockZ()+(int)Math.round(Math.sin(Math.PI*2D*i/Math.max(1,count))*radius);
            EntityType type=types.get(i%types.size());Location owner=new Location(anchor.getWorld(),x,anchor.getBlockY(),z);
            scheduleSpawn(e,owner,()->spawnMob(e,topOf(anchor.getWorld(),x,z),type,elite&&index==0?"elite":"wave"));
        }
        armTimeout(e,"A breach időkorlátja lejárt.");return true;
    }

    public boolean startBoss(int players,Runnable victory,Consumer<String> failure){return startBoss(players,victory,failure,-1L);}
    boolean startBoss(int players,Runnable victory,Consumer<String> failure,long timeoutOverride){
        if(!PrologueContentPolicy.active(config)||isActive())return false;Location anchor=worldAccess.bossAnchor();
        if(anchor==null||anchor.getWorld()==null){if(failure!=null)failure.accept("Nincs beállítva Prologue boss/gate anchor.");return false;}
        long fallback=Math.max(60L,config.getLong("world-events.prologue.finale.boss.timeout-seconds",900L))*1000L;
        ActiveEncounter e=new ActiveEncounter("prologue-boss",victory==null?()->{}:victory,failure==null?x->{}:failure,1,timeout(timeoutOverride,fallback));
        e.bossEncounter=true;activeEncounter=e;
        scheduleSpawn(e,anchor,()->{
            anchor.getWorld().playSound(anchor,Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,1.8F,.55F);
            anchor.getWorld().spawnParticle(Particle.REVERSE_PORTAL,anchor.clone().add(0,1,0),60,1.5,2,1.5,.08);
            Mob boss=spawnMob(e,topOf(anchor.getWorld(),anchor.getBlockX(),anchor.getBlockZ()),EntityType.WITHER_SKELETON,"boss");
            if(boss==null){failEncounter(e,"A Prologue boss nem spawnolható a beállított anchoron.");return;}
            bossId=boss.getUniqueId();configureBoss(boss,players,e);
        });
        armTimeout(e,"A Hasadék Őrének időkorlátja lejárt.");return true;
    }

    private void scheduleSpawn(ActiveEncounter e,Location owner,Runnable work){
        if(e.finished.get()||shuttingDown){pendingDone(e);return;}
        try{plugin.getServer().getRegionScheduler().run(plugin,owner,t->runSpawn(e,owner,work));}
        catch(IllegalPluginAccessException x){pendingDone(e);if(!shuttingDown)failEncounter(e,"A Prologue spawn scheduler nem elérhető.");}
    }
    private void runSpawn(ActiveEncounter e,Location owner,Runnable work){
        if(e.finished.get()||activeEncounter!=e||shuttingDown){pendingDone(e);return;}
        if(e.paused.get()){
            try{plugin.getServer().getRegionScheduler().runDelayed(plugin,owner,t->runSpawn(e,owner,work),20L);}
            catch(IllegalPluginAccessException x){pendingDone(e);if(!shuttingDown)failEncounter(e,"A pause-olt Prologue spawn nem ütemezhető újra.");}
            return;
        }
        try{work.run();}finally{pendingDone(e);}
    }
    private void pendingDone(ActiveEncounter e){e.pendingSpawns.updateAndGet(v->Math.max(0,v-1));checkWaveComplete(e);}
    private void armTimeout(ActiveEncounter e,String reason){
        e.timeout=Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,t->{
            if(e.finished.get()||activeEncounter!=e){t.cancel();return;}
            if(e.clock.expired(now())){t.cancel();failEncounter(e,reason);}
        },20L,20L);
    }

    public long pauseActive(){
        ActiveEncounter e=activeEncounter;if(e==null||e.finished.get())return -1L;
        if(e.paused.compareAndSet(false,true)){e.clock.pause(now());setMobPause(e,true);}
        return e.clock.remainingMillis(now());
    }
    public void resumeActive(){
        ActiveEncounter e=activeEncounter;if(e==null||e.finished.get())return;
        if(e.paused.compareAndSet(true,false)){e.clock.resume(now());setMobPause(e,false);
            try{Bukkit.getGlobalRegionScheduler().run(plugin,t->checkWaveComplete(e));}catch(IllegalPluginAccessException ignored){}}
    }
    private void setMobPause(ActiveEncounter e,boolean paused){
        hu.taliann.icesmp.pve.AuthoredCreatureSpawnService spawns=
                hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.current();
        for(UUID id:Set.copyOf(e.mobs)){
            MobHandle h=liveMobs.get(id);if(h==null)continue;
            try{h.scheduler.run(plugin,t->{Mob m=h.mob;if(!m.isValid()||m.isDead()){liveMobs.remove(id,h);return;}
                m.setAI(!paused);m.setInvulnerable(paused);if(paused){m.setTarget(null);if(spawns!=null)spawns.pause(m);}
                else if(spawns!=null)spawns.resume(m);},()->liveMobs.remove(id,h));}
            catch(IllegalPluginAccessException x){liveMobs.remove(id,h);}
        }
    }

    private Mob spawnMob(ActiveEncounter e,Location spot,EntityType type,String role){
        if(spot==null||spot.getWorld()==null||e.finished.get()||e.paused.get())return null;
        if(spawnGuard!=null&&(spawnGuard.isBlocked(EVENT_KEY,spot)||spawnGuard.isUnsafeSurface(EVENT_KEY,spot.getWorld(),spot.getBlockX(),spot.getBlockZ())))return null;
        hu.taliann.icesmp.pve.AuthoredCreatureSpawnService spawns=
                hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.current();if(spawns==null)return null;
        String template=templateFor(type,role);int level=levelFor(role);
        Mob mob=spawns.spawn(spot,hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.Request.template(
                "prologue",e.id,role,template,level,
                hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.RewardOwner.NONE,true,
                1D,1D,0L));if(mob==null)return null;
        mob.getPersistentDataContainer().set(prologueMobKey,PersistentDataType.BYTE,(byte)1);
        mob.getPersistentDataContainer().set(encounterKey,PersistentDataType.STRING,e.id);
        mob.getPersistentDataContainer().set(roleKey,PersistentDataType.STRING,role);
        mob.setRemoveWhenFarAway(false);mob.setPersistent(false);mob.setGlowing("elite".equals(role)||"boss".equals(role));
        TransientEntities.register(plugin,mob);UUID id=mob.getUniqueId();transientEntities.add(id);entityEncounters.put(id,e.id);e.mobs.add(id);liveMobs.put(id,new MobHandle(mob,mob.getScheduler()));
        if("elite".equals(role)){mob.customName(Component.text("Hasadékbajnok",NamedTextColor.DARK_RED));mob.setCustomNameVisible(true);}
        return mob;
    }

    private void configureBoss(Mob boss,int players,ActiveEncounter e){
        int min=Math.max(1,config.getInt("world-events.prologue.scaling.minimum-players",5)),max=Math.max(min,config.getInt("world-events.prologue.scaling.maximum-players",45));
        double healthMultiplier=PrologueScaling.bossHealth(1D,players,min,max,
                Math.max(0D,config.getDouble("world-events.prologue.scaling.boss-health-per-extra-player",.075D)),
                Math.max(1D,config.getDouble("world-events.prologue.scaling.boss-health-maximum-multiplier",4D)));
        hu.taliann.icesmp.pve.AuthoredCreatureSpawnService spawns=
                hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.current();
        if(spawns!=null)spawns.applyParticipantModifier(boss,healthMultiplier,1D,"prologue:participants");
        boss.customName(Component.text(config.getString("world-events.prologue.finale.boss.name","A Hasadék Őre"),NamedTextColor.DARK_RED));boss.setCustomNameVisible(true);boss.setGlowing(true);
    }

    private int levelFor(String role){return switch(role){case "boss"->Math.max(1,config.getInt("world-events.prologue.finale.boss.level",55));case "elite"->Math.max(1,config.getInt("world-events.prologue.breach.elite-level",28));case "add"->Math.max(1,config.getInt("world-events.prologue.finale.boss.add-level",32));default->Math.max(1,config.getInt("world-events.prologue.breach.mob-level",20));};}
    private static String templateFor(EntityType type,String role){if("boss".equals(role))return "prologue_finale_boss";if("elite".equals(role))return "prologue_breach_elite";if("add".equals(role))return switch(type){case BLAZE->"prologue_flame_add";case PIGLIN_BRUTE->"prologue_brute_add";default->"prologue_bone_add";};return switch(type){case PIGLIN->"prologue_breach_piglin";case PIGLIN_BRUTE->"prologue_breach_brute";case HOGLIN->"prologue_breach_hoglin";case BLAZE->"prologue_breach_blaze";case WITHER_SKELETON->"prologue_breach_skeleton";default->throw new IllegalArgumentException("Unsupported Prologue wave type: "+type);};}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void onAnyDamage(EntityDamageEvent event){if(pausedFor(event.getEntity().getUniqueId())!=null)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void onDamage(EntityDamageByEntityEvent event){
        if(pausedFor(event.getEntity().getUniqueId())!=null||pausedDamager(event.getDamager())!=null){event.setCancelled(true);return;}
        if(!isPrologueEntity(event.getEntity()))return;Player p=playerAttacker(event.getDamager());if(p!=null)participants.recordDamage(p.getUniqueId(),event.getFinalDamage(),isBoss(event.getEntity()));
    }
    private ActiveEncounter pausedDamager(Entity d){ActiveEncounter e=pausedFor(d.getUniqueId());if(e!=null)return e;
        return d instanceof Projectile p&&p.getShooter() instanceof Entity shooter?pausedFor(shooter.getUniqueId()):null;}
    private ActiveEncounter pausedFor(UUID id){ActiveEncounter e=activeEncounter;if(e==null||e.finished.get()||!e.paused.get())return null;return e.id.equals(entityEncounters.get(id))?e:null;}

    @EventHandler public void onDeath(EntityDeathEvent event){
        LivingEntity entity=event.getEntity();if(!isPrologueEntity(entity))return;event.getDrops().clear();event.setDroppedExp(0);UUID id=entity.getUniqueId();
        transientEntities.remove(id);liveMobs.remove(id);TransientEntities.markGone(id);String owner=entityEncounters.remove(id);ActiveEncounter e=activeEncounter;
        if(e==null||owner==null||!e.id.equals(owner))return;e.mobs.remove(id);
        if(id.equals(bossId)){if(!e.completionStarted.compareAndSet(false,true))return;try{e.completion.run();}catch(RuntimeException x){failEncounter(e,"A boss-győzelem callback hibát dobott.");return;}
            bossId=null;if(e.finished.compareAndSet(false,true)){cancelTimeout(e);cleanupEncounterEntities(e);if(activeEncounter==e)activeEncounter=null;}return;}
        checkWaveComplete(e);
    }
    private void checkWaveComplete(ActiveEncounter e){
        if(e.paused.get()||e.bossEncounter||e.pendingSpawns.get()>0||!e.mobs.isEmpty())return;
        if(e.finished.compareAndSet(false,true)){cancelTimeout(e);if(activeEncounter==e)activeEncounter=null;if(e.completionStarted.compareAndSet(false,true))e.completion.run();}
    }
    public void abortActive(String reason){ActiveEncounter e=activeEncounter;if(e!=null)failEncounter(e,reason==null||reason.isBlank()?"Az encounter megszakadt.":reason);}
    public void abortActiveSilently(){ActiveEncounter e=activeEncounter;if(e==null||!e.finished.compareAndSet(false,true))return;cancelTimeout(e);cleanupEncounterEntities(e);if(activeEncounter==e)activeEncounter=null;bossId=null;}
    private void failEncounter(ActiveEncounter e,String reason){if(!e.finished.compareAndSet(false,true))return;cancelTimeout(e);cleanupEncounterEntities(e);if(activeEncounter==e)activeEncounter=null;bossId=null;e.failure.accept(reason);}
    private void cleanupEncounterEntities(ActiveEncounter e){hu.taliann.icesmp.pve.AuthoredCreatureSpawnService spawns=hu.taliann.icesmp.pve.AuthoredCreatureSpawnService.current();for(UUID id:Set.copyOf(e.mobs)){MobHandle h=liveMobs.get(id);if(spawns!=null&&h!=null)h.scheduler.run(plugin,t->spawns.detach(h.mob),null);TransientEntities.removeById(plugin,id);transientEntities.remove(id);entityEncounters.remove(id);liveMobs.remove(id);}e.mobs.clear();}
    public void shutdown(){shuttingDown=true;ActiveEncounter e=activeEncounter;if(e!=null&&e.finished.compareAndSet(false,true)){cancelTimeout(e);cleanupEncounterEntities(e);}activeEncounter=null;bossId=null;TransientEntities.removeAllOnShutdown(transientEntities);transientEntities.clear();entityEncounters.clear();liveMobs.clear();}

    private void warningPulse(Location anchor,BreachSeverity severity){plugin.getServer().getRegionScheduler().run(plugin,anchor,t->{if(anchor.getWorld()==null)return;anchor.getWorld().playSound(anchor,Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,severity==BreachSeverity.CRITICAL?1.8F:1.2F,severity==BreachSeverity.MINOR?.9F:.6F);anchor.getWorld().spawnParticle(Particle.REVERSE_PORTAL,anchor.clone().add(0,1,0),severity==BreachSeverity.CRITICAL?72:36,1.5,2,1.5,.08);});}
    private Location topOf(World world,int x,int z){return new Location(world,x+.5,world.getHighestBlockYAt(x,z)+1,z+.5);}
    private List<EntityType> mobTypes(String path,List<EntityType> fallback){List<EntityType> out=new ArrayList<>();for(String raw:config.getStringList(path))try{EntityType t=EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));if(t.getEntityClass()!=null&&Mob.class.isAssignableFrom(t.getEntityClass()))out.add(t);}catch(IllegalArgumentException ignored){}return out.isEmpty()?fallback:List.copyOf(out);}
    private static Player playerAttacker(Entity d){if(d instanceof Player p)return p;if(d instanceof Projectile p&&p.getShooter() instanceof Player player)return player;return null;}
    private static long timeout(long override,long fallback){return override>0?override:fallback;}
    private static long now(){return System.nanoTime()/1_000_000L;}
    private static void cancelTimeout(ActiveEncounter e){ScheduledTask t=e.timeout;if(t!=null)t.cancel();}

    private record MobHandle(Mob mob,EntityScheduler scheduler){}
    private static final class ActiveEncounter{
        final String id;final Runnable completion;final Consumer<String> failure;final AtomicInteger pendingSpawns;
        final Set<UUID> mobs=ConcurrentHashMap.newKeySet();final AtomicBoolean finished=new AtomicBoolean(),completionStarted=new AtomicBoolean(),paused=new AtomicBoolean();
        final ProloguePauseClock clock;volatile ScheduledTask timeout;volatile boolean bossEncounter;
        ActiveEncounter(String id,Runnable completion,Consumer<String> failure,int pending,long timeout){this.id=id;this.completion=completion;this.failure=failure;pendingSpawns=new AtomicInteger(pending);clock=new ProloguePauseClock(timeout,now());}
    }
}
