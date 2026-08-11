package hu.taliann.icesmp.classspec.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Logical Soulbond identity; no physical ItemStack is authoritative here. */
public record SoulbondState(UUID signatureId, int evolution, List<String> modules,
                            long revision, String recoveryNote) {

    public SoulbondState {
        Objects.requireNonNull(signatureId, "signatureId");
        if (evolution < 0 || revision < 0L) {
            throw new IllegalArgumentException("Soulbond evolution and revision must be non-negative");
        }
        modules = List.copyOf(Objects.requireNonNull(modules, "modules"));
        recoveryNote = recoveryNote == null ? "" : recoveryNote.trim();
        if (modules.stream().anyMatch(module -> module == null || module.isBlank())) {
            throw new IllegalArgumentException("Soulbond module ids must be non-blank");
        }
    }
}
