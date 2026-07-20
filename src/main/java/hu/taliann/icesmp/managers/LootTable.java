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
     * Opcionális unique-material híd: ha be van kötve (IceSMPCore, induláskor), a
     * loot-táblák {@code "unique:<id>"} / {@code "unique:<id>:DARAB"} sorokat is
     * elfogadnak — a tárgyat a UniqueMaterialFactory építi (PDC-taggel). Nélküle a
     * unique-sorok némán kimaradnak a sorsolásból.
     */
    private static volatile hu.taliann.icesmp.items.UniqueMaterialFactory uniqueFactory;

    public static void setUniqueFactory(final hu.taliann.icesmp.items.UniqueMaterialFactory factory) {
        uniqueFactory = factory;
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

    /**
     * Validates a loot-table entry and describes what is wrong with it, or returns
     * null if the entry is well-formed. Used by {@link ConfigValidator} so admin
     * typos ({@code min > max}, negative or non-numeric counts, unknown materials)
     * surface as load-time warnings instead of being silently coerced at roll time.
     */
    public static String describeProblem(final String entry) {
        final String[] parts = entry.split(":");
        if ("unique".equalsIgnoreCase(parts[0].trim())) {
            // A unique-id létezését a factory csak futásidőben (config-betöltés után) tudja
            // igazolni — formailag az "unique:<id>[:db]" alak az elvárás.
            return parts.length >= 2 && !parts[1].trim().isEmpty()
                    ? null : "hiányzó unique-azonosító: '" + entry + "'";
        }
        final Material material = Material.matchMaterial(parts[0].trim());
        if (material == null || material.isAir()) {
            return "ismeretlen Material: '" + parts[0].trim() + "'";
        }
        try {
            if (parts.length == 2) {
                final int count = Integer.parseInt(parts[1].trim());
                if (count < 1) {
                    return "a darabszám nem lehet " + count + " (minimum 1)";
                }
            } else if (parts.length >= 3) {
                final int min = Integer.parseInt(parts[1].trim());
                final int max = Integer.parseInt(parts[2].trim());
                if (min < 1) {
                    return "a MIN nem lehet " + min + " (minimum 1)";
                }
                if (min > max) {
                    return "a MIN (" + min + ") nagyobb, mint a MAX (" + max + ")";
                }
            }
        } catch (final NumberFormatException e) {
            return "nem szám a darabszám: '" + entry + "'";
        }
        return null;
    }

    /** Parses a {@code "MATERIAL"}, {@code "MATERIAL:COUNT"}, {@code "MATERIAL:MIN:MAX"} or
     * {@code "unique:<id>[:COUNT]"} entry, or null if invalid. */
    public static ItemStack parseEntry(final String entry) {
        final String[] parts = entry.split(":");
        if ("unique".equalsIgnoreCase(parts[0].trim())) {
            final hu.taliann.icesmp.items.UniqueMaterialFactory factory = uniqueFactory;
            if (factory == null || parts.length < 2) {
                return null;
            }
            int uniqueAmount = 1;
            if (parts.length >= 3) {
                try {
                    uniqueAmount = Math.max(1, Integer.parseInt(parts[2].trim()));
                } catch (final NumberFormatException ignored) {
                    // Hibás darabszám — 1 darabbal megyünk tovább.
                }
            }
            return factory.create(parts[1].trim(), uniqueAmount);
        }
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
