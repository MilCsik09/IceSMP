package hu.taliann.icesmp.classspec.integration;

import java.util.Map;
import java.util.UUID;

/** Version-independent boundary for ModelEngine-backed pets, minions and transformations. */
public interface ClassSpecModelPort {

    ModelHandle attach(UUID entityId, String modelId, Map<String, String> context);

    void play(ModelHandle handle, String animationId, double speed);

    void detach(ModelHandle handle);

    record ModelHandle(UUID entityId, String modelId, UUID handleId) {
    }
}
