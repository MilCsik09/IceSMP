package hu.taliann.icesmp.itemization;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded, durable evidence used only to soften repetitive authored loot rolls. */
public record LootDiversityState(List<Drop> recentDrops) {

    public static final int MAX_DROPS = 32;

    public record Drop(UUID itemId, String templateId, ItemRarity rarity,
                       ItemTemplate.Slot slot, ItemTemplate.Family family) {
        public Drop {
            Objects.requireNonNull(itemId, "itemId");
            templateId = ItemStatCatalog.normalizeId(templateId);
            Objects.requireNonNull(rarity, "rarity");
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(family, "family");
        }

        public static Drop of(final UUID itemId, final ItemTemplate template) {
            Objects.requireNonNull(template, "template");
            return new Drop(itemId, template.templateId(), template.rarity(),
                    template.slot(), template.family());
        }
    }

    public LootDiversityState {
        recentDrops = recentDrops == null ? List.of() : List.copyOf(recentDrops);
        if (recentDrops.size() > MAX_DROPS) {
            throw new IllegalArgumentException("loot diversity history exceeds limit");
        }
        if (recentDrops.stream().map(Drop::itemId).distinct().count() != recentDrops.size()) {
            throw new IllegalArgumentException("loot diversity history contains a duplicate item receipt");
        }
    }

    public static LootDiversityState empty() {
        return new LootDiversityState(List.of());
    }

    public LootDiversityState record(final Drop drop) {
        Objects.requireNonNull(drop, "drop");
        if (recentDrops.stream().anyMatch(existing -> existing.itemId().equals(drop.itemId()))) {
            return this;
        }
        final ArrayList<Drop> next = new ArrayList<>(recentDrops);
        next.add(drop);
        while (next.size() > MAX_DROPS) next.remove(0);
        return new LootDiversityState(next);
    }

    public List<Drop> tail(final int limit) {
        if (limit < 1 || limit > MAX_DROPS) {
            throw new IllegalArgumentException("loot diversity window must be between 1 and " + MAX_DROPS);
        }
        final int from = Math.max(0, recentDrops.size() - limit);
        return recentDrops.subList(from, recentDrops.size());
    }
}
