package hu.taliann.icesmp.playerprofile.domain;

import java.util.*;

public enum ProfileSectionId {
    IDENTITY("identity",1), LIFECYCLE("lifecycle",1), ONBOARDING("onboarding",1),
    FACTION("faction",1), ECONOMY("economy",1), CLASS_SPEC("class-spec",1),
    PROFESSIONS("professions",1), SPELLBOOK("spellbook",1), TALENTS("talents",1),
    QUESTS("quests",1), COMPANIONS("companions",1), RELICS("relics",1),
    ACHIEVEMENTS("achievements",1), STATISTICS("statistics",1), PREFERENCES("preferences",1),
    SOCIAL_LINKS("social-links",1), MODERATION("moderation",1), OPERATIONS("operations",1);
    private final String id; private final int currentSchema;
    ProfileSectionId(String id,int schema){this.id=id;this.currentSchema=schema;}
    public String id(){return id;} public int currentSchema(){return currentSchema;}
    public String fileName(){return id+".yml";}
    public static ProfileSectionId parse(String raw){
        if(raw==null) throw new IllegalArgumentException("section id is required");
        String n=raw.trim().toLowerCase(Locale.ROOT).replace('_','-');
        return Arrays.stream(values()).filter(v->v.id.equals(n)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("unknown profile section: "+raw));
    }
}