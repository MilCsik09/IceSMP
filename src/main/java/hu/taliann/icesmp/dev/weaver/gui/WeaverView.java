package hu.taliann.icesmp.dev.weaver.gui;

import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Only immutable display data and navigation values live in a frontend session. */
public record WeaverView(UUID sessionId, long revision, WeaverViewKind kind, Component title,
                         List<Entry> entries, Map<Integer, Entry> controls, int page) {
    public record Entry(Component label, List<Component> lore, String icon, WeaverNavigation navigation) {
        public Entry {
            label = bounded(label, 128); lore = List.copyOf(lore).stream().map(line -> bounded(line, 256)).toList(); java.util.Objects.requireNonNull(navigation);
            if (lore.size() > 16 || icon == null || !icon.matches("[A-Z_]{1,64}")) throw new IllegalArgumentException("Invalid view entry");
        }
        private static Component bounded(final Component source, final int max) {
            final String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(java.util.Objects.requireNonNull(source));
            return Component.text(text.substring(0, text.offsetByCodePoints(0, Math.min(max, text.codePointCount(0, text.length())))));
        }
    }
    public WeaverView {
        java.util.Objects.requireNonNull(sessionId); java.util.Objects.requireNonNull(kind); title = Entry.bounded(title, 128);
        entries = List.copyOf(entries); controls = Map.copyOf(controls);
        if (revision < 1 || entries.size() > 4096 || page < 0 || page > Math.max(0, (entries.size() - 1) / 45)
                || controls.keySet().stream().anyMatch(slot -> slot < 45 || slot > 53)) throw new IllegalArgumentException("Invalid view bounds");
    }
    public java.util.Optional<Entry> at(final int slot) {
        if (slot < 0 || slot >= 54) return java.util.Optional.empty();
        if (slot >= 45) return java.util.Optional.ofNullable(controls.get(slot));
        final int index = page * 45 + slot;
        return index < entries.size() ? java.util.Optional.of(entries.get(index)) : java.util.Optional.empty();
    }
}
