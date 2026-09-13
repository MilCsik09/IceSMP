package hu.taliann.icesmp.dev.weaver.gui;

import hu.taliann.icesmp.dev.artifact.DevArtifactContext;
import hu.taliann.icesmp.dev.weaver.api.WeaverDomainRejection;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** The renderer knows only shared entry data; domain adapters never receive this renderer. */
public final class WorldWeaverGUI {
    public void open(final DevArtifactContext context, final WeaverView view) {
        final var player = context.player();
        if (player == null || !Bukkit.isOwnedByCurrentRegion(player)) throw new WeaverDomainRejection("AUTHORITY_REJECTED");
        final WorldWeaverHolder holder = new WorldWeaverHolder(view.sessionId(), view.revision(), view.kind());
        final var inventory = Bukkit.createInventory(holder, 54, view.title()); holder.attach(inventory);
        for (int slot = 0; slot < 54; slot++) {
            final var entry = view.at(slot);
            if (entry.isEmpty()) continue;
            final Material icon = Material.matchMaterial(entry.get().icon());
            final ItemStack item = new ItemStack(icon == null || !icon.isItem() || icon.isAir() ? Material.PAPER : icon);
            final var meta = item.getItemMeta(); meta.displayName(entry.get().label()); meta.lore(entry.get().lore()); item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }
        player.openInventory(inventory);
    }
}
