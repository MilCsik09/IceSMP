package hu.taliann.icesmp.playerprofile.domain;
import java.time.Instant;import java.util.*;
public record ProfileSectionSnapshot<T extends ProfileSectionData>(ProfileSectionId sectionId,int schema,long revision,Instant updatedAt,T value,SectionHealth health,Map<String,Object> extensions){
    public ProfileSectionSnapshot{Objects.requireNonNull(sectionId);Objects.requireNonNull(updatedAt);Objects.requireNonNull(value);Objects.requireNonNull(health);if(schema<1)throw new IllegalArgumentException("schema must be positive");if(revision<0)throw new IllegalArgumentException("revision must be non-negative");if(value.sectionId()!=sectionId)throw new IllegalArgumentException("section value id mismatch");extensions=ImmutableValues.map(extensions);}
    public ProfileSectionSnapshot(ProfileSectionId id,int schema,long revision,Instant updatedAt,T value,SectionHealth health){this(id,schema,revision,updatedAt,value,health,Map.of());}
    public ProfileSectionSnapshot<T> withValue(long nextRevision,T next,Instant time){return new ProfileSectionSnapshot<>(sectionId,schema,nextRevision,time,next,SectionHealth.healthy(),extensions);}
}