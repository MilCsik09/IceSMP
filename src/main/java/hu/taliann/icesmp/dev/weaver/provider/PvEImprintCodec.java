package hu.taliann.icesmp.dev.weaver.provider;

import hu.taliann.icesmp.dev.weaver.api.*;
import hu.taliann.icesmp.pve.*;
import java.util.*;
import java.util.function.Supplier;

/** A combat recipe, never an entity or player snapshot. References are checked again on apply. */
final class PvEImprintCodec implements WeaverTypeCodec {
    static final WeaverTypeId TYPE = WeaverTypeId.parse("icesmp:pve_combat_imprint@1");
    private final Supplier<Map<String, MobAbilityDefinition>> abilities;
    private final Supplier<Map<String, MobTemplate>> templates;
    PvEImprintCodec(Supplier<Map<String, MobAbilityDefinition>> abilities, Supplier<Map<String, MobTemplate>> templates) {
        this.abilities = Objects.requireNonNull(abilities); this.templates = Objects.requireNonNull(templates);
    }
    @Override public WeaverTypeId type() { return TYPE; }
    @Override public ValidationResult validate(Map<String, Object> payload) {
        try {
            if (!payload.keySet().equals(Set.of("rank", "archetype", "template", "abilities"))) return ValidationResult.rejected("IMPRINT_WHITELIST");
            final MobRank rank = MobRank.valueOf(((String) payload.get("rank")).toUpperCase(Locale.ROOT));
            final String rawArchetype = (String) payload.get("archetype");
            final MobArchetype archetype = rawArchetype.isEmpty() ? null : MobArchetype.valueOf(rawArchetype.toUpperCase(Locale.ROOT));
            final String template = (String) payload.get("template");
            if (!template.isEmpty() && !templates.get().containsKey(template)) return ValidationResult.rejected("IMPRINT_TEMPLATE_UNAVAILABLE");
            if (!(payload.get("abilities") instanceof List<?> kit) || kit.size() > 32 || new HashSet<>(kit).size() != kit.size()) return ValidationResult.rejected("IMPRINT_KIT_BOUNDS");
            final Map<String, MobAbilityDefinition> definitions = abilities.get();
            for (final Object id : kit) {
                if (!(id instanceof String text) || !definitions.containsKey(text) || !definitions.get(text).eligible(rank, archetype)) return ValidationResult.rejected("IMPRINT_ABILITY_INCOMPATIBLE");
            }
            return ValidationResult.accepted();
        } catch (RuntimeException invalid) { return ValidationResult.rejected("IMPRINT_INVALID"); }
    }
    @Override public byte[] canonicalBytes(Map<String, Object> payload) { validate(payload).requireValid(); return CanonicalValueBytes.encode(payload); }
}
