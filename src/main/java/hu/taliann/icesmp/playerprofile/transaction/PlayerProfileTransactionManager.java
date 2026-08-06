package hu.taliann.icesmp.playerprofile.transaction;

import hu.taliann.icesmp.playerprofile.domain.*;
import java.util.*;
import java.util.concurrent.CompletionStage;

public interface PlayerProfileTransactionManager {
    <T> CompletionStage<T> execute(UUID playerId, ProfileTransactionWork<T> transaction);

    @FunctionalInterface interface ProfileTransactionWork<T>{TransactionPlan<T> prepare(PlayerProfileSnapshot snapshot);}
    record SectionUpdate(ProfileSectionId section,long expectedRevision,ProfileSectionData next){
        public SectionUpdate{Objects.requireNonNull(section);Objects.requireNonNull(next);if(next.sectionId()!=section)throw new IllegalArgumentException("section mismatch");if(expectedRevision<0)throw new IllegalArgumentException("expected revision must be non-negative");}
    }
    record TransactionPlan<T>(String operationId,String type,String fingerprint,List<SectionUpdate> updates,T result){
        public TransactionPlan{operationId=require(operationId,"operationId");type=require(type,"type");fingerprint=require(fingerprint,"fingerprint");updates=List.copyOf(updates);if(updates.isEmpty())throw new IllegalArgumentException("transaction requires updates");Set<ProfileSectionId> seen=EnumSet.noneOf(ProfileSectionId.class);for(SectionUpdate u:updates)if(!seen.add(u.section()))throw new IllegalArgumentException("duplicate section update");}
        private static String require(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" cannot be blank");return v.trim();}
    }
}
