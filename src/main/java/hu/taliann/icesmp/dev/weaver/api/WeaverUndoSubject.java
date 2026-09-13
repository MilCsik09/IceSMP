package hu.taliann.icesmp.dev.weaver.api;

import hu.taliann.icesmp.dev.weaver.subject.SubjectKeyCodec;
import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;
import java.util.Map;

/** A changed item revision or created entity must be named by immutable, provider-owned after evidence. */
public final class WeaverUndoSubject {
    public static final String CAPABILITY = "weaver.undo_target";
    public static final WeaverTypeId TYPE = new WeaverTypeId("weaver", "subject_ref", 1);
    private WeaverUndoSubject() { }
    public static SubjectRef resolve(final WeaverReceipt receipt) {
        return resolve(receipt.providerId(), receipt.subject(), receipt.after());
    }
    static SubjectRef resolve(final String provider, final SubjectRef original, final Map<String, WeaverValue> after) {
        SubjectRef target = original; boolean declared = false;
        for (final var entry : after.entrySet()) {
            final WeaverValue value = entry.getValue();
            if (!value.sourceCapabilities().contains(CAPABILITY)) continue;
            if (declared || !entry.getKey().startsWith(provider + ".") || !value.sourceProvider().equals(provider)
                    || !value.sourceFacet().startsWith(provider + ".") || !value.type().equals(TYPE)) throw new IllegalArgumentException("Invalid Undo target evidence");
            target = SubjectKeyCodec.decodePayload(value.payload()); declared = true;
        }
        return target;
    }
}
