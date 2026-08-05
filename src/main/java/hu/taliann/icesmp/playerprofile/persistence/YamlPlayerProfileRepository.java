package hu.taliann.icesmp.playerprofile.persistence;

import hu.taliann.icesmp.playerprofile.domain.*;
import hu.taliann.icesmp.playerprofile.domain.section.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;

/** Modular human-readable YAML repository with per-section CAS and restart recovery. */
public final class YamlPlayerProfileRepository implements PlayerProfileRepository {
    public static final long MAX_YAML_BYTES=2L*1024L*1024L;
    private final Path root;
    private final ExecutorService io;
    private final ConcurrentMap<UUID,PlayerProfileSnapshot> cache=new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> inFlight=ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, Set<CompletableFuture<?>>> playerInFlight = new ConcurrentHashMap<>();
    private final AtomicBoolean accepting=new AtomicBoolean(true);
    private final Clock clock;
    private final FaultInjector faultInjector;

    public YamlPlayerProfileRepository(Path root){this(root,Clock.systemUTC(),Executors.newVirtualThreadPerTaskExecutor(),FaultInjector.none());}
    public YamlPlayerProfileRepository(Path root,Clock clock,ExecutorService io){this(root,clock,io,FaultInjector.none());}
    public YamlPlayerProfileRepository(Path root,Clock clock,ExecutorService io,FaultInjector faultInjector){this.root=Objects.requireNonNull(root).toAbsolutePath().normalize();this.clock=Objects.requireNonNull(clock);this.io=Objects.requireNonNull(io);this.faultInjector=Objects.requireNonNull(faultInjector);}

    @Override public CompletionStage<PlayerProfileSnapshot> load(UUID id){Objects.requireNonNull(id);PlayerProfileSnapshot hit=cache.get(id);if(hit!=null)return CompletableFuture.completedFuture(hit);return submit(id,()->withLock(id,()->loadLocked(id,true)));}
    @Override public CompletionStage<Optional<PlayerProfileSnapshot>> find(UUID id){
        Objects.requireNonNull(id);PlayerProfileSnapshot hit=cache.get(id);if(hit!=null)return CompletableFuture.completedFuture(Optional.of(hit));
        if(!Files.exists(profileDir(id).resolve("manifest.yml")))return CompletableFuture.completedFuture(Optional.empty());
        return submit(id,()->withLock(id,()->Optional.of(loadLocked(id,false))));
    }
    @Override public CompletionStage<Optional<PlayerProfileSnapshot>> findByName(String playerName){
        String wanted=required(playerName,"playerName").toLowerCase(Locale.ROOT);
        return submit(new UUID(0L,0L),()->{
            if(!Files.isDirectory(root))return Optional.empty();
            try(DirectoryStream<Path> dirs=Files.newDirectoryStream(root)){
                for(Path dir:dirs){if(!Files.isDirectory(dir))continue;UUID id;try{id=UUID.fromString(dir.getFileName().toString());}catch(IllegalArgumentException ignored){continue;}
                    Optional<PlayerProfileSnapshot> found=withLock(id,()->Files.exists(dir.resolve("manifest.yml"))?Optional.of(loadLocked(id,false)):Optional.empty());
                    if(found.isPresent()&&found.orElseThrow().identity().value().lastKnownName().equalsIgnoreCase(wanted))return found;
                }
            }
            return Optional.empty();
        });
    }
    @Override public CompletionStage<Set<UUID>> listPlayerIds(){
        return submit(new UUID(0L,1L),()->{
            if(!Files.isDirectory(root))return Set.of();
            TreeSet<UUID> ids=new TreeSet<>();
            try(DirectoryStream<Path> dirs=Files.newDirectoryStream(root)){
                for(Path dir:dirs){
                    if(!Files.isDirectory(dir)||!Files.isRegularFile(dir.resolve("manifest.yml")))continue;
                    try{ids.add(UUID.fromString(dir.getFileName().toString()));}
                    catch(IllegalArgumentException ignored){}
                }
            }
            return Collections.unmodifiableSet(new LinkedHashSet<>(ids));
        });
    }
    @Override public CompletionStage<Optional<ProfileSectionSnapshot<?>>> loadSection(UUID id,ProfileSectionId section){Objects.requireNonNull(section);return find(id).thenApply(p->p.flatMap(profile->profile.section(section)));}
    @Override public CompletionStage<SectionSaveResult> saveSection(UUID id,ProfileSectionId section,long expectedRevision,ProfileSectionSnapshot<?> next){return saveSection(id,section,expectedRevision,-1,next);}
    @Override public CompletionStage<SectionSaveResult> saveSection(UUID id,ProfileSectionId section,long expectedRevision,long expectedGeneration,ProfileSectionSnapshot<?> next){
        Objects.requireNonNull(id);Objects.requireNonNull(section);Objects.requireNonNull(next);if(next.sectionId()!=section)return CompletableFuture.completedFuture(new SectionSaveResult(SectionSaveResult.Status.REJECTED,cached(id).orElse(null),"section mismatch",""));
        return submit(id,()->withLock(id,()->saveLocked(id,section,expectedRevision,expectedGeneration,next)));
    }

