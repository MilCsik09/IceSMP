package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ItemRarityService;
import hu.taliann.icesmp.managers.ProfessionRecipeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Rolls random-attribute affixes onto a profession masterwork at the moment it is crafted,
 * so each masterwork comes out unique. The level/profession requirement is already enforced
 * by {@link ProfessionRecipeListener} (it blanks the result for the unqualified), so a present
 * masterwork result here means the crafter is eligible. Shift-click bulk crafts keep the base
 * stats — the rolled unique comes from a normal single craft.
 */
public final class MasterworkCraftListener implements Listener {

    private final ProfessionRecipeManager recipeManager;
    private final ItemRarityService affixService;

    public MasterworkCraftListener(final ProfessionRecipeManager recipeManager,
                                   final ItemRarityService affixService) {
        this.recipeManager = recipeManager;
        this.affixService = affixService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(final CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player) || event.isShiftClick()) {
            return;
        }
        final ItemStack result = event.getCurrentItem();
        if (recipeManager.getRequirement(result) == null || affixService.isRolled(result)) {
            return;
        }
        final ItemStack rolled = affixService.roll(result, ItemRarityService.TIER_CRAFTED);
        if (rolled != result) {
            event.setCurrentItem(rolled);
        }
    }
}
