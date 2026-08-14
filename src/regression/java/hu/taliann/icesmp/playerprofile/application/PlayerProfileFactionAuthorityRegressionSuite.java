package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.playerprofile.persistence.YamlPlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.YamlPlayerProfileTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

/** Cross-section faction switch and faction-owned state regressions. */
public final class PlayerProfileFactionAuthorityRegressionSuite {
    private static int assertions;

    private PlayerProfileFactionAuthorityRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("player-profile-faction-");
        final YamlPlayerProfileRepository repository = new YamlPlayerProfileRepository(root);
        final YamlPlayerProfileTransactionManager transactions =
                new YamlPlayerProfileTransactionManager(repository);
        final PlayerProfileService service = new PlayerProfileService(repository, transactions);
        final PlayerProfileAuthority authority = PlayerProfileAuthority.install(
                service, repository, transactions);
        try {
            final UUID player = UUID.fromString("00000000-0000-0000-0000-000000001091");
            repository.loadSnapshot(player).toCompletableFuture().join();
            final PlayerProfileEconomyStore economy = new PlayerProfileEconomyStore();
            final PlayerProfileFactionStore factions = new PlayerProfileFactionStore();
            final PlayerProfileSinStore sins = new PlayerProfileSinStore();
            final PlayerProfileWhisperStore whispers = new PlayerProfileWhisperStore();

            economy.mutate(player, before -> PlayerProfileEconomyStore.Decision.changed(
                    before.add(CurrencyType.NEUTRAL, 100_000L), true))
                    .toCompletableFuture().join();
            check(factions.switchDurably(player, null, FactionType.RED,
                    CurrencyType.NEUTRAL, 25.0D, 12345L)
                    .toCompletableFuture().join(), "paid switch committed");
            check(factions.readCached(player).membership().orElseThrow() == FactionType.RED,
                    "membership committed");
            check(factions.readCached(player).lastChosen().orElseThrow() == FactionType.RED,
                    "history committed");
            check(factions.readCached(player).lastPaidSwitchAt() > 0L,
                    "paid timestamp committed");
            check(factions.readCached(player).switchesThisSeason() == 1,
                    "season switch count committed");
            check(economy.readCached(player).milli(CurrencyType.NEUTRAL) == 75_000L,
                    "wallet deducted atomically");
            check(!factions.switchDurably(player, null, FactionType.BLUE,
                    CurrencyType.NEUTRAL, 1.0D, 12345L)
                    .toCompletableFuture().join(), "stale expected membership rejected");

            whispers.makeWhisperer(player).toCompletableFuture().join();
            check(whispers.read(player).whisperer(), "whisperer flag committed");
            check(Math.abs(whispers.adjust(player, 19.5D, 20.0D)
                    .toCompletableFuture().join().state().suspicion() - 19.5D) < 0.0001D,
                    "suspicion uses milli-units");
            final var exposed = whispers.adjust(player, 0.5D, 20.0D)
                    .toCompletableFuture().join();
            check(exposed.exposed() && !exposed.state().whisperer(),
                    "threshold atomically exposes");
            check(exposed.state().suspicionMilli() == 0L,
                    "exposure clears suspicion");

            final var firstSin = sins.add(player, 2, 4).toCompletableFuture().join();
            check(firstSin.state().count() == 2 && firstSin.state().sinner(),
                    "sin count and mark committed");
            final var exile = sins.add(player, 2, 4).toCompletableFuture().join();
            check(exile.exiled(), "threshold exile reported");
            check(exile.state().membership().orElseThrow() == FactionType.DARK,
                    "threshold exile changes membership in same CAS");
            check(exile.state().darkPact(), "threshold exile seals pact");
            check(!sins.clearSinner(player).toCompletableFuture().join(),
                    "pact blocks ordinary cleanse");
            sins.breakDarkPact(player).toCompletableFuture().join();
            check(!sins.read(player).sinner() && !sins.read(player).darkPact()
                    && sins.read(player).count() == 0, "penance clears all sin state");

            repository.invalidate(player);
            repository.loadSnapshot(player).toCompletableFuture().join();
            check(factions.readCached(player).membership().orElseThrow() == FactionType.DARK,
                    "membership restart durable");
            check(economy.readCached(player).milli(CurrencyType.NEUTRAL) == 75_000L,
                    "wallet restart durable");
            check(!whispers.read(player).whisperer(), "exposure restart durable");
            check(!sins.read(player).darkPact(), "penance restart durable");

            check(service.shutdown(Duration.ofSeconds(5)).toCompletableFuture().join().drained(),
                    "repository drained");
        } finally {
            authority.uninstall();
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (final Exception ignored) { }
                });
            }
        }
        System.out.println("PlayerProfile faction authority regression suite passed. assertions="
                + assertions);
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