    private SectionSaveResult saveLocked(UUID id,ProfileSectionId section,long expectedRevision,long expectedGeneration,ProfileSectionSnapshot<?> next)throws Exception{
        PlayerProfileSnapshot durable=loadLocked(id,true);ProfileSectionSnapshot<?> current=durable.section(section).orElseThrow();
        if(!current.health().usable())return new SectionSaveResult(SectionSaveResult.Status.SECTION_QUARANTINED,durable,current.health().diagnostic(),current.health().evidenceId());
        if(expectedGeneration>=0&&durable.profileRevision()!=expectedGeneration)return new SectionSaveResult(SectionSaveResult.Status.STALE_GENERATION,durable,"stale profile generation","");
        if(current.revision()!=expectedRevision)return new SectionSaveResult(SectionSaveResult.Status.STALE_REVISION,durable,"stale section revision","");
        long wanted=Math.addExact(expectedRevision,1L);if(next.revision()!=wanted)return new SectionSaveResult(SectionSaveResult.Status.REJECTED,durable,"section revision must advance exactly once","");
        long nextGeneration=Math.addExact(durable.profileRevision(),1L);Instant now=clock.instant();ProfileSectionSnapshot<?> normalized=new ProfileSectionSnapshot<>(section,next.schema(),next.revision(),now,next.value(),SectionHealth.healthy(),next.extensions());PlayerProfileSnapshot candidate=durable.withSection(normalized,nextGeneration,now);
        commitSingle(id,durable,candidate,section);cache.put(id,candidate);return new SectionSaveResult(SectionSaveResult.Status.COMMITTED,candidate,"","");
    }

    @Override public CompletionStage<QuarantineResult> quarantineSection(UUID id,ProfileSectionId section,byte[] payload,String reason){Objects.requireNonNull(payload);return submit(id,()->withLock(id,()->{
        PlayerProfileSnapshot durable=loadLocked(id,true);String evidence=UUID.randomUUID().toString();Path dir=profileDir(id).resolve("quarantine").resolve(section.id());Files.createDirectories(dir);atomicBytes(dir.resolve(evidence+".evidence"),payload);Path current=sectionPath(id,section);if(Files.exists(current))Files.copy(current,dir.resolve(evidence+".last-good.yml"),StandardCopyOption.REPLACE_EXISTING);Map<String,Object> marker=new LinkedHashMap<>();marker.put("evidence-id",evidence);marker.put("reason",cleanReason(reason));marker.put("created-at",clock.millis());writeYaml(quarantineMarker(id,section),marker);ProfileSectionSnapshot<?> old=durable.section(section).orElseThrow();ProfileSectionSnapshot<?> q=new ProfileSectionSnapshot<>(section,old.schema(),old.revision(),old.updatedAt(),old.value(),SectionHealth.quarantined(cleanReason(reason),evidence),old.extensions());PlayerProfileSnapshot candidate=replaceWithoutRevision(durable,q);cache.put(id,candidate);return new QuarantineResult(candidate,evidence,cleanReason(reason));}));}

