package hu.taliann.icesmp.moderation;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dependency-free ownership gate for one live inventory edit. Exactly one side may claim the
 * admin's inserted stack before the target write, and exactly one completion path may return the
 * displaced target stack after the write. The item payload deliberately remains in InvseeManager.
 */
public final class InventoryEscrowGate {
    public enum State { INIT, TARGET_CLAIMED, INSERTED_RETURN_CLAIMED, COMPLETE }

    private static final int INIT = 0;
    private static final int TARGET_CLAIMED = 1;
    private static final int INSERTED_RETURN_CLAIMED = 2;
    private static final int COMPLETE = 3;

    private final AtomicInteger state = new AtomicInteger(INIT);
    private final AtomicBoolean displacedReturnClaimed = new AtomicBoolean();

    public boolean claimTarget() {
        return state.compareAndSet(INIT, TARGET_CLAIMED);
    }

    public boolean claimInsertedReturn() {
        return state.compareAndSet(INIT, INSERTED_RETURN_CLAIMED);
    }

    public void completeTargetWrite() {
        if (!state.compareAndSet(TARGET_CLAIMED, COMPLETE)) {
            throw new IllegalStateException("target write completed without target ownership");
        }
    }

    public boolean claimDisplacedReturn() {
        return state.get() == COMPLETE && displacedReturnClaimed.compareAndSet(false, true);
    }

    public State state() {
        return switch (state.get()) {
            case INIT -> State.INIT;
            case TARGET_CLAIMED -> State.TARGET_CLAIMED;
            case INSERTED_RETURN_CLAIMED -> State.INSERTED_RETURN_CLAIMED;
            case COMPLETE -> State.COMPLETE;
            default -> throw new IllegalStateException("unknown escrow state");
        };
    }
}
