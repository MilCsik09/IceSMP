package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.TransactionJournal;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import hu.taliann.icesmp.itemization.ItemIdentityService;
import hu.taliann.icesmp.itemization.ItemInstance;
import hu.taliann.icesmp.itemization.ItemTemplate;
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
 * Player market with a shared YAML/WAL aggregate. Player-owned crash witnesses are item metadata,
 * never player PDC: a listing take uses a short-lived marker on the exact hand item and delivery
 * uses a transaction marker on every granted stack. Complete, absent and partial evidence are
 * distinguished during restart recovery.
 */
public final class MarketManager implements PersistentStore {

    public record Listing(UUID id, UUID seller, String sellerName, double price,
                          CurrencyType currency, ItemStack item, long createdAt,
                          boolean auction, long endsAt, double highestBid,
                          UUID highestBidder, String highestBidderName, double buyOut) {
        public boolean hasBid() { return highestBidder != null; }
        public boolean hasBuyOut() { return buyOut > 0.0D; }
    }

    public record BidOutcome(String errorKey, double amount, UUID previousBidder,
                             double previousBid, boolean boughtOut) {
        static BidOutcome error(final String errorKey) {
            return new BidOutcome(errorKey, 0.0D, null, 0.0D, false);
        }
    }

    public record BuyOutcome(String errorKey, double amount) {
        static BuyOutcome error(final String errorKey) {
            return new BuyOutcome(errorKey, 0.0D);
        }
    }

    public record Transaction(Material itemType, double price,
                              CurrencyType currency, long timestamp) { }

    public record MarketStats(int activeListings, int activeAuctions,
                              List<Map.Entry<Material, Long>> topItemTypes,
                              Map<CurrencyType, Double> averagePriceByCurrency,
                              Transaction biggestRecentSale, int recentSaleCount) { }

    public record ItemMetadata(String templateId, hu.taliann.icesmp.itemization.ItemRarity rarity,
                               int itemLevel, ItemTemplate.Slot slot, Set<String> classRestrictions,
                               String signatureEffectId, int socketCount, String ascensionState,
                               double averageRollQuality, String setId, Set<String> statIds) { }

    public record ItemFilter(String templateId,
                             hu.taliann.icesmp.itemization.ItemRarity rarity,
                             Integer minimumItemLevel, Integer maximumItemLevel,
                             ItemTemplate.Slot slot, String classId,
                             String signatureEffectId, Integer minimumSocketCount,
                             String ascensionState, Double minimumRollQuality,
                             String setId, String requiredStatId) { }

    private enum TakeEvidence { TAGGED_PRESENT, ORIGINAL_PRESENT, ABSENT, AMBIGUOUS }

    private record DeliveryEvidence(int expectedAmount, int taggedAmount, boolean invalid) {
        boolean none() { return taggedAmount == 0 && !invalid; }
        boolean complete() { return taggedAmount == expectedAmount && !invalid; }
        boolean partial() { return taggedAmount > 0 && taggedAmount < expectedAmount && !invalid; }
    }

    private static final int TRANSACTION_LOG_CAP = 50;
    private static final String TYPE_LIST = "LIST";
    private static final String TYPE_BUY = "BUY";
    private static final String TYPE_BID = "BID";
    private static final String TYPE_SETTLE = "SETTLE";
    private static final String TYPE_DELIVER = "DELIVER";
    private static final double MONEY_EPSILON = 0.005D;
    private static final NamespacedKey TAKE_MARKER_KEY =
            NamespacedKey.fromString("icesmp:market_take_txn");
    private static final NamespacedKey DELIVERY_MARKER_KEY =
            NamespacedKey.fromString("icesmp:market_delivery_txn");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final FactionRelationManager relationManager;
    private final MessageManager messageManager;
    private final ItemIdentityService itemIdentity;
    private final File storageFile;
    private final TransactionJournal journal;
    private final Map<UUID, Listing> listings = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingDeliveries = new ConcurrentHashMap<>();
    private final Set<String> committedTxns = ConcurrentHashMap.newKeySet();
    private final Set<UUID> unconfirmedListings = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<TransactionJournal.Entry>> playerRecoveries = new ConcurrentHashMap<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private final AtomicBoolean journalWarningLogged = new AtomicBoolean(false);
    private final Deque<Transaction> recentTransactions = new ConcurrentLinkedDeque<>();
    private volatile boolean playerSaveSupported = true;
    private volatile EconomyEventManager economyEventManager;

