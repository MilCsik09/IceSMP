package hu.taliann.icesmp.monk;

import java.nio.file.Files;
import java.nio.file.Path;

/** Focused lifecycle witness for Stagger debt conservation across session boundaries. */
public final class MonkStaggerLifecycleRegressionSuite {

    private static int assertions;

    private MonkStaggerLifecycleRegressionSuite() {
    }

    public static void main(final String[] args) throws Exception {
        logoutPaysDebtBeforeReconnect();
        deathAndSpecSwitchSemanticsAreExplicit();
        System.out.println("Monk Stagger lifecycle regression suite passed. assertions=" + assertions);
    }

    private static void logoutPaysDebtBeforeReconnect() {
        final MonkCombatState oldSession = new MonkCombatState();
        oldSession.stagger(7.5D, 20.0D);
        final double debt = oldSession.collapseStagger();
        check(Math.abs(debt - 7.5D) < 1.0E-9,
                "logout consequence extracts the whole remaining Stagger debt");
        check(oldSession.staggerPool() == 0.0D,
                "old session contains no debt after the consequence was paid");

        final MonkCombatState reconnect = new MonkCombatState();
        check(reconnect.staggerPool() == 0.0D,
                "reconnect starts a clean transient pool because the previous session already paid its debt");
        reconnect.stagger(2.0D, 20.0D);
        check(Math.abs(reconnect.collapseStagger() - 2.0D) < 1.0E-9,
                "new-session debt is independent and cannot resurrect the old pool");
    }

    private static void deathAndSpecSwitchSemanticsAreExplicit() throws Exception {
        final String service = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/monk/MonkGameplayService.java"))
                .replace("\r\n", "\n");
        final int quit = service.indexOf("public void onQuit(final PlayerQuitEvent event)");
        final int quitPay = service.indexOf("applyStaggerConsequence(event.getPlayer());", quit);
        final int quitClear = service.indexOf("clearPlayerState(event.getPlayer().getUniqueId());", quit);
        check(quit >= 0 && quitPay > quit && quitClear > quitPay,
                "logout pays Stagger before transient state is deleted");

        final int kick = service.indexOf("public void onKick(final PlayerKickEvent event)");
        final int kickPay = service.indexOf("applyStaggerConsequence(event.getPlayer());", kick);
        final int kickClear = service.indexOf("clearPlayerState(event.getPlayer().getUniqueId());", kick);
        check(kick >= 0 && kickPay > kick && kickClear > kickPay,
                "kick pays Stagger before transient state is deleted");

        final int spec = service.indexOf("public void clearSpecializationState(final UUID playerId)");
        final int specPay = service.indexOf("applyStaggerConsequence(player);", spec);
        final int specClear = service.indexOf("state.clearSpecializationState();", specPay);
        check(spec >= 0 && specPay > spec && specClear > specPay,
                "spec/loadout switch pays Stagger before clearing specialization state");

        final int offlineSpec = service.indexOf("public void clearSpecializationStateOffline");
        final int offlineClear = service.indexOf("state.clearSpecializationState();", offlineSpec);
        check(offlineSpec >= 0 && offlineClear > offlineSpec
                        && service.substring(offlineSpec, offlineClear).indexOf("setHealth") < 0,
                "offline reconciliation clears UUID state without touching a Bukkit entity");

        final int death = service.indexOf("onPlayerDeath(final PlayerDeathEvent event)");
        check(death >= 0 && service.indexOf("clearPlayerState(event.getEntity().getUniqueId());", death) > death,
                "death discards the pool only after death itself already settled the player's life state");

        check(service.contains("final double finalBefore = event.getFinalDamage();")
                        && service.contains("MonkCombatState.acceptedDefer(finalBefore,"),
                "Stagger defer is calculated from final mitigated damage");
        check(service.contains("MonkCombatState.bankedFromReducedFinal("),
                "the settled event final damage is converted to debt exactly once");
        check(!service.contains("event.getDamage() * staggerPercent"),
                "raw pre-mitigation damage is never used as Stagger debt");
        check(service.contains("player.setHealth(Math.max(1.0D,") && !service.contains("player.damage(pool)"),
                "periodic/consequence debt is not sent through armor/resistance a second time");
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
