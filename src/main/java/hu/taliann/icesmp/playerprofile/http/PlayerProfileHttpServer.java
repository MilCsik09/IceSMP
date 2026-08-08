package hu.taliann.icesmp.playerprofile.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import hu.taliann.icesmp.playerprofile.api.IceSMPPlayerProfileApi;
import hu.taliann.icesmp.playerprofile.api.PublicPlayerProfileDto;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSnapshot;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionSnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Optional read-only v1 HTTP adapter. It never calls Bukkit/entity APIs. */
public final class PlayerProfileHttpServer implements AutoCloseable {
    public enum Scope { PUBLIC, SELF, ADMIN }

    /** SELF grants are bound to one player UUID. ADMIN grants may use a null playerId. */
    public record TokenGrant(Scope scope, UUID playerId) {
        public TokenGrant {
            Objects.requireNonNull(scope, "scope");
            if (scope == Scope.SELF && playerId == null) {
                throw new IllegalArgumentException("SELF token requires a player UUID");
            }
            if (scope == Scope.PUBLIC) {
                throw new IllegalArgumentException("PUBLIC endpoints do not use bearer tokens");
            }
        }

        public static TokenGrant self(final UUID playerId) { return new TokenGrant(Scope.SELF, playerId); }
        public static TokenGrant admin() { return new TokenGrant(Scope.ADMIN, null); }
    }

    public record Config(boolean enabled, String bindAddress, int port,
                         Map<String, TokenGrant> bearerTokens,
                         int requestsPerMinute, int maxRequestBytes,
                         int maxResponseBytes, Duration timeout) {
        public Config {
            bindAddress = bindAddress == null || bindAddress.isBlank()
                    ? "127.0.0.1" : bindAddress.trim();
            if (port < 0 || port > 65_535) throw new IllegalArgumentException("invalid port");
            bearerTokens = Map.copyOf(bearerTokens == null ? Map.of() : bearerTokens);
            if (requestsPerMinute < 1 || maxRequestBytes < 0 || maxResponseBytes < 256) {
                throw new IllegalArgumentException("invalid HTTP limits");
            }
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("invalid timeout");
        }

        public static Config disabled() {
            return new Config(false, "127.0.0.1", 8765, Map.of(), 60, 1024,
                    1_048_576, Duration.ofSeconds(3));
        }
    }

    private final IceSMPPlayerProfileApi api;
    private final Config config;
    private final Map<String, TokenGrant> tokenDigests;
    private final ConcurrentMap<String, Window> rate = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean accepting = new AtomicBoolean();
    private HttpServer server;

    public PlayerProfileHttpServer(final IceSMPPlayerProfileApi api, final Config config) {
        this.api = Objects.requireNonNull(api, "api");
        this.config = Objects.requireNonNull(config, "config");
        final LinkedHashMap<String, TokenGrant> digests = new LinkedHashMap<>();
        config.bearerTokens().forEach((token, grant) -> {
            if (token == null || token.length() < 24) {
                throw new IllegalArgumentException("bearer token must be at least 24 characters");
            }
            if (digests.put(digest(token), Objects.requireNonNull(grant, "grant")) != null) {
                throw new IllegalArgumentException("duplicate bearer token");
            }
        });
        tokenDigests = Map.copyOf(digests);
    }

    public synchronized boolean start() throws IOException {
        if (!config.enabled()) return false;
        if (server != null) return true;
        server = HttpServer.create(new InetSocketAddress(
                InetAddress.getByName(config.bindAddress()), config.port()), 64);
        server.createContext("/api/v1", this::handle);
        server.setExecutor(executor);
        accepting.set(true);
        server.start();
        return true;
    }

    public int boundPort() {
        final HttpServer current = server;
        return current == null ? -1 : current.getAddress().getPort();
    }

