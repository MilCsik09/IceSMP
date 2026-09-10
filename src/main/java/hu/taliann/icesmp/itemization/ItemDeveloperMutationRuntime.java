package hu.taliann.icesmp.itemization;

import hu.taliann.icesmp.dev.weaver.api.IntegrityMode;
import hu.taliann.icesmp.dev.weaver.execution.WeaverNativeEffectAuthority;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.items.UniqueMaterialFactory;
import hu.taliann.icesmp.pve.EquippedCombatPowerService;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import hu.taliann.icesmp.storage.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Owner-local developer ingress sharing the coordinator's journal, lock, mutation and render services. */
public final class ItemDeveloperMutationRuntime {
    public static final class Plan {
        private final ItemDeveloperMutationRuntime runtime;
        private final ItemSlotRef subject;
        private final ItemDeveloperReceipt receipt;
        private final ItemInstance result;
        private final IntegrityMode mode;
        private final long created = System.nanoTime();
        private final AtomicBoolean entered = new AtomicBoolean();
        private Plan(ItemDeveloperMutationRuntime runtime, ItemSlotRef subject, ItemDeveloperReceipt receipt,
                     ItemInstance result, IntegrityMode mode) {
            this.runtime = runtime; this.subject = subject; this.receipt = receipt; this.result = result; this.mode = mode;
        }
        public ItemDeveloperReceipt receipt() { return receipt; }
        public ItemSlotRef subject() { return subject; }
    }
    public record Result(ItemDeveloperReceipt receipt, ItemSlotRef observedSubject) { }
    private final JavaPlugin plugin;
    private final ItemIdentityService identity;
    private final UniqueMaterialFactory materials;
    private final ItemMutationService mutations;
    private final ItemMutationJournal journal;
    private final Set<UUID> inFlight;
    private final Supplier<RuneMutationPolicy.FamilyCompatibility> compatibility;
    private final BooleanSupplier current;
    private final WeaverItemSlots slots;
    private final Map<UUID, UUID> leases = new ConcurrentHashMap<>();

    ItemDeveloperMutationRuntime(JavaPlugin plugin, ItemIdentityService identity, UniqueMaterialFactory materials,
            ItemMutationService mutations, ItemMutationJournal journal, Set<UUID> inFlight,
            Supplier<RuneMutationPolicy.FamilyCompatibility> compatibility, BooleanSupplier current) {
        this.plugin = plugin; this.identity = identity; this.materials = materials; this.mutations = mutations;
        this.journal = journal; this.inFlight = inFlight; this.compatibility = compatibility; this.current = current;
        this.slots = new WeaverItemSlots(identity);
    }

