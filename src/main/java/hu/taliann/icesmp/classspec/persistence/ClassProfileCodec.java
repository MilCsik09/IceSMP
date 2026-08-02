package hu.taliann.icesmp.classspec.persistence;

import hu.taliann.icesmp.classspec.domain.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Deterministic bounded ICS2 codec. The SHA-256 digest detects accidental corruption; it is not
 * authentication because an offline editor can recompute an unkeyed digest.
 */
public final class ClassProfileCodec {
    public static final byte[] MAGIC={'I','C','S','2'};
    public static final int CODEC_VERSION=2;
    public static final int DIGEST_BYTES=32;
    private static final int HEADER_BYTES=MAGIC.length+Integer.BYTES+Integer.BYTES;
    private final Limits limits;
    public ClassProfileCodec(){this(Limits.defaults());}
    public ClassProfileCodec(Limits limits){this.limits=Objects.requireNonNull(limits);}

    public byte[] encode(ClassProfile profile){
        Objects.requireNonNull(profile,"profile");
        Writer payload=new Writer(limits.maxPayloadBytes(),limits);writeProfile(payload,profile);byte[] body=payload.bytes();
        Writer result=new Writer(HEADER_BYTES+body.length+DIGEST_BYTES,limits);result.raw(MAGIC);result.i(CODEC_VERSION);result.i(body.length);result.raw(body);result.raw(sha256(result.bytes()));return result.bytes();
    }
    public ClassProfile decode(byte[] encoded)throws DecodeException{return decodeForOwner(encoded,null);}
    public ClassProfile decodeForOwner(byte[] encoded,UUID expectedOwner)throws DecodeException{
        if(encoded==null)throw new DecodeException("Profile payload is null");
        if(encoded.length<HEADER_BYTES+DIGEST_BYTES)throw new DecodeException("Truncated ICS2 envelope");
        if(encoded.length>limits.maxPayloadBytes()+HEADER_BYTES+DIGEST_BYTES)throw new DecodeException("ICS2 envelope exceeds size limit");
        Reader e=new Reader(encoded,limits);if(!Arrays.equals(MAGIC,e.raw(MAGIC.length)))throw new DecodeException("Invalid ICS2 magic");
        int version=e.i();if(version!=CODEC_VERSION)throw new DecodeException("Unsupported profile codec version: "+version);
        int len=e.length(limits.maxPayloadBytes(),"payload");long expected=(long)HEADER_BYTES+len+DIGEST_BYTES;
        if(expected!=encoded.length)throw new DecodeException(expected<encoded.length?"Trailing data after ICS2 envelope":"Truncated ICS2 payload");
        byte[] payload=e.raw(len),stored=e.raw(DIGEST_BYTES);if(!MessageDigest.isEqual(stored,sha256(Arrays.copyOf(encoded,HEADER_BYTES+len))))throw new DecodeException("ICS2 SHA-256 digest mismatch");e.exhausted("envelope");
        Reader r=new Reader(payload,limits);ClassProfile p;try{p=readProfile(r);}catch(DecodeException x){throw x;}catch(RuntimeException x){throw new DecodeException("Decoded profile violates domain invariants",x);}r.exhausted("profile payload");
        if(expectedOwner!=null&&!expectedOwner.equals(p.ownerId()))throw new DecodeException("Profile payload owner does not match repository key");return p;
    }
    public static String digestHex(byte[] payload){StringBuilder b=new StringBuilder(64);for(byte v:sha256(payload)){b.append(Character.forDigit((v>>>4)&15,16)).append(Character.forDigit(v&15,16));}return b.toString();}

