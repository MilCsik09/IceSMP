package hu.taliann.icesmp.playerprofile.domain;
import java.util.Objects;
public record SectionHealth(Status status,String diagnostic,String evidenceId,String recoveryAuditId){
    public SectionHealth{Objects.requireNonNull(status);diagnostic=clean(diagnostic);evidenceId=clean(evidenceId);recoveryAuditId=clean(recoveryAuditId);if(status==Status.QUARANTINED&&evidenceId.isBlank())throw new IllegalArgumentException("quarantine requires evidence id");}
    public SectionHealth(Status status,String diagnostic,String evidenceId){this(status,diagnostic,evidenceId,"");}
    public static SectionHealth healthy(){return new SectionHealth(Status.HEALTHY,"","","");}
    public static SectionHealth defaulted(String detail){return new SectionHealth(Status.DEFAULTED,detail,"","");}
    public static SectionHealth quarantined(String detail,String evidence){return new SectionHealth(Status.QUARANTINED,detail,evidence,"");}
    public static SectionHealth recovered(String evidence,String audit){return new SectionHealth(Status.HEALTHY,"recovered",evidence,audit);}
    public boolean usable(){return status==Status.HEALTHY||status==Status.DEFAULTED;}
    private static String clean(String v){return v==null?"":v.trim();}
    public enum Status{HEALTHY,DEFAULTED,REVIEW_REQUIRED,QUARANTINED}
}