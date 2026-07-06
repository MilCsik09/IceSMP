package hu.taliann.icesmp.managers;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared helper for config-driven loot tables (treasure chests, wild-hunt drops).
 * A table is a string list under a config path where each entry is one of
 * {@code "MATERIAL"}, {@code "MATERIAL:COUNT"} or {@code "MATERIAL:MIN:MAX"};
 * {@link #roll} picks a random subset into item stacks. Rewards are always raw
 * items, never currency.
 */
public final class LootTable {

    private LootTable() {
    }

    /**
     * Rolls {@code rolls} random entries from the loot table at {@code path}.
     *
     * @param configManager the config source
     * @param path the config path of the string-list loot table
     * @param rolls how many entries to draw (at least 1)
     * @return the rolled item stacks (empty if the table is empty)
     */
    public static List<ItemStack> roll(final ConfigManager configManager, final String path, final int rolls) {
        final List<String> table = configManager.getStringList(path);
        if (table.isEmpty()) {
            return List.of();
        }
        final int count = Math.max(1, rolls);
        final List<ItemStack> loot = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final ItemStack stack = parseEntry(table.get(ThreadLocalRandom.current().nextInt(table.size())));
            if (stack != null) {
                loot.add(stack);
            }
        }
        return loot;
    }

    /** Parses a {@code "MATERIAL"}, {@code "MATERIAL:COUNT"} or {@code "MATERIAL:MIN:MAX"} entry, or null if invalid. */
    public static ItemStack parseEntry(final String entry) {
        final String[] parts = entry.split(":");
        final Material material = Material.matchMaterial(parts[0].trim());
        if (material == null || material.isAir()) {
            return null;
        }
        int amount = 1;
        try {
            if (parts.length == 2) {
                amount = Integer.parseInt(parts[1].trim());
            } else if (parts.length >= 3) {
                final int min = Integer.parseInt(parts[1].trim());
                final int max = Integer.parseInt(parts[2].trim());
                amount = min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
            }
        } catch (final NumberFormatException ignored) {
            amount = 1;
        }
        return new ItemStack(material, Math.max(1, Math.min(material.getMaxStackSize(), amount)));
    }
}