    @Override public CompletionStage<RecoveryResult> recoverSection(UUID id,ProfileSectionId section,String evidenceId,String auditId){return submit(id,()->withLock(id,()->{
        String evidence=required(evidenceId,"evidenceId"),audit=required(auditId,"auditId");Path markerPath=quarantineMarker(id,section);if(!Files.exists(markerPath)){
            Path recovered=profileDir(id).resolve("quarantine").resolve(section.id()).resolve(evidence+".recovered.yml");if(Files.exists(recovered)){PlayerProfileSnapshot p=loadLocked(id,true);return new RecoveryResult(p,evidence,audit,true);}throw new PlayerProfileRepositoryException("section is not quarantined");}
        Map<String,Object> marker=readYaml(markerPath);if(!evidence.equals(string(marker.get("evidence-id"))))throw new PlayerProfileRepositoryException("quarantine evidence mismatch");Path dir=profileDir(id).resolve("quarantine").resolve(section.id());Path lastGood=dir.resolve(evidence+".last-good.yml");PlayerProfileSnapshot durable=loadLockedIgnoringMarker(id,section);ProfileSectionSnapshot<?> recovered=Files.exists(lastGood)?readSectionFile(lastGood,section):defaultSection(section,clock.instant());long revision=Math.addExact(durable.section(section).orElseThrow().revision(),1L);ProfileSectionSnapshot<?> next=new ProfileSectionSnapshot<>(section,recovered.schema(),revision,clock.instant(),recovered.value(),SectionHealth.recovered(evidence,audit),recovered.extensions());PlayerProfileSnapshot candidate=durable.withSection(next,Math.addExact(durable.profileRevision(),1L),clock.instant());commitSingle(id,durable,candidate,section);Map<String,Object> done=new LinkedHashMap<>(marker);done.put("recovered",true);done.put("recovery-audit-id",audit);done.put("recovered-at",clock.millis());writeYaml(dir.resolve(evidence+".recovered.yml"),done);Files.deleteIfExists(markerPath);cache.put(id,candidate);return new RecoveryResult(candidate,evidence,audit,false);}));}

    private PlayerProfileSnapshot loadLocked(UUID id,boolean initialize)throws Exception{return loadLocked(id,initialize,null);}
    private PlayerProfileSnapshot loadLockedIgnoringMarker(UUID id,ProfileSectionId ignored)throws Exception{return loadLocked(id,true,ignored);}
    private PlayerProfileSnapshot loadLocked(UUID id,boolean initialize,ProfileSectionId ignoredMarker)throws Exception{
        cleanupOrphanTemps(id);recoverWal(id);Path dir=profileDir(id);Path manifestPath=dir.resolve("manifest.yml");if(!Files.exists(manifestPath)){if(!initialize)throw new FileNotFoundException("profile missing");PlayerProfileSnapshot fresh=PlayerProfileSnapshot.greenfield(id,clock.instant());writeWholeProfile(fresh);cache.put(id,fresh);return fresh;}
        PlayerProfileManifest manifest=StructuredPlayerProfileYaml.decodeManifest(id,readYaml(manifestPath));EnumMap<ProfileSectionId,ProfileSectionSnapshot<?>> sections=new EnumMap<>(ProfileSectionId.class);
        for(ProfileSectionId sid:ProfileSectionId.values()){
            Path marker=quarantineMarker(id,sid);if(Files.exists(marker)&&sid!=ignoredMarker){Map<String,Object> m=readYaml(marker);String evidence=string(m.get("evidence-id")),reason=string(m.get("reason"));ProfileSectionSnapshot<?> fallback=defaultSection(sid,manifest.updatedAt());sections.put(sid,new ProfileSectionSnapshot<>(sid,sid.currentSchema(),manifest.sections().getOrDefault(sid,new SectionRevision(sid.currentSchema(),0)).revision(),manifest.updatedAt(),fallback.value(),SectionHealth.quarantined(reason,evidence)));continue;}
            Path path=sectionPath(id,sid);if(!Files.exists(path)){ProfileSectionSnapshot<?> def=defaultSection(sid,manifest.updatedAt());writeSectionFile(path,def);sections.put(sid,def);continue;}
            try{ProfileSectionSnapshot<?> section=readSectionFile(path,sid);SectionRevision declared=manifest.sections().get(sid);if(declared==null||declared.schema()!=section.schema()||declared.revision()!=section.revision())throw new IllegalArgumentException("manifest/section revision mismatch");sections.put(sid,section);}catch(Exception corrupt){byte[] evidence=Files.readAllBytes(path);String ev=UUID.randomUUID().toString();Path qdir=dir.resolve("quarantine").resolve(sid.id());Files.createDirectories(qdir);atomicBytes(qdir.resolve(ev+".evidence"),evidence);Map<String,Object> mark=Map.of("evidence-id",ev,"reason",safeMessage(corrupt),"created-at",clock.millis());writeYaml(marker,mark);ProfileSectionSnapshot<?> def=defaultSection(sid,manifest.updatedAt());sections.put(sid,new ProfileSectionSnapshot<>(sid,sid.currentSchema(),manifest.sections().getOrDefault(sid,new SectionRevision(sid.currentSchema(),0)).revision(),manifest.updatedAt(),def.value(),SectionHealth.quarantined(safeMessage(corrupt),ev)));}
        }
        PlayerProfileSnapshot result=PlayerProfileSnapshot.fromMap(id,manifest.profileRevision(),manifest.createdAt(),manifest.updatedAt(),sections);cache.put(id,result);return result;
    }