    private void handle(final HttpExchange exchange) {
        try {
            if (!accepting.get()) {
                reply(exchange, 503, Map.of("error", "service-unavailable"), null);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                reply(exchange, 405, Map.of("error", "method-not-allowed"), null);
                return;
            }
            final String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
            if (contentLength != null && Long.parseLong(contentLength) > config.maxRequestBytes()) {
                reply(exchange, 413, Map.of("error", "request-too-large"), null);
                return;
            }
            if (!allow(exchange)) {
                reply(exchange, 429, Map.of("error", "rate-limited"), null);
                return;
            }
            route(exchange);
        } catch (Forbidden forbidden) {
            safeReply(exchange, 403, Map.of("error", "forbidden"));
        } catch (TimeoutException timeout) {
            safeReply(exchange, 504, Map.of("error", "query-timeout"));
        } catch (NoSuchElementException | IllegalArgumentException notFound) {
            safeReply(exchange, 404, Map.of("error", "not-found"));
        } catch (Throwable failure) {
            safeReply(exchange, 500, Map.of("error", "internal-error"));
        } finally {
            exchange.close();
        }
    }

    private void route(final HttpExchange exchange) throws Exception {
        final String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/v1/health")) {
            reply(exchange, 200, Map.of("status", "ok", "api-version", "v1"), null);
            return;
        }
        final String[] parts = path.split("/");
        if (parts.length < 5 || !"players".equals(parts[3])) {
            reply(exchange, 404, Map.of("error", "not-found"), null);
            return;
        }
        if ("by-name".equals(parts[4])) {
            final String name = decode(parts.length > 5 ? parts[5] : "");
            // Auth resolves BEFORE any storage read: egy anonim hívó különben kikényszeríthetné
            // az O(N) profilkönyvtár-szkennelést, és a 403-vs-404 különbségből név-létezési
            // orákulumot kapna. SELF token csak a saját, owner-bound profilját olvashatja.
            final TokenGrant grant = authenticate(exchange, Scope.SELF);
            if (grant.scope() == Scope.SELF) {
                final Optional<PlayerProfileSnapshot> own = await(api.find(grant.playerId()));
                if (own.isEmpty() || !own.orElseThrow().identity().value()
                        .lastKnownName().equalsIgnoreCase(name)) {
                    reply(exchange, 404, Map.of("error", "player-not-found"), null);
                    return;
                }
                final PlayerProfileSnapshot profile = own.orElseThrow();
                replyDto(exchange, await(api.selfProfile(profile.playerId())), profile.profileRevision());
                return;
            }
            final Optional<PlayerProfileSnapshot> found = await(api.findByName(name));
            if (found.isEmpty()) {
                reply(exchange, 404, Map.of("error", "player-not-found"), null);
                return;
            }
            final PlayerProfileSnapshot profile = found.orElseThrow();
            replyDto(exchange, await(api.selfProfile(profile.playerId())), profile.profileRevision());
            return;
        }

