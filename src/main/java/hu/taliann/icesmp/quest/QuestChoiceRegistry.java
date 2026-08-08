package hu.taliann.icesmp.quest;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kattintható quest-választások rövid életű, egyszer használatos tokenjei. A chat-beli
 * választó-gomb NEM hordozhat nyers `/quest accept <id>` parancsot (az remote-accept
 * bypass lenne): a token csak valódi forrás-eseménykor (NPC-interakció, dialógus)
 * kerül kiosztásra, a beváltás pedig visszaadja az EREDETI forrás-kontextust — így a
 * kattintás a megtörtént interakció authorityját viszi tovább, nem újat kreál.
 */
public final class QuestChoiceRegistry {

    public record Choice(UUID playerId, String questId, QuestSourceContext source,
                         long expiresAtMillis) {
    }

    private static final long DEFAULT_TTL_MILLIS = 60_000L;
    private static final int MAX_PENDING = 1024;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Choice> pending = new ConcurrentHashMap<>();

    public String issue(final UUID playerId, final String questId,
                        final QuestSourceContext source, final long nowMillis) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(source, "source");
        if (pending.size() >= MAX_PENDING) {
            pending.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= nowMillis);
        }
        final String token = Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong());
        pending.put(token, new Choice(playerId, questId, source,
                nowMillis + DEFAULT_TTL_MILLIS));
        return token;
    }

    /**
     * Egyszer használatos: sikeres beváltás után a token azonnal érvénytelen. A törlés
     * feltételes (remove(token, choice)): idegen játékos próbálkozása nem égetheti el a
     * jogos tulajdonos érvényes tokenjét, két konkurens jogos beváltásból pedig pontosan
     * egy nyerhet.
     */
    public Optional<Choice> consume(final UUID playerId, final String token,
                                    final long nowMillis) {
        if (playerId == null || token == null || token.isBlank()) {
            return Optional.empty();
        }
        final Choice choice = pending.get(token);
        if (choice == null || choice.expiresAtMillis() <= nowMillis) {
            if (choice != null) {
                pending.remove(token, choice);
            }
            return Optional.empty();
        }
        if (!choice.playerId().equals(playerId)) {
            return Optional.empty();
        }
        return pending.remove(token, choice) ? Optional.of(choice) : Optional.empty();
    }

    public void invalidate(final UUID playerId) {
        if (playerId != null) {
            pending.entrySet().removeIf(entry -> entry.getValue().playerId().equals(playerId));
        }
    }
}
