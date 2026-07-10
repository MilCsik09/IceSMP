package hu.taliann.icesmp.spells;

import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SunDanceSpell extends BaseSpell {

    private static final Map<Material, List<CookingRecipe<?>>> RECIPE_CACHE = new ConcurrentHashMap<>();
    private static boolean recipeCachePopulated = false;

    public SunDanceSpell(final MessageManager messageManager) {
        super(messageManager, "sun_dance", "Naptanc", 3600, SpellCostType.XP, 352);
    }

    @Override
    public void execute(final Player player) {
        player.getWorld().setStorm(false);
        player.getWorld().setThundering(false);
        player.sendMessage(resolveMessage("spell.sun_dance.start", "<gold>A Nap ereje athatol a kemencek falan!</gold>"));

        populateRecipeCache();

        // Folia: keep the furnace scan small and region-local — a 25-radius cube was ~132K block
        // reads + tile-entity access on the region thread AND crossed region boundaries (illegal).
        // 8 wide / 5 tall stays inside the caster's region.
        final Location center = player.getLocation();
        final int radius = balanceInt("radius", 8);
        final int vertical = balanceInt("vertical-range", 5);
        int furnaceCount = 0;
        int totalItems = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if ((x * x) + (z * z) > (radius * radius)) continue;
                for (int y = -vertical; y <= vertical; y++) {
                    final Block block = center.getBlock().getRelative(x, y, z);
                    final Material type = block.getType();

                    // 1. Gyors sz\u0171r\u00e9s: csak a kemence t\u00edpusok
                    if (type != Material.FURNACE && type != Material.SMOKER && type != Material.BLAST_FURNACE) continue;

                    // 2. K\u00e9nyszer\u00edtett \u00e1llapot lek\u00e9r\u00e9s
                    if (!(block.getState() instanceof Furnace furnace)) continue;

                    final FurnaceInventory inv = furnace.getInventory();
                    final ItemStack input = inv.getSmelting();

                    if (input == null || input.getType().isAir()) continue;

                    // 3. Recept keres\u00e9se
                    final ItemStack resultTemplate = resolveResult(input, type);
                    if (resultTemplate == null) continue;

                    // 4. Sz\u00e1m\u00edt\u00e1s
                    int inputAmount = input.getAmount();
                    int producedAmount = inputAmount * resultTemplate.getAmount();

                    // 5. INVENTORY M\u00d3DOS\u00cdT\u00c1S (live inventory-n)
                    inv.clear(0); // Input slot t\u00f6rl\u00e9se

                    ItemStack finalResult = resultTemplate.clone();
                    finalResult.setAmount(producedAmount);

                    injectToOutput(furnace, inv, finalResult);

                    // 6. FRISS SNAPSHOT \u2013 m\u00e1r tartalmazza az inventory v\u00e1ltoz\u00e1sokat
                    if (!(block.getState() instanceof Furnace freshState)) continue;

                    // 7. \u00c1llapot reset a friss snapshot-on
                    freshState.setBurnTime((short) 0);
                    freshState.setCookTime((short) 0);
                    freshState.setCookTimeTotal(0);

                    // 8. K\u00c9NYSZER\u00cdTETT FRISS\u00cdT\u00c9S a friss snapshot-tal
                    if (freshState.update(true, true)) {
                        furnaceCount++;
                        totalItems += producedAmount;
                    }
                }
            }
        }

        player.sendMessage(resolveMessage(
                "spell.sun_dance.finish",
                "<green>Naptanc befejezve! Kemencek: {furnaceCount}, Targyak: {totalItems}</green>",
                Map.of(
                        "furnaceCount", String.valueOf(furnaceCount),
                        "totalItems", String.valueOf(totalItems)
                )
        ));
    }

    private static void populateRecipeCache() {
        if (recipeCachePopulated) {
            return;
        }
        final Map<Material, List<CookingRecipe<?>>> cache = new ConcurrentHashMap<>();
        cache.put(Material.FURNACE, new ArrayList<>());
        cache.put(Material.SMOKER, new ArrayList<>());
        cache.put(Material.BLAST_FURNACE, new ArrayList<>());

        final Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            final Recipe r = it.next();
            if (r instanceof SmokingRecipe smoking) {
                cache.get(Material.SMOKER).add(smoking);
            } else if (r instanceof BlastingRecipe blasting) {
                cache.get(Material.BLAST_FURNACE).add(blasting);
            } else if (r instanceof FurnaceRecipe furnaceRecipe) {
                cache.get(Material.FURNACE).add(furnaceRecipe);
            }
        }
        RECIPE_CACHE.putAll(cache);
        recipeCachePopulated = true;
    }

    private ItemStack resolveResult(ItemStack input, Material furnaceType) {
        ItemStack test = input.clone();
        test.setAmount(1);

        final List<CookingRecipe<?>> recipes = RECIPE_CACHE.get(furnaceType);
        if (recipes == null) {
            return null;
        }

        for (final CookingRecipe<?> cooking : recipes) {
            if (cooking.getInputChoice().test(test)) {
                return cooking.getResult().clone();
            }
        }
        return null;
    }

    private void injectToOutput(Furnace furnace, FurnaceInventory inv, ItemStack result) {
        ItemStack current = inv.getResult();
        if (current == null || current.getType().isAir()) {
            int toPlace = Math.min(result.getAmount(), 64);
            ItemStack stack = result.clone();
            stack.setAmount(toPlace);
            inv.setResult(stack);

            if (result.getAmount() > toPlace) {
                dropItems(furnace, result, result.getAmount() - toPlace);
            }
        } else if (current.isSimilar(result)) {
            int space = 64 - current.getAmount();
            int toAdd = Math.min(space, result.getAmount());
            current.setAmount(current.getAmount() + toAdd);
            inv.setResult(current);

            if (result.getAmount() > toAdd) {
                dropItems(furnace, result, result.getAmount() - toAdd);
            }
        } else {
            dropItems(furnace, result, result.getAmount());
        }
    }

    private void dropItems(Furnace furnace, ItemStack template, int amount) {
        ItemStack drop = template.clone();
        drop.setAmount(amount);
        furnace.getWorld().dropItemNaturally(furnace.getLocation().add(0.5, 1, 0.5), drop);
    }
}