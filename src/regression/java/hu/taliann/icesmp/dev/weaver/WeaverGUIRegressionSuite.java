package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.dev.weaver.gui.*;
import hu.taliann.icesmp.dev.weaver.subject.*;
import net.kyori.adventure.text.Component;
import java.util.*;

public final class WeaverGUIRegressionSuite {
    public static void main(final String[] args) {
        final WeaverTypeRegistry types = new WeaverTypeRegistry(); ScalarTypeCodec.registerBuiltins(types);
        final ActionParameter number = new ActionParameter("number", Component.text("number"), WeaverTypeId.parse("weaver:int@1"), ActionParameter.InputKind.INTEGER,
                true, Optional.empty(), OptionalDouble.of(-5), OptionalDouble.of(10), OptionalInt.empty(), Optional.empty(), Set.of());
        check(WeaverParameterInput.parse(number, "10", types).payload().get("value").equals(10L), "integer parameter parsing");
        rejects(() -> WeaverParameterInput.parse(number, "11", types)); rejects(() -> WeaverParameterInput.parse(number, "1.5", types));
        rejects(() -> WeaverParameterInput.parse(number, "2147483648", types)); rejects(() -> WeaverParameterInput.parse(number, "7\n", types));
        final ActionParameter reference = new ActionParameter("number", Component.text("number"), number.type(), number.input(), true, Optional.empty(),
                number.minimum(), number.maximum(), OptionalInt.empty(), Optional.empty(), Set.of("fixture.real_source"));
        rejects(() -> WeaverParameterInput.parse(reference, "5", types));
        final ActionDescriptor base = WeaverContractRegressionSuite.safe("fixture");
        final ActionDescriptor descriptor = new ActionDescriptor(base.id(), base.facetId(), base.label(), base.risk(), base.lifetimes(), base.integrityModes(), base.integrityImpacts(),
                base.subjects(), List.of(number), base.areaSupport(), base.areaLimits(), base.undoable(), base.irreversibleReason(), base.rateCost());
        final SubjectSnapshot snapshot = new SubjectSnapshot(new PlayerRef(UUID.randomUUID()), 0, "revision", Map.of());
        final WeaverActionDraft initial = WeaverActionDraft.start(descriptor, snapshot, IntegrityMode.SANDBOX);
        check(!initial.validate(types).valid(), "missing required parameter accepted");
        final WeaverActionDraft ready = initial.with("number", WeaverParameterInput.parse(number, "7", types), types);
        check(ready.validate(types).valid() && !initial.id().equals(ready.id()) && initial.parameters().isEmpty(), "draft mutation reused stale nonce/state");
        rejects(() -> ready.parameters().put("number", WeaverParameterInput.parse(number, "1", types)));
        final List<WeaverView.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 90; i++) entries.add(new WeaverView.Entry(Component.text("entry " + i), List.of(Component.text("x".repeat(400))), "PAPER", new WeaverNavigation.None()));
        final WeaverView view = new WeaverView(UUID.randomUUID(), 3, WeaverViewKind.FACET, Component.text("Fixture"), entries, Map.of(), 1);
        check(view.at(0).orElseThrow().label().equals(Component.text("entry 45")) && view.at(44).isPresent(), "generic pagination lost descriptor entries");
        check(view.at(-1).isEmpty() && view.at(54).isEmpty(), "foreign inventory slot became navigation");
        check(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(view.at(0).orElseThrow().lore().getFirst()).length() == 256, "unbounded GUI detail");
        final String emoji = "\uD83E\uDDF5".repeat(129);
        final WeaverView.Entry unicode = new WeaverView.Entry(Component.text(emoji), List.of(), "STRING", new WeaverNavigation.None());
        check(unicode.label().equals(Component.text("\uD83E\uDDF5".repeat(128))), "GUI clipped a Unicode surrogate pair");
        rejects(() -> new WeaverView(UUID.randomUUID(), 1, WeaverViewKind.SUBJECT, Component.empty(), entries, Map.of(), 2));
        System.out.println("Weaver GUI data/input regression passed; native connected-client confirmation remains a live evidence gate.");
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(final Runnable action) { WeaverTypeCompatibilityRegressionSuite.rejects(action); }
}
