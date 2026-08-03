package hu.taliann.icesmp.playerprofile.domain.section;
import hu.taliann.icesmp.playerprofile.domain.*;
import java.util.*;

public record SocialLinkSection(Map<String,String> links, Map<String,Object> extensions) implements PlayerProfileSection {
    public SocialLinkSection{links=ImmutableValues.strings(links,32);extensions=ImmutableValues.map(extensions);}
    @Override public ProfileSectionId sectionId(){return ProfileSectionId.SOCIAL_LINKS;}
    public static SocialLinkSection empty(long now){return new SocialLinkSection(Map.of(),Map.of());}

}
