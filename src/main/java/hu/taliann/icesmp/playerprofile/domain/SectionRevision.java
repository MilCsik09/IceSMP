package hu.taliann.icesmp.playerprofile.domain;
public record SectionRevision(int schema,long revision){
    public SectionRevision{if(schema<1)throw new IllegalArgumentException("schema must be positive");if(revision<0)throw new IllegalArgumentException("revision must be non-negative");}
}