    private void cleanupOrphanTemps(UUID id)throws IOException{
        Path dir=profileDir(id);if(!Files.isDirectory(dir))return;
        try(var paths=Files.walk(dir)){for(Path path:paths.filter(Files::isRegularFile).filter(p->p.getFileName().toString().contains(".tmp-")).toList())Files.deleteIfExists(path);}
    }

    private void writeWholeProfile(PlayerProfileSnapshot p)throws Exception{Path dir=profileDir(p.playerId());Files.createDirectories(dir);for(var e:p.sectionMap().entrySet())writeSectionFile(sectionPath(p.playerId(),e.getKey()),e.getValue());writeYaml(dir.resolve("manifest.yml"),StructuredPlayerProfileYaml.encodeManifest(PlayerProfileManifest.from(p)));fsyncDirectory(dir);}
    private void commitSingle(UUID id,PlayerProfileSnapshot before,PlayerProfileSnapshot after,ProfileSectionId section)throws Exception{
        Path wal=profileDir(id).resolve("wal").resolve("section-"+UUID.randomUUID());Files.createDirectories(wal);Path oldSection=sectionPath(id,section);if(Files.exists(oldSection))Files.copy(oldSection,wal.resolve("old-section.yml"));Files.copy(profileDir(id).resolve("manifest.yml"),wal.resolve("old-manifest.yml"));writeSectionFile(wal.resolve("new-section.yml"),after.section(section).orElseThrow());writeYaml(wal.resolve("new-manifest.yml"),StructuredPlayerProfileYaml.encodeManifest(PlayerProfileManifest.from(after)));writeYaml(wal.resolve("intent.yml"),Map.of("section",section.id(),"before-generation",before.profileRevision(),"after-generation",after.profileRevision(),"state","prepared"));fsyncDirectory(wal);
        atomicMove(wal.resolve("new-section.yml"),oldSection);faultInjector.afterSectionsMovedBeforeManifest(id,Set.of(section));atomicMove(wal.resolve("new-manifest.yml"),profileDir(id).resolve("manifest.yml"));faultInjector.afterManifestMovedBeforeCleanup(id,Set.of(section));writeYaml(wal.resolve("committed.yml"),Map.of("state","committed"));deleteTree(wal);
    }
    private void recoverWal(UUID id)throws Exception{Path walRoot=profileDir(id).resolve("wal");if(!Files.isDirectory(walRoot))return;try(DirectoryStream<Path> dirs=Files.newDirectoryStream(walRoot)){for(Path wal:dirs){if(!Files.isDirectory(wal))continue;Map<String,Object> intent=Files.exists(wal.resolve("intent.yml"))?readYaml(wal.resolve("intent.yml")):Map.of();long after=intent.get("after-generation") instanceof Number n?n.longValue():Long.MAX_VALUE;long current=-1;Path manifest=profileDir(id).resolve("manifest.yml");if(Files.exists(manifest)){try{current=StructuredPlayerProfileYaml.decodeManifest(id,readYaml(manifest)).profileRevision();}catch(Exception ignored){}}
            if(current>=after){deleteTree(wal);continue;}String section=String.valueOf(intent.getOrDefault("section",""));if(!section.isBlank()&&Files.exists(wal.resolve("old-section.yml")))atomicMove(wal.resolve("old-section.yml"),sectionPath(id,ProfileSectionId.parse(section)));Path oldDir=wal.resolve("old");if(Files.isDirectory(oldDir))try(DirectoryStream<Path> oldFiles=Files.newDirectoryStream(oldDir,"*.yml")){for(Path oldFile:oldFiles){String name=oldFile.getFileName().toString();ProfileSectionId sid=ProfileSectionId.parse(name.substring(0,name.length()-4));atomicMove(oldFile,sectionPath(id,sid));}}if(Files.exists(wal.resolve("old-manifest.yml")))atomicMove(wal.resolve("old-manifest.yml"),manifest);deleteTree(wal);}}
    }


