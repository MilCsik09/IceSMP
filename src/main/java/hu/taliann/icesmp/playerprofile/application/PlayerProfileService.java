package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.api.AdminPlayerProfileDto;
import hu.taliann.icesmp.playerprofile.api.IceSMPPlayerProfileApi;
import hu.taliann.icesmp.playerprofile.api.PlayerProfileQueryService;
import hu.taliann.icesmp.playerprofile.api.PublicPlayerProfileDto;
import hu.taliann.icesmp.playerprofile.api.SelfPlayerProfileDto;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSnapshot;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionData;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionSnapshot;
import hu.taliann.icesmp.playerprofile.domain.SectionHealth;
import hu.taliann.icesmp.playerprofile.domain.section.IdentitySection;
import hu.taliann.icesmp.playerprofile.domain.section.LifecycleSection;
import hu.taliann.icesmp.playerprofile.persistence.PlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.persistence.PlayerProfileRepositoryException;
import hu.taliann.icesmp.playerprofile.transaction.PlayerProfileTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Storage-independent facade used by gameplay, Bukkit API and HTTP query adapters. */
public final class PlayerProfileService implements IceSMPPlayerProfileApi {
    private final PlayerProfileRepository repository;
    private final PlayerProfileTransactionManager transactions;
    private final PlayerProfileQueryService queries;
    private final Clock clock;
    private final List<PlayerProfileListener> listeners = new CopyOnWriteArrayList<>();
    // A process-wide monotonic token avoids both stale-session reuse and per-UUID retention.
    private final AtomicLong nextGeneration = new AtomicLong();
    private final ConcurrentMap<UUID, Long> currentGenerations = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<Void>> sessionTails = new ConcurrentHashMap<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public PlayerProfileService(final PlayerProfileRepository repository,
                                final PlayerProfileTransactionManager transactions) {
        this(repository, transactions, Clock.systemUTC());
    }

