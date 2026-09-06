package hu.taliann.icesmp.dev.weaver.integrity;

import java.util.Set;

/** Planned quarantine is durable before stages can run; it is not a fabricated applied influence. */
public record WeaverEffectIntent(Set<WeaverInfluenceTarget> targets) {
    public WeaverEffectIntent { targets = Set.copyOf(targets); if (targets.size() > 128) throw new IllegalArgumentException("Influence intent target cap"); }
    public static WeaverEffectIntent none() { return new WeaverEffectIntent(Set.of()); }
}
