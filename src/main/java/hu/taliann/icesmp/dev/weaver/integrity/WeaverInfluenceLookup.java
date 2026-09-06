package hu.taliann.icesmp.dev.weaver.integrity;

import hu.taliann.icesmp.dev.weaver.persistence.WeaverJournal;
import hu.taliann.icesmp.integrity.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Unacknowledged storage and shutdown deny rewards; a restart cannot silently treat missing evidence as clean. */
public final class WeaverInfluenceLookup implements InfluenceRewardEligibilityPolicy.Lookup {
    private final WeaverJournal journal;
    private final LongSupplier clock;
    public WeaverInfluenceLookup(final WeaverJournal journal, final LongSupplier clock) { this.journal = Objects.requireNonNull(journal); this.clock = Objects.requireNonNull(clock); }
    @Override public InfluenceRewardEligibilityPolicy.Evidence recipient(final UUID player) { return source(new RewardSource.Player(player)); }
    @Override public InfluenceRewardEligibilityPolicy.Evidence source(final RewardSource source) {
        if (!journal.ready()) return InfluenceRewardEligibilityPolicy.Evidence.UNAVAILABLE;
        final boolean quarantined = journal.influenceIndex().quarantined(source, clock.getAsLong());
        if (!journal.ready()) return InfluenceRewardEligibilityPolicy.Evidence.UNAVAILABLE;
        return quarantined ? InfluenceRewardEligibilityPolicy.Evidence.QUARANTINED : InfluenceRewardEligibilityPolicy.Evidence.CLEAN;
    }
}
