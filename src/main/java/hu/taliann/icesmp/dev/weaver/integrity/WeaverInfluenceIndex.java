package hu.taliann.icesmp.dev.weaver.integrity;

import hu.taliann.icesmp.dev.weaver.api.IntegrityMode;
import hu.taliann.icesmp.dev.weaver.api.DeveloperInfluence;
import hu.taliann.icesmp.dev.weaver.persistence.WeaverJournalState;
import hu.taliann.icesmp.dev.weaver.subject.AreaRef;
import hu.taliann.icesmp.integrity.RewardSource;
import java.util.*;

/** Built on the storage coordinator, then published with its journal generation; reward paths only read it. */
public final class WeaverInfluenceIndex {
    public record SourceEvidence(Set<DeveloperInfluence> origins, boolean uncertain) {
        public SourceEvidence { origins = Set.copyOf(origins); }
        public boolean clean() { return !uncertain && origins.isEmpty(); }
    }
    private record Evidence(Optional<WeaverInfluenceRecord> record, Optional<AreaRef> area) {
        boolean active(final long now) { return record.isEmpty() || record.get().quarantines(now); }
    }
    private record Chunk(UUID world, int x, int z) { }
    private final Map<RewardSource, List<Evidence>> exact;
    private final Map<Chunk, List<Evidence>> spatial;
    public WeaverInfluenceIndex(final WeaverJournalState state) {
        final Map<RewardSource, List<Evidence>> targets = new HashMap<>(); final Map<Chunk, List<Evidence>> regions = new HashMap<>();
        state.intents().forEach((operation, intent) -> {
            if (state.operations().get(operation).request().integrityMode() == IntegrityMode.SANDBOX) {
                intent.targets().forEach(target -> add(targets, regions, target, Optional.empty()));
            }
        });
        state.influences().values().stream().filter(record -> record.influence().quarantinesRewards())
                .forEach(record -> add(targets, regions, record.target(), Optional.of(record)));
        targets.replaceAll((source, values) -> List.copyOf(values)); regions.replaceAll((chunk, values) -> List.copyOf(values));
        exact = Map.copyOf(targets); spatial = Map.copyOf(regions);
    }
    private static void add(final Map<RewardSource, List<Evidence>> exact, final Map<Chunk, List<Evidence>> spatial,
                            final WeaverInfluenceTarget target, final Optional<WeaverInfluenceRecord> record) {
        final Evidence evidence = new Evidence(record, target.area());
        if (target.area().isEmpty()) { exact.computeIfAbsent(normalize(target.source()), ignored -> new ArrayList<>()).add(evidence); return; }
        final AreaRef area = target.area().get(); final var bounds = area.shape().bounds();
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
            spatial.computeIfAbsent(new Chunk(area.worldId(), x, z), ignored -> new ArrayList<>()).add(evidence);
        }
    }
    public boolean quarantined(final RewardSource source, final long now) {
        if (now < 0) throw new IllegalArgumentException("Invalid evidence time");
        if (exact.getOrDefault(normalize(source), List.of()).stream().anyMatch(evidence -> evidence.active(now))) return true;
        if (!(source instanceof RewardSource.Location location)) return false;
        if (exact.getOrDefault(new RewardSource.World(location.world()), List.of()).stream().anyMatch(evidence -> evidence.active(now))) return true;
        final int x = (int) Math.floor(location.x()), y = (int) Math.floor(location.y()), z = (int) Math.floor(location.z());
        return spatial.getOrDefault(new Chunk(location.world(), x >> 4, z >> 4), List.of()).stream()
                .anyMatch(evidence -> evidence.active(now) && evidence.area().orElseThrow().shape().contains(x, y, z));
    }
    /** Detached source lineage for durable derived effects; a PREPARED intent is uncertainty, not an applied origin. */
    public SourceEvidence trace(final Collection<RewardSource> sources, final long now) {
        if (now < 0 || sources.isEmpty() || sources.size() > 64) throw new IllegalArgumentException("Influence trace bounds");
        final Set<DeveloperInfluence> origins = new HashSet<>(); boolean uncertain = false;
        for (final RewardSource source : sources) {
            final List<Evidence> evidence = new ArrayList<>(exact.getOrDefault(normalize(source), List.of()));
            if (source instanceof RewardSource.Location location) {
                evidence.addAll(exact.getOrDefault(new RewardSource.World(location.world()), List.of()));
                final int x = (int) Math.floor(location.x()), y = (int) Math.floor(location.y()), z = (int) Math.floor(location.z());
                spatial.getOrDefault(new Chunk(location.world(), x >> 4, z >> 4), List.of()).stream()
                        .filter(value -> value.area().orElseThrow().shape().contains(x, y, z)).forEach(evidence::add);
            }
            for (final Evidence value : evidence) if (value.active(now)) {
                if (value.record().isEmpty()) uncertain = true;
                else origins.add(value.record().get().influence());
            }
        }
        return new SourceEvidence(origins, uncertain);
    }
    private static RewardSource normalize(final RewardSource source) {
        if (source instanceof RewardSource.Player player) return new RewardSource.Entity(player.id());
        if (source instanceof RewardSource.Location location) return new RewardSource.Location(location.world(), Math.floor(location.x()), Math.floor(location.y()), Math.floor(location.z()));
        return source;
    }
}
