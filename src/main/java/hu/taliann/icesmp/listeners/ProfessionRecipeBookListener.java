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
 * Click handling and crafting for the profession recipe-book GUI. A left click on a craftable
 * recipe verifies the profession level, the learned state (for blueprint recipes) and the
 * ingredients, then consumes them and grants the result — rolled through {@link ItemRarityService}
 * on the recipe's {@code affix-tier} when set, so crafted gear comes out unique. All inventory
 * touches are on the clicking player's own region thread (Folia-safe).
 */
public final class ProfessionRecipeBookListener implements Listener {

    /** B21 — setter-injektált: az első craft bestiárium-bejegyzése. */
    private volatile hu.taliann.icesmp.managers.BestiaryManager bestiaryManager;

    /** E7 — setter-injektált: kaszt-zárt receptek (job:) ellenőrzése. */
    private volatile hu.taliann.icesmp.managers.JobManager jobManager;

    public void setJobManager(final hu.taliann.icesmp.managers.JobManager jobManager) {
        this.jobManager = jobManager;
    }

    public void setBestiaryManager(final hu.taliann.icesmp.managers.BestiaryManager bestiaryManager) {
        this.bestiaryManager = bestiaryManager;
    }

    /** Setter-injektált: a recept-craft XP-je is tölti a heti céh-célt (mint a gyűjtő-XP). */
    private volatile hu.taliann.icesmp.managers.ProfessionWeeklyGoalManager weeklyGoal;

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
    /** PDC tag on crafted signature items (K2/K3) — the perk listener recognises items by this id. */
    private final NamespacedKey signatureKey;
    /** I14 — „a mester keze alól kikerülő mű a nevét is viseli" (kódex VIII.): készítő + időpont. */
    private final NamespacedKey craftedByKey;
    private final NamespacedKey craftedAtKey;
    private volatile hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService;
    private final hu.taliann.icesmp.professions.ProfessionCraftTransaction craftTransaction;

    public void setItemIdentityService(
            final hu.taliann.icesmp.itemization.ItemIdentityService itemIdentityService) {
        this.itemIdentityService = itemIdentityService;
    }

    public ProfessionRecipeBookListener(final JavaPlugin plugin,
                                        final ProfessionManager professionManager, final ProfessionRecipeCatalog catalog,
                                        final ItemRarityService affixService,
                                        final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterials,
                                        final MessageManager messageManager, final FactionManager factionManager,
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

    /** Opens the recipe book for a player at the first page. */
    public void open(final Player player) {
        ProfessionRecipeGUI.open(player, 0, professionManager, catalog, uniqueMaterials);
    }

    @EventHandler
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ProfessionRecipeHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(holder.getOwnerId())
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        final String action = holder.recipeAt(event.getSlot());
        if (action == null) {
            return;
        }
        switch (action) {
            case "CLOSE" -> player.closeInventory();
            case "PREV" -> ProfessionRecipeGUI.open(player, holder.getPage() - 1, professionManager, catalog, uniqueMaterials);
            case "NEXT" -> ProfessionRecipeGUI.open(player, holder.getPage() + 1, professionManager, catalog, uniqueMaterials);
            default -> craft(player, action, holder.getPage(), event.isShiftClick());
        }
    }

