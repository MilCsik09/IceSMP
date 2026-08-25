package hu.taliann.icesmp.classspec.integration;

import java.util.Map;
import java.util.UUID;

/** Boundary for the current FancyNpcs mentor and native confirmation flows. */
public interface ClassSpecDialogPort {

    void open(UUID playerId, String dialogId, Map<String, String> context, String validationToken);

    void clear(UUID playerId);
}
