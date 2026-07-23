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
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Player market: sellers list the item
 * held in hand for a price in a chosen currency; buyers purchase from the
 * market GUI using bank balances. A configurable fee burns on every sale
 * (money sink), and player-to-player trade moves currency supply — feeding
 * the dynamic exchange rates. Listings persist to market.yml.
 *
 * <p>Auction listings extend the same store:
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
                          UUID highestBidder, String highestBidderName, double buyOut) {

        public boolean hasBid() {
            return highestBidder != null;
        }

        /** Whether this auction offers an instant buy-out price. */
        public boolean hasBuyOut() {
            return buyOut > 0.0D;
        }
    }

    /**
     * The result of a bid attempt: on failure only {@code errorKey} is set; on
     * success it is null and the escrowed amount plus the refunded previous
     * bidder (if any) are reported so the caller can notify them. When
     * {@code boughtOut} is true the bid met the buy-out price and the auction
     * was won immediately — the item is already on its way to the bidder.
     */
    public record BidOutcome(String errorKey, double amount, UUID previousBidder, double previousBid,
                             boolean boughtOut) {

        static BidOutcome error(final String errorKey) {
            return new BidOutcome(errorKey, 0.0D, null, 0.0D, false);
        }
    }

    /**
     * The result of a fixed-price purchase attempt: on failure only {@code errorKey}
     * is set; on success it is null and {@code amount} is the price actually deducted
     * from the buyer at the moment of the sale (faction-reputation adjusted), so callers
     * report exactly what was charged instead of re-deriving a possibly stale estimate.
     */
    public record BuyOutcome(String errorKey, double amount) {

        static BuyOutcome error(final String errorKey) {
            return new BuyOutcome(errorKey, 0.0D);
        }
    }

    /** A single completed sale kept for {@code /market stats} — not persisted. */
    public record Transaction(Material itemType, double price, CurrencyType currency, long timestamp) {
    }

    /** How many recent transactions {@link #recentTransactions} keeps for {@code /market stats}. */
    private static final int TRANSACTION_LOG_CAP = 50;

    /**
     * Summary snapshot for {@code /market stats}: current listing/auction counts, the
     * most-listed item types, the average recent sale price per currency, and the
     * single biggest recent sale.
     */
    public record MarketStats(int activeListings, int activeAuctions,
                              List<Map.Entry<Material, Long>> topItemTypes,
                              Map<CurrencyType, Double> averagePriceByCurrency,
                              Transaction biggestRecentSale, int recentSaleCount) {
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
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    // In-memory sale log for /market stats (task-scoped, not persisted): capped deque of
    // the last completed transactions, touched from multiple region threads (buy/bid/tickAuctions).
    private final Deque<Transaction> recentTransactions = new ConcurrentLinkedDeque<>();

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

                        // A malformed bidder id must not discard the whole listing (the item
                        // would vanish) — degrade to "no bid" and keep the auction alive.
                        UUID bidderId = null;
                        try {
                            final String bidderRaw = section.getString(idKey + ".highest-bidder", "");
                            bidderId = bidderRaw.isEmpty() ? null : UUID.fromString(bidderRaw);
                        } catch (final IllegalArgumentException ignored) {
                            bidderId = null;
                        }
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
                                bidderId,
                                section.getString(idKey + ".highest-bidder-name", null),
                                section.getDouble(idKey + ".buy-out", 0.0D)
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

    /**
     * Debounced async flush for transaction paths: a busy market used to rewrite the whole
     * market.yml synchronously on the region thread for every buy/bid/cancel — bursts now
     * coalesce into one write ~2s later (CurrencyManager pattern). Shutdown still calls
     * {@link #save()} directly for a final synchronous flush.
     */
    private void requestSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                save();
            }, 2L, TimeUnit.SECONDS);
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
                if (listing.hasBuyOut()) {
                    yaml.set(basePath + ".buy-out", listing.buyOut());
                }
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

    /** @return every listing, newest first */
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
        return createListingInternal(seller, price, currency, 0L, 0.0D);
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
                                             final CurrencyType currency, final long requestedDurationMillis,
                                             final double buyOut) {
        final long defaultMillis = (long) (configManager.getDouble("market.auction.default-duration-hours", 24.0D) * 3_600_000L);
        final long maxMillis = (long) (configManager.getDouble("market.auction.max-duration-hours", 72.0D) * 3_600_000L);
        final long duration = Math.max(60_000L, Math.min(Math.max(60_000L, maxMillis),
                requestedDurationMillis > 0L ? requestedDurationMillis : defaultMillis));

        // A buy-out below the opening bid is nonsensical — reject it rather than silently drop it.
        if (buyOut > 0.0D && buyOut < startPrice) {
            return "market-buyout-too-low";
        }
        return createListingInternal(seller, startPrice, currency, duration, Math.max(0.0D, buyOut));
    }

    private String createListingInternal(final Player seller, final double price,
                                         final CurrencyType currency, final long auctionDurationMillis,
                                         final double buyOut) {
        final ItemStack held = seller.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            return "market-no-item";
        }

        // Valódi relikvia (relic_id PDC) NEM listázható: a relikvia több-lépcsős
        // kihívással szerzett, egyedi-tulajdonú tárgy — a börze a SZILÁNKOKÉ és az
        // unique anyagoké. Kapcsoló: market.allow-relic-listing (default: tilos).
        if (!configManager.getBoolean("market.allow-relic-listing", false)
                && held.hasItemMeta()
                && held.getItemMeta().getPersistentDataContainer().has(
                        org.bukkit.NamespacedKey.fromString("icesmp:relic_id"),
                        org.bukkit.persistence.PersistentDataType.STRING)) {
            return "market-relic-not-tradeable";
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
                0.0D, null, null, auction ? buyOut : 0.0D));
        seller.getInventory().setItemInMainHand(null);
        requestSave();
        return null;
    }

    /**
     * Buys a fixed-price listing using the buyer's bank balance. The configured
     * fee percent burns (money sink); the rest is credited to the seller.
     *
     * @param buyer the buyer
     * @param listingId the listing to buy
     * @return the outcome; {@link BuyOutcome#errorKey()} is null on success and
     *         {@link BuyOutcome#amount()} is the price actually deducted from the buyer
     */
    public synchronized BuyOutcome buy(final Player buyer, final UUID listingId) {
        final Listing listing = listings.get(listingId);
        if (listing == null) {
            return BuyOutcome.error("market-listing-gone");
        }

        if (listing.auction()) {
            return BuyOutcome.error("market-auction-use-bid");
        }

        if (listing.seller().equals(buyer.getUniqueId())) {
            return BuyOutcome.error("market-own-listing");
        }

        // Faction reputation adjusts what the buyer pays; both fee and seller share are
        // derived from that same amount so the trade conserves currency (no minting/burning
        // beyond the configured fee sink). Computed once, right before the deduction, so the
        // amount reported back to the caller always matches what was actually taken — even if
        // the faction relation changes a moment later.
        final double buyerCost = getEffectivePrice(buyer, listing);
        if (!currencyManager.deductFromBalance(buyer.getUniqueId(), listing.currency(), buyerCost)) {
            return BuyOutcome.error("market-insufficient-balance");
        }

        // Claim the listing atomically; if it vanished (concurrent buy/cancel), refund the buyer.
        if (listings.remove(listingId) == null) {
            currencyManager.addToBalance(buyer.getUniqueId(), listing.currency(), buyerCost);
            return BuyOutcome.error("market-listing-gone");
        }

        creditSellerShare(listing.seller(), listing.currency(), buyerCost);
        recordTransaction(listing.item(), buyerCost, listing.currency());

        requestSave();

        final Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(listing.item());
        leftovers.values().forEach(item -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), item));
        return new BuyOutcome(null, buyerCost);
    }

    /** Appends a completed sale to the capped in-memory log used by {@code /market stats}. */
    private void recordTransaction(final ItemStack item, final double price, final CurrencyType currency) {
        recentTransactions.addLast(new Transaction(item.getType(), price, currency, System.currentTimeMillis()));
        while (recentTransactions.size() > TRANSACTION_LOG_CAP) {
            recentTransactions.pollFirst();
        }
    }

    /** Credits the seller the sale amount minus the configured burn fee.
     * F14 — konjunktúra alatt az érintett valutában a díj a csökkentett érték
     * (EconomyEventManager.marketFeeOverride), amíg az ablak él. */
    private void creditSellerShare(final UUID seller, final CurrencyType currency, final double amount) {
        Double override = null;
        final EconomyEventManager economyRef = economyEventManager;
        if (economyRef != null) {
            override = economyRef.marketFeeOverride(currency);
        }
        final double feePercent = override != null ? override
                : Math.max(0.0D, Math.min(100.0D, configManager.getDouble("market.fee-percent", 10.0D)));
        final double sellerShare = amount * (1.0D - (feePercent / 100.0D));
        if (sellerShare > 0.0D) {
            currencyManager.addToBalance(seller, currency, sellerShare);
        }
    }

    /** F14: setterrel kötve (az esemény-manager a piac után épülhet a DI-sorrendben). */
    private volatile EconomyEventManager economyEventManager;

    public void setEconomyEventManager(final EconomyEventManager economyEventManager) {
        this.economyEventManager = economyEventManager;
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
     * Gets a larger "quick raise" bid for the GUI's right-click: the current bid
     * (or opening price) raised by the configured big-increment percent, never
     * below the ordinary minimum next bid.
     *
     * @param listing the auction listing
     * @return the big-jump bid amount
     */
    public double getBigBid(final Listing listing) {
        final double base = listing.hasBid() ? listing.highestBid() : listing.price();
        final double bigPercent = Math.max(1.0D,
                configManager.getDouble("market.auction.big-increment-percent", 25.0D));
        final double big = Math.ceil(base * (1.0D + bigPercent / 100.0D) * 100.0D) / 100.0D;
        return Math.max(big, getMinimumBid(listing));
    }

    /** Places the minimum next bid — convenience wrapper for the GUI's plain click. */
    public synchronized BidOutcome bid(final Player bidder, final UUID listingId) {
        final Listing listing = listings.get(listingId);
        return bid(bidder, listingId, listing == null ? 0.0D : getMinimumBid(listing));
    }

    /**
     * Places a bid of at least {@code amount} on an auction from the bidder's
     * bank balance (the amount must reach the minimum next bid). The bid is
     * escrowed immediately; the previously escrowed bid (if any) is refunded to
     * the outbid player. If the amount reaches the listing's buy-out price, the
     * auction is won on the spot ({@link BidOutcome#boughtOut()} is true).
     *
     * @param bidder the bidding player
     * @param listingId the auction listing
     * @param amount the bid amount the player wants to place
     * @return the outcome; {@link BidOutcome#errorKey()} is null on success
     */
    public synchronized BidOutcome bid(final Player bidder, final UUID listingId, final double amount) {
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

        if (!Double.isFinite(amount)) {
            return BidOutcome.error("market-bid-too-low");
        }

        // Reaching the buy-out price wins immediately at that price — this path also
        // bypasses the minimum-increment rule (the buy-out IS the closing price), and
        // the current leader may use it too (their escrowed bid refunds like any outbid).
        final boolean boughtOut = listing.hasBuyOut() && amount >= listing.buyOut();

        if (bidder.getUniqueId().equals(listing.highestBidder()) && !boughtOut) {
            return BidOutcome.error("market-already-highest");
        }
        if (!boughtOut && amount < getMinimumBid(listing)) {
            return BidOutcome.error("market-bid-too-low");
        }

        final double effective = boughtOut ? listing.buyOut() : amount;

        if (!currencyManager.deductFromBalance(bidder.getUniqueId(), listing.currency(), effective)) {
            return BidOutcome.error("market-insufficient-balance");
        }

        // Refund the outbid player's escrowed bid before recording the new one.
        if (listing.hasBid()) {
            currencyManager.addToBalance(listing.highestBidder(), listing.currency(), listing.highestBid());
        }

        final UUID previousBidder = listing.highestBidder();
        final double previousBid = listing.highestBid();

        if (boughtOut) {
            // Settle now: seller paid (minus fee), item delivered to the buyer.
            listings.remove(listingId);
            creditSellerShare(listing.seller(), listing.currency(), effective);
            recordTransaction(listing.item(), effective, listing.currency());
            queueDelivery(bidder.getUniqueId(), listing.item());
            requestSave();
            return new BidOutcome(null, effective, previousBidder, previousBid, true);
        }

        listings.put(listingId, new Listing(listing.id(), listing.seller(), listing.sellerName(),
                listing.price(), listing.currency(), listing.item(), listing.createdAt(),
                true, listing.endsAt(), effective, bidder.getUniqueId(), bidder.getName(), listing.buyOut()));
        requestSave();
        return new BidOutcome(null, effective, previousBidder, previousBid, false);
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
                    recordTransaction(listing.item(), listing.highestBid(), listing.currency());
                    queueDelivery(listing.highestBidder(), listing.item());
                } else {
                    queueDelivery(listing.seller(), listing.item());
                }
                settled.add(listing);
            }
            if (!settled.isEmpty()) {
                requestSave();
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
        requestSave();
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
            requestSave();
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

    /**
     * Builds a snapshot summary for {@code /market stats}: current listing/auction counts,
     * the 3 most-listed item types, the average sale price per currency from the recent
     * transaction log, and the single biggest recent sale.
     *
     * @return the stats snapshot
     */
    public MarketStats getStats() {
        final List<Listing> listingSnapshot = List.copyOf(listings.values());
        final int activeListings = listingSnapshot.size();
        final int activeAuctions = (int) listingSnapshot.stream().filter(Listing::auction).count();

        final Map<Material, Long> counts = listingSnapshot.stream()
                .collect(Collectors.groupingBy(listing -> listing.item().getType(), Collectors.counting()));
        final List<Map.Entry<Material, Long>> topItemTypes = counts.entrySet().stream()
                .sorted(Map.Entry.<Material, Long>comparingByValue().reversed())
                .limit(3)
                .toList();

        final List<Transaction> transactionSnapshot = List.copyOf(recentTransactions);
        final Map<CurrencyType, double[]> sumAndCount = new EnumMap<>(CurrencyType.class);
        Transaction biggest = null;
        for (final Transaction transaction : transactionSnapshot) {
            final double[] accumulator = sumAndCount.computeIfAbsent(transaction.currency(), key -> new double[2]);
            accumulator[0] += transaction.price();
            accumulator[1] += 1.0D;
            if (biggest == null || transaction.price() > biggest.price()) {
                biggest = transaction;
            }
        }
        final Map<CurrencyType, Double> averagePriceByCurrency = new EnumMap<>(CurrencyType.class);
        sumAndCount.forEach((currency, accumulator) -> averagePriceByCurrency.put(currency, accumulator[0] / accumulator[1]));

        return new MarketStats(activeListings, activeAuctions, topItemTypes, averagePriceByCurrency,
                biggest, transactionSnapshot.size());
    }
}
