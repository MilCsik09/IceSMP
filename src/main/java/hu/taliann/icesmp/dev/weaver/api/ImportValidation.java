package hu.taliann.icesmp.dev.weaver.api;

import java.util.List;

public record ImportValidation(boolean compatible, List<String> reasons) {
    public ImportValidation {
        reasons = List.copyOf(reasons);
        if (reasons.size() > 16 || reasons.stream().anyMatch(s -> s.isBlank() || s.length() > 256)
                || compatible != reasons.isEmpty()) throw new IllegalArgumentException("Invalid import validation");
    }
    public static ImportValidation accepted() { return new ImportValidation(true, List.of()); }
    public static ImportValidation rejected(final String reason) { return new ImportValidation(false, List.of(reason)); }
}
