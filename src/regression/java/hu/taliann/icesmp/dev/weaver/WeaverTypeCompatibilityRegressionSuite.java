package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import java.util.*;

public final class WeaverTypeCompatibilityRegressionSuite {
    private static final UUID ID = new UUID(1, 2);
    public static void main(final String[] args) {
        final List<SubjectRef> subjects = List.of(new PlayerRef(ID), new EntityRef(ID), new WorldRef(ID),
                new BlockRef(ID, -17, 64, 16), new LocationRef(ID, -0.25, 64.5, 16, 13.25F, -80),
                new ItemSlotRef(ID, WeaverSlot.named(WeaverSlot.Kind.OFF_HAND), Optional.of("icesmp:fixture"),
                        Optional.of(ID), OptionalLong.of(7), "a".repeat(64)),
                new AreaRef(ID, new RadiusArea(0, 64, 0, 8)),
                new AreaRef(ID, new CuboidArea(new AreaBounds(-16, 60, -16, 15, 65, 15))),
                new AreaRef(ID, new CylinderArea(0, 0, 16, 64, 70)),
                new AreaRef(ID, new PolygonPrismArea(List.of(new PolygonPrismArea.Vertex(0, 0),
                        new PolygonPrismArea.Vertex(15, 0), new PolygonPrismArea.Vertex(0, 15)), 64, 70)),
                new AreaRef(ID, new TerritoryArea("fixture", "revision-1", new CylinderArea(0, 0, 4, 64, 70))));
        for (final SubjectRef subject : subjects) {
            check(subject.equals(SubjectKeyCodec.decode(subject.stableKey())), "subject key round trip " + subject.kind());
        }
        final Map<String, Object> first = new LinkedHashMap<>(); first.put("z", 1); first.put("a", List.of("árvíztűrő", true));
        final Map<String, Object> second = new LinkedHashMap<>(); second.put("a", List.of("árvíztűrő", true)); second.put("z", 1L);
        check(Arrays.equals(CanonicalValueBytes.encode(first), CanonicalValueBytes.encode(second)), "map ordering/numeric YAML normalization");
        final byte[] bytes = CanonicalValueBytes.encode(first);
        check(Arrays.equals(bytes, CanonicalValueBytes.encode(CanonicalValueBytes.decode(bytes))), "canonical binary round trip");
        rejects(() -> CanonicalValueBytes.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        rejects(() -> CanonicalValueBytes.encode(Map.of("object", new Object())));
        rejects(() -> CanonicalValueBytes.encode(Map.of("number", Double.NaN)));
        rejects(() -> SubjectKeyCodec.decode("weaver-subject@2:bad"));
        final Map<String, Object> unknown = new LinkedHashMap<>(SubjectKeyCodec.payload(subjects.getFirst())); unknown.put("java-class", "x");
        rejects(() -> SubjectKeyCodec.decodePayload(unknown));
        rejects(() -> new LocationRef(ID, Double.POSITIVE_INFINITY, 0, 0, 0, 0));
        rejects(() -> new WeaverSlot(WeaverSlot.Kind.INVENTORY, 36));
        rejects(() -> new ItemSlotRef(ID, WeaverSlot.named(WeaverSlot.Kind.CURSOR), Optional.empty(), Optional.empty(), OptionalLong.empty(), "stale"));
        final WeaverTypeRegistry types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        final WeaverTypeId type = WeaverTypeId.parse("weaver:int@1");
        final WeaverValue value = new WeaverValue(type, Map.of("value", 7), "fixture", "fixture.state", Set.of("fixture.copy"), 1);
        types.validate(value);
        for (final String builtin : List.of("boolean", "int", "double", "duration_ticks", "uuid", "location", "area", "subject_ref", "projection_ref")) {
            check(types.contains(new WeaverTypeId("weaver", builtin, 1)), "missing normative builtin " + builtin);
        }
        types.require(WeaverTypeId.parse("weaver:location@1")).validate(SubjectKeyCodec.payload(subjects.get(4))).requireValid();
        check(!types.require(WeaverTypeId.parse("weaver:location@1")).validate(SubjectKeyCodec.payload(subjects.getFirst())).valid(), "player accepted as location");
        final var duration = WeaverTypeId.parse("weaver:duration_ticks@1");
        final var bound = new ActionParameter("ticks", net.kyori.adventure.text.Component.text("ticks"), duration,
                ActionParameter.InputKind.INTEGER, true, Optional.empty(), OptionalDouble.of(0), OptionalDouble.of(9007199254740992D),
                OptionalInt.empty(), Optional.empty(), Set.of());
        check(!bound.validate(new WeaverValue(duration, Map.of("value", 9007199254740993L), "fixture", "fixture.state", Set.of(), 1), types).valid(), "integer bound rounded away");
        check(types.compatible(value, type, Set.of("fixture.copy")), "matching capabilities refused");
        check(!types.compatible(value, type, Set.of("fixture.other")), "missing capability accepted");
        check(!types.compatible(value, WeaverTypeId.parse("weaver:int@2"), Set.of()), "schema coercion accepted");
        rejects(() -> types.require(WeaverTypeId.parse("weaver:int@2")));
        types.freeze(); rejects(() -> types.register(new ScalarTypeCodec(type, ScalarTypeCodec.Shape.INT, id -> false)));
        rejects(() -> value.payload().put("mutated", true));
        System.out.println("Weaver type compatibility regression suite passed.");
    }
    static void check(final boolean condition, final String reason) { if (!condition) throw new AssertionError(reason); }
    static void rejects(final Runnable action) {
        try { action.run(); } catch (final RuntimeException expected) { return; }
        throw new AssertionError("Invalid Weaver value/ref accepted");
    }
}
