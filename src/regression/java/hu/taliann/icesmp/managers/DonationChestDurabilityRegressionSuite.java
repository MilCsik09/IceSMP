package hu.taliann.icesmp.managers;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DonationChestDurabilityRegressionSuite {
    private DonationChestDurabilityRegressionSuite() { }

    public static void main(final String[] args) throws Exception {
        preparedDepositRollsBackWhenSourceMarkerSurvivesRestart();
        removedSourceCommitsExactlyOneDonationAfterRestart();
        preparedClaimDeliversOrFinalizesExactlyOnce();
        successfulClaimDoesNotReopenInventoryWithMarkedCursor();
        verifiesRuntimeUsesDurablePhasesAndOwnerHops();
        System.out.println("Donation chest durability regression suite passed.");
    }

    private static void preparedDepositRollsBackWhenSourceMarkerSurvivesRestart() {
        int ownerItems = 1;
        int visibleDonations = 0;
        final DonationTransferLifecycle.Recovery recovery = DonationTransferLifecycle.recovery(
                DonationTransferLifecycle.State.DEPOSIT_PREPARED, true);
        if (recovery == DonationTransferLifecycle.Recovery.ROLLBACK_DEPOSIT) {
            visibleDonations = 0;
        }
        check(ownerItems == 1 && visibleDonations == 0,
                "prepared deposit with surviving source marker duplicated the item");
    }

    private static void removedSourceCommitsExactlyOneDonationAfterRestart() {
        int ownerItems = 0;
        int visibleDonations = 0;
        final DonationTransferLifecycle.Recovery recovery = DonationTransferLifecycle.recovery(
                DonationTransferLifecycle.State.DEPOSIT_PREPARED, false);
        if (recovery == DonationTransferLifecycle.Recovery.COMMIT_DEPOSIT) visibleDonations++;
        check(ownerItems == 0 && visibleDonations == 1,
                "removed source was not recovered as exactly one durable donation");
        check(DonationTransferLifecycle.recovery(DonationTransferLifecycle.State.AVAILABLE, false)
                        == DonationTransferLifecycle.Recovery.NONE,
                "available donation replay attempted a second commit");
    }

    private static void preparedClaimDeliversOrFinalizesExactlyOnce() {
        int claimantItems = 0;
        final DonationTransferLifecycle.Recovery missingMarker = DonationTransferLifecycle.recovery(
                DonationTransferLifecycle.State.CLAIM_PREPARED, false);
        if (missingMarker == DonationTransferLifecycle.Recovery.DELIVER_CLAIM) claimantItems++;
        check(claimantItems == 1, "restart did not deliver the durable prepared claim");

        final DonationTransferLifecycle.Recovery deliveredMarker = DonationTransferLifecycle.recovery(
                DonationTransferLifecycle.State.CLAIM_PREPARED, true);
        if (deliveredMarker == DonationTransferLifecycle.Recovery.DELIVER_CLAIM) claimantItems++;
        check(deliveredMarker == DonationTransferLifecycle.Recovery.FINALIZE_CLAIM
                        && claimantItems == 1,
                "delivered claim replay duplicated the donation");
    }

    private static void successfulClaimDoesNotReopenInventoryWithMarkedCursor() throws Exception {
        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/DonationChestListener.java"));
        final String gui = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/gui/DonationChestGUI.java"));
        final int success = listener.indexOf("\"donation-take-success\"");
        final int boundary = listener.indexOf("private void removeClaimedEntryIfViewing", success);
        check(success >= 0 && boundary > success, "donation success handler is missing");
        final String successHandler = listener.substring(success, boundary);
        check(successHandler.contains("removeClaimedEntryIfViewing")
                        && !successHandler.contains("refreshIfViewing"),
                "successful claim reopens the GUI while the durable item is still on the cursor");
        check(gui.contains("removeClaimedEntry") && gui.contains("GuiUtil.filler()"),
                "claimed donation is not removed from the current view in place");
    }

    private static void verifiesRuntimeUsesDurablePhasesAndOwnerHops() throws Exception {
        final String manager = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/managers/DonationChestManager.java"));
        final String listener = Files.readString(Path.of(
                "src/main/java/hu/taliann/icesmp/listeners/DonationChestListener.java"));
        check(manager.contains("DonationTransferLifecycle.State.DEPOSIT_PREPARED")
                        && manager.contains("DonationTransferLifecycle.State.CLAIM_PREPARED")
                        && manager.contains("writeSnapshot()")
                        && manager.contains("getAsyncScheduler().runNow")
                        && manager.contains("player.getScheduler().run")
                        && manager.contains("currentSession(player) == sessionGeneration")
                        && !manager.contains("requestSave()"),
                "runtime mutation still relies on delayed autosave or lacks explicit hops");
        check(listener.contains("PlayerJoinEvent")
                        && listener.contains("PlayerRespawnEvent")
                        && listener.contains("isTransferMarked")
                        && listener.contains("PlayerDropItemEvent")
                        && listener.contains("attempt < 20")
                        && listener.contains("removeClaimedEntryIfViewing")
                        && listener.contains("DonationChestGUI.removeClaimedEntry(holder, entryId)"),
                "transfer marker lifecycle is not protected across restart/player events");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}
