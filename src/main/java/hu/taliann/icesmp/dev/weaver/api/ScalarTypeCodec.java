package hu.taliann.icesmp.dev.weaver.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

public final class ScalarTypeCodec implements WeaverTypeCodec {
    public enum Shape { BOOLEAN, INT, DOUBLE, DURATION_TICKS, UUID, TEXT, REFERENCE }
    private final WeaverTypeId type;
    private final Shape shape;
    private final Predicate<String> knownReference;
    public ScalarTypeCodec(final WeaverTypeId type, final Shape shape, final Predicate<String> knownReference) {
        this.type = Objects.requireNonNull(type); this.shape = Objects.requireNonNull(shape);
        this.knownReference = Objects.requireNonNull(knownReference);
    }
    public static ScalarTypeCodec reference(final WeaverTypeId type, final Predicate<String> exists) {
        return new ScalarTypeCodec(type, Shape.REFERENCE, exists);
    }
    public Shape shape() { return shape; }
    @Override public java.util.Set<ActionParameter.InputKind> supportedInputs() {
        final ActionParameter.InputKind input = switch (shape) {
            case BOOLEAN -> ActionParameter.InputKind.BOOLEAN;
            case INT, DURATION_TICKS -> ActionParameter.InputKind.INTEGER;
            case DOUBLE -> ActionParameter.InputKind.DECIMAL;
            case UUID, TEXT -> ActionParameter.InputKind.TEXT;
            case REFERENCE -> ActionParameter.InputKind.CATALOG;
        };
        return java.util.Set.of(input, ActionParameter.InputKind.THREAD);
    }
    @Override public WeaverTypeId type() { return type; }
    @Override public ValidationResult validate(final Map<String, Object> payload) {
        final String key = shape == Shape.REFERENCE ? "id" : "value";
        if (payload.size() != 1 || !payload.containsKey(key)) return ValidationResult.rejected("INVALID_VALUE_SHAPE");
        final Object value = payload.get(key);
        boolean valid;
        try {
            valid = switch (shape) {
                case BOOLEAN -> value instanceof Boolean;
                case INT -> (value instanceof Integer || value instanceof Long) && ((Number) value).longValue() >= Integer.MIN_VALUE
                        && ((Number) value).longValue() <= Integer.MAX_VALUE;
                case DOUBLE -> value instanceof Double number && Double.isFinite(number);
                case DURATION_TICKS -> (value instanceof Integer || value instanceof Long) && ((Number) value).longValue() >= 0;
                case UUID -> value instanceof String text && UUID.fromString(text).toString().equals(text);
                case TEXT -> value instanceof String text && text.length() <= 4096 && text.codePoints().noneMatch(c -> Character.isISOControl(c) && c != '\n');
                case REFERENCE -> value instanceof String text && WeaverIds.content(text).equals(text) && knownReference.test(text);
            };
        } catch (final RuntimeException invalid) { valid = false; }
        return valid ? ValidationResult.accepted() : ValidationResult.rejected("INVALID_TYPED_VALUE");
    }
    @Override public byte[] canonicalBytes(final Map<String, Object> payload) {
        validate(payload).requireValid(); return CanonicalValueBytes.encode(payload);
    }
    public static void registerBuiltins(final WeaverTypeRegistry registry) {
        for (final Shape shape : Shape.values()) {
            if (shape != Shape.REFERENCE) registry.register(new ScalarTypeCodec(new WeaverTypeId("weaver", shape.name().toLowerCase(java.util.Locale.ROOT), 1), shape, id -> false));
        }
        for (final String kind : java.util.List.of("subject_ref", "location", "area")) registry.register(new SubjectTypeCodec(kind));
        registry.register(new ProjectionReferenceTypeCodec());
    }
}
