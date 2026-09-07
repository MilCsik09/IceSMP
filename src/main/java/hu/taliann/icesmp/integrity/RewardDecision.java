package hu.taliann.icesmp.integrity;

public record RewardDecision(boolean allowed, String reason) {
    public RewardDecision {
        if (reason == null || !reason.matches("[A-Z_]{1,64}") || allowed && !reason.equals("ALLOW")) throw new IllegalArgumentException("Invalid reward decision");
    }
    public static RewardDecision allow() { return new RewardDecision(true, "ALLOW"); }
    public static RewardDecision deny(final String reason) { return new RewardDecision(false, reason); }
}
