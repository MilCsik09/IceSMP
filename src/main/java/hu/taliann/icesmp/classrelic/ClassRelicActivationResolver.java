package hu.taliann.icesmp.classrelic;

import hu.taliann.icesmp.classspec.domain.LoadoutStatus;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A Class Relic aktiválás egyetlen döntési pontja. Pure: a világ-ownership, a fizikai
 * birtoklás és a Profile v2 class/spec tények nézet-interfészeken érkeznek, így a
 * resolver Bukkit nélkül tesztelhető, az adapterek pedig nem duplikálhatják a szabályokat.
 *
 * Kulcs-invariánsok:
 * - OWNER ≠ ACTIVE POSSESSION: az ownership önmagában nem ad gameplay-erőt, ha a binding
 *   fizikai birtoklást követel (lost/reclaim vagy hiányzó tárgy → DORMANT).
 * - SEALED specializáció nem rezonál, de a kaszt-szintű Class Power tovább élhet.
 * - Csak gameplay-re használható profil (READY + nem blokkolt session) aktiválhat.
 */
public final class ClassRelicActivationResolver {

    /** Explicit framework-kapu: false esetén MINDEN feloldás FRAMEWORK_DISABLED. */
    @FunctionalInterface
    public interface FrameworkView {
        boolean enabled();
    }

    /** Világ-szintű relic authority nézete (ownership + lost/reclaim). */
    public interface OwnershipView {
        Optional<UUID> ownerOf(String relicId);

        boolean isLost(String relicId);
    }

    /** Használható fizikai tárgy a játékosnál (adapter: régió-szálas inventory-nézet). */
    public interface PossessionView {
        boolean possessesUsable(UUID playerId, String relicId);
    }

    /** Profile v2 class/spec tények; üres, ha a profil gameplay-re nem használható. */
    public interface ProfileView {
        Optional<ProfileFacts> facts(UUID playerId);
    }

    /**
     * @param primaryClassId a kaszt kanonikus id-je (üres string = nincs kaszt)
     * @param activeSpecializationId az aktív loadout specializációja (üres = nincs aktív)
     * @param activeSpecializationStatus az aktív slot loadout-státusza
     */
    public record ProfileFacts(String primaryClassId,
                               String activeSpecializationId,
                               LoadoutStatus activeSpecializationStatus) {
        public ProfileFacts {
            primaryClassId = normalize(primaryClassId);
            activeSpecializationId = normalize(activeSpecializationId);
            Objects.requireNonNull(activeSpecializationStatus, "activeSpecializationStatus");
        }

        private static String normalize(final String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
    }

    private final FrameworkView framework;
    private final OwnershipView ownership;
    private final PossessionView possession;
    private final ProfileView profile;

    public ClassRelicActivationResolver(final FrameworkView framework,
                                        final OwnershipView ownership,
                                        final PossessionView possession,
                                        final ProfileView profile) {
        this.framework = Objects.requireNonNull(framework, "framework");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.possession = Objects.requireNonNull(possession, "possession");
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public ClassRelicActivation resolve(final ClassRelicCatalog catalog, final UUID playerId,
                                        final String relicId) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(playerId, "playerId");
        final String normalizedRelic = relicId == null ? ""
                : relicId.trim().toLowerCase(Locale.ROOT);
        // A kapu a possession-mellékhatás ELŐTT dönt: requires-physical-possession=false
        // kötés sem aktiválódhat kikapcsolt framework mellett.
        if (!framework.enabled()) {
            return ClassRelicActivation.dormant(playerId, normalizedRelic, "",
                    ClassRelicActivation.DormantReason.FRAMEWORK_DISABLED);
        }
        final Optional<ClassRelicBinding> bound = catalog.byRelic(normalizedRelic);
        if (bound.isEmpty()) {
            return ClassRelicActivation.dormant(playerId, normalizedRelic, "",
                    ClassRelicActivation.DormantReason.NO_BINDING);
        }
        final ClassRelicBinding binding = bound.orElseThrow();
        final Optional<ProfileFacts> profileFacts = profile.facts(playerId);
        if (profileFacts.isEmpty()) {
            return ClassRelicActivation.dormant(playerId, binding.relicId(), binding.classId(),
                    ClassRelicActivation.DormantReason.PROFILE_NOT_USABLE);
        }
        final ProfileFacts facts = profileFacts.orElseThrow();
        if (!binding.classId().equals(facts.primaryClassId())) {
            return ClassRelicActivation.dormant(playerId, binding.relicId(), binding.classId(),
                    ClassRelicActivation.DormantReason.WRONG_CLASS);
        }
        final Optional<UUID> owner = ownership.ownerOf(binding.relicId());
        if (owner.isEmpty() || !playerId.equals(owner.orElseThrow())) {
            return ClassRelicActivation.dormant(playerId, binding.relicId(), binding.classId(),
                    ClassRelicActivation.DormantReason.NOT_OWNER);
        }
        if (ownership.isLost(binding.relicId())) {
            return ClassRelicActivation.dormant(playerId, binding.relicId(), binding.classId(),
                    ClassRelicActivation.DormantReason.RELIC_LOST);
        }
        if (binding.requiresPhysicalPossession()
                && !possession.possessesUsable(playerId, binding.relicId())) {
            return ClassRelicActivation.dormant(playerId, binding.relicId(), binding.classId(),
                    ClassRelicActivation.DormantReason.NO_PHYSICAL_POSSESSION);
        }
        final boolean specActive = !facts.activeSpecializationId().isEmpty()
                && facts.activeSpecializationStatus() == LoadoutStatus.ACTIVE;
        final ClassRelicBinding.Resonance resonance = specActive
                ? binding.resonances().get(facts.activeSpecializationId()) : null;
        return new ClassRelicActivation(playerId, binding.relicId(), binding.classId(),
                specActive ? Optional.of(facts.activeSpecializationId()) : Optional.empty(),
                true,
                resonance == null ? Optional.empty() : Optional.of(resonance.id()),
                resonance != null && resonance.enabled(),
                binding.awakening().enabled(),
                ClassRelicActivation.DormantReason.NONE);
    }

    /** A játékos kasztjához kötött relic feloldása (modifier-lekérdezések belépője). */
    public ClassRelicActivation resolveForClass(final ClassRelicCatalog catalog,
                                                final UUID playerId) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(playerId, "playerId");
        if (!framework.enabled()) {
            return ClassRelicActivation.dormant(playerId, "", "",
                    ClassRelicActivation.DormantReason.FRAMEWORK_DISABLED);
        }
        final Optional<ProfileFacts> facts = profile.facts(playerId);
        if (facts.isEmpty()) {
            return ClassRelicActivation.dormant(playerId, "", "",
                    ClassRelicActivation.DormantReason.PROFILE_NOT_USABLE);
        }
        final Optional<ClassRelicBinding> binding =
                catalog.byClass(facts.orElseThrow().primaryClassId());
        if (binding.isEmpty()) {
            return ClassRelicActivation.dormant(playerId, "",
                    facts.orElseThrow().primaryClassId(),
                    ClassRelicActivation.DormantReason.NO_BINDING);
        }
        return resolve(catalog, playerId, binding.orElseThrow().relicId());
    }
}
