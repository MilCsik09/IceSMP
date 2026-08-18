package hu.taliann.icesmp.gui;

import hu.taliann.icesmp.itemization.ItemInstance;
import hu.taliann.icesmp.itemization.ItemMutationCoordinator;
import hu.taliann.icesmp.itemization.ItemStatCatalog;
import hu.taliann.icesmp.itemization.RuneMutationPolicy;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Vanilla/resource-pack optional preview and confirmation surface for item lifecycle mutations. */
public final class ItemForgeGUI implements Listener {

    private static final int SLOT_ITEM = 13;
    private static final int SLOT_REROLL = 22;
    private static final int SLOT_ASCEND = 24;
    private static final int SLOT_SALVAGE = 26;
    private static final int SLOT_AMPLIFIER = 30;
    private static final int SLOT_STABILITY = 32;
    private static final int SLOT_RUNE_1 = 9;
    private static final int SLOT_RUNE_2 = 10;
    private static final int SLOT_RUNE_REMOVE = 15;
    private static final int SLOT_RUNE_REPLACE = 16;
    private static final List<Integer> STAT_SLOTS = List.of(
            18, 19, 20, 21, 23, 27, 28, 29, 31, 33, 34,
            35, 36, 37, 38, 39, 40, 41, 42, 43, 44);
    private final ItemMutationCoordinator coordinator;
    private final MessageManager messages;

    public ItemForgeGUI(final ItemMutationCoordinator coordinator, final MessageManager messages) {
        this.coordinator = coordinator;
        this.messages = messages;
    }

    public void open(final Player player) {
        open(player, "", false, false, player.getInventory().getHeldItemSlot(), 0);
    }

