package hu.taliann.icesmp.dev.weaver.gui;

import hu.taliann.icesmp.dev.weaver.subject.SubjectRef;
import java.util.UUID;

public sealed interface WeaverNavigation {
    record None() implements WeaverNavigation {}
    record Refresh() implements WeaverNavigation {}
    record Facet(String id) implements WeaverNavigation {}
    record Action(String id) implements WeaverNavigation {}
    record Export(String id) implements WeaverNavigation {}
    record Catalog(String id, int offset) implements WeaverNavigation {}
    record Threads() implements WeaverNavigation {}
    record Recent() implements WeaverNavigation {}
    record SelectThread(UUID id) implements WeaverNavigation {}
    record Subject(SubjectRef ref) implements WeaverNavigation {}
    record Page(int index) implements WeaverNavigation {}
    record Diagnostics() implements WeaverNavigation {}
    record Close() implements WeaverNavigation {}
    record EditParameter(UUID draftId, String parameterId) implements WeaverNavigation {}
    record AssignParameter(UUID draftId, String parameterId, hu.taliann.icesmp.dev.weaver.api.WeaverValue value) implements WeaverNavigation {}
    record ParameterCatalog(UUID draftId, String parameterId, int offset) implements WeaverNavigation {}
    record CatalogParameterValue(UUID draftId, String parameterId, String stableId) implements WeaverNavigation {}
    record Preview(UUID draftId) implements WeaverNavigation {}
    record ConfirmFinal(UUID draftId) implements WeaverNavigation {}
    record Execute(UUID draftId) implements WeaverNavigation {}
    record Receipt(UUID id) implements WeaverNavigation {}
    record Undo(UUID receiptId) implements WeaverNavigation {}
    record History() implements WeaverNavigation {}
    record ImportThread(String importerId, UUID threadId) implements WeaverNavigation {}
}
