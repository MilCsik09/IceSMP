package hu.taliann.icesmp.client.projection;

import hu.taliann.icesmp.client.protocol.ProfessionStatePayload;
import hu.taliann.icesmp.data.ProfessionCategory;
import hu.taliann.icesmp.data.ProfessionSpecializationType;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager;
import hu.taliann.icesmp.managers.SpecializationManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * PROFESSION_STATE-projekció a szakma-áttekintő vanilla felületeivel (ProfessionGUI,
 * /profession info, /szakmacel) azonos manager-hívásokból. A teljes nyolc-szakmás
 * roster utazik (a vanilla GUI a tanulható főszakmákat is mutatja); spec-opció viszont
 * csak a játékos aktívan gyakorolt szakmáira megy ki — más szakmák spec-fája nem
 * kerül a vezetékre. Recept tétel-szinten nem utazik, csak darabszám (a katalógus a
 * külön recept-böngésző modul dolga marad).
 */
public final class ClientProfessionProjector {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ClientProfessionProjector() {
    }

    public static ProfessionStatePayload project(final Player player,
                                                 final ProfessionManager professions,
                                                 final SpecializationManager specializations,
                                                 final ProfessionRecipeCatalog catalog,
                                                 final ProfessionWeeklyGoalManager weeklyGoal) {
        final ProfessionSpecializationType activeSpec =
                specializations.getProfessionSpecialization(player);
        final List<ProfessionStatePayload.Entry> entries = new ArrayList<>(ProfessionType.values().length);
        for (final ProfessionType profession : ProfessionType.values()) {
            entries.add(entryFor(player, professions, catalog, weeklyGoal, profession));
        }
        final List<ProfessionStatePayload.SpecOption> options = new ArrayList<>();
        for (final ProfessionSpecializationType spec : ProfessionSpecializationType.values()) {
            if (!professions.hasProfession(player, spec.getParentProfession())) {
                continue;
            }
            options.add(new ProfessionStatePayload.SpecOption(
                    spec.getId(),
                    PLAIN.serialize(spec.getDisplayName()),
                    spec.getParentProfession().getId(),
                    specializations.getRequiredProfessionLevel(),
                    specializations.canSelectProfessionSpecialization(player, spec)));
        }
        return new ProfessionStatePayload(
                activeSpec == null ? "" : activeSpec.getId(),
                activeSpec == null ? "" : PLAIN.serialize(activeSpec.getDisplayName()),
                entries, options);
    }

    private static ProfessionStatePayload.Entry entryFor(final Player player,
                                                         final ProfessionManager professions,
                                                         final ProfessionRecipeCatalog catalog,
                                                         final ProfessionWeeklyGoalManager weeklyGoal,
                                                         final ProfessionType profession) {
        final boolean active = professions.hasProfession(player, profession);
        final boolean slotTaken = !active
                && profession.getCategory() != ProfessionCategory.SECONDARY
                && professions.getProfession(player, profession.getCategory()) != null;
        final int level = professions.getLevel(player, profession);
        final ProfessionManager.XpBreakdown xp = professions.xpBreakdown(player, profession);
        int recipeTotal = 0;
        int blueprintKnown = 0;
        int blueprintTotal = 0;
        for (final ProfessionRecipeCatalog.Recipe recipe : catalog.recipesFor(profession)) {
            recipeTotal++;
            if (recipe.blueprint()) {
                blueprintTotal++;
                if (professions.hasLearnedRecipe(player, recipe.id())) {
                    blueprintKnown++;
                }
            }
        }
        return new ProfessionStatePayload.Entry(
                profession.getId(),
                PLAIN.serialize(profession.getDisplayName()),
                profession.getCategory().name(),
                profession.getCategory().getDisplayName(),
                active, slotTaken,
                level, ProfessionManager.MAX_PROFESSION_LEVEL,
                ProfessionManager.getRankName(level),
                xp.intoLevel(), xp.forNextLevel(),
                weeklyGoal.counterOf(profession), weeklyGoal.goalOf(profession),
                recipeTotal, blueprintKnown, blueprintTotal);
    }
}
