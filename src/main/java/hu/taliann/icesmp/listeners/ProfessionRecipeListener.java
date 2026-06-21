package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ProfessionRecipeManager;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gates the profession masterwork recipes: if the crafting result is a profession
 * recipe and the crafter lacks the required profession/level, the result is
 * cleared (with a throttled action-bar hint).
 */
public final class ProfessionRecipeListener implements Listener {

    private final ProfessionRecipeManager recipeManager;
    private final ProfessionManager professionManager;
    private final MessageManager messageManager;
    private final Map<UUID, Long> hintThrottle = new ConcurrentHashMap<>();

    public ProfessionRecipeListener(final ProfessionRecipeManager recipeManager,
                                    final ProfessionManager professionManager, final MessageManager messageManager) {
        this.recipeManager = recipeManager;
        this.professionManager = professionManager;
        this.messageManager = messageManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareCraft(final PrepareItemCraftEvent event) {
        final ItemStack result = event.getInventory().getResult();
        final ProfessionRecipeManager.Recipe recipe = recipeManager.getRequirement(result);
        if (recipe == null) {
            return;
        }

        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }

        final boolean meets = professionManager.hasProfession(player, recipe.profession())
                && professionManager.getLevel(player, recipe.profession()) >= recipe.requiredLevel();
        if (meets) {
            return;
        }

        event.getInventory().setResult(null);

        final long now = System.currentTimeMillis();
        if (now - hintThrottle.getOrDefault(player.getUniqueId(), 0L) < 2000L) {
            return;
        }
        hintThrottle.put(player.getUniqueId(), now);
        player.sendActionBar(messageManager.getMessage(
                "profession-recipe-locked",
                "<red>Ehhez {profession} {level}. szint kell.</red>",
                Map.of(
                        "profession", plainName(recipe),
                        "level", String.valueOf(recipe.requiredLevel())
                )));
    }

    @EventHandler
    public void onPlayerQuit(final org.bukkit.event.player.PlayerQuitEvent event) {
        hintThrottle.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(final org.bukkit.event.player.PlayerKickEvent event) {
        hintThrottle.remove(event.getPlayer().getUniqueId());
    }

    private String plainName(final ProfessionRecipeManager.Recipe recipe) {
        return recipe.profession().name().toLowerCase(java.util.Locale.ROOT);
    }
}
