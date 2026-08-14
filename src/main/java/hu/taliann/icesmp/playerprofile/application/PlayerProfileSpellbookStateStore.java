package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.SpellbookSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/** Typed authority for selected spell and restart-durable spell cooldown timestamps. */
public final class PlayerProfileSpellbookStateStore {

    public String selectedSpell(final UUID playerId) {
        return section(playerId).selectedSpell();
    }

    public long lastCast(final UUID playerId, final String spellId) {
        return section(playerId).persistentCooldowns().getOrDefault(spellId(spellId), 0L);
    }

    public CompletionStage<String> select(final UUID playerId, final String spellId) {
        return selectWhile(playerId, spellId, () -> true);
    }

    public CompletionStage<String> selectWhile(final UUID playerId, final String spellId,
                                                final BooleanSupplier currentSession) {
        final String selected = spellId == null || spellId.isBlank() ? "" : spellId(spellId);
        Objects.requireNonNull(currentSession, "currentSession");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                    if (!currentSession.getAsBoolean()) {
                        throw new IllegalStateException("stale spellbook session mutation");
                    }
                    if (current.selectedSpell().equals(selected)) {
                        return PlayerProfileService.ConditionalMutation.unchanged(selected);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, selected, current.persistentCooldowns()), selected);
                });
    }

    public CompletionStage<Long> recordLastCast(final UUID playerId,
                                                 final String spellId,
                                                 final long timestamp) {
        if (timestamp < 0L) throw new IllegalArgumentException("negative spell cooldown timestamp");
        final String id = spellId(spellId);
        return PlayerProfileAuthority.current().mutateSectionConditional(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                    final long existing = current.persistentCooldowns().getOrDefault(id, 0L);
                    if (existing == timestamp) {
                        return PlayerProfileService.ConditionalMutation.unchanged(existing);
                    }
                    if (existing > timestamp) {
                        throw new IllegalStateException("spell cooldown timestamp moved backwards");
                    }
                    final LinkedHashMap<String, Long> cooldowns =
                            new LinkedHashMap<>(current.persistentCooldowns());
                    cooldowns.put(id, timestamp);
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, current.selectedSpell(), cooldowns), timestamp);
                });
    }

    public CompletionStage<Void> clearCooldowns(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                    if (current.persistentCooldowns().isEmpty()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(null);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, current.selectedSpell(), Map.of()), null);
                });
    }

    public CompletionStage<Void> reset(final UUID playerId) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                    if (current.selectedSpell().isEmpty()
                            && current.persistentCooldowns().isEmpty()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(null);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withState(current, "", Map.of()), null);
                });
    }

    private static SpellbookSection section(final UUID playerId) {
        return PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.SPELLBOOK, SpellbookSection.class);
    }

    private static SpellbookSection withState(final SpellbookSection current,
                                              final String selected,
                                              final Map<String, Long> cooldowns) {
        return new SpellbookSection(current.provenance(), selected,
                current.favorites(), current.mastery(), cooldowns,
                current.uiState(), current.extensions());
    }

    private static String spellId(final String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("spell id required");
        final String id = raw.trim().toLowerCase(Locale.ROOT);
        if (id.length() > 128 || !id.matches("[a-z0-9][a-z0-9._:-]*")) {
            throw new IllegalArgumentException("invalid spell id: " + raw);
        }
        return id;
    }
}
