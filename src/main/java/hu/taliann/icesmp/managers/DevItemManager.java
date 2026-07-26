package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.items.BlueprintItemFactory;
import hu.taliann.icesmp.items.DevItemFactory;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.listeners.ProfessionRecipeBookListener;
import hu.taliann.icesmp.storage.PersistentStore;
import hu.taliann.icesmp.storage.YamlStore;
import hu.taliann.icesmp.utils.MessageManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime and persistence for the first owner-bound DEV item, the Csodalatos Bingulus.
 *
 * <p>The reward clock advances only while the configured owner is online and the authoritative
 * item is in their personal inventory. The progress belongs to owner+item, not to an ItemStack,
 * therefore copied stacks never create additional reward clocks.</p>
 */
public final class DevItemManager implements PersistentStore {

    private static final UUID DEFAULT_OWNER = UUID.fromString("eb80c20f-092a-4d76-bd44-d168c91ea9e2");
    private static final String BASE = "dev-items." + DevItemFactory.BINGULUS_ID;
    private static final List<String> RARITY_ORDER = List.of(
            "kozonseges", "nem_mindennapi", "ritka", "epikus", "legendas", "ereklye");

    private record WeightedValue(String value, double weight) {
    }

    private record PendingReward(String rarity, String entry) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MessageManager messageManager;
    private final UniqueMaterialFactory uniqueMaterials;
    private final ProfessionRecipeCatalog recipeCatalog;
    private final BlueprintItemFactory blueprintFactory;
    private final ProfessionRecipeBookListener recipeBuilder;
    private final DevItemFactory itemFactory;
    private final File stateFile;
    /** Protects compound persisted-state transitions from mixed async snapshots. */
    private final Object stateLock = new Object();

    /** The item is restored only after its first successful admin issuance. */
    private final AtomicBoolean issued = new AtomicBoolean();
    private final AtomicLong progressMillis = new AtomicLong();
    private final AtomicReference<String> pendingRarity = new AtomicReference<>("");
    private final AtomicReference<String> pendingEntry = new AtomicReference<>("");
    /** Exact rolled item: amount, affixes and crafted stamps remain stable while inventory is full. */
    private final AtomicReference<ItemStack> pendingItem = new AtomicReference<>();
    private final AtomicInteger sinceRare = new AtomicInteger();
    private final AtomicInteger sinceEpic = new AtomicInteger();
    private final AtomicInteger sinceLegendary = new AtomicInteger();
    private final AtomicBoolean rewardInventoryNoticeSent = new AtomicBoolean();
    private final AtomicBoolean restoreInventoryNoticeSent = new AtomicBoolean();
    private final AtomicBoolean ownerTickQueued = new AtomicBoolean();
    private final AtomicLong lastActiveNanos = new AtomicLong();
    private final AtomicReference<UUID> boundOwner = new AtomicReference<>();
    private final AtomicBoolean rewardConfigWarningSent = new AtomicBoolean();

    /** Coalesces immediate state saves without allowing concurrent writes to the same YAML file. */
    private final AtomicBoolean saveQueued = new AtomicBoolean();
    private final AtomicBoolean saveAgain = new AtomicBoolean();

    private volatile UUID instanceId = UUID.randomUUID();
    private volatile ScheduledTask tickTask;
    private volatile long scheduledTicks = -1L;

