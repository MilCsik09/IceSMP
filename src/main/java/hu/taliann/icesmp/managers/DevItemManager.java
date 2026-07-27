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

/** Runtime and persistence for the Csodalatos Bingulus easter-egg DEV item. */
public final class DevItemManager implements PersistentStore {

    private static final UUID DEFAULT_OWNER = UUID.fromString("eb80c20f-092a-4d76-bd44-d168c91ea9e2");
    private static final String BASE = "dev-items." + DevItemFactory.BINGULUS_ID;
    private static final List<String> RARITY_ORDER = List.of(
            "kozonseges", "nem_mindennapi", "ritka", "epikus", "legendas", "ereklye");

    private record WeightedValue(String value, double weight) {
    }

    private record RewardSelection(String rarity, String entry) {
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
    private final DevItemStateData.TickGate ownerTickGate = new DevItemStateData.TickGate();

    private DevItemStateData<ItemStack> state = DevItemStateData.fresh(DEFAULT_OWNER, UUID.randomUUID());
    private volatile ScheduledTask tickTask;
    private volatile long scheduledTicks = -1L;
    private volatile long lastActiveNanos;
    private volatile boolean shuttingDown;
    private volatile boolean rewardInventoryNoticeSent;
    private volatile boolean restoreInventoryNoticeSent;
    private volatile boolean rewardConfigWarningSent;
    private volatile boolean stateHealthWarningSent;

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
        synchronized (stateLock) {
            return state.owner();
        }
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
        shuttingDown = false;
        reconcileConfiguredOwner();
        validateConfiguration();
        rescheduleTickTask();
        refreshOnlineOwnerItems(true);
    }

    public synchronized void shutdown() {
        shuttingDown = true;
        final ScheduledTask current = tickTask;
        tickTask = null;
        scheduledTicks = -1L;
        if (current != null) {
            current.cancel();
        }
        lastActiveNanos = 0L;
        ownerTickGate.exit();
    }

    /** Applies live config reload, including a simple runtime owner transfer. */
    public void refreshOnlineOwner() {
        reconcileConfiguredOwner();
        validateConfiguration();
        rescheduleTickTask();
        refreshOnlineOwnerItems(true);
    }

    public void handleJoin(final Player player) {
        if (isOwner(player)) {
            lastActiveNanos = 0L;
            ensureAuthoritativeItem(player, isEnabled() && autoRestoreEnabled(), true);
        } else {
            removeAllBingulusItems(player);
        }
    }

    public void handleRespawn(final Player player) {
        if (isOwner(player)) {
            lastActiveNanos = 0L;
            ensureAuthoritativeItem(player, true, true);
        }
    }

