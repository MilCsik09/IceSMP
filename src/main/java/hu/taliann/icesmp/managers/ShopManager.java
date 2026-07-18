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

        // Escort-success perk: while the bonus window is open, the caravan also
        // sells its bonus-items entries (appended after the regular stock).
        if (CaravanManager.SHOP_NAME.equalsIgnoreCase(npcName)
                && escortBonusCheck != null && escortBonusCheck.getAsBoolean()) {
            entries.addAll(sectionsOf(shop.getConfigurationSection("bonus-items")));
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
        final double price = getPrice(item);
        if (price > 0.0D && !currencyManager.deductFromBalance(buyer.getUniqueId(), currency, price)) {
            return "shop-insufficient";
        }

        // The price is now burned (never credited) — this is the money sink.
        final int amount = getAmount(item);
        final Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(new ItemStack(material, amount));
        leftovers.values().forEach(left -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), left));

        buyer.sendMessage(messageManager.get(
                "shop-buy-success",
                "&aVettél: &f%s x%s &7(%s %s).",
                material.name(), amount, currencyManager.formatBalance(price), currency.getDisplayName()
        ));
        return null;
    }
}
