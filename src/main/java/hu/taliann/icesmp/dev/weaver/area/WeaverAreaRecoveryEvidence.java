package hu.taliann.icesmp.dev.weaver.area;

import hu.taliann.icesmp.dev.weaver.api.AreaSupport;
import hu.taliann.icesmp.dev.weaver.execution.RegionOwner;
import hu.taliann.icesmp.dev.weaver.persistence.WeaverJournalCodec;
import hu.taliann.icesmp.dev.weaver.subject.*;
import java.util.*;

/** Recovery captures the acknowledged target set, never newly arrived entities or newly loaded skipped chunks. */
public record WeaverAreaRecoveryEvidence(AreaRef area, AreaSupport support, List<SubjectRef> targets,
                                         Map<RegionOwner, String> skippedChunks, Map<SubjectRef, String> skippedTargets) {
    public static final String KEY = "weaver.area";
    public WeaverAreaRecoveryEvidence {
        Objects.requireNonNull(area); Objects.requireNonNull(support); targets = List.copyOf(targets);
        skippedChunks = Map.copyOf(skippedChunks); skippedTargets = Map.copyOf(skippedTargets);
        final int cap = support == AreaSupport.ENTITY_FANOUT ? 128 : support == AreaSupport.BLOCK_FANOUT ? 4096 : 0;
        if (cap == 0 || targets.size() + skippedTargets.size() > cap || skippedChunks.size() > 9 || targets.stream().distinct().count() != targets.size()) throw new IllegalArgumentException("AREA recovery bounds");
        for (final SubjectRef ref : targets) if (!WeaverAreaCollection.kind(support, ref) || skippedTargets.containsKey(ref)
                || ref instanceof BlockRef block && (!block.worldId().equals(area.worldId()) || !area.shape().contains(block.x(), block.y(), block.z()))) throw new IllegalArgumentException("AREA recovery target identity");
        for (final SubjectRef ref : skippedTargets.keySet()) if (!WeaverAreaCollection.kind(support, ref)) throw new IllegalArgumentException("AREA skipped target identity");
        for (final var entry : skippedChunks.entrySet()) if (!WeaverAreaEngine.chunks(area).contains(entry.getKey()) || !entry.getValue().matches("[A-Z_]{1,64}")) throw new IllegalArgumentException("AREA skipped chunk identity");
        for (final String code : skippedTargets.values()) if (!code.matches("[A-Z_]{1,64}")) throw new IllegalArgumentException("AREA skip code");
    }
    public static WeaverAreaRecoveryEvidence of(final WeaverAreaCollection collection) {
        return new WeaverAreaRecoveryEvidence(collection.area(), collection.support(), collection.targets().stream().map(SubjectSnapshot::ref).toList(), collection.skippedChunks(), collection.skippedTargets());
    }
    public Map<String, Object> encode() {
        return Map.of("schema", 1, "support", support.name(), "targets", targets.stream().map(SubjectKeyCodec::payload).toList(),
                "skipped-chunks", skippedChunks.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<RegionOwner, String> entry) -> entry.getKey().chunkX()).thenComparingInt(entry -> entry.getKey().chunkZ()))
                        .map(entry -> Map.of("x", entry.getKey().chunkX(), "z", entry.getKey().chunkZ(), "code", entry.getValue())).toList(),
                "skipped-targets", skippedTargets.entrySet().stream().sorted(Comparator.comparing(entry -> SubjectKeyCodec.encode(entry.getKey())))
                        .map(entry -> Map.of("ref", SubjectKeyCodec.payload(entry.getKey()), "code", entry.getValue())).toList());
    }
    public static WeaverAreaRecoveryEvidence decode(final AreaRef area, final Object encoded) {
        final Map<String, Object> data = WeaverJournalCodec.map(encoded);
        if (!data.keySet().equals(Set.of("schema", "support", "targets", "skipped-chunks", "skipped-targets"))
                || integer(data.get("schema")) != 1) throw new IllegalArgumentException("AREA recovery schema");
        final List<SubjectRef> targets = list(data.get("targets"), 4096).stream().map(value -> SubjectKeyCodec.decodePayload(WeaverJournalCodec.map(value))).toList();
        final Map<RegionOwner, String> chunks = new LinkedHashMap<>();
        for (final Object value : list(data.get("skipped-chunks"), 9)) {
            final var row = WeaverJournalCodec.map(value);
            if (!row.keySet().equals(Set.of("x", "z", "code")) || chunks.putIfAbsent(new RegionOwner(area.worldId(), integer(row.get("x")), integer(row.get("z"))), (String) row.get("code")) != null) throw new IllegalArgumentException("AREA duplicate/unknown skipped chunk");
        }
        final Map<SubjectRef, String> skipped = new LinkedHashMap<>();
        for (final Object value : list(data.get("skipped-targets"), 4096)) {
            final var row = WeaverJournalCodec.map(value);
            if (!row.keySet().equals(Set.of("ref", "code")) || skipped.putIfAbsent(SubjectKeyCodec.decodePayload(WeaverJournalCodec.map(row.get("ref"))), (String) row.get("code")) != null) throw new IllegalArgumentException("AREA duplicate/unknown skipped target");
        }
        return new WeaverAreaRecoveryEvidence(area, AreaSupport.valueOf((String) data.get("support")), targets, chunks, skipped);
    }
    private static int integer(final Object value) {
        if (!(value instanceof Integer || value instanceof Long)) throw new IllegalArgumentException("AREA recovery integer"); return Math.toIntExact(((Number) value).longValue());
    }
    private static List<?> list(final Object value, final int cap) { if (!(value instanceof List<?> list) || list.size() > cap) throw new IllegalArgumentException("AREA recovery list"); return list; }
}
