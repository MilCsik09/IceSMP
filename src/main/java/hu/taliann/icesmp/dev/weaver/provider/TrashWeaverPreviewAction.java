package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.execution.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import hu.taliann.icesmp.trash.TrashAnomalyActivationService;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Reuses the native immediate Anomaly presentation route for owned sandbox copies. */
final class TrashWeaverPreviewAction {
    static final String ID = "trash.preview_anomaly";
    private final WeaverItemSlots slots;
    private final TrashAnomalyActivationService activation;
    TrashWeaverPreviewAction(WeaverItemSlots slots, TrashAnomalyActivationService activation) { this.slots = slots; this.activation = activation; }
    ActionDescriptor descriptor() { return new ActionDescriptor(ID, TrashWeaverProvider.FACET, Component.text("Sandbox Anomaly viselkedéspróba"), RiskLevel.SAFE,
            Set.of(Lifetime.ONE_SHOT), Set.of(IntegrityMode.SANDBOX), Set.of(IntegrityImpact.NONE), Set.of(WeaverSubjectKind.ITEM_SLOT), List.of(), AreaSupport.NONE, Optional.empty(), false, Optional.empty(), 1); }
    boolean visible(SubjectSnapshot snapshot) {
        final var behavior = snapshot.facts().get("trash.behavior");
        return snapshot.ref() instanceof ItemSlotRef item && item.slot().kind() == WeaverSlot.Kind.OFF_HAND && snapshot.facts().containsKey("trash.prototype_owner")
                && behavior != null && behavior.payload().get("value") instanceof String raw && TrashAnomalyActivationService.sandboxPreviewable(raw);
    }
    PreparedAction prepare(ProviderContext context, SubjectSnapshot snapshot, ActionRequest request) {
        context.authority().requireValid();
        if (!ID.equals(request.actionId()) || !visible(snapshot) || context.integrityMode() != IntegrityMode.SANDBOX || request.integrityMode() != IntegrityMode.SANDBOX
                || context.lifetime() != Lifetime.ONE_SHOT || request.lifetime() != Lifetime.ONE_SHOT || !request.parameters().isEmpty()) throw new WeaverDomainRejection("PREVIEW_UNAVAILABLE");
        final ItemSlotRef item = (ItemSlotRef) snapshot.ref(); final var action = descriptor();
        final var stage = new ExecutionStage("trash.preview.native", new EntityOwner(item.holderId()), Map.of(), (execution, payload) -> {
            execution.authority().requireValid(); final var player = org.bukkit.Bukkit.getPlayer(item.holderId());
            if (player == null || !org.bukkit.Bukkit.isOwnedByCurrentRegion(player)) throw new WeaverDomainRejection("OWNER_UNAVAILABLE");
            slots.verify(player, item);
            final var result = activation.sandboxPreviewOnOwner(player, player.getInventory().getItemInOffHand().clone(), () -> { execution.authority().requireValid(); return true; });
            if (result == TrashAnomalyActivationService.Result.REFUSED || result == TrashAnomalyActivationService.Result.IGNORED) throw new WeaverDomainRejection("PREVIEW_UNAVAILABLE");
            return CompletableFuture.completedFuture(new StageResult(snapshot.revisionFingerprint(), Map.of(), Map.of()));
        }, Optional.empty(), 5000);
        return new PreparedAction(UUID.randomUUID(), action, item, snapshot.revisionFingerprint(), List.of(stage), new OperationRecoveryPayload(1, Map.of()),
                (prepared, results, time) -> new WeaverReceipt(UUID.randomUUID(), prepared.operationId(), "trash", ID, item, action.risk(), request.lifetime(), request.integrityMode(),
                        snapshot.revisionFingerprint(), snapshot.revisionFingerprint(), Map.of(), Map.of(), Optional.empty(), time, ReceiptStatus.COMMITTED));
    }
}
