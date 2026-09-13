package hu.taliann.icesmp.dev.weaver.gui;

import hu.taliann.icesmp.dev.weaver.api.*;
import java.util.Map;
import java.util.Set;

/** Manual input creates primitive values only; source capabilities must come from real exports/catalogs. */
public final class WeaverParameterInput {
    private WeaverParameterInput() {}
    public static WeaverValue parse(final ActionParameter parameter, final String text, final WeaverTypeRegistry types) {
        if (text == null || text.length() > 256 || text.codePoints().anyMatch(Character::isISOControl)) throw new WeaverDomainRejection("INVALID_PARAMETER_INPUT");
        final Object raw;
        try {
            raw = switch (parameter.input()) {
                case BOOLEAN -> {
                    if (!text.equals("true") && !text.equals("false")) throw new IllegalArgumentException("Invalid boolean");
                    yield Boolean.parseBoolean(text);
                }
                case INTEGER -> Long.parseLong(text);
                case DECIMAL -> Double.parseDouble(text);
                case TEXT -> text;
                default -> throw new WeaverDomainRejection("TYPED_SELECTION_REQUIRED");
            };
        } catch (final NumberFormatException invalid) { throw new WeaverDomainRejection("INVALID_PARAMETER_NUMBER"); }
        final WeaverValue value = new WeaverValue(parameter.type(), Map.of("value", raw), "weaver", "weaver.input", Set.of(), System.currentTimeMillis());
        parameter.validate(value, types).requireValid(); return value;
    }
}
