package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.itemization.ItemTransformationPolicy;
import hu.taliann.icesmp.itemization.ItemTransformationPolicy.Classification;
import hu.taliann.icesmp.itemization.ItemTransformationPolicy.Decision;
import hu.taliann.icesmp.itemization.ItemTransformationPolicy.Transformation;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingTrimRecipe;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Paper/Folia adapter; all transformation and consumption decisions remain in ItemTransformationPolicy. */
public final class VanillaCraftingBoundaryListener implements Listener {

    private final ItemTransformationPolicy policy;
    private final MessageManager messages;
    private final Map<UUID, Long> feedback = new ConcurrentHashMap<>();

    public VanillaCraftingBoundaryListener(final ItemTransformationPolicy policy,
                                           final MessageManager messages) {
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
        this.messages = java.util.Objects.requireNonNull(messages, "messages");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(final PrepareItemCraftEvent event) {
        final Player player = player(event.getView().getPlayer());
        final Decision blocked = firstBlocked(event.getInventory().getMatrix(),
                Transformation.VANILLA_CRAFT_INPUT);
        final Decision output = blocked == null ? blockOutput(event.getInventory().getResult()) : null;
        if (blocked != null || output != null) {
            event.getInventory().setResult(null);
            notify(player, blocked == null ? output : blocked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(final CraftItemEvent event) {
        final Decision blocked = firstBlocked(event.getInventory().getMatrix(),
                Transformation.VANILLA_CRAFT_INPUT);
        final Decision output = blocked == null ? blockOutput(event.getCurrentItem()) : null;
        if (blocked != null || output != null) {
            event.setCancelled(true);
            notify(player(event.getWhoClicked()), blocked == null ? output : blocked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(final CrafterCraftEvent event) {
        final Inventory inventory = event.getBlock().getState() instanceof Crafter crafter
                ? crafter.getInventory() : null;
        final Decision blocked = inventory == null ? null
                : firstBlocked(inventory.getStorageContents(), Transformation.VANILLA_CRAFT_INPUT);
        final Decision output = blocked == null ? blockOutput(event.getResult()) : null;
        if (blocked != null || output != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCook(final BlockCookEvent event) {
        final Decision input = blocked(event.getSource(), Transformation.FURNACE_OR_CAMPFIRE);
        final Decision output = input == null ? blockOutput(event.getResult()) : null;
        if (input != null || output != null) event.setCancelled(true);
    }

    /** Furnace, smoker and blast-furnace fuel is protected at the actual consume event. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceFuel(final FurnaceBurnEvent event) {
        if (blocked(event.getFuel(), Transformation.VANILLA_CONSUMER_SINK) != null) {
            event.setCancelled(true);
        }
    }

    /** Hopper-fed or manually inserted brewing fuel is protected when the stand would consume it. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrewingFuel(final BrewingStandFuelEvent event) {
        if (blocked(event.getFuel(), Transformation.VANILLA_CONSUMER_SINK) != null) {
            event.setCancelled(true);
        }
    }

    /** Brewing ingredients are protected on the final brew transition, independent of insertion path. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(final BrewEvent event) {
        if (blocked(event.getContents().getIngredient(), Transformation.VANILLA_CONSUMER_SINK) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(final PrepareSmithingEvent event) {
        final SmithingInventory inventory = event.getInventory();
        final Transformation operation = inventory.getRecipe() instanceof SmithingTrimRecipe
                ? Transformation.ARMOR_TRIM : Transformation.SMITHING_UPGRADE;
        final Decision blocked = firstBlocked(new ItemStack[]{inventory.getInputTemplate(),
                inventory.getInputEquipment(), inventory.getInputMineral()}, operation);
        final Decision output = blocked == null ? blockOutput(event.getResult()) : null;
        if (blocked != null || output != null) {
            event.setResult(null);
            notify(player(event.getView().getPlayer()), blocked == null ? output : blocked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmith(final SmithItemEvent event) {
        final SmithingInventory inventory = event.getInventory();
        final Transformation operation = inventory.getRecipe() instanceof SmithingTrimRecipe
                ? Transformation.ARMOR_TRIM : Transformation.SMITHING_UPGRADE;
        final Decision blocked = firstBlocked(new ItemStack[]{inventory.getInputTemplate(),
                inventory.getInputEquipment(), inventory.getInputMineral()}, operation);
        final Decision output = blocked == null ? blockOutput(event.getCurrentItem()) : null;
        if (blocked != null || output != null) {
            event.setCancelled(true);
            notify(player(event.getWhoClicked()), blocked == null ? output : blocked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(final PrepareAnvilEvent event) {
        final AnvilInventory inventory = event.getInventory();
        final Transformation operation = anvilOperation(inventory);
        final Decision blocked = firstBlocked(inventory.getStorageContents(), operation);
        final Decision output = blocked == null ? blockOutput(event.getResult()) : null;
        if (blocked != null || output != null) {
            event.setResult(null);
            notify(player(event.getView().getPlayer()), blocked == null ? output : blocked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(final PrepareGrindstoneEvent event) {
        final Decision blocked = firstBlocked(event.getInventory().getStorageContents(),
                Transformation.GRINDSTONE);
        final Decision output = blocked == null ? blockOutput(event.getResult()) : null;
        if (blocked != null || output != null) {
            event.setResult(null);
            notify(player(event.getView().getPlayer()), blocked == null ? output : blocked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(final PrepareItemEnchantEvent event) {
        final Decision blocked = blocked(event.getItem(), Transformation.ENCHANTING_TABLE);
        if (blocked != null) {
            event.setCancelled(true);
            notify(event.getEnchanter(), blocked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(final EnchantItemEvent event) {
        final Decision blocked = blocked(event.getItem(), Transformation.ENCHANTING_TABLE);
        if (blocked != null) {
            event.setCancelled(true);
            notify(event.getEnchanter(), blocked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrade(final PlayerTradeEvent event) {
        final Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
        final Decision cost = top.getType() == InventoryType.MERCHANT
                ? firstBlocked(new ItemStack[]{top.getItem(0), top.getItem(1)},
                Transformation.VANILLA_CONSUMER_SINK) : null;
        final Decision output = cost == null ? blockOutput(event.getTrade().getResult()) : null;
        if (cost != null || output != null) {
            event.setCancelled(true);
            notify(event.getPlayer(), cost == null ? output : cost);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResultClick(final InventoryClickEvent event) {
        final Player player = player(event.getWhoClicked());
        final ItemStack keyboardItem = event.getClick().isKeyboardClick() && event.getHotbarButton() >= 0
                ? player == null ? null : player.getInventory().getItem(event.getHotbarButton()) : null;
        final boolean armorDestination = event.getSlotType() == InventoryType.SlotType.ARMOR;
        final boolean playerInventoryShiftEquip = event.isShiftClick()
                && event.getView().getTopInventory().getType() == InventoryType.CRAFTING;
        final Decision illegal = armorDestination
                ? firstIllegal(event.getCursor(), keyboardItem)
                : playerInventoryShiftEquip ? illegal(event.getCurrentItem()) : null;
        if (illegal != null) {
            event.setCancelled(true);
            notify(player, illegal);
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        final Inventory top = event.getView().getTopInventory();
        final Transformation operation = switch (top.getType()) {
            case CRAFTING, WORKBENCH -> Transformation.VANILLA_CRAFT_INPUT;
            case ANVIL -> anvilOperation((AnvilInventory) top);
            case SMITHING -> ((SmithingInventory) top).getRecipe() instanceof SmithingTrimRecipe
                    ? Transformation.ARMOR_TRIM : Transformation.SMITHING_UPGRADE;
            case GRINDSTONE -> Transformation.GRINDSTONE;
            case STONECUTTER -> Transformation.STONECUTTER;
            case MERCHANT -> Transformation.VILLAGER_TRADE;
            default -> null;
        };
        if (operation == null) return;
        final ItemStack[] inputs = top instanceof CraftingInventory crafting
                ? crafting.getMatrix() : top.getStorageContents();
        final Decision blocked = firstBlocked(inputs, operation);
        final Decision output = blocked == null ? blockOutput(event.getCurrentItem()) : null;
        if (blocked != null || output != null) {
            event.setCancelled(true);
            notify(player, blocked == null ? output : blocked);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(final InventoryDragEvent event) {
        final boolean armorDestination = event.getRawSlots().stream()
                .anyMatch(rawSlot -> event.getView().getSlotType(rawSlot) == InventoryType.SlotType.ARMOR);
        final Decision illegal = armorDestination ? illegal(event.getOldCursor()) : null;
        if (illegal != null) {
            event.setCancelled(true);
            notify(player(event.getWhoClicked()), illegal);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHeld(final PlayerItemHeldEvent event) {
        final Decision illegal = illegal(event.getPlayer().getInventory().getItem(event.getNewSlot()));
        if (illegal != null) {
            event.setCancelled(true);
            notify(event.getPlayer(), illegal);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(final PlayerSwapHandItemsEvent event) {
        final Decision illegal = firstIllegal(event.getMainHandItem(), event.getOffHandItem());
        if (illegal != null) {
            event.setCancelled(true);
            notify(event.getPlayer(), illegal);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        final Decision illegal = illegal(event.getItem());
        if (illegal != null) {
            event.setCancelled(true);
            notify(event.getPlayer(), illegal);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        final Decision illegal = illegal(player.getInventory().getItemInMainHand());
        if (illegal != null) {
            event.setCancelled(true);
            notify(player, illegal);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenseArmor(final BlockDispenseArmorEvent event) {
        if (illegal(event.getItem()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        feedback.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(final PlayerKickEvent event) {
        feedback.remove(event.getPlayer().getUniqueId());
    }

    private Decision blockOutput(final ItemStack output) {
        final Classification classified = policy.classify(output);
        if (classified.domain() != ItemTransformationPolicy.Domain.CANONICAL_MMO_GEAR) return null;
        final Decision decision = policy.decide(classified, Transformation.VANILLA_RECIPE_OUTPUT);
        return decision.directlyAllowed() ? null : decision;
    }

    private Decision blocked(final ItemStack item, final Transformation operation) {
        if (item == null || item.getType().isAir()) return null;
        final Classification classified = policy.classify(item);
        if (!classified.transformationProtected()) return null;
        final Decision decision = policy.decide(classified, operation);
        return decision.directlyAllowed() ? null : decision;
    }

    private Decision firstBlocked(final ItemStack[] items, final Transformation operation) {
        if (items == null) return null;
        for (final ItemStack item : items) {
            final Decision decision = blocked(item, operation);
            if (decision != null) return decision;
        }
        return null;
    }

    private Decision illegal(final ItemStack item) {
        final Classification classified = policy.classify(item);
        if (classified.domain() != ItemTransformationPolicy.Domain.CANONICAL_MMO_GEAR
                || classified.identityStatus() != hu.taliann.icesmp.itemization.ItemIdentityService.Status.VALID) {
            return null;
        }
        final String violation = policy.illegalCanonicalState(item);
        return violation.isBlank() ? null : new Decision(ItemTransformationPolicy.Action.DENY,
                "itemization-vanilla-boundary-illegal-state", violation);
    }

    private Decision firstIllegal(final ItemStack... items) {
        if (items == null) return null;
        return Arrays.stream(items).map(this::illegal).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }

    private static Transformation anvilOperation(final AnvilInventory inventory) {
        final ItemStack first = inventory.getFirstItem();
        final ItemStack second = inventory.getSecondItem();
        if (second == null || second.getType().isAir()) return Transformation.ANVIL_RENAME;
        if (second.getType() == org.bukkit.Material.ENCHANTED_BOOK) {
            return Transformation.ANVIL_ENCHANT_MERGE;
        }
        if (first != null && first.getType() == second.getType()) {
            return Transformation.ANVIL_ITEM_REPAIR;
        }
        return Transformation.ANVIL_MATERIAL_REPAIR;
    }

    private void notify(final Player player, final Decision decision) {
        if (player == null || decision == null) return;
        final long now = System.currentTimeMillis();
        final long cooldown = policy.rules().feedbackCooldownMillis();
        final Long previous = feedback.get(player.getUniqueId());
        if (previous != null && now - previous < cooldown) return;
        feedback.put(player.getUniqueId(), now);
        player.sendActionBar(messages.getMessage(decision.reasonKey(),
                "<red>Ezt az IceSMP felszerelést csak szakmai vagy kovácsolási művelettel lehet fejleszteni.</red>"));
    }

    private static Player player(final org.bukkit.entity.HumanEntity entity) {
        return entity instanceof Player player ? player : null;
    }
}
