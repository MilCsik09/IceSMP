package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.playerprofile.application.PlayerProfileAuthority;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileService;
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

    public enum ToggleResult { ADDED, REMOVED, LIMIT_REACHED }

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
        return toggleCapped(player, spellId, Integer.MAX_VALUE)
                .thenApply(result -> result == ToggleResult.ADDED);
    }

    /**
     * Atomically toggles a favorite and enforces the maximum inside the same PlayerProfile CAS.
     * This closes the read-before-async-toggle race where rapid GUI clicks could exceed the cap.
     */
    public CompletionStage<ToggleResult> toggleCapped(final Player player,
                                                       final String spellId,
                                                       final int maximum) {
        Objects.requireNonNull(player, "player");
        final String normalized = normalize(spellId);
        if (normalized.isEmpty()) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalArgumentException("spellId cannot be blank"));
        }
        if (maximum < 1) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalArgumentException("maximum favorites must be positive"));
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                player.getUniqueId(),
                ProfileSectionId.SPELLBOOK,
                SpellbookSection.class,
                current -> {
                    final LinkedHashSet<String> favorites = new LinkedHashSet<>(current.favorites());
                    final ToggleResult result;
                    if (favorites.remove(normalized)) {
                        result = ToggleResult.REMOVED;
                    } else {
                        if (favorites.size() >= maximum) {
                            return PlayerProfileService.ConditionalMutation.unchanged(
                                    ToggleResult.LIMIT_REACHED);
                        }
                        favorites.add(normalized);
                        result = ToggleResult.ADDED;
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            new SpellbookSection(
                                    current.provenance(),
                                    current.selectedSpell(),
                                    List.copyOf(favorites),
                                    current.mastery(),
                                    current.persistentCooldowns(),
                                    current.uiState(),
                                    current.extensions()),
                            result);
                });
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
