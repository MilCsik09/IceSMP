package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.crates.CrateFormatting;
import hu.taliann.icesmp.managers.CrateManager;
import hu.taliann.icesmp.managers.CurrencyManager;
import hu.taliann.icesmp.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Read-only native `/crates` list and reward preview. */
public final class CrateBrowserGUI {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private CrateBrowserGUI() {
    }

    public static void openList(final Player player, final CrateManager manager,
                                final CurrencyManager currencyManager) {
        final CrateBrowserHolder holder = new CrateBrowserHolder(player.getUniqueId(),
                CrateBrowserHolder.View.LIST, null);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("IceSMP ládák", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inventory);
        GuiUtil.fill(inventory);

        int index = 0;
        for (final String crateId : manager.accessibleCrateIds(player)) {
            if (index >= CONTENT_SLOTS.length) {
                break;
            }
            final CrateManager.CrateDefinition definition = manager.definition(crateId);
            final int slot = CONTENT_SLOTS[index++];
            final ItemStack icon = new ItemStack(definition.keyMaterial());
            final ItemMeta meta = icon.getItemMeta();
            meta.displayName(LEGACY.deserialize(TextUtil.color(definition.displayName()))
                    .decoration(TextDecoration.ITALIC, false));
            final List<Component> lore = new ArrayList<>();
            lore.add(GuiUtil.label("Kulcs ára", Component.text(
                    currencyManager.formatBalance(definition.keyPriceAmount()) + " "
                            + definition.keyPriceCurrency().getDisplayName(), NamedTextColor.WHITE)));
            lore.add(GuiUtil.label("Kulcs/nyitás", Component.text(definition.requiredKeys(), NamedTextColor.YELLOW)));
            lore.add(GuiUtil.label("Töltési idő", Component.text(
                    definition.cooldownMillis() <= 0L ? "nincs" : definition.cooldownMillis() / 1000L + " mp",
                    NamedTextColor.AQUA)));
            lore.add(GuiUtil.grey("Kattints a jutalmak előnézetéhez."));
            meta.lore(lore);
            icon.setItemMeta(meta);
            hu.taliann.icesmp.items.ItemDataFactory.applyItemModel(icon, definition.keyItemModel());
            inventory.setItem(slot, icon);
            holder.bind(slot, "PREVIEW:" + crateId);
        }
        inventory.setItem(49, GuiUtil.icon(Material.BARRIER,
                Component.text("Bezárás", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()));
        holder.bind(49, "CLOSE");
        player.openInventory(inventory);
        GuiUtil.sound(player, GuiUtil.GuiSound.OPEN);
    }

    public static void openPreview(final Player player, final CrateManager manager, final String crateId) {
        final CrateManager.CrateDefinition definition = manager.definition(crateId);
        if (definition == null) {
            return;
        }
        final CrateBrowserHolder holder = new CrateBrowserHolder(player.getUniqueId(),
                CrateBrowserHolder.View.PREVIEW, crateId);
        final Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("Jutalmak: ", NamedTextColor.GOLD)
                        .append(LEGACY.deserialize(TextUtil.color(definition.displayName())))
                        .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inventory);
        GuiUtil.fill(inventory);

        int index = 0;
        for (final CrateManager.RewardOdds odds : manager.rewardOdds(crateId)) {
            if (index >= CONTENT_SLOTS.length) {
                break;
            }
            final int slot = CONTENT_SLOTS[index++];
            final CrateManager.RewardEntry reward = odds.reward();
            final ItemStack icon = manager.rewardPreview(reward);
            final ItemMeta meta = icon.getItemMeta();
            meta.displayName(LEGACY.deserialize(TextUtil.color(odds.description()))
                    .decoration(TextDecoration.ITALIC, false));
            final List<Component> lore = meta.lore() == null
                    ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(GuiUtil.label("Esély", Component.text(
                    CrateFormatting.decimal(odds.percent()) + "%", NamedTextColor.YELLOW)));
            lore.add(GuiUtil.label("Típus", Component.text(rewardTypeLabel(reward.type()), NamedTextColor.AQUA)));
            lore.add(GuiUtil.grey("Csak előnézet — innen tárgy nem vehető ki."));
            meta.lore(lore);
            icon.setItemMeta(meta);
            manager.applyRewardPreviewAppearance(icon, reward);
            inventory.setItem(slot, icon);
        }
        inventory.setItem(45, GuiUtil.icon(Material.ARROW,
                Component.text("Vissza", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false), List.of()));
        holder.bind(45, "BACK");
        inventory.setItem(49, GuiUtil.icon(Material.BARRIER,
                Component.text("Bezárás", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false), List.of()));
        holder.bind(49, "CLOSE");
        player.openInventory(inventory);
        GuiUtil.sound(player, GuiUtil.GuiSound.PAGE);
    }

    private static String rewardTypeLabel(final CrateManager.RewardType type) {
        return switch (type) {
            case ITEM -> "Tárgy";
            case COMMAND -> "Különleges jutalom";
            case CURRENCY -> "Valuta";
            case UNIQUE_ITEM -> "Egyedi tárgy";
            case TEMPLATE -> "Felszerelés";
            case RECIPE_ITEM -> "Recepttárgy";
            case BLUEPRINT -> "Tervrajz";
            case RANDOM_BLUEPRINT -> "Véletlen tervrajz";
            case CRATE_KEY -> "Ládakulcs";
        };
    }
}