    public MarketManager(final JavaPlugin plugin, final ConfigManager configManager,
                         final CurrencyManager currencyManager,
                         final FactionManager factionManager,
                         final FactionRelationManager relationManager,
                         final MessageManager messageManager,
                         final ItemIdentityService itemIdentity) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currencyManager = currencyManager;
        this.factionManager = factionManager;
        this.relationManager = relationManager;
        this.messageManager = messageManager;
        this.itemIdentity = itemIdentity;
        this.storageFile = new File(plugin.getDataFolder(), "market.yml");
        this.journal = new TransactionJournal(
                new File(plugin.getDataFolder(), "market-journal.yml"), plugin.getLogger());
        plugin.getDataFolder().mkdirs();
    }

    public double getEffectivePrice(final Player buyer, final Listing listing) {
        final double multiplier = relationManager.getMarketPriceMultiplier(
                factionManager.getChosenFaction(buyer.getUniqueId()).orElse(null),
                factionManager.getChosenFaction(listing.seller()).orElse(null));
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
                hu.taliann.icesmp.utils.StartupLog.info(plugin.getLogger(), configManager, "Loaded " + listings.size() + " market listing(s) and "
                        + pendingDeliveries.size() + " pending deliver(y/ies).");
            } catch (final Exception exception) {
                plugin.getLogger().severe("Failed to load market.yml: " + exception.getMessage());
            }
        }
        recoverJournal();
    }

    private void loadListings(final YamlConfiguration yaml) {
        final ConfigurationSection section = yaml.getConfigurationSection("listings");
        if (section == null) return;
        for (final String idKey : section.getKeys(false)) {
            final UUID id;
            final UUID seller;
            try {
                id = UUID.fromString(idKey);
                seller = UUID.fromString(section.getString(idKey + ".seller", ""));
            } catch (final IllegalArgumentException invalid) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Értelmezhetetlen piaci tétel-azonosító vagy eladó: " + idKey);
                return;
            }
            final CurrencyType currency = CurrencyType.fromInput(
                    section.getString(idKey + ".currency", ""));
            final ItemStack item = section.getItemStack(idKey + ".item");
            if (currency == null || item == null || item.getType().isAir()) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Hiányzó vagy érvénytelen valuta/tárgy a(z) " + idKey + " piaci tételnél");
                return;
            }
            UUID bidderId = null;
            final String bidderRaw = section.getString(idKey + ".highest-bidder", "");
            if (!bidderRaw.isEmpty()) {
                try { bidderId = UUID.fromString(bidderRaw); }
                catch (final IllegalArgumentException invalid) {
                    YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                            "Értelmezhetetlen licitáló-azonosító a(z) " + idKey + " aukciónál");
                    return;
                }
            } else if (section.getDouble(idKey + ".highest-bid", 0.0D) > 0.0D) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Zárolt licit licitáló nélkül a(z) " + idKey + " aukciónál");
                return;
            }
            final double highestBid = section.getDouble(idKey + ".highest-bid", 0.0D);
            if (bidderId != null && !(highestBid > 0.0D)) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Licitáló zárolt összeg nélkül a(z) " + idKey + " aukciónál");
                return;
            }
            final double price = section.getDouble(idKey + ".price", 1.0D);
            final double buyOut = section.getDouble(idKey + ".buy-out", 0.0D);
            if (!isFiniteNonNegative(price) || !isFiniteNonNegative(highestBid)
                    || !isFiniteNonNegative(buyOut)) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Nem véges vagy negatív összeg a(z) " + idKey + " piaci tételnél");
                return;
            }
            listings.put(id, new Listing(id, seller,
                    section.getString(idKey + ".seller-name", "?"),
                    Math.max(0.01D, price), currency, item,
                    section.getLong(idKey + ".created-at", System.currentTimeMillis()),
                    section.getBoolean(idKey + ".auction", false),
                    section.getLong(idKey + ".ends-at", 0L), highestBid, bidderId,
                    section.getString(idKey + ".highest-bidder-name", null), buyOut));
        }
    }

    private void loadPendingDeliveries(final YamlConfiguration yaml) {
        final ConfigurationSection deliveries = yaml.getConfigurationSection("pending-deliveries");
        if (deliveries == null) return;
        for (final String playerKey : deliveries.getKeys(false)) {
            final UUID owner;
            try { owner = UUID.fromString(playerKey); }
            catch (final IllegalArgumentException invalid) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Értelmezhetetlen tulajdonos-azonosító a várólistán: " + playerKey);
                return;
            }
            final List<ItemStack> items = readItemsStrict(deliveries.getList(playerKey), playerKey);
            if (items == null) return;
            pendingDeliveries.put(owner, items);
        }
    }

    private void requestSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                saveScheduled.set(false);
                save();
            }, 2L, TimeUnit.SECONDS);
        }
    }

    public synchronized void save() { flush(); }

    private boolean flush() {
        if (YamlStore.isLoadFailed(storageFile)) return false;
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
                if (listing.hasBuyOut()) yaml.set(basePath + ".buy-out", listing.buyOut());
                if (listing.hasBid()) {
                    yaml.set(basePath + ".highest-bidder", listing.highestBidder().toString());
                    yaml.set(basePath + ".highest-bidder-name", listing.highestBidderName());
                }
            }
        }
        for (final Map.Entry<UUID, List<ItemStack>> entry : pendingDeliveries.entrySet()) {
            yaml.set("pending-deliveries." + entry.getKey(), entry.getValue());
        }
        if (!committedTxns.isEmpty()) yaml.set("committed-txn", new ArrayList<>(committedTxns));
        try {
            YamlStore.saveAtomic(storageFile, yaml);
            return true;
        } catch (final IOException exception) {
            plugin.getLogger().severe("Failed to save market.yml: " + exception.getMessage());
            return false;
        }
    }

    public Listing getListing(final UUID listingId) {
        if (listingId == null || unconfirmedListings.contains(listingId)) return null;
        return listings.get(listingId);
    }

    public List<Listing> getListingsSorted() {
        return listings.values().stream()
                .filter(listing -> !unconfirmedListings.contains(listing.id()))
                .sorted(Comparator.comparingLong(Listing::createdAt).reversed()).toList();
    }

    public ItemMetadata metadataOf(final Listing listing) {
        if (listing == null) return null;
        final ItemIdentityService.Inspection inspection = itemIdentity.inspect(listing.item());
        if (inspection.status() != ItemIdentityService.Status.VALID) return null;
        final ItemInstance instance = inspection.instance();
        final ItemTemplate template = inspection.template();
        final double averageQuality = instance.rolls().isEmpty() ? 1.0D
                : instance.rolls().values().stream().mapToDouble(ItemInstance.Roll::quality)
                .average().orElse(1.0D);
        final HashSet<String> stats = new HashSet<>(template.fixedStatsAt(
                instance.ascension().stageId()).keySet());
        stats.addAll(instance.rolls().keySet());
        return new ItemMetadata(template.templateId(), template.rarity(), instance.itemLevel(),
                template.slot(), template.classRestrictions(), template.signatureEffectId(),
                template.runeSocketCountAt(instance.ascension().stageId()),
                instance.ascension().stageId(), averageQuality, template.setId(), Set.copyOf(stats));
    }

    public List<Listing> filterListings(final ItemFilter filter) {
        if (filter == null) return getListingsSorted();
        return getListingsSorted().stream().filter(listing -> {
            final ItemMetadata item = metadataOf(listing);
            if (item == null) return false;
            return (filter.templateId() == null || filter.templateId().isBlank()
                    || item.templateId().equalsIgnoreCase(filter.templateId()))
                    && (filter.rarity() == null || item.rarity() == filter.rarity())
                    && (filter.minimumItemLevel() == null || item.itemLevel() >= filter.minimumItemLevel())
                    && (filter.maximumItemLevel() == null || item.itemLevel() <= filter.maximumItemLevel())
                    && (filter.slot() == null || item.slot() == filter.slot())
                    && (filter.classId() == null || filter.classId().isBlank()
                    || item.classRestrictions().isEmpty()
                    || item.classRestrictions().contains(filter.classId().toLowerCase(java.util.Locale.ROOT)))
                    && (filter.signatureEffectId() == null || filter.signatureEffectId().isBlank()
                    || item.signatureEffectId().equalsIgnoreCase(filter.signatureEffectId()))
                    && (filter.minimumSocketCount() == null
                    || item.socketCount() >= filter.minimumSocketCount())
                    && (filter.ascensionState() == null || filter.ascensionState().isBlank()
                    || item.ascensionState().equalsIgnoreCase(filter.ascensionState()))
                    && (filter.minimumRollQuality() == null
                    || item.averageRollQuality() >= Math.max(0.0D,
                    Math.min(1.0D, filter.minimumRollQuality())))
                    && (filter.setId() == null || filter.setId().isBlank()
                    || item.setId().equalsIgnoreCase(filter.setId()))
                    && (filter.requiredStatId() == null || filter.requiredStatId().isBlank()
                    || item.statIds().contains(filter.requiredStatId()
                    .toLowerCase(java.util.Locale.ROOT).replace('-', '_')));
        }).toList();
    }

    public long countListingsOf(final UUID seller) {
        return listings.values().stream()
                .filter(listing -> !unconfirmedListings.contains(listing.id()))
                .filter(listing -> listing.seller().equals(seller)).count();
    }

    public synchronized String createListing(final Player seller, final double price,
                                             final CurrencyType currency) {
        return createListingInternal(seller, price, currency, 0L, 0.0D);
    }

    public synchronized String createAuction(final Player seller, final double startPrice,
                                             final CurrencyType currency,
                                             final long requestedDurationMillis,
                                             final double buyOut) {
        final long defaultMillis = safeHours(configManager.getDouble(
                "market.auction.default-duration-hours", 24.0D));
        final long maxMillis = safeHours(configManager.getDouble(
                "market.auction.max-duration-hours", 72.0D));
        final long duration = Math.max(60_000L, Math.min(Math.max(60_000L, maxMillis),
                requestedDurationMillis > 0L ? requestedDurationMillis : defaultMillis));
        if (buyOut > 0.0D && buyOut < startPrice) return "market-buyout-too-low";
        return createListingInternal(seller, startPrice, currency, duration,
                Math.max(0.0D, buyOut));
    }

    private String createListingInternal(final Player seller, final double price,
                                         final CurrencyType currency,
                                         final long auctionDurationMillis,
                                         final double buyOut) {
        final ItemStack held = seller.getInventory().getItemInMainHand();
        if (held.getType().isAir()) return "market-no-item";
        final ItemIdentityService.Inspection authored = itemIdentity.inspect(held);
        if (authored.status() != ItemIdentityService.Status.NOT_MANAGED) {
            if (authored.status() != ItemIdentityService.Status.VALID) {
                return "market-item-identity-invalid";
            }
            if (authored.template().tradePolicy() != ItemTemplate.TradePolicy.TRADEABLE
                    || authored.template().bindPolicy() == ItemTemplate.BindPolicy.ACCOUNT) {
                return "market-item-policy-blocked";
            }
            final ItemIdentityService.DuplicateReport duplicates = itemIdentity.inspectDuplicates(
                    java.util.Arrays.asList(seller.getInventory().getContents()));
            if (!duplicates.clean()) return "market-item-duplicate";
        } else if (itemIdentity.classifyLegacy(held) != ItemIdentityService.LegacyKind.NONE
                && !configManager.getBoolean("itemization.legacy.market-enabled", true)) {
            return "market-legacy-blocked";
        }
        if (!configManager.getBoolean("market.allow-relic-listing", false)
                && held.hasItemMeta()
                && held.getItemMeta().getPersistentDataContainer().has(
                        NamespacedKey.fromString("icesmp:relic_id"), PersistentDataType.STRING)) {
            return "market-relic-not-tradeable";
        }
        if (!Double.isFinite(price) || price <= 0.0D) return "amount-must-be-positive";
        final int maxListings = Math.max(1,
                configManager.getInt("market.max-listings-per-player", 5));
        if (countListingsOf(seller.getUniqueId()) >= maxListings) return "market-too-many-listings";
        if (!storageHealthy() || !playerSaveSupported) return "market-journal-unavailable";

        final UUID id = UUID.randomUUID();
        final long now = System.currentTimeMillis();
        final boolean auction = auctionDurationMillis > 0L;
        final ItemStack original = held.clone();
        final Listing listing = new Listing(id, seller.getUniqueId(), seller.getName(), price,
                currency, original.clone(), now, auction,
                auction ? saturatingAdd(now, auctionDurationMillis) : 0L,
                0.0D, null, null, auction ? buyOut : 0.0D);
        final TransactionJournal.Entry entry = journal.create(TYPE_LIST);
        entry.data().set("owner", seller.getUniqueId().toString());
        entry.data().set("listing-id", id.toString());
        entry.data().set("item", listing.item());
        entry.data().set("hand-slot", seller.getInventory().getHeldItemSlot());
        if (!journal.prepare(entry)) return "market-journal-unavailable";

        final ItemStack tagged = withMarker(original, TAKE_MARKER_KEY, entry.id());
        seller.getInventory().setItemInMainHand(tagged);
        if (!persistPlayer(seller)) {
            seller.getInventory().setItemInMainHand(original);
            journal.complete(entry);
            return "market-playerdata-unavailable";
        }

        seller.getInventory().setItemInMainHand(null);
        if (!persistPlayer(seller)) {
            seller.getInventory().setItemInMainHand(tagged);
            plugin.getLogger().warning("Piaci listázás playerdata eltávolítása bizonytalan; "
                    + "a tárgy markerrel a játékosnál marad, a napló recoveryre vár: " + entry.id());
            return "market-playerdata-unavailable";
        }

        listings.put(id, listing);
        if (commitState(entry, false)) {
            finish(entry);
            return null;
        }
        unconfirmedListings.add(id);
        plugin.getLogger().severe("A piaci listázás commitja nem sikerült (" + entry.id()
                + "); a tétel rejtve marad a következő recoveryig.");
        return "market-commit-pending";
    }

    public synchronized BuyOutcome buy(final Player buyer, final UUID listingId) {
        final Listing listing = getListing(listingId);
        if (listing == null) return BuyOutcome.error("market-listing-gone");
        if (listing.auction()) return BuyOutcome.error("market-auction-use-bid");
        if (listing.seller().equals(buyer.getUniqueId())) return BuyOutcome.error("market-own-listing");
        final String transferError = validateCanonicalTransfer(listing.item(), buyer, true);
        if (transferError != null) return BuyOutcome.error(transferError);
        final double buyerCost = getEffectivePrice(buyer, listing);
        if (currencyManager.getBalance(buyer.getUniqueId(), listing.currency()) < buyerCost) {
            return BuyOutcome.error("market-insufficient-balance");
        }
        if (!storageHealthy()) return BuyOutcome.error("market-journal-unavailable");
        final double sellerShare = sellerShare(listing.currency(), buyerCost);
        final TransactionJournal.Entry entry = journal.create(TYPE_BUY);
        entry.data().set("owner", buyer.getUniqueId().toString());
        entry.data().set("listing-id", listing.id().toString());
        entry.data().set("item", listing.item());
        recordMoney(entry, 0, buyer.getUniqueId(), listing.currency(), -buyerCost);
        recordMoney(entry, 1, listing.seller(), listing.currency(), sellerShare);
        if (!journal.prepare(entry)) return BuyOutcome.error("market-journal-unavailable");
        if (!currencyManager.deductFromBalance(buyer.getUniqueId(), listing.currency(), buyerCost)) {
            journal.complete(entry);
            return BuyOutcome.error("market-insufficient-balance");
        }
        if (sellerShare > 0.0D) currencyManager.addToBalance(
                listing.seller(), listing.currency(), sellerShare);
        listings.remove(listing.id());
        queueDelivery(buyer.getUniqueId(), listing.item());
        recordTransaction(listing.item(), buyerCost, listing.currency());
        commitAndFinish(entry, true);
        deliverPending(buyer);
        return new BuyOutcome(null, buyerCost);
    }

    private void recordTransaction(final ItemStack item, final double price,
                                   final CurrencyType currency) {
        recentTransactions.addLast(new Transaction(
                item.getType(), price, currency, System.currentTimeMillis()));
        while (recentTransactions.size() > TRANSACTION_LOG_CAP) recentTransactions.pollFirst();
    }

    private double sellerShare(final CurrencyType currency, final double amount) {
        Double override = null;
        final EconomyEventManager economyRef = economyEventManager;
        if (economyRef != null) override = economyRef.marketFeeOverride(currency);
        final double feePercent = override != null ? override
                : Math.max(0.0D, Math.min(100.0D,
                configManager.getDouble("market.fee-percent", 10.0D)));
        return Math.max(0.0D, amount * (1.0D - feePercent / 100.0D));
    }

    public void setEconomyEventManager(final EconomyEventManager economyEventManager) {
        this.economyEventManager = economyEventManager;
    }

    public double getMinimumBid(final Listing listing) {
        if (!listing.hasBid()) return listing.price();
        final double incrementPercent = Math.max(1.0D,
                configManager.getDouble("market.auction.min-increment-percent", 10.0D));
        return Math.ceil(listing.highestBid()
                * (1.0D + incrementPercent / 100.0D) * 100.0D) / 100.0D;
    }

    public double getBigBid(final Listing listing) {
        final double base = listing.hasBid() ? listing.highestBid() : listing.price();
        final double bigPercent = Math.max(1.0D,
                configManager.getDouble("market.auction.big-increment-percent", 25.0D));
        final double big = Math.ceil(base * (1.0D + bigPercent / 100.0D) * 100.0D) / 100.0D;
        return Math.max(big, getMinimumBid(listing));
    }

    public synchronized BidOutcome bid(final Player bidder, final UUID listingId) {
        final Listing listing = getListing(listingId);
        return bid(bidder, listingId, listing == null ? 0.0D : getMinimumBid(listing));
    }

    public synchronized BidOutcome bid(final Player bidder, final UUID listingId,
                                       final double amount) {
        final Listing listing = getListing(listingId);
        if (listing == null || !listing.auction()) return BidOutcome.error("market-listing-gone");
        if (System.currentTimeMillis() >= listing.endsAt()) return BidOutcome.error("market-auction-ended");
        if (listing.seller().equals(bidder.getUniqueId())) return BidOutcome.error("market-own-listing");
        final String transferError = validateCanonicalTransfer(listing.item(), bidder, true);
        if (transferError != null) return BidOutcome.error(transferError);
        if (!Double.isFinite(amount)) return BidOutcome.error("market-bid-too-low");
        final boolean boughtOut = listing.hasBuyOut() && amount >= listing.buyOut();
        if (bidder.getUniqueId().equals(listing.highestBidder()) && !boughtOut) {
            return BidOutcome.error("market-already-highest");
        }
        if (!boughtOut && amount < getMinimumBid(listing)) return BidOutcome.error("market-bid-too-low");
        final double effective = boughtOut ? listing.buyOut() : amount;
        if (currencyManager.getBalance(bidder.getUniqueId(), listing.currency()) < effective) {
            return BidOutcome.error("market-insufficient-balance");
        }
        if (!storageHealthy()) return BidOutcome.error("market-journal-unavailable");

        final UUID previousBidder = listing.highestBidder();
        final double previousBid = listing.highestBid();
        final double share = boughtOut ? sellerShare(listing.currency(), effective) : 0.0D;
        final TransactionJournal.Entry entry = journal.create(TYPE_BID);
        entry.data().set("owner", bidder.getUniqueId().toString());
        entry.data().set("listing-id", listing.id().toString());
        entry.data().set("item", listing.item());
        recordMoney(entry, 0, bidder.getUniqueId(), listing.currency(), -effective);
        if (previousBidder != null) recordMoney(
                entry, 1, previousBidder, listing.currency(), previousBid);
        if (boughtOut && share > 0.0D) recordMoney(
                entry, 2, listing.seller(), listing.currency(), share);
        if (!journal.prepare(entry)) return BidOutcome.error("market-journal-unavailable");
        if (!currencyManager.deductFromBalance(bidder.getUniqueId(), listing.currency(), effective)) {
            journal.complete(entry);
            return BidOutcome.error("market-insufficient-balance");
        }
        if (previousBidder != null) currencyManager.addToBalance(
                previousBidder, listing.currency(), previousBid);
        if (boughtOut) {
            listings.remove(listing.id());
            if (share > 0.0D) currencyManager.addToBalance(
                    listing.seller(), listing.currency(), share);
            recordTransaction(listing.item(), effective, listing.currency());
            queueDelivery(bidder.getUniqueId(), listing.item());
            commitAndFinish(entry, true);
            return new BidOutcome(null, effective, previousBidder, previousBid, true);
        }
        listings.put(listing.id(), new Listing(listing.id(), listing.seller(),
                listing.sellerName(), listing.price(), listing.currency(), listing.item(),
                listing.createdAt(), true, listing.endsAt(), effective,
                bidder.getUniqueId(), bidder.getName(), listing.buyOut()));
        commitAndFinish(entry, true);
        return new BidOutcome(null, effective, previousBidder, previousBid, false);
    }

    public void tickAuctions() {
        final long now = System.currentTimeMillis();
        final List<Listing> settled = new ArrayList<>();
        synchronized (this) {
            for (final Listing listing : List.copyOf(listings.values())) {
                if (!listing.auction() || now < listing.endsAt()
                        || unconfirmedListings.contains(listing.id())) continue;
                if (!storageHealthy()) {
                    if (journalWarningLogged.compareAndSet(false, true)) {
                        plugin.getLogger().severe("Az aukció-lezárás elhalasztva: a piaci tranzakciós napló nem írható.");
                    }
                    break;
                }
                final double share = listing.hasBid()
                        ? sellerShare(listing.currency(), listing.highestBid()) : 0.0D;
                final TransactionJournal.Entry entry = journal.create(TYPE_SETTLE);
                entry.data().set("listing-id", listing.id().toString());
                entry.data().set("item", listing.item());
                if (share > 0.0D) recordMoney(
                        entry, 0, listing.seller(), listing.currency(), share);
                if (!journal.prepare(entry)) break;
                listings.remove(listing.id());
                if (listing.hasBid()) {
                    if (share > 0.0D) currencyManager.addToBalance(
                            listing.seller(), listing.currency(), share);
                    recordTransaction(listing.item(), listing.highestBid(), listing.currency());
                    queueDelivery(listing.highestBidder(), listing.item());
                } else {
                    queueDelivery(listing.seller(), listing.item());
                }
                commitAndFinish(entry, share > 0.0D);
                settled.add(listing);
            }
        }
        for (final Listing listing : settled) {
            if (listing.hasBid()) {
                notifyAndDeliver(listing.highestBidder(), messageManager.getMessage(
                        "market-auction-won",
                        "&aMegnyerted az aukciót (&f{item}&a) &f{price} {currency}&a licittel!",
                        Map.of("item", listing.item().getType().name(),
                                "price", currencyManager.formatBalance(listing.highestBid()),
                                "currency", listing.currency().getDisplayName())));
                notifyAndDeliver(listing.seller(), messageManager.getMessage(
                        "market-auction-sold",
                        "&aAz aukciód lezárult: &f{bidder}&a vitte el &f{price} {currency}&a-ért — a bevétel a bankodba került.",
                        Map.of("bidder", listing.highestBidderName() == null
                                        ? "?" : listing.highestBidderName(),
                                "price", currencyManager.formatBalance(listing.highestBid()),
                                "currency", listing.currency().getDisplayName())));
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

    private void notifyAndDeliver(final UUID playerId,
                                  final net.kyori.adventure.text.Component notice) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        player.getScheduler().run(plugin, task -> {
            player.sendMessage(notice);
            deliverPending(player);
        }, null);
    }

    public boolean hasPendingDelivery(final UUID playerId) {
        return pendingDeliveries.containsKey(playerId) || playerRecoveries.containsKey(playerId);
    }

    public synchronized int deliverPending(final Player player) {
        resolvePlayerRecoveries(player);
        cleanupOrphanMarkers(player);
        final List<ItemStack> items = pendingDeliveries.get(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            pendingDeliveries.remove(player.getUniqueId());
            return 0;
        }
        for (final ItemStack item : items) {
            final String transferError = validateCanonicalTransfer(item, null, false);
            if (transferError != null) {
                player.sendMessage(messageManager.getMessage(transferError,
                        "<red>A piaci tárgy identitása vagy egyedisége nem ellenőrizhető; az átvétel függőben maradt.</red>"));
                return 0;
            }
        }
        final ArrayList<ItemStack> combined = new ArrayList<>(
                java.util.Arrays.asList(player.getInventory().getContents()));
        combined.addAll(items);
        for (final ItemStack item : combined) {
            final ItemIdentityService.Inspection inspection = itemIdentity.inspect(item);
            if (inspection.status() != ItemIdentityService.Status.NOT_MANAGED
                    && inspection.status() != ItemIdentityService.Status.VALID) {
                player.sendMessage(messageManager.getMessage("market-item-identity-invalid",
                        "<red>Hibás canonical item identity miatt a piaci átvétel függőben maradt.</red>"));
                return 0;
            }
        }
        if (!itemIdentity.inspectDuplicates(combined).clean()) {
            player.sendMessage(messageManager.getMessage("market-item-duplicate",
                    "<red>Duplikált item UUID miatt a piaci átvétel függőben maradt.</red>"));
            return 0;
        }
        if (!storageHealthy() || !playerSaveSupported) return 0;

        final TransactionJournal.Entry entry = journal.create(TYPE_DELIVER);
        entry.data().set("owner", player.getUniqueId().toString());
        entry.data().set("items", items.stream().map(ItemStack::clone).toList());
        if (!journal.prepare(entry)) return 0;
        final List<ItemStack> tagged = items.stream()
                .map(item -> withMarker(item, DELIVERY_MARKER_KEY, entry.id())).toList();
        if (!canFitAll(player, tagged)) {
            journal.complete(entry);
            player.sendMessage(messageManager.getMessage(
                    "market-delivery-inventory-full",
                    "<yellow>A piaci átvételhez nincs elegendő inventoryhely. Semmi nem veszett el.</yellow>"));
            return 0;
        }

        pendingDeliveries.remove(player.getUniqueId());
        if (!commitState(entry, false)) {
            pendingDeliveries.put(player.getUniqueId(), items);
            committedTxns.remove(entry.id().toString());
            journal.complete(entry);
            return 0;
        }

        final ItemStack[] before = cloneStorageContents(player);
        for (final ItemStack item : tagged) {
            final Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                player.getInventory().setStorageContents(before);
                plugin.getLogger().severe("Market delivery fit preflight diverged for "
                        + player.getUniqueId() + "; journal remains open.");
                return 0;
            }
        }
        if (!persistPlayer(player)) {
            plugin.getLogger().warning("A piaci kézbesítés tárgy-oldala nem lett azonnal tartós ("
                    + entry.id() + "); a napló nyitva marad.");
            return 0;
        }
        if (finish(entry)) {
            stripMarker(player, DELIVERY_MARKER_KEY, entry.id());
            persistPlayer(player);
        }
        return items.size();
    }

    private String validateCanonicalTransfer(final ItemStack item, final Player recipient,
                                             final boolean enforceTradePolicy) {
        final ItemIdentityService.Inspection authored = itemIdentity.inspect(item);
        if (authored.status() == ItemIdentityService.Status.NOT_MANAGED) return null;
        if (authored.status() != ItemIdentityService.Status.VALID) {
            return "market-item-identity-invalid";
        }
        if (enforceTradePolicy && (authored.template().tradePolicy()
                != ItemTemplate.TradePolicy.TRADEABLE
                || authored.template().bindPolicy() == ItemTemplate.BindPolicy.ACCOUNT)) {
            return "market-item-policy-blocked";
        }
        if (recipient != null) {
            final ArrayList<ItemStack> combined = new ArrayList<>(
                    java.util.Arrays.asList(recipient.getInventory().getContents()));
            combined.add(item);
            for (final ItemStack candidate : combined) {
                final ItemIdentityService.Inspection inspection = itemIdentity.inspect(candidate);
                if (inspection.status() != ItemIdentityService.Status.NOT_MANAGED
                        && inspection.status() != ItemIdentityService.Status.VALID) {
                    return "market-item-identity-invalid";
                }
            }
            if (!itemIdentity.inspectDuplicates(combined).clean()) {
                return "market-item-duplicate";
            }
        }
        return null;
    }

    public synchronized int cancelListings(final Player player) {
        final List<Listing> cancellable = new ArrayList<>();
        for (final Listing listing : List.copyOf(listings.values())) {
            if (!listing.seller().equals(player.getUniqueId()) || listing.hasBid()
                    || unconfirmedListings.contains(listing.id())) continue;
            cancellable.add(listing);
        }
        if (cancellable.isEmpty() || !storageHealthy()) return 0;
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

    public boolean hasLockedAuction(final UUID seller) {
        return listings.values().stream()
                .filter(listing -> !unconfirmedListings.contains(listing.id()))
                .anyMatch(listing -> listing.seller().equals(seller) && listing.hasBid());
    }

    public MarketStats getStats() {
        final List<Listing> listingSnapshot = getListingsSorted();
        final int activeListings = listingSnapshot.size();
        final int activeAuctions = (int) listingSnapshot.stream().filter(Listing::auction).count();
        final Map<Material, Long> counts = listingSnapshot.stream()
                .collect(Collectors.groupingBy(
                        listing -> listing.item().getType(), Collectors.counting()));
        final List<Map.Entry<Material, Long>> topItemTypes = counts.entrySet().stream()
                .sorted(Map.Entry.<Material, Long>comparingByValue().reversed())
                .limit(3).toList();
        final List<Transaction> transactionSnapshot = List.copyOf(recentTransactions);
        final Map<CurrencyType, double[]> sumAndCount = new EnumMap<>(CurrencyType.class);
        Transaction biggest = null;
        for (final Transaction transaction : transactionSnapshot) {
            final double[] accumulator = sumAndCount.computeIfAbsent(
                    transaction.currency(), key -> new double[2]);
            accumulator[0] += transaction.price();
            accumulator[1] += 1.0D;
            if (biggest == null || transaction.price() > biggest.price()) biggest = transaction;
        }
        final Map<CurrencyType, Double> averages = new EnumMap<>(CurrencyType.class);
        sumAndCount.forEach((currency, accumulator) ->
                averages.put(currency, accumulator[0] / accumulator[1]));
        return new MarketStats(activeListings, activeAuctions, topItemTypes,
                averages, biggest, transactionSnapshot.size());
    }

    private boolean storageHealthy() {
        return journal.isHealthy() && !YamlStore.isLoadFailed(storageFile);
    }

    private boolean commitState(final TransactionJournal.Entry entry,
                                final boolean moneyTouched) {
        committedTxns.add(entry.id().toString());
        if (moneyTouched) currencyManager.save();
        return flush();
    }

    private boolean finish(final TransactionJournal.Entry entry) {
        if (!journal.complete(entry)) {
            plugin.getLogger().severe("A piaci napló lezárása nem sikerült (" + entry.type()
                    + " " + entry.id() + "); a commit-tanú marad.");
            return false;
        }
        committedTxns.remove(entry.id().toString());
        committedTxns.remove(resolutionKey(entry));
        return true;
    }

    private void commitAndFinish(final TransactionJournal.Entry entry,
                                 final boolean moneyTouched) {
        if (commitState(entry, moneyTouched)) {
            finish(entry);
            return;
        }
        plugin.getLogger().severe("A piaci tranzakció commitja nem sikerült ("
                + entry.type() + " " + entry.id() + "); a napló nyitva marad.");
    }

    private void recordMoney(final TransactionJournal.Entry entry, final int index,
                             final UUID player, final CurrencyType currency,
                             final double delta) {
        final String base = "money." + index;
        entry.data().set(base + ".player", player.toString());
        entry.data().set(base + ".currency", currency.name());
        entry.data().set(base + ".before", currencyManager.getBalance(player, currency));
        entry.data().set(base + ".delta", delta);
    }

    private boolean isCommitted(final TransactionJournal.Entry entry) {
        return committedTxns.contains(entry.id().toString());
    }

    private String resolutionKey(final TransactionJournal.Entry entry) {
        return entry.id() + ":r";
    }

    private boolean persistPlayer(final Player player) {
        if (!playerSaveSupported) return false;
        try {
            player.saveData();
            return true;
        } catch (final RuntimeException | LinkageError failure) {
            playerSaveSupported = false;
            plugin.getLogger().warning("A piac nem tudja azonnal kiírni a játékos-adatot: "
                    + failure);
            return false;
        }
    }

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
                    if (repairMoney(entry, committed)) finish(entry);
                }
                case TYPE_LIST -> deferToOwner(entry, committed);
                case TYPE_DELIVER -> {
                    if (committed) deferToOwner(entry, true); else finish(entry);
                }
                default -> finish(entry);
            }
        }
        final Set<String> keep = new HashSet<>();
        for (final TransactionJournal.Entry entry : journal.pending()) {
            keep.add(entry.id().toString());
            keep.add(resolutionKey(entry));
        }
        committedTxns.retainAll(keep);
        requestSave();
        for (final UUID owner : List.copyOf(playerRecoveries.keySet())) {
            final Player online = Bukkit.getPlayer(owner);
            if (online != null) {
                online.getScheduler().run(plugin, task -> deliverPending(online), null);
            }
        }
    }

    private void deferToOwner(final TransactionJournal.Entry entry,
                              final boolean committed) {
        final UUID owner = parseUuid(entry.data().getString("owner"));
        if (owner == null) {
            finish(entry);
            return;
        }
        if (committed && TYPE_LIST.equals(entry.type())) {
            final UUID listingId = parseUuid(entry.data().getString("listing-id"));
            if (listingId != null) unconfirmedListings.add(listingId);
        }
        playerRecoveries.computeIfAbsent(owner, key -> new ArrayList<>()).add(entry);
        plugin.getLogger().warning("Piaci tranzakció (" + entry.type() + " "
                + entry.id() + ") a tulajdonos belépéséig várakozik: " + owner);
    }

    private boolean repairMoney(final TransactionJournal.Entry entry,
                                final boolean forward) {
        final ConfigurationSection money = entry.data().getConfigurationSection("money");
        if (money == null) return true;
        boolean allReached = true;
        for (final String index : money.getKeys(false)) {
            final UUID player = parseUuid(money.getString(index + ".player"));
            final CurrencyType currency = CurrencyType.fromInput(
                    money.getString(index + ".currency", ""));
            if (player == null || currency == null) {
                plugin.getLogger().severe("Piac-helyreállítás: értelmezhetetlen egyenleg-bejegyzés; napló nyitva marad.");
                allReached = false;
                continue;
            }
            final double before = money.getDouble(index + ".before");
            final double target = forward
                    ? Math.max(0.0D, before + money.getDouble(index + ".delta")) : before;
            final double current = currencyManager.getBalance(player, currency);
            final double difference = target - current;
            if (Math.abs(difference) < MONEY_EPSILON) continue;
            if (difference > 0.0D) {
                currencyManager.addToBalance(player, currency, difference);
            } else if (!currencyManager.deductFromBalance(player, currency, -difference)) {
                allReached = false;
                continue;
            }
            plugin.getLogger().warning("Piac-helyreállítás: " + player + " "
                    + currency.name() + " egyenlege korrigálva "
                    + currencyManager.formatBalance(current) + " -> "
                    + currencyManager.formatBalance(target));
        }
        if (allReached) currencyManager.save();
        return allReached;
    }

    private void resolvePlayerRecoveries(final Player player) {
        final List<TransactionJournal.Entry> entries =
                playerRecoveries.remove(player.getUniqueId());
        if (entries == null) return;
        for (final TransactionJournal.Entry entry : entries) {
            if (TYPE_LIST.equals(entry.type())) resolveListRecovery(player, entry);
            else resolveDeliveryRecovery(player, entry);
        }
    }

    private void resolveListRecovery(final Player seller,
                                     final TransactionJournal.Entry entry) {
        final UUID listingId = parseUuid(entry.data().getString("listing-id"));
        final TakeEvidence evidence = takeEvidence(seller, entry);
        if (evidence == TakeEvidence.AMBIGUOUS) {
            deferAgain(seller.getUniqueId(), entry);
            plugin.getLogger().severe("Ambiguous market LIST item evidence for " + entry.id());
            return;
        }
        final boolean itemStillPresent = evidence == TakeEvidence.TAGGED_PRESENT
                || evidence == TakeEvidence.ORIGINAL_PRESENT;
        if (isCommitted(entry)) {
            if (!itemStillPresent) {
                if (listingId != null) unconfirmedListings.remove(listingId);
                finish(entry);
                seller.sendMessage(messageManager.getMessage(
                        "market-recovery-listing-restored",
                        "&aA megszakadt listázásod helyreállt — a tételed újra elérhető a piacon."));
                return;
            }
            if (listingId != null) {
                listings.remove(listingId);
                unconfirmedListings.remove(listingId);
            }
            if (!flush()) {
                deferAgain(seller.getUniqueId(), entry);
                return;
            }
            if (finish(entry)) {
                stripMarker(seller, TAKE_MARKER_KEY, entry.id());
                persistPlayer(seller);
            }
            seller.sendMessage(messageManager.getMessage(
                    "market-recovery-listing-cancelled",
                    "&eA szerver leállása félbevágta a listázásodat — a tárgy nálad maradt, listázd újra."));
            return;
        }
        if (itemStillPresent) {
            if (finish(entry)) {
                stripMarker(seller, TAKE_MARKER_KEY, entry.id());
                persistPlayer(seller);
            }
            return;
        }
        final ItemStack item = entry.data().getItemStack("item");
        if (!committedTxns.contains(resolutionKey(entry))) {
            if (item != null) queueDelivery(seller.getUniqueId(), item);
            committedTxns.add(resolutionKey(entry));
            if (!flush()) {
                committedTxns.remove(resolutionKey(entry));
                if (item != null) removeLastQueued(seller.getUniqueId());
                deferAgain(seller.getUniqueId(), entry);
                return;
            }
        }
        finish(entry);
        seller.sendMessage(messageManager.getMessage(
                "market-recovery-item-returned",
                "&eA szerver leállása félbevágta a listázásodat — a tárgyat visszakapod."));
    }

    private void resolveDeliveryRecovery(final Player player,
                                         final TransactionJournal.Entry entry) {
        final List<ItemStack> expected = readItems(entry.data().getList("items"));
        final DeliveryEvidence evidence = deliveryEvidence(player, entry.id(), expected);
        if (evidence.invalid() || evidence.partial()) {
            deferAgain(player.getUniqueId(), entry);
            player.sendMessage(messageManager.getMessage(
                    "market-recovery-delivery-review",
                    "<red>A félbemaradt piaci kézbesítés részleges tanút talált; admin egyeztetés szükséges.</red>"));
            return;
        }
        if (evidence.complete()) {
            if (finish(entry)) {
                stripMarker(player, DELIVERY_MARKER_KEY, entry.id());
                persistPlayer(player);
            }
            return;
        }
        if (!committedTxns.contains(resolutionKey(entry))) {
            for (final ItemStack item : expected) queueDelivery(player.getUniqueId(), item);
            committedTxns.add(resolutionKey(entry));
            if (!flush()) {
                committedTxns.remove(resolutionKey(entry));
                for (int i = 0; i < expected.size(); i++) removeLastQueued(player.getUniqueId());
                deferAgain(player.getUniqueId(), entry);
                return;
            }
        }
        finish(entry);
        player.sendMessage(messageManager.getMessage(
                "market-recovery-delivery-retry",
                "&eA szerver leállása félbevágta az átvételt — a tételeid újra elérhetők."));
    }

    private TakeEvidence takeEvidence(final Player seller,
                                      final TransactionJournal.Entry entry) {
        final String id = entry.id().toString();
        final ItemStack expected = entry.data().getItemStack("item");
        int taggedMatches = 0;
        boolean invalid = false;
        for (final ItemStack item : seller.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;
            final String marker = item.getItemMeta().getPersistentDataContainer().get(
                    TAKE_MARKER_KEY, PersistentDataType.STRING);
            if (!id.equals(marker)) continue;
            taggedMatches++;
            if (!sameIgnoringMarker(item, expected, TAKE_MARKER_KEY)) invalid = true;
        }
        if (invalid || taggedMatches > 1) return TakeEvidence.AMBIGUOUS;
        if (taggedMatches == 1) return TakeEvidence.TAGGED_PRESENT;
        final int slot = entry.data().getInt("hand-slot", -1);
        if (slot >= 0 && slot < seller.getInventory().getStorageContents().length) {
            final ItemStack current = seller.getInventory().getItem(slot);
            if (sameIgnoringMarker(current, expected, TAKE_MARKER_KEY)) {
                return TakeEvidence.ORIGINAL_PRESENT;
            }
        }
        return TakeEvidence.ABSENT;
    }

    private DeliveryEvidence deliveryEvidence(final Player player, final UUID transactionId,
                                                final List<ItemStack> expectedItems) {
        final List<ItemStack> remaining = expectedItems.stream().map(ItemStack::clone)
                .collect(Collectors.toCollection(ArrayList::new));
        int expectedAmount = remaining.stream().mapToInt(ItemStack::getAmount).sum();
        int taggedAmount = 0;
        boolean invalid = false;
        for (final ItemStack current : player.getInventory().getStorageContents()) {
            if (current == null || current.getType().isAir() || !current.hasItemMeta()) continue;
            final String marker = current.getItemMeta().getPersistentDataContainer().get(
                    DELIVERY_MARKER_KEY, PersistentDataType.STRING);
            if (!transactionId.toString().equals(marker)) continue;
            final ItemStack untagged = withoutMarker(current, DELIVERY_MARKER_KEY);
            int amount = untagged.getAmount();
            taggedAmount = Math.addExact(taggedAmount, amount);
            for (final ItemStack expected : remaining) {
                if (amount <= 0) break;
                if (!expected.isSimilar(untagged) || expected.getAmount() <= 0) continue;
                final int consumed = Math.min(amount, expected.getAmount());
                expected.setAmount(expected.getAmount() - consumed);
                amount -= consumed;
            }
            if (amount > 0) invalid = true;
        }
        if (remaining.stream().allMatch(item -> item.getAmount() == 0)
                && taggedAmount != expectedAmount) invalid = true;
        return new DeliveryEvidence(expectedAmount, taggedAmount, invalid);
    }

    private void cleanupOrphanMarkers(final Player player) {
        final Set<String> pendingIds = journal.pending().stream()
                .map(entry -> entry.id().toString()).collect(Collectors.toSet());
        boolean changed = false;
        for (final ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;
            final var meta = item.getItemMeta();
            final String take = meta.getPersistentDataContainer().get(
                    TAKE_MARKER_KEY, PersistentDataType.STRING);
            final String delivery = meta.getPersistentDataContainer().get(
                    DELIVERY_MARKER_KEY, PersistentDataType.STRING);
            if (take != null && !pendingIds.contains(take)) {
                meta.getPersistentDataContainer().remove(TAKE_MARKER_KEY);
                changed = true;
            }
            if (delivery != null && !pendingIds.contains(delivery)) {
                meta.getPersistentDataContainer().remove(DELIVERY_MARKER_KEY);
                changed = true;
            }
            if (changed) item.setItemMeta(meta);
        }
        if (changed) persistPlayer(player);
    }

    private void stripMarker(final Player player, final NamespacedKey key,
                             final UUID transactionId) {
        for (final ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;
            final var meta = item.getItemMeta();
            final String marker = meta.getPersistentDataContainer().get(
                    key, PersistentDataType.STRING);
            if (!transactionId.toString().equals(marker)) continue;
            meta.getPersistentDataContainer().remove(key);
            item.setItemMeta(meta);
        }
    }

    private ItemStack withMarker(final ItemStack original, final NamespacedKey key,
                                 final UUID transactionId) {
        final ItemStack copy = original.clone();
        final var meta = copy.getItemMeta();
        meta.getPersistentDataContainer().set(
                key, PersistentDataType.STRING, transactionId.toString());
        copy.setItemMeta(meta);
        return copy;
    }

    private ItemStack withoutMarker(final ItemStack original, final NamespacedKey key) {
        if (original == null) return null;
        final ItemStack copy = original.clone();
        if (copy.hasItemMeta()) {
            final var meta = copy.getItemMeta();
            meta.getPersistentDataContainer().remove(key);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    private boolean sameIgnoringMarker(final ItemStack current, final ItemStack expected,
                                       final NamespacedKey key) {
        if (current == null || expected == null || current.getType().isAir()
                || expected.getType().isAir()) return false;
        final ItemStack clean = withoutMarker(current, key);
        return clean.getAmount() == expected.getAmount() && clean.isSimilar(expected);
    }

    private boolean canFitAll(final Player player, final List<ItemStack> stacks) {
        final ItemStack[] simulated = cloneStorageContents(player);
        for (final ItemStack original : stacks) {
            final ItemStack stack = original.clone();
            int remaining = stack.getAmount();
            final int max = Math.max(1, stack.getMaxStackSize());
            for (int slot = 0; slot < simulated.length && remaining > 0; slot++) {
                final ItemStack existing = simulated[slot];
                if (existing != null && !existing.getType().isAir() && existing.isSimilar(stack)) {
                    final int moved = Math.min(remaining,
                            Math.max(0, max - existing.getAmount()));
                    existing.setAmount(existing.getAmount() + moved);
                    remaining -= moved;
                }
            }
            for (int slot = 0; slot < simulated.length && remaining > 0; slot++) {
                if (simulated[slot] == null || simulated[slot].getType().isAir()) {
                    final int moved = Math.min(remaining, max);
                    final ItemStack placed = stack.clone();
                    placed.setAmount(moved);
                    simulated[slot] = placed;
                    remaining -= moved;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private ItemStack[] cloneStorageContents(final Player player) {
        final ItemStack[] contents = player.getInventory().getStorageContents();
        final ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            clone[i] = contents[i] == null ? null : contents[i].clone();
        }
        return clone;
    }

    private void deferAgain(final UUID owner, final TransactionJournal.Entry entry) {
        playerRecoveries.computeIfAbsent(owner, key -> new ArrayList<>()).add(entry);
    }

    private void removeLastQueued(final UUID owner) {
        final List<ItemStack> queued = pendingDeliveries.get(owner);
        if (queued == null || queued.isEmpty()) return;
        queued.remove(queued.size() - 1);
        if (queued.isEmpty()) pendingDeliveries.remove(owner);
    }

    private List<ItemStack> readItems(final List<?> raw) {
        final List<ItemStack> items = new ArrayList<>();
        if (raw == null) return items;
        for (final Object entry : raw) {
            if (entry instanceof ItemStack item && !item.getType().isAir()) items.add(item);
        }
        return items;
    }

    private List<ItemStack> readItemsStrict(final List<?> raw, final String where) {
        if (raw == null || raw.isEmpty()) {
            YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                    "Üres vagy hiányzó várólista-tárgylista: " + where);
            return null;
        }
        final List<ItemStack> items = new ArrayList<>();
        for (final Object entry : raw) {
            if (!(entry instanceof ItemStack item) || item.getType().isAir()
                    || item.getAmount() <= 0) {
                YamlStore.failCorrupt(storageFile, plugin.getLogger(),
                        "Érvénytelen tárgy a várólistán: " + where);
                return null;
            }
            items.add(item);
        }
        return items;
    }

    private static long safeHours(final double hours) {
        if (!Double.isFinite(hours) || hours <= 0.0D) return 60_000L;
        final double millis = hours * 3_600_000.0D;
        return millis >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) millis;
    }

    private static long saturatingAdd(final long first, final long second) {
        if (second <= 0L) return first;
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private static boolean isFiniteNonNegative(final double value) {
        return Double.isFinite(value) && value >= 0.0D;
    }

    private UUID parseUuid(final String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try { return UUID.fromString(raw); }
        catch (final IllegalArgumentException exception) { return null; }
    }
}