    /** Admin delivery path. Only the configured owner can receive the singleton item. */
    public boolean giveToOwner(final Player target) {
        requireHealthyState();
        final UUID expectedOwner = ownerUuid();
        if (target == null || !expectedOwner.equals(target.getUniqueId())) {
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

        markIssuedDurably(expectedOwner);
        final DevItemStateData<ItemStack> current = currentState();
        if (!current.ownerIs(expectedOwner)) {
            return false;
        }
        removeAllBingulusItems(target);
        target.getInventory().setItem(reusableSlot,
                itemFactory.createBingulus(current.owner(), current.instanceId()));
        if (!isCurrentOwner(expectedOwner, target.getUniqueId())) {
            removeAllBingulusItems(target);
            return false;
        }
        rewardInventoryNoticeSent = false;
        restoreInventoryNoticeSent = false;
        lastActiveNanos = 0L;
        return true;
    }

    private void markIssuedDurably(final UUID expectedOwner) {
        synchronized (stateLock) {
            requireHealthyState();
            if (!state.ownerIs(expectedOwner)) {
                return;
            }
            if (state.issued()) {
                return;
            }
            final DevItemStateData<ItemStack> candidate = state.issued(ItemStack::clone);
            writeSnapshot(snapshotYaml(candidate));
            state = candidate;
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
        if (!enabled || shuttingDown) {
            if (tickTask != null) {
                tickTask.cancel();
                tickTask = null;
            }
            scheduledTicks = -1L;
            lastActiveNanos = 0L;
            ownerTickGate.exit();
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
        lastActiveNanos = 0L;
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
        if (shuttingDown) {
            ownerTickGate.exit();
            lastActiveNanos = 0L;
            return;
        }
        if (!hasHealthyState()) {
            logUnhealthyStateOnce();
            lastActiveNanos = 0L;
            return;
        }
        stateHealthWarningSent = false;

        final DevItemStateData<ItemStack> snapshot = currentState();
        if (!isEnabled() || !snapshot.issued()) {
            lastActiveNanos = 0L;
            return;
        }
        final Player owner = Bukkit.getPlayer(snapshot.owner());
        if (owner == null) {
            lastActiveNanos = 0L;
            return;
        }
        if (!ownerTickGate.tryEnter()) {
            return;
        }

        try {
            final ScheduledTask scheduled = owner.getScheduler().run(plugin, task -> {
                try {
                    if (!shuttingDown) {
                        tickOwner(owner, snapshot.owner());
                    }
                } finally {
                    ownerTickGate.exit();
                }
            }, () -> {
                ownerTickGate.exit();
                lastActiveNanos = 0L;
            });
            if (scheduled == null) {
                ownerTickGate.exit();
                lastActiveNanos = 0L;
            }
        } catch (final RuntimeException | Error schedulingFailure) {
            ownerTickGate.exit();
            lastActiveNanos = 0L;
            throw schedulingFailure;
        }
    }

    private void tickOwner(final Player owner, final UUID expectedOwner) {
        if (!owner.isOnline() || !isCurrentOwner(expectedOwner, owner.getUniqueId())) {
            lastActiveNanos = 0L;
            return;
        }
        final DevItemStateData<ItemStack> before = currentState();
        if (!before.issued() || !ensureAuthoritativeItem(owner, autoRestoreEnabled(), false)) {
            lastActiveNanos = 0L;
            return;
        }

        final long now = System.nanoTime();
        final long previous = lastActiveNanos;
        lastActiveNanos = now;
        if (previous <= 0L || now <= previous) {
            return;
        }
        final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(now - previous);
        if (elapsedMillis <= 0L) {
            return;
        }

        final long intervalMillis = Math.max(1L,
                configManager.getLong(BASE + ".reward-interval-seconds", 600L)) * 1000L;
        final DevItemStateData<ItemStack> progressed;
        synchronized (stateLock) {
            if (!state.ownerIs(expectedOwner)) {
                return;
            }
            progressed = state.advanceProgress(elapsedMillis, intervalMillis, ItemStack::clone);
            state = progressed;
        }
        if (progressed.progressMillis() < intervalMillis) {
            return;
        }

        DevItemStateData.PendingReward<ItemStack> pending = progressed.pending();
        if (pending == null) {
            final RewardSelection selection = rollPendingReward();
            if (selection == null) {
                if (!rewardConfigWarningSent) {
                    rewardConfigWarningSent = true;
                    plugin.getLogger().warning(
                            "Csodálatos Bingulus: nincs kisorsolható, érvényes jutalom a konfigurációban.");
                }
                return;
            }
            rewardConfigWarningSent = false;
            final ItemStack rolled = resolveReward(owner, selection.entry());
            if (!isValidItem(rolled)) {
                plugin.getLogger().warning("Csodálatos Bingulus: nem építhető jutalom: " + selection.entry());
                return;
            }
            final DevItemStateData.PendingReward<ItemStack> proposed =
                    DevItemStateData.PendingReward.of(selection.rarity(), selection.entry(), rolled,
                            ItemStack::clone, this::isValidItem, rarity -> rankOf(rarity) >= 0);
            pending = preparePendingRewardDurably(expectedOwner, proposed);
            if (pending == null) {
                return;
            }
        }

        if (!isCurrentOwner(expectedOwner, owner.getUniqueId())) {
            return;
        }
        final ItemStack reward = pending.itemCopy(ItemStack::clone);
        if (!canFit(owner.getInventory(), reward)) {
            notifyRewardInventoryFull(owner);
            return;
        }

        final ItemStack[] inventoryBefore = cloneStorageContents(owner.getInventory());
        final Map<Integer, ItemStack> leftovers = owner.getInventory().addItem(reward.clone());
        if (!leftovers.isEmpty()) {
            owner.getInventory().setStorageContents(inventoryBefore);
            notifyRewardInventoryFull(owner);
            return;
        }

        // Owner changes between inventory mutation and completion are intentionally handled by
        // stopping the old tick. The pending state remains for the new owner; no transaction layer.
        if (!isCurrentOwner(expectedOwner, owner.getUniqueId())) {
            return;
        }
        if (!completePendingRewardDurably(expectedOwner, owner.getUniqueId(), pending)) {
            return;
        }
        rewardInventoryNoticeSent = false;
        announce(owner, pending.rarity(), reward);
    }

    private void notifyRewardInventoryFull(final Player owner) {
        if (!rewardInventoryNoticeSent) {
            rewardInventoryNoticeSent = true;
            owner.sendMessage(messageManager.get("dev-item.inventory-full",
                    "&eA Csodálatos Bingulus jutalma várakozik. Szabadíts fel egy helyet az inventorydban!"));
        }
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
        DevItemStateData<ItemStack> current = currentState();
        if (!current.ownerIs(player.getUniqueId())) {
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
            if (!found && isAuthoritative(item, current)) {
                authoritativeCopySeen = true;
                found = true;
                if (refreshVisuals) {
                    player.getInventory().setItem(slot,
                            itemFactory.createBingulus(current.owner(), current.instanceId()));
                } else if (item.getAmount() != 1) {
                    item.setAmount(1);
                }
            } else {
                player.getInventory().setItem(slot, null);
            }
        }

        final ItemStack cursor = player.getItemOnCursor();
        if (itemFactory.isBingulus(cursor)) {
            if (!found && isAuthoritative(cursor, current)) {
                authoritativeCopySeen = true;
                found = true;
                if (refreshVisuals) {
                    player.setItemOnCursor(itemFactory.createBingulus(current.owner(), current.instanceId()));
                } else if (cursor.getAmount() != 1) {
                    cursor.setAmount(1);
                }
            } else {
                player.setItemOnCursor(new ItemStack(Material.AIR));
            }
        }

        for (int slot = 0; slot < player.getEnderChest().getSize(); slot++) {
            final ItemStack item = player.getEnderChest().getItem(slot);
            if (!itemFactory.isBingulus(item)) {
                continue;
            }
            if (isAuthoritative(item, current)) {
                authoritativeCopySeen = true;
            }
            player.getEnderChest().setItem(slot, null);
        }

        if (!hasHealthyState()) {
            logUnhealthyStateOnce();
            return false;
        }
        stateHealthWarningSent = false;

        if (authoritativeCopySeen && !current.issued()) {
            markIssuedDurably(current.owner());
            current = currentState();
        }
        if (found) {
            restoreInventoryNoticeSent = false;
            return true;
        }
        if (!allowRestore || !current.issued()
                || !isCurrentOwner(current.owner(), player.getUniqueId())) {
            return false;
        }

        final int empty = player.getInventory().firstEmpty();
        if (empty < 0) {
            if (!restoreInventoryNoticeSent) {
                restoreInventoryNoticeSent = true;
                player.sendMessage(messageManager.get("dev-item.restore-full",
                        "&eA Csodálatos Bingulus visszatérne hozzád, de nincs szabad inventoryhelyed."));
            }
            return false;
        }
        player.getInventory().setItem(empty,
                itemFactory.createBingulus(current.owner(), current.instanceId()));
        restoreInventoryNoticeSent = false;
        player.sendMessage(messageManager.get("dev-item.restored",
                "&d✦ A Csodálatos Bingulus visszatért hozzád."));
        return true;
    }

    private boolean isAuthoritative(final ItemStack item, final DevItemStateData<ItemStack> current) {
        return itemFactory.isBingulus(item)
                && current.owner().equals(itemFactory.ownerOf(item))
                && current.instanceId().equals(itemFactory.instanceOf(item));
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

    private DevItemStateData.PendingReward<ItemStack> preparePendingRewardDurably(
            final UUID expectedOwner,
            final DevItemStateData.PendingReward<ItemStack> proposed) {
        synchronized (stateLock) {
            requireHealthyState();
            if (!state.ownerIs(expectedOwner) || state.pending() != null) {
                return null;
            }
            final DevItemStateData<ItemStack> candidate = state.withPending(proposed, ItemStack::clone);
            writeSnapshot(snapshotYaml(candidate));
            state = candidate;
            return candidate.pending().copy(ItemStack::clone);
        }
    }

    private boolean completePendingRewardDurably(
            final UUID expectedOwner,
            final UUID actor,
            final DevItemStateData.PendingReward<ItemStack> expectedPending) {
        synchronized (stateLock) {
            requireHealthyState();
            if (!state.ownerIs(expectedOwner) || !expectedOwner.equals(actor)
                    || state.pending() == null
                    || !state.pending().same(expectedPending, this::sameItem)) {
                return false;
            }
            final DevItemStateData.PityCounters updatedPity = pityAfter(
                    expectedPending.rarity(), state.pity());
            final DevItemStateData<ItemStack> candidate = state.completed(updatedPity, ItemStack::clone);
            writeSnapshot(snapshotYaml(candidate));
            state = candidate;
            return true;
        }
    }

    private DevItemStateData<ItemStack> currentState() {
        synchronized (stateLock) {
            return state.copy(ItemStack::clone);
        }
    }

    private boolean isCurrentOwner(final UUID expectedOwner, final UUID actor) {
        synchronized (stateLock) {
            return expectedOwner.equals(actor) && state.ownerIs(expectedOwner);
        }
    }

    private boolean isValidItem(final ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }

    private boolean sameItem(final ItemStack left, final ItemStack right) {
        return left != null && right != null
                && left.getAmount() == right.getAmount()
                && left.isSimilar(right);
    }

    private String forcedMinimumRarity() {
        final DevItemStateData.PityCounters pity = currentState().pity();
        if (pity.sinceLegendary() >= pityThreshold("legendas", 1000)) {
            return "legendas";
        }
        if (pity.sinceEpic() >= pityThreshold("epikus", 150)) {
            return "epikus";
        }
        if (pity.sinceRare() >= pityThreshold("ritka", 30)) {
            return "ritka";
        }
        return "kozonseges";
    }

    private int pityThreshold(final String rarity, final int fallback) {
        return Math.max(1, configManager.getInt(BASE + ".pity." + rarity + ".after-rolls", fallback));
    }

    private RewardSelection rollPendingReward() {
        final String rarity = weightedRarity(forcedMinimumRarity());
        final String entry = rarity == null ? null : weightedEntry(rarity);
        return entry == null ? null : new RewardSelection(rarity, entry);
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

    private DevItemStateData.PityCounters pityAfter(
            final String rarity, final DevItemStateData.PityCounters current) {
        final int rank = rankOf(rarity);
        return new DevItemStateData.PityCounters(
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
        synchronized (stateLock) {
            previous = state.owner();
            if (previous.equals(configured)) {
                return;
            }
            final DevItemStateData<ItemStack> candidate = state.withOwner(configured, ItemStack::clone);
            writeSnapshot(snapshotYaml(candidate));
            state = candidate;
        }
        lastActiveNanos = 0L;
        plugin.getLogger().info("Csodálatos Bingulus: tulajdonos változott (" + previous + " → " + configured
                + "), az instance, az aktívidő, a pity és a pending jutalom megőrizve.");
    }

    private boolean hasHealthyState() {
        return !YamlStore.isLoadFailed(stateFile) && !YamlStore.hasWriteFailure(stateFile);
    }

    private void requireHealthyState() {
        if (!hasHealthyState()) {
            throw new IllegalStateException("A DEV-item állapottár sérült vagy tartós írási hibát jelzett; "
                    + "a DEV jutalmazás restartig vagy kontrollált state reloadig megtagadva.");
        }
    }

    private void logUnhealthyStateOnce() {
        if (!stateHealthWarningSent) {
            stateHealthWarningSent = true;
            plugin.getLogger().severe("A DEV-item állapottár nem egészséges; csak a Bingulus aktívidő- és "
                    + "jutalomrendszere marad fail-closed. Más persistent store-ok ettől nem állnak le.");
        }
    }

    private void validateConfiguration() {
        rewardConfigWarningSent = false;
        int warningCount = 0;
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

    @Override
    public void load() {
        final UUID configuredOwner = configuredOwnerUuid();
        if (!stateFile.exists()) {
            synchronized (stateLock) {
                state = DevItemStateData.fresh(configuredOwner, UUID.randomUUID());
            }
            stateHealthWarningSent = false;
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

            final boolean hasRarity = !savedPendingRarity.isBlank();
            final boolean hasEntry = !savedPendingEntry.isBlank();
            final boolean hasItem = exactPending != null;
            if (hasRarity != hasEntry || hasRarity != hasItem) {
                throw new IllegalArgumentException(
                        "pending rarity, entry and exact item must all be present or all be absent");
            }
            final DevItemStateData.PendingReward<ItemStack> pending = hasItem
                    ? DevItemStateData.PendingReward.of(savedPendingRarity, savedPendingEntry,
                    exactPending, ItemStack::clone, this::isValidItem, rarity -> rankOf(rarity) >= 0)
                    : null;
            final DevItemStateData<ItemStack> loaded = new DevItemStateData<>(
                    savedOwner,
                    savedInstance,
                    savedIssued,
                    savedProgress,
                    pending,
                    new DevItemStateData.PityCounters(
                            requireInt(yaml, "bingulus.pity.since-rare"),
                            requireInt(yaml, "bingulus.pity.since-epic"),
                            requireInt(yaml, "bingulus.pity.since-legendary")));
            synchronized (stateLock) {
                state = loaded.copy(ItemStack::clone);
            }
            rewardInventoryNoticeSent = false;
            restoreInventoryNoticeSent = false;
            lastActiveNanos = 0L;
            stateHealthWarningSent = false;
        } catch (final RuntimeException invalidState) {
            YamlStore.failCorrupt(stateFile, plugin.getLogger(),
                    "A DEV-item állapota szemantikailag érvénytelen: " + invalidState.getMessage());
        }
        validateConfiguration();
    }

    @Override
    public void save() {
        if (!hasHealthyState()) {
            logUnhealthyStateOnce();
            return;
        }
        synchronized (stateLock) {
            writeSnapshot(snapshotYaml(state));
        }
    }

    private YamlConfiguration snapshotYaml(final DevItemStateData<ItemStack> snapshot) {
        final DevItemStateData.PendingReward<ItemStack> pending = snapshot.pending();
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("bingulus.owner", snapshot.owner().toString());
        yaml.set("bingulus.issued", snapshot.issued());
        yaml.set("bingulus.instance", snapshot.instanceId().toString());
        yaml.set("bingulus.progress-millis", snapshot.progressMillis());
        yaml.set("bingulus.pending.rarity", pending == null ? "" : pending.rarity());
        yaml.set("bingulus.pending.entry", pending == null ? "" : pending.entry());
        yaml.set("bingulus.pending.item", pending == null ? null : pending.itemCopy(ItemStack::clone));
        yaml.set("bingulus.pity.since-rare", snapshot.pity().sinceRare());
        yaml.set("bingulus.pity.since-epic", snapshot.pity().sinceEpic());
        yaml.set("bingulus.pity.since-legendary", snapshot.pity().sinceLegendary());
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
