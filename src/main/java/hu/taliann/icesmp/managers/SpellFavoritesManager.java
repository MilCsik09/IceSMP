package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.SpellbookSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * PlayerProfile-backed spell favourites.
 *
 * <p>The manager owns no durable state. Reads use the session-fenced cached spellbook
 * section and mutations become visible only after the section CAS commits.</p>
 */
public final class SpellFavoritesManager {

    private final JavaPlugin plugin;

    public SpellFavoritesManager(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public Set<String> favorites(final Player player) {
        Objects.requireNonNull(player, "player");
        return Set.copyOf(new LinkedHashSet<>(PlayerProfileAuthority.current()
                .requireSection(player.getUniqueId(), ProfileSectionId.SPELLBOOK, SpellbookSection.class)
                .favorites()));
    }

    public boolean isFavorite(final Player player, final String spellId) {
        final String normalized = normalize(spellId);
        return !normalized.isEmpty() && favorites(player).contains(normalized);
    }

    public CompletionStage<Boolean> toggle(final Player player, final String spellId) {
        Objects.requireNonNull(player, "player");
        final String normalized = normalize(spellId);
        if (normalized.isEmpty()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalArgumentException("spellId cannot be blank"));
        }
        return PlayerProfileAuthority.current().mutateSection(
                        player.getUniqueId(),
                        ProfileSectionId.SPELLBOOK,
                        SpellbookSection.class,
                        current -> {
                            final LinkedHashSet<String> favorites = new LinkedHashSet<>(current.favorites());
                            if (!favorites.remove(normalized)) {
                                favorites.add(normalized);
                            }
                            return new SpellbookSection(
                                    current.provenance(),
                                    current.selectedSpell(),
                                    List.copyOf(favorites),
                                    current.mastery(),
                                    current.persistentCooldowns(),
                                    current.uiState(),
                                    current.extensions());
                        })
                .thenApply(snapshot -> snapshot.spellbook().value().favorites().contains(normalized));
    }

    public void runOnOwnerThread(final Player player, final Runnable action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        player.getScheduler().run(plugin, ignored -> action.run(), null);
    }

    private static String normalize(final String spellId) {
        return spellId == null ? "" : spellId.trim().toLowerCase(Locale.ROOT);
    }
}
