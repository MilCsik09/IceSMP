package hu.taliann.icesmp.dev.weaver.area;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.RegionOwner;
import hu.taliann.icesmp.dev.weaver.subject.*;
import java.util.*;

/** Membership and child revision evidence is deterministic; capture timestamps never enter its fingerprint. */
public record WeaverAreaCollection(AreaRef area, AreaSupport support, List<SubjectSnapshot> targets,
                                   Map<RegionOwner, String> skippedChunks, Map<SubjectRef, String> skippedTargets) {
    public WeaverAreaCollection {
        Objects.requireNonNull(area); Objects.requireNonNull(support);
        targets = List.copyOf(targets); skippedChunks = Map.copyOf(skippedChunks); skippedTargets = Map.copyOf(skippedTargets);
        final int cap = support == AreaSupport.ENTITY_FANOUT ? 128 : support == AreaSupport.BLOCK_FANOUT ? 4096 : 0;
        if (cap == 0 || targets.size() + skippedTargets.size() > cap || skippedChunks.size() > 9
                || targets.stream().map(SubjectSnapshot::ref).distinct().count() != targets.size()) throw new IllegalArgumentException("Invalid AREA collection bounds");
        for (final SubjectSnapshot snapshot : targets) {
            if (!kind(support, snapshot.ref()) || !contains(area, snapshot) || skippedTargets.containsKey(snapshot.ref())) throw new IllegalArgumentException("Foreign AREA target");
        }
        for (final SubjectRef skipped : skippedTargets.keySet()) if (!kind(support, skipped)) throw new IllegalArgumentException("Foreign skipped AREA kind");
        for (final RegionOwner chunk : skippedChunks.keySet()) if (!WeaverAreaEngine.chunks(area).contains(chunk)) throw new IllegalArgumentException("Foreign skipped AREA chunk");
        for (final String code : skippedChunks.values()) validateCode(code);
        for (final String code : skippedTargets.values()) validateCode(code);
    }
    private static void validateCode(final String code) { if (!code.matches("[A-Z_]{1,64}")) throw new IllegalArgumentException("Invalid AREA failure code"); }
    static boolean kind(final AreaSupport support, final SubjectRef ref) {
        return support == AreaSupport.BLOCK_FANOUT ? ref instanceof BlockRef : support == AreaSupport.ENTITY_FANOUT && (ref instanceof PlayerRef || ref instanceof EntityRef);
    }
    public static boolean contains(final AreaRef area, final SubjectSnapshot snapshot) {
        if (snapshot.ref() instanceof BlockRef block) return block.worldId().equals(area.worldId()) && area.shape().contains(block.x(), block.y(), block.z());
        if (!(snapshot.ref() instanceof EntityRef || snapshot.ref() instanceof PlayerRef)) return false;
        final WeaverValue value = snapshot.facts().get("minecraft.location");
        if (value == null || !value.type().equals(new WeaverTypeId("weaver", "location", 1))) return false;
        final SubjectRef decoded = SubjectKeyCodec.decodePayload(value.payload());
        return decoded instanceof LocationRef location && location.worldId().equals(area.worldId())
                && area.shape().contains((int) Math.floor(location.x()), (int) Math.floor(location.y()), (int) Math.floor(location.z()));
    }
    public String fingerprint() {
        try {
            final var digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(CanonicalValueBytes.encode(Map.of("area", SubjectKeyCodec.payload(area), "support", support.name())));
            targets.stream().sorted(Comparator.comparing(target -> SubjectKeyCodec.encode(target.ref()))).forEach(target -> digest.update(CanonicalValueBytes.encode(
                    Map.of("target", SubjectKeyCodec.payload(target.ref()), "revision", target.revisionFingerprint()))));
            skippedChunks.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<RegionOwner, String> entry) -> entry.getKey().chunkX()).thenComparingInt(entry -> entry.getKey().chunkZ()))
                    .forEach(entry -> digest.update(CanonicalValueBytes.encode(Map.of("chunk-x", entry.getKey().chunkX(), "chunk-z", entry.getKey().chunkZ(), "skipped", entry.getValue()))));
            skippedTargets.entrySet().stream().sorted(Comparator.comparing(entry -> SubjectKeyCodec.encode(entry.getKey())))
                    .forEach(entry -> digest.update(CanonicalValueBytes.encode(Map.of("target", SubjectKeyCodec.payload(entry.getKey()), "skipped", entry.getValue()))));
            return HexFormat.of().formatHex(digest.digest());
        } catch (final java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public SubjectSnapshot decorate(final SubjectSnapshot snapshot) {
        if (!snapshot.ref().equals(area)) throw new IllegalArgumentException("AREA snapshot differs from selection");
        final Map<String, WeaverValue> facts = new HashMap<>(snapshot.facts());
        final String selected = fingerprint();
        facts.put("weaver.area_targets", scalar("int", targets.size(), snapshot.capturedAt()));
        facts.put("weaver.area_skipped", scalar("int", skippedChunks.size() + skippedTargets.size(), snapshot.capturedAt()));
        facts.put("weaver.area_selection", scalar("text", selected, snapshot.capturedAt()));
        return new SubjectSnapshot(area, snapshot.capturedAt(), selected, facts);
    }
    private static WeaverValue scalar(final String type, final Object value, final long now) {
        return new WeaverValue(new WeaverTypeId("weaver", type, 1), Map.of("value", value), "weaver", "weaver.area", Set.of(), now);
    }
}
