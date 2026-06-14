package hu.taliann.icesmp.data;

/**
 * WoW-style profession categories: a player picks ONE gathering and ONE
 * crafting primary profession, while every secondary profession is available
 * to everyone automatically.
 */
public enum ProfessionCategory {
    GATHERING("Gyűjtögető"),
    CRAFTING("Készítő"),
    SECONDARY("Másodlagos");

    private final String displayName;

    ProfessionCategory(final String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