    private void craft(final Player player, final String recipeId, final int page, final boolean batchRequested) {
        final ProfessionRecipeCatalog.Recipe recipe = catalog.get(recipeId);
        if (recipe == null) {
            return;
        }
        // A GUI csak az aktív szakmák receptjeit listázza, de a jogosultságot minden mutációs
        // útnak MAGÁNAK kell ellenőriznie: a szintet a profil megőrzi szakmaváltás után is,
        // így egy nyitva maradt GUI-ból a régi szakma receptjei tovább craftolhatók lennének.
        if (!professionManager.hasProfession(player, recipe.profession())) {
            player.sendMessage(messageManager.get("profession-recipe-not-practiced",
                    "&cEzt a szakmát jelenleg nem gyakorlod."));
            return;
        }
        if (professionManager.getLevel(player, recipe.profession()) < recipe.level()) {
            player.sendMessage(messageManager.get("profession-recipe-level", "&cEhhez a recepthez magasabb szakma-szint kell."));
            return;
        }
        if (recipe.blueprint() && !professionManager.hasLearnedRecipe(player, recipe.id())) {
            player.sendMessage(messageManager.get("profession-recipe-not-learned", "&cEhhez a recepthez előbb meg kell szerezned a tervrajzot."));
            return;
        }
        // Signature (frakció-kötött) receptek: csak a megfelelő frakció mesterei készíthetik.
        // ConcurrentHashMap-olvasás — szál-biztos a játékos régió-szálán.
        if (recipe.faction() != null && !factionManager.isMember(player.getUniqueId(), recipe.faction())) {
            player.sendMessage(messageManager.get("profession-recipe-faction",
                    "&cEzt a receptet csak a(z) &f%s&c frakció mesterei készíthetik.",
                    recipe.faction().getDisplayName() + " (" + recipe.faction().getFullName() + ")"));
            return;
        }
        // Kaszt-zárt recept (job:): csak a megadott kaszt olvashatja fel sikerrel.
        final hu.taliann.icesmp.managers.JobManager jobRef = jobManager;
        if (recipe.job() != null && jobRef != null) {
            final hu.taliann.icesmp.data.JobType required = hu.taliann.icesmp.data.JobType.fromId(recipe.job());
            if (required != null && jobRef.getPrimaryJob(player) != required) {
                player.sendMessage(messageManager.get("profession-recipe-job",
                        "&cEzt a receptet csak a(z) &f%s&c kaszt mesterei készíthetik.", recipe.job()));
                return;
            }
        }
        final ProfessionRecipeCatalog.EconomyMetadata economy = catalog.economy(recipe.id());
        final int batches = batchRequested && economy.batchable() && recipe.templateId() == null
                && recipe.affixTier() == null ? Math.min(5, economy.batchLimit()) : 1;
        if (!hasIngredients(player, recipe)) {
            player.sendMessage(messageManager.get("profession-recipe-missing", "&cNincs meg minden hozzávaló ehhez a recepthez."));
            return;
        }
        final java.util.UUID rootOperationId = java.util.UUID.randomUUID();
        final java.util.List<ItemStack> outputs = new java.util.ArrayList<>();
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
        final var transaction = craftTransaction.apply(player, recipe, batches, outputs);
        if (!transaction.applied()) {
            if (transaction.status() == hu.taliann.icesmp.professions.ProfessionCraftTransaction.Status.INVENTORY_FULL) {
                player.sendMessage(messageManager.get("profession-recipe-inventory-full",
                        "&cNincs elég hely a hátizsákodban; semmi nem fogyott el."));
            } else {
                player.sendMessage(messageManager.get("profession-recipe-missing",
                        "&cNincs meg minden hozzávaló ehhez az adaghoz; semmi nem fogyott el."));
            }
            return;
        }
        EquippedCombatPowerService.refreshAfterMutation(player);
        // WoW-stílusú skill-up: a craft szakma-XP-t ad — a szintedhez közeli recept a teljes
        // értéket, a rég kinőtt („szürke”) recept semmit (fele-út: fél XP). Élő kulcsok.
        final int playerLevel = professionManager.getLevel(player, recipe.profession());
        final int craftBase = Math.max(0, configManager.getInt("professions.xp.recipe-craft-base", 8));
        final int craftPerLevel = Math.max(0, configManager.getInt("professions.xp.recipe-craft-per-level", 2));
        final int greyAfter = Math.max(2, configManager.getInt("professions.xp.recipe-craft-grey-after", 20));
        int craftXp = craftBase + recipe.level() * craftPerLevel;
        final int levelDiff = playerLevel - recipe.level();
        if (levelDiff >= greyAfter) {
            craftXp = 0;
        } else if (levelDiff >= greyAfter / 2) {
            craftXp /= 2;
        }
        if (craftXp > 0) {
            final int bulkCap = Math.max(1, configManager.getInt("professions.xp.bulk-event-cap", 16));
            final int durableCraftXp = Math.multiplyExact(craftXp, Math.min(batches, bulkCap));
            professionManager.addXpFor(player, recipe.profession(), durableCraftXp)
                    .whenComplete((change, failure) -> {
                        // A heti céh-cél CSAK valódi, tartósan jóváírt XP-re tölthet — a
                        // gyűjtő-XP útjával azonos szabály (ProfessionXpListener).
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
                                player.sendMessage(messageManager.get("profession-craft-xp-storage-failed",
                                        "&eA tárgy elkészült, de a szakma-XP mentése meghiúsult; az adminok értesítést kaptak."));
                            }
                        });
                    });
        }
        // A recept első elkészítése lajstrom-bejegyzés.
        final hu.taliann.icesmp.managers.BestiaryManager bestiaryRef = bestiaryManager;
        if (bestiaryRef != null) {
            bestiaryRef.record(player, hu.taliann.icesmp.managers.BestiaryManager.Category.RECIPES, recipe.id());
        }
        hu.taliann.icesmp.professions.ProfessionEconomyTelemetry.global().recordCraft(
                recipe, batches, masterworkCount, recipe.level() >= 40 || recipe.blueprint());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6F, 1.2F);
        player.sendMessage(messageManager.get("profession-recipe-crafted", "&aElkészítetted: &e%s",
                recipe.displayName() + (batches > 1 ? " ×" + batches : "")));
        // Refresh so the ingredient counts / craftable states update.
        ProfessionRecipeGUI.open(player, page, professionManager, catalog, uniqueMaterials);
    }

    private boolean hasIngredients(final Player player, final ProfessionRecipeCatalog.Recipe recipe) {
        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {
            if (hu.taliann.icesmp.utils.PlainIngredients.count(
                    player, entry.getKey(), uniqueMaterials) < entry.getValue()) {
                return false;
            }
        }
        for (final Map.Entry<String, Integer> entry : recipe.uniqueIngredients().entrySet()) {
            if (countUnique(player, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private int countUnique(final Player player, final String uniqueId) {
        int count = 0;
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item != null && uniqueId.equals(uniqueMaterials.idOf(item))) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void consumeUnique(final Player player, final ProfessionRecipeCatalog.Recipe recipe) {
        for (final Map.Entry<String, Integer> entry : recipe.uniqueIngredients().entrySet()) {
            int remaining = entry.getValue();
            final ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
                final ItemStack item = contents[slot];
                if (item == null || !entry.getKey().equals(uniqueMaterials.idOf(item))) {
                    continue;
                }
                final int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
            }
        }
    }

    @EventHandler
    public void onDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ProfessionRecipeHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * A recept EREDMÉNY-tárgyának felépítése a teljes stamp-lánccal (név/lore →
     * signature-PDC + Quick Charge + custom enchant → result.enchant → crafted-by →
     * affix-roll). A craft-út mellett az admin item-adó parancs (/iceitem) is ezt
     * hívja, így a parancsból adott tárgy bitre azonos a craftolttal.
     * A hívó szála a cél-játékos szála legyen (a crafted-by a nevét bélyegzi).
     *
     * @return a kész tárgy, vagy null (ismeretlen unique-eredmény / hibás presentation-config)
     */
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
                final hu.taliann.icesmp.itemization.ItemTemplate template =
                        identity.template(recipe.templateId());
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
                final ItemStack rendered = identity.render(template, instance);
                if (professionCraft && instance.origin().masterwork()) {
                    hu.taliann.icesmp.managers.AdvancementService.award(player, "masterwork");
                }
                return rendered;
            } catch (final RuntimeException invalid) {
                plugin.getLogger().severe("Canonical profession result failed for " + recipe.id()
                        + ": " + invalid.getMessage());
                return null;
            }
        }
        ItemStack result = recipe.uniqueResult() != null
                ? uniqueMaterials.create(recipe.uniqueResult(), recipe.resultAmount())
                : new ItemStack(recipe.result(), recipe.resultAmount());
        if (result == null) {
            return null;
        }
        // Named prestige items (gear / tome / special consumable): stamp the designed name + lore so the
        // crafted item matches the recipe book and the mob-loot naming model. Bulk results carry no lore
        // and stay vanilla + stackable. Unique materials already carry their own name/lore from the factory.
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
        // Signature perk-tag: a perk listener a PDC-id alapján ismeri fel a tárgyat. A roll
        // ELŐTT kerül fel, mert a roll klónja a PDC-t is viszi.
        if (recipe.signature() != null && result.getItemMeta() != null) {
            final ItemMeta sigMeta = result.getItemMeta();
            sigMeta.getPersistentDataContainer().set(signatureKey, PersistentDataType.STRING, recipe.signature());
            // Tűzköpő: a „+felhúzási sebesség" fele a vanília Quick Charge-on át.
            if (SignatureItemListener.TUZKOPO.equals(recipe.signature())) {
                final int level = Math.max(0, Math.min(3,
                        configManager.getInt("signature.tuzkopo.quick-charge-level", 2)));
                if (level > 0) {
                    sigMeta.addEnchant(org.bukkit.enchantments.Enchantment.QUICK_CHARGE, level, true);
                }
            }
            // Bootstrap-regisztrált signature-enchant (IceSMPBootstrap): a lore-név valódi
            // enchant-sorként jelenik meg a tooltipben; a Fagypáncél/Főnixtoll egyben
            // iskola-counter is. Globális + itemenkénti kapcsoló (élőben olvasva).
            if (configManager.getBoolean("signature.custom-enchants.enabled", true)
                    && configManager.getBoolean("signature.custom-enchants.items." + recipe.signature(), true)) {
                final net.kyori.adventure.key.Key enchantKey =
                        hu.taliann.icesmp.items.SignatureEnchantKeys.BY_SIGNATURE.get(recipe.signature());
                if (enchantKey != null) {
                    final org.bukkit.enchantments.Enchantment enchant =
                            io.papermc.paper.registry.RegistryAccess.registryAccess()
                                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                                    .get(org.bukkit.NamespacedKey.fromString(enchantKey.asString()));
                    if (enchant != null) {
                        sigMeta.addEnchant(enchant, 1, true);
                    }
                }
            }
            result.setItemMeta(sigMeta);
        }
        // Recept-oldali enchant (result.enchant: "<kulcs>:<szint>", pl. "icesmp:runavert:1"):
        // enchantelt könyvnél stored-enchantként, egyébként rendes enchantként kerül fel —
        // így receptből adható a bootstrap-regisztrált enchantok könyv-formája (üllőhöz).
        final String enchantSpec = configManager.getString(
                "profession-recipes." + recipe.id() + ".result.enchant", "");
        if (!enchantSpec.isBlank()) {
            final int levelSplit = enchantSpec.lastIndexOf(':');
            final String enchantKey = levelSplit > 0 ? enchantSpec.substring(0, levelSplit) : enchantSpec;
            int enchantLevel = 1;
            try {
                enchantLevel = levelSplit > 0 ? Integer.parseInt(enchantSpec.substring(levelSplit + 1)) : 1;
            } catch (final NumberFormatException ignored) {
                // Hibás szint a configban — 1-es szinttel megyünk tovább.
            }
            // Hibás kulcs a configban (rossz formátum/ismeretlen enchant) nem törheti a craftot.
            org.bukkit.enchantments.Enchantment enchant = null;
            try {
                final org.bukkit.NamespacedKey parsedKey = org.bukkit.NamespacedKey.fromString(enchantKey);
                if (parsedKey != null) {
                    enchant = io.papermc.paper.registry.RegistryAccess.registryAccess()
                            .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                            .get(parsedKey);
                }
            } catch (final Exception exception) {
                // Ismeretlen/érvénytelen enchant-kulcs — enchant nélkül megy tovább a craft.
            }
            final ItemMeta enchMeta = result.getItemMeta();
            if (enchant != null && enchMeta != null) {
                if (enchMeta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta storage) {
                    storage.addStoredEnchant(enchant, enchantLevel, true);
                } else {
                    enchMeta.addEnchant(enchant, enchantLevel, true);
                }
                result.setItemMeta(enchMeta);
            }
        }

        // Recept-oldali főzethatás (result.potion-effects): a főzet-eredmény valódi custom
        // effekteket kap, így a vanília dobás/terület/időtartam kezeli — meta-művelet, ezért
        // a data-komponens-blokk ELŐTT fut.
        final List<String> potionSpecs = configManager.getConfiguration()
                .getStringList("profession-recipes." + recipe.id() + ".result.potion-effects");
        if (!potionSpecs.isEmpty()) {
            hu.taliann.icesmp.items.ItemDataFactory.applyPotionEffects(result, potionSpecs,
                    configManager.getString("profession-recipes." + recipe.id() + ".result.potion-color", ""));
        }

        // „Készítette: X" (kódex VIII.: a mester keze alól kikerülő mű a nevét is viseli):
        // a NEVES/gear eredmények PDC-ben és lore-sorban viszik a készítő nevét — a piacon is
        // megmarad, márkajelzésként. Bulk (lore nélküli, stackelhető) eredményre nem kerül,
        // hogy a stackelést ne törje. A roll ELŐTT fut (a roll a lore alá fűzi az affixokat).
        //
        // A STACKELHETŐ unique-ALKATRÉSZ is kimarad (affix nélkül): ezekből ugyanaz a tárgy más
        // úton is a játékoshoz kerülhet (pl. USE_REMAINDER-ként visszakapott üres kupa), és a
        // bélyeg a két példányt összeférhetetlenné tenné — két külön kupa-kupac állna a
        // hátizsákban ugyanabból a tárgyból.
        final boolean stackableComponent = recipe.uniqueResult() != null
                && recipe.affixTier() == null && result.getMaxStackSize() > 1;
        if (configManager.getBoolean("crafted-by.enabled", true) && !stackableComponent
                && (recipe.affixTier() != null || (recipe.lore() != null && !recipe.lore().isEmpty()))) {
            final ItemMeta craftedMeta = result.getItemMeta();
            if (craftedMeta != null) {
                craftedMeta.getPersistentDataContainer().set(craftedByKey, PersistentDataType.STRING, player.getName());
                // Időbélyeg CSAK nem-stackelhető (gear) eredményre: a milliszekundumos crafted_at
                // a stackelhető (pl. lore-os étel) tételek külön craft-adagjait örökre
                // összeférhetetlenné tenné — a név (crafted_by) stackelés-barát, az marad.
                if (result.getMaxStackSize() == 1) {
                    craftedMeta.getPersistentDataContainer().set(craftedAtKey, PersistentDataType.LONG, System.currentTimeMillis());
                }
                final List<Component> craftedLore = craftedMeta.lore() == null
                        ? new ArrayList<>() : new ArrayList<>(craftedMeta.lore());
                craftedLore.add(Component.text("Készítette: " + player.getName(),
                                net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, true));
                craftedMeta.lore(craftedLore);
                result.setItemMeta(craftedMeta);
            }
        }

        // Roll a unique quality + affixes for single-item gear results (crafted tier). The roll keeps the
        // stamped display name (prefixing the rarity) and appends the affix lines below the lore.
        if (recipe.affixTier() != null && recipe.resultAmount() == 1) {
            result = affixService.roll(result, recipe.affixTier());
        }
        // Explicit, determinisztikus attribútum-módosítók (result.attributes) — az affix-roll UTÁN,
        // hogy a kettő összeadódjon. A stat saját lore-sorként jelenik meg, ezért a vanília
        // attribútum-tooltipet elrejtjük (a roll affixnál már megtette; itt a nem-affix esetre kell).
        final List<String> attrSpecs = configManager.getConfiguration()
                .getStringList("profession-recipes." + recipe.id() + ".result.attributes");
        if (hu.taliann.icesmp.items.ItemDataFactory.applyAttributeModifiers(result, attrSpecs)) {
            hu.taliann.icesmp.items.ItemDataFactory.hideAttributeTooltip(result);
            // Az applyAttributeModifiers meta-round-tripje a roll() által feltett RARITY
            // data-komponenst is eldobta — a rollolt fokot vissza kell tenni, különben a
            // vanília felület a Material alapértelmezett színfokát mutatja.
            final String rolledRarity = affixService.rarityIdOf(result);
            if (rolledRarity != null) {
                hu.taliann.icesmp.items.ItemDataFactory.applyRarity(result,
                        hu.taliann.icesmp.items.ItemDataFactory.vanillaRarityOf(rolledRarity));
            }
        }
        // P7 data-komponensek UTOLSÓnak (minden setItemMeta után). A signature-étel
        // CONSUMABLE komponense csak a fogyasztási UX-et hordozza; a buffot a
        // FactionFoodListener az élő tagság alapján adja.
        if (recipe.signature() != null) {
            hu.taliann.icesmp.items.ItemDataFactory.applySignatureFoodConsumable(result, recipe.signature());
        }
        // Recept-vezérelt fogyaszthatóság (új ételek/italok): a result.consumable blokk él-configból.
        final org.bukkit.configuration.ConfigurationSection consumableSection = configManager.getConfiguration()
                .getConfigurationSection("profession-recipes." + recipe.id() + ".result.consumable");
        if (consumableSection != null) {
            hu.taliann.icesmp.items.ItemDataFactory.applyRecipeConsumable(result, consumableSection);
        }

        // A unique factory által korábban feltett data componenteket a fenti meta/affix lánc
        // ledobhatta, ezért a végleges resulton újraalkalmazzuk a unique presentationt.
        if (recipe.uniqueResult() != null && !uniqueMaterials.applyPresentation(result, recipe.uniqueResult())) {
            return null;
        }

        // Külön identity az inventory/hand modellhez és a viselt equipment assethez.
        // Explicit equipment-asset elsőbbséget élvez; hiányában az equippable IceSMP itemek
        // dokumentált 1:1 render-id fallbackje használható. A pack validator ennek létezését bizonyítja.
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

        // Mestermű-mérföldkő: ha az affix-roll a létra felső fokát adta, az elismerést érdemel.
        if (professionCraft) {
            awardMasterworkIfEligible(player, result);
        }
        // result.rarity: a saját létra egy foka (ocska…ereklye) — tervezett itemnek, amely nem
        // esik át affix-rollon. Az affix-rollos gear a rollott fokot kapja az ItemRarityService-től.
        final String rarityId = configManager.getString(
                "profession-recipes." + recipe.id() + ".result.rarity", "");
        if (!rarityId.isBlank()) {
            hu.taliann.icesmp.items.ItemDataFactory.applyRarity(result,
                    hu.taliann.icesmp.items.ItemDataFactory.vanillaRarityOf(rarityId));
        }
        // result.use-remainder: <MATERIAL> — használat után a helyén maradó tárgy (üres kupa stb.).
        final String remainderName = configManager.getString(
                "profession-recipes." + recipe.id() + ".result.use-remainder", "");
        if (!remainderName.isBlank()) {
            // Kétféle megadás: "unique:<id>" (bélyegzett saját tárgy, pl. üres kupa) vagy nyers Material.
            org.bukkit.inventory.ItemStack remainder = null;
            if (remainderName.toLowerCase(Locale.ROOT).startsWith("unique:")) {
                remainder = uniqueMaterials.create(remainderName.substring("unique:".length()), 1);
            } else {
                final org.bukkit.Material remainderMaterial = org.bukkit.Material.matchMaterial(remainderName);
                if (remainderMaterial != null) {
                    remainder = new org.bukkit.inventory.ItemStack(remainderMaterial);
                }
            }
            if (remainder == null) {
                plugin.getLogger().warning("profession-recipes." + recipe.id()
                        + ".result.use-remainder: feloldhatatlan \"" + remainderName + "\" - kihagyva.");
            } else {
                hu.taliann.icesmp.items.ItemDataFactory.applyUseRemainder(result, remainder);
            }
        }
        // result.use-cooldown: { group, seconds } — saját cooldown-csoport (lassú, „nehéz" fegyver
        // vagy fogyóeszköz); a csoport miatt NEM sötétíti a vele azonos Materialú vanília itemeket.
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

    private static double clamp01(final double value) {
        if (!Double.isFinite(value)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static String locationSnapshot(final Player player) {
        final org.bukkit.Location location = player.getLocation();
        return location.getWorld().getName() + ':' + location.getBlockX() + ','
                + location.getBlockY() + ',' + location.getBlockZ();
    }

    /** Fires the milestone only after a deferred item has crossed its real delivery boundary. */
    public void awardMasterworkIfEligible(final Player player, final ItemStack result) {
        if (player == null || result == null || affixService == null) {
            return;
        }
        final String rolled = affixService.rarityIdOf(result);
        if ("legendas".equals(rolled) || "ereklye".equals(rolled)) {
            hu.taliann.icesmp.managers.AdvancementService.award(player, "masterwork");
        }
    }
}
