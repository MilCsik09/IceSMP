package hu.taliann.icesmp.dev.weaver.area;

import hu.taliann.icesmp.dev.weaver.api.AreaSupport;
import hu.taliann.icesmp.dev.weaver.execution.RegionOwner;
import hu.taliann.icesmp.dev.weaver.subject.*;
import java.util.List;
import java.util.Optional;

/** Both methods execute on the requested owner; only immutable values cross the return boundary. */
public interface WeaverAreaAccess {
    record ChunkSelection(List<SubjectRef> targets, Optional<String> unavailable) {
        public ChunkSelection {
            targets = List.copyOf(targets); java.util.Objects.requireNonNull(unavailable);
            if (targets.size() > 4096 || unavailable.isPresent() && (!targets.isEmpty() || !unavailable.get().matches("[A-Z_]{1,64}"))) throw new IllegalArgumentException("Invalid chunk selection");
        }
        public static ChunkSelection unavailable(final String code) { return new ChunkSelection(List.of(), Optional.of(code)); }
    }
    ChunkSelection collectOnOwner(AreaRef area, RegionOwner chunk, AreaSupport support, int limit);
    SubjectSnapshot snapshotOnOwner(SubjectRef subject);
}
