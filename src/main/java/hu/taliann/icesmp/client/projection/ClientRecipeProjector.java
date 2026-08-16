package hu.taliann.icesmp.client.projection;

import hu.taliann.icesmp.client.protocol.ClientProtocol;
import hu.taliann.icesmp.client.protocol.RecipePagePayload;
import hu.taliann.icesmp.data.ProfessionType;
import hu.taliann.icesmp.gui.ProfessionRecipeGUI;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RECIPE_PAGE-projekció a vanilla recept-könyv (ProfessionRecipeGUI.buildTile)
 * csempe-logikájával azonos feltételekből — a craftolhatóság a vanilla csempével
 * egyezően szint + tervrajz + hozzávalók (a kaszt-kötést a csempe sem mutatja, azt
 * a craft-tranzakció validálja). A have-számlálás ugyanazokat a megosztott
 * GUI-helpereket használja, így a unique-anyagok kizárása is bitre azonos.
 * Player-szálon hívandó (inventory-olvasás).
 */
public final class ClientRecipeProjector {

    private ClientRecipeProjector() {
    }

    public static RecipePagePayload page(final Player player,
                                         final ProfessionType profession,
                                         final int requestedPage,
                                         final int requestedPageSize,
                                         final ProfessionManager professions,
                                         final ProfessionRecipeCatalog catalog,
                                         final UniqueMaterialFactory uniqueMaterials) {
        final List<ProfessionRecipeCatalog.Recipe> recipes = catalog.recipesFor(profession);
        final int pageSize = Math.max(1, Math.min(requestedPageSize, ClientProtocol.MAX_LIST_ELEMENTS));
        final int pageCount = Math.max(1, (recipes.size() + pageSize - 1) / pageSize);
        final int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        final int playerLevel = professions.getLevel(player, profession);

        final int from = page * pageSize;
        final int to = Math.min(recipes.size(), from + pageSize);
        final List<RecipePagePayload.Entry> entries = new ArrayList<>(Math.max(0, to - from));
        for (int i = from; i < to; i++) {
            entries.add(entryFor(player, recipes.get(i), playerLevel, professions, uniqueMaterials));
        }
        return new RecipePagePayload(profession.getId(), page, pageCount, recipes.size(), entries);
    }

    private static RecipePagePayload.Entry entryFor(final Player player,
                                                    final ProfessionRecipeCatalog.Recipe recipe,
                                                    final int playerLevel,
                                                    final ProfessionManager professions,
                                                    final UniqueMaterialFactory uniqueMaterials) {
        final boolean levelOk = playerLevel >= recipe.level();
        final boolean learned = !recipe.blueprint()
                || professions.hasLearnedRecipe(player, recipe.id());
        boolean hasMats = true;
        final List<RecipePagePayload.Ingredient> ingredients = new ArrayList<>(
                recipe.ingredients().size() + recipe.uniqueIngredients().size());
        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {
            final int have = ProfessionRecipeGUI.countMaterial(player, entry.getKey(), uniqueMaterials);
            hasMats &= have >= entry.getValue();
            ingredients.add(new RecipePagePayload.Ingredient(
                    ProfessionRecipeGUI.prettyName(entry.getKey()), entry.getValue(), have, false));
        }
        for (final Map.Entry<String, Integer> entry : recipe.uniqueIngredients().entrySet()) {
            final int have = ProfessionRecipeGUI.countUnique(player, entry.getKey(), uniqueMaterials);
            hasMats &= have >= entry.getValue();
            ingredients.add(new RecipePagePayload.Ingredient(
                    uniqueMaterials.displayName(entry.getKey()), entry.getValue(), have, true));
        }
        return new RecipePagePayload.Entry(
                recipe.id(), recipe.displayName(), recipe.category(), recipe.kind(),
                recipe.level(), levelOk, recipe.blueprint(), learned, recipe.lootOnly(),
                recipe.faction() == null ? ""
                        : recipe.faction().getDisplayName() + " (" + recipe.faction().getFullName() + ")",
                recipe.affixTier() != null,
                recipe.resultAmount(),
                levelOk && learned && hasMats,
                ingredients);
    }
}
