package hu.taliann.icesmp.playerprofile.domain.section;
import hu.taliann.icesmp.playerprofile.domain.*;
import java.util.*;

public record StatisticsSection(Map<String,Long> lifetime, Map<String,Long> season, Set<String> claimedMilestones, Map<String,Object> extensions) implements PlayerProfileSection {
    public StatisticsSection{lifetime=longMap(lifetime,2048);season=longMap(season,2048);claimedMilestones=ImmutableValues.stringSet(claimedMilestones,1024);extensions=ImmutableValues.map(extensions);}
    @Override public ProfileSectionId sectionId(){return ProfileSectionId.STATISTICS;}
    public static StatisticsSection empty(long now){return new StatisticsSection(Map.of(),Map.of(),Set.of(),Map.of());}

    private static Map<String,Long> longMap(Map<String,Long> source,int max){if(source==null||source.isEmpty())return Map.of();if(source.size()>max)throw new IllegalArgumentException("map limit exceeded");LinkedHashMap<String,Long> out=new LinkedHashMap<>();source.forEach((k,v)->out.put(ImmutableValues.id(k,"key"),ImmutableValues.nonNegative(v==null?0:v,"value")));return Collections.unmodifiableMap(out);}
    private static Map<String,Integer> intMap(Map<String,Integer> source,int max){if(source==null||source.isEmpty())return Map.of();if(source.size()>max)throw new IllegalArgumentException("map limit exceeded");LinkedHashMap<String,Integer> out=new LinkedHashMap<>();source.forEach((k,v)->out.put(ImmutableValues.id(k,"key"),ImmutableValues.nonNegative(v==null?0:v,"value")));return Collections.unmodifiableMap(out);}
    private static Map<String,Set<String>> setMap(Map<String,Set<String>> source,int max){if(source==null||source.isEmpty())return Map.of();if(source.size()>max)throw new IllegalArgumentException("map limit exceeded");LinkedHashMap<String,Set<String>> out=new LinkedHashMap<>();source.forEach((k,v)->out.put(ImmutableValues.id(k,"key"),ImmutableValues.stringSet(v,512)));return Collections.unmodifiableMap(out);}
    private static Map<String,Map<String,Long>> nestedLongMap(Map<String,Map<String,Long>> source,int max){if(source==null||source.isEmpty())return Map.of();if(source.size()>max)throw new IllegalArgumentException("map limit exceeded");LinkedHashMap<String,Map<String,Long>> out=new LinkedHashMap<>();source.forEach((k,v)->out.put(ImmutableValues.id(k,"key"),longMap(v,512)));return Collections.unmodifiableMap(out);}
    private static Map<String,Map<String,String>> nestedStringMap(Map<String,Map<String,String>> source,int max){if(source==null||source.isEmpty())return Map.of();if(source.size()>max)throw new IllegalArgumentException("map limit exceeded");LinkedHashMap<String,Map<String,String>> out=new LinkedHashMap<>();source.forEach((k,v)->out.put(ImmutableValues.id(k,"key"),ImmutableValues.strings(v,128)));return Collections.unmodifiableMap(out);}
    private static Map<String,Map<String,Object>> nestedObjectMap(Map<String,Map<String,Object>> source,int max){if(source==null||source.isEmpty())return Map.of();if(source.size()>max)throw new IllegalArgumentException("map limit exceeded");LinkedHashMap<String,Map<String,Object>> out=new LinkedHashMap<>();source.forEach((k,v)->out.put(ImmutableValues.id(k,"key"),ImmutableValues.map(v)));return Collections.unmodifiableMap(out);}

}
