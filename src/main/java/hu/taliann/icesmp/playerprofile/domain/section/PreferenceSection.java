package hu.taliann.icesmp.playerprofile.domain.section;
import hu.taliann.icesmp.playerprofile.domain.*;
import java.util.*;

public record PreferenceSection(String language, boolean hudEnabled, boolean scoreboardEnabled, boolean notificationsEnabled, boolean publicProfile, boolean publicCompanion, boolean publicAchievements, boolean publicClassFactionSpec, boolean apiVisible, Map<String,String> values, Map<String,Object> extensions) implements PlayerProfileSection {
    public PreferenceSection{language=ImmutableValues.id(language==null||language.isBlank()?"hu_HU":language,"language");values=ImmutableValues.strings(values,128);extensions=ImmutableValues.map(extensions);}
    @Override public ProfileSectionId sectionId(){return ProfileSectionId.PREFERENCES;}
    public static PreferenceSection empty(long now){return new PreferenceSection("hu_HU",true,true,true,true,true,true,true,false,Map.of(),Map.of());}

}
