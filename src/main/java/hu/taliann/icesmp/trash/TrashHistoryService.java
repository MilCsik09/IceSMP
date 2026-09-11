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
        if (!pdc.has(instanceKey) && !pdc.has(revisionKey)) return Optional.empty();
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

    /** Caller owns the item. A busy store is unavailable; stale markers never become fresh history. */
    public Optional<ItemInspection> tryInspect(final ItemStack item) {
        if (!itemFactory.isKnownItem(item) || item.getAmount() < 1) return Optional.empty();
        final String baseId = itemFactory.idOf(item).orElseThrow();
        final String phase = itemFactory.phaseOf(item).orElseThrow();
        final Optional<UUID> instance = instanceIdOf(item);
        final Optional<TrashHistoryEvent> origin = creationEventOf(item);
        final Optional<PreparedRepair> pending = preparedRepair(item);
        if (pending.isPresent()) return Optional.empty();
        if (instance.isEmpty()) {
            if (!"base".equals(phase)) return Optional.empty();
            return Optional.of(new ItemInspection(catalog.require(baseId), phase, origin, Optional.empty(), Optional.empty()));
        }
        if (item.getAmount() != 1) return Optional.empty();
        return store.tryInspect(instance.get()).flatMap(inspection -> inspection.history()
                .filter(snapshot -> snapshot.baseId().equals(baseId) && snapshot.phase().equals(phase)
                        && snapshot.revision() == revisionOf(item))
                .map(snapshot -> new ItemInspection(catalog.require(baseId), phase, origin, Optional.of(snapshot), inspection.pendingWall())));
    }

    public record ItemInspection(TrashDefinition definition, String phase,
                                 Optional<TrashHistoryEvent> origin,
                                 Optional<TrashHistoryStore.Snapshot> history,
                                 Optional<TrashHistoryStore.WallReceipt> pendingWall) {
        public ItemInspection {
            Objects.requireNonNull(definition); Objects.requireNonNull(phase);
            Objects.requireNonNull(origin); Objects.requireNonNull(history); Objects.requireNonNull(pendingWall);
            if (pendingWall.isPresent()) {
                final var receipt = pendingWall.orElseThrow();
                final var snapshot = history.orElseThrow(() -> new IllegalArgumentException("pending wall lacks native history"));
                if (!snapshot.instanceId().equals(receipt.instanceId()) || snapshot.revision() != receipt.revision()
                        || !definition.id().equals(receipt.baseId()) || !phase.equals(receipt.phase())
                        || !snapshot.baseId().equals(receipt.baseId()) || !snapshot.phase().equals(receipt.phase())) {
                    throw new IllegalArgumentException("pending wall and history are from different native states");
                }
            }
        }
    }

    /** Detached, bounded native plan. Preparation never grants an item or writes an operation receipt. */
    public static final class DeveloperPlan {
        private final TrashHistoryService authority;
        private final UUID operation, actor, instance;
        private final TrashDeveloperReceipt.Kind kind;
        private final int source, destination;
        private final ItemStack[] before;
        private final ItemInspection inspection;
        private final Optional<TrashDeveloperReceipt> reverses;
        private final java.util.concurrent.atomic.AtomicBoolean entered = new java.util.concurrent.atomic.AtomicBoolean();
        private final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        private DeveloperPlan(TrashHistoryService authority, UUID operation, UUID actor, UUID instance,
                TrashDeveloperReceipt.Kind kind, int source, int destination, ItemStack[] before, ItemInspection inspection) {
            this(authority, operation, actor, instance, kind, source, destination, before, inspection, Optional.empty());
        }
        private DeveloperPlan(TrashHistoryService authority, UUID operation, UUID actor, UUID instance,
                TrashDeveloperReceipt.Kind kind, int source, int destination, ItemStack[] before, ItemInspection inspection,
                Optional<TrashDeveloperReceipt> reverses) {
            this.authority = authority; this.operation = operation; this.actor = actor; this.instance = instance;
            this.kind = kind; this.source = source; this.destination = destination;
            this.before = cloneContents(before); this.inspection = inspection;
            this.reverses = reverses;
        }
        public UUID instanceId() { return instance; }
        public UUID operationId() { return operation; }
        public boolean matchesInventory(ItemStack[] current) { return java.util.Arrays.equals(before, current); }
    }

    /** Native player inventory indices only. The caller must capture the complete inventory on its owner. */
    public Optional<DeveloperPlan> tryPrepareDeveloperMutation(final UUID operation, final UUID actor,
            final TrashDeveloperReceipt.Kind kind, final int source, final ItemStack[] inventory) {
        Objects.requireNonNull(operation); Objects.requireNonNull(actor); Objects.requireNonNull(kind);
        if (kind == TrashDeveloperReceipt.Kind.REVERT || !hu.taliann.icesmp.security.HiddenDevAuthority.isDeveloper(actor) || inventory == null
                || inventory.length != 41 || source < 0 || source >= inventory.length) return Optional.empty();
        final ItemStack held = inventory[source];
        if (held == null || held.getAmount() < 1) return Optional.empty();
        final var prototype = hu.taliann.icesmp.itemization.ItemPrototypePolicy.scan(held);
        if (prototype != hu.taliann.icesmp.itemization.ItemPrototypePolicy.Scan.CLEAN
                && !hu.taliann.icesmp.itemization.ItemPrototypePolicy.allowedCustody(held, actor,
                    hu.taliann.icesmp.security.HiddenDevAuthority.PRIMARY_DEVELOPER)) return Optional.empty();
        if (kind == TrashDeveloperReceipt.Kind.SANDBOX_COPY
                && prototype != hu.taliann.icesmp.itemization.ItemPrototypePolicy.Scan.CLEAN) return Optional.empty();
        final var inspected = tryInspect(held);
        if (inspected.isEmpty() || inspected.orElseThrow().pendingWall().isPresent()) return Optional.empty();
        final var item = inspected.orElseThrow();
        if (kind == TrashDeveloperReceipt.Kind.INDIVIDUALIZE && item.history().isPresent()
                || kind == TrashDeveloperReceipt.Kind.TRANSITION_SUCCESS && itemFactory.successPhaseOf(held).isEmpty()
                || kind == TrashDeveloperReceipt.Kind.REPAIR
                    && (!(held.getItemMeta() instanceof Damageable damage) || damage.getDamage() <= 0)) return Optional.empty();
        final UUID instance = kind == TrashDeveloperReceipt.Kind.SANDBOX_COPY ? UUID.randomUUID()
                : item.history().map(TrashHistoryStore.Snapshot::instanceId).orElseGet(UUID::randomUUID);
        final var nativeState = store.tryInspect(instance);
        final var operationState = store.tryInspectDeveloperReceipt(operation);
        if (nativeState.isEmpty() || operationState.isEmpty() || operationState.orElseThrow().isPresent()
                || !nativeState.orElseThrow().history().equals(kind == TrashDeveloperReceipt.Kind.SANDBOX_COPY
                    ? Optional.empty() : item.history())) return Optional.empty();
        int destination = source;
        if (kind == TrashDeveloperReceipt.Kind.SANDBOX_COPY || held.getAmount() > 1) {
            destination = -1;
            for (int slot = 0; slot < 36; slot++) if (slot != source && empty(inventory[slot])) { destination = slot; break; }
            if (destination < 0) return Optional.empty();
        }
        return Optional.of(new DeveloperPlan(this, operation, actor, instance, kind, source, destination, inventory, item));
    }

    /** Reverse only an observed, unchanged native effect; allocated identity and truthful history remain. */
    public Optional<DeveloperPlan> tryPrepareDeveloperReversal(final UUID operation, final UUID actor,
            final TrashDeveloperReceipt original, final ItemStack[] inventory) {
        Objects.requireNonNull(operation); Objects.requireNonNull(actor); Objects.requireNonNull(original);
        if (!hu.taliann.icesmp.security.HiddenDevAuthority.isDeveloper(actor) || !actor.equals(original.actor())
                || !original.projectionObserved() || original.kind() == TrashDeveloperReceipt.Kind.INDIVIDUALIZE
                || original.kind() == TrashDeveloperReceipt.Kind.REVERT || inventory == null || inventory.length != 41
                || !matchesProjection(original, inventory, true)) return Optional.empty();
        final var stored = store.tryInspectDeveloperReceipt(original.operationId());
        final var operationState = store.tryInspectDeveloperReceipt(operation);
        final var nativeState = store.tryInspect(original.instanceId());
        if (stored.isEmpty() || !stored.orElseThrow().filter(original::equals).isPresent()
                || operationState.isEmpty() || operationState.orElseThrow().isPresent() || nativeState.isEmpty()
                || nativeState.orElseThrow().history().filter(value -> value.revision() == original.afterRevision()
                    && value.baseId().equals(original.baseId()) && value.phase().equals(original.afterPhase())).isEmpty()) return Optional.empty();
        final int source = original.slots().getFirst().slot();
        final var inspection = tryInspect(inventory[source]);
        if (inspection.isEmpty() || inspection.orElseThrow().pendingWall().isPresent()) return Optional.empty();
        return Optional.of(new DeveloperPlan(this, operation, actor, original.instanceId(), TrashDeveloperReceipt.Kind.REVERT,
                source, original.slots().getLast().slot(), inventory, inspection.orElseThrow(), Optional.of(original)));
    }

    /** Actual owner admission and the one-use influence/authority permit precede the existing native WAL boundary. */
    public Optional<TrashDeveloperReceipt> tryCommitDeveloperMutation(final DeveloperPlan plan,
            final java.util.function.BooleanSupplier admission, final java.util.function.BooleanSupplier finalAdmission,
            final java.util.function.Consumer<ItemStack[]> projection, final Runnable restoreProjection) {
        Objects.requireNonNull(plan); Objects.requireNonNull(admission); Objects.requireNonNull(finalAdmission);
        Objects.requireNonNull(projection); Objects.requireNonNull(restoreProjection);
        if (plan.authority != this) return Optional.empty();
        final var receipt = new java.util.concurrent.atomic.AtomicReference<TrashDeveloperReceipt>();
        final boolean committed = store.tryTransact(() -> !plan.entered.get() && System.nanoTime() - plan.deadline < 0
                && developerHistoryMatches(plan) && admission.getAsBoolean(),
                () -> plan.entered.compareAndSet(false, true) && finalAdmission.getAsBoolean(), () -> {
            if (plan.reverses.isPresent()) {
                commitDeveloperReversal(plan, receipt, projection);
                return;
            }
            final ItemStack[] after = cloneContents(plan.before);
            final ItemStack singleton = after[plan.source].clone(); singleton.setAmount(1);
            final String base = plan.inspection.definition().id(), phase = plan.inspection.phase();
            final long beforeRevision = plan.kind == TrashDeveloperReceipt.Kind.SANDBOX_COPY ? 0
                    : plan.inspection.history().map(TrashHistoryStore.Snapshot::revision).orElse(0L);
            final TrashHistoryStore.Snapshot result;
            if (plan.kind == TrashDeveloperReceipt.Kind.SANDBOX_COPY) {
                final var meta = singleton.getItemMeta(); final var pdc = meta.getPersistentDataContainer();
                pdc.remove(instanceKey); pdc.remove(revisionKey); pdc.remove(originKey);
                pdc.set(originKey, PersistentDataType.STRING, "DEV_PROTOTYPE");
                singleton.setItemMeta(meta);
                result = store.createAndRecord(plan.instance, base, phase, plan.kind.event(), plan.actor, plan.operation.toString());
            } else if (plan.kind == TrashDeveloperReceipt.Kind.TRANSITION_SUCCESS) {
                if (beforeRevision == 0) {
                    store.createAndRecord(plan.instance, base, phase,
                            plan.inspection.origin().orElse(TrashHistoryEvent.DEV_INDIVIDUALIZED),
                            plan.inspection.origin().isPresent() ? null : plan.actor,
                            plan.inspection.origin().isPresent() ? "" : plan.operation.toString());
                }
                final String target = plan.inspection.definition().successPhase();
                result = store.transitionDeveloper(plan.instance, base, phase, target, plan.actor, plan.operation);
                itemFactory.applyPhase(singleton, target);
            } else {
                result = individualizeInternal(singleton, plan.kind.event(), plan.actor, plan.operation.toString(), plan.instance);
                if (plan.kind == TrashDeveloperReceipt.Kind.REPAIR) {
                    final Damageable meta = (Damageable) singleton.getItemMeta(); meta.setDamage(0); singleton.setItemMeta(meta);
                }
            }
            writeAuthority(singleton, result);
            if (plan.kind == TrashDeveloperReceipt.Kind.SANDBOX_COPY) {
                hu.taliann.icesmp.itemization.ItemPrototypePolicy.mark(singleton,
                        new hu.taliann.icesmp.itemization.ItemPrototypePolicy.Identity(plan.actor, plan.operation));
            }
            if (plan.destination == plan.source) after[plan.source] = singleton;
            else if (plan.kind == TrashDeveloperReceipt.Kind.SANDBOX_COPY) after[plan.destination] = singleton;
            else { after[plan.source] = singleton; after[plan.destination] = remainderOf(plan.before[plan.source]); }
            final List<TrashDeveloperReceipt.SlotChange> changes = new ArrayList<>();
            changes.add(new TrashDeveloperReceipt.SlotChange(plan.source, encodeSlot(plan.before[plan.source]), encodeSlot(after[plan.source])));
            if (plan.destination != plan.source) changes.add(new TrashDeveloperReceipt.SlotChange(plan.destination,
                    encodeSlot(plan.before[plan.destination]), encodeSlot(after[plan.destination])));
            final var acknowledged = new TrashDeveloperReceipt(plan.operation, plan.actor, plan.kind, plan.instance, base,
                    phase, beforeRevision, result.phase(), result.revision(), result.updatedAt(), changes, false);
            store.putDeveloperReceipt(acknowledged);
            projection.accept(cloneContents(after));
            receipt.set(acknowledged);
        }, restoreProjection);
        return committed ? Optional.ofNullable(receipt.get()) : Optional.empty();
    }

    private void commitDeveloperReversal(final DeveloperPlan plan,
            final java.util.concurrent.atomic.AtomicReference<TrashDeveloperReceipt> receipt,
            final java.util.function.Consumer<ItemStack[]> projection) {
        final var original = plan.reverses.orElseThrow();
        final var result = store.revertDeveloper(original, plan.operation);
        final ItemStack[] after = cloneContents(plan.before);
        if (original.kind() == TrashDeveloperReceipt.Kind.SANDBOX_COPY) {
            for (final var slot : original.slots()) after[slot.slot()] = decodeSlot(slot.before());
        } else {
            final ItemStack restored = decodeSlot(original.slots().getFirst().before());
            if (restored == null) throw new IllegalStateException("Native reversal has no original unit");
            restored.setAmount(1);
            // Keep any tracked or newly allocated UUID. Recombining an untracked batch would erase monotonic provenance.
            writeAuthority(restored, result);
            after[plan.source] = restored;
        }
        final List<TrashDeveloperReceipt.SlotChange> changes = new ArrayList<>();
        for (final var slot : original.slots()) changes.add(new TrashDeveloperReceipt.SlotChange(slot.slot(),
                encodeSlot(plan.before[slot.slot()]), encodeSlot(after[slot.slot()])));
        final var inverse = new TrashDeveloperReceipt(plan.operation, plan.actor, TrashDeveloperReceipt.Kind.REVERT,
                plan.instance, original.baseId(), original.afterPhase(), original.afterRevision(), result.phase(), result.revision(),
                result.updatedAt(), changes, false, Optional.of(original.operationId()));
        store.putDeveloperReceipt(inverse);
        projection.accept(cloneContents(after));
        receipt.set(inverse);
    }

    private boolean developerHistoryMatches(final DeveloperPlan plan) {
        if (!store.developerMutationAvailable(plan.operation, plan.instance)) return false;
        if (plan.reverses.isPresent()) return store.developerReversalAvailable(plan.reverses.orElseThrow());
        final var expected = plan.inspection.history();
        if (expected.isPresent()) {
            final var before = expected.orElseThrow();
            if (!store.matches(before.instanceId(), before.baseId(), before.phase(), before.revision())) return false;
        }
        return plan.kind != TrashDeveloperReceipt.Kind.SANDBOX_COPY && expected.isPresent() || store.find(plan.instance).isEmpty();
    }

    public Optional<Optional<TrashDeveloperReceipt>> tryInspectDeveloperReceipt(final UUID operation) {
        return store.tryInspectDeveloperReceipt(operation);
    }

    /** Pure owner-local assessment. This neither confirms nor restores the native projection. */
    public Optional<Boolean> tryObserveDeveloperProjection(final TrashDeveloperReceipt receipt, final ItemStack[] inventory) {
        return store.tryObserveDeveloperProjection(receipt, () -> matchesProjection(receipt, inventory, true));
    }

    public Optional<Boolean> tryObserveDeveloperBeforeProjection(final TrashDeveloperReceipt receipt, final ItemStack[] inventory) {
        return store.tryObserveDeveloperProjection(receipt, () -> !receipt.projectionObserved() && matchesProjection(receipt, inventory, false));
    }

    public Optional<List<TrashDeveloperReceipt>> tryInspectPendingDeveloperProjections(final UUID actor) {
        return store.tryInspectDeveloperReceipts(actor).map(receipts -> receipts.stream()
                .filter(receipt -> !receipt.projectionObserved()).sorted(java.util.Comparator.comparingLong(TrashDeveloperReceipt::recordedAt)
                    .thenComparing(TrashDeveloperReceipt::operationId)).limit(16).toList());
    }

    /** Native observation is separate from the write acknowledgement and carries no item recreation authority. */
    public boolean tryConfirmDeveloperProjection(final TrashDeveloperReceipt receipt,
            final java.util.function.Supplier<ItemStack[]> ownerInventory) {
        Objects.requireNonNull(ownerInventory);
        return store.tryConfirmDeveloperProjection(receipt, () -> matchesProjection(receipt, ownerInventory.get(), true));
    }

    /** Exact pending before-state recovery only; never overwrites a conflict or reissues an observed completion. */
    public boolean tryRestoreDeveloperProjection(final TrashDeveloperReceipt receipt,
            final java.util.function.Supplier<ItemStack[]> ownerInventory,
            final java.util.function.Consumer<ItemStack[]> projection, final Runnable restoreProjection) {
        Objects.requireNonNull(ownerInventory); Objects.requireNonNull(projection); Objects.requireNonNull(restoreProjection);
        return store.tryRestoreDeveloperProjection(receipt, () -> matchesProjection(receipt, ownerInventory.get(), false), () -> {
            final ItemStack[] restored = cloneContents(ownerInventory.get());
            for (final var slot : receipt.slots()) restored[slot.slot()] = decodeSlot(slot.after());
            projection.accept(restored);
        }, restoreProjection);
    }

    private boolean matchesProjection(final TrashDeveloperReceipt receipt, final ItemStack[] inventory, final boolean after) {
        if (inventory == null || inventory.length != 41) return false;
        for (final var slot : receipt.slots()) {
            // Paper may reorder native NBT across deserialize/serialize. Compare the complete decoded item,
            // including amount and all metadata, rather than treating byte ordering as an authority revision.
            final ItemStack expected = decodeSlot(after ? slot.after() : slot.before());
            if (expected == null ? !empty(inventory[slot.slot()]) : !expected.equals(inventory[slot.slot()])) return false;
        }
        for (int slot = 0; slot < inventory.length; slot++) {
            final int index = slot;
            if (receipt.slots().stream().anyMatch(change -> change.slot() == index)) continue;
            if (instanceIdOf(inventory[slot]).filter(receipt.instanceId()::equals).isPresent()) return false;
        }
        return true;
    }
    private static boolean empty(ItemStack item) { return item == null || item.getType().isAir(); }
    private static String encodeSlot(ItemStack item) {
        return empty(item) ? "" : java.util.Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }
    private static ItemStack decodeSlot(String encoded) {
        return encoded.isEmpty() ? null : ItemStack.deserializeBytes(java.util.Base64.getDecoder().decode(encoded));
    }

    /** Detached native target identity; preparation owns no history entry or second operation ledger. */
    public static final class UnitPlan {
        private final TrashHistoryService authority;
        private final ItemStack before;
        private final UUID instanceId;
        private final UUID actor;
        private final TrashHistoryEvent event;
        private final java.util.concurrent.atomic.AtomicBoolean entered = new java.util.concurrent.atomic.AtomicBoolean();
        private final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        private UnitPlan(TrashHistoryService authority, ItemStack before, UUID instanceId,
                         UUID actor, TrashHistoryEvent event) {
            this.authority = authority; this.before = before.clone(); this.instanceId = instanceId;
            this.actor = actor; this.event = event;
        }
        public UUID instanceId() { return instanceId; }
    }

    /** Caller owns the singleton; no physical marker, history event or WAL frame is written here. */
    public Optional<UnitPlan> tryPrepareUnit(final ItemStack singleton, final TrashHistoryEvent event,
                                             final UUID actor) {
        Objects.requireNonNull(event); Objects.requireNonNull(actor);
        if (singleton == null || singleton.getAmount() != 1) return Optional.empty();
        final var inspection = tryInspect(singleton);
        if (inspection.isEmpty() || inspection.orElseThrow().pendingWall().isPresent()) return Optional.empty();
        final UUID instance = instanceIdOf(singleton).orElseGet(UUID::randomUUID);
        final var nativeState = store.tryInspect(instance);
        if (nativeState.isEmpty()) return Optional.empty();
        final var expected = inspection.orElseThrow().history();
        if (!nativeState.orElseThrow().history().equals(expected)) return Optional.empty();
        return Optional.of(new UnitPlan(this, singleton, instance, actor, event));
    }

    /** Both predicates and projection execute on the current item owner; the final permit is never retried. */
    public boolean tryIndividualizePlannedUnit(final UnitPlan plan, final ItemStack current,
            final java.util.function.BooleanSupplier admission,
            final java.util.function.BooleanSupplier finalAdmission,
            final java.util.function.Consumer<ItemStack> projection, final Runnable restoreProjection) {
        return tryCommitPlannedUnit(plan, current, admission, finalAdmission, projection, restoreProjection, false);
    }

    /** Native success transition keeps the prepared UUID and the original origin in one WAL frame. */
    public boolean tryTransformPlannedUnit(final UnitPlan plan, final ItemStack current,
            final java.util.function.BooleanSupplier admission,
            final java.util.function.BooleanSupplier finalAdmission,
            final java.util.function.Consumer<ItemStack> projection, final Runnable restoreProjection) {
        Objects.requireNonNull(plan);
        if (plan.event != TrashHistoryEvent.ACTIVATED || itemFactory.successPhaseOf(plan.before).isEmpty()) return false;
        return tryCommitPlannedUnit(plan, current, admission, finalAdmission, projection, restoreProjection, true);
    }

    private boolean tryCommitPlannedUnit(final UnitPlan plan, final ItemStack current,
            final java.util.function.BooleanSupplier admission,
            final java.util.function.BooleanSupplier finalAdmission,
            final java.util.function.Consumer<ItemStack> projection, final Runnable restoreProjection,
            final boolean transition) {
        Objects.requireNonNull(plan); Objects.requireNonNull(admission); Objects.requireNonNull(finalAdmission);
        Objects.requireNonNull(projection); Objects.requireNonNull(restoreProjection);
        if (plan.authority != this) return false;
        return store.tryTransact(() -> !plan.entered.get() && System.nanoTime() - plan.deadline < 0
                        && plan.before.equals(current) && plannedHistoryMatches(plan) && admission.getAsBoolean(),
                () -> plan.entered.compareAndSet(false, true) && finalAdmission.getAsBoolean(), () -> {
                    final ItemStack singleton = plan.before.clone();
                    final var activated = individualizeInternal(singleton, plan.event, plan.actor, "", plan.instanceId);
                    if (transition) {
                        final String phase = itemFactory.successPhaseOf(plan.before).orElseThrow();
                        itemFactory.applyPhase(singleton, phase);
                        writeAuthority(singleton, store.transform(activated.instanceId(), activated.baseId(),
                                activated.phase(), phase, plan.actor));
                    }
                    projection.accept(singleton);
                }, restoreProjection);
    }

    private boolean plannedHistoryMatches(final UnitPlan plan) {
        final var existing = instanceIdOf(plan.before);
        return existing.isEmpty() ? store.find(plan.instanceId).isEmpty()
                : store.matches(plan.instanceId, itemFactory.idOf(plan.before).orElseThrow(),
                        itemFactory.phaseOf(plan.before).orElseThrow(), revisionOf(plan.before));
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
        return transformInventorySlotOnSuccess(player, slot, null, null);
    }

    /** A persisted singleton fences old player snapshots before any consuming consequence. */
    public boolean consumeInventorySlotDurably(final Player player, final int slot) {
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(player) || !player.isOnline()) return false;
        final ItemStack source = player.getInventory().getItem(slot);
        if (source == null || itemFactory.successPhaseOf(source).isEmpty()
                || source.getAmount() > 1 && player.getInventory().firstEmpty() < 0) return false;
        if (instanceIdOf(source).isEmpty()) {
            final SplitResult split = splitAndRecord(source, TrashHistoryEvent.ACTIVATED, player.getUniqueId(), "");
            player.getInventory().setItem(slot, split.singleton());
            if (split.remainder() != null && !player.getInventory().addItem(split.remainder()).isEmpty())
                throw new IllegalStateException("a lefoglalt Trash maradéka nem fér el");
        }
        player.saveData();
        if (!transformInventorySlotOnSuccess(player, slot)) return false;
        player.saveData();
        return true;
    }

    /** Caller owns the inventory; a busy history writer refuses before any projection or waiting. */
    public boolean tryTransformInventorySlotOnSuccess(final Player player, final int slot,
                                                       final java.util.function.BooleanSupplier admission) {
        return transformInventorySlotOnSuccess(player, slot, Objects.requireNonNull(admission, "admission"), null);
    }

    private boolean transformInventorySlotOnSuccess(final Player player, final int slot,
                                                     final java.util.function.BooleanSupplier admission,
                                                     final java.util.function.Consumer<ItemStack> afterProjection) {
        return transformInventorySlotOnSuccess(player, slot, admission, () -> true, afterProjection);
    }

    private boolean transformInventorySlotOnSuccess(final Player player, final int slot,
                                                     final java.util.function.BooleanSupplier admission,
                                                     final java.util.function.BooleanSupplier finalAdmission,
                                                     final java.util.function.Consumer<ItemStack> afterProjection) {
        Objects.requireNonNull(player, "player");
        if (slot < 0 || slot >= player.getInventory().getSize()) return false;
        final ItemStack source = player.getInventory().getItem(slot);
        if (source == null || itemFactory.successPhaseOf(source).isEmpty()) return false;
        if (source.getAmount() > 1 && player.getInventory().firstEmpty() < 0) return false;
        final ItemStack captured = source.clone();
        final ItemStack[] before = cloneContents(player.getInventory().getContents());
        final Runnable mutation = () -> {
            final ItemStack singleton = source.clone();
            singleton.setAmount(1);
            final SplitResult result = transformInternal(source, singleton, player.getUniqueId());
            player.getInventory().setItem(slot, result.singleton());
            if (result.remainder() != null
                    && !player.getInventory().addItem(result.remainder()).isEmpty()) {
                throw new IllegalStateException("a Trash transform remainder nem fér el");
            }
            if (afterProjection != null) afterProjection.accept(result.singleton());
        };
        final Runnable restore = () -> player.getInventory().setContents(before);
        return admission == null ? store.transact(() -> { mutation.run(); return true; }, restore)
                : store.tryTransact(() -> admission.getAsBoolean()
                        && captured.equals(player.getInventory().getItem(slot)), finalAdmission, mutation, restore);
    }

    /** Native wall consumption and its unresolved effect receipt share one fsynced history frame. */
    public Optional<TrashHistoryStore.WallReceipt> tryConsumeProjectileWall(
            final Player player, final int slot, final TrashRuleFieldService.RuleField field,
            final UUID projectileId,
            final java.util.function.BooleanSupplier admission,
            final java.util.function.BooleanSupplier finalAdmission) {
        Objects.requireNonNull(admission, "admission");
        Objects.requireNonNull(finalAdmission, "finalAdmission");
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(player) || !player.getUniqueId().equals(field.owner())) return Optional.empty();
        if (slot < 0 || slot >= player.getInventory().getSize()) return Optional.empty();
        final ItemStack before = player.getInventory().getItem(slot);
        if (before == null || before.getAmount() != 1 || instanceIdOf(before).isEmpty()) return Optional.empty();
        final long beforeRevision = revisionOf(before);
        final var receipt = new java.util.concurrent.atomic.AtomicReference<TrashHistoryStore.WallReceipt>();
        final boolean consumed = transformInventorySlotOnSuccess(player, slot, admission, finalAdmission, singleton -> {
            final var recorded = new TrashHistoryStore.WallReceipt(field.id(), player.getUniqueId(),
                    field.center().world(), projectileId, instanceIdOf(singleton).orElseThrow(), revisionOf(singleton),
                    itemFactory.idOf(singleton).orElseThrow(), itemFactory.phaseOf(singleton).orElseThrow(),
                    System.currentTimeMillis(), field, beforeRevision);
            store.putWallReceipt(recorded);
            receipt.set(recorded);
        });
        return consumed ? Optional.of(receipt.get()) : Optional.empty();
    }

    public boolean tryConfirmProjectileWallRemoval(final TrashHistoryStore.WallReceipt receipt,
                                                    final java.util.function.BooleanSupplier observedRemoved) {
        return store.tryInspectObservedWallRemoval(receipt).orElse(false)
                || store.tryConfirmWallRemoval(receipt, observedRemoved);
    }

    public Optional<List<TrashHistoryStore.WallReceipt>> tryInspectPendingProjectileWalls() {
        return store.tryInspectWallReceipts();
    }

    public Optional<java.util.Map<UUID, TrashHistoryStore.WallReceipt>> tryInspectWallRecoveryReceipts(
            final java.util.Set<UUID> instances) {
        return store.tryInspectWallRecoveryReceipts(instances);
    }

    /** Caller owns the captured unit and projection; missing, drifted or duplicated units are never recreated. */
    public boolean tryRestoreAcknowledgedWallProjection(final ItemStack source, final UUID actor,
            final TrashHistoryStore.WallReceipt receipt, final java.util.function.BooleanSupplier admission,
            final java.util.function.Consumer<ItemStack> projection) {
        Objects.requireNonNull(actor); Objects.requireNonNull(receipt);
        Objects.requireNonNull(admission); Objects.requireNonNull(projection);
        if (!actor.equals(receipt.actor()) || source == null || source.getAmount() != 1
                || !itemFactory.isKnownItem(source) || !receipt.baseId().equals(itemFactory.idOf(source).orElse(null))
                || !"base".equals(itemFactory.phaseOf(source).orElse(null))
                || !receipt.instanceId().equals(instanceIdOf(source).orElse(null))
                || revisionOf(source) != receipt.beforeRevision() || preparedRepair(source).isPresent()) return false;
        creationEventOf(source);
        final ItemStack before = source.clone();
        return store.tryRestoreWallProjection(receipt, admission, acknowledged -> {
            final ItemStack restored = before.clone();
            itemFactory.applyPhase(restored, receipt.phase());
            writeAuthority(restored, acknowledged);
            projection.accept(restored);
        }, () -> projection.accept(before.clone()));
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
        final ItemStack captured = source.clone();
        final ItemStack unit = captured.clone(); unit.setAmount(1);
        final var plan = tryPrepareUnit(unit, event, player.getUniqueId());
        if (plan.isEmpty()) return false;
        return tryIndividualizeHandOnSuccess(player, hand, plan.orElseThrow(), () -> true, () -> true, ignored -> {});
    }

    /** Native inventory projection shares the prepared unit's WAL boundary; caller supplies only bounded owner-local work. */
    public boolean tryIndividualizeHandOnSuccess(final Player player, final EquipmentSlot hand, final UnitPlan plan,
            final java.util.function.BooleanSupplier admission, final java.util.function.BooleanSupplier finalAdmission,
            final java.util.function.Consumer<ItemStack> beforePublication) {
        return tryCommitPlannedHand(player, hand, plan, admission, finalAdmission, beforePublication, false);
    }

    public boolean tryTransformPlannedHandOnSuccess(final Player player, final EquipmentSlot hand, final UnitPlan plan,
            final java.util.function.BooleanSupplier admission, final java.util.function.BooleanSupplier finalAdmission) {
        return tryCommitPlannedHand(player, hand, plan, admission, finalAdmission, ignored -> {}, true);
    }

    private boolean tryCommitPlannedHand(final Player player, final EquipmentSlot hand, final UnitPlan plan,
            final java.util.function.BooleanSupplier admission, final java.util.function.BooleanSupplier finalAdmission,
            final java.util.function.Consumer<ItemStack> beforePublication, final boolean transition) {
        Objects.requireNonNull(player); Objects.requireNonNull(plan); Objects.requireNonNull(beforePublication);
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(player) || !player.getUniqueId().equals(plan.actor)
                || (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND)) return false;
        final ItemStack source = itemInHand(player, hand);
        if (!itemFactory.isKnownItem(source)) return false;
        final ItemStack captured = source.clone();
        final ItemStack unit = captured.clone(); unit.setAmount(1);
        final int heldSlot = player.getInventory().getHeldItemSlot();
        final ItemStack[] before = cloneContents(player.getInventory().getContents());
        if (transition && (plan.event != TrashHistoryEvent.ACTIVATED || itemFactory.successPhaseOf(plan.before).isEmpty())) return false;
        return tryCommitPlannedUnit(plan, unit,
                () -> admission.getAsBoolean() && captured.equals(itemInHand(player, hand))
                        && (hand != EquipmentSlot.HAND || heldSlot == player.getInventory().getHeldItemSlot())
                        && (captured.getAmount() == 1 || player.getInventory().firstEmpty() >= 0),
                finalAdmission, singleton -> {
                    beforePublication.accept(singleton);
                    setItemInHand(player, hand, singleton);
                    final ItemStack remainder = remainderOf(captured);
                    if (remainder != null && !player.getInventory().addItem(remainder).isEmpty()) {
                        throw new IllegalStateException("a Trash reservation remainder nem fér el");
                    }
                }, () -> player.getInventory().setContents(before), transition);
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
        return individualizeInternal(item, event, actor, detail, null);
    }

    private TrashHistoryStore.Snapshot individualizeInternal(
            final ItemStack item, final TrashHistoryEvent event,
            final UUID actor, final String detail, final UUID plannedInstance) {
        validateSingleton(item);
        final String baseId = itemFactory.idOf(item).orElseThrow();
        final String phase = itemFactory.phaseOf(item).orElseThrow();
        final UUID existing = instanceIdOf(item).orElse(null);
        final TrashHistoryStore.Snapshot history;
        if (existing == null) {
            final UUID instanceId = plannedInstance == null ? UUID.randomUUID() : plannedInstance;
            final TrashHistoryEvent creation = creationEventOf(item).orElse(null);
            final TrashHistoryStore.Snapshot created = store.createAndRecord(instanceId, baseId,
                    phase, creation == null ? event : creation, creation == null ? actor : null,
                    creation == null ? detail : "");
            history = creation == null ? created
                    : store.record(instanceId, baseId, phase, event, actor, detail);
        } else {
            if (plannedInstance != null && !existing.equals(plannedInstance)) {
                throw new IllegalStateException("planned native instance changed");
            }
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
        if (!pdc.has(repairPendingKey) && !pdc.has(repairBeforeDamageKey)
                && !pdc.has(repairActorKey)) return Optional.empty();
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
        final PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(originKey)) return Optional.empty();
        final String raw = pdc.get(originKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) throw new IllegalStateException("hiányos Trash origin marker");
        if (raw.equals("DEV_PROTOTYPE")) return Optional.of(TrashHistoryEvent.DEV_PROTOTYPED);
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
