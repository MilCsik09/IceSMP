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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime and persistence for the first owner-bound DEV item, the Csodalatos Bingulus.
 *
 * <p>The reward clock advances only while the configured owner is online and the authoritative
 * item is in their personal inventory. The progress belongs to owner+item, not to an ItemStack,
 * therefore copied stacks never create additional reward clocks.
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

    private final AtomicLong progressMillis = new AtomicLong();
    private final AtomicReference<String> pendingRarity = new AtomicReference<>("");
    private final AtomicReference<String> pendingEntry = new AtomicReference<>("");
    /** Exact rolled item: amount, affixes and crafted stamps remain stable while inventory is full. */
    private final AtomicReference<ItemStack> pendingItem = new AtomicReference<>();
    private final AtomicInteger sinceRare = new AtomicInteger();
    private final AtomicInteger sinceEpic = new AtomicInteger();
    private final AtomicInteger sinceLegendary = new AtomicInteger();
    private final AtomicBoolean inventoryNoticeSent = new AtomicBoolean();
    private final AtomicBoolean ownerTickQueued = new AtomicBoolean();

    private volatile UUID instanceId = UUID.randomUUID();
    private volatile ScheduledTask tickTask;

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
        } catch (final IllegalArgumentException ignored) {
            return DEFAULT_OWNER;
        }
    }

    public boolean isOwner(final Player player) {
        return player != null && ownerUuid().equals(player.getUniqueId());
    }

    public void start() {
        if (tickTask != null) {
            return;
        }
        final long ticks = Math.max(20L, configManager.getLong(BASE + ".check-interval-ticks", 20L));
        tickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> queueOwnerTick(ticks * 50L),
                ticks, ticks);
        refreshOnlineOwner();
    }

    public void shutdown() {
        final ScheduledTask current = tickTask;
        tickTask = null;
        if (current != null) {
            current.cancel();
        }
    }

    public void refreshOnlineOwner() {
        final Player owner = Bukkit.getPlayer(ownerUuid());
        if (owner != null) {
            owner.getScheduler().run(plugin, task -> ensureAuthoritativeItem(owner, true), null);
        }
    }

    public void handleJoin(final Player player) {
        if (isOwner(player)) {
            ensureAuthoritativeItem(player, true);
        } else {
            removeAllBingulusItems(player);
        }
    }

    public void handleRespawn(final Player player) {
        if (isOwner(player)) {
            ensureAuthoritativeItem(player, true);
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
            if (itemFactory.isBingulus(contents[slot])) {
                if (reusableSlot < 0) {
                    reusableSlot = slot;
                }
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
        inventoryNoticeSent.set(false);
        return true;
    }

    private void queueOwnerTick(final long elapsedMillis) {
        if (!configManager.getBoolean(BASE + ".enabled", true)) {
            return;
        }
        final Player owner = Bukkit.getPlayer(ownerUuid());
        if (owner == null || !ownerTickQueued.compareAndSet(false, true)) {
            return;
        }
        owner.getScheduler().run(plugin, task -> {
            try {
                tickOwner(owner, elapsedMillis);
            } finally {
                ownerTickQueued.set(false);
            }
        }, () -> ownerTickQueued.set(false));
    }

    private void tickOwner(final Player owner, final long elapsedMillis) {
        if (!owner.isOnline() || !isOwner(owner)) {
            return;
        }
        if (!ensureAuthoritativeItem(owner, true)) {
            return;
        }

        final long intervalMillis = Math.max(1L,
                configManager.getLong(BASE + ".reward-interval-seconds", 600L)) * 1000L;
        final long progressed = progressMillis.updateAndGet(current ->
                Math.min(intervalMillis, current + Math.max(0L, elapsedMillis)));
        if (progressed < intervalMillis) {
            return;
        }

        PendingReward pending = pendingReward();
        if (pending == null) {
            pending = rollPendingReward();
            if (pending == null) {
                return;
            }
            pendingRarity.set(pending.rarity());
            pendingEntry.set(pending.entry());
        }

        ItemStack reward = pendingItem.get();
        if (reward == null) {
            reward = resolveReward(owner, pending.entry());
            if (reward == null || reward.getType().isAir()) {
                plugin.getLogger().warning("Csodálatos Bingulus: nem építhető jutalom: " + pending.entry());
                pendingRarity.set("");
                pendingEntry.set("");
                pendingItem.set(null);
                progressMillis.set(0L);
                return;
            }
            pendingItem.set(reward.clone());
        } else {
            reward = reward.clone();
        }

        if (!canFit(owner.getInventory(), reward)) {
            if (inventoryNoticeSent.compareAndSet(false, true)) {
                owner.sendMessage(messageManager.get("dev-item.inventory-full",
                        "&eA Csodálatos Bingulus jutalma várakozik. Szabadíts fel egy helyet az inventorydban!"));
            }
            return;
        }

        owner.getInventory().addItem(reward);
        inventoryNoticeSent.set(false);
        progressMillis.addAndGet(-intervalMillis);
        pendingRarity.set("");
        pendingEntry.set("");
        pendingItem.set(null);
        updatePityAfter(pending.rarity());
        announce(owner, pending.rarity(), reward);
    }

    private boolean ensureAuthoritativeItem(final Player player, final boolean restore) {
        if (!isOwner(player)) {
            removeAllBingulusItems(player);
            return false;
        }

        boolean found = false;
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            final ItemStack item = contents[slot];
            if (!itemFactory.isBingulus(item)) {
                continue;
            }
            if (!found && isAuthoritative(item)) {
                if (item.getAmount() != 1) {
                    item.setAmount(1);
                }
                found = true;
            } else {
                player.getInventory().setItem(slot, null);
            }
        }

        final ItemStack cursor = player.getItemOnCursor();
        if (itemFactory.isBingulus(cursor)) {
            if (!found && isAuthoritative(cursor)) {
                if (cursor.getAmount() != 1) {
                    cursor.setAmount(1);
                }
                found = true;
            } else {
                player.setItemOnCursor(new ItemStack(Material.AIR));
            }
        }

        // A DEV item cannot live in an ender chest. Legacy/bugged copies are removed and restored
        // into the personal inventory below.
        for (int slot = 0; slot < player.getEnderChest().getSize(); slot++) {
            if (itemFactory.isBingulus(player.getEnderChest().getItem(slot))) {
                player.getEnderChest().setItem(slot, null);
            }
        }

        if (found || !restore || !configManager.getBoolean(BASE + ".auto-restore", true)) {
            return found;
        }

        final int empty = player.getInventory().firstEmpty();
        if (empty < 0) {
            if (inventoryNoticeSent.compareAndSet(false, true)) {
                player.sendMessage(messageManager.get("dev-item.restore-full",
                        "&eA Csodálatos Bingulus visszatérne hozzád, de nincs szabad inventoryhelyed."));
            }
            return false;
        }
        player.getInventory().setItem(empty, itemFactory.createBingulus(ownerUuid(), instanceId));
        inventoryNoticeSent.set(false);
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
        final String rarity = pendingRarity.get();
        final String entry = pendingEntry.get();
        return rarity.isBlank() || entry.isBlank() ? null : new PendingReward(rarity, entry);
    }

    private PendingReward rollPendingReward() {
        final String minimum = forcedMinimumRarity();
        final String rarity = weightedRarity(minimum);
        if (rarity == null) {
            plugin.getLogger().warning("Csodálatos Bingulus: üres/hibás rarity-weights konfiguráció.");
            return null;
        }
        final String entry = weightedEntry(rarity);
        if (entry == null) {
            plugin.getLogger().warning("Csodálatos Bingulus: üres jutalomtábla: " + rarity);
            return null;
        }
        return new PendingReward(rarity, entry);
    }

    private String forcedMinimumRarity() {
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
            if (rankOf(key) >= minRank) {
                values.add(new WeightedValue(key.toLowerCase(Locale.ROOT), Math.max(0.0D, section.getDouble(key))));
            }
        }
        return pick(values);
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
            if (!entry.isBlank()) {
                values.add(new WeightedValue(entry, Math.max(0.0D, reward.getDouble("weight", 1.0D))));
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

    private boolean canFit(final PlayerInventory inventory, final ItemStack incoming) {
        int remaining = incoming.getAmount();
        for (final ItemStack existing : inventory.getStorageContents()) {
            if (existing == null || existing.getType().isAir()) {
                return true;
            }
            if (existing.isSimilar(incoming)) {
                remaining -= Math.max(0, existing.getMaxStackSize() - existing.getAmount());
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updatePityAfter(final String rarity) {
        final int rank = rankOf(rarity);
        updateCounter(sinceRare, rank >= rankOf("ritka"));
        updateCounter(sinceEpic, rank >= rankOf("epikus"));
        updateCounter(sinceLegendary, rank >= rankOf("legendas"));
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

        final int rank = rankOf(rarity);
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
        final int rank = RARITY_ORDER.indexOf(rarity.toLowerCase(Locale.ROOT));
        return rank < 0 ? 0 : rank;
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

    @Override
    public void load() {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        final String rawInstance = yaml.getString("bingulus.instance", "");
        try {
            instanceId = rawInstance.isBlank() ? UUID.randomUUID() : UUID.fromString(rawInstance);
        } catch (final IllegalArgumentException ignored) {
            instanceId = UUID.randomUUID();
        }
        progressMillis.set(Math.max(0L, yaml.getLong("bingulus.progress-millis", 0L)));
        pendingRarity.set(yaml.getString("bingulus.pending.rarity", ""));
        pendingEntry.set(yaml.getString("bingulus.pending.entry", ""));
        pendingItem.set(yaml.getItemStack("bingulus.pending.item"));
        sinceRare.set(Math.max(0, yaml.getInt("bingulus.pity.since-rare", 0)));
        sinceEpic.set(Math.max(0, yaml.getInt("bingulus.pity.since-epic", 0)));
        sinceLegendary.set(Math.max(0, yaml.getInt("bingulus.pity.since-legendary", 0)));
    }

    @Override
    public void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("bingulus.owner", ownerUuid().toString());
        yaml.set("bingulus.instance", instanceId.toString());
        yaml.set("bingulus.progress-millis", progressMillis.get());
        yaml.set("bingulus.pending.rarity", pendingRarity.get());
        yaml.set("bingulus.pending.entry", pendingEntry.get());
        final ItemStack exactPending = pendingItem.get();
        yaml.set("bingulus.pending.item", exactPending == null ? null : exactPending.clone());
        yaml.set("bingulus.pity.since-rare", sinceRare.get());
        yaml.set("bingulus.pity.since-epic", sinceEpic.get());
        yaml.set("bingulus.pity.since-legendary", sinceLegendary.get());
        try {
            YamlStore.saveAtomic(stateFile, yaml);
        } catch (final IOException exception) {
            plugin.getLogger().severe("A DEV item állapota nem menthető: " + exception.getMessage());
        }
    }
}
