package hu.taliann.icesmp.data;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * WoW-style profession roster. Gathering professions pair naturally with
 * crafting professions (Bányász→Kovács, Gyógynövényész→Alkimista,
 * Favágó→Bűvölő...), while the secondary professions (Halász, Szakács) are
 * learnable by everyone without taking a primary slot.
 */
public enum ProfessionType {
    // Gyűjtögető főszakmák
    MINER("miner", "<gray>Bányász</gray>", ProfessionCategory.GATHERING),
    HERBALIST("herbalist", "<green>Gyógynövényész</green>", ProfessionCategory.GATHERING),
    LUMBERJACK("lumberjack", "<dark_green>Favágó</dark_green>", ProfessionCategory.GATHERING),
    // Készítő főszakmák
    ARMORER("armorer", "<gold>Kovács</gold>", ProfessionCategory.CRAFTING),
    ALCHEMIST("alchemist", "<light_purple>Alkimista</light_purple>", ProfessionCategory.CRAFTING),
    ENCHANTER("enchanter", "<aqua>Bűvölő</aqua>", ProfessionCategory.CRAFTING),
    // Másodlagos szakmák (mindenkié)
    FISHERMAN("fisherman", "<blue>Halász</blue>", ProfessionCategory.SECONDARY),
    COOK("cook", "<red>Szakács</red>", ProfessionCategory.SECONDARY);

    private final String id;
    private final Component displayName;
    private final ProfessionCategory category;

    ProfessionType(final String id, final String displayName, final ProfessionCategory category) {
        this.id = id;
        this.displayName = MiniMessage.miniMessage().deserialize(displayName);
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public ProfessionCategory getCategory() {
        return category;
    }

    public static ProfessionType fromId(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }

        for (final ProfessionType professionType : values()) {
            if (professionType.id.equalsIgnoreCase(id) || professionType.name().equalsIgnoreCase(id)) {
                return professionType;
            }
        }

        return null;
    }
}