    public Plan prepareOnOwner(Player actor, WeaverSlot slot, String expectedFingerprint, long expectedRevision,
                                UUID operation, ItemDeveloperMutation request, IntegrityMode mode) {
        Objects.requireNonNull(operation); Objects.requireNonNull(request); Objects.requireNonNull(mode);
        requireOwner(actor);
        if (!journal.isHealthy() || journal.hasPendingForDeveloper(actor.getUniqueId())
                || inFlight.contains(actor.getUniqueId()) || slot.kind() == WeaverSlot.Kind.CURSOR) throw refused("ITEM_MUTATION_PENDING");
        final ItemSlotRef subject = slots.capture(actor, slot);
        if (!subject.fingerprint().equals(expectedFingerprint) || subject.revision().isEmpty()
                || subject.revision().getAsLong() != expectedRevision) throw refused("ITEM_MUTATION_CONFLICT");
        final var before = actor.getInventory().getContents();
        if (before.length != 41 || !identity.inspectDuplicates(Arrays.asList(before)).clean()) throw refused("ITEM_DUPLICATE_OR_UNAVAILABLE");
        final int source = index(slot, actor.getInventory().getHeldItemSlot());
        final var inspection = identity.inspect(before[source]);
        if (inspection.status() != ItemIdentityService.Status.VALID || before[source].getAmount() != 1) throw refused("ITEM_IDENTITY_INVALID");
        final var instance = inspection.instance(); final var template = inspection.template();
        final boolean prototype = ItemPrototypePolicy.isPrototype(instance);
        requireMode(request.kind(), mode, prototype);
        if (prototype && !ItemPrototypePolicy.allowedCustody(before[source], actor.getUniqueId(), HiddenDevAuthority.PRIMARY_DEVELOPER)) {
            throw refused("ITEM_PROTOTYPE_CUSTODY_INVALID");
        }
        int target = source; ItemInstance candidate = instance; final long now = System.currentTimeMillis();
        if (request.kind() == ItemDeveloperMutation.Kind.CLONE_PROTOTYPE) {
            target = -1;
            for (int i = 0; i < 36; i++) if (before[i] == null || before[i].isEmpty()) { target = i; break; }
            if (target < 0) throw refused("ITEM_PROTOTYPE_STORAGE_FULL");
            candidate = mutations.clonePrototype(template, instance, UUID.randomUUID(), actor.getUniqueId(), operation, now);
        } else if (request.kind().reroll()) {
            if (template.rolledStatsAt(instance.ascension().stageId()).isEmpty()) throw refused("ITEM_NO_ROLLS");
            final var result = mutations.rerollFromDeveloper(template, instance,
                    new ItemMutationService.RerollRequest(operation, request.lockedStat(), request.minimumQuality(), request.stabilitySeal(), now),
                    () -> ThreadLocalRandom.current().nextDouble());
            if (!result.applied()) throw refused("ITEM_REROLL_REFUSED"); candidate = result.candidate();
        } else if (request.kind().ascend()) {
            final var result = mutations.ascendFromDeveloper(template, instance, new ItemMutationService.AscensionRequest(operation, now));
            if (!result.applied()) throw refused("ITEM_ASCENSION_REFUSED"); candidate = result.candidate();
        } else if (request.kind().addRune() || request.kind().removeRune()) {
            if (request.kind().addRune() && !materials.isDefined(request.runeId())) throw refused("ITEM_RUNE_UNKNOWN");
            final var next = RuneMutationPolicy.apply(instance.runes(), template.runeSocketCountAt(instance.ascension().stageId()),
                    request.kind().addRune() ? RuneMutationPolicy.Action.INSERT : RuneMutationPolicy.Action.REMOVE,
                    request.socketIndex(), request.runeId(), template.armorFamily(), compatibility.get());
            candidate = mutations.changeRunesFromDeveloper(template, instance, operation, next.runes(), now);
        }
        final ItemStack[] after = before.clone();
        after[target] = CanonicalPhysicalState.preserve(before[source], identity.render(template, candidate));
        final var rendered = identity.inspect(after[target]);
        if (rendered.status() != ItemIdentityService.Status.VALID || !candidate.equals(rendered.instance())) throw refused("ITEM_RENDER_REFUSED");
        final var entry = new ItemMutationJournal.Entry(operation, actor.getUniqueId(), "DEV_" + request.kind(), instance.itemId(),
                ItemMutationJournal.encodeInventory(before), ItemMutationJournal.encodeInventory(after), now);
        final var receipt = new ItemDeveloperReceipt(entry, request.kind(), source, target, candidate.itemId(),
                instance.mutationRevision(), candidate.mutationRevision(), ItemDeveloperReceipt.State.PENDING, 0);
        return new Plan(this, subject, receipt, candidate, mode);
    }

    /** The authenticated, acknowledged WW stage is required before native WAL or inventory publication. */
    public CompletionStage<Result> executeOnOwner(Plan plan, WeaverNativeEffectAuthority authority) {
        Objects.requireNonNull(plan); Objects.requireNonNull(authority);
        final var result = new CompletableFuture<Result>();
        final UUID playerId = plan.subject.holderId();
        final UUID operation = plan.receipt.entry().operationId();
        boolean acquired = false;
        try {
            final var actor = owner(playerId); validate(plan, authority, actor);
            if (!plan.entered.compareAndSet(false, true) || !inFlight.add(playerId)) throw refused("ITEM_MUTATION_PENDING");
            leases.put(playerId, operation); acquired = true;
            final var context = new GameplayEffectContext(sources(actor, plan), Set.of(new RewardSource.Item(plan.receipt.resultItemId())), 0);
            authority.prepare(context).whenComplete((permit, failure) -> schedule(playerId, () -> {
                if (failure != null || permit == null) { fail(result, plan, "ITEM_INFLUENCE_UNAVAILABLE", failure); return; }
                try { validate(plan, authority, owner(playerId)); }
                catch (RuntimeException stale) { fail(result, plan, "ITEM_MUTATION_CONFLICT", stale); return; }
                journal.prepareDeveloper(plan.receipt).whenComplete((prepared, writeFailure) -> schedule(playerId, () -> {
                    if (writeFailure != null || !Boolean.TRUE.equals(prepared)) { fail(result, plan, "ITEM_JOURNAL_UNAVAILABLE", writeFailure); return; }
                    publish(plan, authority, permit, result);
                }, () -> fail(result, plan, "ITEM_OWNER_RETIRED", null)));
            }, () -> fail(result, plan, "ITEM_OWNER_RETIRED", null)));
        } catch (RuntimeException failure) {
            // A caller that never acquired this operation must not unlock somebody else's work.
            if (acquired) release(plan);
            result.completeExceptionally(failure);
        }
        return result;
    }

