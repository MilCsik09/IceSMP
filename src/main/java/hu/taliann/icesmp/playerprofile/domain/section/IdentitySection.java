package hu.taliann.icesmp.playerprofile.domain.section;
import hu.taliann.icesmp.playerprofile.domain.*;
import java.util.*;

public record IdentitySection(String lastKnownName, long firstSeenAt, long lastSeenAt, long lastSuccessfulLoadAt, boolean publicVisible, boolean apiVisible, Map<String,String> accountLinks, Map<String,Object> extensions) implements PlayerProfileSection {
    public IdentitySection{lastKnownName=ImmutableValues.text(lastKnownName,64); firstSeenAt=ImmutableValues.nonNegative(firstSeenAt,"firstSeenAt"); lastSeenAt=ImmutableValues.nonNegative(lastSeenAt,"lastSeenAt"); lastSuccessfulLoadAt=ImmutableValues.nonNegative(lastSuccessfulLoadAt,"lastSuccessfulLoadAt"); accountLinks=ImmutableValues.strings(accountLinks,32); extensions=ImmutableValues.map(extensions);}
    @Override public ProfileSectionId sectionId(){return ProfileSectionId.IDENTITY;}
    public static IdentitySection empty(long now){return new IdentitySection("",now,now,0,true,false,Map.of(),Map.of());}

}
