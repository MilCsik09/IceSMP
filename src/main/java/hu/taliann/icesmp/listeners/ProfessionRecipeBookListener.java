package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.ProfessionRecipeGUI;
import hu.taliann.icesmp.gui.ProfessionRecipeHolder;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.ItemRarityService;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
import hu.taliann.icesmp.pve.EquippedCombatPowerService;
import hu.taliann.icesmp.utils.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Profession recipe-book execution adapter. Eligibility, specialization arithmetic and inventory
 * capacity are all derived from the same immutable ProfessionEffectiveCraftPlan consumed by the
 * transaction. All inventory touches stay on the clicking player's owner thread.
 */
public final class ProfessionRecipeBookListener implements Listener {

    private volatile hu.taliann.icesmp.managers.BestiaryManager bestiaryManager;
    private volatile hu.taliann.icesmp.managers.JobManager jobManager;
    private volatile hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyGoal;

    public void setJobManager(final hu.taliann.icesmp.managers.JobManager jobManager) {
        this.jobManager = jobManager;
    }

    public void setBestiaryManager(final hu.taliann.icesmp.managers.BestiaryManager bestiaryManager) {
        this.bestiaryManager = bestiaryManager;
    }

    public void setWeeklyGoal(final hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyGoal) {
        this.weeklyGoal = weeklyGoal;
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ProfessionManager professionManager;
    private final ProfessionRecipeCatalog catalog;
    private final ItemRarityService affixService;
    private final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials;
    private final JavaPlugin plugin;
    private final MessageManager messageManager;
    private final FactionManager factionManager;
    private final hu.taliann.icesmp.managers.ConfigManager configManager;
    private final NamespacedKey signatureKey;
    private final NamespacedKey craftedByKey;
    private final NamespacedKey craftedAtKey;
    private volatile hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService;
    private final hu.taliann.icesmp.professions.ProfessionCraftTransaction craftTransaction;

    public void setItemIdentityService(
            final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService) {
        this.itemIdentityService = itemIdentityService;
    }

    public ProfessionRecipeBookListener(final JavaPlugin plugin,
                                        final ProfessionManager professionManager,
                                        final ProfessionRecipeCatalog catalog,
                                        final ItemRarityService affixService,
                                        final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials,
                                        final MessageManager messageManager,
                                        final FactionManager factionManager,
                                        final hu.taliann.icesmp.managers.ConfigManager configManager) {
        this.plugin = plugin;
        this.professionManager = professionManager;
        this.catalog = catalog;
        this.affixService = affixService;
        this.uniqueMaterials = uniqueMaterials;
        this.messageManager = messageManager;
        this.factionManager = factionManager;
        this.configManager = configManager;
        this.signatureKey = new NamespacedKey(plugin, "signature_item");
        this.craftedByKey = new NamespacedKey(plugin, "crafted_by");
        this.craftedAtKey = new NamespacedKey(plugin, "crafted_at");
        this.craftTransaction = new hu.taliann.icesmp.professions.ProfessionCraftTransaction(uniqueMaterials);
    }

    public void open(final Player player) {
        ProfessionRecipeGUI.open(player, 0, professionManager, catalog, uniqueMaterials);
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ProfessionRecipeHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(holder.getOwnerId())
                || event.getClickedInventory() != event.getView().getTopInventory()) return;

        final String action = holder.recipeAt(event.getSlot());
        if (action == null) return;
        switch (action) {
            case "CLOSE" -> player.closeInventory();
            case "PREV" -> ProfessionRecipeGUI.open(player, holder.getPage() - 1,
                    professionManager, catalog, uniqueMaterials);
            case "NEXT" -> ProfessionRecipeGUI.open(player, holder.getPage() + 1,
                    professionManager, catalog, uniqueMaterials);
            default -> craft(player, action, holder.getPage(), event.isShiftClick());
        }
    }

