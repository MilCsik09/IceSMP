package hu.taliann.icesmp.items;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Factory for the siege cannon: a craftable, PDC-tagged item that only works
 * during a raid. Right-clicking it fires a destructive blast at the target
 * (SiegeWeaponListener).
 */
@SuppressWarnings("deprecation")
public final class SiegeWeaponFactory {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final NamespacedKey siegeKey;
    private final NamespacedKey recipeKey;

    public SiegeWeaponFactory(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.siegeKey = new NamespacedKey(plugin, "is_siege_weapon");
        this.recipeKey = new NamespacedKey(plugin, "siege_cannon");
    }

    public ItemStack create(final int amount) {
        final ItemStack itemStack = new ItemStack(Material.TNT_MINECART, Math.max(1, amount));
        final ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(MINI_MESSAGE.deserialize("<red><bold>Ostromágyú</bold></red>").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                MINI_MESSAGE.deserialize("<gray>Csak raid alatt működik.</gray>").decoration(TextDecoration.ITALIC, false),
                MINI_MESSAGE.deserialize("<gray>Jobb katt: <white>pusztító lövés a célpontra</white></gray>").decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(siegeKey, PersistentDataType.BOOLEAN, true);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public boolean isSiegeWeapon(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }

        return Boolean.TRUE.equals(itemStack.getItemMeta().getPersistentDataContainer().get(siegeKey, PersistentDataType.BOOLEAN));
    }

    /**
     * Registers the crafting recipe (TNT core, iron frame, blaze-powder fuse).
     * Safe to call once on enable.
     */
    public void registerRecipe() {
        final ShapedRecipe recipe = new ShapedRecipe(recipeKey, create(1));
        recipe.shape("IBI", "ITI", "III");
        recipe.setIngredient('I', Material.IRON_BLOCK);
        recipe.setIngredient('B', Material.BLAZE_POWDER);
        recipe.setIngredient('T', Material.TNT);
        try {
            plugin.getServer().addRecipe(recipe);
        } catch (final IllegalStateException exception) {
            // Recipe already registered (reload) — ignore.
        }
    }
}
