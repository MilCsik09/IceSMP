package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.data.FactionType;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Faction shop NPCs (ROADMAP economy: "frakció-bolt NPC-k" money sink):
 * config-driven vendors bound to a FancyNpcs NPC. Right-clicking a shop NPC
 * opens a buy GUI; purchases deduct the price from the player's bank balance
 * and the currency is BURNED (never credited anywhere) — a pure money sink
 * that pulls currency out of the economy. Shops are stateless (read straight
 * from config); only the currency deduction touches persistent state.
 */
public final class ShopManager {

    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final MessageManager messageManager;

    /**
     * Gate for the reserved {@code "caravan"} shop: true only while the merchant
     * caravan is in town. Set by {@link CaravanManager} after both are constructed
     * (avoids a construction-order dependency). Null = caravan feature not wired.
     */
    private java.util.function.BooleanSupplier caravanActiveCheck;

    /**
     * Gate for the caravan's bonus stock: true while a recent escort success keeps
     * the {@code caravan.bonus-items} entries on sale. Set by the core after the
     * {@link EscortManager} is constructed. Null = escort feature not wired.
     */
    private java.util.function.BooleanSupplier escortBonusCheck;
    /** A karaván-látogatás készlet-sorsolási magja (rotáló kínálat — CaravanManager adja). */
    private java.util.function.LongSupplier caravanStockSeed;

    public void setCaravanStockSeed(final java.util.function.LongSupplier caravanStockSeed) {
        this.caravanStockSeed = caravanStockSeed;
    }

    public ShopManager(final ConfigManager configManager, final CurrencyManager currencyManager,
                       final FactionManager factionManager, final MessageManager messageManager) {
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.messageManager = messageManager;
    }

    /** Registers the predicate that reports whether the caravan is currently present. */
    public void setCaravanActiveCheck(final java.util.function.BooleanSupplier caravanActiveCheck) {
        this.caravanActiveCheck = caravanActiveCheck;
    }

    /** Registers the predicate that reports whether the escort-success bonus stock is unlocked. */
    public void setEscortBonusCheck(final java.util.function.BooleanSupplier escortBonusCheck) {
        this.escortBonusCheck = escortBonusCheck;
    }

    /** Whether an NPC name has a configured, enabled shop. */
    public boolean hasShop(final String npcName) {
        return getShop(npcName) != null;
    }

    public ConfigurationSection getShop(final String npcName) {
        if (npcName == null || configManager.getConfiguration() == null) {
            return null;
        }

        // The caravan's rotating stock lives at the config root `caravan`, and is only
        // buyable while the merchant is actually in town (gated by the active-check).
        if (CaravanManager.SHOP_NAME.equalsIgnoreCase(npcName)) {
            if (!configManager.getBoolean("caravan.enabled", true)
                    || caravanActiveCheck == null || !caravanActiveCheck.getAsBoolean()) {
                return null;
            }
            return configManager.getConfiguration().getConfigurationSection("caravan");
        }

        if (!configManager.getBoolean("faction-shops.enabled", true)) {
            return null;
        }
        return configManager.getConfiguration()
                .getConfigurationSection("faction-shops." + npcName.toLowerCase(Locale.ROOT));
    }

    public String getTitle(final String npcName) {
        final ConfigurationSection shop = getShop(npcName);
        return shop == null ? "Bolt" : shop.getString("title", "Bolt");
    }