    private void publish(Plan plan, WeaverNativeEffectAuthority authority, GameplayEffectPermit permit,
                         CompletableFuture<Result> result) {
        final UUID playerId = plan.subject.holderId();
        final Player actor;
        try {
            actor = owner(playerId); validate(plan, authority, actor);
            if (!permit.claim(sources(actor, plan))) throw refused("ITEM_INFLUENCE_REFUSED");
        } catch (RuntimeException rejected) {
            // No native effect has been entered. Retain the known abort even if the selection moved.
            journal.resolveDeveloper(plan.receipt, ItemDeveloperReceipt.State.ABORTED)
                    .whenComplete((ignored, failure) -> fail(result, plan, "ITEM_MUTATION_REFUSED", rejected));
            return;
        }
        try {
            actor.getInventory().setContents(ItemMutationJournal.decodeInventory(plan.receipt.entry().afterInventory()));
            actor.saveData();
            if (!matches(actor.getInventory().getContents(), plan.receipt.entry().afterInventory())) throw refused("ITEM_PROJECTION_UNOBSERVED");
            EquippedCombatPowerService.refreshAfterMutation(actor);
        } catch (RuntimeException failure) {
            // Retain PENDING. Never silently overwrite a partly published result or invent a refund.
            fail(result, plan, "ITEM_PROJECTION_PENDING", failure); return;
        }
        journal.resolveDeveloper(plan.receipt, ItemDeveloperReceipt.State.OBSERVED).whenComplete((observed, failure) ->
                schedule(playerId, () -> {
                    try {
                        if (failure != null || !Boolean.TRUE.equals(observed)) throw refused("ITEM_PROJECTION_PENDING");
                        final var currentActor = owner(playerId);
                        if (!matches(currentActor.getInventory().getContents(), plan.receipt.entry().afterInventory())) throw refused("ITEM_PROJECTION_CHANGED");
                        final var nativeReceipt = journal.findDeveloper(plan.receipt.entry().operationId()).orElseThrow();
                        final var observedSlot = slot(plan.receipt.targetSlot());
                        result.complete(new Result(nativeReceipt, slots.capture(currentActor, observedSlot)));
                    } catch (RuntimeException unavailable) { result.completeExceptionally(unavailable); }
                    finally { release(plan); }
                }, () -> fail(result, plan, "ITEM_OWNER_RETIRED", failure)));
    }

