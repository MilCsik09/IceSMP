package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.gui.ProfessionRecipeGUI;
import hu.taliann.icesmp.gui.ProfessionRecipeHolder;
import hu.taliann.icesmp.managers.FactionManager;
import hu.taliann.icesmp.managers.ItemRarityService;
import hu.taliann.icesmp.managers.ProfessionManager;
import hu.taliann.icesmp.managers.ProfessionRecipeCatalog;
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
            default -> craft(player, action, holder.getPage());
        }
    }

    private void craft(final Player player, final String recipeId, final int page) {
        final ProfessionRecipeCatalog.Recipe recipe = catalog.get(recipeId);
        if (recipe == null) {
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
        if (recipe.faction() != null && factionManager.getFaction(player.getUniqueId()) != recipe.faction()) {
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
        if (!hasIngredients(player, recipe)) {
            player.sendMessage(messageManager.get("profession-recipe-missing", "&cNincs meg minden hozzávaló ehhez a recepthez."));
            return;
        }

        // ELŐBB épül az eredmény, és csak sikeres build UTÁN fogy a hozzávaló —
        // hibás recept-config (feloldhatatlan unique eredmény) nem nyelheti el az anyagot.
        final ItemStack result = buildResult(player, recipe);
        if (result == null) {
            return;
        }
        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {
            consumePlain(player, entry.getKey(), entry.getValue());
        }
        consumeUnique(player, recipe);
        for (final ItemStack overflow : player.getInventory().addItem(result).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
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
            professionManager.addXpFor(player, recipe.profession(), craftXp);
        }
        // A recept első elkészítése lajstrom-bejegyzés.
        final hu.taliann.icesmp.managers.BestiaryManager bestiaryRef = bestiaryManager;
        if (bestiaryRef != null) {
            bestiaryRef.record(player, hu.taliann.icesmp.managers.BestiaryManager.Category.RECIPES, recipe.id());
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6F, 1.2F);
        player.sendMessage(messageManager.get("profession-recipe-crafted", "&aElkészítetted: &e%s", recipe.displayName()));
        // Refresh so the ingredient counts / craftable states update.
        ProfessionRecipeGUI.open(player, page, professionManager, catalog, uniqueMaterials);
    }

    private boolean hasIngredients(final Player player, final ProfessionRecipeCatalog.Recipe recipe) {
        for (final Map.Entry<Material, Integer> entry : recipe.ingredients().entrySet()) {
            if (countPlain(player, entry.getKey()) < entry.getValue()) {
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

    /**
     * Hozzávalónak elfogadható-e a tárgy. A kopás/sérülés NEM zavar (a használt szerszám is jó
     * hozzávaló), de bármilyen IDENTITÁS-jel kizárja: unique-material, PDC-bélyeg (signature,
     * „Készítette”), saját név vagy lore. Az ilyen tárgyak nem tűnhetnek el hozzávalóként.
     *
     * <p>KRITIKUS: a készlet-számolás és a fogyasztás UGYANEZT a predikátumot használja. Amíg a
     * számolás csak típust nézett, a fogyasztás pedig {@code removeItem}-mel (azaz
     * {@code isSimilar}-ral) dolgozott, a kettő szétcsúszott: a saját craftolt tárgyad (és minden
     * sérült szerszám) FEDEZTE a hozzávalót, de a levonás nem találta meg — így a hozzávaló ingyen
     * maradt. 65 recept eredménye osztozik anyagon a saját plain hozzávalójával, ezért ez
     * receptek tucatjain volt kihasználható.
     */
    private boolean isPlainIngredient(final ItemStack item, final Material material) {
        if (item == null || item.getType() != material || uniqueMaterials.idOf(item) != null) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return true;
        }
        final org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasLore() && meta.getPersistentDataContainer().isEmpty();
    }

    /** Counts plain items of a material, EXCLUDING anything that carries identity. */
    private int countPlain(final Player player, final Material material) {
        int count = 0;
        for (final ItemStack item : player.getInventory().getContents()) {
            if (isPlainIngredient(item, material)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /** Levonás pontosan azokból a tárgyakból, amiket a {@link #countPlain} beszámított. */
    private void consumePlain(final Player player, final Material material, final int amount) {
        int remaining = amount;
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            final ItemStack item = contents[slot];
            if (!isPlainIngredient(item, material)) {
                continue;
            }
            final int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
            remaining -= take;
        }
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
     * @return a kész tárgy, vagy null (ismeretlen unique-eredmény)
     */
    public ItemStack buildResult(final Player player, final ProfessionRecipeCatalog.Recipe recipe) {
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
        }
        // P7 data-komponensek UTOLSÓnak (minden setItemMeta után) — a signature-ételek
        // fix-effektű buffja a CONSUMABLE-be kerül (a FactionFoodListener a food_v2 jelölő
        // alapján hagyja ki rájuk a legacy-buffot).
        if (recipe.signature() != null) {
            hu.taliann.icesmp.items.ItemDataFactory.applySignatureFoodConsumable(result, recipe.signature(), configManager);
        }
        // Recept-vezérelt fogyaszthatóság (új ételek/italok): a result.consumable blokk él-configból.
        final org.bukkit.configuration.ConfigurationSection consumableSection = configManager.getConfiguration()
                .getConfigurationSection("profession-recipes." + recipe.id() + ".result.consumable");
        if (consumableSection != null) {
            hu.taliann.icesmp.items.ItemDataFactory.applyRecipeConsumable(result, consumableSection);
        }
        final String itemModel = configManager.getString(
                "profession-recipes." + recipe.id() + ".result.item-model", "");
        if (!itemModel.isBlank()) {
            hu.taliann.icesmp.items.ItemDataFactory.applyItemModel(result, itemModel);
        }
        // Mestermű-mérföldkő: ha az affix-roll a létra felső fokát adta, az elismerést érdemel.
        if (recipe.affixTier() != null && affixService != null) {
            final String rolled = affixService.rarityIdOf(result);
            if ("legendas".equals(rolled) || "ereklye".equals(rolled)) {
                hu.taliann.icesmp.managers.AdvancementService.award(player, "masterwork");
            }
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
}
