package hu.taliann.icesmp.dev.weaver.api;

import java.util.List;

public record ValidationResult(boolean valid, List<String> errors) {
    public ValidationResult {
        errors = List.copyOf(errors);
        if (errors.size() > 16 || errors.stream().anyMatch(s -> s == null || s.length() > 256)
                || valid != errors.isEmpty()) throw new IllegalArgumentException("Invalid validation result");
    }
    public static ValidationResult accepted() { return new ValidationResult(true, List.of()); }
    public static ValidationResult rejected(final String code) { return new ValidationResult(false, List.of(code)); }
    public void requireValid() { if (!valid) throw new IllegalArgumentException(String.join(", ", errors)); }
}
