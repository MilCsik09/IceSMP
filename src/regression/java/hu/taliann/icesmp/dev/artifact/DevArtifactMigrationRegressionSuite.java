package hu.taliann.icesmp.dev.artifact;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DevArtifactMigrationRegressionSuite {
    private static final UUID OWNER = new UUID(42, 42);
    private static final UUID INSTANCE = new UUID(84, 84);

    public static void main(final String[] args) {
        exactPendingProgressAndPitySurvive();
        unissuedStateSurvivesWithoutAnItem();
        schemaTwoRoundTripPreservesOtherArtifacts();
        invalidLegacyStateDoesNotInventDefaults();
        unsupportedSchemaAndUnsafeValuesFail();
        System.out.println("DEV artifact migration regression suite passed.");
    }

    private static Map<String, Object> legacy() {
        final Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("owner", OWNER.toString()); legacy.put("instance", INSTANCE.toString());
        legacy.put("issued", true); legacy.put("progress-millis", 599_123L);
        legacy.put("pending", Map.of("rarity", "epikus", "entry", "unique:fixture:2", "item", new ExactReward("exact-pdc-bytes")));
        legacy.put("pity", Map.of("since-rare", 23, "since-epic", 145, "since-legendary", 998));
        return legacy;
    }

    private static Map<String, DevArtifactState> migrate(final Map<String, Object> value) {
        return DevArtifactStateCodec.decode(Map.of("bingulus", value), item -> {
            if (!(item instanceof ExactReward reward)) throw new IllegalArgumentException("Invalid exact item");
            return reward.bytes();
        });
    }

    private static void exactPendingProgressAndPitySurvive() {
        final DevArtifactState state = migrate(legacy()).get("csodalatos_bingulus");
        check(state.owner().equals(OWNER) && state.instanceId().equals(INSTANCE) && state.issued(), "identity drift");
        check(state.behaviorState().get("progress-millis").equals(599_123L), "active time reset");
        check(state.behaviorState().get("pity").equals(Map.of("since-rare", 23, "since-epic", 145, "since-legendary", 998)), "pity reset");
        check(state.behaviorState().get("pending").equals(Map.of("rarity", "epikus", "entry", "unique:fixture:2", "item", "exact-pdc-bytes")), "pending rerolled or rebuilt");
        check(DevArtifactStateCodec.decode(DevArtifactStateCodec.encode(Map.of("csodalatos_bingulus", state)),
                item -> { throw new AssertionError("schema 2 used legacy item decoder"); })
                .get("csodalatos_bingulus").equals(state), "migrated state changed on restart");
    }

    private static void unissuedStateSurvivesWithoutAnItem() {
        final Map<String, Object> value = legacy();
        value.put("issued", false); value.put("progress-millis", 0L);
        value.put("pending", Map.of("rarity", "", "entry", ""));
        value.put("pity", Map.of("since-rare", 0, "since-epic", 0, "since-legendary", 0));
        final DevArtifactState state = migrate(value).get("csodalatos_bingulus");
        check(!state.issued() && !state.behaviorState().containsKey("pending"), "migration issued/rewarded empty artifact");
    }

    private static void schemaTwoRoundTripPreservesOtherArtifacts() {
        final Map<String, DevArtifactState> states = new LinkedHashMap<>(migrate(legacy()));
        states.put("future_artifact", new DevArtifactState(new UUID(90, 90), new UUID(91, 91), true, 37,
                Map.of("versioned-payload", Map.of("field", java.util.List.of("one", "two")))));
        check(DevArtifactStateCodec.decode(DevArtifactStateCodec.encode(states), item -> "unused").equals(states),
                "schema 2 erased another artifact/behavior");
    }

    private static void invalidLegacyStateDoesNotInventDefaults() {
        for (final String field : new String[]{"owner", "instance", "issued", "progress-millis", "pending", "pity"}) {
            final Map<String, Object> value = legacy(); value.remove(field);
            rejects(() -> migrate(value));
        }
        final Map<String, Object> wrongOwner = legacy(); wrongOwner.put("owner", "forged");
        rejects(() -> migrate(wrongOwner));
        final Map<String, Object> halfPending = legacy(); halfPending.put("pending", Map.of("rarity", "epikus", "entry", ""));
        rejects(() -> migrate(halfPending));
        final Map<String, Object> wrongItem = legacy(); wrongItem.put("pending", Map.of("rarity", "epikus", "entry", "unique:fixture:2", "item", "not an item"));
        rejects(() -> migrate(wrongItem));
        final Map<String, Object> negative = legacy(); negative.put("progress-millis", -1L);
        rejects(() -> migrate(negative));
        final Map<String, Object> unissuedProgress = legacy(); unissuedProgress.put("issued", false);
        rejects(() -> migrate(unissuedProgress));
    }

    private static void unsupportedSchemaAndUnsafeValuesFail() {
        rejects(() -> DevArtifactStateCodec.decode(Map.of("schema-version", 3, "artifacts", Map.of()), item -> ""));
        rejects(() -> DevArtifactStateCodec.decode(Map.of("schema-version", 2.0, "artifacts", Map.of()), item -> ""));
        final Map<String, Object> invalid = Map.of("schema-version", 2, "artifacts", Map.of("artifact", Map.of(
                "owner", OWNER.toString(), "instance", INSTANCE.toString(), "issued", true,
                "behavior-state", Map.of("live-object", new Object()))));
        rejects(() -> DevArtifactStateCodec.decode(invalid, item -> ""));
    }

    private record ExactReward(String bytes) {}
    private static void rejects(final Runnable action) {
        try { action.run(); } catch (final RuntimeException expected) { return; }
        throw new AssertionError("Invalid state loaded");
    }
    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
