package hu.taliann.icesmp.trash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Display observations must never become canonical lore through creative inventory packets. */
final class ArchaeologyTooltipLore {
    static final Component HEADER = Component.text("Régészeti megfigyelések", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);

    private ArchaeologyTooltipLore() { }

    static boolean strip(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        final var meta = item.getItemMeta();
        final List<Component> lore = meta.lore();
        if (lore == null || !lore.contains(HEADER)) return false;
        final List<Component> clean = withoutObservations(lore);
        meta.lore(clean.isEmpty() ? null : clean);
        item.setItemMeta(meta);
        return true;
    }

    static List<Component> withoutObservations(final List<Component> lore) {
        final var clean = new ArrayList<Component>();
        for (int i = 0; i < lore.size(); i++) {
            if (!HEADER.equals(lore.get(i))) { clean.add(lore.get(i)); continue; }
            if (!clean.isEmpty() && Component.empty().equals(clean.getLast())) clean.removeLast();
            while (i + 1 < lore.size() && observation(lore.get(i + 1))) i++;
        }
        return List.copyOf(clean);
    }

    private static boolean observation(final Component line) {
        return NamedTextColor.GRAY.equals(line.color())
                && line.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE
                && PlainTextComponentSerializer.plainText().serialize(line).startsWith("• ");
    }
}
