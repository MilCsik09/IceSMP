package hu.taliann.icesmp.dev.weaver;

import hu.taliann.icesmp.security.HiddenDevAuthority;
import hu.taliann.icesmp.dev.weaver.api.*;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class WorldWeaverAuthorityRegressionSuite {
    public static void main(final String[] args) {
        final AtomicLong clock = new AtomicLong();
        final UUID actor = HiddenDevAuthority.PRIMARY_DEVELOPER;
        final UUID artifact = UUID.randomUUID();
        final WorldWeaverSessionManager sessions = new WorldWeaverSessionManager(clock::get);
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> sessions.open(UUID.randomUUID(), artifact, 1));
        final var session = sessions.open(actor, artifact, 1);
        final long firstView = session.nextView();
        WeaverTypeCompatibilityRegressionSuite.check(sessions.matches(actor, session.id(), firstView), "current view refused");
        session.nextView();
        WeaverTypeCompatibilityRegressionSuite.check(!sessions.matches(actor, session.id(), firstView), "stale GUI view accepted");
        final WeaverAuthorityToken token = new WeaverAuthorityToken(actor, session.id(), 10000, session::active, clock::get);
        token.requireValid();
        session.arming().grant(token, Set.of(WeaverArming.ArmingCapability.CANONICAL, WeaverArming.ArmingCapability.LIVE_GM), 30000);
        WeaverTypeCompatibilityRegressionSuite.check(session.arming().consume(session.id(), Set.of(WeaverArming.ArmingCapability.CANONICAL,
                WeaverArming.ArmingCapability.LIVE_GM)), "valid arming refused");
        WeaverTypeCompatibilityRegressionSuite.check(!session.arming().consume(session.id(), Set.of(WeaverArming.ArmingCapability.CANONICAL)), "arming replay accepted");
        session.arming().grant(token, Set.of(WeaverArming.ArmingCapability.LIVE_GM), 500);
        WeaverTypeCompatibilityRegressionSuite.check(!session.arming().consume(UUID.randomUUID(), Set.of(WeaverArming.ArmingCapability.LIVE_GM)), "foreign session arming accepted");
        WeaverTypeCompatibilityRegressionSuite.check(session.arming().active(session.id()).isEmpty(), "failed attempt retained grant");
        session.arming().grant(token, Set.of(WeaverArming.ArmingCapability.DESTRUCTIVE), 500);
        clock.set(500); WeaverTypeCompatibilityRegressionSuite.check(session.arming().active(session.id()).isEmpty(), "expired arming retained");
        final var value = new WeaverValue(WeaverTypeId.parse("weaver:int@1"), Map.of("value", 1), "fixture", "fixture.state", Set.of(), 1);
        for (int i = 0; i < 20; i++) session.threads().add(value);
        WeaverTypeCompatibilityRegressionSuite.check(session.threads().snapshot().size() == 12, "Thread Case unbounded");
        sessions.close(actor); WeaverTypeCompatibilityRegressionSuite.rejects(token::requireValid);
        WeaverTypeCompatibilityRegressionSuite.check(session.threads().snapshot().isEmpty(), "logout retained Thread Case");
        final var relog = sessions.open(actor, artifact, 2);
        WeaverTypeCompatibilityRegressionSuite.check(!relog.id().equals(session.id()), "relogin reused session nonce");
        WeaverTypeCompatibilityRegressionSuite.check(relog.mode() == IntegrityMode.SANDBOX && relog.arming().active(relog.id()).isEmpty(), "relogin retained live authority");
        sessions.shutdown(); WeaverTypeCompatibilityRegressionSuite.check(!relog.active(), "disable retained session");
        WeaverTypeCompatibilityRegressionSuite.rejects(() -> new WeaverAuthorityToken(UUID.randomUUID(), UUID.randomUUID(), 1000, () -> true, clock::get));
        System.out.println("WorldWeaver authority regression suite passed (primary-only sessions, stale views, arming and lifecycle).");
    }
}