    public CompletionStage<PlayerProfileSnapshot> commitSections(UUID id,long expectedGeneration,Map<ProfileSectionId,ProfileSectionSnapshot<?>> replacements,String operationId,String fingerprint){
        Objects.requireNonNull(id);Objects.requireNonNull(replacements);return submit(id,()->withLock(id,()->commitSectionsLocked(id,expectedGeneration,replacements,operationId,fingerprint)));
    }
    private PlayerProfileSnapshot commitSectionsLocked(UUID id,long expectedGeneration,Map<ProfileSectionId,ProfileSectionSnapshot<?>> replacements,String operationId,String fingerprint)throws Exception{
        PlayerProfileSnapshot durable=loadLocked(id,true);if(durable.profileRevision()!=expectedGeneration)throw new PlayerProfileRepositoryException.RevisionConflict(expectedGeneration,durable.profileRevision(),"stale profile generation");
        EnumMap<ProfileSectionId,ProfileSectionSnapshot<?>> nextMap=new EnumMap<>(durable.sectionMap());Instant now=clock.instant();
        for(var e:replacements.entrySet()){ProfileSectionSnapshot<?> current=durable.section(e.getKey()).orElseThrow();ProfileSectionSnapshot<?> next=e.getValue();if(!current.health().usable())throw new PlayerProfileRepositoryException.Quarantined(e.getKey().id()+" section quarantined");if(next.sectionId()!=e.getKey()||next.revision()!=Math.addExact(current.revision(),1L))throw new IllegalArgumentException("invalid section CAS transition for "+e.getKey().id());nextMap.put(e.getKey(),new ProfileSectionSnapshot<>(e.getKey(),next.schema(),next.revision(),now,next.value(),SectionHealth.healthy(),next.extensions()));}
        long nextGeneration=Math.addExact(durable.profileRevision(),1L);PlayerProfileSnapshot candidate=PlayerProfileSnapshot.fromMap(id,nextGeneration,durable.createdAt(),now,nextMap);commitMultiple(id,durable,candidate,replacements.keySet(),operationId,fingerprint);cache.put(id,candidate);return candidate;
    }
    private void commitMultiple(UUID id,PlayerProfileSnapshot before,PlayerProfileSnapshot after,Set<ProfileSectionId> changed,String operationId,String fingerprint)throws Exception{
        Path wal=profileDir(id).resolve("wal").resolve("transaction-"+required(operationId,"operationId"));if(Files.exists(wal)){recoverWal(id);if(Files.exists(wal))throw new PlayerProfileRepositoryException("transaction WAL still active");}Files.createDirectories(wal.resolve("old"));Files.createDirectories(wal.resolve("new"));
        Files.copy(profileDir(id).resolve("manifest.yml"),wal.resolve("old-manifest.yml"));writeYaml(wal.resolve("new-manifest.yml"),StructuredPlayerProfileYaml.encodeManifest(PlayerProfileManifest.from(after)));
        for(ProfileSectionId sid:changed){Path old=sectionPath(id,sid);if(Files.exists(old))Files.copy(old,wal.resolve("old").resolve(sid.fileName()));writeSectionFile(wal.resolve("new").resolve(sid.fileName()),after.section(sid).orElseThrow());}
        writeYaml(wal.resolve("intent.yml"),Map.of("operation-id",operationId,"fingerprint",fingerprint==null?"":fingerprint,"before-generation",before.profileRevision(),"after-generation",after.profileRevision(),"sections",changed.stream().map(ProfileSectionId::id).sorted().toList(),"state","prepared"));fsyncDirectory(wal);
        for(ProfileSectionId sid:changed)atomicMove(wal.resolve("new").resolve(sid.fileName()),sectionPath(id,sid));faultInjector.afterSectionsMovedBeforeManifest(id,Set.copyOf(changed));atomicMove(wal.resolve("new-manifest.yml"),profileDir(id).resolve("manifest.yml"));faultInjector.afterManifestMovedBeforeCleanup(id,Set.copyOf(changed));writeYaml(wal.resolve("committed.yml"),Map.of("state","committed"));deleteTree(wal);
    }

