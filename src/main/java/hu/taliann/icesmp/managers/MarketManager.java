package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.storage.PersistentStore;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.storage.TransactionJournal;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * <p><b>Tranzakciós invariáns.</b> Egy piaci művelet három, egymáshoz képest NEM atomi
 * tárolót érint: a listákat (market.yml), a banki egyenlegeket (currency-balances.yml)
 * és a játékos inventoryját (playerdata). Ezért minden pénzt vagy tárgyat mozgató út
 * write-ahead naplón megy át ({@link TransactionJournal}, market-journal.yml):</p>
 * <ol>
 *   <li>a tranzakció leírása (érintett egyenlegek a művelet ELŐTTI absztolút értékkel,
 *       tárgy-pillanatkép) lemezre kerül, MIELŐTT bármi módosulna;</li>
 *   <li>a hatások memóriában alkalmazódnak, majd egyetlen commit-pont következik: a
 *       valuta-fájl szinkron kiírása, utána a market.yml szinkron kiírása — utóbbi a
 *       TANÚ, mert tartalmazza a commitolt tranzakció-azonosítót;</li>
 *   <li>a játékos inventoryja CSAK a commit után változik;</li>
 *   <li>indulásnál a megmaradt bejegyzések determinisztikusan zárulnak: a tanú szerint
 *       commitolt tranzakció előre, a nem commitolt vissza — az egyenlegeket absztolút
 *       célértékre állítjuk, ezért a helyreállítás ismételve is ugyanaz (idempotens).</li>
 * </ol>
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

    /** Tranzakció-típusok a naplóban. */
    private static final String TYPE_LIST = "LIST";
    private static final String TYPE_BUY = "BUY";
    private static final String TYPE_BID = "BID";
    private static final String TYPE_SETTLE = "SETTLE";
    private static final String TYPE_DELIVER = "DELIVER";

    /**
     * A játékos-oldali visszaigazolás jelzői. A jelző beállítása és az inventory
     * módosítása UGYANABBA a playerdata-fájlba kerül, tehát a lemezen nem létezhet
     * olyan állapot, ahol az inventory-változás tartós, de a jelző nem — ebből tudja
     * a helyreállítás, hogy a tárgy-oldal véglegesült-e.
     *
     * <p>A jelzőt SOHA nem töröljük a tranzakció lezárása előtt: a törlés (ha közben a
     * napló-írás elakad) a lemezen tartós elvételt mutatna nem-tartósnak, és a tétel
     * törlésével a tárgy elveszne.
     *
     * <p>Az átvétel-jelző TRANZAKCIÓNKÉNTI kulcsot kap: egyetlen közös kulcs egyetlen értéket
     * tárolt, pedig több nyitott LIST-bejegyzés is létezhet — az újabb listázás felülírta a
     * régebbi jelzőjét, ezért annak helyreállítása „a tárgy nálad maradt"-ot látott és VÉGLEG
     * eldobta a tárgyat.
     */
    private static final String TAKE_ACK_PREFIX = "icesmp:market_take_ack_";
    private static final NamespacedKey DELIVERY_ACK_KEY = NamespacedKey.fromString("icesmp:market_delivery_ack");

    private static NamespacedKey takeAckKey(final TransactionJournal.Entry entry) {
        return NamespacedKey.fromString(TAKE_ACK_PREFIX + entry.id());
    }

    /** Az egyenleg-helyreállítás toleranciája (a pénz két tizedesig értelmes). */
    private static final double MONEY_EPSILON = 0.005D;

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
    private final TransactionJournal journal;
    private final Map<UUID, Listing> listings = new ConcurrentHashMap<>();
    // Items owed to (possibly offline) players: auction wins and unsold auction
    // returns. Delivered on the owner's own region thread (join / /market claim).
    private final Map<UUID, List<ItemStack>> pendingDeliveries = new ConcurrentHashMap<>();
    /**
     * A commit-pontot átlépett tranzakciók azonosítói. A market.yml-be íródnak, és
     * EZ a tanú: ha egy félbehagyott tranzakció azonosítója szerepel a lemezen, akkor
     * a hatásai véglegesültek (előre kell zárni), ha nem, akkor egyáltalán nem
     * véglegesültek (vissza kell forgatni).
     */
    private final Set<String> committedTxns = ConcurrentHashMap.newKeySet();
    /**
     * Crash után publikálhatatlan tételek: a tétel a lemezen van, de nem tudjuk, hogy
     * az eladó inventoryjából tartósan kikerült-e a tárgy. Amíg az eladó belépésével ez
     * el nem dől, a tétel nem látszik és nem vehető meg (különben duplikálódna a tárgy).
     */
    private final Set<UUID> unconfirmedListings = ConcurrentHashMap.newKeySet();
    /**
     * Helyreállításra váró bejegyzések tulajdonos szerint. Az inventory-oldalt csak a
     * játékos PDC-jelzője dönti el, azt pedig kizárólag belépéskor, a játékos SAJÁT
     * régió-szálán olvashatjuk ki.
     */
    private final Map<UUID, List<TransactionJournal.Entry>> playerRecoveries = new ConcurrentHashMap<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private final AtomicBoolean journalWarningLogged = new AtomicBoolean(false);
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
        this.journal = new TransactionJournal(new File(plugin.getDataFolder(), "market-journal.yml"),
                plugin.getLogger());
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

    public synchronized void load() {
        listings.clear();
        pendingDeliveries.clear();
        committedTxns.clear();
        unconfirmedListings.clear();
        playerRecoveries.clear();
        journal.load();

        if (storageFile.exists()) {
            try {
                final YamlConfiguration yaml = YamlStore.loadTracked(storageFile, plugin.getLogger());
                loadListings(yaml);
                loadPendingDeliveries(yaml);
                committedTxns.addAll(yaml.getStringList("committed-txn"));

                plugin.getLogger().info("Loaded " + listings.size() + " market listing(s) and "
                        + pendingDeliveries.size() + " pending deliver(y/ies).");
            } catch (final Exception exception) {
                plugin.getLogger().severe("Failed to load market.yml: " + exception.getMessage());
            }
        }

        recoverJournal();
    }

    private void loadListings(final YamlConfiguration yaml) {
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

    private void loadPendingDeliveries(final YamlConfiguration yaml) {
        final ConfigurationSection deliveries = yaml.getConfigurationSection("pending-deliveries");
        if (deliveries == null) {
            return;
        }

        for (final String playerKey : deliveries.getKeys(false)) {
            try {
                final UUID owner = UUID.fromString(playerKey);
                final List<ItemStack> items = readItems(deliveries.getList(playerKey));
                if (!items.isEmpty()) {
                    pendingDeliveries.put(owner, items);
                }
            } catch (final IllegalArgumentException ignored) {
                // Skip malformed entries.
            }
        }
    }

    /**
     * Debounced async flush for read-only-ish bookkeeping (a napló-helyreállítás utáni
     * takarítás): a tranzakciós utak ezt NEM használhatják, ott a commit-pont szinkron.
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
        flush();
    }

    /**
     * A market.yml kiírása. A visszatérési érték a COMMIT-PONT eredménye: false esetén a
     * lemezen a tranzakció előtti állapot maradt (a saveAtomic vagy megtagadta az írást,
     * vagy meghiúsult a rename), ezért a hívó nem zárhatja le a napló-bejegyzést.
     */
    private boolean flush() {
        if (YamlStore.isLoadFailed(storageFile)) {
            return false;
        }

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
        if (!committedTxns.isEmpty()) {
            yaml.set("committed-txn", new ArrayList<>(committedTxns));
        }

        try {
            YamlStore.saveAtomic(storageFile, yaml);
            return true;
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save market.yml: " + exception.getMessage());
            return false;
        }
    }

    /** @return a tétel, ha létező ÉS publikálható (a crash-helyreállításra várók nem azok) */
    public Listing getListing(final UUID listingId) {
        if (listingId == null || unconfirmedListings.contains(listingId)) {
            return null;
        }
        return listings.get(listingId);
    }

    /** @return every listing, newest first */
    public List<Listing> getListingsSorted() {
        return listings.values().stream()
                .filter(listing -> !unconfirmedListings.contains(listing.id()))
                .sorted(Comparator.comparingLong(Listing::createdAt).reversed())
                .toList();
    }

    public long countListingsOf(final UUID seller) {
        // A meg nem erősített (helyreállításra váró) tétel REJTETT — nem foglalhat helyet a
        // max-listings-per-player limitben, mert a játékos nem is látja/nem vonhatja vissza.
        return listings.values().stream()
                .filter(listing -> !unconfirmedListings.contains(listing.id()))
                .filter(listing -> listing.seller().equals(seller)).count();
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

        if (!storageHealthy()) {
            return "market-journal-unavailable";
        }

        final UUID id = UUID.randomUUID();
        final long now = System.currentTimeMillis();
        final boolean auction = auctionDurationMillis > 0L;
        final Listing listing = new Listing(id, seller.getUniqueId(), seller.getName(), price,
                currency, held.clone(), now, auction, auction ? now + auctionDurationMillis : 0L,
                0.0D, null, null, auction ? buyOut : 0.0D);

        final TransactionJournal.Entry entry = journal.create(TYPE_LIST);
        entry.data().set("owner", seller.getUniqueId().toString());
        entry.data().set("listing-id", id.toString());
        entry.data().set("item", listing.item());
        if (!journal.prepare(entry)) {
            return "market-journal-unavailable";
        }

        // A jelzőt és az elvételt ugyanaz a playerdata-írás viszi lemezre, ezért a
        // helyreállítás a jelzőből tudja, hogy a tárgy tartósan kikerült-e a kézből.
        seller.getPersistentDataContainer().set(takeAckKey(entry), PersistentDataType.STRING,
                entry.id().toString());
        seller.getInventory().setItemInMainHand(null);
        persistPlayer(seller);

        listings.put(id, listing);
        if (commitState(entry, false)) {
            finish(entry);
        } else {
            plugin.getLogger().severe("A piaci listázás commitja nem sikerült (" + entry.id()
                    + ") — a napló nyitva marad, a következő indulás helyreállítja.");
        }
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
        final Listing listing = getListing(listingId);
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
        if (currencyManager.getBalance(buyer.getUniqueId(), listing.currency()) < buyerCost) {
            return BuyOutcome.error("market-insufficient-balance");
        }

        if (!storageHealthy()) {
            return BuyOutcome.error("market-journal-unavailable");
        }

        final double sellerShare = sellerShare(listing.currency(), buyerCost);
        final TransactionJournal.Entry entry = journal.create(TYPE_BUY);
        entry.data().set("owner", buyer.getUniqueId().toString());
        entry.data().set("listing-id", listing.id().toString());
        entry.data().set("item", listing.item());
        recordMoney(entry, 0, buyer.getUniqueId(), listing.currency(), -buyerCost);
        recordMoney(entry, 1, listing.seller(), listing.currency(), sellerShare);
        if (!journal.prepare(entry)) {
            return BuyOutcome.error("market-journal-unavailable");
        }

        if (!currencyManager.deductFromBalance(buyer.getUniqueId(), listing.currency(), buyerCost)) {
            // Semmi nem történt még: a bejegyzés nyomtalanul lezárható.
            journal.complete(entry);
            return BuyOutcome.error("market-insufficient-balance");
        }

        if (sellerShare > 0.0D) {
            currencyManager.addToBalance(listing.seller(), listing.currency(), sellerShare);
        }
        listings.remove(listing.id());
        queueDelivery(buyer.getUniqueId(), listing.item());
        recordTransaction(listing.item(), buyerCost, listing.currency());
        commitAndFinish(entry, true);

        // A vevő inventoryja CSAK a commit után változik: a tárgy a már tartós
        // kifizetés-várólistáról kerül át, saját (naplózott) tranzakcióban.
        deliverPending(buyer);
        return new BuyOutcome(null, buyerCost);
    }

    /** Appends a completed sale to the capped in-memory log used by {@code /market stats}. */
    private void recordTransaction(final ItemStack item, final double price, final CurrencyType currency) {
        recentTransactions.addLast(new Transaction(item.getType(), price, currency, System.currentTimeMillis()));
        while (recentTransactions.size() > TRANSACTION_LOG_CAP) {
            recentTransactions.pollFirst();
        }
    }

    /** Az eladónak jutó rész: az összeg mínusz az elégő díj.
     * F14 — konjunktúra alatt az érintett valutában a díj a csökkentett érték
     * (EconomyEventManager.marketFeeOverride), amíg az ablak él. */
    private double sellerShare(final CurrencyType currency, final double amount) {
        Double override = null;
        final EconomyEventManager economyRef = economyEventManager;
        if (economyRef != null) {
            override = economyRef.marketFeeOverride(currency);
        }
        final double feePercent = override != null ? override
                : Math.max(0.0D, Math.min(100.0D, configManager.getDouble("market.fee-percent", 10.0D)));
        return Math.max(0.0D, amount * (1.0D - (feePercent / 100.0D)));
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
        final Listing listing = getListing(listingId);
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
        final Listing listing = getListing(listingId);
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
        if (currencyManager.getBalance(bidder.getUniqueId(), listing.currency()) < effective) {
            return BidOutcome.error("market-insufficient-balance");
        }

        if (!storageHealthy()) {
            return BidOutcome.error("market-journal-unavailable");
        }

        final UUID previousBidder = listing.highestBidder();
        final double previousBid = listing.highestBid();
        final double share = boughtOut ? sellerShare(listing.currency(), effective) : 0.0D;

        final TransactionJournal.Entry entry = journal.create(TYPE_BID);
        entry.data().set("owner", bidder.getUniqueId().toString());
        entry.data().set("listing-id", listing.id().toString());
        entry.data().set("item", listing.item());
        recordMoney(entry, 0, bidder.getUniqueId(), listing.currency(), -effective);
        if (previousBidder != null) {
            recordMoney(entry, 1, previousBidder, listing.currency(), previousBid);
        }
        if (boughtOut && share > 0.0D) {
            recordMoney(entry, 2, listing.seller(), listing.currency(), share);
        }
        if (!journal.prepare(entry)) {
            return BidOutcome.error("market-journal-unavailable");
        }

        if (!currencyManager.deductFromBalance(bidder.getUniqueId(), listing.currency(), effective)) {
            journal.complete(entry);
            return BidOutcome.error("market-insufficient-balance");
        }

        // Refund the outbid player's escrowed bid before recording the new one.
        if (previousBidder != null) {
            currencyManager.addToBalance(previousBidder, listing.currency(), previousBid);
        }

        if (boughtOut) {
            // Settle now: seller paid (minus fee), item delivered to the buyer.
            listings.remove(listing.id());
            if (share > 0.0D) {
                currencyManager.addToBalance(listing.seller(), listing.currency(), share);
            }
            recordTransaction(listing.item(), effective, listing.currency());
            queueDelivery(bidder.getUniqueId(), listing.item());
            commitAndFinish(entry, true);
            return new BidOutcome(null, effective, previousBidder, previousBid, true);
        }

        listings.put(listing.id(), new Listing(listing.id(), listing.seller(), listing.sellerName(),
                listing.price(), listing.currency(), listing.item(), listing.createdAt(),
                true, listing.endsAt(), effective, bidder.getUniqueId(), bidder.getName(), listing.buyOut()));
        commitAndFinish(entry, true);
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
                if (!listing.auction() || now < listing.endsAt() || unconfirmedListings.contains(listing.id())) {
                    continue;
                }
                if (!storageHealthy()) {
                    // Napló nélkül a lezárás nem visszaforgatható — az aukció maradjon nyitva.
                    if (journalWarningLogged.compareAndSet(false, true)) {
                        plugin.getLogger().severe("Az aukció-lezárás elhalasztva: a piaci tranzakciós "
                                + "napló nem írható. Javítsd a market-journal.yml / market.yml fájlt.");
                    }
                    break;
                }

                final double share = listing.hasBid()
                        ? sellerShare(listing.currency(), listing.highestBid()) : 0.0D;
                final TransactionJournal.Entry entry = journal.create(TYPE_SETTLE);
                entry.data().set("listing-id", listing.id().toString());
                entry.data().set("item", listing.item());
                if (share > 0.0D) {
                    recordMoney(entry, 0, listing.seller(), listing.currency(), share);
                }
                if (!journal.prepare(entry)) {
                    break;
                }

                listings.remove(listing.id());
                if (listing.hasBid()) {
                    if (share > 0.0D) {
                        currencyManager.addToBalance(listing.seller(), listing.currency(), share);
                    }
                    recordTransaction(listing.item(), listing.highestBid(), listing.currency());
                    queueDelivery(listing.highestBidder(), listing.item());
                } else {
                    queueDelivery(listing.seller(), listing.item());
                }
                commitAndFinish(entry, share > 0.0D);
                settled.add(listing);
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
        return pendingDeliveries.containsKey(playerId) || playerRecoveries.containsKey(playerId);
    }

    /**
     * Hands every queued item to the player. MUST be called on the player's own
     * region thread (command execution / join event / entity scheduler task).
     *
     * @param player the recipient
     * @return the number of items delivered
     */
    public synchronized int deliverPending(final Player player) {
        // Előbb a crash utáni, PDC-alapú eldöntés: ez tehet vissza tételeket a várólistára.
        resolvePlayerRecoveries(player);

        final List<ItemStack> items = pendingDeliveries.get(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            pendingDeliveries.remove(player.getUniqueId());
            return 0;
        }

        if (!storageHealthy()) {
            // A tételek a várólistán maradnak: naplózás nélkül az átadás elveszhetne.
            return 0;
        }

        final TransactionJournal.Entry entry = journal.create(TYPE_DELIVER);
        entry.data().set("owner", player.getUniqueId().toString());
        // Klónok: az inventory-hozzáadás módosíthatja az átadott stackek darabszámát,
        // a naplóban viszont az ÁTADÁS ELŐTTI állapotnak kell maradnia.
        entry.data().set("items", items.stream().map(ItemStack::clone).toList());
        if (!journal.prepare(entry)) {
            return 0;
        }

        pendingDeliveries.remove(player.getUniqueId());
        if (!commitState(entry, false)) {
            // A lemezen a tételek MÉG a várólistán vannak — memóriában is oda kerülnek vissza.
            pendingDeliveries.put(player.getUniqueId(), items);
            committedTxns.remove(entry.id().toString());
            journal.complete(entry);
            return 0;
        }

        // Inventory CSAK a commit után: innentől a market.yml már nem tartozik a tárggyal.
        player.getPersistentDataContainer().set(DELIVERY_ACK_KEY, PersistentDataType.STRING,
                entry.id().toString());
        for (final ItemStack item : items) {
            final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        persistPlayer(player);
        finish(entry);
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
        final List<Listing> cancellable = new ArrayList<>();
        for (final Listing listing : List.copyOf(listings.values())) {
            if (!listing.seller().equals(player.getUniqueId()) || listing.hasBid()
                    || unconfirmedListings.contains(listing.id())) {
                continue;
            }
            cancellable.add(listing);
        }

        if (cancellable.isEmpty()) {
            return 0;
        }
        if (!storageHealthy()) {
            return 0;
        }

        // A tétel-törlés és a tárgy várólistára tétele UGYANABBA a fájlba íródik, tehát
        // atomi: crash után vagy mindkettő megvan, vagy egyik sem. A tárgy csak ezután,
        // naplózott átadással kerül az inventoryba.
        for (final Listing listing : cancellable) {
            listings.remove(listing.id());
            queueDelivery(player.getUniqueId(), listing.item());
        }
        if (!flush()) {
            for (final Listing listing : cancellable) {
                listings.put(listing.id(), listing);
                removeLastQueued(player.getUniqueId());
            }
            return 0;
        }

        deliverPending(player);
        return cancellable.size();
    }

    /**
     * Whether the player still has an active listing that couldn't be cancelled
     * (an auction with a live bid).
     */
    public boolean hasLockedAuction(final UUID seller) {
        return listings.values().stream()
                .filter(listing -> !unconfirmedListings.contains(listing.id()))
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
        final List<Listing> listingSnapshot = getListingsSorted();
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

    // ---------------------------------------------------------------------
    // Tranzakció-kezelés
    // ---------------------------------------------------------------------

    /** Naplózni és commitolni csak akkor lehet, ha MINDKÉT állapotfájl írható. */
    private boolean storageHealthy() {
        return journal.isHealthy() && !YamlStore.isLoadFailed(storageFile);
    }

    /**
     * A tranzakció COMMIT-PONTJA. Sorrend-invariáns: előbb a valuta-fájl, utána a
     * market.yml — utóbbi hordozza a commitolt azonosítót, tehát a tanú akkor és csak
     * akkor kerül lemezre, ha a pénz-oldal írása már megtörtént. Szinkron írás, mert
     * egy debounce-olt mentés pont azt a rést hagyná nyitva, amit a napló bezár.
     */
    private boolean commitState(final TransactionJournal.Entry entry, final boolean moneyTouched) {
        committedTxns.add(entry.id().toString());
        if (moneyTouched) {
            currencyManager.save();
        }
        return flush();
    }

    /**
     * A bejegyzés lezárása: a tanú-azonosító CSAK akkor dobható el, ha a napló-bejegyzés
     * bizonyítottan eltűnt a lemezről is. Feltétel nélküli törléssel a napló-írás bukása
     * után a bejegyzés a lemezen maradt, a tanú viszont nem: a következő mentés tanú nélkül
     * írta ki a market.yml-t, az újraindulás „nem commitolt"-nak minősítette a már kifizetett
     * vásárlást és visszaforgatta a pénzt — a tárgy közben tartósan a vevőnél volt.
     */
    private void finish(final TransactionJournal.Entry entry) {
        if (!journal.complete(entry)) {
            plugin.getLogger().severe("A piaci napló lezárása nem sikerült (" + entry.type() + " "
                    + entry.id() + ") — a commit-tanú MARAD, hogy az újraindulás előre zárja "
                    + "a tranzakciót (nincs visszaforgatás a már kifizetett tétel ellen).");
            return;
        }
        committedTxns.remove(entry.id().toString());
        committedTxns.remove(resolutionKey(entry));
    }

    /**
     * Commit + lezárás. Ha a commit-írás nem sikerült, a bejegyzés NYITVA marad: így a
     * következő induláskor a tanú dönt (tiszta leállításnál a mentés kiírja a
     * tanút → előre zárás, crashnél nincs tanú → visszaforgatás), és mindkét irány
     * konzisztens állapotot hagy.
     */
    private void commitAndFinish(final TransactionJournal.Entry entry, final boolean moneyTouched) {
        if (commitState(entry, moneyTouched)) {
            finish(entry);
            return;
        }
        plugin.getLogger().severe("A piaci tranzakció commitja nem sikerült (" + entry.type() + " "
                + entry.id() + ") — a napló nyitva marad, a következő indulás helyreállítja.");
    }

    /**
     * Az érintett egyenleg naplózása: a művelet ELŐTTI absztolút érték + a szándékolt
     * delta. A helyreállítás absztolút célértékre állít, ezért többszöri lefutás
     * ugyanazt az eredményt adja.
     */
    private void recordMoney(final TransactionJournal.Entry entry, final int index,
                             final UUID player, final CurrencyType currency, final double delta) {
        final String base = "money." + index;
        entry.data().set(base + ".player", player.toString());
        entry.data().set(base + ".currency", currency.name());
        entry.data().set(base + ".before", currencyManager.getBalance(player, currency));
        entry.data().set(base + ".delta", delta);
    }

    /** Van-e a lemezen tanú a bejegyzés commitjáról. */
    private boolean isCommitted(final TransactionJournal.Entry entry) {
        return committedTxns.contains(entry.id().toString());
    }

    /** Tanú-azonosító a helyreállítás SAJÁT írásához (a visszatétel is csak egyszer futhat le). */
    private String resolutionKey(final TransactionJournal.Entry entry) {
        return entry.id() + ":r";
    }

    /**
     * Azonnali playerdata-kiírás: az inventory-változás és a hozzá tartozó PDC-jelző így
     * egyetlen fájlba kerül, tehát a helyreállítás a jelzőből biztosan tudja, hogy a
     * tárgy-oldal tartós-e. Ha a mentés nem megy, a HELYESSÉG nem sérül — a jelző csak
     * később (a következő playerdata-mentéssel) válik tartóssá, és addig a helyreállítás
     * a biztonságos (nem duplikáló) irányt választja.
     */
    private void persistPlayer(final Player player) {
        if (!playerSaveSupported) {
            return;
        }
        try {
            player.saveData();
        } catch (final RuntimeException | LinkageError failure) {
            // Egyszer próbálkozunk: ha a platform nem engedi a célzott playerdata-írást,
            // a további hívások csak a logot szemetelnék.
            playerSaveSupported = false;
            plugin.getLogger().warning("A piac nem tudja azonnal kiírni a játékos-adatot, a "
                    + "megerősítés a következő playerdata-mentésig tolódik: " + failure);
        }
    }

    /** Támogatja-e a platform a célzott playerdata-írást (lásd {@link #persistPlayer}). */
    private volatile boolean playerSaveSupported = true;

    // ---------------------------------------------------------------------
    // Helyreállítás
    // ---------------------------------------------------------------------

    /**
     * Determinista helyreállítás induláskor. A pénz-oldalt itt zárjuk (a tanú alapján
     * előre vagy vissza), a tárgy-oldalt a tulajdonos belépéséig halasztjuk: az
     * inventory tartósságát kizárólag a játékos PDC-jelzője mondja meg.
     */
    private void recoverJournal() {
        final List<TransactionJournal.Entry> pending = journal.pending();
        if (pending.isEmpty()) {
            committedTxns.clear();
            return;
        }

        plugin.getLogger().warning("Piaci tranzakciós napló: " + pending.size()
                + " félbehagyott tranzakció — helyreállítás indul.");

        for (final TransactionJournal.Entry entry : pending) {
            final boolean committed = isCommitted(entry);
            switch (entry.type()) {
                case TYPE_BUY, TYPE_BID, TYPE_SETTLE -> {
                    repairMoney(entry, committed);
                    finish(entry);
                }
                case TYPE_LIST -> deferToOwner(entry, committed);
                case TYPE_DELIVER -> {
                    if (committed) {
                        deferToOwner(entry, true);
                    } else {
                        // A tételek tartósan a várólistán maradtak — nincs mit javítani.
                        finish(entry);
                    }
                }
                default -> finish(entry);
            }
        }

        // Csak a MÉG nyitott bejegyzések tanú-azonosítóira van szükség.
        final Set<String> keep = new HashSet<>();
        for (final List<TransactionJournal.Entry> entries : playerRecoveries.values()) {
            for (final TransactionJournal.Entry entry : entries) {
                keep.add(entry.id().toString());
                keep.add(resolutionKey(entry));
            }
        }
        committedTxns.retainAll(keep);
        requestSave();

        // Ha a tulajdonos már online (pl. /icesmp reload), a saját szálán azonnal eldönthető.
        for (final UUID owner : List.copyOf(playerRecoveries.keySet())) {
            final Player online = Bukkit.getPlayer(owner);
            if (online != null) {
                online.getScheduler().run(plugin, task -> deliverPending(online), null);
            }
        }
    }

    /**
     * A bejegyzés a tulajdonos belépéséig várakozik. Commitolt LIST esetén a tétel a
     * lemezen van, de a tárgy még lehet az eladó inventoryjában is — amíg ez el nem dől,
     * a tétel nem publikálható (különben a tárgy duplikálódna).
     */
    private void deferToOwner(final TransactionJournal.Entry entry, final boolean committed) {
        final UUID owner = parseUuid(entry.data().getString("owner"));
        if (owner == null) {
            finish(entry);
            return;
        }

        if (committed && TYPE_LIST.equals(entry.type())) {
            final UUID listingId = parseUuid(entry.data().getString("listing-id"));
            if (listingId != null) {
                unconfirmedListings.add(listingId);
            }
        }
        playerRecoveries.computeIfAbsent(owner, key -> new ArrayList<>()).add(entry);
        plugin.getLogger().warning("Piaci tranzakció (" + entry.type() + " " + entry.id()
                + ") a tulajdonos belépéséig várakozik: " + owner);
    }

    /**
     * Az érintett egyenlegek absztolút célértékre állítása: {@code forward} esetén a
     * tranzakció UTÁNI, egyébként az ELŐTTI állapot. Absztolút célérték = idempotens
     * helyreállítás (a többszöri lefutás nem visz kétszer pénzt).
     */
    private void repairMoney(final TransactionJournal.Entry entry, final boolean forward) {
        final ConfigurationSection money = entry.data().getConfigurationSection("money");
        if (money == null) {
            return;
        }

        for (final String index : money.getKeys(false)) {
            final UUID player = parseUuid(money.getString(index + ".player"));
            final CurrencyType currency = CurrencyType.fromInput(money.getString(index + ".currency", ""));
            if (player == null || currency == null) {
                continue;
            }

            final double before = money.getDouble(index + ".before");
            final double target = forward
                    ? Math.max(0.0D, before + money.getDouble(index + ".delta")) : before;
            final double current = currencyManager.getBalance(player, currency);
            final double difference = target - current;
            if (Math.abs(difference) < MONEY_EPSILON) {
                continue;
            }

            if (difference > 0.0D) {
                currencyManager.addToBalance(player, currency, difference);
            } else if (!currencyManager.deductFromBalance(player, currency, -difference)) {
                plugin.getLogger().severe("Piac-helyreállítás: " + player + " egyenlege ("
                        + currency.name() + ") nem fedezte a korrekciót — kézi ellenőrzés kell.");
                continue;
            }
            plugin.getLogger().warning("Piac-helyreállítás: " + player + " " + currency.name()
                    + " egyenlege korrigálva " + currencyManager.formatBalance(current) + " -> "
                    + currencyManager.formatBalance(target) + " (" + entry.type() + ")");
        }
    }

    /**
     * A játékos saját régió-szálán eldönti a rá váró félbehagyott tranzakciókat.
     * A jelzők a playerdatából jönnek, tehát megmondják, hogy az inventory-oldal
     * tartósan megtörtént-e.
     */
    private void resolvePlayerRecoveries(final Player player) {
        final List<TransactionJournal.Entry> entries = playerRecoveries.remove(player.getUniqueId());
        if (entries == null) {
            return;
        }

        for (final TransactionJournal.Entry entry : entries) {
            if (TYPE_LIST.equals(entry.type())) {
                resolveListRecovery(player, entry);
            } else {
                resolveDeliveryRecovery(player, entry);
            }
        }
    }

    private void resolveListRecovery(final Player seller, final TransactionJournal.Entry entry) {
        final UUID listingId = parseUuid(entry.data().getString("listing-id"));
        final NamespacedKey ackKey = takeAckKey(entry);
        final boolean takePersisted = entry.id().toString().equals(
                seller.getPersistentDataContainer().get(ackKey, PersistentDataType.STRING));

        if (isCommitted(entry)) {
            if (takePersisted) {
                // A tárgy tartósan kikerült a kézből ÉS a tétel a lemezen van: publikálható.
                if (listingId != null) {
                    unconfirmedListings.remove(listingId);
                }
                finish(entry);
                seller.getPersistentDataContainer().remove(ackKey);
                seller.sendMessage(messageManager.getMessage("market-recovery-listing-restored",
                        "&aA megszakadt listázásod helyreállt — a tételed újra elérhető a piacon."));
                return;
            }

            // A lemezen a tárgy az inventoryban maradt: a tétel duplikátum lenne.
            if (listingId != null) {
                listings.remove(listingId);
                unconfirmedListings.remove(listingId);
            }
            if (flush()) {
                finish(entry);
            }
            seller.sendMessage(messageManager.getMessage("market-recovery-listing-cancelled",
                    "&eA szerver leállása félbevágta a listázásodat — a tárgy nálad maradt, listázd újra."));
            return;
        }

        if (!takePersisted) {
            // Semmi nem véglegesült: a tárgy a helyén van.
            finish(entry);
            return;
        }

        // A tárgy tartósan kikerült, de a tétel nem jött létre — vissza kell adni.
        final ItemStack item = entry.data().getItemStack("item");
        if (!committedTxns.contains(resolutionKey(entry))) {
            if (item != null) {
                queueDelivery(seller.getUniqueId(), item);
            }
            committedTxns.add(resolutionKey(entry));
            if (!flush()) {
                // A visszatétel nem tartós: a bejegyzés maradjon nyitva, újra próbálkozunk.
                committedTxns.remove(resolutionKey(entry));
                if (item != null) {
                    removeLastQueued(seller.getUniqueId());
                }
                playerRecoveries.computeIfAbsent(seller.getUniqueId(), key -> new ArrayList<>()).add(entry);
                return;
            }
        }
        finish(entry);
        // A tranzakciónkénti jelző lezárás után törölhető: nélküle a PDC korlátlanul nőne.
        seller.getPersistentDataContainer().remove(ackKey);
        seller.sendMessage(messageManager.getMessage("market-recovery-item-returned",
                "&eA szerver leállása félbevágta a listázásodat — a tárgyat visszakapod."));
    }

    private void resolveDeliveryRecovery(final Player player, final TransactionJournal.Entry entry) {
        final boolean delivered = entry.id().toString().equals(
                player.getPersistentDataContainer().get(DELIVERY_ACK_KEY, PersistentDataType.STRING));
        if (delivered) {
            finish(entry);
            return;
        }

        if (!committedTxns.contains(resolutionKey(entry))) {
            final List<ItemStack> items = readItems(entry.data().getList("items"));
            for (final ItemStack item : items) {
                queueDelivery(player.getUniqueId(), item);
            }
            committedTxns.add(resolutionKey(entry));
            if (!flush()) {
                committedTxns.remove(resolutionKey(entry));
                for (int i = 0; i < items.size(); i++) {
                    removeLastQueued(player.getUniqueId());
                }
                playerRecoveries.computeIfAbsent(player.getUniqueId(), key -> new ArrayList<>()).add(entry);
                return;
            }
        }
        finish(entry);
        player.sendMessage(messageManager.getMessage("market-recovery-delivery-retry",
                "&eA szerver leállása félbevágta az átvételt — a tételeid újra elérhetők."));
    }

    /** A várólista utolsó elemének visszavonása (meghiúsult visszatétel esetén). */
    private void removeLastQueued(final UUID owner) {
        final List<ItemStack> queued = pendingDeliveries.get(owner);
        if (queued == null || queued.isEmpty()) {
            return;
        }
        queued.remove(queued.size() - 1);
        if (queued.isEmpty()) {
            pendingDeliveries.remove(owner);
        }
    }

    private List<ItemStack> readItems(final List<?> raw) {
        final List<ItemStack> items = new ArrayList<>();
        if (raw == null) {
            return items;
        }
        for (final Object entry : raw) {
            if (entry instanceof ItemStack item && !item.getType().isAir()) {
                items.add(item);
            }
        }
        return items;
    }

    private UUID parseUuid(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }
}
