package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.data.CraftingRule;
import hu.taliann.icesmp.managers.CraftingRestrictionManager;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class JobCraftRestrictionListener implements Listener {

    private final CraftingRestrictionManager craftingRestrictionManager;
    private final MessageManager messageManager;

    public JobCraftRestrictionListener(final CraftingRestrictionManager craftingRestrictionManager,
                                       final MessageManager messageManager) {
        this.craftingRestrictionManager = craftingRestrictionManager;
        this.messageManager = messageManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareItemCraft(final PrepareItemCraftEvent event) {
        final ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }

        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }

        final CraftingRule rule = craftingRestrictionManager.findViolatedRule(player, result.getType());
        if (rule == null) {
            return;
        }

        event.getInventory().setResult(null);
        notifyRestriction(player, rule);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(final PrepareSmithingEvent event) {
        final ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }

        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }

        final CraftingRule rule = craftingRestrictionManager.findViolatedRule(player, result.getType());
        if (rule == null) {
            return;
        }

        event.setResult(null);
        notifyRestriction(player, rule);
    }

    private void notifyRestriction(final Player player, final CraftingRule rule) {
        if (!craftingRestrictionManager.shouldNotify(player.getUniqueId())) {
            return;
        }

        final String levelText = String.valueOf(rule.requiredLevel());
        if (rule.requiredJob() == null) {
            player.sendMessage(messageManager.getMessage(
                    "crafting.restricted-any",
                    "&cEhhez a tárgyhoz legalább &f{level}&c. szintű kaszt szükséges.",
                    Map.of("level", levelText)
            ));
            return;
        }

        final String jobName = PlainTextComponentSerializer.plainText().serialize(rule.requiredJob().getDisplayName());
        player.sendMessage(messageManager.getMessage(
                "crafting.restricted",
                "&cEhhez a tárgyhoz &f{job} &ckaszt szükséges (min. &f{level}&c. szint).",
                Map.of("job", jobName, "level", levelText)
        ));
    }
}
