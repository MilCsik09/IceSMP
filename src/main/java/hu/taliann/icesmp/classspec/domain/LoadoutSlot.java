package hu.taliann.icesmp.classspec.domain;

/** The two fixed Profile v2 loadout positions. */
public enum LoadoutSlot {
    FIRST(0),
    SECOND(1);

    private final int index;

    LoadoutSlot(final int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }
}
