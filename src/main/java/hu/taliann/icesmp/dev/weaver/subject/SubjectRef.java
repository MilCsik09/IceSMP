package hu.taliann.icesmp.dev.weaver.subject;

public sealed interface SubjectRef permits PlayerRef, EntityRef, ItemSlotRef, BlockRef, LocationRef, AreaRef, WorldRef {
    WeaverSubjectKind kind();
    default String stableKey() { return SubjectKeyCodec.encode(this); }
}
