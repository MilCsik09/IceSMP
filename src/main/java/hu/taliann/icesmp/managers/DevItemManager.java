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
import java.io.UncheckedIOException;
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
    private static final DevItemRewardTransition.ExactItemPolicy<ItemStack> ITEM_POLICY =
            new DevItemRewardTransition.ExactItemPolicy<>() {
                @Override
                public ItemStack copy(final ItemStack item) {
                    return item == null ? null : item.clone();
                }

                @Override
                public boolean isValid(final ItemStack item) {
                    return item != null && !item.getType().isAir() && item.getAmount() > 0;
                }

                @Override
                public boolean same(final ItemStack left, final ItemStack right) {
                    return left != null && right != null
                            && left.getAmount() == right.getAmount()
                            && left.isSimilar(right);
                }
            };

    private record WeightedValue(String value, double weight) {
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
    private final DevItemRewardTransition.TickGate ownerTickGate =
            new DevItemRewardTransition.TickGate();
    private final AtomicLong lastActiveNanos = new AtomicLong();
    private final AtomicReference<UUID> boundOwner = new AtomicReference<>();
    private final AtomicBoolean rewardConfigWarningSent = new AtomicBoolean();
    private final AtomicBoolean stateHealthWarningSent = new AtomicBoolean();

    /** Monotonic live-owner fence. Read and mutated only while holding stateLock. */
    private long ownerGeneration;
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
        // Issuance and reward delivery require an observable durable commit. A failed write must
        // therefore trip the shared critical-persistence circuit instead of being logged away.
        YamlStore.registerCriticalWrite(stateFile);
    }

    public DevItemFactory itemFactory() {
        return itemFactory;
    }

    public UUID ownerUuid() {
        final UUID loadedOwner = boundOwner.get();
        return loadedOwner == null ? configuredOwnerUuid() : loadedOwner;
    }

    private UUID configuredOwnerUuid() {
        final String raw = configManager.getString(BASE + ".owner-uuid", DEFAULT_OWNER.toString());
        try {
            return UUID.fromString(raw.trim());
        } catch (final RuntimeException invalid) {
            throw new IllegalStateException("Érvénytelen DEV-item owner-uuid: '" + raw + "'", invalid);
        }
    }

    public boolean isOwner(final Player player) {
        return player != null && ownerUuid().equals(player.getUniqueId());
    }

    public void start() {
        // Offline config changes are committed before the new owner becomes live.
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
        ownerTickGate.exit();
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
        requireHealthyState();
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

        final DevItemRewardTransition.OwnerFence expectedFence = currentOwnerFence(target.getUniqueId());
        if (expectedFence == null) {
            return false;
        }
        // Commit issued=true before the authentic item becomes visible.
        markIssuedDurably();
        if (!isCurrentOwnerFence(expectedFence, target.getUniqueId())) {
            return false;
        }
        removeAllBingulusItems(target);
        target.getInventory().setItem(reusableSlot, itemFactory.createBingulus(ownerUuid(), instanceId));
        if (!isCurrentOwnerFence(expectedFence, target.getUniqueId())) {
            removeAllBingulusItems(target);
            return false;
        }
        rewardInventoryNoticeSent.set(false);
        restoreInventoryNoticeSent.set(false);
        lastActiveNanos.set(0L);
        return true;
    }

    private void markIssuedDurably() {
        synchronized (this) {
            final DevItemRewardTransition.State<ItemStack> rewardState;
            final YamlConfiguration snapshot;
            synchronized (stateLock) {
                if (issued.get()) {
                    return;
                }
                rewardState = rewardStateLocked();
                snapshot = snapshotYamlLocked(rewardState, true);
            }
            // Publish issued=true only after the durable snapshot succeeds.
            writeSnapshot(snapshot);
            synchronized (stateLock) {
                issued.set(true);
            }
        }
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
        if (!hasHealthyState()) {
            logUnhealthyStateOnce();
            lastActiveNanos.set(0L);
            return;
        }
        stateHealthWarningSent.set(false);
        if (!isEnabled() || !issued.get()) {
            lastActiveNanos.set(0L);
            return;
        }
        final Player owner = Bukkit.getPlayer(ownerUuid());
        if (owner == null) {
            lastActiveNanos.set(0L);
            return;
        }
        if (!ownerTickGate.tryEnter()) {
            return;
        }
        owner.getScheduler().run(plugin, task -> {
            try {
                tickOwner(owner);
            } finally {
                ownerTickGate.exit();
            }
        }, () -> {
            ownerTickGate.exit();
            lastActiveNanos.set(0L);
        });
    }

    private void tickOwner(final Player owner) {
        if (!owner.isOnline() || !issued.get()) {
            lastActiveNanos.set(0L);
            return;
        }
        if (!ensureAuthoritativeItem(owner, autoRestoreEnabled(), false)) {
            lastActiveNanos.set(0L);
            return;
        }
        final DevItemRewardTransition.OwnerFence expectedFence =
                currentOwnerFence(owner.getUniqueId());
        if (expectedFence == null) {
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
        final DevItemRewardTransition.State<ItemStack> progressedState;
        synchronized (this) {
            synchronized (stateLock) {
                final DevItemRewardTransition.State<ItemStack> current = rewardStateLocked();
                if (!current.fence().equals(expectedFence)) {
                    return;
                }
                progressedState = DevItemRewardTransition.advanceProgress(
                        current, expectedFence, elapsedMillis, intervalMillis, ITEM_POLICY);
                applyRewardStateLocked(progressedState);
            }
        }
        if (progressedState.progressMillis() < intervalMillis) {
            return;
        }

        DevItemRewardTransition.Pending<ItemStack> pending = progressedState.pending();
        if (pending == null) {
            final String rarity = weightedRarity(forcedMinimumRarity());
            final String entry = rarity == null ? null : weightedEntry(rarity);
            if (entry == null) {
                if (rewardConfigWarningSent.compareAndSet(false, true)) {
                    plugin.getLogger().warning(
                            "Csodálatos Bingulus: nincs kisorsolható, érvényes jutalom a konfigurációban.");
                }
                return;
            }
            rewardConfigWarningSent.set(false);
            final ItemStack rolled = resolveReward(owner, entry);
            if (!ITEM_POLICY.isValid(rolled)) {
                plugin.getLogger().warning("Csodálatos Bingulus: nem építhető jutalom: " + entry);
                return;
            }
            final DevItemRewardTransition.Pending<ItemStack> proposed =
                    DevItemRewardTransition.pending(rarity, entry, rolled, ITEM_POLICY,
                            value -> rankOf(value) >= 0);
            pending = preparePendingRewardDurably(expectedFence, proposed);
            if (pending == null) {
                return;
            }
        }

        final ItemStack reward = ITEM_POLICY.copy(pending.exactItem());
        if (!isCurrentOwnerFence(expectedFence, owner.getUniqueId())) {
            return;
        }
        if (!canFit(owner.getInventory(), reward)) {
            if (rewardInventoryNoticeSent.compareAndSet(false, true)) {
                owner.sendMessage(messageManager.get("dev-item.inventory-full",
                        "&eA Csodálatos Bingulus jutalma várakozik. Szabadíts fel egy helyet az inventorydban!"));
            }
            return;
        }

        final ItemStack[] inventoryBefore = cloneStorageContents(owner.getInventory());
        final Map<Integer, ItemStack> leftovers = owner.getInventory().addItem(reward.clone());
        if (!leftovers.isEmpty()) {
            owner.getInventory().setStorageContents(inventoryBefore);
            if (rewardInventoryNoticeSent.compareAndSet(false, true)) {
                owner.sendMessage(messageManager.get("dev-item.inventory-full",
                        "&eA Csodálatos Bingulus jutalma várakozik. Szabadíts fel egy helyet az inventorydban!"));
            }
            return;
        }

        try {
            if (!completePendingRewardDurably(expectedFence, owner.getUniqueId(), pending)) {
                owner.getInventory().setStorageContents(inventoryBefore);
                return;
            }
        } catch (final RuntimeException | Error failure) {
            owner.getInventory().setStorageContents(inventoryBefore);
            throw failure;
        }
        rewardInventoryNoticeSent.set(false);
        announce(owner, pending.rarity(), reward);
    }

    private ItemStack[] cloneStorageContents(final PlayerInventory inventory) {
        final ItemStack[] contents = inventory.getStorageContents();
        final ItemStack[] clone = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            clone[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
        return clone;
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

        // A DEV item cannot live in an ender chest. Forbidden copies are removed and, if this
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

        // Even after a persistence failure, keep enforcing the physical singleton by removing
        // duplicate/forbidden copies. Stateful recovery and the reward clock stay closed.
        if (!hasHealthyState()) {
            logUnhealthyStateOnce();
            return false;
        }
        stateHealthWarningSent.set(false);

        if (authoritativeCopySeen && !issued.get()) {
            // A recovered authentic copy must become durable before it can reactivate the reward
            // clock. Otherwise a crash can leave a live singleton that the state file calls unissued.
            markIssuedDurably();
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

    private DevItemRewardTransition.Pending<ItemStack> pendingRewardLocked() {
        final String rarity = pendingRarity.get();
        final String entry = pendingEntry.get();
        final ItemStack item = pendingItem.get();
        final boolean hasRarity = !rarity.isBlank();
        final boolean hasEntry = !entry.isBlank();
        final boolean hasItem = ITEM_POLICY.isValid(item);
        if (!hasRarity && !hasEntry && item == null) {
            return null;
        }
        if (hasRarity != hasEntry || hasRarity != hasItem) {
            throw new IllegalStateException("A pending DEV-item jutalom állapota részleges vagy sérült.");
        }
        return DevItemRewardTransition.pending(rarity, entry, item, ITEM_POLICY,
                value -> rankOf(value) >= 0);
    }

    private void clearPendingRewardLocked() {
        pendingRarity.set("");
        pendingEntry.set("");
        pendingItem.set(null);
    }

    private DevItemRewardTransition.State<ItemStack> rewardStateLocked() {
        final UUID owner = boundOwner.get();
        if (owner == null) {
            throw new IllegalStateException("A DEV-item owner állapota még nincs betöltve.");
        }
        return DevItemRewardTransition.state(
                new DevItemRewardTransition.OwnerFence(owner, ownerGeneration),
                progressMillis.get(), pendingRewardLocked(),
                new DevItemRewardTransition.PityCounters(
                        sinceRare.get(), sinceEpic.get(), sinceLegendary.get()),
                ITEM_POLICY);
    }

    private void applyRewardStateLocked(final DevItemRewardTransition.State<ItemStack> state) {
        boundOwner.set(state.fence().owner());
        ownerGeneration = state.fence().generation();
        progressMillis.set(state.progressMillis());
        final DevItemRewardTransition.Pending<ItemStack> pending = state.pending();
        pendingRarity.set(pending == null ? "" : pending.rarity());
        pendingEntry.set(pending == null ? "" : pending.entry());
        pendingItem.set(pending == null ? null : ITEM_POLICY.copy(pending.exactItem()));
        sinceRare.set(state.pity().sinceRare());
        sinceEpic.set(state.pity().sinceEpic());
        sinceLegendary.set(state.pity().sinceLegendary());
    }

    private DevItemRewardTransition.OwnerFence currentOwnerFence(final UUID actor) {
        synchronized (this) {
            synchronized (stateLock) {
                final DevItemRewardTransition.OwnerFence fence = rewardStateLocked().fence();
                return fence.owner().equals(actor) ? fence : null;
            }
        }
    }

    private boolean isCurrentOwnerFence(final DevItemRewardTransition.OwnerFence expected,
                                        final UUID actor) {
        synchronized (this) {
            synchronized (stateLock) {
                final DevItemRewardTransition.OwnerFence current = rewardStateLocked().fence();
                return current.equals(expected) && current.owner().equals(actor);
            }
        }
    }

    private DevItemRewardTransition.Pending<ItemStack> preparePendingRewardDurably(
            final DevItemRewardTransition.OwnerFence expectedFence,
            final DevItemRewardTransition.Pending<ItemStack> proposed) {
        requireHealthyState();
        synchronized (this) {
            final DevItemRewardTransition.State<ItemStack> current;
            synchronized (stateLock) {
                current = rewardStateLocked();
            }
            final DevItemRewardTransition.Preparation<ItemStack> preparation =
                    DevItemRewardTransition.prepare(current, expectedFence, proposed,
                            ITEM_POLICY, this::writeRewardStateCandidate);
            if (!preparation.prepared()) {
                return null;
            }
            synchronized (stateLock) {
                applyRewardStateLocked(preparation.state());
            }
            return preparation.pending();
        }
    }

    private boolean completePendingRewardDurably(
            final DevItemRewardTransition.OwnerFence expectedFence,
            final UUID actor,
            final DevItemRewardTransition.Pending<ItemStack> expectedPending) {
        requireHealthyState();
        synchronized (this) {
            final DevItemRewardTransition.State<ItemStack> current;
            synchronized (stateLock) {
                current = rewardStateLocked();
            }
            final DevItemRewardTransition.Completion<ItemStack> completion =
                    DevItemRewardTransition.complete(current, expectedFence, actor, expectedPending,
                            ITEM_POLICY, this::pityAfter, this::writeRewardStateCandidate);
            if (!completion.committed()) {
                return false;
            }
            synchronized (stateLock) {
                applyRewardStateLocked(completion.state());
            }
            return true;
        }
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
        }
        return "kozonseges";
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

    private DevItemRewardTransition.PityCounters pityAfter(
            final String rarity, final DevItemRewardTransition.PityCounters current) {
        final int rank = rankOf(rarity);
        return new DevItemRewardTransition.PityCounters(
                rank >= rankOf("ritka") ? 0 : current.sinceRare() + 1,
                rank >= rankOf("epikus") ? 0 : current.sinceEpic() + 1,
                rank >= rankOf("legendas") ? 0 : current.sinceLegendary() + 1);
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
        requireHealthyState();
        final UUID configured = configuredOwnerUuid();
        final UUID previous;
        synchronized (this) {
            final DevItemRewardTransition.State<ItemStack> current;
            synchronized (stateLock) {
                current = rewardStateLocked();
                previous = current.fence().owner();
            }
            if (previous.equals(configured)) {
                return;
            }
            // The candidate is written before boundOwner/ownerGeneration are published.
            final DevItemRewardTransition.State<ItemStack> transferred =
                    DevItemRewardTransition.transfer(current, configured, ITEM_POLICY,
                            this::writeRewardStateCandidate);
            synchronized (stateLock) {
                applyRewardStateLocked(transferred);
            }
        }
        lastActiveNanos.set(0L);
        plugin.getLogger().info("Csodálatos Bingulus: tulajdonos változott (" + previous + " → " + configured
                + "), az instance, az aktívidő, a pity és a pending jutalom megőrizve.");
    }

    private boolean hasHealthyState() {
        return !YamlStore.isLoadFailed(stateFile) && !YamlStore.hasWriteFailure(stateFile);
    }

    private void requireHealthyState() {
        if (!hasHealthyState()) {
            throw new IllegalStateException("A DEV-item állapottár sérült vagy tartós írási hibát jelzett; "
                    + "a művelet restartig megtagadva.");
        }
    }

    private void logUnhealthyStateOnce() {
        if (stateHealthWarningSent.compareAndSet(false, true)) {
            plugin.getLogger().severe("A DEV-item állapottár nem egészséges; aktívidő, recovery és jutalomkiosztás "
                    + "restartig fail-closed állapotban marad.");
        }
    }

    private void validateConfiguration() {
        rewardConfigWarningSent.set(false);
        int warningCount = 0;

        // Authorization config is fail-closed. configuredOwnerUuid() throws before any online
        // refresh can authorize a fallback UUID.
        configuredOwnerUuid();

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
            ownerGeneration = 0L;
            progressMillis.set(0L);
            clearPendingRewardLocked();
            sinceRare.set(0);
            sinceEpic.set(0);
            sinceLegendary.set(0);
            rewardInventoryNoticeSent.set(false);
            restoreInventoryNoticeSent.set(false);
            lastActiveNanos.set(0L);
        }
    }

    @Override
    public void load() {
        final UUID configuredOwner = configuredOwnerUuid();
        if (!stateFile.exists()) {
            resetRuntimeState();
            synchronized (stateLock) {
                boundOwner.set(configuredOwner);
            }
            stateHealthWarningSent.set(false);
            validateConfiguration();
            return;
        }

        final YamlConfiguration yaml = YamlStore.loadTracked(stateFile, plugin.getLogger());
        try {
            final UUID savedOwner = DevItemStateData.requireUuid(
                    requireString(yaml, "bingulus.owner"), "bingulus.owner");
            final UUID savedInstance = DevItemStateData.requireUuid(
                    requireString(yaml, "bingulus.instance"), "bingulus.instance");
            final boolean savedIssued = requireBoolean(yaml, "bingulus.issued");
            final long savedProgress = requireLong(yaml, "bingulus.progress-millis");
            final String savedPendingRarity = requireString(yaml, "bingulus.pending.rarity");
            final String savedPendingEntry = requireString(yaml, "bingulus.pending.entry");
            final Object rawPendingItem = yaml.get("bingulus.pending.item");
            final ItemStack exactPending = yaml.getItemStack("bingulus.pending.item");
            if (rawPendingItem != null && exactPending == null) {
                throw new IllegalArgumentException("bingulus.pending.item is not a valid ItemStack");
            }
            if (exactPending != null && (exactPending.getType().isAir() || exactPending.getAmount() <= 0)) {
                throw new IllegalArgumentException("bingulus.pending.item is empty");
            }

            final DevItemStateData loaded = new DevItemStateData(
                    savedOwner,
                    savedInstance,
                    savedIssued,
                    savedProgress,
                    savedPendingRarity,
                    savedPendingEntry,
                    exactPending != null,
                    requireInt(yaml, "bingulus.pity.since-rare"),
                    requireInt(yaml, "bingulus.pity.since-epic"),
                    requireInt(yaml, "bingulus.pity.since-legendary"));
            if (loaded.hasPendingReward() && rankOf(loaded.pendingRarity()) < 0) {
                throw new IllegalArgumentException("unknown pending rarity: " + loaded.pendingRarity());
            }

            synchronized (stateLock) {
                boundOwner.set(loaded.owner());
                ownerGeneration = 0L;
                instanceId = loaded.instanceId();
                issued.set(loaded.issued());
                progressMillis.set(loaded.progressMillis());
                pendingRarity.set(loaded.pendingRarity());
                pendingEntry.set(loaded.pendingEntry());
                pendingItem.set(exactPending == null ? null : exactPending.clone());
                sinceRare.set(loaded.rollsSinceRare());
                sinceEpic.set(loaded.rollsSinceEpic());
                sinceLegendary.set(loaded.rollsSinceLegendary());
                rewardInventoryNoticeSent.set(false);
                restoreInventoryNoticeSent.set(false);
                lastActiveNanos.set(0L);
            }
            stateHealthWarningSent.set(false);
        } catch (final RuntimeException invalidState) {
            YamlStore.failCorrupt(stateFile, plugin.getLogger(),
                    "A DEV-item állapota szemantikailag érvénytelen: " + invalidState.getMessage());
        }
        validateConfiguration();
    }

    @Override
    public void save() {
        synchronized (this) {
            final DevItemRewardTransition.State<ItemStack> rewardState;
            final YamlConfiguration snapshot;
            synchronized (stateLock) {
                rewardState = rewardStateLocked();
                snapshot = snapshotYamlLocked(rewardState, issued.get());
            }
            writeSnapshot(snapshot);
        }
    }

    private void writeRewardStateCandidate(
            final DevItemRewardTransition.State<ItemStack> candidate) {
        final YamlConfiguration snapshot;
        synchronized (stateLock) {
            snapshot = snapshotYamlLocked(candidate, issued.get());
        }
        writeSnapshot(snapshot);
    }

    private YamlConfiguration snapshotYamlLocked(
            final DevItemRewardTransition.State<ItemStack> rewardState,
            final boolean issuedValue) {
        final DevItemRewardTransition.Pending<ItemStack> pending = rewardState.pending();
        final ItemStack exactPending = pending == null ? null : pending.exactItem();
        if (exactPending != null && !ITEM_POLICY.isValid(exactPending)) {
            throw new IllegalStateException("Üres ItemStack nem menthető pending DEV-item jutalomként.");
        }

        final DevItemStateData state;
        try {
            state = new DevItemStateData(
                    rewardState.fence().owner(),
                    instanceId,
                    issuedValue,
                    rewardState.progressMillis(),
                    pending == null ? "" : pending.rarity(),
                    pending == null ? "" : pending.entry(),
                    pending != null,
                    rewardState.pity().sinceRare(),
                    rewardState.pity().sinceEpic(),
                    rewardState.pity().sinceLegendary());
        } catch (final IllegalArgumentException invalidState) {
            throw new IllegalStateException("Érvénytelen DEV-item állapotpillanat nem menthető.", invalidState);
        }
        if (state.hasPendingReward() && rankOf(state.pendingRarity()) < 0) {
            throw new IllegalStateException("Ismeretlen pending jutalomritkaság nem menthető: "
                    + state.pendingRarity());
        }

        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("bingulus.owner", state.owner().toString());
        yaml.set("bingulus.issued", state.issued());
        yaml.set("bingulus.instance", state.instanceId().toString());
        yaml.set("bingulus.progress-millis", state.progressMillis());
        yaml.set("bingulus.pending.rarity", state.pendingRarity());
        yaml.set("bingulus.pending.entry", state.pendingEntry());
        yaml.set("bingulus.pending.item", exactPending == null ? null : ITEM_POLICY.copy(exactPending));
        yaml.set("bingulus.pity.since-rare", state.rollsSinceRare());
        yaml.set("bingulus.pity.since-epic", state.rollsSinceEpic());
        yaml.set("bingulus.pity.since-legendary", state.rollsSinceLegendary());
        return yaml;
    }

    private void writeSnapshot(final YamlConfiguration snapshot) {
        try {
            YamlStore.saveAtomic(stateFile, snapshot);
        } catch (final IOException exception) {
            throw new UncheckedIOException("A DEV item állapota nem menthető", exception);
        }
    }

    private String requireString(final YamlConfiguration yaml, final String path) {
        final Object raw = yaml.get(path);
        if (raw instanceof String value) {
            return value;
        }
        throw new IllegalArgumentException(path + " must be a string");
    }

    private boolean requireBoolean(final YamlConfiguration yaml, final String path) {
        final Object raw = yaml.get(path);
        if (raw instanceof Boolean value) {
            return value;
        }
        throw new IllegalArgumentException(path + " must be a boolean");
    }

    private long requireLong(final YamlConfiguration yaml, final String path) {
        final Object raw = yaml.get(path);
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be an integer number");
        }
        final long value = number.longValue();
        if (Double.compare(number.doubleValue(), (double) value) != 0) {
            throw new IllegalArgumentException(path + " must be an integer number");
        }
        return value;
    }

    private int requireInt(final YamlConfiguration yaml, final String path) {
        final long value = requireLong(yaml, path);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(path + " is outside the integer range");
        }
        return (int) value;
    }

}
