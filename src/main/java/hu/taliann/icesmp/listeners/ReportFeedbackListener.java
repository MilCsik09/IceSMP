package hu.taliann.icesmp.listeners;

import hu.taliann.icesmp.managers.ReportManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * IDEAS A58: az offline bejelentőknek eltárolt report-visszajelzések kézbesítése belépéskor.
 * A join-event a játékos saját régió-szálán fut, így a közvetlen üzenetküldés Folia-safe.
 */
public final class ReportFeedbackListener implements Listener {

    private final ReportManager reportManager;

    public ReportFeedbackListener(final ReportManager reportManager) {
        this.reportManager = reportManager;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        reportManager.deliverPendingFeedback(event.getPlayer());
    }
}
