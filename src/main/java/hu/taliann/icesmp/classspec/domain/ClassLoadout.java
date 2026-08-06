package hu.taliann.icesmp.classspec.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable specialization-local state. */
public record ClassLoadout(String specializationId, LoadoutStatus status, SealReason sealReason,
                           Map<String, String> doctrineChoices, MasteryProgress mastery,
                           SoulbondState soulbond, Set<String> favoriteSpells, String selectedSpell,
                           CapstoneStatus capstoneStatus, Map<UUID, CompanionProfile> companionRoster,
                           Map<String, String> mechanicState, String diagnosticNote) {
    public static final int MAX_MECHANIC_ENTRIES=256, MAX_ROSTER_ENTRIES=64;
    public ClassLoadout {
        Objects.requireNonNull(status,"status"); Objects.requireNonNull(mastery,"mastery"); Objects.requireNonNull(capstoneStatus,"capstoneStatus");
        specializationId=ClassSpecCatalog.normalize(specializationId); selectedSpell=ClassSpecCatalog.normalize(selectedSpell); diagnosticNote=clean(diagnosticNote,512);
        doctrineChoices=normalizedMap(doctrineChoices,"doctrine",64); favoriteSpells=normalizedSet(favoriteSpells,"favorite spell",128);
        companionRoster=copyRoster(companionRoster); mechanicState=normalizedMap(mechanicState,"mechanic",MAX_MECHANIC_ENTRIES);
        if(status==LoadoutStatus.EMPTY) {
            if(!specializationId.isEmpty()||sealReason!=null||!doctrineChoices.isEmpty()||!mastery.equals(MasteryProgress.empty())||soulbond!=null||!favoriteSpells.isEmpty()||!selectedSpell.isEmpty()||capstoneStatus!=CapstoneStatus.LOCKED||!companionRoster.isEmpty()||!mechanicState.isEmpty()||!diagnosticNote.isEmpty())
                throw new IllegalArgumentException("An EMPTY loadout may not hide state");
        } else if(specializationId.isEmpty()) throw new IllegalArgumentException("A non-empty loadout requires a specialization id");
        if(status==LoadoutStatus.SEALED&&sealReason==null) throw new IllegalArgumentException("A SEALED loadout requires a seal reason");
        if(status!=LoadoutStatus.SEALED&&sealReason!=null) throw new IllegalArgumentException("Only a SEALED loadout may retain a seal reason");
        if(!companionRoster.isEmpty()) {
            final String expected=ClassSpecCatalog.companionNamespace(specializationId);
            if(expected==null||companionRoster.values().stream().anyMatch(c->!expected.equals(c.namespace()))) throw new IllegalArgumentException("Companion roster leaks outside its specialization namespace");
        }
        final boolean hasSoulforge=mechanicState.keySet().stream().anyMatch(ClassLoadout::isSoulforgeKey);
        if(hasSoulforge&&!"necromancer".equals(specializationId)) throw new IllegalArgumentException("Soulforge state is only valid in a necromancer loadout");
    }
    public static ClassLoadout empty() { return new ClassLoadout("",LoadoutStatus.EMPTY,null,Map.of(),MasteryProgress.empty(),null,Set.of(),"",CapstoneStatus.LOCKED,Map.of(),Map.of(),""); }
    public ClassLoadout withStatus(LoadoutStatus s,SealReason r){return copy(s,r,companionRoster,mechanicState,mastery);}
    public ClassLoadout withCompanionRoster(Map<UUID,CompanionProfile> r){return copy(status,sealReason,r,mechanicState,mastery);}
    public ClassLoadout withMechanicState(Map<String,String> m){return copy(status,sealReason,companionRoster,m,mastery);}
    public ClassLoadout withMastery(MasteryProgress m){return copy(status,sealReason,companionRoster,mechanicState,Objects.requireNonNull(m));}
    public boolean isActivatable(){return status==LoadoutStatus.ACTIVE||status==LoadoutStatus.INACTIVE;}
    private ClassLoadout copy(LoadoutStatus s,SealReason reason,Map<UUID,CompanionProfile> roster,Map<String,String> mechanics,MasteryProgress progress){return new ClassLoadout(specializationId,s,reason,doctrineChoices,progress,soulbond,favoriteSpells,selectedSpell,capstoneStatus,roster,mechanics,diagnosticNote);}
    private static boolean isSoulforgeKey(String key){return key.equals("soulforge")||key.startsWith("soulforge.")||key.startsWith("necromancer.soulforge.");}
    private static Map<String,String> normalizedMap(Map<String,String> source,String label,int maximum){Objects.requireNonNull(source,label+" map");if(source.size()>maximum)throw new IllegalArgumentException(label+" map exceeds "+maximum+" entries");final Map<String,String> result=new LinkedHashMap<>();for(var e:source.entrySet()){final String key=ClassSpecCatalog.normalize(e.getKey());if(key.isEmpty()||e.getValue()==null)throw new IllegalArgumentException(label+" keys and values must be present");if(result.putIfAbsent(key,clean(e.getValue(),4096))!=null)throw new IllegalArgumentException("Normalized "+label+" key collision: "+key);}return Map.copyOf(result);}
    private static Set<String> normalizedSet(Collection<String> source,String label,int maximum){Objects.requireNonNull(source,label+" set");if(source.size()>maximum)throw new IllegalArgumentException(label+" set exceeds "+maximum+" entries");final Set<String> result=new LinkedHashSet<>();for(String raw:source){final String value=ClassSpecCatalog.normalize(raw);if(value.isEmpty())throw new IllegalArgumentException(label+" ids must be non-blank");if(!result.add(value))throw new IllegalArgumentException("Normalized "+label+" collision: "+value);}return Set.copyOf(result);}
    private static Map<UUID,CompanionProfile> copyRoster(Map<UUID,CompanionProfile> source){Objects.requireNonNull(source,"companionRoster");if(source.size()>MAX_ROSTER_ENTRIES)throw new IllegalArgumentException("Companion roster exceeds "+MAX_ROSTER_ENTRIES+" entries");final Map<UUID,CompanionProfile> result=new LinkedHashMap<>();for(var e:source.entrySet()){if(e.getKey()==null||e.getValue()==null||!e.getKey().equals(e.getValue().companionId()))throw new IllegalArgumentException("Companion roster key must equal logical companion id");result.put(e.getKey(),e.getValue());}return Map.copyOf(result);}
    private static String clean(String value,int maximum){final String cleaned=value==null?"":value.trim();if(cleaned.length()>maximum)throw new IllegalArgumentException("Profile text exceeds "+maximum+" characters");return cleaned;}
}
