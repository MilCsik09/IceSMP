package hu.taliann.icesmp.playerprofile.domain.section;
import hu.taliann.icesmp.playerprofile.domain.*;
import java.util.*;

public record OperationSection(Map<String,PlayerProfileOperation> operations, Map<String,Object> extensions) implements PlayerProfileSection {
    public OperationSection{if(operations==null)operations=Map.of();if(operations.size()>512)throw new IllegalArgumentException("operation limit exceeded");operations=Collections.unmodifiableMap(new LinkedHashMap<>(operations));extensions=ImmutableValues.map(extensions);}
    @Override public ProfileSectionId sectionId(){return ProfileSectionId.OPERATIONS;}
    public static OperationSection empty(long now){return new OperationSection(Map.of(),Map.of());}

}
