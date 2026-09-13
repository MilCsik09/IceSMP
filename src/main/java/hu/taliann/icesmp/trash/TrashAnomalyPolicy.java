package hu.taliann.icesmp.trash;

/** Pure invariants shared by the Phase D runtime and its behavioral regression. */
final class TrashAnomalyPolicy {

    private TrashAnomalyPolicy() { }

    static boolean blocksGroundMerge(final boolean sourceHasIndividualRuntimeState,
                                     final boolean targetHasIndividualRuntimeState) {
        return sourceHasIndividualRuntimeState || targetHasIndividualRuntimeState;
    }

    static OppositePoint oppositePoint(final double playerX, final double playerZ,
                                       final double targetX, final double targetZ,
                                       final double projectionDistance) {
        final double dx = targetX - playerX;
        final double dz = targetZ - playerZ;
        final double length = Math.hypot(dx, dz);
        if (!Double.isFinite(length) || length < 0.01D
                || !Double.isFinite(projectionDistance) || projectionDistance <= 0.0D) return null;
        return new OppositePoint(playerX - dx / length * projectionDistance,
                playerZ - dz / length * projectionDistance);
    }

    record OppositePoint(double x, double z) { }
}