    private void open(final Player player, final String lockedStat,
                      final boolean amplifier, final boolean stability,
                      final int targetSlot, final int runeSocket) {
        final ItemForgeHolder holder = new ItemForgeHolder(player.getUniqueId(), lockedStat,
                amplifier, stability, targetSlot, runeSocket);
        final Inventory inventory = Bukkit.createInventory(holder, 45,
                messages.getMessage("itemization-forge-title", "<dark_aqua>Item műhely</dark_aqua>"));
        holder.inventory(inventory);
        final ItemStack held = player.getInventory().getItem(targetSlot);
        inventory.setItem(SLOT_ITEM, held == null || held.getType().isAir()
                ? button(Material.BARRIER, "Nincs tárgy a főkézben", List.of()) : held.clone());

        final ItemMutationCoordinator.Options options = new ItemMutationCoordinator.Options(
                lockedStat, amplifier, stability);
        final ItemMutationCoordinator.Preview reroll = coordinator.preview(player,
                ItemMutationCoordinator.Operation.REROLL, options);
        final ItemMutationCoordinator.Preview ascend = coordinator.preview(player,
                ItemMutationCoordinator.Operation.ASCEND, options);
        final ItemMutationCoordinator.Preview salvage = coordinator.preview(player,
                ItemMutationCoordinator.Operation.SALVAGE, options);
        inventory.setItem(SLOT_REROLL, operationButton(Material.ANVIL, "Újrakovácsolás", reroll, true));
        inventory.setItem(SLOT_ASCEND, operationButton(Material.NETHER_STAR, "Felemelkedés", ascend, true));
        inventory.setItem(SLOT_SALVAGE, operationButton(Material.BLAST_FURNACE, "Bontás", salvage, true));
        inventory.setItem(SLOT_AMPLIFIER, button(amplifier ? Material.LIME_DYE : Material.GRAY_DYE,
                "Quality Amplifier: " + (amplifier ? "BE" : "KI"),
                List.of("A következő reroll minimum qualityjét emeli.")));
        inventory.setItem(SLOT_STABILITY, button(stability ? Material.LIME_DYE : Material.GRAY_DYE,
                "Stability Seal: " + (stability ? "BE" : "KI"),
                List.of("A reroll nem növeli a következő költséglépcsőt.")));

        final ItemMutationCoordinator.RunePreview remove = coordinator.previewRune(player,
                targetSlot, RuneMutationPolicy.Action.REMOVE, runeSocket, "");
        final ItemMutationCoordinator.RunePreview replace = coordinator.previewRune(player,
                targetSlot, RuneMutationPolicy.Action.REPLACE, runeSocket, "");
        final ItemInstance runeItem = remove.instance() != null ? remove.instance() : replace.instance();
        for (int socket = 0; socket < 2; socket++) {
            final boolean occupied = runeItem != null && socket < runeItem.runes().size();
            final String rune = occupied ? runeItem.runes().get(socket) : "üres";
            inventory.setItem(socket == 0 ? SLOT_RUNE_1 : SLOT_RUNE_2,
                    button(socket == runeSocket ? Material.LAPIS_LAZULI : Material.AMETHYST_SHARD,
                            (socket + 1) + ". rúnafoglalat: " + rune,
                            List.of(socket == runeSocket ? "KIJELÖLVE" : "Katt: foglalat kijelölése")));
        }
        inventory.setItem(SLOT_RUNE_REMOVE, runeButton(Material.SHEARS, "Rúna eltávolítása", remove));
        inventory.setItem(SLOT_RUNE_REPLACE, runeButton(Material.SMITHING_TABLE, "Rúna cseréje", replace));

        if (reroll.instance() != null && reroll.template() != null) {
            int statIndex = 0;
            for (final Map.Entry<String, ItemInstance.Roll> roll : reroll.instance().rolls().entrySet()) {
                if (statIndex >= STAT_SLOTS.size()) break;
                inventory.setItem(STAT_SLOTS.get(statIndex++), button(
                        roll.getKey().equals(lockedStat) ? Material.LAPIS_LAZULI : Material.PAPER,
                        ItemStatCatalog.require(roll.getKey()).displayName(),
                        List.of("Aktuális quality: " + Math.round(roll.getValue().quality() * 100.0D) + "%",
                                roll.getKey().equals(lockedStat) ? "LOCKOLVA" : "Katt: Stat Lock")));
            }
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ItemForgeHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(holder.ownerId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        final int slot = event.getSlot();
        if (slot == SLOT_AMPLIFIER) {
            open(player, holder.lockedStat(), !holder.amplifier(), holder.stability(),
                    holder.targetSlot(), holder.runeSocket());
            return;
        }
        if (slot == SLOT_STABILITY) {
            open(player, holder.lockedStat(), holder.amplifier(), !holder.stability(),
                    holder.targetSlot(), holder.runeSocket());
            return;
        }
        if (slot == SLOT_RUNE_1 || slot == SLOT_RUNE_2) {
            open(player, holder.lockedStat(), holder.amplifier(), holder.stability(),
                    holder.targetSlot(), slot == SLOT_RUNE_1 ? 0 : 1);
            return;
        }
        final int statIndex = STAT_SLOTS.indexOf(slot);
        if (statIndex >= 0) {
            final ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                final ItemMutationCoordinator.Preview preview = coordinator.preview(player,
                        ItemMutationCoordinator.Operation.REROLL,
                        new ItemMutationCoordinator.Options("", holder.amplifier(), holder.stability()));
                if (preview.instance() != null) {
                    final List<String> stats = new ArrayList<>(preview.instance().rolls().keySet());
                    if (statIndex < stats.size()) {
                        final String stat = stats.get(statIndex);
                        open(player, stat.equals(holder.lockedStat()) ? "" : stat,
                                holder.amplifier(), holder.stability(), holder.targetSlot(), holder.runeSocket());
                    }
                }
            }
            return;
        }
        final RuneMutationPolicy.Action runeAction = switch (slot) {
            case SLOT_RUNE_REMOVE -> RuneMutationPolicy.Action.REMOVE;
            case SLOT_RUNE_REPLACE -> RuneMutationPolicy.Action.REPLACE;
            default -> null;
        };
        if (runeAction != null) {
            executeRune(player, holder, runeAction, event.isShiftClick());
            return;
        }
        final ItemMutationCoordinator.Operation operation = switch (slot) {
            case SLOT_REROLL -> ItemMutationCoordinator.Operation.REROLL;
            case SLOT_ASCEND -> ItemMutationCoordinator.Operation.ASCEND;
            case SLOT_SALVAGE -> ItemMutationCoordinator.Operation.SALVAGE;
            default -> null;
        };
        if (operation == null) return;
        if (!event.isShiftClick()) {
            player.sendMessage(messages.get("itemization-confirm-shift",
                    "&eA művelet és a feltüntetett költség megerősítéséhez SHIFT+katt szükséges."));
            return;
        }
        final ItemMutationCoordinator.Options options = new ItemMutationCoordinator.Options(
                holder.lockedStat(), holder.amplifier(), holder.stability());
        final ItemMutationCoordinator.Preview preview = coordinator.preview(player, operation, options);
        if (!preview.allowed()) {
            player.sendMessage(messages.get(preview.errorKey(),
                    "&cA művelet nem hajtható végre; ellenőrizd az itemet és a költséget."));
            return;
        }
        player.closeInventory();
        coordinator.execute(player, operation, options, outcome -> {
            if (outcome.success() && operation == ItemMutationCoordinator.Operation.SALVAGE
                    && preview.template() != null) {
                hu.taliann.icesmp.professions.ProfessionEconomyTelemetry.global()
                        .recordSalvage(preview.template().armorFamily());
            }
            player.sendMessage(messages.get(outcome.messageKey(), outcome.success()
                    ? "&aA tárgyművelet tartósan lezárult."
                    : "&cA tárgyművelet biztonságosan meghiúsult."));
            if (player.isOnline()) open(player);
        });
    }

    private void executeRune(final Player player, final ItemForgeHolder holder,
                             final RuneMutationPolicy.Action action, final boolean confirmed) {
        if (!confirmed) {
            player.sendMessage(messages.get("itemization-confirm-shift",
                    "&eA művelet és a feltüntetett költség megerősítéséhez SHIFT+katt szükséges."));
            return;
        }
        final ItemMutationCoordinator.RunePreview preview = coordinator.previewRune(player,
                holder.targetSlot(), action, holder.runeSocket(), "");
        if (!preview.allowed()) {
            player.sendMessage(messages.get(preview.errorKey(),
                    "&cA rúnaművelet nem hajtható végre biztonságosan."));
            return;
        }
        player.closeInventory();
        coordinator.executeRuneMutation(player, holder.targetSlot(), action,
                holder.runeSocket(), preview.newRune(), outcome -> {
                    player.sendMessage(messages.get(outcome.messageKey(), outcome.success()
                            ? "&aA rúnaművelet tartósan lezárult."
                            : "&cA rúnaművelet biztonságosan meghiúsult."));
                    if (player.isOnline()) open(player);
                });
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ItemForgeHolder) {
            event.setCancelled(true);
        }
    }