    private void writeProfile(Writer w,ClassProfile p){
        w.uuid(p.ownerId());w.i(p.schemaVersion());w.l(p.revision());w.en(p.status());w.s(p.primaryClassId());w.i(p.classLevel());w.i(p.classExperience());w.nullable(p.activeSlot());w.bool(p.secondSpecUnlocked());w.count(p.loadouts().size(),"loadouts");p.loadouts().forEach(x->writeLoadout(w,x));
        w.count(p.operations().size(),"operations");p.operations().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->writeOperation(w,e.getValue()));
        w.s(p.diagnostics().quarantineEvidenceId());w.s(p.diagnostics().quarantineReason());w.s(p.diagnostics().recoveryAuditId());w.s(p.diagnostics().sessionBlockReason());
    }
    private ClassProfile readProfile(Reader r)throws DecodeException{
        UUID owner=r.uuid();int schema=r.i();long revision=r.l();ProfileStatus status=r.en(ProfileStatus.class);String clazz=r.s();int level=r.i();int experience=r.i();LoadoutSlot active=r.nullable(LoadoutSlot.class);boolean second=r.bool();int count=r.count("loadouts");if(count!=2)throw new DecodeException("Profile v2 must contain exactly two loadouts");ClassLoadout a=readLoadout(r),b=readLoadout(r);
        int opCount=r.count("operations");if(opCount>ClassProfile.MAX_OPERATION_RECEIPTS)throw new DecodeException("Too many operation receipts");Map<String,ProfileOperation> ops=new LinkedHashMap<>();for(int i=0;i<opCount;i++){ProfileOperation op=readOperation(r);if(ops.putIfAbsent(op.operationId(),op)!=null)throw new DecodeException("Duplicate operation receipt");}
        ProfileDiagnostics d=new ProfileDiagnostics(r.s(),r.s(),r.s(),r.s());return ClassProfile.builder(owner).schemaVersion(schema).revision(revision).status(status).primaryClassId(clazz).classLevel(level).classExperience(experience).activeSlot(active).secondSpecUnlocked(second).loadout(LoadoutSlot.FIRST,a).loadout(LoadoutSlot.SECOND,b).operations(ops).diagnostics(d).build();
    }
    private void writeOperation(Writer w,ProfileOperation o){w.s(o.operationId());w.en(o.type());w.en(o.status());w.s(o.target());w.s(o.amount());w.s(o.currencyId());w.l(o.startedRevision());w.s(o.detail());}
    private ProfileOperation readOperation(Reader r)throws DecodeException{return new ProfileOperation(r.s(),r.en(ProfileOperationType.class),r.en(ProfileOperationStatus.class),r.s(),r.s(),r.s(),r.l(),r.s());}
    private void writeLoadout(Writer w,ClassLoadout x){
        w.s(x.specializationId());w.en(x.status());w.bool(x.sealReason()!=null);if(x.sealReason()!=null){w.count(x.sealReason().gateIds().size(),"seal causes");x.sealReason().gateIds().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{w.en(e.getKey());w.s(e.getValue());});w.s(x.sealReason().detail());}
        w.stringMap(x.doctrineChoices());w.i(x.mastery().rank());w.l(x.mastery().experience());w.bool(x.soulbond()!=null);if(x.soulbond()!=null){w.uuid(x.soulbond().signatureId());w.i(x.soulbond().evolution());w.stringList(x.soulbond().modules());w.l(x.soulbond().revision());w.s(x.soulbond().recoveryNote());}
        w.stringSet(x.favoriteSpells());w.s(x.selectedSpell());w.en(x.capstoneStatus());w.count(x.companionRoster().size(),"companions");x.companionRoster().entrySet().stream().sorted(Comparator.comparing(e->e.getKey().toString())).forEach(e->writeCompanion(w,e.getValue()));w.stringMap(x.mechanicState());w.s(x.diagnosticNote());
    }
    private ClassLoadout readLoadout(Reader r)throws DecodeException{
        String spec=r.s();LoadoutStatus status=r.en(LoadoutStatus.class);SealReason seal=null;if(r.bool()){int n=r.count("seal causes");if(n<1||n>SealCause.values().length)throw new DecodeException("Invalid seal cause count");EnumMap<SealCause,String> causes=new EnumMap<>(SealCause.class);for(int i=0;i<n;i++){SealCause c=r.en(SealCause.class);if(causes.putIfAbsent(c,r.s())!=null)throw new DecodeException("Duplicate seal cause");}seal=new SealReason(causes,r.s());}
        Map<String,String> doctrines=r.stringMap();MasteryProgress mastery=new MasteryProgress(r.i(),r.l());SoulbondState soul=null;if(r.bool())soul=new SoulbondState(r.uuid(),r.i(),r.stringList(),r.l(),r.s());Set<String> fav=r.stringSet();String selected=r.s();CapstoneStatus cap=r.en(CapstoneStatus.class);int n=r.count("companions");if(n>ClassLoadout.MAX_ROSTER_ENTRIES)throw new DecodeException("Companion roster exceeds domain limit");Map<UUID,CompanionProfile> roster=new LinkedHashMap<>();for(int i=0;i<n;i++){CompanionProfile c=readCompanion(r);if(roster.putIfAbsent(c.companionId(),c)!=null)throw new DecodeException("Duplicate companion id");}return new ClassLoadout(spec,status,seal,doctrines,mastery,soul,fav,selected,cap,roster,r.stringMap(),r.s());
    }
    private void writeCompanion(Writer w,CompanionProfile c){w.uuid(c.companionId());w.s(c.namespace());w.s(c.typeId());w.s(c.name());w.i(c.level());w.l(c.experience());w.s(c.traitId());w.s(c.stance());w.stringList(c.equipmentIds());w.l(c.resummonAtEpochMillis());w.stringMap(c.persistentState());}
    private CompanionProfile readCompanion(Reader r)throws DecodeException{return new CompanionProfile(r.uuid(),r.s(),r.s(),r.s(),r.i(),r.l(),r.s(),r.s(),r.stringList(),r.l(),r.stringMap());}
    private static byte[] sha256(byte[] b){try{return MessageDigest.getInstance("SHA-256").digest(b);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}

    public record Limits(int maxPayloadBytes,int maxStringBytes,int maxCollectionEntries){public Limits{if(maxPayloadBytes<128||maxStringBytes<1||maxCollectionEntries<1||maxStringBytes>maxPayloadBytes)throw new IllegalArgumentException("Invalid codec limits");}public static Limits defaults(){return new Limits(1_048_576,4096,1024);}}
    public static final class DecodeException extends Exception{private static final long serialVersionUID=1L;public DecodeException(String m){super(m);}public DecodeException(String m,Throwable c){super(m,c);}}
    private static final class Writer{
        final ByteArrayOutputStream out=new ByteArrayOutputStream();final int max;final Limits limits;Writer(int max,Limits limits){this.max=max;this.limits=limits;}void ensure(int n){if(n<0||(long)out.size()+n>max)throw new IllegalArgumentException("Profile payload exceeds codec limit");}void raw(byte[] b){ensure(b.length);out.writeBytes(b);}void bool(boolean b){ensure(1);out.write(b?1:0);}void i(int v){ensure(4);out.write(v>>>24&255);out.write(v>>>16&255);out.write(v>>>8&255);out.write(v&255);}void l(long v){ensure(8);for(int s=56;s>=0;s-=8)out.write((int)(v>>>s)&255);}void uuid(UUID u){l(u.getMostSignificantBits());l(u.getLeastSignificantBits());}void s(String s){if(s==null)throw new IllegalArgumentException("null codec string");byte[] b=s.getBytes(StandardCharsets.UTF_8);if(b.length>limits.maxStringBytes())throw new IllegalArgumentException("String limit");i(b.length);raw(b);}void en(Enum<?> e){s(Objects.requireNonNull(e).name());}void nullable(Enum<?> e){bool(e!=null);if(e!=null)en(e);}void count(int n,String label){if(n<0||n>limits.maxCollectionEntries())throw new IllegalArgumentException(label+" limit");i(n);}void stringList(List<String> v){count(v.size(),"list");v.forEach(this::s);}void stringSet(Set<String> v){count(v.size(),"set");v.stream().sorted().forEach(this::s);}void stringMap(Map<String,String> v){count(v.size(),"map");v.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{s(e.getKey());s(e.getValue());});}byte[] bytes(){return out.toByteArray();}}
    private static final class Reader{
        final byte[] in;final Limits limits;int pos;Reader(byte[] in,Limits limits){this.in=in;this.limits=limits;}void need(int n)throws DecodeException{if(n<0||(long)pos+n>in.length)throw new DecodeException("Truncated profile payload");}byte[] raw(int n)throws DecodeException{need(n);byte[] r=Arrays.copyOfRange(in,pos,pos+n);pos+=n;return r;}boolean bool()throws DecodeException{need(1);int v=in[pos++]&255;if(v!=0&&v!=1)throw new DecodeException("Invalid boolean");return v==1;}int i()throws DecodeException{need(4);int v=(in[pos]&255)<<24|(in[pos+1]&255)<<16|(in[pos+2]&255)<<8|in[pos+3]&255;pos+=4;return v;}long l()throws DecodeException{need(8);long v=0;for(int i=0;i<8;i++)v=v<<8|(in[pos++]&255L);return v;}UUID uuid()throws DecodeException{return new UUID(l(),l());}int length(int max,String label)throws DecodeException{int n=i();if(n<0||n>max)throw new DecodeException("Invalid "+label+" length: "+n);return n;}int count(String label)throws DecodeException{return length(limits.maxCollectionEntries(),label);}String s()throws DecodeException{int n=length(limits.maxStringBytes(),"string");need(n);try{String v=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in,pos,n)).toString();pos+=n;return v;}catch(CharacterCodingException e){throw new DecodeException("Invalid UTF-8",e);}}<E extends Enum<E>>E en(Class<E> t)throws DecodeException{String raw=s();try{return Enum.valueOf(t,raw);}catch(IllegalArgumentException e){throw new DecodeException("Unknown "+t.getSimpleName()+": "+raw,e);}}<E extends Enum<E>>E nullable(Class<E> t)throws DecodeException{return bool()?en(t):null;}List<String> stringList()throws DecodeException{int n=count("list");List<String> r=new ArrayList<>(n);for(int i=0;i<n;i++)r.add(s());return List.copyOf(r);}Set<String> stringSet()throws DecodeException{int n=count("set");Set<String> r=new LinkedHashSet<>(),norm=new LinkedHashSet<>();for(int i=0;i<n;i++){String v=s();if(!r.add(v)||!norm.add(ClassSpecCatalog.normalize(v)))throw new DecodeException("Duplicate/colliding set value");}return Set.copyOf(r);}Map<String,String> stringMap()throws DecodeException{int n=count("map");Map<String,String> r=new LinkedHashMap<>();Set<String> norm=new LinkedHashSet<>();for(int i=0;i<n;i++){String k=s(),v=s();if(r.putIfAbsent(k,v)!=null||!norm.add(ClassSpecCatalog.normalize(k)))throw new DecodeException("Duplicate/colliding map key");}return Map.copyOf(r);}void exhausted(String label)throws DecodeException{if(pos!=in.length)throw new DecodeException("Trailing data in "+label);}}
}
