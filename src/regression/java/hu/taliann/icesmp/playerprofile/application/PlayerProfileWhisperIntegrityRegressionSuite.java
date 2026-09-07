package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Real profile/WAL behavioral tests: replays, expiry, restart and the legal transition. */
public final class PlayerProfileWhisperIntegrityRegressionSuite {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        final Path root = Files.createTempDirectory("whisper-integrity-");
        final var repository = new YamlPlayerProfileRepository(root);
        final var transactions = new YamlPlayerProfileTransactionManager(repository);
        final var service = new PlayerProfileService(repository, transactions);
        final var authority = PlayerProfileAuthority.install(service, repository, transactions);
        try {
            final var clock = new AtomicLong(10_000L);
            final var whispers = new PlayerProfileWhisperStore(clock::get);
            final var factions = new PlayerProfileFactionStore();
            final var sins = new PlayerProfileSinStore();
            final UUID suspect = UUID.randomUUID(), witness = UUID.randomUUID(), other = UUID.randomUUID();
            repository.loadSnapshot(suspect).toCompletableFuture().join();
            repository.loadSnapshot(other).toCompletableFuture().join();
            check(!whispers.canEnter(suspect), "guests cannot perform the rite");
            factions.assign(suspect, FactionType.RED).toCompletableFuture().join();
            check(whispers.canEnter(suspect), "civil entry allowed");
            check(whispers.interruptCandidate(suspect).toCompletableFuture().join(), "witnessed candidate persists one-minute retry");
            check(!whispers.canEnter(suspect) && whispers.returnRemainingMillis(suspect) == 60_000L, "candidate cannot immediately spam a new rite");
            check(!whispers.grantEvidence(witness, suspect, incident(clock.get()), 5_000L).toCompletableFuture().join(), "candidate has no fabricated active-role evidence");
            check(!sins.read(suspect).exiled() && !sins.read(suspect).darkPact(), "candidate interruption is not a crime or oath");
            clock.addAndGet(60_000L);
            check(whispers.canEnter(suspect), "candidate retry boundary opens");
            final var rite = new PlayerProfileWhisperStore.Rite(UUID.randomUUID(), List.of("invite"), List.of("-"), 20, 14);
            check(whispers.prepareRite(suspect, rite).toCompletableFuture().join(), "intent saved before resources");
            check(!whispers.canEnter(suspect) && !whispers.read(suspect).whisperer(), "prepared rite grants no role and blocks duplicate");
            check(whispers.withdraw(suspect).toCompletableFuture().join() == PlayerProfileWhisperStore.Withdrawal.RITE_PENDING, "withdrawal cannot erase a prepared receipt");
            repository.invalidate(suspect); repository.loadSnapshot(suspect).toCompletableFuture().join();
            check(whispers.pendingRite(suspect).orElseThrow().equals(rite), "resource intent survives restart");
            check(whispers.finishRite(suspect, rite.operation(), false).toCompletableFuture().join(), "unpaid rite aborts");
            check(!whispers.read(suspect).whisperer(), "abort grants nothing");
            check(whispers.prepareRite(suspect, rite).toCompletableFuture().join(), "retry prepares");
            check(whispers.finishRite(suspect, rite.operation(), true).toCompletableFuture().join(), "paid receipt grants role");
            check(!whispers.finishRite(suspect, rite.operation(), true).toCompletableFuture().join(), "ritual callback replay is a no-op");
            final String alias = whispers.channelAlias(suspect).toCompletableFuture().join();
            check(!alias.contains(suspect.toString()) && alias.equals(whispers.channelAlias(suspect).toCompletableFuture().join()), "random alias is stable");
            check(whispers.grantEvidence(witness, suspect, incident(clock.get()), 5_000L).toCompletableFuture().join(), "exact evidence granted");
            check(!whispers.grantEvidence(witness, suspect, incident(clock.get()), 5_000L).toCompletableFuture().join(), "repeated sightings cannot refresh same evidence");
            check(whispers.withdraw(suspect).toCompletableFuture().join() == PlayerProfileWhisperStore.Withdrawal.UNRESOLVED, "clean stage cannot hide a fresh witness through withdrawal");
            check(!whispers.hasEvidence(witness, other), "wrong target has no evidence");
            repository.invalidate(suspect); repository.loadSnapshot(suspect).toCompletableFuture().join();
            check(whispers.hasEvidence(witness, suspect), "evidence survives restart");
            final var duplicate = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(i -> whispers.accuse(witness, suspect).toCompletableFuture()).toList();
            long accepted = 0;
            for (var future : duplicate) if (future.join().accepted()) accepted++;
            check(accepted == 1 && whispers.read(suspect).stage() == PlayerProfileWhisperStore.Stage.OBSERVED,
                    "concurrent replay consumes and advances exactly once");
            check(!whispers.grantEvidence(witness, suspect, incident(clock.get()), 5_000L).toCompletableFuture().join(), "consumed sighting cannot be farmed immediately");
            check(whispers.withdraw(suspect).toCompletableFuture().join() == PlayerProfileWhisperStore.Withdrawal.UNRESOLVED, "accusation pressure blocks withdrawal");
            final var stored = authority.requireSection(suspect, hu.taliann.icesmp.playerprofile.domain.ProfileSectionId.FACTION,
                    hu.taliann.icesmp.playerprofile.domain.section.FactionSection.class);
            final var evidence = (Map<?, ?>) ((Map<?, ?>) stored.extensions().get("whisper.evidence")).get(witness.toString());
            check("OFFERING".equals(evidence.get("event-type")) && evidence.containsKey("event-id")
                    && ((Number)evidence.get("observed-at")).longValue() == 70_000L && Boolean.TRUE.equals(evidence.get("consumed")),
                    "consuming evidence retains the exact event provenance");
            clock.addAndGet(5_000L);
            check(!whispers.grantEvidence(witness, suspect, incident(70_000L), 5_000L).toCompletableFuture().join(), "expired incident replay cannot restart TTL");
            check(!whispers.grantEvidence(witness, suspect, incident(clock.get() + 1), 5_000L).toCompletableFuture().join(), "future observation rejected");
            check(!whispers.hasEvidence(witness, suspect), "expiry boundary is exclusive");
            check(whispers.grantEvidence(witness, suspect, incident(clock.get()), 5_000L).toCompletableFuture().join(), "new incident after expiry accepted");
            whispers.accuse(witness, suspect).toCompletableFuture().join();
            check(whispers.applyCover(suspect).toCompletableFuture().join().state().stage() == PlayerProfileWhisperStore.Stage.OBSERVED,
                    "cover removes one stage");
            whispers.grantEvidence(other, suspect, incident(clock.get()), 5_000L).toCompletableFuture().join();
            whispers.accuse(other, suspect).toCompletableFuture().join();
            final UUID lastWitness = UUID.randomUUID();
            whispers.grantEvidence(lastWitness, suspect, incident(clock.get()), 5_000L).toCompletableFuture().join();
            final var exposed = whispers.accuse(lastWitness, suspect).toCompletableFuture().join();
            check(exposed.exposed() && !exposed.state().whisperer(), "third accumulated accusation exposes");
            repository.invalidate(suspect); repository.loadSnapshot(suspect).toCompletableFuture().join();
            check(sins.read(suspect).exiled() && !sins.read(suspect).darkPact() && sins.read(suspect).count() == 0,
                    "same commit stores exile without infamy or oath");
            check(factions.readCached(suspect).membership().orElseThrow() == FactionType.RED, "exposure does not silently join DARK");
            check(whispers.returnRemainingMillis(suspect) == PlayerProfileWhisperStore.RETURN_COOLDOWN_MILLIS,
                    "cooldown survives restart");
            check(!whispers.applyCover(suspect).toCompletableFuture().join().applied(), "cover cannot undo exposure");
            sins.breakDarkPact(suspect).toCompletableFuture().join();
            check(!whispers.canEnter(suspect), "penance does not bypass return cooldown");
            clock.addAndGet(PlayerProfileWhisperStore.RETURN_COOLDOWN_MILLIS);
            check(whispers.canEnter(suspect), "civil player may return after fixed cooldown");
            whispers.makeWhisperer(suspect).toCompletableFuture().join();
            sins.add(suspect, 1, 3, 4).toCompletableFuture().join();
            final var beforeWithdrawal = sins.read(suspect);
            final var withdrawals = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(i -> whispers.withdraw(suspect).toCompletableFuture()).toList();
            check(withdrawals.stream().filter(f -> f.join() == PlayerProfileWhisperStore.Withdrawal.LEFT).count() == 1,
                    "concurrent withdrawal commits exactly once");
            check(sins.read(suspect).equals(beforeWithdrawal) && !whispers.read(suspect).whisperer(), "voluntary leave preserves all legal history");
            repository.invalidate(suspect); repository.loadSnapshot(suspect).toCompletableFuture().join();
            check(!whispers.canEnter(suspect) && whispers.returnRemainingMillis(suspect) == PlayerProfileWhisperStore.RETURN_COOLDOWN_MILLIS,
                    "withdrawal and its full cooldown survive restart");
            sins.breakDarkPact(suspect).toCompletableFuture().join();
            final long generation = sins.read(suspect).generation();
            sins.markSinner(suspect).toCompletableFuture().join();
            check(sins.read(suspect).generation() == generation + 1, "new crime generation after zero count");
            sins.markSinner(suspect).toCompletableFuture().join();
            check(sins.read(suspect).generation() == generation + 1, "idempotent crime marker preserves generation");
            final var participation = new PlayerProfileSeasonParticipationStore();
            check(participation.record(suspect, FactionType.RED, 1, "community").toCompletableFuture().join(), "real activity counted");
            check(!participation.record(suspect, FactionType.RED, 1, "community").toCompletableFuture().join(), "same-day category farming blocked");
            check(participation.contributions(suspect, FactionType.RED, 1) == 1, "personal contribution persisted");
            factions.assign(suspect, FactionType.BLUE).toCompletableFuture().join();
            check(participation.contributions(suspect, FactionType.BLUE, 1) == 0, "faction hopping cannot carry rewards");
            check(participation.contributions(suspect, FactionType.RED, 2) == 0, "new season cannot reuse old participation");
            check(service.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(), "WAL drained");
        } finally {
            authority.uninstall();
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("Whisper integrity regression suite passed. assertions=" + assertions);
    }
    private static PlayerProfileWhisperStore.Incident incident(long at) {
        return new PlayerProfileWhisperStore.Incident(UUID.randomUUID(), PlayerProfileWhisperStore.EvidenceType.OFFERING, at);
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
