package hu.taliann.icesmp.dev.weaver.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class WeaverTypeRegistry {
    private final Map<WeaverTypeId, WeaverTypeCodec> codecs = new LinkedHashMap<>();
    private boolean frozen;
    public synchronized void register(final WeaverTypeCodec codec) {
        Objects.requireNonNull(codec);
        if (frozen || codecs.size() >= 256 || codecs.putIfAbsent(codec.type(), codec) != null) {
            throw new IllegalStateException("Weaver type registration closed, duplicate or full");
        }
    }
    public synchronized WeaverTypeCodec require(final WeaverTypeId type) {
        final WeaverTypeCodec codec = codecs.get(type);
        if (codec == null) throw new IllegalArgumentException("Unknown Weaver type/schema: " + type.canonical());
        return codec;
    }
    public synchronized boolean contains(final WeaverTypeId type) { return codecs.containsKey(type); }
    public synchronized Map<WeaverTypeId, WeaverTypeCodec> snapshot() { return Map.copyOf(codecs); }
    public synchronized void freeze() { frozen = true; }
    public void validate(final WeaverValue value) { require(value.type()).validate(value.payload()).requireValid(); }
    public boolean compatible(final WeaverValue value, final WeaverTypeId accepted, final java.util.Set<String> required) {
        return value.type().equals(accepted) && value.sourceCapabilities().containsAll(required)
                && require(accepted).validate(value.payload()).valid();
    }
}
