package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.integrity.*;
import hu.taliann.icesmp.dev.weaver.persistence.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.integrity.RewardSource;
import hu.taliann.icesmp.itemization.*;
import hu.taliann.icesmp.security.HiddenDevAuthority;
import hu.taliann.icesmp.storage.ItemDeveloperReceipt;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.*;

/** WW metadata and result observation; all item decisions and writes stay inside native Itemization. */
final class ItemizationWeaverActions {
    static final String INVENTORY = "item.inventory_fingerprint", AFTER_SUBJECT = "item.after_subject";
    static final WeaverRevisionScope SCOPE = new WeaverRevisionScope(1, Set.of(INVENTORY));
    static final Map<String, ItemDeveloperMutation.Kind> KINDS;
    static {
        final var kinds = new TreeMap<String, ItemDeveloperMutation.Kind>();
        for (final var kind : ItemDeveloperMutation.Kind.values()) kinds.put(ItemDeveloperMutationRuntime.actionId(kind), kind);
        KINDS = Map.copyOf(kinds);
    }
    private final ItemMutationCoordinator coordinator;
    private final ItemDeveloperMutationRuntime nativeItems;
    private final WeaverItemSlots slots;
    private final WeaverTypeRegistry types;
    private final Map<String, ActionDescriptor> actions;
    ItemizationWeaverActions(WeaverTypeRegistry types, ItemMutationCoordinator coordinator, ItemIdentityService identity) {
        this.types = types; this.coordinator = Objects.requireNonNull(coordinator);
        nativeItems = coordinator.developerMutations(); slots = new WeaverItemSlots(identity);
        final var descriptors = new TreeMap<String, ActionDescriptor>();
        KINDS.forEach((id, kind) -> descriptors.put(id, descriptor(kind))); actions = Map.copyOf(descriptors);
    }
    static ActionDescriptor descriptor(ItemDeveloperMutation.Kind kind) {
        final boolean canonical = !kind.prototype() && kind != ItemDeveloperMutation.Kind.REFRESH_PRESENTATION;
        final Set<IntegrityMode> modes = kind == ItemDeveloperMutation.Kind.REFRESH_PRESENTATION
                ? Set.of(IntegrityMode.values()) : Set.of(canonical ? IntegrityMode.LIVE_GM : IntegrityMode.SANDBOX);
        final var parameters = new ArrayList<ActionParameter>();
        if (kind.reroll()) {
            parameters.add(parameter("locked_stat", "Megtartott stat (üres: nincs)", "text", ActionParameter.InputKind.TEXT, "", 0, 0));
            parameters.add(parameter("minimum_quality", "Minimális minőség", "double", ActionParameter.InputKind.DECIMAL, 0.0D, 0, 1));
            parameters.add(parameter("stability_seal", "Költséglépcső megtartása", "boolean", ActionParameter.InputKind.BOOLEAN, false, 0, 0));
        }
        if (kind.addRune()) parameters.add(new ActionParameter("rune", Component.text("Rúna"), ItemizationWeaverProvider.RUNE,
                ActionParameter.InputKind.CATALOG, true, Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalInt.empty(), Optional.of("item.runes"), Set.of("item.rune")));
        if (kind.removeRune()) parameters.add(parameter("socket", "Rúnahely (0–1)", "int", ActionParameter.InputKind.INTEGER, 0, 0, 1));
        return new ActionDescriptor(ItemDeveloperMutationRuntime.actionId(kind), ItemizationWeaverProvider.FACET,
                Component.text(label(kind)), canonical ? RiskLevel.CANONICAL : RiskLevel.MUTATING, Set.of(Lifetime.ONE_SHOT), modes,
                Set.of(kind == ItemDeveloperMutation.Kind.CLONE_PROTOTYPE ? IntegrityImpact.TAINT_CREATED : IntegrityImpact.TAINT_SUBJECT),
                Set.of(WeaverSubjectKind.ITEM_SLOT), parameters, AreaSupport.NONE, Optional.empty(), kind != ItemDeveloperMutation.Kind.REFRESH_PRESENTATION,
                kind == ItemDeveloperMutation.Kind.REFRESH_PRESENTATION ? Optional.of("A megjelenést az aktuális tárgysablon állítja elő; a régi megjelenés nem állítható vissza.")
                        : Optional.empty(), canonical ? 10 : 5, SCOPE);
    }
    private static String label(ItemDeveloperMutation.Kind kind) {
        return switch (kind) {
            case CLONE_PROTOTYPE -> "Védett próbapéldány készítése";
            case REROLL_PROTOTYPE -> "Próbapéldány újrasorsolása"; case REROLL_CANONICAL -> "Valódi tárgy újrasorsolása";
            case ASCEND_PROTOTYPE -> "Próbapéldány felemelése"; case ASCEND_CANONICAL -> "Valódi tárgy felemelése";
            case ADD_RUNE_PROTOTYPE -> "Rúna a próbapéldányba"; case ADD_RUNE_CANONICAL -> "Rúna a valódi tárgyba";
            case REMOVE_RUNE_PROTOTYPE -> "Rúna eltávolítása a próbapéldányból"; case REMOVE_RUNE_CANONICAL -> "Rúna eltávolítása a valódi tárgyból";
            case REFRESH_PRESENTATION -> "Tárgymegjelenés frissítése";
        };
    }
    private static ActionParameter parameter(String id, String label, String type, ActionParameter.InputKind input, Object value, double min, double max) {
        final var token = WeaverTypeId.parse("weaver:" + type + "@1");
        final boolean number = input == ActionParameter.InputKind.INTEGER || input == ActionParameter.InputKind.DECIMAL;
        return new ActionParameter(id, Component.text(label), token, input, false,
                Optional.of(new WeaverValue(token, Map.of("value", value), "item", ItemizationWeaverProvider.FACET, Set.of(), 0)),
                number ? OptionalDouble.of(min) : OptionalDouble.empty(), number ? OptionalDouble.of(max) : OptionalDouble.empty(),
                input == ActionParameter.InputKind.TEXT ? OptionalInt.of(64) : OptionalInt.empty(), Optional.empty(), Set.of());
    }
    List<ActionDescriptor> descriptors() { return actions.values().stream().sorted(Comparator.comparing(ActionDescriptor::id)).toList(); }
    static ItemDeveloperMutation request(ActionDescriptor descriptor, ActionRequest request, WeaverTypeRegistry types) {
        final var values = new HashMap<String, WeaverValue>();
        final var known = descriptor.parameters().stream().map(ActionParameter::id).collect(java.util.stream.Collectors.toSet());
        if (!descriptor.id().equals(request.actionId()) || !known.containsAll(request.parameters().keySet())) throw refused("ITEM_PARAMETERS_INVALID");
        for (final var parameter : descriptor.parameters()) {
            final var value = Optional.ofNullable(request.parameters().get(parameter.id())).or(() -> parameter.defaultValue()).orElseThrow(() -> refused("ITEM_PARAMETER_REQUIRED"));
            parameter.validate(value, types).requireValid(); values.put(parameter.id(), value);
        }
        return new ItemDeveloperMutation(KINDS.get(request.actionId()), string(values, "locked_stat", "value", ""),
                values.containsKey("minimum_quality") ? ((Number) values.get("minimum_quality").payload().get("value")).doubleValue() : 0,
                values.containsKey("stability_seal") && Boolean.TRUE.equals(values.get("stability_seal").payload().get("value")),
                string(values, "rune", "id", ""), values.containsKey("socket") ? Math.toIntExact(((Number) values.get("socket").payload().get("value")).longValue()) : -1);
    }
    private static String string(Map<String, WeaverValue> values, String id, String key, String fallback) {
        return values.containsKey(id) ? (String) values.get(id).payload().get(key) : fallback;
    }
    Set<String> discover(SubjectSnapshot snapshot) {
        if (!(snapshot.ref() instanceof ItemSlotRef subject) || !HiddenDevAuthority.isDeveloper(subject.holderId())
                || subject.slot().kind() == WeaverSlot.Kind.CURSOR || !snapshot.facts().containsKey(INVENTORY)) return Set.of();
        final boolean prototype = "true".equals(scalarValue(snapshot.facts(), "item.prototype"));
        return KINDS.entrySet().stream().filter(entry -> {
            final var kind = entry.getValue();
            if (kind == ItemDeveloperMutation.Kind.REFRESH_PRESENTATION) return true;
            if (kind == ItemDeveloperMutation.Kind.CLONE_PROTOTYPE) return !prototype;
            if (kind.prototype() != prototype) return false;
            if (kind.reroll()) return "true".equals(scalarValue(snapshot.facts(), "item.has_rolls"));
            if (kind.ascend()) return "true".equals(scalarValue(snapshot.facts(), "item.can_ascend"));
            final int count = Integer.parseInt(scalarValue(snapshot.facts(), "item.rune_count"));
            final int capacity = Integer.parseInt(scalarValue(snapshot.facts(), "item.rune_capacity"));
            return kind.removeRune() ? count > 0 : count < capacity;
        }).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    static ItemSlotRef subject(SubjectRef ref, UUID actor) {
        if (!(ref instanceof ItemSlotRef item) || !HiddenDevAuthority.isDeveloper(actor) || !item.holderId().equals(actor)
                || item.slot().kind() == WeaverSlot.Kind.CURSOR || item.instanceId().isEmpty() || item.revision().isEmpty()) throw refused("ITEM_PERSONAL_MANAGED_SLOT_REQUIRED");
        return item;
    }
    static Player owner(UUID id) {
        final var player = Bukkit.getPlayer(id);
        if (player == null || !Bukkit.isOwnedByCurrentRegion(player)) throw refused("OWNER_UNAVAILABLE");
        if (!player.isOnline() || !player.isValid() || player.isDead()) throw refused("PLAYER_UNAVAILABLE");
        return player;
    }
    static WeaverValue scalar(String value) { return new WeaverValue(WeaverTypeId.parse("weaver:text@1"), Map.of("value", value), "item", ItemizationWeaverProvider.FACET, Set.of(), 0); }
    static String scalarValue(Map<String, WeaverValue> facts, String key) { return facts.containsKey(key) ? Objects.toString(facts.get(key).payload().get("value"), "") : ""; }
    static Map<String, WeaverValue> inventoryFacts(ItemStack[] contents) {
        if (contents.length != 41) throw refused("ITEM_INVENTORY_UNAVAILABLE");
        final var encoded = Arrays.stream(contents).map(item -> item == null || item.isEmpty() ? "EMPTY" : WeaverItemSlots.fingerprint(item.serializeAsBytes())).toList();
        return Map.of(INVENTORY, scalar(WeaverItemSlots.fingerprint(CanonicalValueBytes.encode(Map.of("slots", encoded)))));
    }
    static String recordedInventoryFingerprint(List<String> inventory) {
        if (inventory.size() != 41) throw refused("ITEM_INVENTORY_UNAVAILABLE");
        final var digests = inventory.stream().map(value -> value.equals("-") ? "EMPTY"
                : WeaverItemSlots.fingerprint(Base64.getDecoder().decode(value))).toList();
        return WeaverItemSlots.fingerprint(CanonicalValueBytes.encode(Map.of("slots", digests)));
    }
    static String fingerprint(SubjectRef ref, Map<String, WeaverValue> facts) { return SCOPE.apply(new SubjectSnapshot(ref, 0, "item", facts)).revisionFingerprint(); }
    Map<String, WeaverValue> captureRecovery(RecoveryContext context) {
        context.authority().require(context.operation()); final var subject = subject(context.operation().subject(), context.operation().actorId());
        return inventoryFacts(owner(subject.holderId()).getInventory().getContents());
    }
    PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        return prepare(context, snapshot, request, Optional.empty());
    }
    PreparedAction undo(ProviderContext context, SubjectSnapshot snapshot, WeaverReceipt receipt) {
        context.authority().requireValid();
        final var undo = receipt.undo().orElseThrow(() -> refused("ITEM_UNDO_UNAVAILABLE"));
        if (!receipt.providerId().equals("item") || !receipt.receiptId().equals(receipt.operationId())
                || receipt.status() != ReceiptStatus.COMMITTED || !receipt.actionId().equals(undo.actionId())
                || receipt.integrityMode() != context.integrityMode() || receipt.lifetime() != context.lifetime()
                || !WeaverUndoSubject.resolve(receipt).equals(snapshot.ref())
                || !undo.expectedCurrentFingerprint().equals(snapshot.revisionFingerprint())) throw refused("CONFLICT");
        final ItemDeveloperReceipt nativeReceipt;
        try { nativeReceipt = nativeItems.inspect(receipt.operationId()).orElseThrow(() -> refused("ITEM_UNDO_RECEIPT_MISSING")); }
        catch (IllegalStateException unavailable) { throw refused("ITEM_NATIVE_UNAVAILABLE"); }
        if (nativeReceipt.kind() != KINDS.get(receipt.actionId()) || nativeReceipt.state() != ItemDeveloperReceipt.State.OBSERVED
                || nativeReceipt.reverses().isPresent()) throw refused("ITEM_UNDO_UNAVAILABLE");
        return prepare(context, snapshot, new ActionRequest(undo.actionId(), undo.parameters(), context.lifetime(), context.integrityMode()),
                Optional.of(receipt.operationId()));
    }
    private PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request, Optional<UUID> reverses) {
        context.authority().requireValid(); final var subject = subject(snapshot.ref(), context.authority().actor());
        final var descriptor = Optional.ofNullable(actions.get(request.actionId())).orElseThrow(() -> refused("ITEM_ACTION_UNAVAILABLE"));
        if (context.lifetime() != Lifetime.ONE_SHOT || request.lifetime() != Lifetime.ONE_SHOT || request.integrityMode() != context.integrityMode()
                || !descriptor.integrityModes().contains(request.integrityMode())) throw refused("ITEM_MODE_CONFLICT");
        final var actor = owner(subject.holderId()); slots.verify(actor, subject);
        final var before = inventoryFacts(actor.getInventory().getContents());
        if (!fingerprint(subject, before).equals(snapshot.revisionFingerprint())) throw refused("CONFLICT");
        final var mutation = request(descriptor, request, types);
        final UUID operation = UUID.randomUUID();
        final ItemDeveloperMutationRuntime.Plan plan;
        try { plan = reverses.isPresent() ? nativeItems.prepareReversalOnOwner(actor, subject.slot(), subject.fingerprint(), subject.revision().orElseThrow(),
                operation, reverses.orElseThrow(), request.integrityMode())
                : nativeItems.prepareOnOwner(actor, subject.slot(), subject.fingerprint(), subject.revision().orElseThrow(), operation, mutation, request.integrityMode()); }
        catch (IllegalStateException | IllegalArgumentException rejected) { throw refused("ITEM_NATIVE_PREPARATION_REFUSED"); }
        final var nativeReceipt = plan.receipt();
        final var recovery = new OperationRecoveryPayload(2, Map.of("kind", "itemization", "operation", operation.toString(),
                "native_kind", mutation.kind().name(), "result_item", nativeReceipt.resultItemId().toString(), "source", nativeReceipt.sourceSlot(),
                "target", nativeReceipt.targetSlot(), "before", scalarValue(before, INVENTORY), "reverses", reverses.map(UUID::toString).orElse("")));
        final var stage = new ExecutionStage("item.native_mutation", new EntityOwner(subject.holderId()), Map.of(), (execution, ignored) -> {
            execution.authority().requireValid();
            return coordinator.executeFromWorldWeaver(plan, execution.nativeEffects().orElseThrow()).thenApply(result -> {
                final var observed = observedFacts(result);
                return new StageResult(fingerprint(result.observedSubject().orElse(subject), observed), observed, Map.of());
            });
        }, Optional.empty(), 5000);
        return new PreparedAction(operation, descriptor, subject, snapshot.revisionFingerprint(), List.of(stage), recovery,
                (prepared, results, now) -> receipt(operation, descriptor, request, subject, before, results.getLast().facts(), reverses.isEmpty(), now));
    }
    private Map<String, WeaverValue> observedFacts(ItemDeveloperMutationRuntime.Result result) {
        if (result.receipt().state() != ItemDeveloperReceipt.State.OBSERVED) throw refused("ITEM_PROJECTION_PENDING");
        final var actor = owner(result.receipt().entry().playerId());
        if (!ItemDeveloperMutationRuntime.matches(actor.getInventory().getContents(), result.receipt().entry().afterInventory())) throw refused("ITEM_PROJECTION_CHANGED");
        final var facts = new HashMap<>(inventoryFacts(actor.getInventory().getContents()));
        if (result.observedSubject().isEmpty() && (result.receipt().reverses().isEmpty()
                || result.receipt().kind() != ItemDeveloperMutation.Kind.CLONE_PROTOTYPE)) throw refused("ITEM_RESULT_SUBJECT_MISSING");
        result.observedSubject().ifPresent(ref -> facts.put(AFTER_SUBJECT, new WeaverValue(WeaverUndoSubject.TYPE, SubjectKeyCodec.payload(ref), "item",
                ItemizationWeaverProvider.FACET, Set.of(WeaverUndoSubject.CAPABILITY), 0)));
        return Map.copyOf(facts);
    }
    PreparedEffects effects(ProviderContext context, PreparedAction prepared) {
        context.authority().requireValid();
        if (!actions.containsKey(prepared.descriptor().id()) || !prepared.operationId().toString().equals(prepared.recoveryPayload().fields().get("operation"))) throw refused("ITEM_EFFECT_PLAN_CONFLICT");
        return new PreparedEffects(new WeaverEffectIntent(Set.of(WeaverInfluenceTarget.exact(new RewardSource.Item(
                UUID.fromString((String) prepared.recoveryPayload().fields().get("result_item")))))), (action, results, receipt, sequence) -> WeaverEffectCommit.none());
    }
    static WeaverReceipt receipt(UUID operation, ActionDescriptor descriptor, ActionRequest request, SubjectRef subject,
                                 Map<String, WeaverValue> before, Map<String, WeaverValue> after, boolean offerUndo, long now) {
        final var resultSubject = after.containsKey(AFTER_SUBJECT) ? SubjectKeyCodec.decodePayload(after.get(AFTER_SUBJECT).payload()) : subject;
        final var afterFingerprint = fingerprint(resultSubject, after);
        if (offerUndo && descriptor.undoable() && !after.containsKey(AFTER_SUBJECT)) throw refused("ITEM_RESULT_SUBJECT_MISSING");
        return new WeaverReceipt(operation, operation, "item", descriptor.id(), subject, descriptor.risk(), Lifetime.ONE_SHOT, request.integrityMode(),
                fingerprint(subject, before), afterFingerprint, before, after,
                offerUndo && descriptor.undoable() ? Optional.of(new UndoSpec(descriptor.id(), afterFingerprint, request.parameters())) : Optional.empty(), now, ReceiptStatus.COMMITTED);
    }
    static Map<String, Object> recoveryFields(WeaverOperationRecord operation) {
        final var subject = subject(operation.subject(), operation.actorId());
        final var fields = new HashMap<>(operation.recoveryPayload().fields()); fields.remove(WeaverOperationScope.RESERVATIONS);
        final boolean legacy = operation.recoveryPayload().schemaVersion() == 1;
        if (legacy) {
            if (fields.containsKey("reverses") || operation.undoClaim().isPresent()) throw refused("ITEM_RECOVERY_PLAN_CONFLICT");
            fields.put("reverses", "");
        }
        if (!operation.providerId().equals("item") || !KINDS.containsKey(operation.request().actionId())
                || !descriptor(KINDS.get(operation.request().actionId())).integrityModes().contains(operation.request().integrityMode())
                || operation.request().lifetime() != Lifetime.ONE_SHOT || !legacy && operation.recoveryPayload().schemaVersion() != 2
                || !fields.keySet().equals(Set.of("kind", "operation", "native_kind", "result_item", "source", "target", "before", "reverses"))
                || !(fields.get("reverses") instanceof String inverse)
                || !inverse.equals(operation.undoClaim().map(claim -> claim.receiptId().toString()).orElse(""))
                || operation.undoClaim().isPresent() && (!descriptor(KINDS.get(operation.request().actionId())).undoable()
                    || !operation.undoClaim().orElseThrow().expectedFingerprint().equals(operation.beforeFingerprint())
                    || operation.undoClaim().orElseThrow().receiptId().equals(operation.operationId()))
                || !"itemization".equals(fields.get("kind")) || !operation.operationId().toString().equals(fields.get("operation"))
                || !KINDS.get(operation.request().actionId()).name().equals(fields.get("native_kind"))
                || !(fields.get("before") instanceof String before) || !before.matches("[a-f0-9]{64}")
                || !fingerprint(subject, Map.of(INVENTORY, scalar(before))).equals(operation.beforeFingerprint())) throw refused("ITEM_RECOVERY_PLAN_CONFLICT");
        if (!(fields.get("result_item") instanceof String result) || !UUID.fromString(result).toString().equals(result)) throw refused("ITEM_RECOVERY_PLAN_CONFLICT");
        for (String key : List.of("source", "target")) {
            final var value = fields.get(key);
            if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0 || ((Number) value).longValue() > 40) throw refused("ITEM_RECOVERY_PLAN_CONFLICT");
        }
        final int source = ((Number) fields.get("source")).intValue(), target = ((Number) fields.get("target")).intValue();
        if (subject.slot().kind() != WeaverSlot.Kind.MAIN_HAND && !fixedSlot(source).equals(subject.slot())) throw refused("ITEM_RECOVERY_SLOT_CONFLICT");
        if (subject.slot().kind() == WeaverSlot.Kind.MAIN_HAND && source > 8) throw refused("ITEM_RECOVERY_SLOT_CONFLICT");
        if (operation.undoClaim().isEmpty() && KINDS.get(operation.request().actionId()) == ItemDeveloperMutation.Kind.CLONE_PROTOTYPE
                ? source == target || target > 35 || result.equals(subject.instanceId().orElseThrow().toString())
                : source != target || !result.equals(subject.instanceId().orElseThrow().toString())) throw refused("ITEM_RECOVERY_IDENTITY_CONFLICT");
        return Map.copyOf(fields);
    }
    RecoveryAssessment assess(RecoveryContext context, SubjectSnapshot snapshot, WeaverOperationRecord operation) {
        context.authority().require(operation);
        try {
            final var fields = recoveryFields(operation); final var subject = subject(operation.subject(), operation.actorId());
            request(actions.get(operation.request().actionId()), operation.request(), types);
            if (!snapshot.ref().equals(subject)) return conflict("ITEM_RECOVERY_SUBJECT_CONFLICT");
            final var before = Map.of(INVENTORY, scalar((String) fields.get("before")));
            final Optional<ItemDeveloperReceipt> found;
            try { found = nativeItems.inspect(operation.operationId()); }
            catch (IllegalStateException unavailable) { throw refused("NATIVE_PROJECTION_PENDING"); }
            if (found.isEmpty()) return snapshot.revisionFingerprint().equals(operation.beforeFingerprint())
                    ? new RecoveryAssessment(ObservedOperationState.BEFORE, false, Optional.empty(), "ITEM_NATIVE_BEFORE_OBSERVED") : conflict("ITEM_NATIVE_RECEIPT_MISSING");
            final var nativeReceipt = found.orElseThrow();
            if (!nativeReceipt.entry().operationId().equals(operation.operationId())
                    || !nativeReceipt.entry().playerId().equals(operation.actorId()) || !nativeReceipt.entry().itemId().equals(subject.instanceId().orElseThrow())
                    || nativeReceipt.beforeRevision() != subject.revision().orElseThrow() || !nativeReceipt.kind().name().equals(fields.get("native_kind"))
                    || !nativeReceipt.resultItemId().toString().equals(fields.get("result_item"))
                    || nativeReceipt.sourceSlot() != ((Number) fields.get("source")).intValue()
                    || nativeReceipt.targetSlot() != ((Number) fields.get("target")).intValue()
                    || !nativeReceipt.reverses().map(UUID::toString).orElse("").equals(fields.get("reverses"))
                    || !recordedInventoryFingerprint(nativeReceipt.entry().beforeInventory()).equals(fields.get("before"))
                    || !WeaverItemSlots.fingerprint(Base64.getDecoder().decode(nativeReceipt.entry().beforeInventory().get(nativeReceipt.sourceSlot()))).equals(subject.fingerprint()))
                return conflict("ITEM_NATIVE_RECEIPT_CONFLICT");
            final var actor = owner(subject.holderId()); final var inventory = actor.getInventory().getContents();
            if (nativeReceipt.state() == ItemDeveloperReceipt.State.PENDING) {
                if (ItemDeveloperMutationRuntime.matches(inventory, nativeReceipt.entry().beforeInventory())
                        || ItemDeveloperMutationRuntime.matches(inventory, nativeReceipt.entry().afterInventory())) throw refused("NATIVE_PROJECTION_PENDING");
                return conflict("ITEM_NATIVE_PROJECTION_CONFLICT");
            }
            if (nativeReceipt.state() == ItemDeveloperReceipt.State.ABORTED) return ItemDeveloperMutationRuntime.matches(inventory, nativeReceipt.entry().beforeInventory())
                    ? new RecoveryAssessment(ObservedOperationState.BEFORE, false, Optional.empty(), "ITEM_NATIVE_ABORT_OBSERVED") : conflict("ITEM_NATIVE_ABORT_CONFLICT");
            if (!ItemDeveloperMutationRuntime.matches(inventory, nativeReceipt.entry().afterInventory())) return conflict("ITEM_NATIVE_AFTER_CHANGED");
            final var resultSubject = nativeReceipt.reverses().isPresent() && nativeReceipt.kind() == ItemDeveloperMutation.Kind.CLONE_PROTOTYPE
                    ? Optional.<ItemSlotRef>empty() : Optional.of(slots.capture(actor, fixedSlot(nativeReceipt.targetSlot())));
            final var after = observedFacts(new ItemDeveloperMutationRuntime.Result(nativeReceipt, resultSubject));
            final var recovered = receipt(operation.operationId(), actions.get(operation.request().actionId()), operation.request(),
                    subject, before, after, operation.undoClaim().isEmpty() && operation.recoveryPayload().schemaVersion() >= 2,
                    operation.receipt().map(WeaverReceipt::createdAt).orElseGet(() -> Math.max(System.currentTimeMillis(), operation.preparedAt())));
            if (operation.receipt().isPresent() && !operation.receipt().orElseThrow().equals(recovered)) return conflict("ITEM_WW_RECEIPT_CHANGED");
            return new RecoveryAssessment(ObservedOperationState.APPLIED, false, Optional.of(recovered), "ITEM_NATIVE_RECEIPT_AND_PROJECTION_OBSERVED");
        } catch (WeaverDomainRejection unavailable) {
            if (Set.of("NATIVE_PROJECTION_PENDING", "OWNER_UNAVAILABLE", "PLAYER_UNAVAILABLE").contains(unavailable.code())) throw unavailable;
            return conflict("ITEM_RECOVERY_CONFLICT");
        } catch (RuntimeException invalid) { return conflict("ITEM_RECOVERY_UNAVAILABLE"); }
    }
    private static WeaverSlot fixedSlot(int index) {
        return switch (index) {
            case 36 -> WeaverSlot.named(WeaverSlot.Kind.BOOTS); case 37 -> WeaverSlot.named(WeaverSlot.Kind.LEGGINGS);
            case 38 -> WeaverSlot.named(WeaverSlot.Kind.CHESTPLATE); case 39 -> WeaverSlot.named(WeaverSlot.Kind.HELMET);
            case 40 -> WeaverSlot.named(WeaverSlot.Kind.OFF_HAND); default -> new WeaverSlot(WeaverSlot.Kind.INVENTORY, index);
        };
    }
    private static RecoveryAssessment conflict(String reason) { return new RecoveryAssessment(ObservedOperationState.PARTIAL_OR_CONFLICT, false, Optional.empty(), reason); }
    private static WeaverDomainRejection refused(String reason) { return new WeaverDomainRejection(reason); }
}
