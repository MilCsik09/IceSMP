package hu.taliann.icesmp.playerprofile.domain.section;
import hu.taliann.icesmp.playerprofile.domain.*;
import java.util.*;

public record OnboardingSection(Map<String,String> steps, Set<String> claimedRewards, Set<String> codexEntries, boolean memorySpecUnlocked, Map<String,Object> extensions) implements PlayerProfileSection {
    public OnboardingSection{steps=ImmutableValues.strings(steps,128); claimedRewards=ImmutableValues.stringSet(claimedRewards,128); codexEntries=ImmutableValues.stringSet(codexEntries,512); extensions=ImmutableValues.map(extensions);}
    @Override public ProfileSectionId sectionId(){return ProfileSectionId.ONBOARDING;}
    public static OnboardingSection empty(long now){return new OnboardingSection(Map.of(),Set.of(),Set.of(),false,Map.of());}

}
