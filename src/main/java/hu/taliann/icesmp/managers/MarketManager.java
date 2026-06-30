package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.storage.YamlStore;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player market (ideas.md "Piaci tábla / aukciósház"): sellers list the item
 * held in hand for a price in a chosen currency; buyers purchase from the
 * market GUI using bank balances. A configurable fee burns on every sale
 * (money sink), and player-to-player trade moves currency supply — feeding
 * the dynamic exchange rates. Listings persist to market.yml.
 */
public final class MarketManager implements PersistentStore {

    /** A single market listing. */
    public record Listing(UUID id, UUID seller, String sellerName, double price,
                          CurrencyType currency, ItemStack item, long createdAt) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final FactionRelationManager relationManager;
    private final File storageFile;
    private final Map<UUID, Listing> listings = new ConcurrentHashMap<>();

    public MarketManager(final JavaPlugin plugin, final ConfigManager configManager,
                         final CurrencyManager currencyManager, final FactionManager factionManager,
                         final FactionRelationManager relationManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.relationManager = relationManager;
        this.storageFile = new File(plugin.getDataFolder(), "market.yml");
        plugin.getDataFolder().mkdirs();
    }

    /**
     * Gets the effective price a buyer pays for a listing, after applying the
     * faction reputation modifier (enemy surcharge / ally discount).
     *
     * @param buyer the prospective buyer
     * @param listing the listing
     * @return the reputation-adjusted price
     */
    public double getEffectivePrice(final org.bukkit.entity.Player buyer, final Listing listing) {
        final double multiplier = relationManager.getMarketPriceMultiplier(
                factionManager.getFaction(buyer.getUniqueId()),
                factionManager.getFaction(listing.seller()));
        return listing.price() * multiplier;
    }

    public void load() {
        listings.clear();

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
            final ConfigurationSection section = yaml.getConfigurationSection("listings");
            if (section == null) {
                return;
            }

            for (final String idKey : section.getKeys(false)) {
                try {
                    final UUID id = UUID.fromString(idKey);
                    final UUID seller = UUID.fromString(section.getString(idKey + ".seller", ""));
                    final CurrencyType currency = CurrencyType.fromInput(section.getString(idKey + ".currency", ""));
                    final ItemStack item = section.getItemStack(idKey + ".item");
                    if (currency == null || item == null || item.getType() == Material.AIR) {
                        continue;
                    }

                    listings.put(id, new Listing(
                            id,
                            seller,
                            section.getString(idKey + ".seller-name", "?"),
                            Math.max(0.01D, section.getDouble(idKey + ".price", 1.0D)),
                            currency,
                            item,
                            section.getLong(idKey + ".created-at", System.currentTimeMillis())
                    ));
                } catch (final IllegalArgumentException ignored) {
                    // Skip malformed entries.
                }
            }

            plugin.getLogger().info("Loaded " + listings.size() + " market listing(s).");
        } catch (final Exception exception) {
            plugin.getLogger().severe("Failed to load market.yml: " + exception.getMessage());
        }
    }

    public synchronized void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Listing listing : listings.values()) {
            final String basePath = "listings." + listing.id();
            yaml.set(basePath + ".seller", listing.seller().toString());
            yaml.set(basePath + ".seller-name", listing.sellerName());
            yaml.set(basePath + ".price", listing.price());
            yaml.set(basePath + ".currency", listing.currency().name());
            yaml.set(basePath + ".item", listing.item());
            yaml.set(basePath + ".created-at", listing.createdAt());
        }

        try {
            YamlStore.saveAtomic(storageFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save market.yml: " + exception.getMessage());
        }
    }

    public Listing getListing(final UUID listingId) {
        return listingId == null ? null : listings.get(listingId);
    }

    /**
     * Gets every listing, newest first.
     *
     * @return sorted listings
     */
    public List<Listing> getListingsSorted() {
        return listings.values().stream()
                .sorted(Comparator.comparingLong(Listing::createdAt).reversed())
                .toList();
    }

    public long countListingsOf(final UUID seller) {
        return listings.values().stream().filter(listing -> listing.seller().equals(seller)).count();
    }

    /**
     * Lists the item held in the seller's main hand.
     *
     * @param seller the seller
     * @param price the asking price
     * @param currency the currency the price is denominated in
     * @return null on success, otherwise an error message key
     */
    public synchronized String createListing(final Player seller, final double price, final CurrencyType currency) {
        final ItemStack held = seller.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            return "market-no-item";
        }

        if (!Double.isFinite(price) || price <= 0.0D) {
            return "amount-must-be-positive";
        }

        final int maxListings = Math.max(1, configManager.getInt("market.max-listings-per-player", 5));
        if (countListingsOf(seller.getUniqueId()) >= maxListings) {
            return "market-too-many-listings";
        }

        final UUID id = UUID.randomUUID();
        listings.put(id, new Listing(id, seller.getUniqueId(), seller.getName(), price,
                currency, held.clone(), System.currentTimeMillis()));
        seller.getInventory().setItemInMainHand(null);
        save();
        return null;
    }

    /**
     * Buys a listing using the buyer's bank balance. The configured fee percent
     * burns (money sink); the rest is credited to the seller.
     *
     * @param buyer the buyer
     * @param listingId the listing to buy
     * @return null on success, otherwise an error message key
     */
    public synchronized String buy(final Player buyer, final UUID listingId) {
        final Listing listing = listings.get(listingId);
        if (listing == null) {
            return "market-listing-gone";
        }

        if (listing.seller().equals(buyer.getUniqueId())) {
            return "market-own-listing";
        }

        // Faction reputation adjusts what the buyer pays; both fee and seller share are
        // derived from that same amount so the trade conserves currency (no minting/burning
        // beyond the configured fee sink).
        final double buyerCost = getEffectivePrice(buyer, listing);
        if (!currencyManager.deductFromBalance(buyer.getUniqueId(), listing.currency(), buyerCost)) {
            return "market-insufficient-balance";
        }

        // Claim the listing atomically; if it vanished (concurrent buy/cancel), refund the buyer.
        if (listings.remove(listingId) == null) {
            currencyManager.addToBalance(buyer.getUniqueId(), listing.currency(), buyerCost);
            return "market-listing-gone";
        }

        final double feePercent = Math.max(0.0D, Math.min(100.0D, configManager.getDouble("market.fee-percent", 10.0D)));
        final double sellerShare = buyerCost * (1.0D - (feePercent / 100.0D));
        if (sellerShare > 0.0D) {
            currencyManager.addToBalance(listing.seller(), listing.currency(), sellerShare);
        }

        save();

        final Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(listing.item());
        leftovers.values().forEach(item -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), item));
        return null;
    }

    /**
     * Cancels every listing of the player, returning the items.
     *
     * @param player the seller
     * @return the number of cancelled listings
     */
    public synchronized int cancelListings(final Player player) {
        int cancelled = 0;
        for (final Listing listing : List.copyOf(listings.values())) {
            if (!listing.seller().equals(player.getUniqueId())) {
                continue;
            }

            listings.remove(listing.id());
            final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(listing.item());
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            cancelled++;
        }

        if (cancelled > 0) {
            save();
        }
        return cancelled;
    }
}
