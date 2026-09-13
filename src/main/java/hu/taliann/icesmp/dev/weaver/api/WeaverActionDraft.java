package hu.taliann.icesmp.dev.weaver.api;

import hu.taliann.icesmp.dev.weaver.subject.SubjectSnapshot;
import java.util.*;

public record WeaverActionDraft(UUID id, ActionDescriptor descriptor, SubjectSnapshot snapshot,
                                Lifetime lifetime, IntegrityMode integrityMode, Map<String, WeaverValue> parameters) {
    public WeaverActionDraft {
        Objects.requireNonNull(id); Objects.requireNonNull(descriptor); Objects.requireNonNull(snapshot);
        Objects.requireNonNull(lifetime); Objects.requireNonNull(integrityMode); parameters = Map.copyOf(parameters);
        if (!descriptor.lifetimes().contains(lifetime) || !descriptor.integrityModes().contains(integrityMode)
                || !descriptor.subjects().contains(snapshot.ref().kind()) || parameters.size() > 16
                || parameters.keySet().stream().anyMatch(key -> descriptor.parameters().stream().noneMatch(p -> p.id().equals(key)))) {
            throw new IllegalArgumentException("Action draft violates descriptor");
        }
    }
    public static WeaverActionDraft start(final ActionDescriptor descriptor, final SubjectSnapshot snapshot, final IntegrityMode mode) {
        final Map<String, WeaverValue> defaults = new HashMap<>();
        descriptor.parameters().forEach(parameter -> parameter.defaultValue().ifPresent(value -> defaults.put(parameter.id(), value)));
        final Lifetime lifetime = Arrays.stream(Lifetime.values()).filter(descriptor.lifetimes()::contains).findFirst().orElseThrow();
        return new WeaverActionDraft(UUID.randomUUID(), descriptor, snapshot, lifetime, mode, defaults);
    }
    public WeaverActionDraft with(final String parameterId, final WeaverValue value, final WeaverTypeRegistry types) {
        final ActionParameter parameter = descriptor.parameters().stream().filter(p -> p.id().equals(parameterId)).findFirst().orElseThrow();
        parameter.validate(value, types).requireValid();
        final Map<String, WeaverValue> next = new HashMap<>(parameters); next.put(parameterId, value);
        return new WeaverActionDraft(UUID.randomUUID(), descriptor, snapshot, lifetime, integrityMode, next);
    }
    public ValidationResult validate(final WeaverTypeRegistry types) {
        for (final ActionParameter parameter : descriptor.parameters()) {
            final WeaverValue value = parameters.get(parameter.id());
            if (value == null) { if (parameter.required()) return ValidationResult.rejected("MISSING_PARAMETER"); }
            else { final var result = parameter.validate(value, types); if (!result.valid()) return result; }
        }
        return ValidationResult.accepted();
    }
    public ActionRequest request(final WeaverTypeRegistry types) { validate(types).requireValid(); return new ActionRequest(descriptor.id(), parameters, lifetime, integrityMode); }
}
