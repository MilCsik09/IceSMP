package hu.taliann.icesmp.playerprofile.api;
import java.time.Instant;import java.util.*;
public record PublicPlayerProfileDto(UUID playerId,boolean visible,String name,String faction,String primaryClass,String activeSpecialization,Set<String> achievements,Map<String,Long> publicStatistics,String publicCompanion,long profileRevision,Map<String,Long> sectionRevisions,Instant updatedAt){
 public PublicPlayerProfileDto{Objects.requireNonNull(playerId);name=clean(name);faction=clean(faction);primaryClass=clean(primaryClass);activeSpecialization=clean(activeSpecialization);achievements=Set.copyOf(achievements==null?Set.of():achievements);publicStatistics=Map.copyOf(publicStatistics==null?Map.of():publicStatistics);publicCompanion=clean(publicCompanion);sectionRevisions=Map.copyOf(sectionRevisions==null?Map.of():sectionRevisions);Objects.requireNonNull(updatedAt);}private static String clean(String v){return v==null?"":v;}
}