    /** The sale entries of a shop, in numeric key order. */
    public List<ConfigurationSection> getItems(final String npcName) {
        final ConfigurationSection shop = getShop(npcName);
        if (shop == null) {
            return List.of();
        }
        final java.util.List<ConfigurationSection> entries =
                new java.util.ArrayList<>(sectionsOf(shop.getConfigurationSection("items")));

        if (CaravanManager.SHOP_NAME.equalsIgnoreCase(npcName)) {
            // Rotáló karaván-készlet: érkezésenként a teljes áru-poolból stock-size darab
            // sorsolódik (a látogatás stockSeed-jével determinisztikusan — a bolt a
            // tartózkodás ALATT stabil, a következő érkezéskor fordul).
            if (configManager.getBoolean("caravan.rotation.enabled", true)
                    && caravanStockSeed != null) {
                final int stockSize = Math.max(1, configManager.getInt("caravan.rotation.stock-size", 4));
                if (entries.size() > stockSize) {
                    final java.util.Random rotationRandom = new java.util.Random(caravanStockSeed.getAsLong());
                    // Gameplay-audit: unique-anyag slot-garancia — a ritka szakma-alapanyag
                    // pool-ága ne sorsolódhasson ki teljesen (alternatív forrás maradjon).
                    final java.util.List<ConfigurationSection> uniquePool =
                            configManager.getBoolean("caravan.rotation.guarantee-unique", true)
                                    ? entries.stream().filter(entry -> entry.getString("unique") != null).toList()
                                    : List.<ConfigurationSection>of();
                    java.util.Collections.shuffle(entries, rotationRandom);
                    entries.subList(stockSize, entries.size()).clear();
                    if (!uniquePool.isEmpty() && entries.stream().noneMatch(entry -> entry.getString("unique") != null)) {
                        entries.set(entries.size() - 1, uniquePool.get(rotationRandom.nextInt(uniquePool.size())));
                    }
                }
            }
            // Escort-success perk: while the bonus window is open, the caravan also
            // sells its bonus-items entries (appended after the regular stock).
            if (escortBonusCheck != null && escortBonusCheck.getAsBoolean()) {
                entries.addAll(sectionsOf(shop.getConfigurationSection("bonus-items")));
            }
        }
        return List.copyOf(entries);
    }

