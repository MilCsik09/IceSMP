package hu.taliann.icesmp.dev.weaver.api;

import java.util.Set;

public record ImportDescriptor(String id, String actionId, WeaverTypeId acceptedType,
                               Set<String> requiredCapabilities, String parameterId) {
    public ImportDescriptor {
        WeaverIds.descriptor(id); WeaverIds.descriptor(actionId); java.util.Objects.requireNonNull(acceptedType);
        WeaverIds.parameter(parameterId); requiredCapabilities = Set.copyOf(requiredCapabilities);
        requiredCapabilities.forEach(WeaverIds::descriptor);
        if (requiredCapabilities.size() > 32) throw new IllegalArgumentException("Import capability cap");
    }
}
