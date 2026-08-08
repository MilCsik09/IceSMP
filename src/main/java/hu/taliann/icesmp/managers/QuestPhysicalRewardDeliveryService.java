package hu.taliann.icesmp.managers;

import hu.taliann.icesmp.data.CurrencyType;
import hu.taliann.icesmp.items.CrateKeyFactory;
import hu.taliann.icesmp.playerprofile.application.PlayerProfileQuestStore;
import hu.taliann.icesmp.playerprofile.application.QuestRewardDeliveryProtocol;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Crash-safe delivery boundary for physical quest rewards. */
public final class QuestPhysicalRewardDeliveryService {

    private static final int MAX_COMPONENTS = 512;
    private static final String RECEIPT_KEY_NAME = "quest_reward_receipt";
    private static final String COMPONENT_KEY_NAME = "quest_reward_component";

    private final JavaPlugin plugin;
    private final PlayerProfileQuestStore questStore;
    private final CurrencyManager currencyManager;
    private final FactionManager factionManager;
    private final NamespacedKey receiptKey;
    private final NamespacedKey componentKey;

    public QuestPhysicalRewardDeliveryService(final JavaPlugin plugin,
                                              final PlayerProfileQuestStore questStore,
                                              final CurrencyManager currencyManager,
                                              final FactionManager factionManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.questStore = Objects.requireNonNull(questStore, "questStore");
        this.currencyManager = Objects.requireNonNull(currencyManager, "currencyManager");
        this.factionManager = Objects.requireNonNull(factionManager, "factionManager");
        this.receiptKey = new NamespacedKey(plugin, RECEIPT_KEY_NAME);
        this.componentKey = new NamespacedKey(plugin, COMPONENT_KEY_NAME);
    }

