package hu.taliann.icesmp.dev.weaver.persistence;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.projection.WeaverProjection;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.RewardSource;
import java.util.*;
import static hu.taliann.icesmp.dev.weaver.persistence.WeaverJournalCodec.*;

/** Only tagged immutable identities and registered typed values enter the durable effect schema. */
final class WeaverEffectCodec {
    private final WeaverJournalCodec journal;
    WeaverEffectCodec(final WeaverJournalCodec journal) { this.journal = journal; }
    Map<String, Object> projection(final WeaverProjection projection) {
        final Map<String, Object> row = new TreeMap<>();
        row.put("id", projection.projectionId().toString()); row.put("sequence", projection.sequence()); row.put("provider", projection.providerId());
        row.put("action", projection.actionId()); row.put("subject", SubjectKeyCodec.encode(projection.subject())); row.put("lifetime", projection.lifetime().name());
        row.put("influence", origin(projection.influence())); row.put("values", journal.values(projection.values())); row.put("canonical", projection.canonicalFingerprintAtApply());
        row.put("created", projection.createdAt()); row.put("expires", projection.expiresAt().orElse(-1)); return Map.copyOf(row);
    }
    WeaverProjection projection(final Map<String, Object> row) {
        keys(row, "id", "sequence", "provider", "action", "subject", "lifetime", "influence", "values", "canonical", "created", "expires");
        final long expires = number(row, "expires"); if (expires < -1) throw new IllegalArgumentException("Invalid expiry");
        return new WeaverProjection(uuid(row, "id"), number(row, "sequence"), text(row, "provider"), text(row, "action"), SubjectKeyCodec.decode(text(row, "subject")),
                Lifetime.valueOf(text(row, "lifetime")), origin(map(row.get("influence"))), journal.readValues(map(row.get("values"))), text(row, "canonical"), number(row, "created"),
                expires == -1 ? OptionalLong.empty() : OptionalLong.of(expires));
    }
    Map<String, Object> influence(final WeaverInfluenceRecord influence) {
        return Map.of("id", influence.id().toString(), "origin", origin(influence.influence()), "target", target(influence.target()),
                "active", influence.active(), "until", influence.quarantinedUntil());
    }
    WeaverInfluenceRecord influence(final Map<String, Object> row) {
        keys(row, "id", "origin", "target", "active", "until");
        return new WeaverInfluenceRecord(uuid(row, "id"), origin(map(row.get("origin"))), target(map(row.get("target"))), bool(row, "active"), number(row, "until"));
    }
    List<Object> intent(final WeaverEffectIntent intent) {
        return intent.targets().stream().sorted(Comparator.comparing(target -> target(target).toString())).map(target -> (Object) target(target)).toList();
    }
    WeaverEffectIntent intent(final Object encoded) {
        if (!(encoded instanceof List<?> list) || list.size() > 128) throw new IllegalArgumentException("Intent encoding");
        final Set<WeaverInfluenceTarget> targets = new HashSet<>();
        for (final Object value : list) if (!targets.add(target(map(value)))) throw new IllegalArgumentException("Duplicate intent target");
        return new WeaverEffectIntent(targets);
    }
    private Map<String, Object> origin(final DeveloperInfluence origin) {
        return Map.of("operation", origin.operationId().toString(), "actor", origin.actorId().toString(), "action", origin.actionId(), "mode", origin.mode().name(), "applied", origin.appliedAt());
    }
    private DeveloperInfluence origin(final Map<String, Object> row) {
        keys(row, "operation", "actor", "action", "mode", "applied");
        return new DeveloperInfluence(uuid(row, "operation"), IntegrityMode.valueOf(text(row, "mode")), text(row, "action"), uuid(row, "actor"), number(row, "applied"));
    }
    private Map<String, Object> target(final WeaverInfluenceTarget target) {
        final Map<String, Object> source = switch (target.source()) {
            case RewardSource.Player player -> Map.of("kind", "PLAYER", "id", player.id().toString());
            case RewardSource.Entity entity -> Map.of("kind", "ENTITY", "id", entity.id().toString());
            case RewardSource.Item item -> Map.of("kind", "ITEM", "id", item.instanceId().toString());
            case RewardSource.Event event -> Map.of("kind", "EVENT", "id", event.instanceId().toString(), "owner", event.lifecycleOwner());
            case RewardSource.World world -> Map.of("kind", "WORLD", "id", world.id().toString());
            case RewardSource.Location location -> Map.of("kind", "LOCATION", "world", location.world().toString(), "x", location.x(), "y", location.y(), "z", location.z());
        };
        return Map.of("source", source, "area", target.area().map(SubjectKeyCodec::encode).orElse(""));
    }
    private WeaverInfluenceTarget target(final Map<String, Object> row) {
        keys(row, "source", "area"); final Map<String, Object> source = map(row.get("source")); final String kind = text(source, "kind");
        switch (kind) {
            case "PLAYER", "ENTITY", "ITEM", "WORLD" -> keys(source, "kind", "id");
            case "EVENT" -> keys(source, "kind", "id", "owner");
            case "LOCATION" -> keys(source, "kind", "world", "x", "y", "z");
            default -> throw new IllegalArgumentException("Unknown reward source kind");
        }
        final RewardSource decoded = switch (kind) {
            case "PLAYER" -> new RewardSource.Player(uuid(source, "id"));
            case "ENTITY" -> new RewardSource.Entity(uuid(source, "id"));
            case "ITEM" -> new RewardSource.Item(uuid(source, "id"));
            case "WORLD" -> new RewardSource.World(uuid(source, "id"));
            case "EVENT" -> new RewardSource.Event(text(source, "owner"), uuid(source, "id"));
            case "LOCATION" -> new RewardSource.Location(uuid(source, "world"), decimal(source, "x"), decimal(source, "y"), decimal(source, "z"));
            default -> throw new IllegalArgumentException("Unknown reward source kind");
        };
        final String area = text(row, "area");
        return new WeaverInfluenceTarget(decoded, area.isEmpty() ? Optional.empty() : Optional.of((AreaRef) SubjectKeyCodec.decode(area)));
    }
    private static double decimal(final Map<String, Object> row, final String key) {
        final Object value = row.get(key);
        if (!(value instanceof Double number) || !Double.isFinite(number)) throw new IllegalArgumentException("Expected finite coordinate"); return number;
    }
}