        final UUID playerId = UUID.fromString(parts[4]);
        if (parts.length == 5) {
            require(exchange, Scope.SELF, playerId);
            final var dto = await(api.selfProfile(playerId));
            replyDto(exchange, dto, dto.publicProfile().profileRevision());
            return;
        }
        switch (parts[5]) {
            case "public" -> {
                final PublicPlayerProfileDto dto = await(api.publicProfile(playerId));
                if (!dto.visible()) throw new Forbidden();
                replyDto(exchange, dto, dto.profileRevision());
            }
            case "sections" -> {
                if (parts.length < 7) {
                    reply(exchange, 404, Map.of("error", "section-not-found"), null);
                    return;
                }
                final ProfileSectionId sectionId = ProfileSectionId.parse(parts[6]);
                final Scope scope = sectionId == ProfileSectionId.MODERATION
                        || sectionId == ProfileSectionId.OPERATIONS ? Scope.ADMIN : Scope.SELF;
                require(exchange, scope, playerId);
                final Optional<ProfileSectionSnapshot<?>> section = await(api.section(playerId, sectionId));
                if (section.isEmpty()) {
                    reply(exchange, 404, Map.of("error", "section-not-found"), null);
                    return;
                }
                final ProfileSectionSnapshot<?> snapshot = section.orElseThrow();
                if (!snapshot.health().usable()) {
                    reply(exchange, 409, Map.of("error", "section-quarantined",
                            "section", sectionId.id(), "evidence-id", snapshot.health().evidenceId()),
                            etag(snapshot.revision()));
                    return;
                }
                replyDto(exchange, Map.of("section", sectionId.id(), "schema", snapshot.schema(),
                        "revision", snapshot.revision(), "updated-at", snapshot.updatedAt(),
                        "data", snapshot.value()), snapshot.revision());
            }
            case "admin" -> {
                require(exchange, Scope.ADMIN, playerId);
                final var dto = await(api.adminProfile(playerId));
                replyDto(exchange, dto, dto.selfProfile().publicProfile().profileRevision());
            }
            default -> reply(exchange, 404, Map.of("error", "not-found"), null);
        }
    }

    private void replyDto(final HttpExchange exchange, final Object dto, final long revision)
            throws IOException {
        final String tag = etag(revision);
        if (tag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            exchange.getResponseHeaders().set("ETag", tag);
            exchange.sendResponseHeaders(304, -1);
            return;
        }
        reply(exchange, 200, dto, tag);
    }

    private void reply(final HttpExchange exchange, int status, final Object body, final String etag)
            throws IOException {
        byte[] bytes = PlayerProfileJson.encode(body).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > config.maxResponseBytes()) {
            status = 500;
            bytes = PlayerProfileJson.encode(Map.of("error", "response-too-large"))
                    .getBytes(StandardCharsets.UTF_8);
        }
        final Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        if (etag != null) headers.set("ETag", etag);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void safeReply(final HttpExchange exchange, final int status, final Object body) {
        try { reply(exchange, status, body, null); }
        catch (IOException ignored) { }
    }

    private <T> T await(final CompletionStage<T> stage) throws Exception {
        try {
            return stage.toCompletableFuture().get(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException failure) {
            final Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            throw failure;
        }
    }

    private boolean allow(final HttpExchange exchange) {
        final String key = exchange.getRemoteAddress().getAddress().getHostAddress();
        final long minute = System.currentTimeMillis() / 60_000L;
        final Window window = rate.compute(key, (ignored, old) ->
                old == null || old.minute() != minute
                        ? new Window(minute, new AtomicInteger()) : old);
        return window.count().incrementAndGet() <= config.requestsPerMinute();
    }

    private TokenGrant require(final HttpExchange exchange, final Scope required, final UUID target) {
        final TokenGrant grant = authenticate(exchange, required);
        if (grant.scope() == Scope.SELF && !grant.playerId().equals(target)) throw new Forbidden();
        return grant;
    }

    private TokenGrant authenticate(final HttpExchange exchange, final Scope required) {
        final String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new Forbidden();
        final TokenGrant grant = tokenDigests.get(digest(authorization.substring(7)));
        if (grant == null || grant.scope().ordinal() < required.ordinal()) throw new Forbidden();
        return grant;
    }

    private static String digest(final String token) {
        try {
            final byte[] value = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String decode(final String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String etag(final long revision) { return "\"pp-" + revision + "\""; }

    public synchronized CompletionStage<Void> stop(final Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        accepting.set(false);
        final HttpServer current = server;
        if (current != null) {
            current.stop((int) Math.max(0L, timeout.toSeconds()));
            server = null;
        }
        executor.shutdown();
        return CompletableFuture.runAsync(() -> {
            try {
                if (!executor.awaitTermination(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        });
    }

    @Override public void close() { stop(Duration.ofSeconds(3)); }

    private static final class Forbidden extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private record Window(long minute, AtomicInteger count) { }
}
