package hu.taliann.icesmp.playerprofile.http;

import hu.taliann.icesmp.playerprofile.api.AdminPlayerProfileDto;
import hu.taliann.icesmp.playerprofile.api.IceSMPPlayerProfileApi;
import hu.taliann.icesmp.playerprofile.api.PublicPlayerProfileDto;
import hu.taliann.icesmp.playerprofile.api.SelfPlayerProfileDto;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSnapshot;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionData;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionSnapshot;
import hu.taliann.icesmp.playerprofile.domain.SectionHealth;
import hu.taliann.icesmp.playerprofile.domain.section.IdentitySection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Valódi HTTP routingon bizonyítja a by-name szerződést: az auth minden storage-olvasás
 * ELŐTT dől el (nincs anonim O(N) név-szkennelés és nincs 403-vs-404 létezési orákulum),
 * SELF token csak a saját profilját oldhatja fel, ADMIN token kereshet globálisan; a stop
 * idempotens és tényleg elengedi a listenert.
 */
public final class PlayerProfileHttpContractRegressionSuite {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");
    private static final String SELF_TOKEN = "self-bearer-token-0123456789abcdef";
    private static final String STRANGER_TOKEN = "stranger-bearer-token-0123456789ab";
    private static final String ADMIN_TOKEN = "admin-bearer-token-0123456789abcdef";
    private static int assertions;

    private PlayerProfileHttpContractRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        disabledAdapterCreatesNoListener();
        authPrecedesAnyStorageRead();
        selfTokenResolvesOwnNameOnly();
        adminTokenMayResolveAnyName();
        stopIsIdempotentAndClosesListener();
        System.out.println("PlayerProfile HTTP contract regression suite passed. assertions=" + assertions);
    }

    private static void disabledAdapterCreatesNoListener() throws Exception {
        final CountingApi api = new CountingApi();
        final PlayerProfileHttpServer server =
                new PlayerProfileHttpServer(api, PlayerProfileHttpServer.Config.disabled());
        check(!server.start(), "disabled config must not start a listener");
        check(server.boundPort() == -1, "disabled config must not bind a port");
        server.close();
        server.close();
        check(true, "disabled adapter close is idempotent");
    }

    private static void authPrecedesAnyStorageRead() throws Exception {
        final CountingApi api = new CountingApi();
        final PlayerProfileHttpServer server = new PlayerProfileHttpServer(api, config());
        try {
            check(server.start(), "enabled config starts");
            final int port = server.boundPort();
            check(get(port, "/api/v1/health", null).statusCode() == 200, "health is public");

            final HttpResponse<String> anonymous = get(port, "/api/v1/players/by-name/PlayerOne", null);
            check(anonymous.statusCode() == 403, "anonymous by-name is rejected");
            check(api.findByNameCalls.get() == 0, "anonymous caller must not trigger the name scan");
            check(api.findCalls.get() == 0, "anonymous caller must not trigger any profile read");

            final HttpResponse<String> anonymousUnknown = get(port, "/api/v1/players/by-name/NoSuchPlayer", null);
            check(anonymousUnknown.statusCode() == 403,
                    "unknown name answers identically without auth (no existence oracle)");
            check(api.findByNameCalls.get() == 0 && api.findCalls.get() == 0,
                    "unknown-name probe must not reach storage either");
        } finally {
            server.stop(Duration.ofSeconds(2)).toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private static void selfTokenResolvesOwnNameOnly() throws Exception {
        final CountingApi api = new CountingApi();
        final PlayerProfileHttpServer server = new PlayerProfileHttpServer(api, config());
        try {
            check(server.start(), "enabled config starts");
            final int port = server.boundPort();

            final HttpResponse<String> own = get(port, "/api/v1/players/by-name/playerone", SELF_TOKEN);
            check(own.statusCode() == 200, "SELF token resolves its own name case-insensitively");
            check(own.body().contains("PlayerOne"), "self DTO carries the profile");
            check(api.findByNameCalls.get() == 0, "SELF resolution must not run the global scan");

            final HttpResponse<String> other = get(port, "/api/v1/players/by-name/SomeoneElse", SELF_TOKEN);
            check(other.statusCode() == 404, "SELF token gets 404 for any other name");
            check(api.findByNameCalls.get() == 0, "mismatching SELF lookup must not fall back to the scan");

            final HttpResponse<String> stranger = get(port, "/api/v1/players/by-name/PlayerOne", STRANGER_TOKEN);
            check(stranger.statusCode() == 404,
                    "a foreign SELF token cannot resolve another player's name");
            check(api.findByNameCalls.get() == 0, "foreign SELF lookup must not run the global scan");
        } finally {
            server.stop(Duration.ofSeconds(2)).toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private static void adminTokenMayResolveAnyName() throws Exception {
        final CountingApi api = new CountingApi();
        final PlayerProfileHttpServer server = new PlayerProfileHttpServer(api, config());
        try {
            check(server.start(), "enabled config starts");
            final int port = server.boundPort();

            final HttpResponse<String> found = get(port, "/api/v1/players/by-name/PlayerOne", ADMIN_TOKEN);
            check(found.statusCode() == 200, "ADMIN token resolves any known name");
            check(api.findByNameCalls.get() == 1, "ADMIN resolution uses the authenticated scan");

            final HttpResponse<String> missing = get(port, "/api/v1/players/by-name/NoSuchPlayer", ADMIN_TOKEN);
            check(missing.statusCode() == 404, "ADMIN token gets 404 for unknown names");
        } finally {
            server.stop(Duration.ofSeconds(2)).toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private static void stopIsIdempotentAndClosesListener() throws Exception {
        final CountingApi api = new CountingApi();
        final PlayerProfileHttpServer server = new PlayerProfileHttpServer(api, config());
        check(server.start(), "enabled config starts");
        final int port = server.boundPort();
        check(get(port, "/api/v1/health", null).statusCode() == 200, "listener answers before stop");
        server.stop(Duration.ofSeconds(2)).toCompletableFuture().get(5, TimeUnit.SECONDS);
        server.stop(Duration.ofSeconds(2)).toCompletableFuture().get(5, TimeUnit.SECONDS);
        boolean refused = false;
        try {
            get(port, "/api/v1/health", null);
        } catch (final Exception expected) {
            refused = true;
        }
        check(refused, "stopped listener refuses new connections");
    }

    private static PlayerProfileHttpServer.Config config() {
        return new PlayerProfileHttpServer.Config(true, "127.0.0.1", 0,
                Map.of(SELF_TOKEN, PlayerProfileHttpServer.TokenGrant.self(PLAYER),
                        STRANGER_TOKEN, PlayerProfileHttpServer.TokenGrant.self(STRANGER),
                        ADMIN_TOKEN, PlayerProfileHttpServer.TokenGrant.admin()),
                10_000, 1024, 1_048_576, Duration.ofSeconds(3));
    }

    private static HttpResponse<String> get(final int port, final String path, final String bearer)
            throws Exception {
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build();
        final HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET();
        if (bearer != null) {
            request.header("Authorization", "Bearer " + bearer);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static PlayerProfileSnapshot profile() {
        return replace(PlayerProfileSnapshot.greenfield(PLAYER, NOW), new IdentitySection(
                "PlayerOne", NOW.toEpochMilli(), NOW.toEpochMilli(), NOW.toEpochMilli(),
                true, true, Map.of(), Map.of()), SectionHealth.healthy());
    }

    private static <T extends ProfileSectionData> PlayerProfileSnapshot replace(
            final PlayerProfileSnapshot profile, final T value, final SectionHealth health) {
        final ProfileSectionId sectionId = value.sectionId();
        final long nextSectionRevision = profile.sectionRevisions().get(sectionId).revision() + 1L;
        final Instant updated = NOW.plusSeconds(1L);
        return profile.withSection(
                new ProfileSectionSnapshot<>(sectionId, sectionId.currentSchema(),
                        nextSectionRevision, updated, value, health),
                profile.profileRevision() + 1L, updated);
    }

    private static void check(final boolean condition, final String label) {
        if (!condition) {
            throw new IllegalStateException("HTTP contract regression failed: " + label);
        }
        assertions++;
    }

    private static final class CountingApi implements IceSMPPlayerProfileApi {
        final AtomicInteger findCalls = new AtomicInteger();
        final AtomicInteger findByNameCalls = new AtomicInteger();
        private final PlayerProfileSnapshot profile = profile();

        @Override
        public CompletionStage<Optional<PlayerProfileSnapshot>> find(final UUID playerId) {
            findCalls.incrementAndGet();
            return CompletableFuture.completedFuture(
                    PLAYER.equals(playerId) ? Optional.of(profile) : Optional.empty());
        }

        @Override
        public CompletionStage<Optional<PlayerProfileSnapshot>> findByName(final String playerName) {
            findByNameCalls.incrementAndGet();
            return CompletableFuture.completedFuture(
                    "PlayerOne".equalsIgnoreCase(playerName) ? Optional.of(profile) : Optional.empty());
        }

        @Override
        public CompletionStage<Optional<ProfileSectionSnapshot<?>>> section(
                final UUID playerId, final ProfileSectionId section) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletionStage<PublicPlayerProfileDto> publicProfile(final UUID playerId) {
            return CompletableFuture.failedFuture(new NoSuchElementException("unused"));
        }

        @Override
        public CompletionStage<SelfPlayerProfileDto> selfProfile(final UUID playerId) {
            if (!PLAYER.equals(playerId)) {
                return CompletableFuture.failedFuture(new NoSuchElementException("unknown player"));
            }
            final PublicPlayerProfileDto publicDto = new PublicPlayerProfileDto(PLAYER, true,
                    "PlayerOne", "", "", "", Set.of(), Map.of(), "",
                    profile.profileRevision(), Map.of(), NOW);
            return CompletableFuture.completedFuture(new SelfPlayerProfileDto(publicDto,
                    Map.of(), 0L, Map.of(), Set.of(), Map.of(), "", List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Set.of()));
        }

        @Override
        public CompletionStage<AdminPlayerProfileDto> adminProfile(final UUID playerId) {
            return CompletableFuture.failedFuture(new NoSuchElementException("unused"));
        }

        @Override
        public AutoCloseable subscribe(final PlayerProfileListener listener) {
            return () -> { };
        }
    }
}