    @Override public Optional<PlayerProfileSnapshot> cached(UUID id){return Optional.ofNullable(cache.get(id));}
    @Override public void invalidate(UUID id){cache.remove(id);}
    @Override public CompletionStage<Void> flush(UUID id){Objects.requireNonNull(id);return awaitSnapshot(id);}
    @Override public CompletionStage<Void> flushAll(){CompletableFuture<?>[] all=inFlight.toArray(CompletableFuture[]::new);return CompletableFuture.allOf(all);}
    @Override public CompletionStage<ShutdownResult> shutdown(Duration timeout){Objects.requireNonNull(timeout);accepting.set(false);return CompletableFuture.supplyAsync(()->{CompletableFuture<?>[] pending=inFlight.toArray(CompletableFuture[]::new);try{CompletableFuture.allOf(pending).get(Math.max(1,timeout.toMillis()),TimeUnit.MILLISECONDS);io.shutdown();return new ShutdownResult(true,0,"drained");}catch(Exception e){io.shutdownNow();return new ShutdownResult(false,(int)Arrays.stream(pending).filter(f->!f.isDone()).count(),"shutdown timeout");}});}
    CompletionStage<Void> awaitSnapshot(UUID id){Set<CompletableFuture<?>> set=playerInFlight.get(id);CompletableFuture<?>[] all=set==null?new CompletableFuture<?>[0]:set.toArray(CompletableFuture[]::new);return CompletableFuture.allOf(all);}

