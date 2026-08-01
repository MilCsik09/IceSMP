package hu.taliann.icesmp.classspec.application;

import hu.taliann.icesmp.classspec.domain.LoadoutSlot;
import hu.taliann.icesmp.classspec.domain.SealReason;
import hu.taliann.icesmp.classspec.domain.CompanionProfile;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

/**
 * Bukkit-free facade consumed by legacy managers while Profile v2 is rolled
 * out behind its feature flag. Synchronous reads are cache-only; every critical
 * mutation is asynchronous and represents a durable CAS attempt.
 */
public interface ClassSpecProfileGateway {

    boolean enabled();

    boolean isSessionReady(UUID playerId);

    /** Blocks gameplay reads while a loaded profile is still being gate-checked and rebuilt. */
    void beginSessionActivation(UUID playerId);

    /** Opens gameplay reads only after gate reconcile and runtime rebuild both succeeded. */
    void completeSessionActivation(UUID playerId);

    /** Removes an abandoned activation only when its session generation actually ended. */
    void cancelSessionActivation(UUID playerId);

    Optional<String> activeSpecId(UUID playerId);

    Optional<String> activeMechanic(UUID playerId, String key);

    Optional<CompanionProfile> activeCompanion(UUID playerId);

    ProfileDiagnostic diagnostic(UUID playerId);

    /** Fail-closes class/spec runtime after an external post-commit mirror/economy error. */
    void blockSession(UUID playerId, String reason);

    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> select(
            UUID playerId,
            SelectRequest request);

    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> reset(
            UUID playerId,
            ResetRequest request);

    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> seal(
            UUID playerId,
            SealRequest request);

    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> reconcile(
            UUID playerId,
            ReconcileRequest request);

    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> assignClass(
            UUID playerId,
            ClassAssignmentRequest request);

    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> mirrorClassLevel(
            UUID playerId,
            int classLevel);

    CompletionStage<ProfileMutationResult<ProfileDiagnostic>> incrementSoulforge(
            UUID playerId,
            String branch,
            String operationId);

    /** Closes mutation admission and completes after every already-admitted mutation. */
    default CompletionStage<Void> prepareShutdown() {
        return CompletableFuture.completedFuture(null);
    }

    record ClassAssignmentRequest(String classId, int classLevel, String operationId) {
        public ClassAssignmentRequest {
            classId = requireId(classId, "classId");
            operationId = requireId(operationId, "operationId");
            if (classLevel < 1) {
                throw new IllegalArgumentException("classLevel must be positive");
            }
        }
    }

    /** Drops session-only receipts; durable profile/cache cleanup belongs to the store. */
    void clearSession(UUID playerId);

    record SelectRequest(String specializationId, LoadoutSlot slot, GateSnapshot gates) {
        public SelectRequest {
            specializationId = requireId(specializationId, "specializationId");
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(gates, "gates");
        }
    }

    record ResetRequest(ResetMode mode, Optional<LoadoutSlot> slot, String operationId) {
        public ResetRequest {
            Objects.requireNonNull(mode, "mode");
            slot = slot == null ? Optional.empty() : slot;
            operationId = requireId(operationId, "operationId");
            if (mode == ResetMode.LOADOUT_RESPEC && slot.isEmpty()) {
                throw new IllegalArgumentException("A loadout respec requires a slot");
            }
            if (mode == ResetMode.ADMIN_CLASS && slot.isPresent()) {
                throw new IllegalArgumentException("An admin class reset cannot target one slot");
            }
        }
    }

    record SealRequest(LoadoutSlot slot, SealReason reason) {
        public SealRequest {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Complete gate snapshot for every loadout being reconciled. The copy is
     * immutable so it can safely cross from a player region to an I/O executor.
     */
    record ReconcileRequest(Map<LoadoutSlot, GateSnapshot> gatesBySlot) {
        public ReconcileRequest {
            Objects.requireNonNull(gatesBySlot, "gatesBySlot");
            final EnumMap<LoadoutSlot, GateSnapshot> copy = new EnumMap<>(LoadoutSlot.class);
            for (final Map.Entry<LoadoutSlot, GateSnapshot> entry : gatesBySlot.entrySet()) {
                copy.put(Objects.requireNonNull(entry.getKey(), "gate slot"),
                        Objects.requireNonNull(entry.getValue(), "gate state"));
            }
            gatesBySlot = Collections.unmodifiableMap(copy);
        }
    }

    enum ResetMode {
        LOADOUT_RESPEC,
        ADMIN_CLASS
    }

    private static String requireId(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value.trim();
    }
}