    public PlayerProfileService(final PlayerProfileRepository repository,
                                final PlayerProfileTransactionManager transactions,
                                final Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.queries = new PlayerProfileQueryService(repository);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public CompletionStage<Optional<PlayerProfileSnapshot>> find(final UUID id) { return queries.find(id); }
    @Override public CompletionStage<Optional<PlayerProfileSnapshot>> findByName(final String name) { return queries.findByName(name); }
    @Override public CompletionStage<Optional<ProfileSectionSnapshot<?>>> section(final UUID id,
                                                                                  final ProfileSectionId section) {
        return queries.section(id, section);
    }
    @Override public CompletionStage<PublicPlayerProfileDto> publicProfile(final UUID id) { return queries.publicDto(id); }
    @Override public CompletionStage<SelfPlayerProfileDto> selfProfile(final UUID id) { return queries.selfDto(id); }
    @Override public CompletionStage<AdminPlayerProfileDto> adminProfile(final UUID id) { return queries.adminDto(id); }

    @Override public AutoCloseable subscribe(final PlayerProfileListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    /** Initializes a greenfield profile when missing and durably fences a new login generation. */
    public CompletionStage<PlayerProfileSession> join(final UUID playerId, final String playerName) {
        Objects.requireNonNull(playerId, "playerId");
        final String cleanName = playerName == null ? "" : playerName.trim();
        if (cleanName.length() > 64) return CompletableFuture.failedFuture(
                new IllegalArgumentException("playerName exceeds 64 characters"));
        if (!accepting.get()) return CompletableFuture.failedFuture(
                new IllegalStateException("PlayerProfile lifecycle stopped"));
        return enqueue(playerId, () -> repository.loadSnapshot(playerId).thenCompose(snapshot -> {
            if (!accepting.get()) return CompletableFuture.failedFuture(
                    new IllegalStateException("PlayerProfile lifecycle stopped"));
            final long durableGeneration = snapshot.lifecycle().value().sessionGeneration();
            final long generation = nextGeneration.updateAndGet(current ->
                    Math.addExact(Math.max(current, durableGeneration), 1L));
            currentGenerations.put(playerId, generation);
            final Instant now = clock.instant();
            final long millis = now.toEpochMilli();
            final IdentitySection identity = snapshot.identity().value();
            final LifecycleSection lifecycle = snapshot.lifecycle().value();
            final IdentitySection nextIdentity = new IdentitySection(cleanName,
                    identity.firstSeenAt(), millis, millis, identity.publicVisible(), identity.apiVisible(),
                    identity.accountLinks(), identity.extensions());
            final LifecycleSection nextLifecycle = new LifecycleSection("active", lifecycle.profileCreatedAt(),
                    millis, millis, lifecycle.lastQuitAt(), generation, lifecycle.extensions());
            final String operationId = "session-join-" + generation;
            final String fingerprint = "join:" + playerId + ':' + generation + ':'
                    + cleanName.toLowerCase(Locale.ROOT);
            return transactions.execute(playerId, current -> {
                if (current.lifecycle().value().sessionGeneration() >= generation) {
                    throw new IllegalStateException("stale session generation");
                }
                return new PlayerProfileTransactionManager.TransactionPlan<>(operationId, "session-join",
                        fingerprint, List.of(
                        new PlayerProfileTransactionManager.SectionUpdate(ProfileSectionId.IDENTITY,
                                current.identity().revision(), nextIdentity),
                        new PlayerProfileTransactionManager.SectionUpdate(ProfileSectionId.LIFECYCLE,
                                current.lifecycle().revision(), nextLifecycle)), generation);
            }).thenCompose(committedGeneration -> repository.loadSnapshot(playerId))
                    .thenApply(durable -> new PlayerProfileSession(generation, durable.profileRevision()))
                    .whenComplete((session, failure) -> {
                        if (failure != null) currentGenerations.remove(playerId, generation);
                    });
        }));
    }

    /** A stale generation is a no-op; a current generation is durably closed before cache invalidation. */
    public CompletionStage<Void> quit(final UUID playerId, final long sessionGeneration) {
        Objects.requireNonNull(playerId, "playerId");
        return enqueue(playerId, () -> {
            if (!Objects.equals(currentGenerations.get(playerId), sessionGeneration)) {
                return CompletableFuture.completedFuture(null);
            }
            final Instant now = clock.instant();
            final long millis = now.toEpochMilli();
            final String operationId = "session-quit-" + sessionGeneration;
            final String fingerprint = "quit:" + playerId + ':' + sessionGeneration;
            return transactions.execute(playerId, current -> {
                final LifecycleSection lifecycle = current.lifecycle().value();
                if (lifecycle.sessionGeneration() != sessionGeneration
                        || !Objects.equals(currentGenerations.get(playerId), sessionGeneration)) {
                    throw new StaleSessionException();
                }
                final LifecycleSection next = new LifecycleSection("offline", lifecycle.profileCreatedAt(),
                        millis, lifecycle.lastJoinAt(), millis, sessionGeneration, lifecycle.extensions());
                return new PlayerProfileTransactionManager.TransactionPlan<>(operationId, "session-quit",
                        fingerprint, List.of(new PlayerProfileTransactionManager.SectionUpdate(
                        ProfileSectionId.LIFECYCLE, current.lifecycle().revision(), next)), null);
            }).handle((ignored, failure) -> {
                if (failure != null && !isStale(failure)) throw new java.util.concurrent.CompletionException(failure);
                return null;
            }).thenCompose(ignored -> repository.flush(playerId)).thenRun(() -> {
                repository.invalidate(playerId);
                currentGenerations.remove(playerId, sessionGeneration);
            });
        });
    }

    public boolean isCurrentSession(final UUID playerId, final long generation) {
        return Objects.equals(currentGenerations.get(playerId), generation);
    }

    public OptionalLong currentSessionGeneration(final UUID playerId) {
        final Long generation = currentGenerations.get(Objects.requireNonNull(playerId, "playerId"));
        return generation == null ? OptionalLong.empty() : OptionalLong.of(generation);
    }

    public CompletionStage<PlayerProfileSnapshot> load(final UUID id) { return repository.loadSnapshot(id); }

    public <T extends ProfileSectionData> CompletionStage<PlayerProfileSnapshot> mutateSection(
            final UUID id, final ProfileSectionId sectionId, final Class<T> type,
            final UnaryOperator<T> mutation) {
        return mutateSection(id, sectionId, type, mutation, 4);
    }

    private <T extends ProfileSectionData> CompletionStage<PlayerProfileSnapshot> mutateSection(
            final UUID id, final ProfileSectionId sectionId, final Class<T> type,
            final UnaryOperator<T> mutation, final int attempts) {
        return repository.loadSnapshot(id).thenCompose(snapshot -> {
            final ProfileSectionSnapshot<?> raw = snapshot.section(sectionId).orElseThrow();
            if (!raw.health().usable()) return CompletableFuture.failedFuture(
                    new PlayerProfileRepositoryException.Quarantined(raw.health().diagnostic()));
            final T current = type.cast(raw.value());
            final T next = Objects.requireNonNull(mutation.apply(current), "mutation result");
            final ProfileSectionSnapshot<T> candidate = new ProfileSectionSnapshot<>(sectionId,
                    raw.schema(), Math.addExact(raw.revision(), 1L), clock.instant(), next,
                    SectionHealth.healthy(), raw.extensions());
            return repository.saveSection(id, sectionId, raw.revision(), snapshot.profileRevision(), candidate)
                    .thenCompose(result -> {
                        if (result.status() == PlayerProfileRepository.SectionSaveResult.Status.COMMITTED) {
                            notifyChanged(id, result.snapshot().profileRevision(), Set.of(sectionId));
                            return CompletableFuture.completedFuture(result.snapshot());
                        }
                        if ((result.status() == PlayerProfileRepository.SectionSaveResult.Status.STALE_REVISION
                                || result.status() == PlayerProfileRepository.SectionSaveResult.Status.STALE_GENERATION)
                                && attempts > 1) {
                            // The repository's CAS failure path has already installed the newest durable
                            // snapshot in its cache. Keep that read authority live and retry against it;
                            // invalidating here creates a synchronous ProfileNotReady storm under contention.
                            return mutateSection(id, sectionId, type, mutation, attempts - 1);
                        }
                        return CompletableFuture.failedFuture(
                                new PlayerProfileRepositoryException(result.detail()));
                    });
        });
    }

    public <T extends ProfileSectionData, R> CompletionStage<R> mutateSectionConditional(
            final UUID id, final ProfileSectionId sectionId, final Class<T> type,
            final Function<T, ConditionalMutation<T, R>> mutation) {
        return mutateSectionConditional(id, sectionId, type, mutation, 4);
    }

    private <T extends ProfileSectionData, R> CompletionStage<R> mutateSectionConditional(
            final UUID id, final ProfileSectionId sectionId, final Class<T> type,
            final Function<T, ConditionalMutation<T, R>> mutation, final int attempts) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sectionId, "sectionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(mutation, "mutation");
        return repository.loadSnapshot(id).thenCompose(snapshot -> {
            final ProfileSectionSnapshot<?> raw = snapshot.section(sectionId).orElseThrow();
            if (!raw.health().usable()) return CompletableFuture.failedFuture(
                    new PlayerProfileRepositoryException.Quarantined(raw.health().diagnostic()));
            final T current = type.cast(raw.value());
            final ConditionalMutation<T, R> decision = Objects.requireNonNull(
                    mutation.apply(current), "conditional mutation result");
            if (!decision.changed()) {
                return CompletableFuture.completedFuture(decision.result());
            }
            final T next = Objects.requireNonNull(decision.next(), "conditional mutation next");
            final ProfileSectionSnapshot<T> candidate = new ProfileSectionSnapshot<>(sectionId,
                    raw.schema(), Math.addExact(raw.revision(), 1L), clock.instant(), next,
                    SectionHealth.healthy(), raw.extensions());
            return repository.saveSection(id, sectionId, raw.revision(), snapshot.profileRevision(), candidate)
                    .thenCompose(result -> {
                        if (result.status() == PlayerProfileRepository.SectionSaveResult.Status.COMMITTED) {
                            notifyChanged(id, result.snapshot().profileRevision(), Set.of(sectionId));
                            return CompletableFuture.completedFuture(decision.result());
                        }
                        if ((result.status() == PlayerProfileRepository.SectionSaveResult.Status.STALE_REVISION
                                || result.status() == PlayerProfileRepository.SectionSaveResult.Status.STALE_GENERATION)
                                && attempts > 1) {
                            // Same CAS contract as mutateSection: retain the repository-installed latest
                            // cache generation so sync consumers remain readable while this bounded retry runs.
                            return mutateSectionConditional(id, sectionId, type, mutation, attempts - 1);
                        }
                        return CompletableFuture.failedFuture(
                                new PlayerProfileRepositoryException(result.detail()));
                    });
        });
    }

    public record ConditionalMutation<T, R>(boolean changed, T next, R result) {
        public ConditionalMutation {
            if (changed && next == null) {
                throw new IllegalArgumentException("changed conditional mutation requires next value");
            }
        }

        public static <T, R> ConditionalMutation<T, R> unchanged(final R result) {
            return new ConditionalMutation<>(false, null, result);
        }

        public static <T, R> ConditionalMutation<T, R> changed(final T next, final R result) {
            return new ConditionalMutation<>(true, Objects.requireNonNull(next, "next"), result);
        }
    }

    public <T> CompletionStage<T> transact(final UUID id,
                                            final PlayerProfileTransactionManager.ProfileTransactionWork<T> work) {
        final AtomicReference<PlayerProfileSnapshot> before = new AtomicReference<>();
        final AtomicReference<Set<ProfileSectionId>> candidates =
                new AtomicReference<>(Set.of());
        return transactions.execute(id, snapshot -> {
            before.set(snapshot);
            final PlayerProfileTransactionManager.TransactionPlan<T> plan =
                    work.prepare(snapshot);
            final java.util.EnumSet<ProfileSectionId> sections =
                    java.util.EnumSet.of(ProfileSectionId.OPERATIONS);
            plan.updates().forEach(update -> sections.add(update.section()));
            candidates.set(Set.copyOf(sections));
            return plan;
        }).thenApply(result -> {
            repository.cached(id).ifPresent(profile -> {
                final Set<ProfileSectionId> changed = changedSections(
                        before.get(), profile, candidates.get());
                if (!changed.isEmpty()) notifyChanged(id, profile.profileRevision(), changed);
            });
            return result;
        });
    }

    public CompletionStage<Void> flush(final UUID id) { return repository.flush(id); }
    public void invalidate(final UUID id) { repository.invalidate(id); }

    public CompletionStage<PlayerProfileRepository.ShutdownResult> shutdown(final Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) throw new IllegalArgumentException("negative shutdown timeout");
        accepting.set(false);
        final long budgetNanos = Math.max(1L, timeout.toNanos());
        final long startedAt = System.nanoTime();
        final CompletableFuture<?>[] sessions = sessionTails.values().toArray(CompletableFuture[]::new);
        final CompletableFuture<Boolean> sessionDrain = CompletableFuture.allOf(sessions)
                .handle((ignored, failure) -> failure == null)
                .completeOnTimeout(false, budgetNanos, TimeUnit.NANOSECONDS);
        return sessionDrain.thenCompose(sessionsDrained -> {
            final long remaining = remainingNanos(startedAt, budgetNanos);
            final int pendingSessions = (int) java.util.Arrays.stream(sessions)
                    .filter(session -> !session.isDone()).count();
            final PlayerProfileRepository.ShutdownResult timeoutResult =
                    new PlayerProfileRepository.ShutdownResult(false, pendingSessions,
                            "repository shutdown deadline exceeded");
            return repository.shutdown(Duration.ofNanos(Math.max(1L, remaining)))
                    .handle((result, failure) -> failure == null ? result
                            : new PlayerProfileRepository.ShutdownResult(false, pendingSessions,
                            "repository shutdown failed: " + failure.getClass().getSimpleName()))
                    .toCompletableFuture()
                    .completeOnTimeout(timeoutResult, Math.max(1L, remaining), TimeUnit.NANOSECONDS)
                    .thenApply(repositoryResult -> {
                        if (sessionsDrained && repositoryResult.drained()) return repositoryResult;
                        final int pending = Math.max(pendingSessions,
                                repositoryResult.pendingOperations());
                        final String detail = !sessionsDrained
                                ? "session drain deadline exceeded; " + repositoryResult.detail()
                                : repositoryResult.detail();
                        return new PlayerProfileRepository.ShutdownResult(false, pending, detail);
                    });
        });
    }

