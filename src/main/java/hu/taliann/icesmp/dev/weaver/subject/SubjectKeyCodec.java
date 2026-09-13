package hu.taliann.icesmp.dev.weaver.subject;

import hu.taliann.icesmp.dev.artifact.DevArtifactStateCodec;
import hu.taliann.icesmp.dev.weaver.api.CanonicalValueBytes;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Versioned explicit data, never an object serialization or a Bukkit handle. */
public final class SubjectKeyCodec {
    private SubjectKeyCodec() {}
    public static String encode(final SubjectRef subject) {
        return "weaver-subject@1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(CanonicalValueBytes.encode(payload(subject)));
    }
    public static SubjectRef decode(final String key) {
        if (key == null || !key.startsWith("weaver-subject@1:") || key.length() > 32768) throw new IllegalArgumentException("Invalid subject key");
        return decodePayload(CanonicalValueBytes.decode(Base64.getUrlDecoder().decode(key.substring(17))));
    }
    public static Map<String, Object> payload(final SubjectRef subject) {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", subject.kind().name());
        switch (subject) {
            case PlayerRef p -> data.put("player", p.playerId().toString());
            case EntityRef e -> data.put("entity", e.entityId().toString());
            case WorldRef w -> data.put("world", w.worldId().toString());
            case BlockRef b -> { data.put("world", b.worldId().toString()); data.put("x", b.x()); data.put("y", b.y()); data.put("z", b.z()); }
            case LocationRef l -> {
                data.put("world", l.worldId().toString()); data.put("x", l.x()); data.put("y", l.y()); data.put("z", l.z());
                data.put("yaw", (double) l.yaw()); data.put("pitch", (double) l.pitch());
            }
            case ItemSlotRef i -> {
                data.put("holder", i.holderId().toString()); data.put("slot", i.slot().kind().name()); data.put("index", i.slot().index());
                i.logicalId().ifPresent(id -> data.put("logical", id)); i.instanceId().ifPresent(id -> data.put("instance", id.toString()));
                if (i.revision().isPresent()) data.put("revision", i.revision().getAsLong()); data.put("fingerprint", i.fingerprint());
            }
            case AreaRef a -> { data.put("world", a.worldId().toString()); data.put("shape", shape(a.shape())); }
        }
        return Map.copyOf(data);
    }
    public static SubjectRef decodePayload(final Map<String, Object> data) {
        final SubjectRef result = switch (WeaverSubjectKind.valueOf(text(data, "kind"))) {
            case PLAYER -> new PlayerRef(uuid(data, "player"));
            case ENTITY -> new EntityRef(uuid(data, "entity"));
            case WORLD -> new WorldRef(uuid(data, "world"));
            case BLOCK -> new BlockRef(uuid(data, "world"), integer(data, "x"), integer(data, "y"), integer(data, "z"));
            case LOCATION -> new LocationRef(uuid(data, "world"), decimal(data, "x"), decimal(data, "y"), decimal(data, "z"),
                    (float) decimal(data, "yaw"), (float) decimal(data, "pitch"));
            case ITEM_SLOT -> new ItemSlotRef(uuid(data, "holder"), new WeaverSlot(WeaverSlot.Kind.valueOf(text(data, "slot")), integer(data, "index")),
                    data.containsKey("logical") ? Optional.of(text(data, "logical")) : Optional.empty(),
                    data.containsKey("instance") ? Optional.of(uuid(data, "instance")) : Optional.empty(),
                    data.containsKey("revision") ? OptionalLong.of(DevArtifactStateCodec.integer(data, "revision")) : OptionalLong.empty(), text(data, "fingerprint"));
            case AREA -> new AreaRef(uuid(data, "world"), decodeShape(DevArtifactStateCodec.map(data, "shape"), 0));
        };
        if (!java.util.Arrays.equals(CanonicalValueBytes.encode(data), CanonicalValueBytes.encode(payload(result)))) {
            throw new IllegalArgumentException("Unknown or noncanonical subject fields");
        }
        return result;
    }
    private static Map<String, Object> shape(final AreaShape area) {
        return switch (area) {
            case RadiusArea r -> Map.of("shape", "radius", "x", r.x(), "y", r.y(), "z", r.z(), "radius", r.radius());
            case CylinderArea c -> Map.of("shape", "cylinder", "x", c.x(), "z", c.z(), "radius", c.radius(), "min-y", c.minY(), "max-y", c.maxY());
            case CuboidArea c -> Map.of("shape", "cuboid", "min-x", c.bounds().minX(), "min-y", c.bounds().minY(), "min-z", c.bounds().minZ(),
                    "max-x", c.bounds().maxX(), "max-y", c.bounds().maxY(), "max-z", c.bounds().maxZ());
            case TerritoryArea t -> Map.of("shape", "territory", "id", t.territoryId(), "revision", t.revision(), "resolved", shape(t.resolved()));
            case PolygonPrismArea p -> Map.of("shape", "polygon", "min-y", p.minY(), "max-y", p.maxY(), "vertices",
                    p.vertices().stream().map(v -> Map.of("x", v.x(), "z", v.z())).toList());
        };
    }
    private static AreaShape decodeShape(final Map<String, Object> data, final int depth) {
        if (depth > 1) throw new IllegalArgumentException("Nested territory shape");
        return switch (text(data, "shape")) {
            case "radius" -> new RadiusArea(decimal(data, "x"), decimal(data, "y"), decimal(data, "z"), decimal(data, "radius"));
            case "cylinder" -> new CylinderArea(decimal(data, "x"), decimal(data, "z"), decimal(data, "radius"), integer(data, "min-y"), integer(data, "max-y"));
            case "cuboid" -> new CuboidArea(new AreaBounds(integer(data, "min-x"), integer(data, "min-y"), integer(data, "min-z"),
                    integer(data, "max-x"), integer(data, "max-y"), integer(data, "max-z")));
            case "territory" -> new TerritoryArea(text(data, "id"), text(data, "revision"), decodeShape(DevArtifactStateCodec.map(data, "resolved"), depth + 1));
            case "polygon" -> {
                if (!(data.get("vertices") instanceof List<?> list) || list.size() > 256) throw new IllegalArgumentException("Invalid polygon vertices");
                final java.util.ArrayList<PolygonPrismArea.Vertex> vertices = new java.util.ArrayList<>();
                for (final Object vertex : list) {
                    final Map<String, Object> v = DevArtifactStateCodec.map(Map.of("vertex", vertex), "vertex");
                    vertices.add(new PolygonPrismArea.Vertex(integer(v, "x"), integer(v, "z")));
                }
                yield new PolygonPrismArea(vertices, integer(data, "min-y"), integer(data, "max-y"));
            }
            default -> throw new IllegalArgumentException("Unknown area shape");
        };
    }
    private static String text(final Map<String, Object> data, final String key) { return DevArtifactStateCodec.string(data, key); }
    private static UUID uuid(final Map<String, Object> data, final String key) { return UUID.fromString(text(data, key)); }
    private static int integer(final Map<String, Object> data, final String key) { return Math.toIntExact(DevArtifactStateCodec.integer(data, key)); }
    private static double decimal(final Map<String, Object> data, final String key) {
        if (data.get(key) instanceof Double value && Double.isFinite(value)) return value;
        throw new IllegalArgumentException("Missing decimal subject coordinate");
    }
}
