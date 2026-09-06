package hu.taliann.icesmp.dev.artifact;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DevArtifactPresentation(String material, String displayName, List<String> lore,
                                      Map<ModelState, String> models) {
    public enum ModelState { IDLE, SUBJECT_LOCKED, THREAD_HELD, CANON_ARMED }

    public DevArtifactPresentation {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(displayName, "displayName");
        lore = List.copyOf(lore);
        models = Map.copyOf(models);
        if (!models.containsKey(ModelState.IDLE)) throw new IllegalArgumentException("Missing idle model");
        for (final String model : models.values()) {
            if (!model.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) {
                throw new IllegalArgumentException("Invalid artifact item-model");
            }
        }
    }

    public String model(final ModelState state) {
        return models.getOrDefault(state, models.get(ModelState.IDLE));
    }

    public static ModelState state(final boolean subject, final boolean thread, final boolean armed) {
        return armed ? ModelState.CANON_ARMED : thread ? ModelState.THREAD_HELD
                : subject ? ModelState.SUBJECT_LOCKED : ModelState.IDLE;
    }
}
