package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSnapshot;
import hu.taliann.icesmp.playerprofile.domain.PlayerProfileSection;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.persistence.PlayerProfileRepository;
import hu.taliann.icesmp.playerprofile.transaction.PlayerProfileTransactionManager;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Process-wide integration port for gameplay managers that are constructed before the
 * PlayerProfile lifecycle starts. The singleton owns no player data: it only delegates to
 * the canonical repository/service/transaction boundary installed by {@code IceSMPCore}.
 *
 * <p>Cached reads fail closed when the login pipeline has not produced a usable profile.
 * Durable writes remain asynchronous and become visible through the repository cache only
 * after the section CAS or cross-section WAL transaction commits.</p>
 */
public final class PlayerProfileAuthority {

    private static final AtomicReference<PlayerProfileAuthority> CURRENT = new AtomicReference<>();

    private final PlayerProfileService service;
    private final PlayerProfileRepository repository;
    private final PlayerProfileTransactionManager transactions;

    public PlayerProfileAuthority(final PlayerProfileService service,
                                  final PlayerProfileRepository repository,
                                  final PlayerProfileTransactionManager transactions) {
        this.service = Objects.requireNonNull(service, "service");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public static PlayerProfileAuthority install(final PlayerProfileService service,
                                                 final PlayerProfileRepository repository,
                                                 final PlayerProfileTransactionManager transactions) {
        final PlayerProfileAuthority authority = new PlayerProfileAuthority(service, repository, transactions);
        if (!CURRENT.compareAndSet(null, authority)) {
            throw new IllegalStateException("PlayerProfileAuthority is already installed");
        }
        return authority;
    }

    public static PlayerProfileAuthority current() {
        final PlayerProfileAuthority authority = CURRENT.get();
        if (authority == null) {
            throw new IllegalStateException("PlayerProfile authority is not installed");
        }
        return authority;
    }

    public static Optional<PlayerProfileAuthority> installed() {
        return Optional.ofNullable(CURRENT.get());
    }

    public void uninstall() {
        if (!CURRENT.compareAndSet(this, null)) {
            throw new IllegalStateException("PlayerProfileAuthority installation changed unexpectedly");
        }
    }

    public PlayerProfileSnapshot requireCached(final UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return repository.cached(playerId).orElseThrow(() ->
                new ProfileNotReadyException(playerId, "PlayerProfile is not loaded for the active session"));
    }

    public <T extends PlayerProfileSection> T requireSection(final UUID playerId,
                                                             final ProfileSectionId sectionId,
                                                             final Class<T> type) {
        Objects.requireNonNull(sectionId, "sectionId");
        Objects.requireNonNull(type, "type");
        final var section = requireCached(playerId).section(sectionId).orElseThrow(() ->
                new ProfileNotReadyException(playerId, "PlayerProfile section is absent: " + sectionId.id()));
        if (!section.health().usable()) {
            throw new ProfileNotReadyException(playerId,
                    "PlayerProfile section is unavailable: " + sectionId.id() + ": "
                            + section.health().diagnostic());
        }
        return type.cast(section.value());
    }

    public <T extends PlayerProfileSection> CompletionStage<PlayerProfileSnapshot> mutateSection(
            final UUID playerId,
            final ProfileSectionId sectionId,
            final Class<T> type,
            final UnaryOperator<T> mutation) {
        return service.mutateSection(playerId, sectionId, type, mutation);
    }

    public <T> CompletionStage<T> transact(
            final UUID playerId,
            final PlayerProfileTransactionManager.ProfileTransactionWork<T> work) {
        return service.transact(playerId, work);
    }

    public PlayerProfileRepository repository() {
        return repository;
    }

    public PlayerProfileTransactionManager transactions() {
        return transactions;
    }

    public static final class ProfileNotReadyException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final UUID playerId;

        public ProfileNotReadyException(final UUID playerId, final String message) {
            super(message);
            this.playerId = Objects.requireNonNull(playerId, "playerId");
        }

        public UUID playerId() {
            return playerId;
        }
    }
}
