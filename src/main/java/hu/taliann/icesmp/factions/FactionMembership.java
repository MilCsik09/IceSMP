package hu.taliann.icesmp.factions;

import hu.taliann.icesmp.data.FactionType;

import java.util.Optional;

/** Explicit citizenship; a missing assignment is a Menedék guest, not a NEUTRAL citizen. */
public record FactionMembership(FactionType chosenFaction) {

    public static FactionMembership guest() {
        return new FactionMembership(null);
    }

    public static FactionMembership citizen(final FactionType faction) {
        if (faction == null) {
            throw new IllegalArgumentException("A chosen faction cannot be null");
        }
        return new FactionMembership(faction);
    }

    public Optional<FactionType> chosenFactionOptional() {
        return Optional.ofNullable(chosenFaction);
    }

    public boolean hasChosenFaction() {
        return chosenFaction != null;
    }

    public boolean isEligibleForFactionBenefits() {
        return hasChosenFaction();
    }

    public boolean isMember(final FactionType faction) {
        return faction != null && chosenFaction == faction;
    }
}
