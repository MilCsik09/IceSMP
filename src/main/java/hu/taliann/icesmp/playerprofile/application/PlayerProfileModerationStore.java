package hu.taliann.icesmp.playerprofile.application;

import hu.taliann.icesmp.playerprofile.domain.ProfileSectionId;
import hu.taliann.icesmp.playerprofile.domain.section.ModerationSection;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * CAS-backed moderation reference/summary for the player profile.
 *
 * <p>The shared punishment ledger stays the audit authority; the profile only carries the
 * stable record references and the strike count, so the summary can always be re-derived
 * and re-synced from the ledger without data loss.</p>
 */
public final class PlayerProfileModerationStore {

    /** Publishes the ledger-derived summary; identical state is a no-op commit. */
    public CompletionStage<Boolean> syncSummary(final UUID playerId,
                                                final Set<String> activePunishmentRefs,
                                                final int strikeCount) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(activePunishmentRefs, "activePunishmentRefs");
        if (strikeCount < 0) throw new IllegalArgumentException("negative strike count");
        final Set<String> refs = new LinkedHashSet<>();
        for (final String ref : activePunishmentRefs) {
            if (ref == null || ref.isBlank()) {
                throw new IllegalArgumentException("blank punishment reference");
            }
            refs.add(ref.trim());
        }
        return PlayerProfileAuthority.current().mutateSectionConditional(
                playerId, ProfileSectionId.MODERATION, ModerationSection.class, current -> {
                    if (current.activePunishmentRefs().equals(refs)
                            && current.strikeCount() == strikeCount) {
                        return PlayerProfileService.ConditionalMutation.unchanged(false);
                    }
                    return PlayerProfileService.ConditionalMutation.changed(
                            new ModerationSection(refs, strikeCount, current.adminNoteRefs(),
                                    current.auditState(), current.extensions()), true);
                });
    }

    /** Session-cached summary read for online-player surfaces. */
    public ModerationSection read(final UUID playerId) {
        return PlayerProfileAuthority.current().requireSection(
                Objects.requireNonNull(playerId, "playerId"),
                ProfileSectionId.MODERATION, ModerationSection.class);
    }
}