    private static long remainingNanos(final long startedAt, final long budgetNanos) {
        return Math.max(0L, budgetNanos - Math.max(0L, System.nanoTime() - startedAt));
    }

    private <T> CompletionStage<T> enqueue(final UUID playerId,
                                            final Supplier<CompletionStage<T>> work) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        sessionTails.compute(playerId, (ignored, previous) -> {
            final CompletableFuture<Void> start = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((value, failure) -> null);
            final CompletableFuture<Void> tail = start.thenCompose(nothing -> {
                final CompletionStage<T> stage;
                try { stage = Objects.requireNonNull(work.get(), "session work"); }
                catch (Throwable failure) { result.completeExceptionally(failure); return CompletableFuture.<Void>completedFuture(null); }
                return stage.<Void>handle((value, failure) -> {
                    if (failure == null) result.complete(value); else result.completeExceptionally(failure);
                    return null;
                }).toCompletableFuture();
            });
            tail.whenComplete((value, failure) -> sessionTails.remove(playerId, tail));
            return tail;
        });
        return result;
    }

    private void notifyChanged(final UUID id, final long revision,
                               final Set<ProfileSectionId> changed) {
        for (PlayerProfileListener listener : listeners) {
            try { listener.changed(id, revision, Set.copyOf(changed)); }
            catch (RuntimeException ignored) { }
        }
    }

    private static Set<ProfileSectionId> changedSections(final PlayerProfileSnapshot before,
                                                         final PlayerProfileSnapshot after,
                                                         final Set<ProfileSectionId> candidates) {
        if (before == null) return Set.of();
        final java.util.EnumSet<ProfileSectionId> changed =
                java.util.EnumSet.noneOf(ProfileSectionId.class);
        for (final ProfileSectionId section : candidates) {
            if (before.section(section).orElseThrow().revision()
                    != after.section(section).orElseThrow().revision()) changed.add(section);
        }
        return Set.copyOf(changed);
    }

    private static boolean isStale(final Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current instanceof StaleSessionException;
    }

    public record PlayerProfileSession(long sessionGeneration, long profileRevision) {
        public PlayerProfileSession {
            if (sessionGeneration < 1 || profileRevision < 0) {
                throw new IllegalArgumentException("invalid PlayerProfile session metadata");
            }
        }
    }

    private static final class StaleSessionException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}