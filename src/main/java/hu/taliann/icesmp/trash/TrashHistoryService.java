package hu.taliann.icesmp.trash;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Opaque per-instance identity, significant-event split and lifecycle transaction boundary. */
public final class TrashHistoryService {

    private final TrashCatalog catalog;
    private final TrashItemFactory itemFactory;
    private final TrashHistoryStore store;
    private final NamespacedKey instanceKey;
    private final NamespacedKey revisionKey;
    private final NamespacedKey originKey;
    private final NamespacedKey repairPendingKey;
    private final NamespacedKey repairBeforeDamageKey;
    private final NamespacedKey repairActorKey;

    public TrashHistoryService(final JavaPlugin plugin, final TrashCatalog catalog,
                               final TrashItemFactory itemFactory,
                               final TrashHistoryStore store) {
        Objects.requireNonNull(plugin, "plugin");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        this.store = Objects.requireNonNull(store, "store");
        this.instanceKey = new NamespacedKey(plugin, "trash_instance");
        this.revisionKey = new NamespacedKey(plugin, "trash_history_revision");
        this.originKey = new NamespacedKey(plugin, "trash_origin");
        this.repairPendingKey = new NamespacedKey(plugin, "trash_repair_pending");
        this.repairBeforeDamageKey = new NamespacedKey(plugin, "trash_repair_before");
        this.repairActorKey = new NamespacedKey(plugin, "trash_repair_actor");
    }

    /** Adds stack-equivalent batch provenance without allocating a per-unit history UUID. */
    public ItemStack markOrigin(final ItemStack item, final TrashLootSource source) {
        Objects.requireNonNull(source, "source");
        if (!itemFactory.isKnownItem(item) || instanceIdOf(item).isPresent()) {
            throw new IllegalArgumentException("csak friss Trash stack kaphat origin markert");
        }
        final ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(originKey, PersistentDataType.STRING, source.name());
        item.setItemMeta(meta);
        itemFactory.refreshPresentation(item);
        return item;
    }

    public Optional<UUID> instanceIdOf(final ItemStack item) {
        if (!itemFactory.isKnownItem(item) || !item.hasItemMeta()) return Optional.empty();
        final PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        final String raw = pdc.get(instanceKey, PersistentDataType.STRING);
        final Long revision = pdc.get(revisionKey, PersistentDataType.LONG);
        if ((raw == null || raw.isBlank()) && revision == null) return Optional.empty();
        if (raw == null || raw.isBlank() || revision == null || revision < 1L) {
            throw new IllegalStateException("hiányos Trash history authority marker");
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalStateException("érvénytelen Trash history instance marker", invalid);
        }
    }

    public boolean isTrash(final ItemStack item) {
        return itemFactory.isKnownItem(item);
    }

    public boolean isValidTracked(final ItemStack item) {
        try {
            final UUID instanceId = instanceIdOf(item).orElse(null);
            if (instanceId == null || item.getAmount() != 1) return false;
            final String baseId = itemFactory.idOf(item).orElse(null);
            final String phase = itemFactory.phaseOf(item).orElse(null);
            final long revision = revisionOf(item);
            return baseId != null && phase != null && revision >= 1L
                    && store.matches(instanceId, baseId, phase, revision);
        } catch (final IllegalStateException malformed) {
            return false;
        }
    }

    public Optional<TrashHistoryStore.Snapshot> historyOf(final ItemStack item) {
        if (!isValidTracked(item)) return Optional.empty();
        return instanceIdOf(item).flatMap(store::find);
    }

    public ItemStack individualizeUnit(final ItemStack rawItem, final TrashHistoryEvent event,
                                       final UUID actor, final String detail) {
        final ItemStack item = Objects.requireNonNull(rawItem, "rawItem");
        validateSingleton(item);
        return mutateItem(item, () -> {
            individualizeInternal(item, event, actor, detail);
            return item;
        });
    }

    public boolean recordIfTracked(final ItemStack item, final TrashHistoryEvent event,
                                   final UUID actor, final String detail) {
        final UUID instanceId = instanceIdOf(item).orElse(null);
        if (instanceId == null) return false;
        mutateItem(item, () -> {
            final String baseId = itemFactory.idOf(item).orElseThrow();
            final String phase = itemFactory.phaseOf(item).orElseThrow();
            requireCurrent(item, instanceId, baseId, phase);
            writeAuthority(item, store.record(instanceId, baseId, phase, event, actor, detail));
            return item;
        });
        return true;
    }