    /** The child sections of a shop item list, in numeric key order (empty if null). */
    private static List<ConfigurationSection> sectionsOf(final ConfigurationSection items) {
        if (items == null) {
            return List.of();
        }
        return items.getKeys(false).stream()
                .sorted(java.util.Comparator.comparingInt(ShopManager::order))
                .map(items::getConfigurationSection)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static int order(final String key) {
        try {
            return Integer.parseInt(key.trim());
        } catch (final NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    /** The currency a shop entry is priced in (OWN = the buyer's faction currency). */
    public CurrencyType resolveCurrency(final ConfigurationSection item, final Player buyer) {
        final String raw = item.getString("currency", "OWN");
        if ("OWN".equalsIgnoreCase(raw) || "FACTION".equalsIgnoreCase(raw) || "SAJAT".equalsIgnoreCase(raw)) {
            return CurrencyType.fromFactionType(factionManager.getFaction(buyer.getUniqueId()));
        }
        final CurrencyType byName = CurrencyType.fromInput(raw);
        return byName == null ? currencyManager.getDefaultCurrency() : byName;
    }

    public Material getMaterial(final ConfigurationSection item) {
        final Material material = Material.matchMaterial(item.getString("material", ""));
        return material == null || material.isAir() ? null : material;
    }

    public int getAmount(final ConfigurationSection item) {
        return Math.max(1, item.getInt("amount", 1));
    }

    public double getPrice(final ConfigurationSection item) {
        return Math.max(0.0D, item.getDouble("price", 0.0D));
    }

    /**
     * Buys the shop entry at the given index for the player, deducting the
     * price from their bank (the currency is burned — money sink).
     *
     * @param buyer the buyer
     * @param npcName the shop NPC name
     * @param index the zero-based item index in the shop's GUI
     * @return null on success, otherwise an error message key
     */
    /** Suttogó-erősítés (setterrel kötve): feketepiaci kedvezmény a felesküdötteknek. */
    private volatile WhisperManager whisperManager;

    public void setWhisperManager(final WhisperManager whisperManager) {
        this.whisperManager = whisperManager;
    }

    public synchronized String buy(final Player buyer, final String npcName, final int index) {
        final ConfigurationSection shop = getShop(npcName);
        if (shop == null) {
            return "shop-closed";
        }

        final String shopFactionName = shop.getString("faction", "ALL");
        if (!shopFactionName.isBlank() && !"ALL".equalsIgnoreCase(shopFactionName)) {
            final FactionType shopFaction = FactionType.fromInput(shopFactionName);
            if (shopFaction != null && factionManager.getFaction(buyer.getUniqueId()) != shopFaction) {
                return "shop-wrong-faction";
            }
        }

        final List<ConfigurationSection> items = getItems(npcName);
        if (index < 0 || index >= items.size()) {
            return "shop-item-gone";
        }
        final ConfigurationSection item = items.get(index);
        final Material material = getMaterial(item);
        if (material == null) {
            return "shop-item-gone";
        }

        final CurrencyType currency = resolveCurrency(item, buyer);
        double price = getPrice(item);
        // Suttogó-erősítés (tulaj-jóváhagyás): a feketepiacon a felesküdöttek a hálózat
        // árát fizetik — csendes kedvezmény (a kijelzett ár marad, a levonás kevesebb;
        // a titkos státuszt nem leplezi le semmi látható).
        final WhisperManager whisperRef = whisperManager;
        if (price > 0.0D && whisperRef != null
                && npcName != null && npcName.equalsIgnoreCase(
                        configManager.getString("factions.whisper.blackmarket-npc", "feketepiac"))
                && whisperRef.isWhispererCached(buyer.getUniqueId())) {
            final double discount = Math.max(0.0D, Math.min(90.0D,
                    configManager.getDouble("factions.whisper.blackmarket-discount-percent", 25.0D)));
            price = price * (1.0D - discount / 100.0D);
        }
        if (price > 0.0D && !currencyManager.deductFromBalance(buyer.getUniqueId(), currency, price)) {
            return "shop-insufficient";
        }

        // The price is now burned (never credited) — this is the money sink.
        final int amount = getAmount(item);
        final Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(buildShopItem(item, material, amount));
        leftovers.values().forEach(left -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), left));

        buyer.sendMessage(messageManager.get(
                "shop-buy-success",
                "&aVettél: &f%s x%s &7(%s %s).",
                material.name(), amount, currencyManager.formatBalance(price), currency.getDisplayName()
        ));
        return null;
    }

    /**
     * A megvásárolt tárgy felépítése: sima material, VAGY — ha a bolt-item configja kéri —
     * nevesített/lore-os/signature-tagelt különleges áru (K10 feketepiac: Sétapálca, Menlevél).
     * Config-mezők az item-szekción: name (legacy &-kódokkal), lore (lista), signature
     * (PDC-id — a SignatureItemListener perk-kulcsa; ugyanaz a tag, mint a recept-motoré).
     */
    /** Vendor-only unique anyagok támogatása: setterrel kötve (a factory később épülhet). */
    private volatile hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterialFactory;

    public void setUniqueMaterialFactory(final hu.taliann.icesmp.items.UniqueMaterialFactory uniqueMaterialFactory) {
        this.uniqueMaterialFactory = uniqueMaterialFactory;
    }

    private ItemStack buildShopItem(final ConfigurationSection item, final Material material, final int amount) {
        // Vendor-only unique anyag (`unique: <id>` a bolt-item configon): a valódi
        // UniqueMaterialFactory-tárgy kerül a kosárba (PDC-taggel) — a recept-motor
        // így ismeri fel; a bolt az EGYETLEN forrása (pénz-nyelő + recept-kereslet).
        final String uniqueId = item.getString("unique", "");
        final hu.taliann.icesmp.items.UniqueMaterialFactory factoryRef = uniqueMaterialFactory;
        if (!uniqueId.isBlank() && factoryRef != null) {
            final ItemStack uniqueStack = factoryRef.create(uniqueId, amount);
            if (uniqueStack != null) {
                return uniqueStack;
            }
        }
        final ItemStack stack = new ItemStack(material, amount);
        final String name = item.getString("name", "");
        final java.util.List<String> lore = item.getStringList("lore");
        final String signature = item.getString("signature", "");
        if (name.isBlank() && lore.isEmpty() && signature.isBlank()) {
            return stack;
        }
        final org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        final net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer legacy =
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
        if (!name.isBlank()) {
            meta.displayName(legacy.deserialize(name)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        if (!lore.isEmpty()) {
            final java.util.List<net.kyori.adventure.text.Component> lines = new java.util.ArrayList<>();
            for (final String line : lore) {
                lines.add(legacy.deserialize(line)
                        .colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        }
        // Resource pack horog: a bolt-különlegességek fix textúrát kaphatnak
        // (`custom-model-data` a bolt-item configon) — docs/RESOURCE_PACK_CMD.md.
        final int customModelData = item.getInt("custom-model-data", 0);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        if (!signature.isBlank()) {
            meta.getPersistentDataContainer().set(
                    hu.taliann.icesmp.listeners.SignatureItemListener.SIGNATURE_PDC_KEY,
                    org.bukkit.persistence.PersistentDataType.STRING, signature);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
