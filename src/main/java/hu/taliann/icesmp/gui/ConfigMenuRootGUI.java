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

/** Staged, extensible admin config root with a dedicated bottom control row. */
public final class ConfigMenuRootGUI {

    private static final int[] CATEGORY_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private ConfigMenuRootGUI() { }

    public static int categoryCapacity() {
        OperationalConfigSchemaGuard.validate();
        AdvancedConfigSchemaGuard.validate();
        return CATEGORY_SLOTS.length;
    }

    public static void openRoot(final Player player) { openRoot(player, null); }

    public static void openRoot(final Player player, final ConfigEditSession session) {
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
                    List.of("&7" + category.entries().size() + " kulcs", "&eKattints a staged szerkesztéshez")));
            holder.bind(slot, "CAT:" + category.id());
        }
        int slot = CATEGORY_SLOTS[index++];
        inventory.setItem(slot, tile(Material.TNT, "&bRobbanás és világregeneráció",
                List.of("&7" + BlockRegenConfigMenuGUI.entryCount() + " kulcs",
                        "&7Zónák, claimek, időzítés, effektek", "&eKattints a staged szerkesztéshez")));
        holder.bind(slot, BlockRegenConfigMenuGUI.ROOT_ACTION);

        slot = CATEGORY_SLOTS[index++];
        inventory.setItem(slot, tile(Material.REDSTONE_TORCH, "&bÜzemeltetés és finomhangolás",
                List.of("&7" + TransactionalOperationalConfigMenuGUI.categoryCount() + " alkategória",
                        "&7" + TransactionalOperationalConfigMenuGUI.entryCount() + " élő kulcs",
                        "&7HUD, AFK, petek és piac", "&eKattints a staged szerkesztéshez")));
        holder.bind(slot, OperationalConfigMenuGUI.ROOT_ACTION);

        slot = CATEGORY_SLOTS[index++];
        inventory.setItem(slot, tile(Material.COMMAND_BLOCK, "&bSzerver, világ és szöveges értékek",
                List.of("&7" + ServerWorldConfigMenuGUI.entryCount() + " élő kulcs",
                        "&7Gamerule, világ-driver, HUD-szöveg, listák",
                        "&7Privát chat-alapú String/lista editor", "&eKattints a staged szerkesztéshez")));
        holder.bind(slot, ServerWorldConfigMenuGUI.ROOT_ACTION);

        slot = CATEGORY_SLOTS[index];
        inventory.setItem(slot, tile(Material.CHEST, "&bNatív crate-editor",
                List.of("&7Crate-alapbeállítások és rewardok",
                        "&7Strukturált, copy-on-write jutalomszerkesztés",
                        "&7A teljes lista csak Mentéskor publikálódik", "&eKattints a staged szerkesztéshez")));
        holder.bind(slot, CrateConfigMenuGUI.ROOT_ACTION);

        ConfigMenuControls.add(inventory, holder, session, false);
        player.openInventory(inventory);
    }

    private static ItemStack tile(final Material material, final String name,
                                  final List<String> loreLines) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(name).decoration(TextDecoration.ITALIC, false));
            final List<Component> lore = new ArrayList<>();
            for (final String line : loreLines) {
                lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(line).colorIfAbsent(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
