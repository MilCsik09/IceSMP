package hu.taliann.icesmp.gui;

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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/** Read-only native `/crates` list and reward preview. */
public final class CrateBrowserGUI {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final DecimalFormat PERCENT = new DecimalFormat("0.##");
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
        for (final String crateId : manager.crateIds()) {
            if (index >= CONTENT_SLOTS.length) {
                break;
            }
            final CrateManager.CrateDefinition definition = manager.definition(crateId);
            final int slot = CONTENT_SLOTS[index++];
            final boolean accessible = definition.enabled() && manager.canAccess(player, definition)
                    && definition.allowsWorld(player.getWorld().getName());
            final ItemStack icon = accessible
                    ? new ItemStack(definition.keyMaterial()) : new ItemStack(Material.BARRIER);
            final ItemMeta meta = icon.getItemMeta();
            meta.displayName(LEGACY.deserialize(TextUtil.color(definition.displayName()))
                    .decoration(TextDecoration.ITALIC, false));
            final List<Component> lore = new ArrayList<>();
            lore.add(GuiUtil.label("Kulcs ára", Component.text(
                    currencyManager.formatBalance(definition.keyPriceAmount()) + " "
                            + definition.keyPriceCurrency().getDisplayName(), NamedTextColor.WHITE)));
            lore.add(GuiUtil.label("Kulcs/nyitás", Component.text(definition.requiredKeys(), NamedTextColor.YELLOW)));
            lore.add(GuiUtil.label("Cooldown", Component.text(
                    definition.cooldownMillis() <= 0L ? "nincs" : definition.cooldownMillis() / 1000L + " mp",
                    NamedTextColor.AQUA)));
            lore.add(GuiUtil.grey(accessible ? "Kattints a jutalmak előnézetéhez."
                    : "Ebben a világban vagy ezzel a joggal nem elérhető."));
            meta.lore(lore);
            icon.setItemMeta(meta);
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
            final ItemStack icon = new ItemStack(reward.iconMaterial());
            final ItemMeta meta = icon.getItemMeta();
            meta.displayName(LEGACY.deserialize(TextUtil.color(odds.description()))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    GuiUtil.label("Esély", Component.text(PERCENT.format(odds.percent()) + "%", NamedTextColor.YELLOW)),
                    GuiUtil.label("Típus", Component.text(reward.type().name(), NamedTextColor.AQUA)),
                    GuiUtil.grey("Csak előnézet — innen tárgy nem vehető ki.")));
            icon.setItemMeta(meta);
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
}
