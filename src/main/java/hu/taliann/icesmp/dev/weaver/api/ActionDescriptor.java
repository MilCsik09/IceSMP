package hu.taliann.icesmp.dev.weaver.api;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import hu.taliann.icesmp.dev.weaver.subject.WeaverSubjectKind;
import net.kyori.adventure.text.Component;

public record ActionDescriptor(String id, String facetId, Component label, RiskLevel risk,
                               Set<Lifetime> lifetimes, Set<IntegrityMode> integrityModes, Set<IntegrityImpact> integrityImpacts,
                               Set<WeaverSubjectKind> subjects, List<ActionParameter> parameters, AreaSupport areaSupport,
                               Optional<AreaLimits> areaLimits, boolean undoable, Optional<String> irreversibleReason, int rateCost, WeaverRevisionScope revisionScope) {
    public ActionDescriptor(final String id, final String facetId, final Component label, final RiskLevel risk,
            final Set<Lifetime> lifetimes, final Set<IntegrityMode> integrityModes, final Set<IntegrityImpact> integrityImpacts,
            final Set<WeaverSubjectKind> subjects, final List<ActionParameter> parameters, final AreaSupport areaSupport,
            final Optional<AreaLimits> areaLimits, final boolean undoable, final Optional<String> irreversibleReason, final int rateCost) {
        this(id, facetId, label, risk, lifetimes, integrityModes, integrityImpacts, subjects, parameters, areaSupport, areaLimits, undoable, irreversibleReason, rateCost, WeaverRevisionScope.full());
    }
    public ActionDescriptor {
        WeaverIds.descriptor(id); WeaverIds.descriptor(facetId); java.util.Objects.requireNonNull(label); java.util.Objects.requireNonNull(risk);
        lifetimes = Set.copyOf(lifetimes); integrityModes = Set.copyOf(integrityModes); integrityImpacts = Set.copyOf(integrityImpacts);
        subjects = Set.copyOf(subjects); parameters = List.copyOf(parameters); java.util.Objects.requireNonNull(areaSupport);
        java.util.Objects.requireNonNull(areaLimits); java.util.Objects.requireNonNull(irreversibleReason);
        java.util.Objects.requireNonNull(revisionScope);
        if (lifetimes.isEmpty() || integrityModes.isEmpty() || subjects.isEmpty() || integrityImpacts.isEmpty() || parameters.size() > 16
                || rateCost < 1 || rateCost > 10) throw new IllegalArgumentException("Invalid action manifest bounds");
        if (parameters.stream().map(ActionParameter::id).distinct().count() != parameters.size()) throw new IllegalArgumentException("Duplicate parameter id");
        if (irreversibleReason.isPresent() && (irreversibleReason.get().isBlank() || irreversibleReason.get().length() > 256)) throw new IllegalArgumentException("Invalid irreversible reason");
    }
    public boolean requiresJournal() { return lifetimes.contains(Lifetime.PERSISTENT) || risk.ordinal() >= RiskLevel.MUTATING.ordinal()
            || integrityImpacts.stream().anyMatch(impact -> impact != IntegrityImpact.NONE); }
    public ActionDescriptor withRevisionScope(final WeaverRevisionScope scope) {
        return new ActionDescriptor(id, facetId, label, risk, lifetimes, integrityModes, integrityImpacts, subjects, parameters, areaSupport, areaLimits, undoable, irreversibleReason, rateCost, scope);
    }
}
