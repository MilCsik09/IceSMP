package hu.taliann.icesmp.classspec.application;

import java.util.Objects;
import java.util.Optional;

/** Durable mutation result; failed candidates are never exposed as authority. */
public record ProfileMutationResult<P>(Status status,P durableProfile,String detail,boolean sessionBlocked){
    public ProfileMutationResult{Objects.requireNonNull(status);detail=detail==null?"":detail;if((status==Status.COMMITTED||status==Status.RUNTIME_EFFECT_FAILED||status==Status.STALE_SESSION)&&durableProfile==null)throw new IllegalArgumentException("Durable result requires profile");if((status==Status.PERSISTENCE_FAILED||status==Status.REVISION_CONFLICT||status==Status.RUNTIME_EFFECT_FAILED)&&!sessionBlocked)throw new IllegalArgumentException("Uncertain state must fail closed");}
    public static <P>ProfileMutationResult<P> committed(P p){return new ProfileMutationResult<>(Status.COMMITTED,Objects.requireNonNull(p),"",false);}public static <P>ProfileMutationResult<P> noChange(P p,String d){return new ProfileMutationResult<>(Status.NO_CHANGE,p,d,false);}public static <P>ProfileMutationResult<P> rejected(P p,String d){return new ProfileMutationResult<>(Status.REJECTED,p,d,false);}public static <P>ProfileMutationResult<P> stale(P p,String d){return new ProfileMutationResult<>(Status.STALE_SESSION,Objects.requireNonNull(p),d,false);}public static <P>ProfileMutationResult<P> failed(P p,Status s,String d){if(s!=Status.PERSISTENCE_FAILED&&s!=Status.REVISION_CONFLICT&&s!=Status.RUNTIME_EFFECT_FAILED&&s!=Status.LIFECYCLE_STOPPED)throw new IllegalArgumentException("Not failure status");return new ProfileMutationResult<>(s,p,d,s!=Status.LIFECYCLE_STOPPED);}
    public boolean committed(){return status==Status.COMMITTED;}public boolean durableMutationApplied(){return status==Status.COMMITTED||status==Status.RUNTIME_EFFECT_FAILED||status==Status.STALE_SESSION;}public Optional<P> durableProfileOptional(){return Optional.ofNullable(durableProfile);}
    public enum Status{COMMITTED,NO_CHANGE,REJECTED,REVISION_CONFLICT,PERSISTENCE_FAILED,RUNTIME_EFFECT_FAILED,STALE_SESSION,LIFECYCLE_STOPPED}
}