    public boolean observeOwnerIfTracked(final ItemStack item, final UUID owner,
                                         final boolean king) {
        final UUID instanceId = instanceIdOf(item).orElse(null);
        if (instanceId == null) return false;
        mutateItem(item, () -> {
            final String baseId = itemFactory.idOf(item).orElseThrow();
            final String phase = itemFactory.phaseOf(item).orElseThrow();
            requireCurrent(item, instanceId, baseId, phase);
            writeAuthority(item, store.observeOwner(instanceId, baseId, phase, owner, king));
            return item;
        });
        return true;
    }

    public SplitResult splitAndRecord(final ItemStack source, final TrashHistoryEvent event,
                                      final UUID actor, final String detail) {
        validateSplittable(source);
        final ItemStack singleton = source.clone();
        singleton.setAmount(1);
        individualizeUnit(singleton, event, actor, detail);
        return new SplitResult(remainderOf(source), singleton);
    }

    /** First king contact is significant and therefore individualizes exactly one unit. */
    public SplitResult splitForKing(final ItemStack source, final UUID kingId) {
        validateSplittable(source);
        if (instanceIdOf(source).isPresent()) {
            observeOwnerIfTracked(source, kingId, true);
            return new SplitResult(null, source);
        }
        final ItemStack singleton = source.clone();
        singleton.setAmount(1);
        final ItemStack before = singleton.clone();
        store.transact(() -> {
            final TrashHistoryStore.Snapshot created = individualizeInternal(singleton,
                    TrashHistoryEvent.HELD_BY_KING, kingId, "");
            writeAuthority(singleton, store.observeOwner(created.instanceId(), created.baseId(),
                    created.phase(), kingId, true));
            return singleton;
        }, () -> restoreItem(singleton, before));
        return new SplitResult(remainderOf(source), singleton);
    }

    public SplitResult transformOnSuccess(final ItemStack source, final UUID actor) {
        validateSplittable(source);
        final ItemStack singleton = source.clone();
        singleton.setAmount(1);
        final ItemStack before = singleton.clone();
        return store.transact(() -> transformInternal(source, singleton, actor),
                () -> restoreItem(singleton, before));
    }

    /** Commits the player inventory projection inside the same durable history transaction. */
    public boolean transformMainHandOnSuccess(final Player player) {
        Objects.requireNonNull(player, "player");
        final ItemStack source = player.getInventory().getItemInMainHand();
        if (itemFactory.successPhaseOf(source).isEmpty()) return false;
        if (source.getAmount() > 1 && player.getInventory().firstEmpty() < 0) return false;
        final ItemStack[] before = cloneContents(player.getInventory().getContents());
        return store.transact(() -> {
            final ItemStack singleton = source.clone();
            singleton.setAmount(1);
            final SplitResult result = transformInternal(source, singleton, player.getUniqueId());
            player.getInventory().setItemInMainHand(result.singleton());
            if (result.remainder() != null
                    && !player.getInventory().addItem(result.remainder()).isEmpty()) {
                throw new IllegalStateException("a Trash transform remainder nem fér el");
            }
            return true;
        }, () -> player.getInventory().setContents(before));
    }

    /** Commits an arbitrary player-inventory slot projection with durable history rollback. */
    public boolean transformInventorySlotOnSuccess(final Player player, final int slot) {
        Objects.requireNonNull(player, "player");
        if (slot < 0 || slot >= player.getInventory().getSize()) return false;
        final ItemStack source = player.getInventory().getItem(slot);
        if (source == null || itemFactory.successPhaseOf(source).isEmpty()) return false;
        if (source.getAmount() > 1 && player.getInventory().firstEmpty() < 0) return false;
        final ItemStack[] before = cloneContents(player.getInventory().getContents());
        return store.transact(() -> {
            final ItemStack singleton = source.clone();
            singleton.setAmount(1);
            final SplitResult result = transformInternal(source, singleton, player.getUniqueId());
            player.getInventory().setItem(slot, result.singleton());
            if (result.remainder() != null
                    && !player.getInventory().addItem(result.remainder()).isEmpty()) {
                throw new IllegalStateException("a Trash transform remainder nem fér el");
            }
            return true;
        }, () -> player.getInventory().setContents(before));
    }

