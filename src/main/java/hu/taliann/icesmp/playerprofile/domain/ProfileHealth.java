package hu.taliann.icesmp.playerprofile.domain;
import java.util.*;
public record ProfileHealth(Status status,Map<ProfileSectionId,SectionHealth> sections,String diagnostic){
    public ProfileHealth{Objects.requireNonNull(status);sections=Collections.unmodifiableMap(new EnumMap<>(sections==null?Map.of():sections));diagnostic=diagnostic==null?"":diagnostic.trim();}
    public static ProfileHealth from(Map<ProfileSectionId,? extends ProfileSectionSnapshot<?>> snapshots){
        EnumMap<ProfileSectionId,SectionHealth> health=new EnumMap<>(ProfileSectionId.class);boolean blocked=false,partial=false;
        for(var e:snapshots.entrySet()){health.put(e.getKey(),e.getValue().health());if(!e.getValue().health().usable()){partial=true;if(e.getKey()==ProfileSectionId.IDENTITY||e.getKey()==ProfileSectionId.LIFECYCLE)blocked=true;}}
        return new ProfileHealth(blocked?Status.BLOCKED:partial?Status.PARTIAL:Status.HEALTHY,health,blocked?"identity or lifecycle unavailable":partial?"one or more sections unavailable":"");
    }
    public boolean usable(){return status!=Status.BLOCKED;}
    public enum Status{HEALTHY,PARTIAL,BLOCKED}
}