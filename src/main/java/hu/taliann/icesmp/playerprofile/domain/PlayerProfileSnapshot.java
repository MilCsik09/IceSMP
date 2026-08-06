package hu.taliann.icesmp.playerprofile.domain;

import hu.taliann.icesmp.playerprofile.domain.section.*;
import java.time.Instant;
import java.util.*;

/** Immutable logical aggregate composed from independently versioned durable sections. */
public record PlayerProfileSnapshot(
        UUID playerId,
        long profileRevision,
        Instant createdAt,
        Instant updatedAt,
        Map<ProfileSectionId, SectionRevision> sectionRevisions,
        ProfileSectionSnapshot<IdentitySection> identity,
        ProfileSectionSnapshot<LifecycleSection> lifecycle,
        ProfileSectionSnapshot<OnboardingSection> onboarding,
        ProfileSectionSnapshot<FactionSection> faction,
        ProfileSectionSnapshot<EconomySection> economy,
        ProfileSectionSnapshot<ClassSpecSection> classSpec,
        ProfileSectionSnapshot<ProfessionSection> professions,
        ProfileSectionSnapshot<SpellbookSection> spellbook,
        ProfileSectionSnapshot<TalentSection> talents,
        ProfileSectionSnapshot<QuestSection> quests,
        ProfileSectionSnapshot<CompanionSection> companions,
        ProfileSectionSnapshot<RelicSection> relics,
        ProfileSectionSnapshot<AchievementSection> achievements,
        ProfileSectionSnapshot<StatisticsSection> statistics,
        ProfileSectionSnapshot<PreferenceSection> preferences,
        ProfileSectionSnapshot<SocialLinkSection> socialLinks,
        ProfileSectionSnapshot<ModerationSection> moderation,
        ProfileSectionSnapshot<OperationSection> operations,
        ProfileHealth health) {

    public PlayerProfileSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (profileRevision < 0) throw new IllegalArgumentException("profileRevision must be non-negative");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt precedes createdAt");
        final EnumMap<ProfileSectionId, SectionRevision> revisions = new EnumMap<>(ProfileSectionId.class);
        if (sectionRevisions != null) revisions.putAll(sectionRevisions);
        final Map<ProfileSectionId, ProfileSectionSnapshot<?>> snapshots = snapshots(identity,lifecycle,onboarding,faction,economy,classSpec,professions,spellbook,talents,quests,companions,relics,achievements,statistics,preferences,socialLinks,moderation,operations);
        if (snapshots.size() != ProfileSectionId.values().length) throw new IllegalArgumentException("all PlayerProfile sections are required");
        for (ProfileSectionId id : ProfileSectionId.values()) {
            ProfileSectionSnapshot<?> section = snapshots.get(id);
            SectionRevision declared = revisions.get(id);
            if (declared == null) revisions.put(id, new SectionRevision(section.schema(), section.revision()));
            else if (declared.schema() != section.schema() || declared.revision() != section.revision()) throw new IllegalArgumentException("section revision map mismatch: "+id.id());
        }
        sectionRevisions = Collections.unmodifiableMap(revisions);
        health = health == null ? ProfileHealth.from(snapshots) : health;
    }

    public static PlayerProfileSnapshot greenfield(UUID playerId, Instant now) {
        Objects.requireNonNull(now, "now");
        return new PlayerProfileSnapshot(playerId,0,now,now,Map.of(),
            section(ProfileSectionId.IDENTITY, IdentitySection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.LIFECYCLE, LifecycleSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.ONBOARDING, OnboardingSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.FACTION, FactionSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.ECONOMY, EconomySection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.CLASS_SPEC, ClassSpecSection.empty(0), now),
            section(ProfileSectionId.PROFESSIONS, ProfessionSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.SPELLBOOK, SpellbookSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.TALENTS, TalentSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.QUESTS, QuestSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.COMPANIONS, CompanionSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.RELICS, RelicSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.ACHIEVEMENTS, AchievementSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.STATISTICS, StatisticsSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.PREFERENCES, PreferenceSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.SOCIAL_LINKS, SocialLinkSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.MODERATION, ModerationSection.empty(now.toEpochMilli()), now),
            section(ProfileSectionId.OPERATIONS, OperationSection.empty(now.toEpochMilli()), now), null);
    }

    public Optional<ProfileSectionSnapshot<?>> section(ProfileSectionId id) { return Optional.ofNullable(sectionMap().get(id)); }
    public Map<ProfileSectionId, ProfileSectionSnapshot<?>> sectionMap() {
        return snapshots(identity,lifecycle,onboarding,faction,economy,classSpec,professions,spellbook,talents,quests,companions,relics,achievements,statistics,preferences,socialLinks,moderation,operations);
    }

    @SuppressWarnings("unchecked")
    public PlayerProfileSnapshot withSection(ProfileSectionSnapshot<?> next, long nextProfileRevision, Instant time) {
        Objects.requireNonNull(next); Objects.requireNonNull(time);
        if (nextProfileRevision != Math.addExact(profileRevision,1L)) throw new IllegalArgumentException("profile revision must advance by one");
        Map<ProfileSectionId,ProfileSectionSnapshot<?>> m=new EnumMap<>(sectionMap());m.put(next.sectionId(),next);
        return fromMap(playerId,nextProfileRevision,createdAt,time,m);
    }

    public static PlayerProfileSnapshot fromMap(UUID id,long revision,Instant created,Instant updated,Map<ProfileSectionId,ProfileSectionSnapshot<?>> m){
        return new PlayerProfileSnapshot(id,revision,created,updated,Map.of(),
            cast(m,ProfileSectionId.IDENTITY),cast(m,ProfileSectionId.LIFECYCLE),cast(m,ProfileSectionId.ONBOARDING),
            cast(m,ProfileSectionId.FACTION),cast(m,ProfileSectionId.ECONOMY),cast(m,ProfileSectionId.CLASS_SPEC),
            cast(m,ProfileSectionId.PROFESSIONS),cast(m,ProfileSectionId.SPELLBOOK),cast(m,ProfileSectionId.TALENTS),
            cast(m,ProfileSectionId.QUESTS),cast(m,ProfileSectionId.COMPANIONS),cast(m,ProfileSectionId.RELICS),
            cast(m,ProfileSectionId.ACHIEVEMENTS),cast(m,ProfileSectionId.STATISTICS),cast(m,ProfileSectionId.PREFERENCES),
            cast(m,ProfileSectionId.SOCIAL_LINKS),cast(m,ProfileSectionId.MODERATION),cast(m,ProfileSectionId.OPERATIONS),null);
    }
    private static <T extends ProfileSectionData> ProfileSectionSnapshot<T> section(ProfileSectionId id,T value,Instant now){return new ProfileSectionSnapshot<>(id,id.currentSchema(),0,now,value,SectionHealth.healthy());}
    @SuppressWarnings("unchecked") private static <T extends ProfileSectionData> ProfileSectionSnapshot<T> cast(Map<ProfileSectionId,ProfileSectionSnapshot<?>> m,ProfileSectionId id){ProfileSectionSnapshot<?> s=Objects.requireNonNull(m.get(id),"missing "+id.id());return (ProfileSectionSnapshot<T>)s;}
    private static Map<ProfileSectionId,ProfileSectionSnapshot<?>> snapshots(ProfileSectionSnapshot<?>... all){EnumMap<ProfileSectionId,ProfileSectionSnapshot<?>> m=new EnumMap<>(ProfileSectionId.class);for(ProfileSectionSnapshot<?> s:all){Objects.requireNonNull(s);if(m.put(s.sectionId(),s)!=null)throw new IllegalArgumentException("duplicate section "+s.sectionId().id());}return Collections.unmodifiableMap(m);}
}
