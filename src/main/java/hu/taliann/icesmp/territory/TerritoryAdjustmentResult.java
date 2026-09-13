package hu.taliann.icesmp.territory;

import java.util.Objects;
import java.util.Optional;

public record TerritoryAdjustmentResult(Status status, Optional<TerritoryAdjustmentReceipt> receipt) {
    public enum Status { APPLIED, ALREADY_APPLIED, CONFLICT, UNKNOWN_TERRITORY, CAPITAL_CONFLICT, CAPACITY, NO_CHANGE, DENIED }
    public TerritoryAdjustmentResult {
        Objects.requireNonNull(status); Objects.requireNonNull(receipt);
        if (receipt.isPresent() != (status == Status.APPLIED || status == Status.ALREADY_APPLIED)) {
            throw new IllegalArgumentException("Receipt does not match adjustment outcome");
        }
    }
    public static TerritoryAdjustmentResult rejected(Status status) { return new TerritoryAdjustmentResult(status, Optional.empty()); }
}
