package hu.taliann.icesmp.playerprofile.domain;
import java.util.Map;
public interface ProfileSectionData {
    ProfileSectionId sectionId();
    default Map<String,Object> extensions(){return Map.of();}
}