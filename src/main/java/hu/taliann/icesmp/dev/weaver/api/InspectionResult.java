package hu.taliann.icesmp.dev.weaver.api;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;

public record InspectionResult(String facetId, Map<String, WeaverValue> facts, List<Component> notes) {
    public InspectionResult {
        WeaverIds.descriptor(facetId); facts = Map.copyOf(facts); notes = List.copyOf(notes);
        facts.keySet().forEach(WeaverIds::descriptor);
        if (facts.size() > 128 || notes.size() > 32) throw new IllegalArgumentException("Inspection result exceeds caps");
    }
}
