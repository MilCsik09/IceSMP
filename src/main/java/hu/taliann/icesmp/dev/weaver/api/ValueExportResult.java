package hu.taliann.icesmp.dev.weaver.api;

import java.util.Optional;

public record ValueExportResult(Optional<WeaverValue> value, String reason) {
    public ValueExportResult {
        java.util.Objects.requireNonNull(value);
        if (reason == null || reason.length() > 256 || (value.isEmpty() && reason.isBlank())) throw new IllegalArgumentException("Invalid export result");
    }
    public static ValueExportResult exported(final WeaverValue value) { return new ValueExportResult(Optional.of(value), ""); }
    public static ValueExportResult rejected(final String reason) { return new ValueExportResult(Optional.empty(), reason); }
}
