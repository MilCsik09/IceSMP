package hu.taliann.icesmp.commands;

import hu.taliann.icesmp.gui.BestiaryHolder;
import hu.taliann.icesmp.managers.BestiaryManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.managers.TerritoryManager;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * B21 — /bestiarium: a krónikás-lajstrom áttekintő GUI-ja (csak olvasható).
 * Négy kategória-ikon mutatja a haladást (első-alkalom bejegyzések + ismert
 * összlétszám, ahol van); a részletes listát a lore-ban NEM soroljuk (400+
 * bejegyzés nem fér el) — a lajstrom lényege a számláló + a mérföldkövek.
 */
public final class BestiaryCommand implements BasicCommand {

    private final BestiaryManager bestiaryManager;
    private final ProfessionRecipeCatalog recipeCatalog;
    private final TerritoryManager territoryManager;
    private final MessageManager messageManager;

    public BestiaryCommand(final BestiaryManager bestiaryManager,
                           final ProfessionRecipeCatalog recipeCatalog,
                           final TerritoryManager territoryManager,
                           final MessageManager messageManager) {
        this.bestiaryManager = bestiaryManager;
        this.recipeCatalog = recipeCatalog;
        this.territoryManager = territoryManager;
        this.messageManager = messageManager;
    }

    @Override
    public void execute(final @NonNull CommandSourceStack commandSourceStack, final @NonNull String[] args) {
        if (!(commandSourceStack.getSender() instanceof Player player)) {
            commandSourceStack.getSender().sendMessage(messageManager.get(
                    "messages.player-only", "&cEzt a parancsot csak játékos használhatja."));
            return;
        }
        if (!bestiaryManager.isEnabled()) {
            player.sendMessage(messageManager.get("bestiary-disabled", "&cA bestiárium jelenleg nem elérhető."));
            return;
        }
        final BestiaryHolder holder = new BestiaryHolder(player.getUniqueId());
        final Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("📜 Bestiárium — a lajstromod"));
        holder.setInventory(inventory);
        inventory.setItem(10, icon(Material.IRON_SWORD, "Szörnyek",
                bestiaryManager.count(player, BestiaryManager.Category.MOBS), -1,
                "Megölt szörny-FAJOK — minden faj első", "elejtése új bejegyzés."));
        inventory.setItem(12, icon(Material.CRAFTING_TABLE, "Receptek",
                bestiaryManager.count(player, BestiaryManager.Category.RECIPES),
                recipeCatalog.allIds().size(),
                "Első craftok a recept-katalógusból.", "A céh számon tartja a munkád."));
        inventory.setItem(14, icon(Material.FILLED_MAP, "Territóriumok",
                bestiaryManager.count(player, BestiaryManager.Category.TERRITORIES), -1,
                "Bejárt territóriumok — az első", "határátlépés írja a lajstromot."));
        inventory.setItem(16, icon(Material.WITHER_SKELETON_SKULL, "Világbossok",
                bestiaryManager.count(player, BestiaryManager.Category.BOSSES), -1,
                "Legyőzött boss-archetípusok.", "A krónikások kedvence."));
        player.openInventory(inventory);
    }

    private ItemStack icon(final Material material, final String title, final int count,
                           final int total, final String... loreLines) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("📜 " + title, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        final java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Bejegyzések: " + count + (total > 0 ? " / " + total : ""),
                NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        for (final String line : loreLines) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public java.util.@NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack,
                                                         final @NonNull String[] args) {
        return List.of();
    }
}
