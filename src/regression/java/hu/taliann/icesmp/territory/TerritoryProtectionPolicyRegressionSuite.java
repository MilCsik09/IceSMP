package hu.taliann.icesmp.territory;

import java.util.List;
import static hu.taliann.icesmp.territory.TerritoryProtectionPolicy.*;

/** Behavioural matrix for the native resolver shared with provider inspection. */
public final class TerritoryProtectionPolicyRegressionSuite {
    private static int assertions;
    public static void main(final String[] args) {
        canonicalMatrix(); lifecyclePrecedence(); unavailableAuthority(); immutableTrace();
        System.out.println("Territory protection policy passed. assertions=" + assertions);
    }

    private static void canonicalMatrix() {
        for (final Rule rule : Rule.values()) for (final boolean protectedZone : List.of(false, true)) {
            for (final boolean restricted : List.of(false, true)) for (final boolean admin : List.of(false, true)) {
                for (final boolean builder : List.of(false, true)) for (final boolean member : List.of(false, true)) {
                    final Facts facts = facts(rule, true, protectedZone, restricted, admin, builder, member, false, false, false);
                    final boolean build = rule == Rule.BUILD || rule == Rule.INTERACT;
                    final boolean pvp = rule == Rule.PVP;
                    final boolean expected = restricted && !(admin && (build || pvp))
                            && !(builder && build) && !(build && !protectedZone && member);
                    final Decision decision = evaluate(facts, Overlay.INHERIT);
                    check(decision.denied() == expected, "canonical matrix " + facts);
                    check(decision.trace().getLast().matched() && decision.reason() == decision.trace().getLast().reason(), "trace and decision agree");
                }
            }
        }
        for (final Rule rule : Rule.values()) {
            check(!evaluate(facts(rule, false, false, false, false, false, false, false, false, false), Overlay.INHERIT).denied(), "wilderness");
        }
        for (final boolean protectedZone : List.of(false, true)) {
            final Facts terrain = new Facts(Rule.BUILD, true, protectedZone, true, true,
                    false, true, false, false, false, false, false, false);
            check(evaluate(terrain, Overlay.INHERIT).denied() == protectedZone, "ownerless terrain preserves survival faction land");
            check(evaluate(terrain, Overlay.DENY).denied(), "terrain DENY consumer");
        }
    }

    private static void lifecyclePrecedence() {
        for (final Overlay overlay : Overlay.values()) for (final boolean available : List.of(false, true)) {
            final Decision grace = evaluate(facts(Rule.PVP, true, true, true, true, false, false, true, true, true), overlay, available);
            check(grace.denied() && grace.reason() == Reason.DOOM_GRACE, "DOOM grace outranks tag, raid, admin and overlay");
            final Decision tag = evaluate(facts(Rule.PVP, true, true, true, false, false, false, false, true, true), overlay, available);
            check(!tag.denied() && tag.reason() == Reason.COMBAT_TAG, "combat-tag remains hard override");
            final Decision raid = evaluate(facts(Rule.PVP, true, true, true, false, false, false, false, false, true), overlay, available);
            check(!raid.denied() && raid.reason() == Reason.RAID_PARTICIPANT, "raid participant remains hard override");
            for (final Rule rule : List.of(Rule.BUILD, Rule.INTERACT, Rule.PVP)) {
                check(!evaluate(facts(rule, true, true, true, true, false, false, false, false, false), overlay, available).denied(), "admin hard bypass");
            }
            for (final Rule rule : List.of(Rule.BUILD, Rule.INTERACT)) {
                check(!evaluate(facts(rule, true, true, true, false, true, false, false, false, false), overlay, available).denied(), "builder hard bypass");
            }
        }
        for (final Rule rule : Rule.values()) {
            final Facts restricted = facts(rule, true, true, true, false, false, false, false, false, false);
            check(!evaluate(restricted, Overlay.ALLOW).denied(), "explicit overlay ALLOW");
            check(evaluate(restricted, Overlay.DENY).denied(), "explicit overlay DENY");
            check(evaluate(restricted, Overlay.DENY).deniesRegeneration(), "regen cannot bypass explicit overlay deny");
            check(!evaluate(restricted, Overlay.INHERIT).deniesRegeneration(), "canonical regen workflow remains available");
            final Facts open = facts(rule, true, false, false, false, false, true, false, false, false);
            check(evaluate(open, Overlay.DENY).denied(), "overlay can restrict a canonically open rule");
        }
    }

    private static void unavailableAuthority() {
        for (final Overlay overlay : Overlay.values()) {
            final Facts unknown = new Facts(Rule.PVP, true, true, true, false,
                    true, false, false, false, false, false, false, false);
            final Decision unavailable = evaluate(unknown, overlay);
            check(unavailable.denied() && unavailable.reason() == Reason.ACTOR_OWNER_UNAVAILABLE, "unavailable permission capture is not speculative allow");
            check(unavailable.deniesRegeneration(), "unavailable owner cannot enter a destructive regen path");
            final Decision failedProvider = evaluate(facts(Rule.FIRE, true, true, false, false, false, false, false, false, false), overlay, false);
            check(failedProvider.denied() && failedProvider.reason() == Reason.PROJECTION_UNAVAILABLE, "failed projection does not become canonical fallback");
            check(failedProvider.deniesRegeneration(), "failed provider cannot enter a destructive regen path");
        }
        expectInvalid(() -> new Facts(Rule.BUILD, false, true, false, false, false, true, false, false, false, false, false, false));
        expectInvalid(() -> new Facts(Rule.BUILD, true, true, true, false, true, true, false, false, false, true, false, false));
        expectInvalid(() -> new Facts(Rule.PVP, true, true, true, false, true, false, true, false, false, false, false, false));
    }

    private static void immutableTrace() {
        final var trace = evaluate(facts(Rule.BUILD, true, true, true, false, false, false, false, false, false), Overlay.INHERIT).trace();
        try { trace.clear(); throw new AssertionError("mutable trace"); }
        catch (final UnsupportedOperationException expected) { assertions++; }
        expectInvalid(() -> new Decision(false, Reason.ADMIN_BYPASS, List.of(new Step(Reason.OVERLAY_DENY, true))));
    }

    private static Facts facts(Rule rule, boolean zone, boolean protectedZone, boolean restricted,
                               boolean admin, boolean builder, boolean member, boolean grace, boolean tag, boolean raid) {
        return new Facts(rule, zone, protectedZone, restricted, false, true, true, admin, builder, member, grace, tag, raid);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); assertions++; }
    private static void expectInvalid(Runnable action) {
        try { action.run(); throw new AssertionError("invalid facts accepted"); }
        catch (final IllegalArgumentException expected) { assertions++; }
    }
}