    public DevItemManager(final JavaPlugin plugin, final ConfigManager configManager,
                          final MessageManager messageManager, final UniqueMaterialFactory uniqueMaterials,
                          final ProfessionRecipeCatalog recipeCatalog, final BlueprintItemFactory blueprintFactory,
                          final ProfessionRecipeBookListener recipeBuilder) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageManager = messageManager;
        this.uniqueMaterials = uniqueMaterials;
        this.recipeCatalog = recipeCatalog;
        this.blueprintFactory = blueprintFactory;
        this.recipeBuilder = recipeBuilder;
        this.itemFactory = new DevItemFactory(plugin, configManager);
        this.stateFile = new File(plugin.getDataFolder(), "dev-items-state.yml");
    }

    public DevItemFactory itemFactory() {
        return itemFactory;
    }

    public UUID ownerUuid() {
        final String raw = configManager.getString(BASE + ".owner-uuid", DEFAULT_OWNER.toString());
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalStateException("dev-items.yml: érvénytelen owner-uuid: " + raw, invalid);
        }
    }

    public boolean isOwner(final Player player) {
        return player != null && ownerUuid().equals(player.getUniqueId());
    }

    public void start() {
        reconcileConfiguredOwner();
        validateConfiguration();
        rescheduleTickTask();
        refreshOnlineOwnerItems(true);
    }

    public synchronized void shutdown() {
        final ScheduledTask current = tickTask;
        tickTask = null;
        scheduledTicks = -1L;
        if (current != null) {
            current.cancel();
        }
        lastActiveNanos.set(0L);
        ownerTickQueued.set(false);
    }

    /**
     * Called after a live config reload. Revalidates reward pools, applies a possible owner change,
     * reschedules the check period and refreshes/cleans every online player's DEV-item copies.
     */
    public void refreshOnlineOwner() {
        reconcileConfiguredOwner();
        validateConfiguration();
        rescheduleTickTask();
        refreshOnlineOwnerItems(true);
    }

    public void handleJoin(final Player player) {
        if (isOwner(player)) {
            lastActiveNanos.set(0L);
            ensureAuthoritativeItem(player, isEnabled() && autoRestoreEnabled(), true);
        } else {
            removeAllBingulusItems(player);
        }
    }

    public void handleRespawn(final Player player) {
        if (isOwner(player)) {
            lastActiveNanos.set(0L);
            // Death protection is unconditional once the item has been issued, even when the general
            // auto-restore switch is disabled.
            ensureAuthoritativeItem(player, true, true);
        }
    }

    /**
     * Admin delivery path. Only the configured owner can receive the authoritative item.
     */
    public boolean giveToOwner(final Player target) {
        if (!isOwner(target)) {
            return false;
        }

        int reusableSlot = -1;
        final ItemStack[] contents = target.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (itemFactory.isBingulus(contents[slot]) && reusableSlot < 0) {
                reusableSlot = slot;
            }
        }
        if (reusableSlot < 0) {
            reusableSlot = target.getInventory().firstEmpty();
        }
        if (reusableSlot < 0) {
            return false;
        }

        removeAllBingulusItems(target);
        target.getInventory().setItem(reusableSlot, itemFactory.createBingulus(ownerUuid(), instanceId));
        synchronized (stateLock) {
            issued.set(true);
            rewardInventoryNoticeSent.set(false);
            restoreInventoryNoticeSent.set(false);
            lastActiveNanos.set(0L);
        }
        requestSave();
        return true;
    }

    private boolean isEnabled() {
        return configManager.getBoolean(BASE + ".enabled", true);
    }

    private boolean autoRestoreEnabled() {
        return configManager.getBoolean(BASE + ".auto-restore", true);
    }

    private synchronized void rescheduleTickTask() {
        final boolean enabled = isEnabled();
        final long ticks = Math.max(20L, configManager.getLong(BASE + ".check-interval-ticks", 20L));
        if (!enabled) {
            if (tickTask != null) {
                tickTask.cancel();
                tickTask = null;
            }
            scheduledTicks = -1L;
            lastActiveNanos.set(0L);
            return;
        }
        if (tickTask != null && scheduledTicks == ticks) {
            return;
        }
        if (tickTask != null) {
            tickTask.cancel();
        }
        scheduledTicks = ticks;
        tickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, task -> queueOwnerTick(), ticks, ticks);
        lastActiveNanos.set(0L);
    }

    private void refreshOnlineOwnerItems(final boolean refreshVisuals) {
        final boolean allowRestore = isEnabled() && autoRestoreEnabled();
        for (final Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                if (isOwner(player)) {
                    ensureAuthoritativeItem(player, allowRestore, refreshVisuals);
                } else {
                    removeAllBingulusItems(player);
                }
            }, null);
        }
    }

    private void queueOwnerTick() {
        if (!isEnabled() || !issued.get()) {
            lastActiveNanos.set(0L);
            return;
        }
        final Player owner = Bukkit.getPlayer(ownerUuid());
        if (owner == null) {
            lastActiveNanos.set(0L);
            return;
        }
        if (!ownerTickQueued.compareAndSet(false, true)) {
            return;
        }
        owner.getScheduler().run(plugin, task -> {
            try {
                tickOwner(owner);
            } finally {
                ownerTickQueued.set(false);
            }
        }, () -> {
            ownerTickQueued.set(false);
            lastActiveNanos.set(0L);
        });
    }

    private void tickOwner(final Player owner) {
        if (!owner.isOnline() || !isOwner(owner) || !issued.get()) {
            lastActiveNanos.set(0L);
            return;
        }
        if (!ensureAuthoritativeItem(owner, autoRestoreEnabled(), false)) {
            lastActiveNanos.set(0L);
            return;
        }

        final long now = System.nanoTime();
        final long previous = lastActiveNanos.getAndSet(now);
        if (previous <= 0L || now <= previous) {
            return;
        }
        final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(now - previous);
        if (elapsedMillis <= 0L) {
            return;
        }

        final long intervalMillis = Math.max(1L,
                configManager.getLong(BASE + ".reward-interval-seconds", 600L)) * 1000L;
        final long progressed;
        synchronized (stateLock) {
            progressed = progressMillis.updateAndGet(current ->
                    Math.min(intervalMillis, current + elapsedMillis));
        }
        if (progressed < intervalMillis) {
            return;
        }

        PendingReward pending = pendingReward();
        if (pending == null) {
            pending = rollPendingReward();
            if (pending == null) {
                if (rewardConfigWarningSent.compareAndSet(false, true)) {
                    plugin.getLogger().warning("Csodálatos Bingulus: nincs kisorsolható, érvényes jutalom a konfigurációban.");
                }
                return;
            }
            rewardConfigWarningSent.set(false);
            synchronized (stateLock) {
                pendingRarity.set(pending.rarity());
                pendingEntry.set(pending.entry());
            }
        }

        ItemStack reward = pendingItem.get();
        if (reward == null) {
            reward = resolveReward(owner, pending.entry());
            if (reward == null || reward.getType().isAir()) {
                plugin.getLogger().warning("Csodálatos Bingulus: nem építhető jutalom: " + pending.entry());
                clearPendingReward();
                requestSave();
                return;
            }
            synchronized (stateLock) {
                pendingItem.set(reward.clone());
            }
            requestSave();
        } else {
            reward = reward.clone();
        }

        if (!canFit(owner.getInventory(), reward)) {
            if (rewardInventoryNoticeSent.compareAndSet(false, true)) {
                owner.sendMessage(messageManager.get("dev-item.inventory-full",
                        "&eA Csodálatos Bingulus jutalma várakozik. Szabadíts fel egy helyet az inventorydban!"));
            }
            return;
        }

        final Map<Integer, ItemStack> leftovers = owner.getInventory().addItem(reward);
        if (!leftovers.isEmpty()) {
            // The capacity check and add happen on the same entity thread, so this is only a defensive
            // fallback for API/component edge cases. Preserve the exact remaining part rather than drop it.
            final ItemStack remainder = combineLeftovers(leftovers.values());
            synchronized (stateLock) {
                pendingItem.set(remainder);
            }
            if (rewardInventoryNoticeSent.compareAndSet(false, true)) {
                owner.sendMessage(messageManager.get("dev-item.inventory-full",
                        "&eA Csodálatos Bingulus jutalma várakozik. Szabadíts fel egy helyet az inventorydban!"));
            }
            requestSave();
            return;
        }

        synchronized (stateLock) {
            rewardInventoryNoticeSent.set(false);
            progressMillis.set(0L);
            clearPendingReward();
            updatePityAfter(pending.rarity());
        }
        announce(owner, pending.rarity(), reward);
        requestSave();
    }

    private ItemStack combineLeftovers(final Iterable<ItemStack> leftovers) {
        ItemStack combined = null;
        int amount = 0;
        for (final ItemStack leftover : leftovers) {
            if (leftover == null || leftover.getType().isAir()) {
                continue;
            }
            if (combined == null) {
                combined = leftover.clone();
                amount = combined.getAmount();
            } else if (combined.isSimilar(leftover)) {
                amount += leftover.getAmount();
            }
        }
        if (combined != null) {
            combined.setAmount(Math.max(1, amount));
        }
        return combined;
    }

    private boolean ensureAuthoritativeItem(final Player player, final boolean allowRestore,
                                             final boolean refreshVisuals) {
        if (!isOwner(player)) {
            removeAllBingulusItems(player);
            return false;
        }

        boolean found = false;
        boolean authoritativeCopySeen = false;
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            final ItemStack item = contents[slot];
            if (!itemFactory.isBingulus(item)) {
                continue;
            }
            if (!found && isAuthoritative(item)) {
                authoritativeCopySeen = true;
                found = true;
                if (refreshVisuals) {
                    player.getInventory().setItem(slot, itemFactory.createBingulus(ownerUuid(), instanceId));
                } else if (item.getAmount() != 1) {
                    item.setAmount(1);
                }
            } else {
                player.getInventory().setItem(slot, null);
            }
        }

        final ItemStack cursor = player.getItemOnCursor();
        if (itemFactory.isBingulus(cursor)) {
            if (!found && isAuthoritative(cursor)) {
                authoritativeCopySeen = true;
                found = true;
                if (refreshVisuals) {
                    player.setItemOnCursor(itemFactory.createBingulus(ownerUuid(), instanceId));
                } else if (cursor.getAmount() != 1) {
                    cursor.setAmount(1);
                }
            } else {
                player.setItemOnCursor(new ItemStack(Material.AIR));
            }
        }

        // A DEV item cannot live in an ender chest. Legacy/bugged copies are removed and, if this
        // was the authoritative copy, restored into the personal inventory below.
        for (int slot = 0; slot < player.getEnderChest().getSize(); slot++) {
            final ItemStack item = player.getEnderChest().getItem(slot);
            if (!itemFactory.isBingulus(item)) {
                continue;
            }
            if (isAuthoritative(item)) {
                authoritativeCopySeen = true;
            }
            player.getEnderChest().setItem(slot, null);
        }

        boolean discoveredIssued = false;
        if (authoritativeCopySeen) {
            synchronized (stateLock) {
                discoveredIssued = issued.compareAndSet(false, true);
            }
        }
        if (discoveredIssued) {
            requestSave();
        }
        if (found) {
            restoreInventoryNoticeSent.set(false);
            return true;
        }
        if (!allowRestore || !issued.get()) {
            return false;
        }

        final int empty = player.getInventory().firstEmpty();
        if (empty < 0) {
            if (restoreInventoryNoticeSent.compareAndSet(false, true)) {
                player.sendMessage(messageManager.get("dev-item.restore-full",
                        "&eA Csodálatos Bingulus visszatérne hozzád, de nincs szabad inventoryhelyed."));
            }
            return false;
        }
        player.getInventory().setItem(empty, itemFactory.createBingulus(ownerUuid(), instanceId));
        restoreInventoryNoticeSent.set(false);
        player.sendMessage(messageManager.get("dev-item.restored",
                "&d✦ A Csodálatos Bingulus visszatért hozzád."));
        return true;
    }

    private boolean isAuthoritative(final ItemStack item) {
        return itemFactory.isBingulus(item)
                && ownerUuid().equals(itemFactory.ownerOf(item))
                && instanceId.equals(itemFactory.instanceOf(item));
    }

    private void removeAllBingulusItems(final Player player) {
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (itemFactory.isBingulus(contents[slot])) {
                player.getInventory().setItem(slot, null);
            }
        }
        if (itemFactory.isBingulus(player.getItemOnCursor())) {
            player.setItemOnCursor(new ItemStack(Material.AIR));
        }
        for (int slot = 0; slot < player.getEnderChest().getSize(); slot++) {
            if (itemFactory.isBingulus(player.getEnderChest().getItem(slot))) {
                player.getEnderChest().setItem(slot, null);
            }
        }
    }

    private PendingReward pendingReward() {
        synchronized (stateLock) {
            final String rarity = pendingRarity.get();
            final String entry = pendingEntry.get();
            return rarity.isBlank() || entry.isBlank() ? null : new PendingReward(rarity, entry);
        }
    }

    private void clearPendingReward() {
        synchronized (stateLock) {
            pendingRarity.set("");
            pendingEntry.set("");
            pendingItem.set(null);
        }
    }

    private PendingReward rollPendingReward() {
        final String minimum = forcedMinimumRarity();
        final String rarity = weightedRarity(minimum);
        if (rarity == null) {
            return null;
        }
        final String entry = weightedEntry(rarity);
        return entry == null ? null : new PendingReward(rarity, entry);
    }

    private String forcedMinimumRarity() {
        synchronized (stateLock) {
            if (sinceLegendary.get() >= pityThreshold("legendas", 1000)) {
                return "legendas";
            }
            if (sinceEpic.get() >= pityThreshold("epikus", 150)) {
                return "epikus";
            }
            if (sinceRare.get() >= pityThreshold("ritka", 30)) {
                return "ritka";
            }
            return "kozonseges";
        }
    }

    private int pityThreshold(final String rarity, final int fallback) {
        return Math.max(1, configManager.getInt(BASE + ".pity." + rarity + ".after-rolls", fallback));
    }

    private String weightedRarity(final String minimum) {
        final ConfigurationSection section = configurationSection(BASE + ".rarity-weights");
        if (section == null) {
            return null;
        }
        final int minRank = rankOf(minimum);
        final List<WeightedValue> values = new ArrayList<>();
        for (final String key : section.getKeys(false)) {
            final int rank = rankOf(key);
            final double weight = section.getDouble(key, 0.0D);
            if (rank >= minRank && weight > 0.0D && hasValidReward(key)) {
                values.add(new WeightedValue(key.toLowerCase(Locale.ROOT), weight));
            }
        }
        return pick(values);
    }

    private boolean hasValidReward(final String rarity) {
        final ConfigurationSection section = configurationSection(BASE + ".rewards." + rarity);
        if (section == null) {
            return false;
        }
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection reward = section.getConfigurationSection(key);
            if (reward == null || reward.getDouble("weight", 1.0D) <= 0.0D) {
                continue;
            }
            if (describeRewardProblem(reward.getString("entry", "")) == null) {
                return true;
            }
        }
        return false;
    }

    private String weightedEntry(final String rarity) {
        final ConfigurationSection section = configurationSection(BASE + ".rewards." + rarity);
        if (section == null) {
            return null;
        }
        final List<WeightedValue> values = new ArrayList<>();
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection reward = section.getConfigurationSection(key);
            if (reward == null) {
                continue;
            }
            final String entry = reward.getString("entry", "");
            final double weight = reward.getDouble("weight", 1.0D);
            if (weight > 0.0D && describeRewardProblem(entry) == null) {
                values.add(new WeightedValue(entry, weight));
            }
        }
        return pick(values);
    }

    private String pick(final List<WeightedValue> values) {
        double total = 0.0D;
        for (final WeightedValue value : values) {
            total += value.weight();
        }
        if (total <= 0.0D) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (final WeightedValue value : values) {
            roll -= value.weight();
            if (roll < 0.0D) {
                return value.value();
            }
        }
        return values.get(values.size() - 1).value();
    }

    private ItemStack resolveReward(final Player owner, final String rawEntry) {
        final String entry = rawEntry.trim();
        final int separator = entry.indexOf(':');
        if (separator > 0) {
            final String type = entry.substring(0, separator).toLowerCase(Locale.ROOT);
            final String id = entry.substring(separator + 1).trim();
            if ("unique".equals(type)) {
                final String[] parts = id.split(":");
                int amount = 1;
                if (parts.length >= 2) {
                    try {
                        amount = Math.max(1, Integer.parseInt(parts[1]));
                    } catch (final NumberFormatException ignored) {
                        amount = 1;
                    }
                }
                return uniqueMaterials.create(parts[0], amount);
            }
            if ("recipe".equals(type)) {
                final ProfessionRecipeCatalog.Recipe recipe = recipeCatalog.get(id);
                return recipe == null ? null : recipeBuilder.buildResult(owner, recipe);
            }
            if ("blueprint".equals(type)) {
                return recipeCatalog.get(id) == null ? null : blueprintFactory.create(id);
            }
            if ("relic".equals(type) || "relikvia".equals(type)) {
                return null;
            }
        }
        return LootTable.parseEntry(entry);
    }

    private String describeRewardProblem(final String rawEntry) {
        if (rawEntry == null || rawEntry.isBlank()) {
            return "üres entry";
        }
        final String entry = rawEntry.trim();
        final int separator = entry.indexOf(':');
        if (separator > 0) {
            final String type = entry.substring(0, separator).toLowerCase(Locale.ROOT);
            final String rest = entry.substring(separator + 1).trim();
            if ("unique".equals(type)) {
                final String[] parts = rest.split(":");
                if (parts.length < 1 || parts[0].isBlank()) {
                    return "hiányzó unique azonosító";
                }
                if (parts.length > 2) {
                    return "a unique entry csak unique:<id>[:darab] alakú lehet";
                }
                if (!uniqueMaterials.isDefined(parts[0])) {
                    return "ismeretlen unique azonosító: " + parts[0];
                }
                if (parts.length == 2) {
                    try {
                        if (Integer.parseInt(parts[1]) < 1) {
                            return "a darabszám minimum 1";
                        }
                    } catch (final NumberFormatException exception) {
                        return "nem szám a darabszám: " + parts[1];
                    }
                }
                return null;
            }
            if ("recipe".equals(type)) {
                return rest.isBlank() || recipeCatalog.get(rest) == null
                        ? "ismeretlen recipe azonosító: " + rest : null;
            }
            if ("blueprint".equals(type)) {
                return rest.isBlank() || recipeCatalog.get(rest) == null
                        ? "ismeretlen blueprint azonosító: " + rest : null;
            }
            if ("relic".equals(type) || "relikvia".equals(type)) {
                return "relikvia nem engedélyezett DEV-item jutalomként";
            }
        }
        return LootTable.describeProblem(entry);
    }

    private boolean canFit(final PlayerInventory inventory, final ItemStack incoming) {
        int remaining = incoming.getAmount();
        final int maxStack = Math.max(1, incoming.getMaxStackSize());
        for (final ItemStack existing : inventory.getStorageContents()) {
            if (existing == null || existing.getType().isAir()) {
                remaining -= maxStack;
            } else if (existing.isSimilar(incoming)) {
                remaining -= Math.max(0, maxStack - existing.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private void updatePityAfter(final String rarity) {
        synchronized (stateLock) {
            final int rank = rankOf(rarity);
            updateCounter(sinceRare, rank >= rankOf("ritka"));
            updateCounter(sinceEpic, rank >= rankOf("epikus"));
            updateCounter(sinceLegendary, rank >= rankOf("legendas"));
        }
    }

    private void updateCounter(final AtomicInteger counter, final boolean reset) {
        if (reset) {
            counter.set(0);
        } else {
            counter.incrementAndGet();
        }
    }

    private void announce(final Player player, final String rarity, final ItemStack reward) {
        final String rarityName = configManager.getString(
                "item-rarity.rarities." + rarity + ".name", rarity);
        final String rarityColor = configManager.getString(
                "item-rarity.rarities." + rarity + ".color", "&f");
        final String itemName = reward.hasItemMeta() && reward.getItemMeta().hasDisplayName()
                ? PlainTextComponentSerializer.plainText().serialize(reward.getItemMeta().displayName())
                : prettyMaterial(reward.getType());

        player.sendMessage(messageManager.get("dev-item.reward",
                "&d✦ A Csodálatos Bingulus jutalma: %s%s &7— &f%s &7×%s",
                rarityColor, rarityName, itemName, String.valueOf(reward.getAmount())));

        final int rank = Math.max(0, rankOf(rarity));
        final Sound sound = rank >= rankOf("legendas")
                ? Sound.ITEM_TOTEM_USE
                : rank >= rankOf("epikus") ? Sound.ENTITY_PLAYER_LEVELUP
                : Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        player.playSound(player.getLocation(), sound, 0.9F, rank >= rankOf("ritka") ? 1.2F : 1.0F);
        if (rank >= rankOf("epikus")) {
            player.getWorld().spawnParticle(Particle.END_ROD,
                    player.getLocation().add(0.0D, 1.0D, 0.0D), 18, 0.55D, 0.8D, 0.55D, 0.02D);
        }
    }

    private int rankOf(final String rarity) {
        if (rarity == null) {
            return -1;
        }
        return RARITY_ORDER.indexOf(rarity.toLowerCase(Locale.ROOT));
    }

    private String prettyMaterial(final Material material) {
        final String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder result = new StringBuilder();
        for (final String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private ConfigurationSection configurationSection(final String path) {
        return configManager.getConfiguration() == null
                ? null : configManager.getConfiguration().getConfigurationSection(path);
    }

    private void reconcileConfiguredOwner() {
        final UUID current = ownerUuid();
        final UUID previous;
        synchronized (stateLock) {
            previous = boundOwner.get();
            if (previous == null) {
                boundOwner.set(current);
                return;
            }
            if (previous.equals(current)) {
                return;
            }
            // Owner identity and the reset instance must be one snapshot boundary. Otherwise an
            // async save can persist the new owner together with the old owner's singleton state.
            boundOwner.set(current);
            resetRuntimeState();
        }
        plugin.getLogger().info("Csodálatos Bingulus: tulajdonos változott (" + previous + " → " + current
                + "), az instance és a jutalomprogressz alaphelyzetbe állt.");
        requestSave();
    }

    private void validateConfiguration() {
        rewardConfigWarningSent.set(false);
        int warningCount = 0;

        final String rawOwner = configManager.getString(BASE + ".owner-uuid", DEFAULT_OWNER.toString());
        try {
            UUID.fromString(rawOwner);
        } catch (final IllegalArgumentException exception) {
            plugin.getLogger().warning("dev-items.yml: érvénytelen owner-uuid, alapértelmezett UUID használva: "
                    + rawOwner);
            warningCount++;
        }

        final ConfigurationSection weights = configurationSection(BASE + ".rarity-weights");
        if (weights == null) {
            plugin.getLogger().warning("dev-items.yml: hiányzik a rarity-weights szekció.");
            warningCount++;
        } else {
            for (final String key : weights.getKeys(false)) {
                if (rankOf(key) < 0) {
                    plugin.getLogger().warning("dev-items.yml: ismeretlen jutalomritkaság: " + key);
                    warningCount++;
                    continue;
                }
                final double weight = weights.getDouble(key, 0.0D);
                if (weight < 0.0D) {
                    plugin.getLogger().warning("dev-items.yml: negatív rarity-súly: " + key + " = " + weight);
                    warningCount++;
                } else if (weight > 0.0D && !hasValidReward(key)) {
                    plugin.getLogger().warning("dev-items.yml: a(z) " + key
                            + " ritkaságnak nincs pozitív súlyú, érvényes jutalma.");
                    warningCount++;
                }
            }
        }

        for (final String rarity : RARITY_ORDER) {
            final ConfigurationSection rewards = configurationSection(BASE + ".rewards." + rarity);
            if (rewards == null) {
                continue;
            }
            for (final String rewardKey : rewards.getKeys(false)) {
                final ConfigurationSection reward = rewards.getConfigurationSection(rewardKey);
                if (reward == null) {
                    plugin.getLogger().warning("dev-items.yml: a jutalom nem szekció: "
                            + rarity + "." + rewardKey);
                    warningCount++;
                    continue;
                }
                final double weight = reward.getDouble("weight", 1.0D);
                if (weight < 0.0D) {
                    plugin.getLogger().warning("dev-items.yml: negatív jutalomsúly: "
                            + rarity + "." + rewardKey + " = " + weight);
                    warningCount++;
                }
                final String entry = reward.getString("entry", "");
                final String problem = describeRewardProblem(entry);
                if (problem != null) {
                    plugin.getLogger().warning("dev-items.yml: hibás jutalom " + rarity + "." + rewardKey
                            + " ('" + entry + "'): " + problem);
                    warningCount++;
                }
            }
        }

        if (warningCount == 0) {
            plugin.getLogger().info("Csodálatos Bingulus: a rarity- és jutalomtáblák érvényesek.");
        }
    }

    private void resetRuntimeState() {
        synchronized (stateLock) {
            issued.set(false);
            instanceId = UUID.randomUUID();
            progressMillis.set(0L);
            clearPendingReward();
            sinceRare.set(0);
            sinceEpic.set(0);
            sinceLegendary.set(0);
            rewardInventoryNoticeSent.set(false);
            restoreInventoryNoticeSent.set(false);
            lastActiveNanos.set(0L);
        }
    }

    private DevItemStateCodec.Snapshot stateSnapshot() {
        synchronized (stateLock) {
            final UUID persistedOwner = boundOwner.get() == null ? ownerUuid() : boundOwner.get();
            return new DevItemStateCodec.Snapshot(persistedOwner, issued.get(), instanceId,
                    progressMillis.get(), pendingRarity.get(), pendingEntry.get(), pendingItem.get(),
                    sinceRare.get(), sinceEpic.get(), sinceLegendary.get());
        }
    }

    private void applySnapshot(final DevItemStateCodec.Snapshot snapshot) {
        synchronized (stateLock) {
            instanceId = snapshot.instanceId();
            issued.set(snapshot.issued());
            progressMillis.set(snapshot.progressMillis());
            pendingRarity.set(snapshot.pendingRarity());
            pendingEntry.set(snapshot.pendingEntry());
            pendingItem.set(snapshot.pendingItem());
            sinceRare.set(snapshot.sinceRare());
            sinceEpic.set(snapshot.sinceEpic());
            sinceLegendary.set(snapshot.sinceLegendary());
            rewardInventoryNoticeSent.set(false);
            restoreInventoryNoticeSent.set(false);
            lastActiveNanos.set(0L);
        }
    }

    @Override
    public void load() {
        final UUID configuredOwner = ownerUuid();
        boundOwner.set(configuredOwner);
        if (!stateFile.exists()) {
            resetRuntimeState();
            boundOwner.set(configuredOwner);
            validateConfiguration();
            return;
        }

        final YamlConfiguration yaml = YamlStore.loadTracked(stateFile, plugin.getLogger());
        final DevItemStateCodec.Snapshot loaded;
        try {
            loaded = DevItemStateCodec.decode(yaml);
        } catch (final IllegalArgumentException invalidState) {
            YamlStore.failCorrupt(stateFile, plugin.getLogger(), invalidState.getMessage());
            throw new AssertionError("unreachable");
        }

        if (!loaded.owner().equals(configuredOwner)) {
            resetRuntimeState();
            boundOwner.set(configuredOwner);
            plugin.getLogger().info("Csodálatos Bingulus: a mentett tulajdonos eltér a konfigurálttól; "
                    + "tiszta instance indul.");
            save();
            validateConfiguration();
            return;
        }

        applySnapshot(loaded);
        validateConfiguration();
    }

    @Override
    public synchronized void save() {
        final YamlConfiguration yaml = DevItemStateCodec.encode(stateSnapshot());
        try {
            YamlStore.saveAtomic(stateFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("A DEV item állapota nem menthető: " + exception.getMessage());
        }
    }

    private void requestSave() {
        if (!plugin.isEnabled()) {
            return;
        }
        saveAgain.set(true);
        if (!saveQueued.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                do {
                    saveAgain.set(false);
                    save();
                } while (saveAgain.get());
            } finally {
                saveQueued.set(false);
                if (saveAgain.get()) {
                    requestSave();
                }
            }
        });
    }
}
