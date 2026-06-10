package hu.taliann.icesmp.relics;

public record RelicTriggerConfig(
        boolean enabled,
        String abilityId,
        long cooldownSeconds,
        String message
) {
}

