package hu.taliann.icesmp.dev.weaver.api;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import net.kyori.adventure.text.Component;

public record ActionParameter(String id, Component label, WeaverTypeId type, InputKind input,
                              boolean required, Optional<WeaverValue> defaultValue,
                              OptionalDouble minimum, OptionalDouble maximum, OptionalInt maxTextLength,
                              Optional<String> catalogId, Set<String> requiredCapabilities) {
    public enum InputKind { BOOLEAN, INTEGER, DECIMAL, TEXT, CATALOG, SUBJECT, AREA, THREAD }
    public ActionParameter {
        WeaverIds.parameter(id); java.util.Objects.requireNonNull(label); java.util.Objects.requireNonNull(type);
        java.util.Objects.requireNonNull(input); java.util.Objects.requireNonNull(defaultValue);
        java.util.Objects.requireNonNull(minimum); java.util.Objects.requireNonNull(maximum); java.util.Objects.requireNonNull(maxTextLength);
        java.util.Objects.requireNonNull(catalogId); catalogId.ifPresent(WeaverIds::descriptor);
        requiredCapabilities = Set.copyOf(requiredCapabilities); requiredCapabilities.forEach(WeaverIds::descriptor);
        if (requiredCapabilities.size() > 32) throw new IllegalArgumentException("Parameter capability cap");
        if (defaultValue.isPresent() && !defaultValue.get().type().equals(type)) throw new IllegalArgumentException("Default parameter type mismatch");
        if (input == InputKind.INTEGER || input == InputKind.DECIMAL) {
            if (minimum.isEmpty() || maximum.isEmpty() || !Double.isFinite(minimum.getAsDouble()) || !Double.isFinite(maximum.getAsDouble())
                    || minimum.getAsDouble() > maximum.getAsDouble()) throw new IllegalArgumentException("Numeric parameter requires finite bounds");
        }
        if (input == InputKind.TEXT && (maxTextLength.isEmpty() || maxTextLength.getAsInt() < 1 || maxTextLength.getAsInt() > 256)) {
            throw new IllegalArgumentException("Text parameter requires a 1..256 character constraint");
        }
        if (input == InputKind.CATALOG && catalogId.isEmpty()) throw new IllegalArgumentException("Catalog parameter requires registry catalog");
    }
    public ValidationResult validate(final WeaverValue value, final WeaverTypeRegistry types) {
        if (!types.compatible(value, type, requiredCapabilities)) return ValidationResult.rejected("PARAMETER_TYPE_OR_CAPABILITY");
        final Object raw = value.payload().get("value");
        if (input == InputKind.INTEGER || input == InputKind.DECIMAL) {
            if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || new java.math.BigDecimal(number.toString()).compareTo(java.math.BigDecimal.valueOf(minimum.orElseThrow())) < 0
                    || new java.math.BigDecimal(number.toString()).compareTo(java.math.BigDecimal.valueOf(maximum.orElseThrow())) > 0
                    || (input == InputKind.INTEGER && !(raw instanceof Integer || raw instanceof Long))) return ValidationResult.rejected("PARAMETER_BOUNDS");
        }
        if (input == InputKind.TEXT && (!(raw instanceof String text) || text.length() > maxTextLength.orElseThrow()
                || text.codePoints().anyMatch(Character::isISOControl))) return ValidationResult.rejected("PARAMETER_TEXT");
        return ValidationResult.accepted();
    }
}
