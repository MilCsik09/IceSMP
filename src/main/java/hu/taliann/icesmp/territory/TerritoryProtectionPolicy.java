package hu.taliann.icesmp.territory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Canonical, Bukkit-free protection evaluation shared by runtime and inspection. */
public final class TerritoryProtectionPolicy {
    private TerritoryProtectionPolicy() { }

    public enum Rule { BUILD, INTERACT, PVP, EXPLOSIONS, FIRE }
    public enum Overlay { INHERIT, ALLOW, DENY }
    public enum Reason {
        DOOM_GRACE, COMBAT_TAG, ADMIN_BYPASS, BUILDER_BYPASS, RAID_PARTICIPANT,
        ACTOR_OWNER_UNAVAILABLE, OVERLAY_ALLOW, OVERLAY_DENY, WILDERNESS,
        RULE_DISABLED, PROTECTED_ZONE, FACTION_MEMBER, FACTION_NON_MEMBER,
        OWNERLESS_FACTION_TERRAIN, ENVIRONMENT_RULE, PVP_RULE, PROJECTION_UNAVAILABLE
    }

    /** All fields are detached values captured by the canonical service, never live handles. */
    public record Facts(Rule rule, boolean zonePresent, boolean protectedZone,
                        boolean restricted, boolean ownerlessTerrain,
                        boolean actorPresent, boolean actorPermissionsKnown,
                        boolean adminBypass, boolean builderBypass, boolean factionMember,
                        boolean doomGrace, boolean combatTagged, boolean raidParticipant) {
        public Facts {
            Objects.requireNonNull(rule);
            if ((!zonePresent && (protectedZone || restricted || raidParticipant || doomGrace))
                    || (!actorPresent && (adminBypass || builderBypass || factionMember || raidParticipant))
                    || (!actorPermissionsKnown && (adminBypass || builderBypass))
                    || (rule != Rule.PVP && (doomGrace || combatTagged || raidParticipant))
                    || (ownerlessTerrain && (rule != Rule.BUILD || actorPresent))) {
                throw new IllegalArgumentException("Contradictory protection facts");
            }
        }
    }

    public record Step(Reason reason, boolean matched) {
        public Step { Objects.requireNonNull(reason); }
    }
    public record Decision(boolean denied, Reason reason, List<Step> trace) {
        public Decision {
            Objects.requireNonNull(reason); trace = List.copyOf(trace);
            if (trace.isEmpty() || !trace.getLast().matched() || trace.getLast().reason() != reason) {
                throw new IllegalArgumentException("Decision must match its terminal trace step");
            }
        }
        /** A temporary native regeneration route must not turn an explicit deny into destruction. */
        public boolean deniesRegeneration() {
            return denied && (reason == Reason.OVERLAY_DENY || reason == Reason.PROJECTION_UNAVAILABLE
                    || reason == Reason.ACTOR_OWNER_UNAVAILABLE);
        }
    }

    /** Hard lifecycle decisions precede projections; faction membership is canonical input. */
    public static Decision evaluate(final Facts facts, final Overlay overlay) {
        return evaluate(facts, overlay, true);
    }

    public static Decision evaluate(final Facts facts, final Overlay overlay, final boolean projectionAvailable) {
        Objects.requireNonNull(facts); Objects.requireNonNull(overlay);
        final List<Step> trace = new ArrayList<>();
        if (facts.rule() == Rule.PVP) {
            if (match(trace, Reason.DOOM_GRACE, facts.doomGrace())) return result(true, trace);
            if (match(trace, Reason.COMBAT_TAG, facts.combatTagged())) return result(false, trace);
        }
        final boolean playerAction = facts.rule() == Rule.BUILD || facts.rule() == Rule.INTERACT || facts.rule() == Rule.PVP;
        if (playerAction && !facts.ownerlessTerrain()) {
            if (match(trace, Reason.ADMIN_BYPASS, facts.actorPresent() && facts.adminBypass())) return result(false, trace);
            if (facts.rule() != Rule.PVP && match(trace, Reason.BUILDER_BYPASS,
                    facts.actorPresent() && facts.builderBypass())) return result(false, trace);
        }
        if (facts.rule() == Rule.PVP && match(trace, Reason.RAID_PARTICIPANT, facts.raidParticipant())) return result(false, trace);
        if (match(trace, Reason.PROJECTION_UNAVAILABLE, !projectionAvailable)) return result(true, trace);
        // An unavailable actor owner is not evidence that a canonical hard bypass is absent.
        // Do not publish a speculative allow while permission capture is unavailable.
        if (playerAction && facts.actorPresent() && !facts.actorPermissionsKnown()
                && (facts.restricted() || overlay != Overlay.INHERIT)) {
            match(trace, Reason.ACTOR_OWNER_UNAVAILABLE, true); return result(true, trace);
        }
        if (match(trace, Reason.OVERLAY_DENY, overlay == Overlay.DENY)) return result(true, trace);
        if (match(trace, Reason.OVERLAY_ALLOW, overlay == Overlay.ALLOW)) return result(false, trace);
        if (match(trace, Reason.WILDERNESS, !facts.zonePresent())) return result(false, trace);
        if (match(trace, Reason.RULE_DISABLED, !facts.restricted())) return result(false, trace);
        if (facts.ownerlessTerrain()) {
            if (match(trace, Reason.OWNERLESS_FACTION_TERRAIN, !facts.protectedZone())) return result(false, trace);
            match(trace, Reason.PROTECTED_ZONE, true); return result(true, trace);
        }
        if (facts.rule() == Rule.BUILD || facts.rule() == Rule.INTERACT) {
            if (match(trace, Reason.PROTECTED_ZONE, facts.protectedZone())) return result(true, trace);
            if (match(trace, Reason.FACTION_MEMBER, facts.factionMember())) return result(false, trace);
            match(trace, Reason.FACTION_NON_MEMBER, true); return result(true, trace);
        }
        match(trace, facts.rule() == Rule.PVP ? Reason.PVP_RULE : Reason.ENVIRONMENT_RULE, true); return result(true, trace);
    }

    private static boolean match(final List<Step> trace, final Reason reason, final boolean matched) {
        trace.add(new Step(reason, matched)); return matched;
    }
    private static Decision result(final boolean denied, final List<Step> trace) {
        return new Decision(denied, trace.getLast().reason(), trace);
    }
}