    private void craft(final Player player, final String recipeId,
                       final int page, final boolean batchRequested) {
        final ProfessionRecipeCatalog.Recipe recipe = catalog.get(recipeId);
        if (recipe == null) return;
        if (!professionManager.hasProfession(player, recipe.profession())) {
            player.sendMessage(messageManager.get("profession-recipe-not-practiced",
                    "&cEzt a szakmát jelenleg nem gyakorlod."));
            return;
        }
        if (professionManager.getLevel(player, recipe.profession()) < recipe.level()) {
            player.sendMessage(messageManager.get("profession-recipe-level",
                    "&cEhhez a recepthez magasabb szakma-szint kell."));
            return;
        }
        if (recipe.blueprint() && !professionManager.hasLearnedRecipe(player, recipe.id())) {
            player.sendMessage(messageManager.get("profession-recipe-not-learned",
                    "&cEhhez a recepthez előbb meg kell szerezned a tervrajzot."));
            return;
        }
        if (recipe.faction() != null
                && !factionManager.isMember(player.getUniqueId(), recipe.faction())) {
            player.sendMessage(messageManager.get("profession-recipe-faction",
                    "&cEzt a receptet csak a(z) &f%s&c frakció mesterei készíthetik.",
                    recipe.faction().getDisplayName() + " (" + recipe.faction().getFullName() + ")"));
            return;
        }
        final hu.taliann.icesmp.managers.JobManager jobRef = jobManager;
        if (recipe.job() != null && jobRef != null) {
            final hu.taliann.icesmp.data.JobType required =
                    hu.taliann.icesmp.data.JobType.fromId(recipe.job());
            if (required != null && jobRef.getPrimaryJob(player) != required) {
                player.sendMessage(messageManager.get("profession-recipe-job",
                        "&cEzt a receptet csak a(z) &f%s&c kaszt mesterei készíthetik.", recipe.job()));
                return;
            }
        }

        final ProfessionRecipeCatalog.EconomyMetadata economy = catalog.economy(recipe.id());
        final int batches = batchRequested && economy.batchable()
                && recipe.templateId() == null && recipe.affixTier() == null
                ? Math.min(5, economy.batchLimit()) : 1;
        final hu.taliann.icesmp.professions.ProfessionSpecializationEconomyPolicy.Effect specialization =
                hu.taliann.icesmp.professions.ProfessionSpecializationEconomyPolicy.effectFor(player, recipe);
        final hu.taliann.icesmp.professions.ProfessionEffectiveCraftPlan plan =
                hu.taliann.icesmp.professions.ProfessionEffectiveCraftPlan.of(
                        recipe, specialization, batches);
        if (!hasIngredients(player, plan)) {
            player.sendMessage(messageManager.get("profession-recipe-missing",
                    "&cNincs meg minden effektív hozzávaló ehhez az adaghoz."));
            return;
        }

        final java.util.UUID rootOperationId = java.util.UUID.randomUUID();
        final List<ItemStack> outputs = new ArrayList<>();
        int masterworkCount = 0;
        for (int index = 0; index < batches; index++) {
            final java.util.UUID operationId = derivedOperationId(rootOperationId, index);
            final ItemStack result = buildResult(player, recipe, true, operationId);
            if (result == null) return;
            outputs.add(result);
            final hu.taliann.icesmp.itemization.ItemIdentityService identity = itemIdentityService;
            if (identity != null) {
                final var inspection = identity.inspect(result);
                if (inspection.readable() && inspection.instance() != null
                        && inspection.instance().origin().masterwork()) masterworkCount++;
            }
        }

        final var preflight = craftTransaction.preflight(player, plan, outputs);
        if (!preflight.applied()) {
            sendCraftPlanFailure(player, preflight.status());
            return;
        }
        final var transaction = craftTransaction.apply(player, plan, outputs);
        if (!transaction.applied()) {
            sendCraftPlanFailure(player, transaction.status());
            return;
        }

        EquippedCombatPowerService.refreshAfterMutation(player);
        for (final ItemStack output : outputs) awardMasterworkIfEligible(player, output);
        if (masterworkCount > 0) {
            hu.taliann.icesmp.managers.AdvancementService.award(player, "masterwork");
        }

        final int playerLevel = professionManager.getLevel(player, recipe.profession());
        final int craftBase = Math.max(0,
                configManager.getInt("professions.xp.recipe-craft-base", 8));
        final int craftPerLevel = Math.max(0,
                configManager.getInt("professions.xp.recipe-craft-per-level", 2));
        final int greyAfter = Math.max(2,
                configManager.getInt("professions.xp.recipe-craft-grey-after", 20));
        int craftXp = craftBase + recipe.level() * craftPerLevel;
        final int levelDiff = playerLevel - recipe.level();
        if (levelDiff >= greyAfter) craftXp = 0;
        else if (levelDiff >= greyAfter / 2) craftXp /= 2;
        if (craftXp > 0) {
            final int bulkCap = Math.max(1,
                    configManager.getInt("professions.xp.bulk-event-cap", 16));
            final int durableCraftXp = Math.multiplyExact(craftXp, Math.min(batches, bulkCap));
            professionManager.addXpFor(player, recipe.profession(), durableCraftXp)
                    .whenComplete((change, failure) -> {
                        if (failure == null && change != null && change.changed()) {
                            professionManager.runOnOwnerThread(player, () -> {
                                final hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyRef = weeklyGoal;
                                if (weeklyRef != null && player.isOnline()) {
                                    weeklyRef.add(player, recipe.profession(), durableCraftXp);
                                }
                            });
                        }
                        if (failure == null) return;
                        plugin.getLogger().severe("Craft XP PlayerProfile commit failed for "
                                + player.getUniqueId() + " / " + recipe.id() + ": "
                                + failure.getMessage());
                        professionManager.runOnOwnerThread(player, () -> {
                            if (player.isOnline()) {
                                player.sendMessage(messageManager.get(
                                        "profession-craft-xp-storage-failed",
                                        "&eA tárgy elkészült, de a szakma-XP mentése meghiúsult; az adminok értesítést kaptak."));
                            }
                        });
                    });
        }
        final hu.taliann.icesmp.managers.BestiaryManager bestiaryRef = bestiaryManager;
        if (bestiaryRef != null) {
            bestiaryRef.record(player,
                    hu.taliann.icesmp.managers.BestiaryManager.Category.RECIPES, recipe.id());
        }
        hu.taliann.icesmp.professions.ProfessionEconomyTelemetry.global().recordCraft(
                recipe, batches, masterworkCount, recipe.level() >= 40 || recipe.blueprint());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6F, 1.2F);
        player.sendMessage(messageManager.get("profession-recipe-crafted",
                "&aElkészítetted: &e%s",
                recipe.displayName() + (batches > 1 ? " ×" + batches : "")));
        ProfessionRecipeGUI.open(player, page, professionManager, catalog, uniqueMaterials);
    }

    private void sendCraftPlanFailure(
            final Player player,
            final hu.taliann.icesmp.professions.ProfessionCraftTransaction.Status status) {
        if (status == hu.taliann.icesmp.professions.ProfessionCraftTransaction.Status.INVENTORY_FULL) {
            player.sendMessage(messageManager.get("profession-recipe-inventory-full",
                    "&cNincs elég hely a hátizsákodban; semmi nem fogyott el."));
        } else if (status == hu.taliann.icesmp.professions.ProfessionCraftTransaction.Status.PERSISTENCE_FAILED) {
            player.sendMessage(messageManager.get("profession-recipe-persistence-failed",
                    "&cA craft mentése meghiúsult; az inventory visszaállt."));
        } else {
            player.sendMessage(messageManager.get("profession-recipe-missing",
                    "&cAz effektív CraftPlan már nem teljesíthető; semmi nem fogyott el."));
        }
    }

    private boolean hasIngredients(
            final Player player,
            final hu.taliann.icesmp.professions.ProfessionEffectiveCraftPlan plan) {
        for (final Map.Entry<Material, Integer> entry : plan.materialInputs().entrySet()) {
            if (hu.taliann.icesmp.utils.PlainIngredients.count(
                    player, entry.getKey(), uniqueMaterials) < entry.getValue()) return false;
        }
        for (final Map.Entry<String, Integer> entry : plan.uniqueInputs().entrySet()) {
            if (countUnique(player, entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    private int countUnique(final Player player, final String uniqueId) {
        int count = 0;
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item != null && uniqueId.equals(uniqueMaterials.idOf(item))) count += item.getAmount();
        }
        return count;
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ProfessionRecipeHolder) {
            event.setCancelled(true);
        }
    }

    public ItemStack buildResult(final Player player, final ProfessionRecipeCatalog.Recipe recipe) {
        return buildResult(player, recipe, true, java.util.UUID.randomUUID());
    }

    /** Builds a crate reward without firing an advancement before the world reveal finishes. */
    public ItemStack buildDeferredReward(final Player player,
                                         final ProfessionRecipeCatalog.Recipe recipe) {
        return buildResult(player, recipe, false, java.util.UUID.randomUUID());
    }

    private ItemStack buildResult(final Player player, final ProfessionRecipeCatalog.Recipe recipe,
                                  final boolean professionCraft, final java.util.UUID operationId) {
        if (recipe.templateId() != null) {
            final hu.taliann.icesmp.itemization.ItemIdentityService identity = itemIdentityService;
            if (identity == null) {
                plugin.getLogger().severe("Canonical profession craft unavailable: ItemIdentityService missing");
                return null;
            }
            try {
                final hu.taliann.icesmp.itemization.ItemTemplate template = identity.template(recipe.templateId());
                final long now = System.currentTimeMillis();
                final hu.taliann.icesmp.itemization.ItemInstance instance;
                if (professionCraft) {
                    final var decision = hu.taliann.icesmp.professions.ProfessionCraftQualityPolicy.decide(
                            operationId, professionManager.getLevel(player, recipe.profession()),
                            recipe.blueprint(), recipe.masterwork(),
                            hu.taliann.icesmp.professions.ProfessionCraftQualityPolicy.from(configManager));
                    instance = identity.rollCraftedInstance(template, operationId,
                            player.getUniqueId(), player.getName(), recipe.profession().getId(),
                            locationSnapshot(player), decision.masterwork(), now, decision.minimumQuality(),
                            decision.qualitySource());
                } else {
                    instance = identity.rollInstance(template, operationId,
                            "crate:authored", recipe.id(), null, "", now,
                            () -> java.util.concurrent.ThreadLocalRandom.current().nextDouble());
                }
                return identity.render(template, instance);
            } catch (final RuntimeException invalid) {
                plugin.getLogger().severe("Canonical profession result failed for " + recipe.id()
                        + ": " + invalid.getMessage());
                return null;
            }
        }
        ItemStack result = recipe.uniqueResult() != null
                ? uniqueMaterials.create(recipe.uniqueResult(), recipe.resultAmount())
                : new ItemStack(recipe.result(), recipe.resultAmount());
        if (result == null) return null;

        if (recipe.uniqueResult() == null && recipe.lore() != null && !recipe.lore().isEmpty()) {
            final ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                meta.displayName(LEGACY.deserialize(recipe.displayName())
                        .colorIfAbsent(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                final List<Component> loreLines = new ArrayList<>();
                for (final String line : recipe.lore()) {
                    loreLines.add(LEGACY.deserialize(line)
                            .colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(loreLines);
                result.setItemMeta(meta);
            }
        }
        if (recipe.signature() != null && result.getItemMeta() != null) {
            final ItemMeta sigMeta = result.getItemMeta();
            sigMeta.getPersistentDataContainer().set(signatureKey, PersistentDataType.STRING, recipe.signature());
            if (SignatureItemListener.TUZKOPO.equals(recipe.signature())) {
                final int level = Math.max(0, Math.min(3,
                        configManager.getInt("signature.tuzkopo.quick-charge-level", 2)));
                if (level > 0) sigMeta.addEnchant(org.bukkit.enchantments.Enchantment.QUICK_CHARGE, level, true);
            }
            result.setItemMeta(sigMeta);
        }
        final String enchantSpec = configManager.getString(
                "profession-recipes." + recipe.id() + ".result.enchant", "");
        if (!enchantSpec.isBlank()) {
            final int levelSplit = enchantSpec.lastIndexOf(':');
            final String enchantKey = levelSplit > 0 ? enchantSpec.substring(0, levelSplit) : enchantSpec;
            int enchantLevel = 1;
            try {
                enchantLevel = levelSplit > 0 ? Integer.parseInt(enchantSpec.substring(levelSplit + 1)) : 1;
            } catch (final NumberFormatException ignored) { }
            org.bukkit.enchantments.Enchantment enchant = null;
            try {
                final org.bukkit.NamespacedKey parsedKey = org.bukkit.NamespacedKey.fromString(enchantKey);
                if (parsedKey != null) {
                    enchant = io.papermc.paper.registry.RegistryAccess.registryAccess()
                            .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(parsedKey);
                }
            } catch (final Exception ignored) { }
            final ItemMeta enchMeta = result.getItemMeta();
            if (enchant != null && enchMeta != null) {
                if (enchMeta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta storage) {
                    storage.addStoredEnchant(enchant, enchantLevel, true);
                } else enchMeta.addEnchant(enchant, enchantLevel, true);
                result.setItemMeta(enchMeta);
            }
        }

        final List<String> potionSpecs = configManager.getConfiguration()
                .getStringList("profession-recipes." + recipe.id() + ".result.potion-effects");
        if (!potionSpecs.isEmpty()) {
            hu.taliann.icesmp.items.ItemDataFactory.applyPotionEffects(result, potionSpecs,
                    configManager.getString("profession-recipes." + recipe.id() + ".result.potion-color", ""));
        }

        final boolean stackableComponent = recipe.uniqueResult() != null
                && recipe.affixTier() == null && result.getMaxStackSize() > 1;
        if (configManager.getBoolean("crafted-by.enabled", true) && !stackableComponent
                && (recipe.affixTier() != null || (recipe.lore() != null && !recipe.lore().isEmpty()))) {
            final ItemMeta craftedMeta = result.getItemMeta();
            if (craftedMeta != null) {
                craftedMeta.getPersistentDataContainer().set(craftedByKey, PersistentDataType.STRING, player.getName());
                if (result.getMaxStackSize() == 1) {
                    craftedMeta.getPersistentDataContainer().set(craftedAtKey,
                            PersistentDataType.LONG, System.currentTimeMillis());
                }
                final List<Component> craftedLore = craftedMeta.lore() == null
                        ? new ArrayList<>() : new ArrayList<>(craftedMeta.lore());
                craftedLore.add(Component.text("Készítette: " + player.getName(), NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true));
                craftedMeta.lore(craftedLore);
                result.setItemMeta(craftedMeta);
            }
        }

        if (recipe.affixTier() != null && recipe.resultAmount() == 1) {
            result = affixService.roll(result, recipe.affixTier());
        }
        final List<String> attrSpecs = configManager.getConfiguration()
                .getStringList("profession-recipes." + recipe.id() + ".result.attributes");
        if (hu.taliann.icesmp.items.ItemDataFactory.applyAttributeModifiers(result, attrSpecs)) {
            hu.taliann.icesmp.items.ItemDataFactory.hideAttributeTooltip(result);
            final String rolledRarity = affixService.rarityIdOf(result);
            if (rolledRarity != null) {
                hu.taliann.icesmp.items.ItemDataFactory.applyRarity(result,
                        hu.taliann.icesmp.items.ItemDataFactory.vanillaRarityOf(rolledRarity));
            }
        }
        if (recipe.signature() != null) {
            hu.taliann.icesmp.items.ItemDataFactory.applySignatureFoodConsumable(result, recipe.signature());
        }
        final org.bukkit.configuration.ConfigurationSection consumableSection = configManager.getConfiguration()
                .getConfigurationSection("profession-recipes." + recipe.id() + ".result.consumable");
        if (consumableSection != null) {
            hu.taliann.icesmp.items.ItemDataFactory.applyRecipeConsumable(result, consumableSection);
        }
        if (recipe.uniqueResult() != null
                && !uniqueMaterials.applyPresentation(result, recipe.uniqueResult())) return null;

        final String presentationBase = "profession-recipes." + recipe.id() + ".result.";
        final String itemModel = configManager.getString(presentationBase + "item-model", "");
        final String equipmentAsset = configManager.getString(presentationBase + "equipment-asset", "");
        final hu.taliann.icesmp.items.WearablePresentation.Result presentation =
                hu.taliann.icesmp.items.WearablePresentation.applyWearablePresentation(
                        result, itemModel, equipmentAsset);
        if (!equipmentAsset.isBlank() && !presentation.equipmentApplied()) {
            plugin.getLogger().warning(presentationBase + "equipment-asset: '" + equipmentAsset
                    + "' cannot be applied (" + presentation.equipmentStatus() + ")");
            return null;
        }

        final String rarityId = configManager.getString(
                "profession-recipes." + recipe.id() + ".result.rarity", "");
        if (!rarityId.isBlank()) {
            hu.taliann.icesmp.items.ItemDataFactory.applyRarity(result,
                    hu.taliann.icesmp.items.ItemDataFactory.vanillaRarityOf(rarityId));
        }
        final String remainderName = configManager.getString(
                "profession-recipes." + recipe.id() + ".result.use-remainder", "");
        if (!remainderName.isBlank()) {
            ItemStack remainder = null;
            if (remainderName.toLowerCase(Locale.ROOT).startsWith("unique:")) {
                remainder = uniqueMaterials.create(remainderName.substring("unique:".length()), 1);
            } else {
                final Material remainderMaterial = Material.matchMaterial(remainderName);
                if (remainderMaterial != null) remainder = new ItemStack(remainderMaterial);
            }
            if (remainder == null) {
                plugin.getLogger().warning("profession-recipes." + recipe.id()
                        + ".result.use-remainder: feloldhatatlan \"" + remainderName + "\" - kihagyva.");
            } else {
                hu.taliann.icesmp.items.ItemDataFactory.applyUseRemainder(result, remainder);
            }
        }
        final org.bukkit.configuration.ConfigurationSection cooldownSection = configManager.getConfiguration()
                .getConfigurationSection("profession-recipes." + recipe.id() + ".result.use-cooldown");
        if (cooldownSection != null) {
            hu.taliann.icesmp.items.ItemDataFactory.applyUseCooldownGroup(result,
                    cooldownSection.getString("group", recipe.id()),
                    (float) cooldownSection.getDouble("seconds", 1.0D));
        }
        return result;
    }

    private static java.util.UUID derivedOperationId(final java.util.UUID root, final int index) {
        if (index == 0) return root;
        final String source = root + ":" + index;
        return java.util.UUID.nameUUIDFromBytes(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String locationSnapshot(final Player player) {
        final org.bukkit.Location location = player.getLocation();
        return location.getWorld().getName() + ':' + location.getBlockX() + ','
                + location.getBlockY() + ',' + location.getBlockZ();
    }

    /** Fires only after the caller crossed its durable delivery boundary. */
    public void awardMasterworkIfEligible(final Player player, final ItemStack result) {
        if (player == null || result == null || affixService == null) return;
        final String rolled = affixService.rarityIdOf(result);
        if ("legendas".equals(rolled) || "ereklye".equals(rolled)) {
            hu.taliann.icesmp.managers.AdvancementService.award(player, "masterwork");
        }
    }
}
