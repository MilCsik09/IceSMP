package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
 *
 * <p>Auction listings (ROADMAP "Valódi aukciósház") extend the same store:
 * bids are escrowed from the bidder's bank immediately, an overbid refunds
 * the previous bidder, and {@link #tickAuctions()} settles expired auctions —
 * the winner's escrowed bid pays the seller (minus the market fee) and the
 * item is delivered, or queued in the pending-delivery store if the winner
 * is offline. Bids are absolute amounts, so the faction reputation price
 * multiplier applies only to fixed-price listings.</p>
 */
public final class MarketManager implements PersistentStore {

    /**
     * A single market listing. Fixed-price listings have {@code auction=false}
     * and zeroed auction fields; auctions track the highest escrowed bid.
     */
    public record Listing(UUID id, UUID seller, String sellerName, double price,
                          CurrencyType currency, ItemStack item, long createdAt,
                          boolean auction, long endsAt, double highestBid,
                          UUID highestBidder, String highestBidderName) {

        public boolean hasBid() {
            return highestBidder != null;
        }
    }

    /**
     * The result of a bid attempt: on failure only {@code errorKey} is set; on
     * success it is null and the escrowed amount plus the refunded previous
     * bidder (if any) are reported so the caller can notify them.
     */
    public record BidOutcome(String errorKey, double amount, UUID previousBidder, double previousBid) {

        static BidOutcome error(final String errorKey) {
            return new BidOutcome(errorKey, 0.0D, null, 0.0D);
        }
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final FactionRelationManager relationManager;
    private final MessageManager messageManager;
    private final File storageFile;
    private final Map<UUID, Listing> listings = new ConcurrentHashMap<>();
    // Items owed to (possibly offline) players: auction wins and unsold auction
    // returns. Delivered on the owner's own region thread (join / /market claim).
    private final Map<UUID, List<ItemStack>> pendingDeliveries = new ConcurrentHashMap<>();

    public MarketManager(final JavaPlugin plugin, final ConfigManager configManager,
                         final CurrencyManager currencyManager, final FactionManager factionManager,
                         final FactionRelationManager relationManager, final MessageManager messageManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.relationManager = relationManager;
        this.messageManager = messageManager;
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
        pendingDeliveries.clear();

        if (!storageFile.exists()) {
            return;
        }

        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
            final ConfigurationSection section = yaml.getConfigurationSection("listings");
            if (section != null) {
                for (final String idKey : section.getKeys(false)) {
                    try {
                        final UUID id = UUID.fromString(idKey);
                        final UUID seller = UUID.fromString(section.getString(idKey + ".seller", ""));
                        final CurrencyType currency = CurrencyType.fromInput(section.getString(idKey + ".currency", ""));
                        final ItemStack item = section.getItemStack(idKey + ".item");
                        if (currency == null || item == null || item.getType() == Material.AIR) {
                            continue;
                        }

                        final String bidderRaw = section.getString(idKey + ".highest-bidder", "");
                        listings.put(id, new Listing(
                                id,
                                seller,
                                section.getString(idKey + ".seller-name", "?"),
                                Math.max(0.01D, section.getDouble(idKey + ".price", 1.0D)),
                                currency,
                                item,
                                section.getLong(idKey + ".created-at", System.currentTimeMillis()),
                                section.getBoolean(idKey + ".auction", false),
                                section.getLong(idKey + ".ends-at", 0L),
                                section.getDouble(idKey + ".highest-bid", 0.0D),
                                bidderRaw.isEmpty() ? null : UUID.fromString(bidderRaw),
                                section.getString(idKey + ".highest-bidder-name", null)
                        ));
                    } catch (final IllegalArgumentException ignored) {
                        // Skip malformed entries.
                    }
                }
            }

            final ConfigurationSection deliveries = yaml.getConfigurationSection("pending-deliveries");
            if (deliveries != null) {
                for (final String playerKey : deliveries.getKeys(false)) {
                    try {
                        final UUID owner = UUID.fromString(playerKey);
                        final List<ItemStack> items = new ArrayList<>();
                        final List<?> raw = deliveries.getList(playerKey);
                        if (raw != null) {
                            for (final Object entry : raw) {
                                if (entry instanceof ItemStack item && !item.getType().isAir()) {
                                    items.add(item);
                                }
                            }
                        }
                        if (!items.isEmpty()) {
                            pendingDeliveries.put(owner, items);
                        }
                    } catch (final IllegalArgumentException ignored) {
                        // Skip malformed entries.
                    }
                }
            }

            plugin.getLogger().info("Loaded " + listings.size() + " market listing(s) and "
                    + pendingDeliveries.size() + " pending deliver(y/ies).");
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
            if (listing.auction()) {
                yaml.set(basePath + ".auction", true);
                yaml.set(basePath + ".ends-at", listing.endsAt());
                yaml.set(basePath + ".highest-bid", listing.highestBid());
                if (listing.hasBid()) {
                    yaml.set(basePath + ".highest-bidder", listing.highestBidder().toString());
                    yaml.set(basePath + ".highest-bidder-name", listing.highestBidderName());
                }
            }
        }
        for (final Map.Entry<UUID, List<ItemStack>> entry : pendingDeliveries.entrySet()) {
            yaml.set("pending-deliveries." + entry.getKey(), entry.getValue());
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
        return createListingInternal(seller, price, currency, 0L);
    }

    /**
     * Lists the item held in the seller's main hand as an auction: {@code startPrice}
     * is the opening bid, and the auction settles after {@code durationMillis}.
     *
     * @param seller the seller
     * @param startPrice the opening bid
     * @param currency the currency bids are denominated in
     * @param requestedDurationMillis how long the auction should run; {@code <= 0}
     *        falls back to the configured default, and the config maximum caps it
     * @return null on success, otherwise an error message key
     */
    public synchronized String createAuction(final Player seller, final double startPrice,
                                             final CurrencyType currency, final long requestedDurationMillis) {
        final long defaultMillis = (long) (configManager.getDouble("market.auction.default-duration-hours", 24.0D) * 3_600_000L);
        final long maxMillis = (long) (configManager.getDouble("market.auction.max-duration-hours", 72.0D) * 3_600_000L);
        final long duration = Math.max(60_000L, Math.min(Math.max(60_000L, maxMillis),
                requestedDurationMillis > 0L ? requestedDurationMillis : defaultMillis));
        return createListingInternal(seller, startPrice, currency, duration);
    }

    private String createListingInternal(final Player seller, final double price,
                                         final CurrencyType currency, final long auctionDurationMillis) {
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
        final long now = System.currentTimeMillis();
        final boolean auction = auctionDurationMillis > 0L;
        listings.put(id, new Listing(id, seller.getUniqueId(), seller.getName(), price,
                currency, held.clone(), now, auction, auction ? now + auctionDurationMillis : 0L,
                0.0D, null, null));
        seller.getInventory().setItemInMainHand(null);
        save();
        return null;
    }

    /**
     * Buys a fixed-price listing using the buyer's bank balance. The configured
     * fee percent burns (money sink); the rest is credited to the seller.
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

        if (listing.auction()) {
            return "market-auction-use-bid";
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

        creditSellerShare(listing.seller(), listing.currency(), buyerCost);

        save();

        final Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(listing.item());
        leftovers.values().forEach(item -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), item));
        return null;
    }

    /** Credits the seller the sale amount minus the configured burn fee. */
    private void creditSellerShare(final UUID seller, final CurrencyType currency, final double amount) {
        final double feePercent = Math.max(0.0D, Math.min(100.0D, configManager.getDouble("market.fee-percent", 10.0D)));
        final double sellerShare = amount * (1.0D - (feePercent / 100.0D));
        if (sellerShare > 0.0D) {
            currencyManager.addToBalance(seller, currency, sellerShare);
        }
    }

    /**
     * Gets the smallest acceptable next bid on an auction: the opening price if
     * nobody has bid yet, otherwise the current bid raised by the configured
     * minimum increment percent.
     *
     * @param listing the auction listing
     * @return the minimum next bid
     */
    public double getMinimumBid(final Listing listing) {
        if (!listing.hasBid()) {
            return listing.price();
        }
        final double incrementPercent = Math.max(1.0D,
                configManager.getDouble("market.auction.min-increment-percent", 10.0D));
        return Math.ceil(listing.highestBid() * (1.0D + incrementPercent / 100.0D) * 100.0D) / 100.0D;
    }

    /**
     * Places the minimum next bid on an auction from the bidder's bank balance.
     * The bid is escrowed immediately; the previously escrowed bid (if any) is
     * refunded to the outbid player's bank.
     *
     * @param bidder the bidding player
     * @param listingId the auction listing
     * @return the outcome; {@link BidOutcome#errorKey()} is null on success
     */
    public synchronized BidOutcome bid(final Player bidder, final UUID listingId) {
        final Listing listing = listings.get(listingId);
        if (listing == null || !listing.auction()) {
            return BidOutcome.error("market-listing-gone");
        }

        if (System.currentTimeMillis() >= listing.endsAt()) {
            return BidOutcome.error("market-auction-ended");
        }

        if (listing.seller().equals(bidder.getUniqueId())) {
            return BidOutcome.error("market-own-listing");
        }

        if (bidder.getUniqueId().equals(listing.highestBidder())) {
            return BidOutcome.error("market-already-highest");
        }

        final double amount = getMinimumBid(listing);
        if (!currencyManager.deductFromBalance(bidder.getUniqueId(), listing.currency(), amount)) {
            return BidOutcome.error("market-insufficient-balance");
        }

        // Refund the outbid player's escrowed bid before recording the new one.
        if (listing.hasBid()) {
            currencyManager.addToBalance(listing.highestBidder(), listing.currency(), listing.highestBid());
        }

        listings.put(listingId, new Listing(listing.id(), listing.seller(), listing.sellerName(),
                listing.price(), listing.currency(), listing.item(), listing.createdAt(),
                true, listing.endsAt(), amount, bidder.getUniqueId(), bidder.getName()));
        save();
        return new BidOutcome(null, amount, listing.highestBidder(), listing.highestBid());
    }

    /**
     * Settles every expired auction: with a winning bid the escrowed amount pays
     * the seller (minus the burn fee) and the item goes to the winner; without
     * bids the item returns to the seller. Items are queued in the pending-delivery
     * store and pushed to online players on their own region thread (Folia).
     * Runs on the shared world-events tick.
     */
    public void tickAuctions() {
        final long now = System.currentTimeMillis();
        final List<Listing> settled = new ArrayList<>();

        synchronized (this) {
            for (final Listing listing : List.copyOf(listings.values())) {
                if (!listing.auction() || now < listing.endsAt()) {
                    continue;
                }
                listings.remove(listing.id());
                if (listing.hasBid()) {
                    creditSellerShare(listing.seller(), listing.currency(), listing.highestBid());
                    queueDelivery(listing.highestBidder(), listing.item());
                } else {
                    queueDelivery(listing.seller(), listing.item());
                }
                settled.add(listing);
            }
            if (!settled.isEmpty()) {
                save();
            }
        }

        // Notices + delivery hop to each player's own region thread (Folia rule:
        // we are on the global scheduler here and must not touch player state).
        for (final Listing listing : settled) {
            if (listing.hasBid()) {
                notifyAndDeliver(listing.highestBidder(), messageManager.getMessage(
                        "market-auction-won",
                        "&aMegnyerted az aukciót (&f{item}&a) &f{price} {currency}&a licittel!",
                        Map.of(
                                "item", listing.item().getType().name(),
                                "price", currencyManager.formatBalance(listing.highestBid()),
                                "currency", listing.currency().getDisplayName()
                        )));
                notifyAndDeliver(listing.seller(), messageManager.getMessage(
                        "market-auction-sold",
                        "&aAz aukciód lezárult: &f{bidder}&a vitte el &f{price} {currency}&a-ért — a bevétel a bankodba került.",
                        Map.of(
                                "bidder", listing.highestBidderName() == null ? "?" : listing.highestBidderName(),
                                "price", currencyManager.formatBalance(listing.highestBid()),
                                "currency", listing.currency().getDisplayName()
                        )));
            } else {
                notifyAndDeliver(listing.seller(), messageManager.getMessage(
                        "market-auction-unsold",
                        "&7Az aukciódra (&f{item}&7) nem érkezett licit — a tárgyat visszakapod.",
                        Map.of("item", listing.item().getType().name())));
            }
        }
    }

    private void queueDelivery(final UUID owner, final ItemStack item) {
        pendingDeliveries.computeIfAbsent(owner, key -> new ArrayList<>()).add(item.clone());
    }

    /** Sends the notice and pushes pending items to the player if online, on their own scheduler. */
    private void notifyAndDeliver(final UUID playerId, final net.kyori.adventure.text.Component notice) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.getScheduler().run(plugin, task -> {
            player.sendMessage(notice);
            deliverPending(player);
        }, null);
    }

    public boolean hasPendingDelivery(final UUID playerId) {
        return pendingDeliveries.containsKey(playerId);
    }

    /**
     * Hands every queued item to the player. MUST be called on the player's own
     * region thread (command execution / join event / entity scheduler task).
     *
     * @param player the recipient
     * @return the number of items delivered
     */
    public synchronized int deliverPending(final Player player) {
        final List<ItemStack> items = pendingDeliveries.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return 0;
        }

        for (final ItemStack item : items) {
            final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        save();
        return items.size();
    }

    /**
     * Cancels every listing of the player, returning the items. Auctions that
     * already have a bid cannot be cancelled (the escrow is committed).
     *
     * @param player the seller
     * @return the number of cancelled listings
     */
    public synchronized int cancelListings(final Player player) {
        int cancelled = 0;
        for (final Listing listing : List.copyOf(listings.values())) {
            if (!listing.seller().equals(player.getUniqueId()) || listing.hasBid()) {
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

    /**
     * Whether the player still has an active listing that couldn't be cancelled
     * (an auction with a live bid).
     */
    public boolean hasLockedAuction(final UUID seller) {
        return listings.values().stream()
                .anyMatch(listing -> listing.seller().equals(seller) && listing.hasBid());
    }
}