    /** Read-only native assessment. OBSERVED and ABORTED operations are never recreated. */
    public Optional<ItemDeveloperReceipt> inspect(UUID operation) { return journal.findDeveloper(operation); }
    boolean hasInFlight(UUID player) { return leases.containsKey(player); }
    void recoverOnOwner(Player actor, ItemMutationJournal.Entry entry) {
        requireOwner(actor);
        final var receipt = journal.findDeveloper(entry.operationId()).orElseThrow();
        if (receipt.state() != ItemDeveloperReceipt.State.PENDING || !receipt.entry().equals(entry)) return;
        final var contents = actor.getInventory().getContents();
        final ItemDeveloperReceipt.State state;
        if (matches(contents, entry.afterInventory())) state = ItemDeveloperReceipt.State.OBSERVED;
        else if (matches(contents, entry.beforeInventory())) state = ItemDeveloperReceipt.State.ABORTED;
        else return;
        actor.saveData();
        journal.resolveDeveloper(receipt, state);
    }
    private void validate(Plan plan, WeaverNativeEffectAuthority authority, Player actor) {
        if (plan.runtime != this || System.nanoTime() - plan.created >= TimeUnit.SECONDS.toNanos(5)
                || !authority.pendingOperation().equals(plan.receipt.entry().operationId())) throw refused("ITEM_PLAN_EXPIRED");
        authority.requireAction("item", actionId(plan.receipt.kind()), plan.mode, plan.subject);
        slots.verify(actor, plan.subject);
        if (index(plan.subject.slot(), actor.getInventory().getHeldItemSlot()) != plan.receipt.sourceSlot()
                || !matches(actor.getInventory().getContents(), plan.receipt.entry().beforeInventory())) throw refused("ITEM_MUTATION_CONFLICT");
    }
    static void requireMode(ItemDeveloperMutation.Kind kind, IntegrityMode mode, boolean prototype) {
        if (kind == ItemDeveloperMutation.Kind.REFRESH_PRESENTATION) {
            if ((mode == IntegrityMode.SANDBOX) != prototype) throw refused("ITEM_MODE_CONFLICT");
        } else if (kind == ItemDeveloperMutation.Kind.CLONE_PROTOTYPE) {
            if (mode != IntegrityMode.SANDBOX || prototype) throw refused("ITEM_MODE_CONFLICT");
        } else if (kind.prototype() ? mode != IntegrityMode.SANDBOX || !prototype
                : mode != IntegrityMode.LIVE_GM || prototype) throw refused("ITEM_MODE_CONFLICT");
    }
    public static String actionId(ItemDeveloperMutation.Kind kind) { return "item." + kind.name().toLowerCase(Locale.ROOT); }
    static int index(WeaverSlot slot, int held) {
        if (held < 0 || held > 8) throw refused("ITEM_HELD_SLOT_UNAVAILABLE");
        return switch (slot.kind()) {
            case INVENTORY -> slot.index(); case MAIN_HAND -> held; case OFF_HAND -> 40;
            case BOOTS -> 36; case LEGGINGS -> 37; case CHESTPLATE -> 38; case HELMET -> 39;
            case CURSOR -> throw refused("ITEM_CURSOR_UNSUPPORTED");
        };
    }
    static WeaverSlot slot(int index) {
        return switch (index) {
            case 36 -> WeaverSlot.named(WeaverSlot.Kind.BOOTS); case 37 -> WeaverSlot.named(WeaverSlot.Kind.LEGGINGS);
            case 38 -> WeaverSlot.named(WeaverSlot.Kind.CHESTPLATE); case 39 -> WeaverSlot.named(WeaverSlot.Kind.HELMET);
            case 40 -> WeaverSlot.named(WeaverSlot.Kind.OFF_HAND);
            default -> new WeaverSlot(WeaverSlot.Kind.INVENTORY, index);
        };
    }
    public static boolean matches(ItemStack[] current, List<String> encoded) {
        if (current == null || current.length != encoded.size()) return false;
        final var expected = ItemMutationJournal.decodeInventory(encoded);
        for (int i = 0; i < current.length; i++) {
            final var actual = current[i] == null || current[i].isEmpty() ? null : current[i];
            if (!Objects.equals(actual, expected[i])) return false;
        }
        return true;
    }
    private List<RewardSource> sources(Player actor, Plan plan) {
        final var sources = new LinkedHashSet<>(BukkitRewardSources.causal(actor));
        sources.add(new RewardSource.Player(actor.getUniqueId())); sources.add(new RewardSource.Item(plan.receipt.entry().itemId()));
        return List.copyOf(sources);
    }
    private Player owner(UUID player) { final var actor = Bukkit.getPlayer(player); requireOwner(actor); return actor; }
    private void requireOwner(Player actor) {
        if (!current.getAsBoolean() || !plugin.isEnabled() || actor == null || !Bukkit.isOwnedByCurrentRegion(actor)) throw refused("ITEM_OWNER_UNAVAILABLE");
        if (!actor.isOnline() || !actor.isValid() || actor.isDead() || !HiddenDevAuthority.isDeveloper(actor.getUniqueId())) throw refused("ITEM_ACTOR_UNAVAILABLE");
    }
    private void schedule(UUID player, Runnable action, Runnable retired) {
        final var actor = Bukkit.getPlayer(player);
        if (actor == null) { retired.run(); return; }
        final var claimed = new AtomicBoolean();
        final Runnable rejected = () -> { if (claimed.compareAndSet(false, true)) retired.run(); };
        try { if (actor.getScheduler().run(plugin, task -> {
            if (claimed.compareAndSet(false, true)) {
                try { action.run(); }
                catch (RuntimeException | LinkageError unavailable) { retired.run(); }
            }
        }, rejected) == null) rejected.run(); }
        catch (RuntimeException failure) { rejected.run(); }
    }
    private void release(Plan plan) {
        if (leases.remove(plan.subject.holderId(), plan.receipt.entry().operationId())) inFlight.remove(plan.subject.holderId());
    }
    private void fail(CompletableFuture<Result> result, Plan plan, String reason, Throwable failure) {
        release(plan); result.completeExceptionally(new IllegalStateException(reason, failure));
    }
    private static IllegalStateException refused(String reason) { return new IllegalStateException(reason); }
}