    /** True while an inventory item is the crash-recovery witness of an unfinalized component. */
    public static boolean isPendingRewardItem(final JavaPlugin plugin, final ItemStack item) {
        if (plugin == null || item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        final PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(new NamespacedKey(plugin, RECEIPT_KEY_NAME), PersistentDataType.STRING)
                && pdc.has(new NamespacedKey(plugin, COMPONENT_KEY_NAME), PersistentDataType.STRING);
    }

    public CompletionStage<Void> deliver(final Player player,
                                         final ConfigurationSection quest,
                                         final String receiptId,
                                         final Supplier<CrateKeyFactory> crateFactorySupplier) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(quest, "quest");
        Objects.requireNonNull(receiptId, "receiptId");
        Objects.requireNonNull(crateFactorySupplier, "crateFactorySupplier");
        final List<Component> components;
        try {
            components = buildComponents(player, quest, crateFactorySupplier);
        } catch (final Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
        if (components.isEmpty()) return CompletableFuture.completedFuture(null);
        if (components.size() > MAX_COMPONENTS) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "quest physical reward component limit exceeded"));
        }
        final Set<String> ids = components.stream().map(Component::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return questStore.prepareRewardComponents(player.getUniqueId(), receiptId, ids)
                .thenCompose(ignored -> onPlayerThread(player, () ->
                        materializeOrAcknowledge(player, receiptId, components)))
                .thenCompose(acknowledged -> questStore.markRewardComponentsDelivered(
                        player.getUniqueId(), receiptId, acknowledged))
                .thenCompose(ignored -> onPlayerThread(player, () -> {
                    clearDeliveryStamps(player, receiptId);
                    return null;
                }));
    }

    private List<Component> buildComponents(final Player player,
                                            final ConfigurationSection quest,
                                            final Supplier<CrateKeyFactory> crateFactorySupplier) {
        final ArrayList<Component> result = new ArrayList<>();
        final ConfigurationSection currency = quest.getConfigurationSection("rewards.currency");
        if (currency != null) {
            final String raw = currency.getString("type", "");
            final CurrencyType type = isOwnFactionCurrency(raw)
                    ? factionManager.getChosenFaction(player.getUniqueId())
                            .map(CurrencyType::fromFactionType).orElse(null)
                    : CurrencyType.fromInput(raw);
            final long total = Math.round(currency.getDouble("amount", 0.0D));
            if (type != null && total > 0L) {
                long left = total;
                int chunk = 0;
                while (left > 0L) {
                    final long batch = Math.min(64L, left);
                    left -= batch;
                    result.add(new Component(
                            "currency:" + type.name().toLowerCase(Locale.ROOT) + ":" + chunk++,
                            currencyManager.createCurrencyItem(type, batch)));
                }
            }
        }
        int rewardIndex = 0;
        for (final String entry : quest.getStringList("rewards.items")) {
            final String[] parts = entry.split(":");
            final Material material = Material.matchMaterial(parts[0].trim());
            if (material == null || material.isAir()) {
                rewardIndex++;
                continue;
            }
            int total = 1;
            if (parts.length >= 2) {
                try { total = Math.max(1, Integer.parseInt(parts[1].trim())); }
                catch (final NumberFormatException ignored) { total = 1; }
            }
            int left = total;
            int chunk = 0;
            final int maxStack = Math.max(1, material.getMaxStackSize());
            while (left > 0) {
                final int batch = Math.min(maxStack, left);
                left -= batch;
                result.add(new Component("item:" + rewardIndex + ":" + chunk++,
                        new ItemStack(material, batch)));
            }
            rewardIndex++;
        }
        final String crateReward = quest.getString("rewards.crate-key");
        if (crateReward != null && !crateReward.isBlank()) {
            final CrateKeyFactory factory = crateFactorySupplier.get();
            if (factory == null) {
                throw new IllegalStateException(
                        "Quest crate reward cannot be delivered: CrateKeyFactory is not bound");
            }
            final String[] parts = crateReward.split(":");
            int total = 1;
            if (parts.length >= 2) {
                try { total = Math.max(1, Integer.parseInt(parts[1].trim())); }
                catch (final NumberFormatException ignored) { total = 1; }
            }
            int left = total;
            int chunk = 0;
            while (left > 0) {
                final int batch = Math.min(64, left);
                left -= batch;
                final ItemStack item = factory.createKey(parts[0].trim(), batch);
                if (item == null || item.getType().isAir()) {
                    throw new IllegalStateException(
                            "Quest crate reward resolves to AIR: " + parts[0].trim());
                }
                result.add(new Component("crate-key:" + chunk++, item));
            }
        }
        return List.copyOf(result);
    }

    private Set<String> materializeOrAcknowledge(final Player player,
                                                 final String receiptId,
                                                 final List<Component> components) {
        final Map<String, PlayerProfileQuestStore.RewardComponentState> states =
                questStore.rewardComponentStates(player.getUniqueId(), receiptId);
        final LinkedHashMap<String, Component> toMint = new LinkedHashMap<>();
        final LinkedHashSet<String> acknowledged = new LinkedHashSet<>();
        for (final Component component : components) {
            final PlayerProfileQuestStore.RewardComponentState state = states.get(component.id());
            if (state == null) {
                throw new IllegalStateException(
                        "physical quest reward component missing durable PREPARED state: " + component.id());
            }
            final boolean witness = hasWitness(player.getInventory(), receiptId, component.id());
            final QuestRewardDeliveryProtocol.Decision decision =
                    QuestRewardDeliveryProtocol.decide(state, witness);
            switch (decision) {
                case SKIP_DELIVERED, ACKNOWLEDGE_WITNESS -> acknowledged.add(component.id());
                case DELIVER -> toMint.put(component.id(), component);
            }
        }
        if (emptyStorageSlots(player.getInventory()) < toMint.size()) {
            throw new IllegalStateException("not enough inventory space for pending quest reward");
        }
        for (final Component component : toMint.values()) {
            final ItemStack stamped = stamp(component.item().clone(), receiptId, component.id());
            final Map<Integer, ItemStack> overflow = player.getInventory().addItem(stamped);
            if (!overflow.isEmpty()) {
                throw new IllegalStateException("quest reward inventory preflight changed before delivery");
            }
            acknowledged.add(component.id());
        }
        return Set.copyOf(acknowledged);
    }

    private ItemStack stamp(final ItemStack item, final String receiptId, final String componentId) {
        if (item == null || item.getType().isAir()) {
            throw new IllegalArgumentException("physical quest reward may not be AIR");
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) throw new IllegalStateException("physical quest reward has no item metadata");
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(receiptKey, PersistentDataType.STRING, receiptId);
        pdc.set(componentKey, PersistentDataType.STRING, componentId);
        item.setItemMeta(meta);
        return item;
    }

    private boolean hasWitness(final PlayerInventory inventory,
                               final String receiptId,
                               final String componentId) {
        for (final ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;
            final PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            if (receiptId.equals(pdc.get(receiptKey, PersistentDataType.STRING))
                    && componentId.equals(pdc.get(componentKey, PersistentDataType.STRING))) return true;
        }
        return false;
    }

    private void clearDeliveryStamps(final Player player, final String receiptId) {
        final ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            final ItemStack item = contents[slot];
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;
            final ItemMeta meta = item.getItemMeta();
            final PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (!receiptId.equals(pdc.get(receiptKey, PersistentDataType.STRING))) continue;
            pdc.remove(receiptKey);
            pdc.remove(componentKey);
            item.setItemMeta(meta);
            contents[slot] = item;
            changed = true;
        }
        if (changed) player.getInventory().setContents(contents);
    }

    private static int emptyStorageSlots(final PlayerInventory inventory) {
        int empty = 0;
        for (final ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir()) empty++;
        }
        return empty;
    }

    private <T> CompletionStage<T> onPlayerThread(final Player player, final Supplier<T> action) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        player.getScheduler().run(plugin, task -> {
            try { result.complete(action.get()); }
            catch (final Throwable failure) { result.completeExceptionally(failure); }
        }, () -> result.completeExceptionally(new IllegalStateException(
                "player scheduler rejected physical quest reward delivery")));
        return result;
    }

    private static boolean isOwnFactionCurrency(final String raw) {
        return "OWN".equalsIgnoreCase(raw) || "FACTION".equalsIgnoreCase(raw)
                || "SAJAT".equalsIgnoreCase(raw) || "SAJÁT".equalsIgnoreCase(raw);
    }

    private record Component(String id, ItemStack item) {
        private Component {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("component id required");
            Objects.requireNonNull(item, "item");
        }
    }
}