    <T> CompletionStage<T> submit(UUID id,Callable<T> work){if(!accepting.get())return CompletableFuture.failedFuture(new IllegalStateException("repository stopped"));CompletableFuture<T> future=new CompletableFuture<>();inFlight.add(future);Set<CompletableFuture<?>> playerSet=playerInFlight.computeIfAbsent(id,ignored->ConcurrentHashMap.newKeySet());playerSet.add(future);io.execute(()->{try{future.complete(work.call());}catch(Throwable t){future.completeExceptionally(t);}finally{inFlight.remove(future);playerSet.remove(future);if(playerSet.isEmpty())playerInFlight.remove(id,playerSet);}});return future;}
    private <T>T withLock(UUID id,Callable<T> work)throws Exception{Path dir=profileDir(id);Files.createDirectories(dir);String lockKey=root+"::"+id;ReentrantLock jvmLock=JVM_LOCKS.computeIfAbsent(lockKey,ignored->new ReentrantLock(true));jvmLock.lockInterruptibly();try(FileChannel channel=FileChannel.open(dir.resolve("profile.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock ignored=channel.lock()){return work.call();}finally{jvmLock.unlock();if(!jvmLock.hasQueuedThreads())JVM_LOCKS.remove(lockKey,jvmLock);}}
    private Path profileDir(UUID id){return root.resolve(id.toString());}private Path sectionPath(UUID id,ProfileSectionId sid){return profileDir(id).resolve(sid.fileName());}private Path quarantineMarker(UUID id,ProfileSectionId sid){return profileDir(id).resolve("quarantine").resolve(sid.id()+".marker.yml");}
    private void writeSectionFile(Path path,ProfileSectionSnapshot<?> s)throws Exception{writeYaml(path,StructuredPlayerProfileYaml.encodeSection(s));}
    private ProfileSectionSnapshot<?> readSectionFile(Path path,ProfileSectionId sid)throws Exception{return StructuredPlayerProfileYaml.decodeSection(sid,readYaml(path));}
    private Map<String,Object> readYaml(Path path)throws IOException,InvalidConfigurationException{long size=Files.size(path);if(size>MAX_YAML_BYTES)throw new IOException("YAML file exceeds size limit");CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);String text=decoder.decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();if(text.contains("!!")||text.contains("!<"))throw new InvalidConfigurationException("YAML object tags are forbidden");YamlConfiguration yaml=new YamlConfiguration();yaml.loadFromString(text);return StructuredPlayerProfileYaml.normalizeRoot(yaml.getValues(false));}
    private void writeYaml(Path path,Map<String,Object> values)throws IOException{YamlConfiguration yaml=new YamlConfiguration();values.forEach(yaml::set);atomicBytes(path,yaml.saveToString().getBytes(StandardCharsets.UTF_8));}
    private void atomicBytes(Path path,byte[] bytes)throws IOException{Files.createDirectories(path.getParent());Path tmp=path.resolveSibling(path.getFileName()+".tmp-"+UUID.randomUUID());try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){c.write(ByteBuffer.wrap(bytes));c.force(true);}atomicMove(tmp,path);fsyncDirectory(path.getParent());}
    private static void atomicMove(Path from,Path to)throws IOException{Files.createDirectories(to.getParent());try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}}
    private static void fsyncDirectory(Path dir){try(FileChannel c=FileChannel.open(dir,StandardOpenOption.READ)){c.force(true);}catch(Exception ignored){}}
    private static void deleteTree(Path p)throws IOException{if(!Files.exists(p))return;Files.walkFileTree(p,new SimpleFileVisitor<>(){@Override public FileVisitResult visitFile(Path f,BasicFileAttributes a)throws IOException{Files.deleteIfExists(f);return FileVisitResult.CONTINUE;}@Override public FileVisitResult postVisitDirectory(Path d,IOException e)throws IOException{Files.deleteIfExists(d);return FileVisitResult.CONTINUE;}});}
    private static String required(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" required");return v.trim();}private static String cleanReason(String v){String s=v==null?"profile section validation failed":v.trim();return s.length()>512?s.substring(0,512):s;}private static String safeMessage(Throwable t){String s=t.getMessage();return cleanReason(s==null?t.getClass().getSimpleName():s);}private static String string(Object v){if(!(v instanceof String s))throw new IllegalArgumentException("string required");return s;}
    private static PlayerProfileSnapshot replaceWithoutRevision(PlayerProfileSnapshot p,ProfileSectionSnapshot<?> s){EnumMap<ProfileSectionId,ProfileSectionSnapshot<?>> m=new EnumMap<>(p.sectionMap());m.put(s.sectionId(),s);return PlayerProfileSnapshot.fromMap(p.playerId(),p.profileRevision(),p.createdAt(),p.updatedAt(),m);}
    private static ProfileSectionSnapshot<?> defaultSection(ProfileSectionId id,Instant now){PlayerProfileSnapshot p=PlayerProfileSnapshot.greenfield(new UUID(0,0),now);return p.section(id).orElseThrow();}

    public interface FaultInjector {
        void afterSectionsMovedBeforeManifest(UUID playerId, Set<ProfileSectionId> changed) throws IOException;
        void afterManifestMovedBeforeCleanup(UUID playerId, Set<ProfileSectionId> changed) throws IOException;
        static FaultInjector none(){return new FaultInjector(){@Override public void afterSectionsMovedBeforeManifest(UUID playerId,Set<ProfileSectionId> changed){}@Override public void afterManifestMovedBeforeCleanup(UUID playerId,Set<ProfileSectionId> changed){}};}
    }
}
