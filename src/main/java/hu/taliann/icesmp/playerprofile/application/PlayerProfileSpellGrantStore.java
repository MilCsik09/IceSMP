package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.classspec.domain.SpellGrantLedger;
import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.SpellbookSection;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/** Canonical CAS-backed spell provenance store; PDC is never read as authority. */
public final class PlayerProfileSpellGrantStore {

    public SpellGrantLedger read(final UUID playerId) {
        final SpellbookSection section = PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.SPELLBOOK, SpellbookSection.class);
        return SpellGrantLedger.fromProvenance(section.provenance());
    }

    public CompletionStage<SpellGrantLedger.Mutation> add(final UUID playerId,
                                                           final String spellId,
                                                           final String source) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                    final SpellGrantLedger.Mutation mutation = SpellGrantLedger
                            .fromProvenance(current.provenance()).add(spellId, source);
                    if (!mutation.changed()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(mutation);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withLedger(current, mutation.ledger()), mutation);
                });
    }

    public CompletionStage<SpellGrantLedger.Mutation> remove(final UUID playerId,
                                                              final String spellId,
                                                              final String source) {
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                    final SpellGrantLedger.Mutation mutation = SpellGrantLedger
                            .fromProvenance(current.provenance()).remove(spellId, source);
                    if (!mutation.changed()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(mutation);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withLedger(current, mutation.ledger()), mutation);
                });
    }

    public CompletionStage<SpellGrantLedger.RevokeResult> revokeSources(
            final UUID playerId, final Predicate<String> sourceMatches) {
        Objects.requireNonNull(sourceMatches, "sourceMatches");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                    final SpellGrantLedger.RevokeResult result = SpellGrantLedger
                            .fromProvenance(current.provenance()).revokeSources(sourceMatches);
                    if (!result.changed()) {
                        return PlayerProfileService.ConditionalMutation.unchanged(result);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withLedger(current, result.ledger()), result);
                });
    }

    public CompletionStage<SpellGrantLedger> replace(final UUID playerId,
                                                      final SpellGrantLedger requested) {
        Objects.requireNonNull(requested, "requested");
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.SPELLBOOK, SpellbookSection.class, current -> {
                    final SpellGrantLedger existing = SpellGrantLedger.fromProvenance(current.provenance());
                    if (existing.provenance().equals(requested.provenance())) {
                        return PlayerProfileService.ConditionalMutation.unchanged(existing);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            withLedger(current, requested), requested);
                });
    }

    private static SpellbookSection withLedger(final SpellbookSection current,
                                                final SpellGrantLedger ledger) {
        return new SpellbookSection(ledger.provenance(), current.selectedSpell(),
                current.favorites(), current.mastery(), current.persistentCooldowns(),
                current.uiState(), current.extensions());
    }
}
