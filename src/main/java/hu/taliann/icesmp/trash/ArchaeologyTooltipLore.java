package hu.taliann.icesmp.trash;

import hu.taliann.icesmp.ux.TooltipPresentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Display observations must never become canonical lore through creative inventory packets. */
final class ArchaeologyTooltipLore {
    private static final Component LEGACY_HEADER = Component.text("Régészeti megfigyelések", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);

    static final Component HEADER = TooltipPresentation.classification(
            "Régészeti megfigyelések", "", NamedTextColor.GOLD);

    private ArchaeologyTooltipLore() { }

    static boolean strip(final ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        final var meta = item.getItemMeta();
        final List<Component> lore = meta.lore();
        if (lore == null || lore.stream().noneMatch(ArchaeologyTooltipLore::header)) return false;
        final List<Component> clean = withoutObservations(lore);
        meta.lore(clean.isEmpty() ? null : clean);
        item.setItemMeta(meta);
        return true;
    }

    static List<Component> withoutObservations(final List<Component> lore) {
        final var clean = new ArrayList<Component>();
        for (int i = 0; i < lore.size(); i++) {
            if (!header(lore.get(i))) { clean.add(lore.get(i)); continue; }
            if (!clean.isEmpty() && Component.empty().equals(clean.getLast())) clean.removeLast();
            while (i + 1 < lore.size() && observation(lore.get(i + 1))) i++;
        }
        return List.copyOf(clean);
    }

    private static boolean header(final Component line) {
        return HEADER.equals(line) || LEGACY_HEADER.equals(line);
    }

    static List<Component> observations(final List<String> observations) {
        if (observations == null || observations.isEmpty()) return List.of();
        final ArrayList<Component> result = new ArrayList<>();
        observations.stream().limit(8).forEach(raw -> {
            final List<String> wrapped = wrap(raw, 46);
            for (int index = 0; index < wrapped.size(); index++) {
                result.add(Component.text((index == 0 ? "• " : "  ") + wrapped.get(index),
                                NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        });
        return List.copyOf(result);
    }

    private static boolean observation(final Component line) {
        final String plain = PlainTextComponentSerializer.plainText().serialize(line);
        return NamedTextColor.GRAY.equals(line.color())
                && line.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE
                && (plain.startsWith("• ") || plain.startsWith("  "));
    }

    private static List<String> wrap(final String raw, final int width) {
        if (raw == null || raw.isBlank()) return List.of();
        final ArrayList<String> lines = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        for (final String word : raw.trim().split("\\s+")) {
            if (current.length() > 0 && current.length() + 1 + word.length() > width) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) current.append(' ');
            current.append(word);
        }
        if (current.length() > 0) lines.add(current.toString());
        return List.copyOf(lines);
    }
}
