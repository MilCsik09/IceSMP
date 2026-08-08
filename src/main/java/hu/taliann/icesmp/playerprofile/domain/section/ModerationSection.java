package hu.taliann.icesmp.playerprofile.domain.section;
import hu.taliann.icesmp.playerprofile.domain.*;
import java.util.*;

public record ModerationSection(Set<String> activePunishmentRefs, int strikeCount, Set<String> adminNoteRefs, Map<String,String> auditState, Map<String,Object> extensions) implements PlayerProfileSection {
    public ModerationSection{activePunishmentRefs=ImmutableValues.stringSet(activePunishmentRefs,128);strikeCount=ImmutableValues.nonNegative(strikeCount,"strikeCount");adminNoteRefs=ImmutableValues.stringSet(adminNoteRefs,256);auditState=ImmutableValues.strings(auditState,128);extensions=ImmutableValues.map(extensions);}
    @Override public ProfileSectionId sectionId(){return ProfileSectionId.MODERATION;}
    public static ModerationSection empty(long now){return new ModerationSection(Set.of(),0,Set.of(),Map.of(),Map.of());}

}
