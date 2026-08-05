package hu.taliann.icesmp.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Bővíthető admin config-főmenü. A korábbi 36 slotos nézet pontosan 21 kategóriánál
 * megtelt; ez a 54 slotos gyökér további kategóriákat enged anélkül, hogy a zárógombot
 * vagy más csempét felülírná.
 */
public final class ConfigMenuRootGUI {

    private static final int[] CATEGORY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
            46, 47, 48, 49, 50, 51, 52
    };

    private ConfigMenuRootGUI() {
    }

    public static int categoryCapacity() {
        OperationalConfigSchemaGuard.validate();
        AdvancedConfigSchemaGuard.validate();
        return CATEGORY_SLOTS.length;
    }

    public static void openRoot(final Player player) {
        OperationalConfigSchemaGuard.validate();
        AdvancedConfigSchemaGuard.validate();
        final int categoryCount = ConfigMenuGUI.CATEGORIES.size() + 4;
        if (categoryCount > CATEGORY_SLOTS.length) {
            throw new IllegalStateException("Az admin config-főmenü kategóriakapacitása elfogyott: "
                    + categoryCount + "/" + CATEGORY_SLOTS.length);
        }

        final ConfigMenuHolder holder = new ConfigMenuHolder(player.getUniqueId(), null);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("⚙ IceSMP Config", NamedTextColor.DARK_AQUA));
        holder.setInventory(inventory);

        int index = 0;
        for (final ConfigMenuGUI.Category category : ConfigMenuGUI.CATEGORIES.values()) {
            final int slot = CATEGORY_SLOTS[index++];
            inventory.setItem(slot, tile(category.icon(), "&b" + category.title(),
                    List.of("&7" + category.entries().size() + " kulcs",
                            "&eKattints a megnyitáshoz")));
            holder.bind(slot, "CAT:" + category.id());
        }

        final int regenSlot = CATEGORY_SLOTS[index++];
        inventory.setItem(regenSlot, tile(Material.TNT,
                "&bRobbanás és világregeneráció",
                List.of("&7" + BlockRegenConfigMenuGUI.entryCount() + " kulcs",
                        "&7Zónák, claimek, időzítés, effektek",
                        "&eKattints a megnyitáshoz")));
        holder.bind(regenSlot, BlockRegenConfigMenuGUI.ROOT_ACTION);

        final int operationalSlot = CATEGORY_SLOTS[index++];
        inventory.setItem(operationalSlot, tile(Material.REDSTONE_TORCH,
                "&bÜzemeltetés és finomhangolás",
                List.of("&7" + OperationalConfigMenuGUI.categoryCount() + " alkategória",
                        "&7" + OperationalConfigMenuGUI.entryCount() + " élő kulcs",
                        "&7HUD, AFK, petek, piac és moderáció",
                        "&eKattints a megnyitáshoz")));
        holder.bind(operationalSlot, OperationalConfigMenuGUI.ROOT_ACTION);

        final int serverWorldSlot = CATEGORY_SLOTS[index++];
        inventory.setItem(serverWorldSlot, tile(Material.COMMAND_BLOCK,
                "&bSzerver, világ és szöveges értékek",
                List.of("&7" + ServerWorldConfigMenuGUI.entryCount() + " élő kulcs",
                        "&7Gamerule, világ-driver, HUD-szöveg, listák",
                        "&7Biztonságos chat-alapú String/lista editor",
                        "&eKattints a megnyitáshoz")));
        holder.bind(serverWorldSlot, ServerWorldConfigMenuGUI.ROOT_ACTION);

        final int crateSlot = CATEGORY_SLOTS[index];
        inventory.setItem(crateSlot, tile(Material.CHEST,
                "&bNatív crate-editor",
                List.of("&7Crate-alapbeállítások és rewardok",
                        "&7Strukturált, copy-on-write jutalomszerkesztés",
                        "&7Lapozható dinamikus crate-lista",
                        "&eKattints a megnyitáshoz")));
        holder.bind(crateSlot, CrateConfigMenuGUI.ROOT_ACTION);

        inventory.setItem(53, tile(Material.BARRIER, "&cBezárás", List.of()));
        holder.bind(53, "CLOSE");
        player.openInventory(inventory);
    }

    private static ItemStack tile(final Material material, final String name,
                                  final List<String> loreLines) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(name)
                    .decoration(TextDecoration.ITALIC, false));
            final List<Component> lore = new ArrayList<>();
            for (final String line : loreLines) {
                lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(line)
                        .colorIfAbsent(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
