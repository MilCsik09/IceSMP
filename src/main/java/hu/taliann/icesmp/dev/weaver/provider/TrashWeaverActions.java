package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import hu.taliann.icesmp.trash.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Native history owns identity, projection and reversal. This adapter owns only the WW plan and receipt. */
final class TrashWeaverActions {
    static final String INVENTORY = "trash.inventory_fingerprint", AFTER_SUBJECT = "trash.after_subject";
    static final WeaverRevisionScope SCOPE = new WeaverRevisionScope(1, Set.of(INVENTORY));
    static final Map<String, TrashDeveloperReceipt.Kind> KINDS = Map.of(
            "trash.individualize_unit", TrashDeveloperReceipt.Kind.INDIVIDUALIZE,
            "trash.transition_success", TrashDeveloperReceipt.Kind.TRANSITION_SUCCESS,
            "trash.repair", TrashDeveloperReceipt.Kind.REPAIR,
            "trash.give_sandbox_copy", TrashDeveloperReceipt.Kind.SANDBOX_COPY);
    private final WeaverOwnerRouter owners;
    private final TrashHistoryService history;
    private final WeaverItemSlots slots;
    private final Map<String, ActionDescriptor> actions;

    TrashWeaverActions(WeaverProviderServices services, TrashHistoryService history, WeaverItemSlots slots) {
        owners = services.owners(); this.history = Objects.requireNonNull(history); this.slots = Objects.requireNonNull(slots);
        final var descriptors = new TreeMap<String, ActionDescriptor>();
        KINDS.forEach((id, kind) -> descriptors.put(id, descriptor(id, kind)));
        actions = Map.copyOf(descriptors);
    }
    static ActionDescriptor descriptor(String id, TrashDeveloperReceipt.Kind kind) {
        final String label = switch (kind) {
            case INDIVIDUALIZE -> "Egy darab egyedivé tétele";
            case TRANSITION_SUCCESS -> "Sikerfázis alkalmazása";
            case REPAIR -> "Lelet javítása";
            case SANDBOX_COPY -> "Védett próbapéldány készítése";
            default -> throw new IllegalArgumentException("Unregistered native action");
        };
        return new ActionDescriptor(id, TrashWeaverProvider.FACET, Component.text(label), RiskLevel.MUTATING,
                Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX),
                Set.of(IntegrityImpact.TAINT_SUBJECT, IntegrityImpact.TAINT_CREATED), Set.of(WeaverSubjectKind.ITEM_SLOT),
                List.of(), AreaSupport.NONE, Optional.empty(), kind != TrashDeveloperReceipt.Kind.INDIVIDUALIZE,
                kind == TrashDeveloperReceipt.Kind.INDIVIDUALIZE ? Optional.of("Az új tárgyazonosító és története végleges.") : Optional.empty(), 5, SCOPE);
    }
    List<ActionDescriptor> descriptors() { return actions.values().stream().sorted(Comparator.comparing(ActionDescriptor::id)).toList(); }
    Set<String> discover(SubjectSnapshot snapshot) {
        if (!(snapshot.ref() instanceof ItemSlotRef item) || !HiddenDevAuthority.isDeveloper(item.holderId())
                || item.slot().kind() == WeaverSlot.Kind.CURSOR || !snapshot.facts().containsKey("trash.identity")
                || !snapshot.facts().containsKey(INVENTORY)) return Set.of();
        final var available = new HashSet<>(actions.keySet());
        if ("true".equals(value(snapshot.facts(), "trash.tracked"))) available.remove("trash.individualize_unit");
        if ("true".equals(value(snapshot.facts(), "trash.prototype"))) available.remove("trash.give_sandbox_copy");
        if (!"base".equals(value(snapshot.facts(), "trash.phase")) || "".equals(value(snapshot.facts(), "trash.success_phase")))
            available.remove("trash.transition_success");
        return Set.copyOf(available);
    }
    private static String value(Map<String, WeaverValue> facts, String key) {
        final var value = facts.get(key); return value == null ? "" : Objects.toString(value.payload().get("value"), "");
    }
    Map<String, WeaverValue> capture(SubjectRef subject) {
        if (!(subject instanceof ItemSlotRef item) || !HiddenDevAuthority.isDeveloper(item.holderId())
                || item.slot().kind() == WeaverSlot.Kind.CURSOR) return Map.of();
        final var player = owner(item.holderId());
        return inventoryFacts(player.getInventory().getContents());
    }
    Map<String, WeaverValue> captureRecovery(RecoveryContext context) {
        context.authority().require(context.operation());
        final var subject = requireSubject(context.operation().subject(), context.operation().actorId());
        final var player = owner(subject.holderId());
        return observedFacts(player, subject);
    }
    private Map<String, WeaverValue> observedFacts(Player player, ItemSlotRef subject) {
        final var facts = new HashMap<>(inventoryFacts(player.getInventory().getContents()));
        slots.peek(player, subject.slot()).ifPresent(item -> facts.put(AFTER_SUBJECT, subjectValue(slots.capture(player, subject.slot()))));
        return Map.copyOf(facts);
    }
    static Map<String, WeaverValue> inventoryFacts(ItemStack[] inventory) {
        if (inventory == null || inventory.length != 41) throw new WeaverDomainRejection("PLAYER_INVENTORY_UNAVAILABLE");
        final var contents = new ArrayList<String>(41);
        for (final var item : inventory) contents.add(item == null || item.isEmpty() ? "EMPTY" : WeaverItemSlots.fingerprint(item.serializeAsBytes()));
        return Map.of(INVENTORY, scalar(WeaverItemSlots.fingerprint(CanonicalValueBytes.encode(Map.of("slots", contents)))));
    }
    static WeaverValue scalar(String text) {
        return new WeaverValue(WeaverTypeId.parse("weaver:text@1"), Map.of("value", text), "trash", TrashWeaverProvider.FACET, Set.of(), 0);
    }
    static WeaverValue subjectValue(SubjectRef subject) {
        return new WeaverValue(WeaverUndoSubject.TYPE, SubjectKeyCodec.payload(subject), "trash", TrashWeaverProvider.FACET, Set.of(WeaverUndoSubject.CAPABILITY), 0);
    }
    static String fingerprint(SubjectRef subject, Map<String, WeaverValue> facts) {
        return SCOPE.apply(new SubjectSnapshot(subject, 0, "trash", facts)).revisionFingerprint();
    }
    private static Player owner(UUID id) {
        final var player = Bukkit.getPlayer(id);
        if (player == null || !Bukkit.isOwnedByCurrentRegion(player)) throw new WeaverDomainRejection("OWNER_UNAVAILABLE");
        if (!player.isOnline() || player.isDead() || !player.isValid()) throw new WeaverDomainRejection("PLAYER_UNAVAILABLE");
        return player;
    }
    static ItemSlotRef requireSubject(SubjectRef subject, UUID actor) {
        if (!(subject instanceof ItemSlotRef item) || !HiddenDevAuthority.isDeveloper(actor) || !item.holderId().equals(actor)
                || item.slot().kind() == WeaverSlot.Kind.CURSOR) throw new WeaverDomainRejection("TRASH_PERSONAL_SLOT_REQUIRED");
        return item;
    }
    static int index(WeaverSlot slot, int held) {
        if (held < 0 || held > 8) throw new WeaverDomainRejection("TRASH_HELD_SLOT_UNAVAILABLE");
        return switch (slot.kind()) {
            case INVENTORY -> slot.index(); case MAIN_HAND -> held; case OFF_HAND -> 40;
            case BOOTS -> 36; case LEGGINGS -> 37; case CHESTPLATE -> 38; case HELMET -> 39;
            case CURSOR -> throw new WeaverDomainRejection("TRASH_PERSONAL_SLOT_REQUIRED");
        };
    }
    private void validate(ProviderContext context, ActionRequest request) {
        context.authority().requireValid();
        if (!actions.containsKey(request.actionId()) || !request.parameters().isEmpty()
                || request.integrityMode() != IntegrityMode.SANDBOX || context.integrityMode() != IntegrityMode.SANDBOX
                || request.lifetime() != Lifetime.ONE_SHOT || context.lifetime() != Lifetime.ONE_SHOT)
            throw new WeaverDomainRejection("TRASH_ACTION_UNAVAILABLE");
    }
    PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        validate(context, request);
        return prepare(context, snapshot, request.actionId(), Optional.empty());
    }
    PreparedAction undo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt) {
        final var undo = receipt.undo().orElseThrow(() -> new WeaverDomainRejection("TRASH_UNDO_UNAVAILABLE"));
        validate(context, new ActionRequest(undo.actionId(), undo.parameters(), context.lifetime(), context.integrityMode()));
        if (!receipt.providerId().equals("trash") || !receipt.receiptId().equals(receipt.operationId())
                || receipt.status() != ReceiptStatus.COMMITTED || !WeaverUndoSubject.resolve(receipt).equals(snapshot.ref())
                || !undo.expectedCurrentFingerprint().equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        final var nativeReceipt = history.tryInspectDeveloperReceipt(receipt.operationId())
                .orElseThrow(() -> new WeaverDomainRejection("TRASH_HISTORY_UNAVAILABLE"))
                .orElseThrow(() -> new WeaverDomainRejection("TRASH_NATIVE_RECEIPT_MISSING"));
        if (nativeReceipt.kind() != KINDS.get(receipt.actionId())) throw new WeaverDomainRejection("TRASH_UNDO_UNAVAILABLE");
        return prepare(context, snapshot, receipt.actionId(), Optional.of(nativeReceipt));
    }
    private PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, String action, Optional<TrashDeveloperReceipt> inverse) {
        final var subject = requireSubject(snapshot.ref(), context.authority().actor());
        final var player = owner(subject.holderId()); slots.verify(player, subject);
        final var before = inventoryFacts(player.getInventory().getContents());
        if (!fingerprint(subject, before).equals(snapshot.revisionFingerprint())) throw new WeaverDomainRejection("CONFLICT");
        final int source = index(subject.slot(), player.getInventory().getHeldItemSlot());
        final UUID operation = UUID.randomUUID();
        final var plan = (inverse.isPresent() ? history.tryPrepareDeveloperReversal(operation, subject.holderId(), inverse.orElseThrow(), player.getInventory().getContents())
                : history.tryPrepareDeveloperMutation(operation, subject.holderId(), KINDS.get(action), source, player.getInventory().getContents()))
                .orElseThrow(() -> new WeaverDomainRejection("TRASH_NATIVE_PREPARATION_REFUSED"));
        if (inverse.isPresent() && inverse.orElseThrow().slots().getFirst().slot() != source) throw new WeaverDomainRejection("CONFLICT");
        final var payload = new OperationRecoveryPayload(1, Map.of("kind", "trash_item", "operation", operation.toString(),
                "instance", plan.instanceId().toString(), "source", source, "before", value(before, INVENTORY),
                "native_kind", inverse.isPresent() ? "REVERT" : KINDS.get(action).name(),
                "reverses", inverse.map(value -> value.operationId().toString()).orElse("")));
        final var stage = new ExecutionStage("trash.native_item", new EntityOwner(subject.holderId()), Map.of(),
                (execution, ignored) -> start(execution, subject, plan, source), Optional.empty(), 5000);
        return new PreparedAction(operation, actions.get(action), subject, snapshot.revisionFingerprint(), List.of(stage), payload,
                (prepared, results, now) -> receipt(operation, action, subject, before, results.getLast().facts(), inverse.isPresent(), now));
    }
    private Optional<UUID> sourceIdentity(Player player, int source) {
        return history.tryInspect(player.getInventory().getItem(source))
                .orElseThrow(() -> new WeaverDomainRejection("TRASH_HISTORY_UNAVAILABLE"))
                .history().map(TrashHistoryStore.Snapshot::instanceId);
    }
    private List<RewardSource> sources(Player player, Optional<UUID> source) {
        final var result = new LinkedHashSet<>(BukkitRewardSources.causal(player));
        result.add(new RewardSource.Player(player.getUniqueId()));
        source.ifPresent(value -> result.add(new RewardSource.Item(value)));
        return List.copyOf(result);
    }
    private CompletionStage<StageResult> start(ExecutionContext execution, ItemSlotRef subject, TrashHistoryService.DeveloperPlan plan, int source) {
        execution.authority().requireValid(); final var nativeAuthority = execution.nativeEffects().orElseThrow();
        final var player = owner(subject.holderId()); slots.verify(player, subject);
        if (index(subject.slot(), player.getInventory().getHeldItemSlot()) != source || !plan.matchesInventory(player.getInventory().getContents()))
            throw new WeaverDomainRejection("CONFLICT");
        final var effect = new GameplayEffectContext(sources(player, sourceIdentity(player, source)), Set.of(new RewardSource.Item(plan.instanceId())), 0);
        // The continuation retains only detached plans/subjects and re-resolves the current player on its owner.
        return nativeAuthority.prepare(effect).thenCompose(permit -> owners.submit(new EntityOwner(subject.holderId()), subject.holderId(),
                Duration.ofSeconds(5), () -> CompletableFuture.completedFuture(commit(execution, subject, plan, source, permit))));
    }
    private StageResult commit(ExecutionContext execution, ItemSlotRef subject, TrashHistoryService.DeveloperPlan plan, int source, GameplayEffectPermit permit) {
        execution.authority().requireValid(); execution.nativeEffects().orElseThrow().requireValid();
        final var player = owner(subject.holderId()); slots.verify(player, subject);
        final var before = cloneContents(player.getInventory().getContents());
        // The native transaction rechecks this exact item's history while holding its own lock.
        // Final causal capture must not re-enter its deliberately non-reentrant inspection API.
        final var sourceIdentity = sourceIdentity(player, source);
        final var receipt = history.tryCommitDeveloperMutation(plan, () -> {
            execution.authority().requireValid(); execution.nativeEffects().orElseThrow().requireValid();
            return owner(subject.holderId()) == player && index(subject.slot(), player.getInventory().getHeldItemSlot()) == source
                    && plan.matchesInventory(player.getInventory().getContents());
        }, () -> permit.claim(sources(player, sourceIdentity)), player.getInventory()::setContents,
                () -> player.getInventory().setContents(cloneContents(before)))
                .orElseThrow(() -> new WeaverDomainRejection("TRASH_NATIVE_COMMIT_REFUSED"));
        player.saveData();
        if (!history.tryConfirmDeveloperProjection(receipt, player.getInventory()::getContents)) throw new WeaverDomainRejection("TRASH_PROJECTION_UNOBSERVED");
        final var after = observedFacts(player, subject);
        final var target = SubjectKeyCodec.decodePayload(after.get(AFTER_SUBJECT).payload());
        return new StageResult(fingerprint(target, after), after, Map.of());
    }
    private static ItemStack[] cloneContents(ItemStack[] contents) {
        return Arrays.stream(contents).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }
    PreparedEffects effects(ProviderContext context, PreparedAction action) {
        context.authority().requireValid();
        if (!actions.containsKey(action.descriptor().id()) || !action.operationId().toString().equals(action.recoveryPayload().fields().get("operation")))
            throw new WeaverDomainRejection("TRASH_EFFECT_PLAN_CONFLICT");
        final UUID instance = UUID.fromString((String) action.recoveryPayload().fields().get("instance"));
        return new PreparedEffects(new WeaverEffectIntent(Set.of(WeaverInfluenceTarget.exact(new RewardSource.Item(instance)))),
                (prepared, results, receipt, sequence) -> WeaverEffectCommit.none());
    }
    static WeaverReceipt receipt(UUID operation, String action, SubjectRef subject, Map<String, WeaverValue> before,
            Map<String, WeaverValue> after, boolean inverse, long now) {
        final var target = SubjectKeyCodec.decodePayload(after.get(AFTER_SUBJECT).payload());
        final String afterFingerprint = fingerprint(target, after);
        return new WeaverReceipt(operation, operation, "trash", action, subject, RiskLevel.MUTATING, Lifetime.ONE_SHOT, IntegrityMode.SANDBOX,
                fingerprint(subject, before), afterFingerprint, before, after,
                inverse || KINDS.get(action) == TrashDeveloperReceipt.Kind.INDIVIDUALIZE ? Optional.empty()
                        : Optional.of(new UndoSpec(action, afterFingerprint, Map.of())), now, ReceiptStatus.COMMITTED);
    }
    static Map<String, Object> recoveryFields(WeaverOperationRecord operation) {
        final var subject = requireSubject(operation.subject(), operation.actorId());
        final var fields = new HashMap<>(operation.recoveryPayload().fields()); fields.remove(WeaverOperationScope.RESERVATIONS);
        if (!operation.providerId().equals("trash") || operation.recoveryPayload().schemaVersion() != 1
                || !fields.keySet().equals(Set.of("kind", "operation", "instance", "source", "before", "native_kind", "reverses"))
                || !"trash_item".equals(fields.get("kind")) || !operation.operationId().toString().equals(fields.get("operation"))
                || !KINDS.containsKey(operation.request().actionId()) || !operation.request().parameters().isEmpty()
                || operation.request().integrityMode() != IntegrityMode.SANDBOX || operation.request().lifetime() != Lifetime.ONE_SHOT
                || !(fields.get("before") instanceof String before) || !before.matches("[0-9a-f]{64}")
                || !fingerprint(subject, Map.of(INVENTORY, scalar(before))).equals(operation.beforeFingerprint())
                || !(fields.get("source") instanceof Long source) || source < 0 || source > 40
                || subject.slot().kind() != WeaverSlot.Kind.MAIN_HAND && source != index(subject.slot(), 0)
                || !(fields.get("instance") instanceof String instance) || !UUID.fromString(instance).toString().equals(instance)
                || !(fields.get("reverses") instanceof String inverse)
                || !inverse.equals(operation.undoClaim().map(claim -> claim.receiptId().toString()).orElse(""))
                || !Objects.equals(fields.get("native_kind"), inverse.isEmpty() ? KINDS.get(operation.request().actionId()).name() : "REVERT")
                || !inverse.isEmpty() && KINDS.get(operation.request().actionId()) == TrashDeveloperReceipt.Kind.INDIVIDUALIZE)
            throw new WeaverDomainRejection("TRASH_RECOVERY_PLAN_MISMATCH");
        return Map.copyOf(fields);
    }
    RecoveryAssessment assess(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        context.authority().require(operation);
        try {
            final var subject = requireSubject(operation.subject(), operation.actorId());
            final var fields = recoveryFields(operation);
            if (!snapshot.ref().equals(subject)) return conflict("TRASH_RECOVERY_PLAN_MISMATCH");
            final var before = Map.of(INVENTORY, scalar((String) fields.get("before")));
            if (!fingerprint(subject, before).equals(operation.beforeFingerprint())) return conflict("TRASH_RECOVERY_PLAN_MISMATCH");
            final var nativeReceipt = history.tryInspectDeveloperReceipt(operation.operationId())
                    .orElseThrow(() -> new WeaverDomainRejection("TRASH_HISTORY_UNAVAILABLE"));
            if (nativeReceipt.isEmpty()) return operation.beforeFingerprint().equals(snapshot.revisionFingerprint())
                    ? new RecoveryAssessment(ObservedOperationState.BEFORE, false, Optional.empty(), "TRASH_NATIVE_BEFORE_OBSERVED")
                    : conflict("TRASH_NATIVE_RECEIPT_MISSING");
            final var observed = nativeReceipt.orElseThrow();
            final boolean inverse = !"".equals(fields.get("reverses"));
            if (!observed.actor().equals(operation.actorId()) || !observed.instanceId().toString().equals(fields.get("instance"))
                    || !observed.kind().name().equals(fields.get("native_kind"))
                    || observed.slots().getFirst().slot() != ((Number) fields.get("source")).intValue()
                    || !observed.reverses().map(UUID::toString).orElse("").equals(fields.get("reverses"))
                    || inverse != operation.undoClaim().isPresent()
                    || inverse && (!operation.undoClaim().orElseThrow().receiptId().toString().equals(fields.get("reverses")) || observed.kind() != TrashDeveloperReceipt.Kind.REVERT)
                    || !inverse && observed.kind() != KINDS.get(operation.request().actionId()))
                return conflict("TRASH_NATIVE_RECEIPT_CONFLICT");
            final var player = owner(subject.holderId());
            if (!observed.projectionObserved()) {
                final var inventory = player.getInventory().getContents();
                if (history.tryObserveDeveloperProjection(observed, inventory).orElse(false)
                        || history.tryObserveDeveloperBeforeProjection(observed, inventory).orElse(false))
                    throw new WeaverDomainRejection("NATIVE_PROJECTION_PENDING");
                return conflict("TRASH_NATIVE_PROJECTION_CONFLICT");
            }
            if (index(subject.slot(), player.getInventory().getHeldItemSlot()) != observed.slots().getFirst().slot()
                    || !history.tryObserveDeveloperProjection(observed, player.getInventory().getContents()).orElse(false))
                return conflict("TRASH_NATIVE_PROJECTION_CONFLICT");
            final var after = observedFacts(player, subject);
            final var restored = receipt(operation.operationId(), operation.request().actionId(), subject, before, after, inverse,
                    operation.receipt().map(WeaverReceipt::createdAt).orElseGet(() -> Math.max(System.currentTimeMillis(), operation.preparedAt())));
            if (operation.receipt().isPresent() && !operation.receipt().orElseThrow().equals(restored)) return conflict("TRASH_RECEIPT_DRIFT");
            return new RecoveryAssessment(ObservedOperationState.APPLIED, false, Optional.of(restored), "TRASH_NATIVE_RECEIPT_AND_PROJECTION_OBSERVED");
        } catch (WeaverDomainRejection unavailable) {
            if (Set.of("NATIVE_PROJECTION_PENDING", "OWNER_UNAVAILABLE", "PLAYER_UNAVAILABLE").contains(unavailable.code())) throw unavailable;
            return conflict("TRASH_RECOVERY_UNAVAILABLE");
        } catch (RuntimeException invalid) { return conflict("TRASH_RECOVERY_UNAVAILABLE"); }
    }
    private static RecoveryAssessment conflict(String reason) {
        return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), reason);
    }
}