    /**
     * Splits and individualizes exactly one unit in the selected hand before a deferred effect
     * reserves it. The player inventory projection rolls back with the durable history write.
     */
    public boolean individualizeHandOnSuccess(final Player player, final EquipmentSlot hand,
                                              final TrashHistoryEvent event) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(event, "event");
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return false;
        final ItemStack source = itemInHand(player, hand);
        if (!itemFactory.isKnownItem(source)) return false;
        if (source.getAmount() > 1 && player.getInventory().firstEmpty() < 0) return false;
        final ItemStack[] before = cloneContents(player.getInventory().getContents());
        return store.transact(() -> {
            final ItemStack singleton = source.clone();
            singleton.setAmount(1);
            individualizeInternal(singleton, event, player.getUniqueId(), "");
            setItemInHand(player, hand, singleton);
            final ItemStack remainder = remainderOf(source);
            if (remainder != null && !player.getInventory().addItem(remainder).isEmpty()) {
                throw new IllegalStateException("a Trash reservation remainder nem fér el");
            }
            return true;
        }, () -> player.getInventory().setContents(before));
    }

    /** Transforms the exact helmet slot; an inventory copy cannot impersonate equipped state. */
    public boolean transformHelmetOnSuccess(final Player player) {
        Objects.requireNonNull(player, "player");
        final ItemStack source = player.getInventory().getHelmet();
        if (source == null || itemFactory.successPhaseOf(source).isEmpty()) return false;
        final ItemStack[] before = cloneContents(player.getInventory().getContents());
        return store.transact(() -> {
            final ItemStack singleton = source.clone();
            singleton.setAmount(1);
            final SplitResult result = transformInternal(source, singleton, player.getUniqueId());
            player.getInventory().setHelmet(result.singleton());
            if (result.remainder() != null
                    && !player.getInventory().addItem(result.remainder()).isEmpty()) {
                throw new IllegalStateException("a Trash helmet transform remainder nem fér el");
            }
            return true;
        }, () -> player.getInventory().setContents(before));
    }

    /**
     * Atomically transforms one inventory Relic and restores one already-consumed vanilla input.
     * If either projection cannot fit, both history and the post-consumption inventory roll back.
     */
    public boolean transformInventorySlotAndAddOnSuccess(final Player player, final int slot,
                                                         final ItemStack restoredInput) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(restoredInput, "restoredInput");
        if (slot < 0 || slot >= player.getInventory().getSize()
                || restoredInput.getType().isAir() || restoredInput.getAmount() != 1) return false;
        final ItemStack source = player.getInventory().getItem(slot);
        if (source == null || itemFactory.successPhaseOf(source).isEmpty()) return false;
        if (source.getAmount() > 1 && player.getInventory().firstEmpty() < 0) return false;
        final ItemStack[] before = cloneContents(player.getInventory().getContents());
        return store.transact(() -> {
            final ItemStack singleton = source.clone();
            singleton.setAmount(1);
            final SplitResult result = transformInternal(source, singleton, player.getUniqueId());
            player.getInventory().setItem(slot, result.singleton());
            if (result.remainder() != null
                    && !player.getInventory().addItem(result.remainder()).isEmpty()) {
                throw new IllegalStateException("a Trash transform remainder nem fér el");
            }
            if (!player.getInventory().addItem(restoredInput.clone()).isEmpty()) {
                throw new IllegalStateException("a megőrzött consumable nem fér el");
            }
            return true;
        }, () -> player.getInventory().setContents(before));
    }

    /** Idempotently prepares exact history units after the source item is durably removed. */
    public List<ItemStack> prepareVendorUnits(final UUID operationId,
                                              final ItemStack soldSnapshot, final int amount,
                                              final UUID actor) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(actor, "actor");
        final TrashHistoryStore.VendorReceipt existing =
                store.findVendorReceipt(operationId).orElse(null);
        if (existing != null) return restoreVendorUnits(existing, soldSnapshot, amount, actor);

        validateVendorSale(soldSnapshot, amount);
        final String baseId = itemFactory.idOf(soldSnapshot).orElseThrow();
        final String phase = itemFactory.phaseOf(soldSnapshot).orElseThrow();
        final boolean tracked = instanceIdOf(soldSnapshot).isPresent();
        if (!tracked && catalog.require(baseId).internalKind().isInert()) return List.of();

        final List<ItemStack> units = new ArrayList<>(amount);
        final List<TrashHistoryStore.Snapshot> snapshots = new ArrayList<>(amount);
        return store.transact(() -> {
            for (int index = 0; index < amount; index++) {
                final ItemStack unit = soldSnapshot.clone();
                unit.setAmount(1);
                snapshots.add(individualizeInternal(
                        unit, TrashHistoryEvent.VENDOR_SOLD, actor, ""));
                units.add(unit);
            }
            store.putVendorReceipt(operationId, actor, baseId, phase, snapshots);
            return immutableClones(units);
        }, null);
    }

    public void completeVendorOperation(final UUID operationId) {
        if (store.findVendorReceipt(operationId).isEmpty()) return;
        store.transact(() -> {
            store.removeVendorReceipt(operationId);
            return null;
        }, null);
    }

    /** Side-effect-free vendor preflight used before the daily budget transaction commits. */
    public void validateVendorSale(final ItemStack soldSnapshot, final int amount) {
        if (amount < 1 || soldSnapshot == null || amount > soldSnapshot.getAmount()
                || !itemFactory.isKnownItem(soldSnapshot)) {
            throw new IllegalArgumentException("érvénytelen Trash vendor sale");
        }
        final UUID instanceId = instanceIdOf(soldSnapshot).orElse(null);
        if (instanceId == null) return;
        if (amount != 1 || soldSnapshot.getAmount() != 1) {
            throw new IllegalStateException("egy history-bearing Trash instance csak egyenként adható el");
        }
        requireCurrent(soldSnapshot, instanceId, itemFactory.idOf(soldSnapshot).orElseThrow(),
                itemFactory.phaseOf(soldSnapshot).orElseThrow());
    }

    public ItemStack recordRecycled(final ItemStack item) {
        if (!recordIfTracked(item, TrashHistoryEvent.VENDOR_RECYCLED, null, "")) {
            throw new IllegalStateException("csak tracked Trash instance recycle-olható");
        }
        return item;
    }

    public ItemStack recordRepair(final ItemStack item, final UUID actor) {
        if (!itemFactory.isKnownItem(item)) return item;
        validateSingleton(item);
        return individualizeUnit(item, TrashHistoryEvent.REPAIRED, actor, "");
    }

    /** Marks a prepared repair result so the post-click item can be committed exactly once. */
    public Optional<String> markPreparedRepair(final ItemStack input, final ItemStack result,
                                               final UUID actor) {
        if (!itemFactory.isKnownItem(input) || !itemFactory.isKnownItem(result)
                || actor == null || result.getAmount() != 1
                || !itemFactory.idOf(input).equals(itemFactory.idOf(result))
                || !itemFactory.phaseOf(input).equals(itemFactory.phaseOf(result))
                || !(input.getItemMeta() instanceof Damageable before)
                || before.getDamage() < 1) {
            return Optional.empty();
        }
        final String token = UUID.randomUUID().toString();
        final ItemMeta meta = result.getItemMeta();
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(repairPendingKey, PersistentDataType.STRING, token);
        pdc.set(repairBeforeDamageKey, PersistentDataType.INTEGER, before.getDamage());
        pdc.set(repairActorKey, PersistentDataType.STRING, actor.toString());
        result.setItemMeta(meta);
        itemFactory.refreshPresentation(result);
        return Optional.of(token);
    }

    public Optional<String> preparedRepairToken(final ItemStack item) {
        return preparedRepair(item).map(PreparedRepair::token);
    }

    /** Finalizes only the exact prepared result that survived the vanilla transaction. */
    public boolean completePreparedRepair(final ItemStack item, final String expectedToken) {
        final PreparedRepair repair = preparedRepair(item)
                .filter(value -> value.token().equals(expectedToken)).orElse(null);
        if (repair == null || !(item.getItemMeta() instanceof Damageable after)
                || after.getDamage() >= repair.beforeDamage()) return false;
        final ItemStack before = item.clone();
        store.transact(() -> {
            individualizeInternal(item, TrashHistoryEvent.REPAIRED, repair.actor(), "");
            clearPreparedRepairInternal(item);
            return item;
        }, () -> restoreItem(item, before));
        return true;
    }

    public boolean clearPreparedRepair(final ItemStack item, final String expectedToken) {
        if (preparedRepair(item).filter(value -> value.token().equals(expectedToken)).isEmpty()) {
            return false;
        }
        clearPreparedRepairInternal(item);
        return true;
    }

    public int historyCount() {
        return store.size();
    }

    private TrashHistoryStore.Snapshot individualizeInternal(
            final ItemStack item, final TrashHistoryEvent event,
            final UUID actor, final String detail) {
        validateSingleton(item);
        final String baseId = itemFactory.idOf(item).orElseThrow();
        final String phase = itemFactory.phaseOf(item).orElseThrow();
        final UUID existing = instanceIdOf(item).orElse(null);
        final TrashHistoryStore.Snapshot history;
        if (existing == null) {
            final UUID instanceId = UUID.randomUUID();
            final TrashHistoryEvent creation = creationEventOf(item).orElse(null);
            final TrashHistoryStore.Snapshot created = store.createAndRecord(instanceId, baseId,
                    phase, creation == null ? event : creation, creation == null ? actor : null,
                    creation == null ? detail : "");
            history = creation == null ? created
                    : store.record(instanceId, baseId, phase, event, actor, detail);
        } else {
            requireCurrent(item, existing, baseId, phase);
            history = store.record(existing, baseId, phase, event, actor, detail);
        }
        writeAuthority(item, history);
        return history;
    }

    private SplitResult transformInternal(final ItemStack source, final ItemStack singleton,
                                          final UUID actor) {
        final String targetPhase = itemFactory.successPhaseOf(source).orElseThrow(() ->
                new IllegalArgumentException("a Trash identityhez nincs authored success phase"));
        final TrashHistoryStore.Snapshot activated = individualizeInternal(
                singleton, TrashHistoryEvent.ACTIVATED, actor, "");
        itemFactory.applyPhase(singleton, targetPhase);
        writeAuthority(singleton, store.transform(activated.instanceId(), activated.baseId(),
                activated.phase(), targetPhase, actor));
        return new SplitResult(remainderOf(source), singleton);
    }

    private List<ItemStack> restoreVendorUnits(final TrashHistoryStore.VendorReceipt receipt,
                                               final ItemStack source, final int amount,
                                               final UUID actor) {
        if (source == null || !itemFactory.isKnownItem(source) || amount != receipt.amount()
                || !actor.equals(receipt.actor())
                || !itemFactory.idOf(source).orElse("").equals(receipt.baseId())
                || !itemFactory.phaseOf(source).orElse("").equals(receipt.phase())) {
            throw new IllegalStateException("a Trash vendor receipt paraméterei eltérnek");
        }
        final UUID sourceInstance = instanceIdOf(source).orElse(null);
        if (sourceInstance != null && (receipt.units().size() != 1
                || !receipt.units().getFirst().instanceId().equals(sourceInstance))) {
            throw new IllegalStateException("a tracked Trash vendor receipt identityje eltér");
        }
        final List<ItemStack> units = new ArrayList<>(receipt.units().size());
        for (final TrashHistoryStore.Snapshot snapshot : receipt.units()) {
            final ItemStack unit = source.clone();
            unit.setAmount(1);
            writeAuthority(unit, snapshot);
            units.add(unit);
        }
        return immutableClones(units);
    }

    private Optional<PreparedRepair> preparedRepair(final ItemStack item) {
        if (!itemFactory.isKnownItem(item) || !item.hasItemMeta()) return Optional.empty();
        final PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        final String token = pdc.get(repairPendingKey, PersistentDataType.STRING);
        final Integer beforeDamage = pdc.get(repairBeforeDamageKey, PersistentDataType.INTEGER);
        final String rawActor = pdc.get(repairActorKey, PersistentDataType.STRING);
        if ((token == null || token.isBlank()) && beforeDamage == null
                && (rawActor == null || rawActor.isBlank())) return Optional.empty();
        if (token == null || token.isBlank() || beforeDamage == null || beforeDamage < 1
                || rawActor == null || rawActor.isBlank()) {
            throw new IllegalStateException("hiányos Trash repair transaction marker");
        }
        try {
            UUID.fromString(token);
            return Optional.of(new PreparedRepair(token, beforeDamage, UUID.fromString(rawActor)));
        } catch (final IllegalArgumentException malformed) {
            throw new IllegalStateException("érvénytelen Trash repair transaction marker", malformed);
        }
    }

    private void clearPreparedRepairInternal(final ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(repairPendingKey);
        pdc.remove(repairBeforeDamageKey);
        pdc.remove(repairActorKey);
        item.setItemMeta(meta);
        itemFactory.refreshPresentation(item);
    }

    private void validateSingleton(final ItemStack item) {
        if (!itemFactory.isKnownItem(item) || item.getAmount() != 1) {
            throw new IllegalArgumentException("csak egyetlen ismert Trash unit individualizálható");
        }
    }

    private void validateSplittable(final ItemStack source) {
        Objects.requireNonNull(source, "source");
        if (!itemFactory.isKnownItem(source) || source.getAmount() < 1) {
            throw new IllegalArgumentException("nem osztható Trash stack");
        }
        if (instanceIdOf(source).isPresent() && source.getAmount() != 1) {
            throw new IllegalStateException("history-bearing Trash instance nem lehet többes stack");
        }
    }

    private void requireCurrent(final ItemStack item, final UUID instanceId,
                                final String baseId, final String phase) {
        if (item.getAmount() != 1 || !store.matches(instanceId, baseId, phase, revisionOf(item))) {
            throw new IllegalStateException("stale vagy duplikált Trash history instance");
        }
    }

    private long revisionOf(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1L;
        final Long revision = item.getItemMeta().getPersistentDataContainer().get(revisionKey,
                PersistentDataType.LONG);
        return revision == null ? -1L : revision;
    }

    private Optional<TrashHistoryEvent> creationEventOf(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        final String raw = item.getItemMeta().getPersistentDataContainer().get(originKey,
                PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(switch (TrashLootSource.valueOf(raw)) {
                case FISHING -> TrashHistoryEvent.CREATED_FISHING;
                case MOB -> TrashHistoryEvent.CREATED_MOB_DROP;
                case AMBIENT -> TrashHistoryEvent.CREATED_AMBIENT;
            });
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalStateException("érvénytelen Trash origin marker", invalid);
        }
    }

    private void writeAuthority(final ItemStack item, final TrashHistoryStore.Snapshot history) {
        final ItemMeta meta = item.getItemMeta();
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(instanceKey, PersistentDataType.STRING, history.instanceId().toString());
        pdc.set(revisionKey, PersistentDataType.LONG, history.revision());
        item.setItemMeta(meta);
        itemFactory.refreshPresentation(item);
    }

    private <T> T mutateItem(final ItemStack item, final Supplier<T> mutation) {
        final ItemStack before = item.clone();
        return store.transact(mutation, () -> restoreItem(item, before));
    }

    private void restoreItem(final ItemStack target, final ItemStack before) {
        target.setType(before.getType());
        target.setAmount(before.getAmount());
        target.setItemMeta(before.getItemMeta());
        if (itemFactory.isKnownItem(target)) itemFactory.refreshPresentation(target);
    }

    private static ItemStack remainderOf(final ItemStack source) {
        if (source.getAmount() == 1) return null;
        final ItemStack remainder = source.clone();
        remainder.setAmount(source.getAmount() - 1);
        return remainder;
    }

    private static ItemStack[] cloneContents(final ItemStack[] contents) {
        final ItemStack[] copies = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copies[index] = contents[index] == null ? null : contents[index].clone();
        }
        return copies;
    }

    private static ItemStack itemInHand(final Player player, final EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
    }

    private static void setItemInHand(final Player player, final EquipmentSlot hand,
                                      final ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(item);
        else player.getInventory().setItemInMainHand(item);
    }

    private static List<ItemStack> immutableClones(final List<ItemStack> items) {
        return items.stream().map(ItemStack::clone).toList();
    }

    private record PreparedRepair(String token, int beforeDamage, UUID actor) { }

    public record SplitResult(ItemStack remainder, ItemStack singleton) {
        public SplitResult { singleton = Objects.requireNonNull(singleton, "singleton"); }
    }
}
