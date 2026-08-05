#!/usr/bin/env python3
"""Add conditional CAS mutation support to PlayerProfileService and its authority port."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileService.java"
AUTHORITY = ROOT / "src/main/java/hu/taliann/icesmp/playerprofile/application/PlayerProfileAuthority.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def patch_service() -> None:
    text = SERVICE.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "import java.util.function.Supplier;\nimport java.util.function.UnaryOperator;\n",
        "import java.util.function.Function;\nimport java.util.function.Supplier;\nimport java.util.function.UnaryOperator;\n",
        "Function import",
    )
    anchor = '''    public <T> CompletionStage<T> transact(final UUID id,
                                            final PlayerProfileTransactionManager.ProfileTransactionWork<T> work) {
'''
    block = '''    public <T extends ProfileSectionData, R> CompletionStage<R> mutateSectionConditional(
            final UUID id, final ProfileSectionId sectionId, final Class<T> type,
            final Function<T, ConditionalMutation<T, R>> mutation) {
        return mutateSectionConditional(id, sectionId, type, mutation, 4);
    }

    private <T extends ProfileSectionData, R> CompletionStage<R> mutateSectionConditional(
            final UUID id, final ProfileSectionId sectionId, final Class<T> type,
            final Function<T, ConditionalMutation<T, R>> mutation, final int attempts) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sectionId, "sectionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(mutation, "mutation");
        return repository.loadSnapshot(id).thenCompose(snapshot -> {
            final ProfileSectionSnapshot<?> raw = snapshot.section(sectionId).orElseThrow();
            if (!raw.health().usable()) return CompletableFuture.failedFuture(
                    new PlayerProfileRepositoryException.Quarantined(raw.health().diagnostic()));
            final T current = type.cast(raw.value());
            final ConditionalMutation<T, R> decision = Objects.requireNonNull(
                    mutation.apply(current), "conditional mutation result");
            if (!decision.changed()) {
                return CompletableFuture.completedFuture(decision.result());
            }
            final T next = Objects.requireNonNull(decision.next(), "conditional mutation next");
            final ProfileSectionSnapshot<T> candidate = new ProfileSectionSnapshot<>(sectionId,
                    raw.schema(), Math.addExact(raw.revision(), 1L), clock.instant(), next,
                    SectionHealth.healthy(), raw.extensions());
            return repository.saveSection(id, sectionId, raw.revision(), snapshot.profileRevision(), candidate)
                    .thenCompose(result -> {
                        if (result.status() == PlayerProfileRepository.SectionSaveResult.Status.COMMITTED) {
                            notifyChanged(id, result.snapshot().profileRevision(), Set.of(sectionId));
                            return CompletableFuture.completedFuture(decision.result());
                        }
                        if ((result.status() == PlayerProfileRepository.SectionSaveResult.Status.STALE_REVISION
                                || result.status() == PlayerProfileRepository.SectionSaveResult.Status.STALE_GENERATION)
                                && attempts > 1) {
                            repository.invalidate(id);
                            return mutateSectionConditional(id, sectionId, type, mutation, attempts - 1);
                        }
                        return CompletableFuture.failedFuture(
                                new PlayerProfileRepositoryException(result.detail()));
                    });
        });
    }

    public record ConditionalMutation<T, R>(boolean changed, T next, R result) {
        public ConditionalMutation {
            if (changed && next == null) {
                throw new IllegalArgumentException("changed conditional mutation requires next value");
            }
        }

        public static <T, R> ConditionalMutation<T, R> unchanged(final R result) {
            return new ConditionalMutation<>(false, null, result);
        }

        public static <T, R> ConditionalMutation<T, R> changed(final T next, final R result) {
            return new ConditionalMutation<>(true, Objects.requireNonNull(next, "next"), result);
        }
    }

'''
    text = replace_once(text, anchor, block + anchor, "conditional mutation block")
    SERVICE.write_text(text, encoding="utf-8")


def patch_authority() -> None:
    text = AUTHORITY.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "import java.util.function.UnaryOperator;\n",
        "import java.util.function.Function;\nimport java.util.function.UnaryOperator;\n",
        "authority Function import",
    )
    anchor = '''    public <T extends PlayerProfileSection> CompletionStage<PlayerProfileSnapshot> mutateExtensions(
'''
    block = '''    public <T extends PlayerProfileSection, R> CompletionStage<R> mutateSectionConditional(
            final UUID playerId,
            final ProfileSectionId sectionId,
            final Class<T> type,
            final Function<T, PlayerProfileService.ConditionalMutation<T, R>> mutation) {
        return service.mutateSectionConditional(playerId, sectionId, type, mutation);
    }

'''
    text = replace_once(text, anchor, block + anchor, "authority conditional method")
    AUTHORITY.write_text(text, encoding="utf-8")


def main() -> int:
    patch_service()
    patch_authority()
    print("PlayerProfile conditional mutation support applied.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
