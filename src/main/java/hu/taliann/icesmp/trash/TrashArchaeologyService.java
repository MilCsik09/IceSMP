package hu.taliann.icesmp.trash;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Inspection orchestration over immutable item evidence and Profile v2 personal knowledge. */
public final class TrashArchaeologyService {

    private final TrashArchaeologyProfileStore profiles;
    private final TrashArchaeologyFactEngine facts;

    public TrashArchaeologyService(final TrashArchaeologyProfileStore profiles,
                                   final TrashArchaeologyFactEngine facts) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.facts = Objects.requireNonNull(facts, "facts");
    }

    public TrashArchaeologyProfileStore.Profile profile(final UUID playerId) {
        return profiles.profile(playerId);
    }

    public CompletionStage<Result> inspect(final UUID playerId, final ItemStack snapshot) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(snapshot, "snapshot");
        if (hu.taliann.icesmp.itemization.ItemPrototypePolicy.scan(snapshot)
                != hu.taliann.icesmp.itemization.ItemPrototypePolicy.Scan.CLEAN) return CompletableFuture.completedFuture(Result.rejected());
        final TrashArchaeologyProfileStore.Profile before;
        final TrashArchaeologyFactEngine.Evaluation evaluation;
        try {
            before = profiles.profile(playerId);
            if (!facts.isCatalogued(snapshot) && !snapshot.getType().isAir()) {
                final var observation = new TrashArchaeologyFactEngine.Fact("unclassified",
                        TrashArchaeologyFactEngine.Category.MATERIAL, 0, 0, false, 0L,
                        "A vizsgálat nem tárt fel biztosan azonosítható történeti nyomot.");
                return CompletableFuture.completedFuture(new Result(true, "", 0L, List.of(observation),
                        before, java.util.Set.of(), 0L, false));
            }
            evaluation = facts.evaluate(snapshot.clone(), before.level()).orElse(null);
        } catch (final RuntimeException rejected) {
            return CompletableFuture.completedFuture(Result.rejected());
        }
        if (evaluation == null) return CompletableFuture.completedFuture(Result.rejected());
        return profiles.commitInspection(playerId, evaluation.evidence()).thenApply(commit ->
                new Result(true, evaluation.trashId(), evaluation.historyRevision(),
                        evaluation.facts(), commit.profile(), commit.novelSignatures(),
                        commit.awardedInsight(), commit.unlockedNow()));
    }

    public CompletionStage<TrashArchaeologyProfileStore.Profile> unlock(final UUID playerId) {
        return profiles.unlock(playerId);
    }

    public CompletionStage<TrashArchaeologyProfileStore.Profile> setLevel(
            final UUID playerId, final int level) {
        return profiles.setLevel(playerId, level);
    }

    public CompletionStage<TrashArchaeologyProfileStore.Profile> addInsight(
            final UUID playerId, final long amount) {
        return profiles.addInsight(playerId, amount);
    }

    public CompletionStage<TrashArchaeologyProfileStore.Profile> reset(final UUID playerId) {
        return profiles.reset(playerId);
    }

    public record Result(boolean accepted, String trashId, long historyRevision,
                         List<TrashArchaeologyFactEngine.Fact> visibleFacts,
                         TrashArchaeologyProfileStore.Profile profile,
                         java.util.Set<String> novelSignatures, long awardedInsight,
                         boolean unlockedNow) {
        public Result {
            trashId = trashId == null ? "" : trashId;
            visibleFacts = List.copyOf(visibleFacts);
            novelSignatures = java.util.Set.copyOf(novelSignatures);
            Objects.requireNonNull(profile, "profile");
        }

        public static Result rejected() {
            return new Result(false, "", 0L, List.of(),
                    TrashArchaeologyProfileStore.Profile.empty(), java.util.Set.of(), 0L, false);
        }
    }
}
