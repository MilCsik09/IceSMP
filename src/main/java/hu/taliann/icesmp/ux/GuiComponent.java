package hu.taliann.icesmp.ux;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** Session-owned inventory component; it never owns an ItemStack outside its session. */
public interface GuiComponent {
    int slot();
    boolean visible(GuiSession session);
    ItemStack render(GuiSession session);
    default boolean enabled(final GuiSession session) { return true; }
    default void onInteraction(final GuiSession session, final InventoryClickEvent event) { }
}
