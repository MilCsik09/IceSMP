package hu.taliann.icesmp.dev.artifact;

public record DevArtifactPolicy(boolean enabled, boolean autoRestore, boolean issueOnJoin,
                                boolean mainHandOnly, long checkIntervalTicks) {
    public DevArtifactPolicy {
        if (checkIntervalTicks < 1 || checkIntervalTicks > 1200) {
            throw new IllegalArgumentException("Artifact check interval outside 1..1200 ticks");
        }
    }
}
