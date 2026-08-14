package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.managers.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Staged category view for the canonical {@link ConfigMenuGUI} allowlist. */
public final class TransactionalConfigMenuGUI {

    private TransactionalConfigMenuGUI() {
    }

    public static void openCategory(final Player player, final String categoryId,
                                    final ConfigManager configManager,
                                    final ConfigEditSession session) {
        final ConfigMenuGUI.Category category = ConfigMenuGUI.CATEGORIES.get(categoryId);
        if (category == null) {
            ConfigMenuRootGUI.openRoot(player, session);
            return;
        }
        if (category.entries().size() > 45) {
            throw new IllegalStateException("Config GUI category capacity exceeded: " + category.id());
        }
        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), categoryId);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ " + category.title(), NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);
        int slot = 0;
        for (final ConfigMenuGUI.Entry entry : category.entries()) {
            inventory.setItem(slot, ConfigMenuEntryRenderer.render(entry, configManager, session));
            holder.bind(slot, switch (entry.type()) {
                case TOGGLE -> "TOGGLE:" + entry.key();
                case CYCLE -> "CYCLE:" + entry.key();
                default -> "NUM:" + entry.key();
            });
            slot++;
        }
        ConfigMenuControls.add(inventory, holder, session, true);
        player.openInventory(inventory);
    }
}