    private static ItemStack operationButton(final Material material, final String name,
                                             final ItemMutationCoordinator.Preview preview,
                                             final boolean irreversible) {
        final ArrayList<String> lore = new ArrayList<>();
        lore.add(preview.allowed() ? preview.resultSummary() : "Nem elérhető: " + preview.errorKey());
        if (!preview.costs().isEmpty()) {
            lore.add("Költség:");
            preview.costs().forEach((id, amount) -> lore.add("• " + id + " ×" + amount));
        }
        if (irreversible) lore.add("SHIFT+katt: végrehajtás (irreverzibilis)");
        return button(preview.allowed() ? material : Material.BARRIER, name, lore);
    }

    private static ItemStack runeButton(final Material material, final String name,
                                        final ItemMutationCoordinator.RunePreview preview) {
        final ArrayList<String> lore = new ArrayList<>();
        lore.add(preview.allowed() ? preview.resultSummary() : "Nem elérhető: " + preview.errorKey());
        if (!preview.costs().isEmpty()) {
            lore.add("Költség:");
            preview.costs().forEach((id, amount) -> lore.add("• " + id + " ×" + amount));
        }
        lore.add("Régi rúna policy: megsemmisül");
        lore.add("SHIFT+katt: végrehajtás (irreverzibilis)");
        return button(preview.allowed() ? material : Material.BARRIER, name, lore);
    }

    private static ItemStack button(final Material material, final String name,
                                    final List<String> loreLines) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(loreLines.stream().<Component>map(line -> Component.text(line,
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
